"""Make the camera's screen button lock the current clip instead of toggling the LCD.

Reads a `cardv` binary (default `cardv.bin` in this folder — pull your own off the camera, or
use the sounds build so both patches apply) and writes `cardv.lockbtn`. Nothing is patched
blind: the original bytes at both sites are asserted first, and afterwards only those bytes are
allowed to have changed.

    python patch_lockbtn.py                 # patches cardv.bin
    python patch_lockbtn.py --base cardv.final

WHAT IT DOES

  The camera can lock a clip — the FAT read-only bit, the `ATTR` 32 -> 33 that cmd 3015 reports
  — but no Wi-Fi command reaches that code, and the button that does it is dead while a phone is
  linked. Meanwhile the screen button does nothing useful once "screen off after boot" is set.

  The screen button does NOT go through the key table `xSysMan_NormalKeyCB` uses for other keys
  (patching that table changes nothing — it was tried). Its branch is self-contained:

    0x1e284  UI_GetData(67)                 ; "key sound" flag
    0x1e290  if 1 -> 0x1e44c: UISound_Play, then branch back to
    0x1e294  SysGetLCDStatus() -> invert -> SysSetLCDStatus()     <-- the screen toggle
    0x1e2a8  b 0x1e0f8                      ; return; no event is ever posted

  Both flag-67 branches converge on 0x1e294, so that is the hook. It becomes a call to a stub
  that posts the Custom1 key event — the same event and parameter the firmware's own "Custom1
  Key" simulator at 0x0001b0a8 posts, which ends in UIFlowWndMovie_OnCustom1 ->
  iamoviemulti_fm_set_ro_file. The stub is written over WiFiCmd_OnExeSetMovieTimelapse
  (0x0003ac3c), a function no command can reach, and tail-calls Ux_PostEvent so it uses no
  stack.

RESULT (verified on hardware)

  Short press while recording: the key sound plays, then the clip being recorded is locked and
  filed under \\ARPHA\\Event with ATTR 33, and the camera says "current video locked".
  Long press still powers the camera off — it is not on this code path.
  The short press no longer toggles the screen. That is the trade.

  Boot is untouched: the patched code only runs on a key press, so a mistake here cannot stop
  the camera booting.

FIRMWARE BUILD

  These offsets are specific to `Arpha-D25--V0.0.6-20230816TE`. The assertions below will fail
  loudly on any other build rather than corrupting it — do not "fix" them by loosening the
  checks; find the equivalent code in your own binary instead.

Install and rollback are in README.md.
"""

import argparse
import hashlib
import os
import struct

BASE = 0x10000  # vaddr = file offset + BASE

HOOK = 0x0001E294        # the screen toggle, where both flag-67 branches meet
STUB = 0x0003AC3C        # dead function: WiFiCmd_OnExeSetMovieTimelapse
POST = 0x000DEA68        # Ux_PostEvent
RETURN_TO = 0x0001E0F8   # the enclosing function's epilogue

# The six instructions the hook must contain before we touch it:
#   bl SysGetLCDStatus / cmp r0,#0 / moveq r0,#1 / movne r0,#0 / bl SysSetLCDStatus / b 0x1e0f8
HOOK_ORIGINAL = bytes.fromhex(
    "3dfbffeb"  "000050e3"  "0100a003"  "0000a013"  "3dfbffeb"  "92ffffea"
)  # the two bl's encode identically: target and PC are both 0x10 apart
# The dead function starts with `push {r4, r5, r6, lr}`.
STUB_ORIGINAL = bytes.fromhex("70402de9")


def arm_bl(at, target):
    return 0xEB000000 | (((target - (at + 8)) >> 2) & 0xFFFFFF)


def arm_b(at, target):
    return 0xEA000000 | (((target - (at + 8)) >> 2) & 0xFFFFFF)


def movw(rd, imm16):
    return 0xE3000000 | ((imm16 >> 12) << 16) | (rd << 12) | (imm16 & 0xFFF)


def movt(rd, imm16):
    return 0xE3400000 | ((imm16 >> 12) << 16) | (rd << 12) | (imm16 & 0xFFF)


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--base", default=os.path.join(here, "cardv.bin"),
                    help="cardv to patch (default: cardv.bin next to this script)")
    ap.add_argument("--out", default=os.path.join(here, "cardv.lockbtn"))
    args = ap.parse_args()

    raw = open(args.base, "rb").read()
    print("base %s  %d bytes  md5 %s"
          % (os.path.basename(args.base), len(raw), hashlib.md5(raw).hexdigest()))

    d = bytearray(raw)
    hook_off, stub_off = HOOK - BASE, STUB - BASE

    got = bytes(d[hook_off:hook_off + len(HOOK_ORIGINAL)])
    assert got == HOOK_ORIGINAL, (
        "the screen-toggle block is not where this patch expects it.\n"
        "  at 0x%06x expected %s\n  found            %s\n"
        "This is a different firmware build - do not patch it with these offsets."
        % (hook_off, HOOK_ORIGINAL.hex(" "), got.hex(" "))
    )
    got = bytes(d[stub_off:stub_off + len(STUB_ORIGINAL)])
    assert got == STUB_ORIGINAL, (
        "the function the stub would overwrite is not the expected one (0x%06x: %s)"
        % (stub_off, got.hex(" "))
    )
    print("both patch sites verified against the expected original bytes")

    # stub: post the Custom1 key event, then tail-call into Ux_PostEvent
    stub = [
        movw(0, 0x0116), movt(0, 0x1106),   # r0 = 0x11060116  (the key message)
        movw(2, 0x0401), movt(2, 0x1106),   # r2 = 0x11060401  (its parameter)
        0xE3A01001,                          # mov r1, #1
        arm_b(STUB + 5 * 4, POST),           # b Ux_PostEvent -> returns to our caller
    ]
    for i, word in enumerate(stub):
        struct.pack_into("<I", d, stub_off + i * 4, word)

    # hook: call the stub, then return through the epilogue
    struct.pack_into("<I", d, hook_off, arm_bl(HOOK, STUB))
    struct.pack_into("<I", d, hook_off + 4, arm_b(HOOK + 4, RETURN_TO))

    changed = [i for i in range(len(d)) if d[i] != raw[i]]
    assert changed and all(hook_off <= c < hook_off + 8 or stub_off <= c < stub_off + 24
                           for c in changed), "patch touched something outside the two sites"
    print("bytes changed: %d (hook at 0x%06x, stub at 0x%06x)" % (len(changed), hook_off, stub_off))

    open(args.out, "wb").write(bytes(d))
    print("wrote %s  md5 %s"
          % (os.path.basename(args.out), hashlib.md5(bytes(d)).hexdigest()))
    print("verify on the camera after copying:  md5sum /usr/bin/cardv")


if __name__ == "__main__":
    main()
