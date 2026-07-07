package com.scamshield.app.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;

/**
 * OverlayPermissionHelper
 * Package: com.scamshield.app.ui
 *
 * Handles the special "Draw over other apps" permission flow.
 *
 * ── Why is this permission different from RECEIVE_SMS? ────────────────────────
 * RECEIVE_SMS uses the normal requestPermissions() system dialog.
 * SYSTEM_ALERT_WINDOW (draw over other apps) does NOT use requestPermissions().
 * Instead, Android sends the user to a special Settings screen where they
 * toggle the permission manually. There is no "Allow/Deny" dialog — only a
 * toggle switch. Your app finds out the result by checking Settings.canDrawOverlays()
 * again when the user comes back (in onResume() of the calling Activity).
 *
 * This is by design: Google considers this permission high-risk because a
 * malicious app could draw fake UI over your bank app to steal credentials.
 * So they made the flow deliberately deliberate (user must go to Settings).
 *
 * ── Request code ──────────────────────────────────────────────────────────────
 * When we call startActivityForResult() to open the Settings screen,
 * we pass REQUEST_CODE_OVERLAY. Android echoes this back in
 * onActivityResult() so we know which settings request the user just closed.
 * ─────────────────────────────────────────────────────────────────────────────
 */
public class OverlayPermissionHelper {

    private static final String TAG = "ScamShield.OverlayPerm";

    /**
     * The request code for the overlay settings Intent.
     * Used in startActivityForResult() and checked in onActivityResult().
     * The value 3001 is arbitrary — just keep it unique within the calling Activity.
     */
    public static final int REQUEST_CODE_OVERLAY = 3001;

    /**
     * Returns true if the app already has "Draw over other apps" permission.
     * Call this in onResume() after returning from the Settings screen to see
     * if the user granted it.
     *
     * @param context Any context — application context is fine.
     */
    public static boolean hasPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(context);
        }
        // On API < 23, SYSTEM_ALERT_WINDOW is granted to all apps at install
        return true;
    }

    /**
     * Shows a plain-language explanation dialog, then sends the user to the
     * system Settings screen to grant "Draw over other apps" permission.
     *
     * Call this from your Activity when hasPermission() returns false.
     *
     * @param activity The calling Activity (needed to start Settings Intent).
     */
    public static void requestPermission(androidx.appcompat.app.AppCompatActivity activity) {
        new AlertDialog.Builder(activity)
            .setTitle("Show alerts on top of other apps")
            .setMessage(
                "Scam Shield needs to show a warning even when you have "
                + "another app open — like when a scammer calls you while "
                + "you are using your bank app.\n\n"
                + "On the next screen, find \"Scam Shield\" and turn it on.\n\n"
                + "Your screen contents are never recorded or watched."
            )
            .setPositiveButton("Open Settings", (dialog, which) -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Intent intent = new Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:" + activity.getPackageName())
                    );
                    // startActivityForResult sends us back to onActivityResult()
                    // when the user presses Back from the Settings screen.
                    activity.startActivityForResult(intent, REQUEST_CODE_OVERLAY);
                    Log.d(TAG, "Opened overlay permission Settings for user.");
                }
            })
            .setNegativeButton("Not now", (dialog, which) ->
                Log.w(TAG, "User declined overlay permission explanation."))
            .setCancelable(false)
            .show();
    }
}
