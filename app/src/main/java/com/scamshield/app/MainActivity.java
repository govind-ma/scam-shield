package com.scamshield.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.scamshield.app.ui.HomeActivity;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * MainActivity
 * Package: com.scamshield.app
 *
 * The launch Activity for Scam Shield.
 *
 * Its only job right now (Part B) is to request SMS permissions at runtime.
 * The actual home screen UI will be built in Part C (Dashboard).
 *
 * ── Why two layers of permissions? ───────────────────────────────────────────
 * Android has two separate gates for sensitive permissions:
 *
 *   Gate 1 — AndroidManifest.xml
 *     Tells the OS: "This app might need this permission."
 *     Without this, runtime requests silently fail every time.
 *
 *   Gate 2 — Runtime request (this file)
 *     Since Android 6.0 (API 23), "dangerous" permissions (ones that touch
 *     private data like messages or location) ALSO require a runtime dialog
 *     where the user taps Allow or Deny.
 *     RECEIVE_SMS and READ_SMS are both in the "dangerous" category.
 *
 * Both gates must be open. This file handles Gate 2.
 * ─────────────────────────────────────────────────────────────────────────────
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "ScamShield.MainActivity";

    /**
     * An arbitrary integer we choose to identify our SMS permission request.
     * Android echoes it back in onRequestPermissionsResult() so we know which
     * request the user just answered (an Activity may request many permissions).
     * Any positive int works — just keep it unique within this Activity.
     */
    private static final int RC_SMS_PERMISSIONS = 2001;

    // Permissions we ask for together on first launch
    private static final String[] REQUIRED_PERMISSIONS = {
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_SMS,
        Manifest.permission.READ_CONTACTS
    };

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // No layout set — this is purely a permission gate.
        // Once permissions are granted it immediately forwards to HomeActivity.
        checkAndRequestSmsPermissions();
    }

    // =========================================================================
    // Runtime permission flow
    // =========================================================================

    /**
     * Entry point for the permission check.
     * Call from onCreate() and from any "Enable Protection" button in Part C.
     *
     * Flow:
     *   Already granted → skip to onPermissionsGranted()
     *   Never asked yet → show OS dialog directly
     *   Previously denied (but not permanently) → show our explanation first
     *   Permanently denied → direct user to Settings
     */
    private void checkAndRequestSmsPermissions() {

        boolean hasReceive   = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)  == PackageManager.PERMISSION_GRANTED;
        boolean hasRead      = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)      == PackageManager.PERMISSION_GRANTED;
        // READ_CONTACTS is optional — if already granted, great; if not, we request it now.
        // Even if the user denies contacts, the app still works (numbers shown instead of names).
        boolean hasContacts  = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;

        if (hasReceive && hasRead && hasContacts) {
            Log.d(TAG, "All permissions already granted.");
            onPermissionsGranted();
            return;
        }

        // shouldShowRequestPermissionRationale() returns true only when the
        // user has previously denied the permission WITHOUT ticking "Never ask again".
        // In that case, best practice (and Google Play policy) is to explain WHY
        // before showing the system dialog again.
        boolean needsExplanation =
                ActivityCompat.shouldShowRequestPermissionRationale(
                        this, Manifest.permission.RECEIVE_SMS)
                || ActivityCompat.shouldShowRequestPermissionRationale(
                        this, Manifest.permission.READ_SMS);

        if (needsExplanation) {
            showRationaleDialog();
        } else {
            // First-ever request, or permanently denied.
            // If permanently denied the OS dialog won't appear — we detect
            // that outcome in onRequestPermissionsResult() below.
            requestSmsPermissions();
        }
    }

    /**
     * Shows a plain-language explanation before the system permission dialog.
     *
     * Written for a non-technical, elderly user — no jargon, no technical terms.
     * Explains what the app does and what it does NOT do with their messages.
     */
    private void showRationaleDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Scam Shield needs a few permissions")
            .setMessage(
                "To protect you from scams, Scam Shield needs:\n\n"
                + "• SMS access — to check messages for scam patterns\n"
                + "• Contacts access — to show your contacts' names in the inbox\n\n"
                + "Your messages and contacts are checked right here on your phone. "
                + "Nothing is ever sent to anyone or shared.\n\n"
                + "Tap \"Allow\" on the next screens to switch on protection."
            )
            .setPositiveButton("Allow", (dialog, which) -> requestSmsPermissions())
            .setNegativeButton("Not now", (dialog, which) -> {
                Log.w(TAG, "User dismissed rationale dialog without granting.");
                onPermissionsDenied(/* permanent= */ false);
            })
            .setCancelable(false)
            .show();
    }

    /**
     * Triggers the OS system permission dialog.
     * The user will see a system-drawn prompt asking to Allow or Deny.
     * We request both permissions in a single call so they appear together.
     */
    private void requestSmsPermissions() {
        ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, RC_SMS_PERMISSIONS);
    }

    /**
     * Android calls this after the user responds to the system permission dialog.
     *
     * @param requestCode  The int we passed to requestPermissions() — lets us
     *                     filter to only our request.
     * @param permissions  The permission strings that were asked about.
     * @param grantResults PERMISSION_GRANTED or PERMISSION_DENIED for each.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode != RC_SMS_PERMISSIONS) return;

        // We require SMS permissions (RECEIVE + READ) to function.
        // Contacts is optional — denied contacts is still OK, we just show numbers.
        boolean hasSmsReceive = false, hasSmsRead = false;
        for (int i = 0; i < permissions.length; i++) {
            if (Manifest.permission.RECEIVE_SMS.equals(permissions[i]))
                hasSmsReceive = (grantResults[i] == PackageManager.PERMISSION_GRANTED);
            if (Manifest.permission.READ_SMS.equals(permissions[i]))
                hasSmsRead = (grantResults[i] == PackageManager.PERMISSION_GRANTED);
        }

        if (hasSmsReceive && hasSmsRead) {
            Log.d(TAG, "SMS permissions granted — SmsReceiver is live.");
            onPermissionsGranted();
            return;
        }

        // If shouldShowRequestPermissionRationale() is now false AND we were
        // just denied, it means the user ticked "Never ask again".
        boolean permanentlyDenied = !ActivityCompat
                .shouldShowRequestPermissionRationale(this, Manifest.permission.RECEIVE_SMS);

        onPermissionsDenied(permanentlyDenied);
    }

    // -------------------------------------------------------------------------
    // Outcomes
    // -------------------------------------------------------------------------

    /**
     * Called when all permissions are granted (or were already granted on launch).
     *
     * Opens HomeActivity and finishes this one so the back-button doesn't loop
     * back to the permission gate.
     *
     * SYSTEM_ALERT_WINDOW permission is requested separately inside
     * HomeActivity.onResume() via OverlayPermissionHelper — no need to ask it here.
     */
    private void onPermissionsGranted() {
        Log.d(TAG, "Permissions granted — launching HomeActivity.");
        Intent intent = new Intent(this, HomeActivity.class);
        // Clear the back stack so Home is the root; pressing Back exits the app.
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    /**
     * Called when the user denied (either once, or permanently).
     *
     * @param permanent true if the user ticked "Never ask again" — in that
     *                  case we must send them to system Settings to re-enable.
     */
    private void onPermissionsDenied(boolean permanent) {
        if (permanent) {
            // OS dialog will never appear again — must guide user to Settings.
            new AlertDialog.Builder(this)
                .setTitle("Protection is switched off")
                .setMessage(
                    "You've turned off SMS access for Scam Shield. "
                    + "To turn protection back on, please go to:\n\n"
                    + "Settings → Apps → Scam Shield → Permissions → SMS → Allow\n\n"
                    + "Then come back and reopen Scam Shield."
                )
                .setPositiveButton("Open Settings", (d, w) -> {
                    android.content.Intent intent = new android.content.Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.fromParts("package", getPackageName(), null)
                    );
                    startActivity(intent);
                })
                .setNegativeButton("Later", null)
                .show();
        } else {
            // Soft denial — can ask again next time
            Toast.makeText(this,
                    "SMS protection is off. Tap the shield to enable it.",
                    Toast.LENGTH_LONG).show();
        }
        Log.w(TAG, "SMS permissions denied. permanent=" + permanent);
    }
}
