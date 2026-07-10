package com.scamshield.app.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.telephony.SmsManager;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.scamshield.app.R;
import com.scamshield.app.data.LocalDataStore;

/**
 * SettingsActivity — Part E: Settings & Permissions
 * Package: com.scamshield.app.ui
 *
 * Manages notification access permissions, system permission shortcuts,
 * and local data reset controls.
 *
 * ── Theming (Safe/Alert Mode) ────────────────────────────────────────────────
 * Dynamically updates its header and tab highlights between Green and Red
 * in response to the global Alert Mode theme status.
 */
public class SettingsActivity extends AppCompatActivity {

    private View settingsTopbar;

    private static final String PREFS_NAME      = "scamshield_settings";
    private static final String KEY_LANGUAGE    = "language";
    private static final String KEY_FAMILY_NUM  = "family_alert_number";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        settingsTopbar = findViewById(R.id.settings_topbar);
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // ── 1. Language selector ──────────────────────────────────────────────
        Button btnLangEn = findViewById(R.id.btn_lang_english);
        Button btnLangHi = findViewById(R.id.btn_lang_hindi);
        Button btnLangGu = findViewById(R.id.btn_lang_gujarati);

        String savedLang = prefs.getString(KEY_LANGUAGE, "English");
        updateLanguageButtons(btnLangEn, btnLangHi, btnLangGu, savedLang);

        View.OnClickListener langClick = v -> {
            String lang;
            if (v.getId() == R.id.btn_lang_hindi) {
                lang = "Hindi";
            } else if (v.getId() == R.id.btn_lang_gujarati) {
                lang = "Gujarati";
            } else {
                lang = "English";
            }
            prefs.edit().putString(KEY_LANGUAGE, lang).apply();
            updateLanguageButtons(btnLangEn, btnLangHi, btnLangGu, lang);
            Toast.makeText(this, "Language set to " + lang, Toast.LENGTH_SHORT).show();
        };
        btnLangEn.setOnClickListener(langClick);
        btnLangHi.setOnClickListener(langClick);
        btnLangGu.setOnClickListener(langClick);

        // ── 2. Family Alert Contact ───────────────────────────────────────────
        EditText etFamilyPhone = findViewById(R.id.et_family_phone);
        String savedNumber = prefs.getString(KEY_FAMILY_NUM, "");
        if (!savedNumber.isEmpty()) etFamilyPhone.setText(savedNumber);

        Button btnTestAlert = findViewById(R.id.btn_test_family_alert);
        btnTestAlert.setOnClickListener(v -> {
            String number = etFamilyPhone.getText().toString().trim();
            if (number.isEmpty()) {
                Toast.makeText(this, "Enter a phone number first.", Toast.LENGTH_SHORT).show();
                return;
            }
            prefs.edit().putString(KEY_FAMILY_NUM, number).apply();
            try {
                SmsManager smsManager = SmsManager.getDefault();
                smsManager.sendTextMessage(number, null,
                        "Scam Shield Test Alert: This is a test message from Scam Shield on your family member's phone.",
                        null, null);
                Toast.makeText(this, "Test alert sent to " + number, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Could not send SMS. Check SMS permission.", Toast.LENGTH_SHORT).show();
            }
        });

        // ── 3. Notification access settings shortcut ──────────────────────────
        View btnNotifications = findViewById(R.id.btn_permission_notifications);
        btnNotifications.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "Could not open notification settings.", Toast.LENGTH_SHORT).show();
            }
        });

        // ── 4. System app-details settings shortcut ────────────────────────────
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

        // ── 5. Clear history — requires confirmation dialog ────────────────────
        View btnClear = findViewById(R.id.btn_clear_history);
        btnClear.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                .setTitle("Clear Detection History")
                .setMessage("Are you sure you want to delete all detection history? This cannot be undone.")
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

        // ── 6. How it Works ───────────────────────────────────────────────────
        View btnHow = findViewById(R.id.btn_how_it_works);
        btnHow.setOnClickListener(v ->
            new AlertDialog.Builder(this)
                .setTitle("How Scam Shield Works")
                .setMessage("Scam Shield monitors incoming SMS messages and payment notifications using an on-device AI engine.\n\n"
                    + "When a suspicious message is detected, it shows a warning overlay before you read it.\n\n"
                    + "Your messages are never uploaded to the internet — all analysis happens privately on your device.\n\n"
                    + "You can also manually check any message using the 'Check Something' screen.")
                .setPositiveButton("Got it", null)
                .show());

        // ── 7. Privacy Policy ─────────────────────────────────────────────────
        View btnPrivacy = findViewById(R.id.btn_privacy_policy);
        btnPrivacy.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://scamshield.app/privacy"));
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "Could not open privacy policy.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** Highlights the active language button and dims the others. */
    private void updateLanguageButtons(Button en, Button hi, Button gu, String active) {
        en.setBackgroundResource("English".equals(active) ? R.drawable.card_elevated : R.drawable.card_white);
        hi.setBackgroundResource("Hindi".equals(active)   ? R.drawable.card_elevated : R.drawable.card_white);
        gu.setBackgroundResource("Gujarati".equals(active) ? R.drawable.card_elevated : R.drawable.card_white);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Setup central bottom tab wiring & colors
        NavigationHelper.setupBottomNavigation(this, NavigationHelper.TAB_SETTINGS);
        NavigationHelper.applyTopBarTheme(this, settingsTopbar);

        boolean isAlert = false;
        try {
            isAlert = com.scamshield.app.data.LocalDataStore.getInstance().isAlertModeActive();
        } catch (Exception ignored) {}
        applySettingsTheme(isAlert);
    }

    private void applySettingsTheme(boolean isAlert) {
        android.view.View root = findViewById(R.id.settings_root_layout);
        TextView tvGroupProtection = findViewById(R.id.tv_settings_group_protection);
        TextView tvGroupData = findViewById(R.id.tv_settings_group_data);
        
        android.view.View btnNotifications = findViewById(R.id.btn_permission_notifications);
        TextView tvNotificationsText = findViewById(R.id.tv_btn_permission_notifications_text);
        TextView tvNotificationsChevron = findViewById(R.id.tv_btn_permission_notifications_chevron);
        
        android.view.View btnSystem = findViewById(R.id.btn_system_settings);
        TextView tvSystemText = findViewById(R.id.tv_btn_system_settings_text);
        TextView tvSystemChevron = findViewById(R.id.tv_btn_system_settings_chevron);
        
        android.view.View btnClear = findViewById(R.id.btn_clear_history);
        TextView tvClearText = findViewById(R.id.tv_btn_clear_history_text);
        TextView tvClearChevron = findViewById(R.id.tv_btn_clear_history_chevron);
        
        if (root == null) return;

        if (isAlert) {
            root.setBackgroundColor(android.graphics.Color.parseColor("#2C2C2A"));
            if (tvGroupProtection != null) tvGroupProtection.setTextColor(android.graphics.Color.parseColor("#B0BEC5"));
            if (tvGroupData != null) tvGroupData.setTextColor(android.graphics.Color.parseColor("#B0BEC5"));
            
            if (btnNotifications != null) btnNotifications.setBackgroundResource(R.drawable.card_dark_gray);
            if (tvNotificationsText != null) tvNotificationsText.setTextColor(android.graphics.Color.WHITE);
            if (tvNotificationsChevron != null) tvNotificationsChevron.setTextColor(android.graphics.Color.parseColor("#B0BEC5"));
            
            if (btnSystem != null) btnSystem.setBackgroundResource(R.drawable.card_dark_gray);
            if (tvSystemText != null) tvSystemText.setTextColor(android.graphics.Color.WHITE);
            if (tvSystemChevron != null) tvSystemChevron.setTextColor(android.graphics.Color.parseColor("#B0BEC5"));
            
            if (btnClear != null) btnClear.setBackgroundResource(R.drawable.card_dark_gray);
            if (tvClearText != null) tvClearText.setTextColor(android.graphics.Color.parseColor("#FF5252"));
            if (tvClearChevron != null) tvClearChevron.setTextColor(android.graphics.Color.parseColor("#FF5252"));
        } else {
            root.setBackgroundColor(android.graphics.Color.parseColor("#F7F8F6"));
            if (tvGroupProtection != null) tvGroupProtection.setTextColor(android.graphics.Color.parseColor("#555555"));
            if (tvGroupData != null) tvGroupData.setTextColor(android.graphics.Color.parseColor("#555555"));
            
            if (btnNotifications != null) btnNotifications.setBackgroundResource(R.drawable.card_white);
            if (tvNotificationsText != null) tvNotificationsText.setTextColor(android.graphics.Color.parseColor("#1A1A1A"));
            if (tvNotificationsChevron != null) tvNotificationsChevron.setTextColor(android.graphics.Color.parseColor("#9E9E9E"));
            
            if (btnSystem != null) btnSystem.setBackgroundResource(R.drawable.card_white);
            if (tvSystemText != null) tvSystemText.setTextColor(android.graphics.Color.parseColor("#1A1A1A"));
            if (tvSystemChevron != null) tvSystemChevron.setTextColor(android.graphics.Color.parseColor("#9E9E9E"));
            
            if (btnClear != null) btnClear.setBackgroundResource(R.drawable.card_light_red);
            if (tvClearText != null) tvClearText.setTextColor(android.graphics.Color.parseColor("#B71C1C"));
            if (tvClearChevron != null) tvClearChevron.setTextColor(android.graphics.Color.parseColor("#B71C1C"));
        }
    }
}
