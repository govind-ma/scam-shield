package com.scamshield.app.ui;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.HapticFeedbackConstants;
import android.view.View;

/**
 * HapticManager
 * Package: com.scamshield.app.ui
 *
 * Centralised haptic feedback utility for Scam Shield.
 * All methods are static — no instance needed, just pass the relevant View.
 *
 * Four feedback types:
 *   scamDetected()      — strong triple-pulse alert (SCAM overlay shown)
 *   safeConfirmed()     — light single tick (SAFE verdict confirmed)
 *   recoveryModeOpened()— medium double-pulse (Recovery Mode opened)
 *   buttonTap()         — subtle click (primary action buttons)
 *
 * Requires VIBRATE permission in AndroidManifest.xml.
 * Degrades gracefully: falls back to View.performHapticFeedback() on API < 26.
 */
public class HapticManager {

    private HapticManager() {
        // Utility class — no instantiation
    }

    /**
     * Strong triple-pulse alert haptic.
     * Call when a SCAM overlay is shown to immediately grab the user's attention.
     *
     * Pattern: 100ms on, 50ms off, 100ms on, 50ms off, 200ms on
     * Amplitudes: 255, 200, 255 for an escalating feel.
     */
    public static void scamDetected(View view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Vibrator vibrator = (Vibrator) view.getContext()
                    .getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                VibrationEffect effect = VibrationEffect.createWaveform(
                        new long[]{0, 100, 50, 100, 50, 200},
                        new int[] {0, 255,  0, 200,  0, 255},
                        -1 // no repeat
                );
                vibrator.vibrate(effect);
            }
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        }
    }

    /**
     * Light single-pulse confirmation haptic.
     * Call when a SAFE verdict is confirmed so the user feels reassured.
     */
    public static void safeConfirmed(View view) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    /**
     * Medium double-pulse haptic signalling "help is here".
     * Call when Recovery Mode (IGotScammedActivity) is opened.
     *
     * Pattern: 80ms on, 60ms off, 80ms on — two clean taps.
     */
    public static void recoveryModeOpened(View view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Vibrator vibrator = (Vibrator) view.getContext()
                    .getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                VibrationEffect effect = VibrationEffect.createWaveform(
                        new long[]{0, 80, 60, 80},
                        new int[] {0, 200,  0, 200},
                        -1
                );
                vibrator.vibrate(effect);
            }
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
    }

    /**
     * Subtle click haptic for primary action button taps.
     * Keeps the interaction feel tactile without being intrusive.
     */
    public static void buttonTap(View view) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY_RELEASE);
    }
}
