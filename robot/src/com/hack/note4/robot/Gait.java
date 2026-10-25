package com.hack.note4.robot;

/** Open-loop gait for a 13-servo biped (PhoneBot-style). Produces joint angle targets.
 *  Joint map:
 *  0 L.hipYaw  1 L.hipRoll  2 L.hipPitch  3 L.knee  4 L.anklePitch  5 L.ankleRoll
 *  6 R.hipYaw  7 R.hipRoll  8 R.hipPitch  9 R.knee 10 R.anklePitch 11 R.ankleRoll
 *  12 waistYaw
 *  All angles 0-180, neutral 90. This is a starting point; the paper's RL-trained
 *  walk is future work (sim + training). */
class Gait {
    static final int NEUTRAL = 90;

    static void standing(int[] j) {
        for (int i = 0; i < 13; i++) j[i] = NEUTRAL;
    }

    /** Balance correction from the gravity vector: lean the ankles to oppose tilt. */
    static void balance(int[] j, float[] gravity) {
        if (gravity == null) return;
        // gx = side tilt, gy = front/back tilt (signs depend on mount; tuned here).
        float gx = gravity[0], gy = gravity[1];
        int ankleRollCorr = clamp((int) (gx * 1.5f), -15, 15);
        int anklePitchCorr = clamp((int) (gy * 1.5f), -15, 15);
        j[5]  = clamp(NEUTRAL - ankleRollCorr, 0, 180);   // L ankle roll
        j[11] = clamp(NEUTRAL + ankleRollCorr, 0, 180);   // R ankle roll
        j[4]  = clamp(NEUTRAL - anklePitchCorr, 0, 180);   // L ankle pitch
        j[10] = clamp(NEUTRAL + anklePitchCorr, 0, 180);   // R ankle pitch
    }

    /** A simple "step in place" gait, phase t in [0,1). Shifts weight, lifts a leg, places it. */
    static void stepInPlace(int[] j, float t) {
        standing(j);
        float phase = t - (float) Math.floor(t);
        boolean left = (Math.floor(t) % 2) == 0;
        // Weight shift to the stance leg (roll toward stance), lift the swing leg knee.
        float lift = (float) Math.sin(phase * Math.PI);   // 0 -> 1 -> 0
        int swingKnee = clamp((int) (NEUTRAL + 40 * lift), 0, 180);
        int stanceRoll = clamp((int) (NEUTRAL + 10 * (1 - lift) * (left ? -1 : 1) * -1), 0, 180);
        if (left) {
            j[3]  = swingKnee;          // L knee lifts
            j[11] = stanceRoll;         // R ankle rolls to take weight
        } else {
            j[9]  = swingKnee;          // R knee lifts
            j[5]  = stanceRoll;         // L ankle rolls to take weight
        }
    }

    static int clamp(int v, int lo, int hi) { return v < lo ? lo : (v > hi ? hi : v); }
}
