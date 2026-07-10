package com.scamshield.app.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.telephony.SmsManager;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.scamshield.app.R;
import com.scamshield.app.data.LocalDataStore;

/**
 * SettingsActivity — Settings &amp; Permissions
 * Package: com.scamshield.app.ui
 *
 * Sections:
 *   1. Protection Settings  — notification access, system permissions, family alert
 *   2. Preferences          — language selection
 *   3. Storage &amp; Data   — clear detection history
 *   4. About                — version, how it works, privacy policy
 */
public class SettingsActivity extends AppCompatActivity {

    private static final String PREFS_NAME      = "ScamShieldPrefs";
    private static final String KEY_LANGUAGE    = "user_language";
    private static final String KEY_FAMILY_NUM  = "family_contact_number";

    private View           settingsTopbar;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        settingsTopbar = findViewById(R.id.settings_topbar);
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        setupProtectionRows();
        setupFamilyAlertRows();
        setupLanguageRow();
        setupDataRows();
        setupAboutRows();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Protection Settings
    // ─────────────────────────────────────────────────────────────────────────

    private void setupProtectionRows() {
        // Notification access
        View btnNotifications = findViewById(R.id.btn_permission_notifications);
        btnNotifications.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            } catch (Exception e) {
                Toast.makeText(this, "Could not open notification settings.", Toast.LENGTH_SHORT).show();
            }
        });

        // System app details (SMS / overlay permissions)
        View btnSystem = findViewById(R.id.btn_system_settings);
        btnSystem.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())
                );
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "Could not open system settings.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Family Alert Contact
    // ─────────────────────────────────────────────────────────────────────────

    private void setupFamilyAlertRows() {
        TextView tvFamilySubtitle = findViewById(R.id.tv_family_contact_subtitle);
        refreshFamilyContactSubtitle(tvFamilySubtitle);

        // Set / edit family contact number
        View btnFamilyContact = findViewById(R.id.btn_family_contact);
        btnFamilyContact.setOnClickListener(v -> showFamilyContactDialog(tvFamilySubtitle));

        // Test alert SMS
        View btnTestAlert = findViewById(R.id.btn_test_family_alert);
        btnTestAlert.setOnClickListener(v -> sendTestFamilyAlert());
    }

    private void showFamilyContactDialog(TextView subtitleView) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_PHONE);
        input.setHint("e.g. +919876543210");
        input.setTextColor(getResources().getColor(R.color.text_primary, getTheme()));
        input.setHintTextColor(getResources().getColor(R.color.text_secondary, getTheme()));

        String current = prefs.getString(KEY_FAMILY_NUM, "");
        if (!current.isEmpty()) input.setText(current);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad / 2, pad, 0);
        container.addView(input);

        new AlertDialog.Builder(this)
            .setTitle("Family Alert Contact")
            .setMessage("Enter the phone number to notify in emergencies.")
            .setView(container)
            .setPositiveButton("Save", (dialog, which) -> {
                String number = input.getText().toString().trim();
                if (number.isEmpty()) {
                    Toast.makeText(this, "Please enter a phone number.", Toast.LENGTH_SHORT).show();
                    return;
                }
                prefs.edit().putString(KEY_FAMILY_NUM, number).apply();
                refreshFamilyContactSubtitle(subtitleView);
                Toast.makeText(this, "Family contact saved.", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void refreshFamilyContactSubtitle(TextView view) {
        if (view == null) return;
        String saved = prefs.getString(KEY_FAMILY_NUM, "");
        view.setText(saved.isEmpty() ? "Not set" : saved);
    }

    private void sendTestFamilyAlert() {
        String number = prefs.getString(KEY_FAMILY_NUM, "");
        if (number.isEmpty()) {
            Toast.makeText(this, "No family contact set. Tap 'Family Alert Contact' first.", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(
                number,
                null,
                "Scam Shield Test: This is a test alert from Scam Shield. Your family member's device is protected.",
                null,
                null
            );
            Toast.makeText(this, "Test SMS sent to " + number, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Failed to send SMS. Check SEND_SMS permission.", Toast.LENGTH_LONG).show();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Language
    // ─────────────────────────────────────────────────────────────────────────

    private void setupLanguageRow() {
        TextView tvLanguageSubtitle = findViewById(R.id.tv_language_subtitle);
        refreshLanguageSubtitle(tvLanguageSubtitle);

        View btnLanguage = findViewById(R.id.btn_language);
        btnLanguage.setOnClickListener(v -> showLanguageDialog(tvLanguageSubtitle));
    }

    private void showLanguageDialog(TextView subtitleView) {
        String[] labels   = { "English", "\u0939\u093F\u0902\u0926\u0940 (Hindi)", "\u0A97\u0AC1\u0A9C\u0AB0\u0ABE\u0AA4\u0AC0 (Gujarati)" };
        String[] values   = { "English", "Hindi", "Gujarati" };

        int current = 0;
        String saved = prefs.getString(KEY_LANGUAGE, "English");
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(saved)) { current = i; break; }
        }
        final int[] selected = { current };

        new AlertDialog.Builder(this)
            .setTitle("Select Language")
            .setSingleChoiceItems(labels, current, (dialog, which) -> selected[0] = which)
            .setPositiveButton("Apply", (dialog, which) -> {
                prefs.edit().putString(KEY_LANGUAGE, values[selected[0]]).apply();
                refreshLanguageSubtitle(subtitleView);
                Toast.makeText(this, "Language updated \u2014 restart app to apply", Toast.LENGTH_LONG).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void refreshLanguageSubtitle(TextView view) {
        if (view == null) return;
        String saved = prefs.getString(KEY_LANGUAGE, "English");
        switch (saved) {
            case "Hindi":    view.setText("\u0939\u093F\u0902\u0926\u0940 (Hindi)"); break;
            case "Gujarati": view.setText("\u0A97\u0AC1\u0A9C\u0AB0\u0ABE\u0AA4\u0AC0 (Gujarati)"); break;
            default:         view.setText("English"); break;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Storage & Data
    // ─────────────────────────────────────────────────────────────────────────

    private void setupDataRows() {
        View btnClear = findViewById(R.id.btn_clear_history);
        btnClear.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                .setTitle("Clear Detection History")
                .setMessage("This will permanently delete all scan records and reset alert mode. Are you sure?")
                .setPositiveButton("Clear", (dialog, which) -> {
                    try {
                        LocalDataStore.getInstance().clearHistory();
                        LocalDataStore.getInstance().setAlertModeActive(false);
                        Toast.makeText(this, "Detection history cleared.", Toast.LENGTH_SHORT).show();
                        onResume();
                    } catch (Exception e) {
                        Toast.makeText(this, "Error clearing history.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. About
    // ─────────────────────────────────────────────────────────────────────────

    private void setupAboutRows() {
        // How Scam Shield Works
        View btnHowItWorks = findViewById(R.id.btn_how_it_works);
        btnHowItWorks.setOnClickListener(v ->
            new AlertDialog.Builder(this)
                .setTitle("How Scam Shield Works")
                .setMessage(
                    "\u2022 Detect \u2014 Scans incoming SMS and notifications in real time using on-device AI.\n\n" +
                    "\u2022 Warn \u2014 Instantly alerts you with a full-screen warning when a likely scam is detected.\n\n" +
                    "\u2022 Educate \u2014 Teaches you how to recognise scam patterns so you stay one step ahead.\n\n" +
                    "\u2022 Recover \u2014 Guides you through the exact steps to take if you have already been scammed."
                )
                .setPositiveButton("Got it", null)
                .show()
        );

        // Privacy Policy
        View btnPrivacy = findViewById(R.id.btn_privacy_policy);
        btnPrivacy.setOnClickListener(v ->
            new AlertDialog.Builder(this)
                .setTitle("Privacy Policy")
                .setMessage(
                    "Scam Shield does not share your messages or personal data with any third party. " +
                    "All detection runs on-device or via encrypted API calls. " +
                    "No message content is stored on external servers."
                )
                .setPositiveButton("OK", null)
                .show()
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected void onResume() {
        super.onResume();
        NavigationHelper.setupBottomNavigation(this, NavigationHelper.TAB_SETTINGS);
        NavigationHelper.applyTopBarTheme(this, settingsTopbar);

        boolean isAlert = false;
        try {
            isAlert = LocalDataStore.getInstance().isAlertModeActive();
        } catch (Exception ignored) {}
        applySettingsTheme(isAlert);

        // Refresh dynamic subtitles on resume
        refreshFamilyContactSubtitle(findViewById(R.id.tv_family_contact_subtitle));
        refreshLanguageSubtitle(findViewById(R.id.tv_language_subtitle));
    }

    private void applySettingsTheme(boolean isAlert) {
        View root = findViewById(R.id.settings_root_layout);
        if (root == null) return;

        if (isAlert) {
            root.setBackgroundColor(android.graphics.Color.parseColor("#0D1B2A"));
        } else {
            root.setBackgroundColor(android.graphics.Color.parseColor("#0D1B2A"));
        }
        // Dark theme is always applied — bg_primary is always #0D1B2A.
        // The existing NavigationHelper.applyTopBarTheme handles the top bar colour.
    }
}
