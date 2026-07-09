package com.scamshield.app.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.scamshield.app.R;
import com.scamshield.app.data.LocalDataStore;

/**
 * NavigationHelper — Part E: UI & Theme Consistency Pass
 * Package: com.scamshield.app.ui
 *
 * Centralized utility to wire bottom navigation tabs and dynamically apply
 * Alert Mode vs Safe Mode theming (green vs red) across Home, Chat, Learn,
 * and Settings activities.
 */
public class NavigationHelper {

    /** Identifiers for tabs */
    public static final int TAB_HOME     = 0;
    public static final int TAB_CHAT     = 1;
    public static final int TAB_LEARN    = 2;
    public static final int TAB_SETTINGS = 3;

    /**
     * Wires the bottom navigation tab clicks and applies dynamic Alert vs Safe Mode theming.
     * Call this in onResume() of every tab-linked Activity.
     *
     * @param activity   The calling Activity.
     * @param currentTab The active tab ID (TAB_HOME, TAB_CHAT, TAB_LEARN, TAB_SETTINGS).
     */
    public static void setupBottomNavigation(final Activity activity, final int currentTab) {
        View bottomNav = activity.findViewById(R.id.bottom_nav_container);
        if (bottomNav == null) return;

        // 1. Get alert mode status from centralized ThemeManager
        boolean isAlertMode = ThemeManager.isAlertMode(activity);

        // 2. Nav bar always white — accent goes on the active tab's icon + label text
        bottomNav.setBackgroundColor(Color.WHITE);

        // 3. Accent color: green in Safe Mode, red in Alert Mode
        int activeColor  = isAlertMode ? Color.parseColor("#E24B4A") : Color.parseColor("#3B6D11");
        int inactiveColor = Color.parseColor("#9E9E9E");

        // Helper arrays — order matches TAB_HOME=0, TAB_CHAT=1, TAB_LEARN=2, TAB_SETTINGS=3
        int[] tabIconIds  = { R.id.tv_nav_home_icon,     R.id.tv_nav_chat_icon,
                              R.id.tv_nav_learn_icon,    R.id.tv_nav_settings_icon };
        int[] tabLabelIds = { R.id.tv_nav_home_label,    R.id.tv_nav_chat_label,
                              R.id.tv_nav_learn_label,   R.id.tv_nav_settings_label };

        for (int i = 0; i < 4; i++) {
            TextView icon  = activity.findViewById(tabIconIds[i]);
            TextView label = activity.findViewById(tabLabelIds[i]);
            int color = (i == currentTab) ? activeColor : inactiveColor;
            if (icon  != null) icon.setTextColor(color);
            if (label != null) label.setTextColor(color);
        }

        // 4. Tab click wiring (clear all backgrounds — no solid color on any tab)
        LinearLayout tabHome     = activity.findViewById(R.id.nav_home);
        LinearLayout tabChat     = activity.findViewById(R.id.nav_chat);
        LinearLayout tabLearn    = activity.findViewById(R.id.nav_learn);
        LinearLayout tabSettings = activity.findViewById(R.id.nav_settings);

        // Remove any previously-set solid background from tab containers
        if (tabHome     != null) tabHome.setBackgroundResource(android.R.drawable.list_selector_background);
        if (tabChat     != null) tabChat.setBackgroundResource(android.R.drawable.list_selector_background);
        if (tabLearn    != null) tabLearn.setBackgroundResource(android.R.drawable.list_selector_background);
        if (tabSettings != null) tabSettings.setBackgroundResource(android.R.drawable.list_selector_background);

        if (tabHome != null) {
            tabHome.setOnClickListener(v -> {
                if (currentTab != TAB_HOME) {
                    Intent intent = new Intent(activity, HomeActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    activity.startActivity(intent);
                }
            });
        }

        if (tabChat != null) {
            tabChat.setOnClickListener(v -> {
                if (currentTab != TAB_CHAT) {
                    Intent intent = new Intent(activity, ChatAssistantActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    activity.startActivity(intent);
                }
            });
        }

        if (tabLearn != null) {
            tabLearn.setOnClickListener(v -> {
                if (currentTab != TAB_LEARN) {
                    Intent intent = new Intent(activity, LearnAboutScamsActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    activity.startActivity(intent);
                }
            });
        }

        if (tabSettings != null) {
            tabSettings.setOnClickListener(v -> {
                if (currentTab != TAB_SETTINGS) {
                    Intent intent = new Intent(activity, SettingsActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    activity.startActivity(intent);
                }
            });
        }
    }

    /**
     * Dynamically updates the top status bar/header view background of the activity.
     * Safe Mode = Green, Alert Mode = Red.
     *
     * @param activity   The calling Activity.
     * @param topbarView The view representing the top bar header.
     */
    public static void applyTopBarTheme(Activity activity, View topbarView) {
        if (topbarView == null) return;
        
        // Use centralized ThemeManager to determine current theme
        boolean isAlertMode = ThemeManager.isAlertMode(activity);

        int color = isAlertMode ? Color.parseColor("#E24B4A") : Color.parseColor("#3B6D11");
        topbarView.setBackgroundColor(color);
    }
}
