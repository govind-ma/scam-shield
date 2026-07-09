package com.scamshield.app.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.scamshield.app.engine.DetectionResult;

/**
 * ThemeManager
 * Package: com.scamshield.app.ui
 *
 * Centralized theme state management for Safe Mode (green) and Alert Mode (red).
 * Persists the current theme state to SharedPreferences so it survives app restarts.
 *
 * ── How it works ────────────────────────────────────────────────────────────
 * When a scam/suspicious detection occurs, ScamAlertManager calls:
 *   ThemeManager.setAlertMode(context, result.verdict)
 *
 * Every Activity's onCreate() calls:
 *   if (ThemeManager.isAlertMode(context)) {
 *       applyAlertTheme();  // show red overlays, warning colors
 *   } else {
 *       applySafeTheme();   // show green, calm colors
 *   }
 *
 * When the user dismisses an alert overlay, ScamAlertManager calls:
 *   ThemeManager.dismissAlert(context)
 *
 * This returns the app to Safe Mode (unless another unresolved alert exists).
 */
public class ThemeManager {

    private static final String TAG = "ScamShield.ThemeManager";

    private static final String PREFS_NAME = "theme_state";
    private static final String KEY_ALERT_MODE = "alert_mode_active";
    private static final String KEY_VERDICT = "current_verdict";

    /** The current theme state — cached in memory for fast repeated checks. */
    private static DetectionResult.Verdict currentMode =
            DetectionResult.Verdict.SAFE;

    /**
     * Sets the app into Alert Mode (red theme) or Safe Mode (green theme).
     * Persists the state to SharedPreferences so the theme survives app restarts.
     *
     * @param context   Application context (do NOT use Activity context to avoid memory leaks).
     * @param verdict   The verdict that triggered the theme change (SCAM or SUSPICIOUS → Alert, SAFE → Safe).
     */
    public static void setAlertMode(Context context, DetectionResult.Verdict verdict) {
        currentMode = verdict;

        // Persist to SharedPreferences
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_VERDICT, verdict.name())
                .putBoolean(KEY_ALERT_MODE, verdict != DetectionResult.Verdict.SAFE)
                .apply();

        Log.d(TAG, "Theme mode set to: " + verdict.name());
    }

    /**
     * Restores the cached theme from SharedPreferences.
     * Called at app startup to restore the last known theme state.
     *
     * @param context Application context.
     * @return The verdict stored in preferences, or SAFE if none found.
     */
    public static DetectionResult.Verdict getCurrentMode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String modeStr = prefs.getString(KEY_VERDICT, DetectionResult.Verdict.SAFE.name());

        try {
            currentMode = DetectionResult.Verdict.valueOf(modeStr);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Invalid verdict in SharedPreferences: " + modeStr + " — defaulting to SAFE");
            currentMode = DetectionResult.Verdict.SAFE;
        }

        return currentMode;
    }

    /**
     * Returns true if the app is currently in Alert Mode (red theme).
     * Used by Activities to decide which colors/layout to apply.
     *
     * @param context Application context.
     * @return true if verdict is SCAM or SUSPICIOUS; false if SAFE.
     */
    public static boolean isAlertMode(Context context) {
        DetectionResult.Verdict mode = getCurrentMode(context);
        return mode == DetectionResult.Verdict.SCAM || mode == DetectionResult.Verdict.SUSPICIOUS;
    }

    /**
     * Dismisses any active alert and returns the app to Safe Mode (green theme).
     * Called when the user taps "Dismiss" or "I've seen this" on an alert overlay.
     *
     * @param context Application context.
     */
    public static void dismissAlert(Context context) {
        setAlertMode(context, DetectionResult.Verdict.SAFE);
        Log.d(TAG, "Alert dismissed — returning to Safe Mode.");
    }

    /**
     * Clears all stored theme state and returns to Safe Mode.
     * Useful for debugging or app reset scenarios.
     *
     * @param context Application context.
     */
    public static void clearTheme(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
        currentMode = DetectionResult.Verdict.SAFE;
        Log.d(TAG, "Theme state cleared.");
    }
}
