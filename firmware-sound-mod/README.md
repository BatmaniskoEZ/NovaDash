# Dashcam custom sounds — ARPHA D25 (Novatek NA51055)

How the camera's prompt sounds were replaced by patching `/usr/bin/cardv` on the live camera
over a root telnet shell. Fully reversible. Done 2026-07-07.

Camera: ARPHA D25, firmware `Arpha-D25--V0.0.6-20230816TE`, IP `192.168.1.254`.
**These byte offsets are specific to THIS firmware build — do not apply to any other version
(it would corrupt `cardv` and brick the camera).**

> ℹ️ **This public copy has the scripts + this howto only.** The camera's proprietary firmware
> binary (`cardv`) and the copyrighted audio clips are intentionally NOT included (they're
> git-ignored). Pull your own `cardv` off your camera and supply your own sounds — see below.

---

## ⏪ ROLLBACK — restore all original sounds ("the switch")

The pristine original is kept on the camera SD card. From a root telnet shell (see below):

```sh
cp /mnt/sd/cardv.orig.bak /usr/bin/cardv && sync && reboot
```

Make your own backup first — `cp /usr/bin/cardv /mnt/sd/cardv.orig.bak` before patching.

## Example mapping

The reference build remapped three slots (bring your own audio for each):

| Trigger | Sound table id | Replaced with |
|---|---|---|
| Power-on / recording start | 23 ("recording started") | a short music clip (~2 s) |
| Touchscreen button | 0 | a short chime |
| Emergency / locked clip | 12 | a short effect |

Requires **Touch-tone toggle ON + volume High** in camera settings for the button sound. The
button and emergency slots are small, so their audio was relocated into bigger, rarely-heard
slots (id 14 = format-first-use, id 26 = format-reminder). Boot jingles (id 2/20) were left
original — they never actually trigger at power-on on this unit; the real power-on sound is id 23.

## Demo

Custom power-on and button-press sounds in action (~12 s):


https://github.com/user-attachments/assets/d55c5564-8afc-4fb2-92ad-4c3c8fa85327


---

## Access to the camera (from a PC on the dashcam Wi-Fi)

- **Telnet:** port 23, login `root`, **no password** → root shell.
- **FTP:** port 21, login `root` / empty password (anonymous is refused with 530). Chrooted to
  the SD card (`/mnt/sd`). Used to upload files: `curl -u root: -T file ftp://192.168.1.254/name`
- **HTTP:** port 80 = the Wi-Fi command API and it serves SD files (`http://192.168.1.254/<file>`).
- The rootfs (`ubi0:rootfs`, ubifs) is mounted **rw**, so `/usr/bin/cardv` can be overwritten
  in place. Replace a running binary with `rm` then `cp` (the live process keeps the old inode).
- ⚠ **Every power-cycle turns the camera Wi-Fi OFF** — re-enable it on the camera and reconnect
  the PC before pushing anything.

### `camsh.py` — scripted root shell from the PC
`python camsh.py "<shell command>" [timeout_seconds]`. Handles telnet IAC + root login.
Gotchas baked in: it splits its end-marker so the server's input-echo can't end the read early;
**don't** let a command get cut off mid-`cp` (a killed copy leaves a truncated file).

---

## How to change a sound

1. **Pull `cardv`** (if you don't have `cardv.bin` here): telnet `cp /usr/bin/cardv /mnt/sd/cardv.bin`,
   then PC `curl http://192.168.1.254/cardv.bin -o cardv.bin`.
2. **Convert** your clip to the camera's format — **16 kHz, 16-bit, mono, headerless PCM**:
   ```sh
   ffmpeg -i input -ac 1 -ar 16000 -af "atrim=0:DUR,alimiter=limit=0.98,afade=t=out:st=..:d=0.05" -f s16le out.raw
   ```
   (add `acompressor=threshold=-24dB:ratio=8:makeup=12` before the limiter for a loud effect;
    louder than this just distorts the speaker.)
3. **Patch** with `patch_final.py` (edit its `jobs` list): each job = `(host_slot_id, pcm_bytes,
   [table-entry-ids to point there])`. It writes the PCM into the host slot's region and repoints
   the entries. **Rules:** the PCM data MUST stay inside the audio pool (≈ `0xe3f8e0..0xf6cd20`);
   the driver (`GxSound_ActOnSndNotInTbl`) plays SILENCE for out-of-pool pointers. If the target's
   own slot is too small, host it in a bigger UNIQUE, expendable slot (not id 2/20 — they share
   boot data). The scripts read `cardv.bin` / the `.raw` clips from their own folder, so just run them from here.
4. **Deploy + verify md5 at every step:**
   ```sh
   curl -u root: -T cardv.final ftp://192.168.1.254/cardv.new
   python camsh.py "md5sum /mnt/sd/cardv.new; rm -f /usr/bin/cardv; cp /mnt/sd/cardv.new /usr/bin/cardv; chmod 755 /usr/bin/cardv; sync; md5sum /usr/bin/cardv"
   python camsh.py "sync && reboot"
   ```

## The sound table (reverse-engineered)

- `cardv` is a 32-bit ARM LE ELF, ET_EXEC, single RWX load segment; **file_offset = vaddr − 0x10000**.
- Table at **file 0x101a7c0** (in `.data.rel.ro`): **27 entries × 20 bytes** =
  `{data_ptr(vaddr), length_bytes, sample_rate(16000), channels(1), id}`.
- Found via `findsr.py` (scan data sections for `{rodata_ptr, plausible_len, known_samplerate}`).
- `table.py` dumps the table; `extract.py` exports all 27 as WAV for auditioning.
- id 2 and id 20 SHARE the same boot-sound data pointer. See the **Sound ID map** below.

## Sound ID map (this firmware's defaults)

What each of the 27 entries plays by default (extracted and identified by ear):

| id | default sound | id | default sound |
|---:|---|---:|---|
| 0 | touchscreen button click | 14 | "please format TF card before first use" |
| 1 | camera photo / snapshot | 15 | "format failed, please try again" |
| 2 | boot / startup | 16 | "format successful" |
| 3 | "Wi-Fi is on" | 17 | "memory card error" |
| 4 | "Wi-Fi is off" | 18 | "entering parking mode" |
| 5 | "front camera displayed" | 19 | shutdown |
| 6 | "rear camera displayed" | 20 | boot / startup (duplicate of id 2) |
| 7 | "screen turned on" | 21 | "rear camera is connected" |
| 8 | "screen turned off" | 22 | "rear camera is disconnected" |
| 9 | "audio on" | 23 | "recording started" (de-facto power-on sound) |
| 10 | "audio off" | 24 | "recording stopped" |
| 11 | "current video locked" | 25 | "timelapse recording started" |
| 12 | "emergency video" | 26 | "regular formatting reminder" |
| 13 | "factory reset" | | |

## Power button = lock the current clip

A second, independent patch built by `patch_lockbtn.py`. Apply it to a pristine `cardv`, or to
the sounds build so both patches are in one binary. **Verified on hardware.**

The camera can lock a clip — the FAT read-only bit, the `ATTR` 32 -> 33 that cmd 3015 reports —
but no Wi-Fi command reaches that code, and the dedicated lock key is dead while a phone is
linked. Meanwhile the screen button does nothing useful once "screen off after boot" is set. So
it gets repurposed.

### Where it goes

The screen button does **not** go through the key table `xSysMan_NormalKeyCB` uses for other
keys — patching that table changes nothing at all, which cost one flash to discover. Its branch
is self-contained:

```
0x1e284  UI_GetData(67)                 ; "key sound" flag
0x1e290  if 1 -> 0x1e44c: UISound_Play, then branch back to
0x1e294  SysGetLCDStatus() -> invert -> SysSetLCDStatus()     <-- the screen toggle
0x1e2a8  b 0x1e0f8                      ; return; no event is ever posted
```

Both flag-67 branches converge on `0x1e294`, so that is the hook:

```
0x1e294:  bl 0x3ac3c          (was SysGetLCDStatus/invert/SysSetLCDStatus)
0x1e298:  b  0x1e0f8

stub at 0x3ac3c, over the unreachable WiFiCmd_OnExeSetMovieTimelapse:
  movw r0,#0x0116 / movt r0,#0x1106     ; event 0x11060116
  movw r2,#0x0401 / movt r2,#0x1106     ; param 0x11060401
  mov  r1,#1
  b    Ux_PostEvent                     ; tail call - returns to 0x1e298
```

That is the same event and parameter the firmware's own "Custom1 Key" simulator (`0x0001b0a8`)
posts, and it ends in `UIFlowWndMovie_OnCustom1` -> `iamoviemulti_fm_set_ro_file`. 29 bytes
change, in those two places only; the script asserts the original bytes at both sites first and
then checks nothing else moved.

### What you get

- **Short press while recording:** the key sound plays, then the clip is locked and filed under
  `\ARPHA\Event` with `ATTR 33`, and the camera says "current video locked" (sound id 11).
- **Long press still powers the camera off** — it is not on this code path.
- **The short press no longer toggles the screen.** That is the trade.

Boot is untouched — the patched code only runs on a key press — so a mistake here cannot stop
the camera booting. The failure mode is a misbehaving button, recoverable by rolling back.

### Install

```sh
python patch_lockbtn.py --base cardv.final     # or cardv.bin for a pristine base
# on the PC
curl -u root: -T cardv.lockbtn ftp://192.168.1.254/cardv.lockbtn
# on the camera (telnet, root, no password) — make the backup first if you haven't
cp /usr/bin/cardv /mnt/sd/cardv.orig.bak
rm /usr/bin/cardv && cp /mnt/sd/cardv.lockbtn /usr/bin/cardv && chmod 755 /usr/bin/cardv
md5sum /usr/bin/cardv          # compare with what the script printed
sync && reboot
```

Test the long press **before** relying on the camera, and remember the Wi-Fi comes up off after
every power cycle.

Rollback: `cp /mnt/sd/cardv.orig.bak /usr/bin/cardv && sync && reboot`.

The Wi-Fi command reference that explains why locking has no command of its own is in
[`firmware-wifi-commands.md`](../firmware-wifi-commands.md).

## Files here
`camsh.py` (telnet driver) · `patch_final.py` (sound patch builder) · `patch_lockbtn.py`
(screen button = lock clip) · `patch_all.py`/`patch3.py` (variants) · `findsr.py` `table.py`
`extract.py` (RE tools).

Not included (git-ignored, per copyright / size): `cardv.bin` / `cardv.final` (proprietary
camera firmware) and `*.raw` / `*.wav` (audio clips). Pull `cardv` from your own camera and
supply your own sounds. The scripts read `cardv.bin` from their own folder — drop your pulled
copy next to them.
