package com.scamshield.app.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        settingsTopbar = findViewById(R.id.settings_topbar);

        // 1. Notification access settings shortcut
        View btnNotifications = findViewById(R.id.btn_permission_notifications);
        btnNotifications.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "Could not open notification settings.", Toast.LENGTH_SHORT).show();
            }
        });

        // 2. Open standard app details settings (for SMS overlays/SMS read permissions)
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

        // 3. Clear data
        View btnClear = findViewById(R.id.btn_clear_history);
        btnClear.setOnClickListener(v -> {
            try {
                LocalDataStore.getInstance().clearHistory();
                LocalDataStore.getInstance().setAlertModeActive(false); // Reset theme
                Toast.makeText(this, "Detection history cleared.", Toast.LENGTH_SHORT).show();
                onResume(); // Refresh local screen styles instantly
            } catch (Exception e) {
                Toast.makeText(this, "Error clearing history.", Toast.LENGTH_SHORT).show();
            }
        });
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
