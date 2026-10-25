# PhoneBot (Note 4) — plan, aligned to UCLA PhoneBot (arXiv 2610.08737)

A small bipedal humanoid that uses the Galaxy Note 4 as its torso/brain. The phone does
camera vision + IMU sensing + movement control, and sends joint targets over Wi-Fi
to an onboard controller that drives 13 servos.

## Architecture

```
[Note 4 = brain, mounted on the torso]
   camera (eye)  -> vision: track a person, detect obstacles
   IMU (accel+gyro+mag) -> balance, orientation
   gait controller -> 13 joint angle targets (0-180 deg)
   Wi-Fi (UDP) -> joint targets -> [ESP32] -> [PCA9685] -> 13 servos
```

The phone talks Wi-Fi (not USB). The ESP32 + PCA9685 is the onboard computer.

## Hardware (bill of materials)

| # | Part | Why | Cost |
|---|---|---|---|
| 1 | ESP32 | Onboard controller: receives Wi-Fi joint targets, drives servos | $6 |
| 2 | PCA9685 16-ch PWM driver | Drives up to 16 servos from ESP32 over I2C | $5 |
| 3 | 13x MG90S (or DS3218) servos | 6 per leg (hip yaw/roll/pitch, knee, ankle pitch/roll) + 1 waist yaw | $39 |
| 4 | Bipedal frame (3D-printed or kit) | Holds phone on torso, servos in legs | $80 |
| 5 | 5V 6A BEC + 2S LiPo | Powers servos + ESP32, NOT the phone | $15 |
| 6 | Phone mount (printed clip) | Phone camera forward, on its own battery | $2 |
| 7 | Jumper wire | Wire ESP32->PCA9685 | $3 |

~$150 (the paper's was ~$400 with machined parts; a 3D-printed frame cuts this).

## Servo layout (13)

Left leg (6):  hipYaw, hipRoll, hipPitch, knee, anklePitch, ankleRoll
Right leg (6): same
Waist (1): torsoYaw  (lets the camera turn while feet stay planted)

## Protocol (phone -> ESP32 over Wi-Fi UDP)

UDP packet, 14 bytes: 13 joint bytes (0-180) + 1 checksum (sum mod 256).
Port 4242. The phone sends at ~30 Hz. ESP32 listens, drives PCA9685.
High-level commands (walk/turn/stop) are produced by the phone from vision+IMU and
turned into joint targets by the phone's gait controller before sending.

## Files

- `work/note4/robot/src/.../RobotBrainActivity.java` — Wi-Fi joint sender + vision + IMU
- `work/note4/robot/src/.../Gait.java` — open-loop standing/sway gait (RL walk is future work)
- `work/note4/robot/src/.../WifiSender.java` — UDP joint-target stream
- `work/note4/robot/esp32/PhoneBot.ino` — ESP32 firmware: Wi-Fi UDP -> PCA9685
- `work/note4/robot/HARDWARE.md` — BOM, wiring, servo map, setup

## What runs on the phone vs what's research

On the phone (I build this): vision (track a person), IMU balance, Wi-Fi command
stream, and an open-loop gait so it can stand and sway. The paper's RL-trained walking
controller is a research effort (sim + training); I scaffold it but cannot reproduce
a trained policy in one session. The platform (phone brain + ESP32 servo driver + protocol)
is the reproducible starting point the paper intends.
