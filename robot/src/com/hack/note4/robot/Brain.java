package com.hack.note4.robot;

import java.util.Map;

/** The brain's decision: turn camera (image) + sensors into a high-level action for the
 *  biped. Phototaxis (walk toward the brighter side / a person lit by the camera) with safety
 * overrides from proximity and tilt. out[0] = action code, out[2]=brightL, out[3]=brightR. */
class Brain {
    // Action codes
    static final int STAND = 0, WALK = 1, TURN_LEFT = 2, TURN_RIGHT = 3, STOP = 4;

    static String decide(byte[] yuv, int pw, int ph, Map<String, float[]> sensors, int[] out) {
        int bl = 0, br = 0;
        if (yuv != null && pw > 0 && yuv.length >= pw * ph * 3 / 2) {
            int half = pw / 2;
            long sumL = 0, sumR = 0, cntL = 0, cntR = 0;
            for (int row = 0; row < ph; row += 2) {
                int base = row * pw;
                for (int x = 0; x < half; x += 2) { sumL += (yuv[base + x] & 0xFF); cntL++; }
                for (int x = half; x < pw; x += 2) { sumR += (yuv[base + x] & 0xFF); cntR++; }
            }
            bl = cntL > 0 ? (int) (sumL / cntL) : 0;
            br = cntR > 0 ? (int) (sumR / cntR) : 0;
        }

        int action;
        String reason;
        int diff = bl - br;
        if (bl < 28 && br < 28) { action = STAND; reason = "stand (dark)"; }
        else if (Math.abs(diff) < 8) { action = WALK; reason = "walk forward"; }
        else if (diff > 0) { action = TURN_LEFT; reason = "turn left (light/person on left)"; }
        else { action = TURN_RIGHT; reason = "turn right (light/person on right)"; }

        // Safety: proximity obstacle.
        float[] prox = sensors.get("android.sensor.proximity");
        if (prox != null && prox[0] < 3.0f) { action = STOP; reason = "STOP (obstacle " + prox[0] + "cm)"; }

        // Safety: tilt (don't climb/fall).
        float[] grav = sensors.get("android.sensor.gravity");
        if (grav != null) {
            double tilt = Math.toDegrees(Math.atan2(
                    Math.sqrt(grav[0] * grav[0] + grav[1] * grav[1]), Math.abs(grav[2])));
            if (tilt > 25) { action = STOP; reason = "STOP (tilt " + (int) tilt + "deg)"; }
        }

        out[0] = action; out[2] = bl; out[3] = br;
        return reason;
    }
}
