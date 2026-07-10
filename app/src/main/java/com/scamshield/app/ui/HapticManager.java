package com.scamshield.app.ui;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

/**
 * HapticManager
 * Package: com.scamshield.app.ui
 *
 * Centralised vibration / haptic feedback manager for Scam Shield.
 *
 * Usage:
 *   HapticManager.getInstance(context).scamDetected();
 *
 * Requires the VIBRATE permission in AndroidManifest.xml.
 */
public class HapticManager {

    private static HapticManager instance;
    private final Vibrator vibrator;

    private HapticManager(Context ctx) {
        vibrator = (Vibrator) ctx.getApplicationContext()
                .getSystemService(Context.VIBRATOR_SERVICE);
    }

    /** Returns the singleton instance, creating it lazily on first call. */
    public static HapticManager getInstance(Context ctx) {
        if (instance == null) {
            instance = new HapticManager(ctx.getApplicationContext());
        }
        return instance;
    }

    // =========================================================================
    // Public haptic events
    // =========================================================================

    /**
     * Strong triple-burst pattern — called when a scam / suspicious message
     * is detected by the overlay or SmsReceiver.
     * Pattern: pause, 200ms, 100ms gap, 200ms, 100ms gap, 400ms
     */
    public void scamDetected() {
        vibrate(new long[]{0, 200, 100, 200, 100, 400}, -1);
    }

    /**
     * Single short tap — called when a message is confirmed SAFE.
     * Pattern: pause, 80ms
     */
    public void safeConfirmed() {
        vibrate(new long[]{0, 80}, -1);
    }

    /**
     * Double-tap pattern — called when Recovery / God Mode is opened.
     * Pattern: pause, 100ms, 50ms gap, 100ms
     */
    public void recoveryModeOpened() {
        vibrate(new long[]{0, 100, 50, 100}, -1);
    }

    /**
     * Very short single tap — generic button press feedback.
     * Pattern: pause, 40ms
     */
    public void buttonTap() {
        vibrate(new long[]{0, 40}, -1);
    }

    // =========================================================================
    // Internal helper
    // =========================================================================

    private void vibrate(long[] pattern, int repeat) {
        if (vibrator == null || !vibrator.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, repeat));
        } else {
            vibrator.vibrate(pattern, repeat);
        }
    }
}
