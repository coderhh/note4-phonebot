// PhoneBot — ESP32 onboard controller firmware.
// Receives 13 joint targets (0-180 deg) over Wi-Fi (UDP port 4242) from the phone brain,
// and drives 13 servos via a PCA9685 16-channel PWM driver over I2C.
//
// Wiring:
//   ESP32  GPIO21 (SDA) -> PCA9685 SDA
//   ESP32  GPIO22 (SCL) -> PCA9685 SCL
//   ESP32  3V3            -> PCA9685 VCC (logic), OE tie low
//   External 5V 6A BEC   -> PCA9685 V+ (servo power) + ESP32 VIN
//   PCA9685 channels 0..12 -> the 13 servos (see SERVO map below)
//   Common ground across ESP32 / PCA9685 / BEC / servos.
//
// Servo map (matches the phone's joint indices):
//   0  L.hipYaw       1  L.hipRoll     2  L.hipPitch
//   3  L.knee        4  L.anklePitch  5  L.ankleRoll
//   6  R.hipYaw       7  R.hipRoll     8  R.hipPitch
//   9  R.knee       10 R.anklePitch 11 R.ankleRoll
//  12 waistYaw

#include <WiFi.h>
#include <AsyncUDP.h>
#include <Adafruit_PWMServoDriver.h>

Adafruit_PWMServoDriver pwm = Adafruit_PWMServoDriver();

// ---- Your Wi-Fi network (the phone and ESP32 must be on the same LAN) ----
const char* WIFI_SSID     = "YOUR_SSID";
const char* WIFI_PASSWORD = "YOUR_PASSWORD";

// The phone sends to this port. (The phone's WifiSender uses 4242.)
const uint16_t UDP_PORT = 4242;

AsyncUDP udp;

// PCA9685: 50 Hz servo refresh, 0.5-2.5 ms pulse -> 0-180 deg.
#define SERVO_MIN 102   // ~0.5 ms  -> 0 deg
#define SERVO_MAX 512   // ~2.5 ms  -> 180 deg
void setServo(uint8_t ch, uint8_t deg) {
  if (deg > 180) deg = 180;
  uint16_t pulse = map(deg, 0, 180, SERVO_MIN, SERVO_MAX);
  pwm.setPWM(ch, 0, pulse);
}

void setup() {
  Serial.begin(115200);
  pwm.begin();
  pwm.setPWMFreq(50);
  // Center all servos on boot.
  for (uint8_t i = 0; i < 13; i++) setServo(i, 90);

  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.print("PhoneBot controller joining Wi-Fi");
  while (WiFi.status() != WL_CONNECTED) { delay(250); Serial.print("."); }
  Serial.println();
  Serial.print("PhoneBot controller IP: ");
  Serial.println(WiFi.localIP());   // Tell the phone this IP, then: /controller?ip=<this>

  if (udp.listen(UDP_PORT)) {
    udp.onPacket([](AsyncUDPPacket packet) {
      if (packet.length() < 14) return;
      const uint8_t* d = packet.data();
      uint8_t sum = 0;
      for (uint8_t i = 0; i < 13; i++) sum += d[i];
      if (sum != d[13]) { Serial.println("bad checksum"); return; }
      for (uint8_t i = 0; i < 13; i++) setServo(i, d[i]);
    });
  }
  Serial.println("listening for joint targets on UDP 4242");
}

void loop() {
  // The phone drives the servos; the controller just forwards. Nothing to do here.
  delay(1000);
}
