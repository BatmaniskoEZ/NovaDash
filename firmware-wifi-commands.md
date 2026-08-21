# Wi-Fi command table - ARPHA D25 (NA51055), firmware Arpha-D25--V0.0.6-20230816TE

Extracted from `firmware-sound-mod/cardv.bin` (ARM32 ELF, vaddr = file offset + 0x10000).
The dispatch table lives at vaddr `0x01035054` as 20-byte records
`{cmd, event, handler, flags, mode}`; commands dispatched by event resolve through the
event->handler table at `0x01034e78`. Handler names are each function's own `__func__`
string, so they are the firmware's names, not guesses.

**61 commands exist. None of them locks or protects a file.**

| cmd | via | handler | firmware name | meaning |
|-----|-----|---------|---------------|---------|
| 1001 | direct | `0x0003ffc4` | `XML_GetPictureEnd` | take photo |
| 1003 | direct | `0x0003fc00` | `` | get freepicnum |
| 2001 | event | `0x0003b784` | `WiFiCmd_OnExeMovieRec` | record start/stop |
| 2002 | event | `0x0003b9d4` | `WiFiCmd_OnExeSetMovieRecSize` | video size |
| 2003 | event | `0x0003ba54` | `WiFiCmd_OnExeCyclicRec` | loop recording |
| 2004 | event | `0x0003bae8` | `WiFiCmd_OnExeMovieHDR` | HDR/WDR |
| 2007 | event | `0x0003afd0` | `WiFiCmd_OnExeSetMovieAudio` | record audio |
| 2009 | direct | `0x0003fc30` | `` | max record time |
| 2011 | event | `0x0003bee4` | `WiFiCmd_OnExeSetMovieGSesnorSensitivity` | g-sensor sensitivity |
| 2015 | event | `0x0003c3dc` | `WiFiCmd_OnExeMovieLiveviewStart` | liveview start/stop |
| 2016 | direct | `0x000401f0` | `` | current rec time |
| 2017 | event | `0x0003b35c` | `WiFiCmd_OnExeTriggerMovieRawEnc` | snapshot while recording |
| 2018 | direct | `0x0003f274` | `` | save snapshot |
| 2019 | direct | `0x0003e5ac` | `` | liveview format |
| 3001 | event | `0x0003a4d8` | `` | mode change |
| 3002 | direct | `0x0003d4b8` | `XML_QueryCmd` | list commands |
| 3003 | event | `0x0003a758` | `` | set SSID |
| 3004 | event | `0x0003a7e8` | `` | set password |
| 3005 | event | `0x0003aa44` | `` | date |
| 3006 | event | `0x0003aae8` | `` | time |
| 3008 | event | `0x0003a698` | `` | language |
| 3010 | event | `0x0003ab8c` | `` | format SD |
| 3011 | event | `0x0003a8c0` | `` | defaults |
| 3012 | direct | `0x0003f770` | `` | version |
| 3013 | event | `0x0003a8ac` | `` | FW update |
| 3014 | direct | `0x0003f880` | `XML_QueryCmd_CurSts` | settings dump |
| 3015 | direct | `0x0003e6d8` | `` | file list |
| 3016 | direct | `0x0003fbd8` | `` | ping |
| 3017 | direct | `0x0003fc74` | `` | free space |
| 3018 | event | `0x0003a904` | `` | reconnect wifi |
| 3019 | direct | `0x0004017c` | `` | battery |
| 3021 | event | `0x0003a6c4` | `` | save settings |
| 3022 | direct | `0x000401c0` | `` | hw capacity |
| 3023 | event | `0x0003a9cc` | `` | remove last user |
| 3024 | direct | `0x000402a4` | `` | SD status |
| 3025 | direct | `0x000404ac` | `` | filedesc URL |
| 3026 | direct | `0x000404dc` | `` | FW URL |
| 3028 | event | `0x0003a6d4` | `` | **undocumented** |
| 3029 | direct | `0x00040b14` | `` | SSID/passphrase |
| 3030 | direct | `0x00040bbc` | `` | video mode list |
| 3031 | direct | `0x00041678` | `XML_GetMenuItem` | menu items (str=key) |
| 3032 | event | `0x0003a648` | `` | STA network |
| 3033 | event | `0x0003a670` | `` | STA mode |
| 3034 | direct | `0x00041bb4` | `XML_AutoTestCmdDone` | autotest done |
| 3037 | direct | `0x0004034c` | `` | current mode status |
| 3038 | direct | `0x00040598` | `wlan0` | AP search |
| 4001 | direct | `0x0003e9c8` | `` | thumbnail |
| 4002 | direct | `0x0003e9c8` | `` | preview |
| 4003 | direct | `0x0003fce0` | `` | delete file |
| 4004 | direct | `0x0003fe3c` | `` | delete all |
| 4005 | direct | `0x0003f4d4` | `` | movie file info |
| 5001 | direct | `0x00041cb0` | `` | upload |
| 6788 | event | `0x0003bdc4` | `WiFiCmd_OnExeSetMovieDateImprint` | **undocumented** |
| 6802 | event | `0x0003bf78` | `WiFiCmd_OnExeParkingGuard` | **undocumented** |
| 6807 | event | `0x0003b0bc` | `WiFiCmd_OnExeSetVolumn` | **undocumented** |
| 6809 | event | `0x0003b134` | `WiFiCmd_OnExeSetFrequence` | **undocumented** |
| 6811 | event | `0x0003b21c` | `WiFiCmd_OnExeWiFiConnect` | **undocumented** |
| 6812 | event | `0x0003b248` | `WiFiCmd_OnExeWiFiDisonnect` | **undocumented** |
| 9945 | direct | `0x000420d8` | `` | **undocumented** |
| 9968 | event | `0x0003c0e8` | `WiFiCmd_OnExeParkingTimelapse` | **undocumented** |
| 9999 | direct | `0x00041fe4` | `` | **undocumented** |

## Why clip locking can't be done over Wi-Fi on this firmware

The camera can lock clips - its own menu has lock all / lock current / unlock - but that path
is UI-only:

| what | where |
|---|---|
| lock current clip | `UIFlowWndMovie_OnCustom1` @ `0x0004ab34` (also `UIFlowWndLCDPro_OnCustom1` @ `0x0004e088`) |
| lock all / unlock all | `MenuCustom_ProtectAll` @ `0x0005817c`, `MenuCustom_UnProtectAll` @ `0x00058218` |
| the primitive that does it | `iamoviemulti_fm_set_ro_file` @ `0x00162c9c` - sets the FAT read-only attribute (the `ATTR` 32 -> 33 that cmd 3015 reports) |
| what triggers it | the `KEY_CUSTOM1` key event - the physical/touch button, and the G-sensor (`Gsensor Trigger Ux_PostEvent KEY_CUSTOM1`) |

Walking the call graph upward from `iamoviemulti_fm_set_ro_file` and from the two `OnCustom1`
handlers reaches no entry in the Wi-Fi command table, and the table itself contains no
protect/lock command: the only file operations exposed are 3015 (list), 4001 (thumbnail),
4002 (preview), 4003 (delete), 4004 (delete all), 4005 (info) and 5001 (upload).

So no command id will ever work, and probing for one is wasted effort. The remaining routes,
both using the root telnet/FTP access already documented in `firmware-sound-mod/README.md`:

1. **Set the read-only attribute from a root shell** - the SD card is mounted at `/mnt/sd`, so
   `chmod a-w /mnt/sd/ARPHA/Normal/<CLIP>.MP4` should map onto the FAT read-only bit that the
   file manager honours. Verify by re-listing with cmd 3015 and watching `ATTR` go 32 -> 33.
   Untested against the live camera.
2. **Patch `cardv`** to route a spare Wi-Fi command to the lock path, the same way the custom
   sounds were patched in. Invasive, and it has to be redone per firmware build.

## Note: two command ids look swapped in NovaDash

`NovaCommands.kt` has `FREQUENCY = 6788` and `DATETIME_STAMP = 6809`. The firmware says the
opposite - each handler names itself:

| cmd | handler | firmware name |
|---|---|---|
| 6788 | `0x0003bdc4` | `WiFiCmd_OnExeSetMovieDateImprint` |
| 6809 | `0x0003b134` | `WiFiCmd_OnExeSetFrequence` |

(6807 -> `WiFiCmd_OnExeSetVolumn` agrees with the app, so the table reading is sound.)

**Confirmed in use, not just on paper.** With the old mapping, picking 50 Hz sent `6788 par=0`,
which the camera read as "date imprint off" - and the date stamps disappeared from the clips.
The inverse held too: switching the date stamp on sent `6809 par=1`, quietly selecting 60 Hz,
so a camera whose stamp was ever toggled is probably sitting on 60 Hz. Fixed in NovaDash as of
commit `1b83379`.

## Previously undocumented ids, now named

| cmd | firmware name |
|---|---|
| 3028 | (event 0x14020028) |
| 6802 | `WiFiCmd_OnExeParkingGuard` |
| 6811 | `WiFiCmd_OnExeWiFiConnect` |
| 6812 | `WiFiCmd_OnExeWiFiDisonnect` (sic) |
| 9945 | `XML_GetVideoRMCInfo` |
| 9968 | `WiFiCmd_OnExeParkingTimelapse` |
| 9999 | raw `custom=1&cmd=` / `&str=` re-parser |

`6820`, which NovaDash offers as an experimental probe, is not in the table at all.

## Parking mode: present, and reachable over Wi-Fi

The firmware implements a full parking monitor, not just a menu label:

| piece | evidence in `cardv` |
|---|---|
| trigger | `Eva21_ACCParkingModeFunc`, `bAccStatusChange`, `UI_GetData(FL_MOVIE_PARKING_GUARD) = %d usb =%d` - it watches ACC / USB power, so it needs constant power to be useful |
| behaviour | "Enter Parking Monitor, close LCD", "close wifi to PRIMARY_MODE_MOVIE", "Reached parking monitor time, shutdown begin" |
| time limit | `Sys_GetParkingMonitorTime()`, `UI_CalcTimeDiff(IN_DATETIME_PARKING)` |
| g-sensor | `JXW_Gsensor_ParkingMode gsensor (GThreshold = %d)` - its own threshold while parked |
| warning UI | `UIFlowWndWrnParkingGuard` |

Menu keys and options (usable with cmd 3031, `str=<key>`):

- `ParkingMode` -> `CollisionDetectionParkingMode`, `NormalRecordingParkingMode`,
  `TimeLapseParkingMode`, `MotionDetectionParkingMode`
- `ParkingModeWorkingTime` -> `ParkingModeWorkingTime8H` / `12H` / `24H` / `48H` / `Always`
- `TimeLapseParkingModeWorkingTime`

Commands: **6802** `WiFiCmd_OnExeParkingGuard` (UI flag 165 = `ParkingMode`) and **9968**
`WiFiCmd_OnExeParkingTimelapse` (flag 172 = `TimeLapseParkingModeWorkingTime`, i.e. how long it
keeps watching - the handler name is misleading). Both answer `ERR: not movie mode` unless the
camera is in movie mode.

The `par` values don't have to be guessed or probed: the menu tables in `.data` carry them.

| 6802 (mode) | | 9968 (keep watching for) | |
|---|---|---|---|
| 0 | off | 1 | 8 h |
| 1 | CollisionDetectionParkingMode | 2 | 12 h |
| 2 | NormalRecordingParkingMode | 3 | 24 h |
| 3 | TimeLapseParkingMode | 4 | 48 h |
| 4 | MotionDetectionParkingMode | 5 | always |

Note 9968 has no 0 - the shortest setting is 8 h.

### The menu tables, and a third proof of the 6788/6809 swap

At `v0x010458f0` sits a table of 12-byte records `{flag_id, key_string, options_table}`, where
each options table is `{index, name}` pairs terminated by `0xffffffff`. It maps every menu key
to the `FL_*` id that the command table's 5th field also carries, which lines the two up:

| flag | menu key | command |
|---|---|---|
| 35 | `LoopRecording` | 2003 |
| 38 | `MOVIE_DATEIMPRINT` | **6788** |
| 69 | `LightSourceFrequency` | **6809** |
| 80 | `G-sensor` | 2011 |
| 127 | `SpeakerVolume` | 6807 |
| 165 | `ParkingMode` | 6802 |
| 172 | `TimeLapseParkingModeWorkingTime` | 9968 |
| 17 | `PIP_VIEW` | 3028 (previously undocumented - see below) |

6788 -> flag 38 -> `MOVIE_DATEIMPRINT` and 6809 -> flag 69 -> `LightSourceFrequency`: a third
independent source agreeing that the two were swapped in the app. `LightSourceFrequency`'s
options are `0 = 50HZ, 1 = 60HZ`, which also confirms NovaDash's existing 50/60 ordering.

### Field 5 of each table record is the UI flag id

`{cmd, event, handler, flags, flag_id}` - the last word is the `FL_*` id the command reads and
writes. 6802 -> 165 lines up with the `FL_MOVIE_PARKING_GUARD` debug string, which is what
confirms the reading. Others: 2003 -> 35, 6788 -> 38, 6809 -> 69, 2011 -> 80, 6807 -> 127,
9968 -> 172.

### 21 event handlers no command can reach

Including `WiFiCmd_OnExeSetMovieTimelapse` (event `0x14020048`, handler `0x0003ac3c`): the code
is there, but the command table routes nothing to it. Same shape of problem as the file lock -
capability present, no id wired to it.

## cmd 3028 = screen layout (PIP)

Flag 17, menu key `PIP_VIEW`, option table at `v0x01045610`. The names encode which sensor and
how big it is - sensor 1 is the front camera, 2 the rear, `B` big, `S` small, `F` full:

| par | firmware name | meaning |
|---|---|---|
| 0 | `1T1F` | front only, full screen |
| 1 | `2T2F` | rear only, full screen |
| 2 | `1T1B2S` | front big, rear in a small inset |
| 3 | `1T1S2B` | rear big, front in a small inset |

It drives the display pipeline (`MovieExe_OnMoviePipStyle`, `PipView_OnDraw_2sensor`, and the
camera's own `UIFlowWndMovie_Static_PIPVIW` touch target), so it changes the screen and the live
view - not the recordings, which stay two separate files per clip.

## cmd 3010 (format SD): why it only works with the camera's Wi-Fi off

`WiFiCmd_OnExeFormat` @ `0x0003ab8c` doesn't format anything itself - it validates and posts a
background event, then returns success immediately. Which event depends on `par`:

| par | posts | handler |
|---|---|---|
| absent or 0 | `BKG_PostEvent(7)` | `BackgroundFormatCard` @ `0x00035878` |
| 1 | card-present check, then `BKG_PostEvent(6)` | `BackgroundFormat` @ `0x000352ac` |
| anything else | nothing - answers status -33 ("err param") | - |

`BackgroundFormatCard` is the real card wipe, and it is done with Linux tools:

```
umount /mnt/sd                              (3 attempts, 100 ms apart,
                                             logs "system() umount failed")
mkexfatfs -n ARPHA -s 128 /dev/mmcblk0
mount -t exfat /dev/mmcblk0p1 /mnt/sd
```

**That is the whole explanation for "I have to turn the camera's Wi-Fi off".** The camera's HTTP
server serves clips straight off `/mnt/sd`, so while Wi-Fi is up the card is in use and `umount`
fails; the format cannot proceed. Switching the camera's Wi-Fi off releases the mount and the
queued format runs. Note the command has already answered status 0 by then, so a successful
reply says nothing about whether the card was erased.

`BackgroundFormat` (par=1) is a different, lighter path through the firmware's own FileSys layer
(the same `0xf63a8` that `BackgroundFormatCard` calls *after* remounting, to set up `A:\ARPHA`).
It is what the camera's own menu posts. Whether it erases a card with the Wi-Fi still up is
untested - worth trying with the custom-command box (`cmd=3010`, `par=1`) while watching the
camera, before changing what the app sends.

### Background events, and one more place the lock lives

The background dispatch table is at `v0x01044924`, records `{handler, event_id}`:

| evt | handler |
|---|---|
| 1 | (delete, from the playback confirm menus) |
| 2 | `Background_DeleteAll` |
| 6 | `BackgroundFormat` |
| 7 | `BackgroundFormatCard` |
| 10 | file protect - posted by `UIMenuWndPlayProtect_Menu_OnTouchPanelClick` |
| 11 | `BackgroundSetProtect` - posted by `SetupExe_OnSysReset` |
| 12 | FW update - posted by **cmd 3013** |
| 15 | `BackgroundFWUpdate` |

Event 10 is the playback menu's protect action, and no Wi-Fi command posts it - which confirms
the earlier finding through a second, independent route: the only command that reaches
`BKG_PostEvent` at all is 3010 (events 6/7) and 3013 (event 12).

## Could the app show a "lock this clip" button?

No - checked in both directions:

- **Backward:** from `iamoviemulti_fm_set_ro_file`, `ImageApp_MovieMulti_TrigEMR`, the two
  `OnCustom1` handlers and background events 10/11, walking callers upward reaches no entry in
  the command table.
- **Forward:** taking all 61 command handlers and following their calls six levels deep gives a
  closure of 504 functions. None of the lock primitives is in it.

(Both follow direct `bl` edges; calls through function pointers are invisible to either. The
command table having no protect entry, and `BKG_PostEvent` being reached only by 3010 and 3013,
are the stronger arguments.)

There is also a detail worth knowing for anyone holding the phone and the camera at once:
**while a phone is linked, the camera's own lock button does nothing.** The window in front is
then `UIMenuWndWiFiMobileLinkOK`, whose `_OnCustom1` handler (`0x0005b49c`) is a stub - its only
call is `Ux_DefaultEvent`. The handler that locks, `UIFlowWndMovie_OnCustom1`, belongs to the
movie window, which isn't in front at that moment.

Routes that would actually work, in increasing order of effort:

1. **Telnet from the phone.** The camera runs a passwordless root telnet and mounts the card at
   `/mnt/sd`, so `chmod a-w /mnt/sd/ARPHA/Normal/<CLIP>.MP4` should set the same read-only bit
   the file manager honours. An app can open a socket and do this itself. Untested.
2. **Patch `cardv`.** Either give `UIMenuWndWiFiMobileLinkOK_OnCustom1` the movie window's
   behaviour (makes the camera's button work while linked), or repoint an unused command's
   handler at a few new instructions that post background event 10 (makes it work from the app).
   The second needs new code in `.text`, not just a byte swap.

## Patching in a lock command: what it would take

The pieces are all identified, so this is a real option rather than a hope.

| piece | value |
|---|---|
| message that locks | `0x11060121` - the movie window's map at `v0x010475d8` binds it to `UIFlowWndMovie_OnCustom1` (`0x0004ab34`); records are `{handler, msg_id}` |
| poster | `Ux_PostEvent` @ `0x000dea6c` |
| spare space | `WiFiCmd_OnExeSetMovieTimelapse` @ `0x0003ac3c` - a whole function nothing reaches (its event `0x14020048` is in no command record) |
| command to hijack | e.g. 9945 (`XML_GetVideoRMCInfo`) - one word in the command table repointed at the stub |

The stub is only a handful of instructions: load `0x11060121`, call `Ux_PostEvent`, return 1.
Both edits are byte patches into existing space, the same shape of change as the sound mod, and
`cardv.orig.bak` on the SD card is still the rollback.

Two things to settle before trying it on hardware:

1. **`Ux_PostEvent`'s signature** - how many parameters it wants and whether the message needs a
   target window handle.
2. **Which window is in front.** While a phone is linked the camera shows
   `UIMenuWndWiFiMobileLinkOK`, not the movie window - which is exactly why the physical lock
   button is dead in that state. A message aimed at the movie window may not be dispatched. If
   that turns out to be the blocker, the alternative is to patch
   `UIMenuWndWiFiMobileLinkOK_OnCustom1` (`0x0005b49c`, currently a stub that only calls
   `Ux_DefaultEvent`) to do what the movie window's handler does - though that fixes the
   camera's own button rather than giving the app a command.

## Better idea: repurpose the screen/power button to lock the clip

This avoids the window problem entirely - you press it while driving, with no phone linked, so
the movie window is in front and its `OnCustom1` handler is live.

The two halves are both located:

| piece | where |
|---|---|
| what the button does now | `xSysMan_NormalKeyCB` @ `0x0001e068` calls `SysSetLCDStatus` (`0x0001cfa0`) - that's the screen going off |
| what we want instead | `Ux_PostEvent(0x11060116, 1, 0x11060401)` - exactly what the "Custom1 Key" debug helper at `0x0001b0a8` does, and what ends in `UIFlowWndMovie_OnCustom1` -> `iamoviemulti_fm_set_ro_file` |

So the patch is: in the key callback, replace the LCD-off call (and its argument setup) with
that post - either inline if the instruction budget fits, or as a `bl` to a stub written over
the dead `WiFiCmd_OnExeSetMovieTimelapse` at `0x0003ac3c`.

Still to confirm before flashing:

1. **Which branch is the short press.** `xSysMan_NormalKeyCB` handles keys generally; the
   long-press power-off path must be left alone, or the camera loses its off switch.
2. Whether the same callback serves other buttons, so the patch doesn't take one of those out.

Test on the bench on USB power first, and `cardv.orig.bak` on the SD card is the rollback.

### The patch that works (verified on hardware)

**The key table is the wrong place.** The screen button never reaches that lookup - an earlier
version of this document proposed patching record 0 of `SetKeyMap(0x01042f74, 18)`, that patch
was flashed, and it did precisely nothing. Its branch (`r2 == 0x80000`) is self-contained:

```
0x1e284  UI_GetData(67)                 ; "key sound" flag
0x1e290  if 1 -> 0x1e44c: UISound_Play, then branch back to
0x1e294  SysGetLCDStatus() -> invert -> SysSetLCDStatus()      <-- the screen toggle
0x1e2a8  b 0x1e0f8                      ; return. No event is ever posted.
```

Both flag-67 branches converge on `0x1e294`, so that is the hook. Replace the toggle with a call
to a stub that posts the Custom1 key event:

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
posts, and it ends in `UIFlowWndMovie_OnCustom1` -> `iamoviemulti_fm_set_ro_file`.

**Confirmed on a real camera:** a short press locks the clip being recorded - it lands in
`\ARPHA\Event` with `ATTR 33`, exactly like a G-sensor emergency, and the camera says "current
video locked" (sound id 11). A long press still powers the camera off; long-press is not on this
code path. The screen no longer toggles on a short press, which is the trade.

Boot is untouched - the patched code only runs on a key press - so a mistake here cannot stop
the camera booting. Keep a copy of the working binary on the SD card first; recovery is
`cp /mnt/sd/cardv.orig.bak /usr/bin/cardv && sync && reboot`.

## Why some settings are silently ignored (parking mode among them)

Setting parking mode over Wi-Fi appeared to do nothing. The cause is not the command id and not
a firmware bug — it is a precondition nobody documents.

`WiFiCmd_OnExeParkingGuard` @ `0x0003bf78` calls `0x0003b5c8` before doing any work, and that
is a one-line wrapper around **`ImageApp_MovieMulti_IsStreamRunning`**:

```
if (stream running)  -> bail: FlowMovie_CheckReOpenItem, post 0x10010002, return status 0
else                 -> UI_SetData(166, par); post message 0x14001400
```

Message `0x14001400` is bound at `v0x01043604` to `v0x00027260`, which does exactly one thing:
`UI_SetData(165, value)` — flag 165 being `FL_MOVIE_PARKING_GUARD`, which
`Eva21_ACCParkingModeFunc`, the parking icon and the record-stop hook all read. So the chain
works; it just never runs while the camera is recording, and a dashcam records continuously.

The bail-out answers **status 0**, so the app saw success every time.

### Which commands are gated

| needs the recorder stopped | applies while recording |
|---|---|
| 6802 parking, 9968 parking time, 2003 loop, 2002 resolution, 2004 HDR, 2011 g-sensor, 6788 date stamp | 3028 PIP, 2007 audio, 6809 frequency, 6807 volume |

That list is worth keeping: every gated command has been quietly ignored whenever the app sent
it while the camera was rolling. NovaDash now stops the recorder around them and restarts it
afterwards, instead of relying on the Settings tab's browsing pause (which follows the app's own
belief about the recording state, and can be stale).

An earlier version of this document claimed cmd 6802 wrote the wrong UI flag and proposed a
one-byte patch at file `0x02c02c`. That was wrong — the flag write via the posted message is
correct, and no patch is needed.

### Setting parking vs. parking engaging — two different gates

They are worth keeping apart:

- **Setting the option** is gated only on movie mode plus the recorder being stopped. ACC has
  nothing to do with it — `WiFiCmd_OnExeParkingGuard` makes exactly two checks (the mode test at
  its head, then `ImageApp_MovieMulti_IsStreamRunning`) before writing the flag.
- **Parking engaging** is `Eva21_ACCParkingModeFunc`, a polled function that reads flag 165
  together with the power state (`UI_GetData(FL_MOVIE_PARKING_GUARD) = %d usb =%d`,
  `bAccStatusChange`) and then does the work the strings describe: "Enter Parking Monitor, close
  LCD", "close wifi to PRIMARY_MODE_MOVIE", "Reached parking monitor time, shutdown begin". That
  half genuinely needs the ACC/constant wiring.

So a bench test can confirm the setting sticks, but only a real ignition-off can confirm the
behaviour.

### Four places can write the parking flag

Everything that sets it posts message `0x14001400`:

| poster | route |
|---|---|
| `WiFiCmd_OnExeParkingGuard` | cmd 6802 — the Wi-Fi route |
| `Movie_MenuCallback` | the camera's own menu |
| `v0x000473f8`, `v0x0004b6dc` | internal (both also read flags 165/166) |

`Movie_MenuCallback` means the firmware does carry a menu path for this, even though the D25's
menu appears not to show a parking entry. Since the menu route is unavailable in practice, cmd
6802 with the recorder stopped is the only way in.

## Verified against the real camera (Arpha-D25--V0.0.6-20230816TE)

PC on the camera's Wi-Fi, `curl` plus the telnet root shell. The firmware string matches the
`cardv` this document was written from, so these confirm the analysis rather than merely
agreeing with it.

| claim | result |
|---|---|
| the 61-command table read out of `cardv` | **exactly** the camera's own cmd 3002 list - no differences either way |
| cmd 2007 (audio) is not gated on recording | confirmed: applied instantly while recording |
| cmd 3005/3006 report the clock | **no** - both answer `Status 0` with no `<String>` and no `<Value>` at all |
| the camera's menu has no parking entry | confirmed: cmd 3031 ignores its `str` key and returns the whole menu - loop, HDR, audio, date stamp, g-sensor, language, volume, frequency. No parking, no PIP, no time-lapse |
| `chmod a-w` locks a clip | **confirmed** - see below |

### The clock is never reported, so the presumption is permanent

`cameraClock()` needs a payload from 3005/3006; this firmware returns neither `<String>` nor
`<Value>`. So `ClockSyncStore` can never record an era on this camera and
`ClockPresumption.of()` will always fall through to `DEFAULT_OFFSET_MS`. The -1h presumption
isn't a stopgap here - it is the only thing that will ever match a moment to a clip.

### Three settings the camera never reports back

The cmd 3014 dump contains 2016, 2001, 2002, 2003, 2004, 2007, 6788, 2011, 3008, 3033, 6807,
6809, 9945 - and **not 6802, 9968 or 3028**. Parking mode, parking working time and PIP layout
are write-only over Wi-Fi: they can be set but never read. Any UI that shows their state is
showing a guess.

### Locking a clip over telnet works

The SD card is mounted `vfat` with `fmask=0000`, so `chmod` maps onto the DOS read-only bit:

```
chmod a-w /mnt/sd/ARPHA/Normal/<clip>.MP4   ->  cmd 3015 reports ATTR 33
chmod a+w /mnt/sd/ARPHA/Normal/<clip>.MP4   ->  cmd 3015 reports ATTR 32
```

Verified in both directions on a real clip. ATTR 33 is exactly what the camera reports for its
own `\Event` clips, and the bit survives `sync; echo 3 > /proc/sys/vm/drop_caches`, so it is on
the card rather than in the inode cache. The camera's file listing lags a few seconds behind the
change.

**This is the route to a working Lock button.** No Wi-Fi command can protect a clip, but a
socket to port 23, a root login with no password and one `chmod` can - from the phone as easily
as from a PC.

## Reading the camera's live settings out of process memory

`cardv` logs to `/dev/console` (a UART), which telnet can't tail, and three settings never
appear in the cmd 3014 dump. But the binary is not PIE and the flags live in one table, so they
can be read straight out of the running process over the telnet shell:

`UI_GetData(id)` (`0x00046108`) is `*(table + id*4 + 0xa4)`, where `table` is the pointer stored
at `0x010417d4` (GOT). On this camera that pointer reads `0x01092d34`, so:

```sh
PID=$(pidof cardv)
# flag N:
dd if=/proc/$PID/mem bs=4 skip=$(( (0x01092d34 + 0xa4 + N*4) / 4 )) count=1 2>/dev/null   | hexdump -e '1/4 "%08x
"'
```

Sanity-checked against the 3014 dump on a live camera - audio 37, date stamp 38, frequency 69,
g-sensor 80, loop 35, HDR 31 all matched exactly. This is how the write-only settings (165
parking, 172 parking working time, 17 PIP) can be verified at all.

## The recording gate, proved on hardware

With flag 165 readable, the gate is no longer an inference:

| action | camera's answer | flag 165 |
|---|---|---|
| `6802 par=4` while recording | `Status 0` | unchanged at 1 - **silently ignored** |
| `6802 par=4` with the recorder stopped | `Status 0` | **4** - applied |

Both answer success. The only way to tell them apart is to read the flag. `9968` behaved the
same way: sent with the recorder stopped it left flag 172 = 3 (24 h).

So parking mode *is* reachable over Wi-Fi on a stock camera - it just has to be sent with
recording stopped, which is what `SettingsRepository.withRecordingStopped` now does.

### The parking icon is not a usable indicator

`FlowMovie_IconDrawParking` (`0x0004db78`) checks `0x0006a744()` *before* it looks at flag 165,
and hides the icon when that returns non-zero. So an absent icon does not mean parking is off -
on a camera with parking enabled (flag 165 = 1) and USB power connected, no icon is drawn.

## What parking mode actually does when the ignition drops

All of it lives in `Eva21_ACCParkingModeFunc`, and the branch on ACC going low is:

| flag 165 | what happens |
|---|---|
| 0 (off) | `"no Parking Monitor, shutdown begin"` - the camera powers down |
| non-zero | `"Enter Parking Monitor, close LCD"` - the LCD is switched off and the camera **stays running** |

It then runs until the working time (flag 172) elapses, at which point
`"Reached parking monitor time, shutdown begin"` powers it down for real. So the camera never
"wakes up" on an impact - it was never asleep. It keeps running on constant power with a dark
screen, for up to the configured 8/12/24/48 hours or indefinitely on "always".

The mode value selects what it does while parked:

| par | mode | behaviour in the code |
|---|---|---|
| 1 | collision | takes none of the pipeline-restart branches - carries on with normal loop recording, leaving the g-sensor to flag an impact the way it always does |
| 2 | normal recording | goes through `FlowMovie_CheckReOpenItem` and restarts the movie pipeline |
| 3 | time-lapse | same restart path, in time-lapse |
| 4 | motion | same restart path, and additionally sets flag 36 (`MOVIE_MOTION_DET`) - motion detection on |

A g-sensor hit while parked produces the same result as one while driving: the clip is locked
and filed under `\ARPHA\Event` with `ATTR 33`. There is also a parking-specific g-sensor
threshold (`JXW_Gsensor_ParkingMode gsensor (GThreshold = %d)`, `UI_DetParkGsensor`, which reads
flag 166 - the flag cmd 6802 sets as a side effect).

Read from the code, not observed. The test is to leave it parked and see whether an `\Event`
clip appears. **Note the battery implication:** collision mode keeps the camera fully powered
and recording for the whole working time, so it depends on the hardwire kit's low-voltage
cutoff, not on the camera being idle.
