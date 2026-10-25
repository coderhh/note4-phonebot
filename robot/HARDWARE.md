# PhoneBot (Note 4) — hardware & setup

A small bipedal humanoid that uses the Galaxy Note 4 as its torso/brain. The phone does
camera vision + IMU sensing + movement control, and sends 13 joint targets over Wi-Fi
to an ESP32 + PCA9685 that drives the servos. Architecture follows UCLA PhoneBot (arXiv 2610.08737).

## Bill of materials

| # | Part | Why | ~Cost |
|---|---|---|---|
| 1 | ESP32 dev board | Onboard controller: receives Wi-Fi joint targets, drives servos | $6 |
| 2 | PCA9685 16-ch PWM driver | Drives up to 16 servos from ESP32 over I2C | $5 |
| 3 | 13x MG90S metal-gear servos | 6 per leg + 1 waist (see map) | $39 |
| 4 | Bipedal frame (3D-printed or kit) | Holds phone on torso, servos in legs | $80 |
| 5 | 5V 6A BEC + 2S LiPo | Powers servos + ESP32, NOT the phone | $15 |
| 6 | Printed phone mount/clip | Phone camera forward, on its own battery | $2 |
| 7 | Jumper wire | Wire ESP32 -> PCA9685 | $3 |

~$150 with a 3D-printed frame.

## Wiring

```
Phone (Note 4)  --Wi-Fi (UDP 4242)-->  ESP32
ESP32:
  GPIO21 (SDA) -> PCA9685 SDA
  GPIO22 (SCL) -> PCA9685 SCL
  3V3          -> PCA9685 VCC (logic), OE -> GND
  VIN / 5V      <- BEC (shared with PCA9685 V+)
PCA9685:
  channels 0..12 -> the 13 servos
  V+            <- BEC 5V 6A (servo power)
  GND           <- BEC GND (common with ESP32)
Battery (2S LiPo -> 5V 6A BEC) -> PCA9685 V+ and ESP32 VIN.
Phone stays on its own battery, mounted on the torso, camera forward.
```

## Servo map (13) — matches the phone's joint indices

| ch | joint | ch | joint |
|---|---|---|---|
| 0 | L.hipYaw | 6 | R.hipYaw |
| 1 | L.hipRoll | 7 | R.hipRoll |
| 2 | L.hipPitch | 8 | R.hipPitch |
| 3 | L.knee | 9 | R.knee |
| 4 | L.anklePitch | 10 | R.anklePitch |
| 5 | L.ankleRoll | 11 | R.ankleRoll |
| 12 | waistYaw | | |

## Setup

1. Flash `esp32/PhoneBot.ino` to the ESP32 (Arduino IDE + ESP32 board support). Put your Wi-Fi
   SSID/password in the sketch first. Open the serial monitor; it prints the ESP32's IP.
2. Power the servos + ESP32 from the BEC. The phone runs on its own battery.
3. On the phone, start RobotBrain (tap the icon, or `am start -n com.hack.note4.robot/.RobotBrainActivity`).
4. Point the phone's browser or curl at `http://<phone-ip>:8080/controller?ip=<ESP32-ip>` so the
   brain knows where to send joint targets.
5. The phone's camera + IMU now decide the gait and stream 13 joint targets to the ESP32
   at ~30 Hz. The ESP32 drives the servos.

## Protocol

UDP packet, 14 bytes: 13 joint bytes (0-180) + 1 checksum (sum mod 256). Port 4242.
The phone's `WifiSender` and the ESP32 firmware both implement this.

## What runs where

- Phone (built here): camera vision (track a person / phototaxis), IMU balance, the
  gait controller (open-loop standing + balance + step-in-place), and the Wi-Fi sender.
- ESP32 (firmware here): receives joint targets, drives PCA9685, drives servos.
- The paper's RL-trained walking is future work (sim + training); this is the reproducible
  platform the paper intends.
