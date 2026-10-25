# note4-phonebot

Turn a rooted Samsung Galaxy Note 4 (SM-N9108V, Android 6.0.1) into the brain of a
robot. The phone does camera vision + IMU sensing + movement control, and streams
joint targets to an onboard controller that drives the motors. Architecture
follows UCLA **PhoneBot** (arXiv 2610.08737); the phone is the brain, the
body is just an actuator platform.

Built and verified on the real device over ADB. Everything runs on the
phone — no PC orchestration.

## What was done to this phone

1. **Rooted** — `su` works (`uid=0`). The shell user alone is locked out of
   `pm disable-user`, but root can.
2. **Backed up** — package list, disabled list, `getprop`, settings, and a full
   `dumpsys package` dump, kept locally (not in this repo: device identifiers).
3. **Debloated** — 130+ Samsung / China-carrier system packages + all 22
   user-installed apps disabled with `pm disable-user --user 0` (never
   uninstalled). Survives reboot. Kept the phone a phone: dialer, messages,
   contacts, settings, camera, gallery, clock, keyboard, Play + GMS,
   SystemUI, and the S Pen / S Note / Air Command suite.
4. **Sped up** — all three animation scales = 0 (instant UI); screen stays
   awake while charging.
5. **Custom dark wallpaper** written to the system wallpaper file with root.
6. **Termux + Linux toolset** — legacy Termux v0.79 (last Android 6 build) +
   Termux:API, pointed at the Bzi-Han community mirror of the 2019
   package snapshot (the official `termux.net`/Bintray repos died in 2020).
   Verified working: bash, coreutils, curl 7.67, wget 1.20, openssh 8.1p1,
   nano 4.6, grep, gawk, sed, tar, procps, net-tools, tree, util-linux, dpkg/apt.
7. **RobotBrain app** — a custom Android app (`com.hack.note4.robot`,
   ~17 KB, built from source with the SDK, signed, sideloaded) that is the
   robot brain. It does NOT auto-start on reboot (you tap the icon to start it).

## The RobotBrain app

A single Android activity, plain Java, no dependencies, `minSdk 23`. It:

- holds the back camera open as the **eye** (continuous preview → JPEG via `YuvImage`)
- reads **every sensor** continuously (accelerometer, gyroscope, magnetometer, proximity, light, barometric pressure, gravity, linear acceleration, rotation vector)
- runs a **decision loop** at ~30 Hz: phototaxis (walk toward the brighter side / a lit person) with safety overrides (proximity = stop, tilt = stop)
- maps the decision to a high-level action (STAND / WALK / TURN_LEFT / TURN_RIGHT / STOP)
- runs an open-loop **gait controller** (standing + IMU ankle balance + step-in-place; turning uses the waist servo) producing 13 joint targets
- streams the 13 joint targets to the onboard controller over **Wi-Fi (UDP 4242)**
- serves a tiny **HTTP API** on the LAN: `/see` (camera JPEG), `/sensors` (JSON), `/controller?ip=<ESP32-ip>`

The RL-trained walking policy from the PhoneBot paper is research work (sim + training);
this is the reproducible platform with an open-loop gait as the starting point.
Swapping in a trained policy later is a drop-in change to `Gait.java`.

## Onboard controller (you build)

An **ESP32 + PCA9685** 16-channel PWM driver drives 13 servos (6 per leg + 1 waist).
The phone talks Wi-Fi; the ESP32 firmware (`robot/esp32/PhoneBot.ino`) receives
the UDP joint targets and drives the servos. ~$150 of hardware with a
3D-printed frame; full bill of materials and wiring in `robot/HARDWARE.md`.

## Repo map

| Path | What |
|---|---|
| `robot/` | RobotBrain app: `AndroidManifest.xml`, `src/`, `build.ps1`, `PLAN.md`, `HARDWARE.md` |
| `robot/esp32/PhoneBot.ino` | ESP32 firmware: Wi-Fi UDP → PCA9685 → 13 servos |
| `launcher/` | CleanHome — the first custom home (a stepping stone; optional) |
| `termux/` | Setting up Termux + a Linux toolset on Android 6 |
| `phone-cleanup/` | The debloat list + a one-command restore script |

## How to use it

1. Flash `robot/esp32/PhoneBot.ino` to the ESP32 (put your Wi-Fi SSID/password in first).
   The serial monitor prints the ESP32's IP.
2. Build and install the brain on the phone:
   `powershell -ExecutionPolicy Bypass -File robot/build.ps1`
   (it installs but does not start it).
3. Tap the **RobotBrain** icon, then open
   `http://<phone-ip>:8080/controller?ip=<ESP32-ip>` so the brain knows where to send.
4. The phone's camera + IMU now decide the gait and stream 13 joint targets to the ESP32.

## How to undo it

`powershell -ExecutionPolicy Bypass -File phone-cleanup/restore.ps1` re-enables
every disabled package, restores the stock home, and resets the animation scales.
Then `adb uninstall com.hack.note4.robot` (and `com.hack.note4.launcher` if used).

## Comparison: OpenBot vs PhoneBot

This project follows the PhoneBot architecture. How the two compare:

| | OpenBot (Intel, 2020) | PhoneBot (UCLA, 2026) — **this repo** |
|---|---|---|
| Body | Wheeled car, 4 DC motors | Bipedal humanoid, 13 servos |
| Cost | ~$50 | ~$150 (3D-printed frame) |
| Phone ↔ body | USB serial → Arduino | Wi-Fi → ESP32 + PCA9685 |
| Locomotion | Drives (no balance) | Walks (must balance) |
| Policy | Learned driving from demonstrations | RL walking in sim (scaffolded here) |
| Difficulty | Moderate | Hard |
| Sensors | Phone cam+IMU + wheel odometry + sonar | Phone cam+IMU only |

The phone's role is identical in both: it is the brain (camera + IMU + compute).
OpenBot is the cheap, easy, wheeled entry point; PhoneBot is the ambitious, bipedal
research platform. This repo implements the PhoneBot path.

## Licence

MIT. The Termux APKs are not included (get them from the Internet Archive; see
`termux/README.md`). No prebuilt APKs or signing keys are committed — build from source.
