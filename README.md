# NovaDash

A clean, native Android replacement for the stock **ARPHA Vision / AZDOME** dashcam app,
targeting **Novatek NA51055 (NT96658)** cameras. No cloud accounts, no telemetry, no dead
multi-chipset code — just local control over the camera's Wi-Fi API.

## Features
- **Live view** - RTSP preview (front/rear), start/stop recording, take photo.
- **Files** - browse SD-card clips/photos with thumbnails, filter (all/video/event/photo),
  download to phone, delete, and play back (streamed or local).
- **Settings** - Wi-Fi SSID/password, record-audio toggle, "mute voice & beep", save to
  camera, format SD, and an experimental panel to probe undocumented firmware commands.
- **Offline/Online mode** - Online for when on dashcam wifi, offline for when not - offline works with locally downloaded videos only, markers, online - connects to dashcam.
- **Android Auto support** - make markers with tags (configurable in settings, can also be marked on phone without AA) on android auto screen, that will then highlight clips or download clips around that time range.

## Protocol
The camera exposes three transports (all local, on the camera's Wi-Fi AP):
- **Control API** — `http://192.168.1.254/?custom=1&cmd=<id>[&par=][&str=]` → XML.
- **Live video** — `rtsp://192.168.1.254/liveRTSP/av4` (front; `av5` rear, unverified).
- **Events** — TCP `192.168.1.254:8192`, camera-pushed status notifications.

Command IDs and response schemas were reverse-engineered from the decompiled stock app and
the docs in the repo root (`../nt-web-api-commands.txt`, `../webapi-error-codes.txt`) and
confirmed against a real camera. See `net/NovaCommands.kt` and `net/NovaStatus.kt`. (idk if I should publish this - api commands and error codes are from [here](https://github.com/nutsey/novatek-web-api-commands))

## TODO/Missing for me/maybe I'll do them later
- **GPS export from videos** - rn can be exported with exiftool `exiftool -ee -G3 -s -api LargeFileSupport=1 filename.mp4`

## Build
Requires JDK 17 and the Android SDK (platform 35). From this directory:

```bash
./gradlew assembleDebug        # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # unit tests (parsing, error mapping, NMEA)
./gradlew installDebug         # install onto a connected device
```

Stack: Kotlin 2.0, Jetpack Compose (Material 3), Hilt, Retrofit/OkHttp + Simple-XML,
media3 ExoPlayer (RTSP + local), Coil, osmdroid. min SDK 24 / target 35.

## Architecture
```
net/    NovaApi (Retrofit) · NovaClient (single-flight dispatcher) · NotifyClient (:8192)
        WifiGate (bind process to camera AP) · model/ (Simple-XML responses)
data/   CameraRepository · FileRepository · SettingsRepository (+ domain models)
gps/    NmeaParser · GpsExtractor
ui/     connect · live · files · settings · map · player  (Compose + per-screen ViewModels)
```
