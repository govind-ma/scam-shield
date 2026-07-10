package com.scamshield.app.ui;

import android.Manifest;
 import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.scamshield.app.R;
import com.scamshield.app.ScamShieldApp;
import com.scamshield.app.data.LocalDataStore;
import com.scamshield.app.engine.DetectionResult;

import java.util.ArrayList;
import java.util.List;

/**
 * HomeActivity
 * Package: com.scamshield.app.ui
 *
 * The main home screen of Scam Shield.
 *
 * Layout (top → bottom, all inside a single ScrollView):
 *   1. App header (shield + title)
 *   2. Protection status card
 *   3. SMS Inbox — full device SMS inbox with SAFE/SCAM labels (NEW)
 *   4. Quick Actions — Check something / Learn / I got scammed (MOVED DOWN)
 *   5. Recent Scam Alerts — entries from LocalDataStore (MOVED DOWN)
 *
 * The floating shield FAB and bottom navigation bar are outside the ScrollView,
 * positioned identically to before.
 *
 * FloatingAssistantService is started once per session and otherwise untouched.
 */
public class HomeActivity extends AppCompatActivity
        implements HistoryAdapter.HistoryRefreshListener {

    private static final String TAG = "ScamShield.Home";

    /** Request code used when asking the user to grant READ_SMS permission. */
    private static final int REQUEST_READ_SMS = 101;

    // ── Adapters ───────────────────────────────────────────────────────────────

    /** Adapter for the new SMS inbox RecyclerView (rv_sms_inbox). */
    private SmsInboxAdapter smsInboxAdapter;

    /** Adapter for the existing "Recent Scam Alerts" RecyclerView (rv_history). */
    private HistoryAdapter historyAdapter;

    /**
     * Tracks whether FloatingAssistantService has been started this session.
     * Prevents repeated startForegroundService() calls on every onResume().
     */
    private boolean floatingServiceStarted = false;



    // =========================================================================
    // Lifecycle
    // =========================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        Log.d(TAG, "HomeActivity created.");

        // ── Apply theme based on current app state ──────────────────────────────
        // If an alert is pending, show Alert Mode (red); otherwise show Safe Mode (green).
        boolean isAlertMode = ThemeManager.isAlertMode(this);
        applyHomeTheme(isAlertMode);

        setupProtectionStatusCard();
        setupSmsInboxList();   // NEW: set up the SMS inbox RecyclerView
        setupHistoryList();    // EXISTING: "Recent Scam Alerts" (unchanged)
        setupActionButtons();  // EXISTING: Check / Learn / I Got Scammed (unchanged)
    }

    @Override
    protected void onResume() {
        super.onResume();

        NavigationHelper.setupBottomNavigation(this, NavigationHelper.TAB_HOME);

        // Re-check overlay permission and alert mode on every screen return.
        updateProtectionStatus();

        // Start shield pulse animation
        ImageView shieldIcon = findViewById(R.id.iv_app_shield);
        if (shieldIcon != null) {
            boolean alertMode = ThemeManager.isAlertMode(this);
            android.view.animation.Animation pulse = AnimationUtils.loadAnimation(this,
                    alertMode ? R.anim.shield_pulse_scam : R.anim.shield_pulse_safe);
            shieldIcon.startAnimation(pulse);
            shieldIcon.setColorFilter(alertMode
                    ? getResources().getColor(R.color.scam_red, getTheme())
                    : getResources().getColor(R.color.safe_green, getTheme()));
        }

        // Register as history refresh listener so live detections update the list.
        HistoryAdapter.setRefreshListener(this);

        // Do NOT call refreshHistory() here — setupHistoryList() already did it once in onCreate().
        // Live updates go via: logDetection() → notifyHistoryChanged() → onHistoryChanged() → refreshHistory().
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Stop shield pulse animation so it doesn't leak while off screen
        ImageView shieldIcon = findViewById(R.id.iv_app_shield);
        if (shieldIcon != null) shieldIcon.clearAnimation();
        // Prevent ScamAlertManager from holding a reference to a paused Activity.
        HistoryAdapter.clearRefreshListener();
    }

    // ── Permission result (for the SMS inbox READ_SMS request) ─────────────────

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_READ_SMS) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "READ_SMS granted mid-session — loading SMS inbox.");
                loadSmsInbox(); // User just granted it: load inbox now
            } else {
                Log.w(TAG, "READ_SMS denied — inbox will show empty state.");
                showSmsInboxEmptyState("SMS permission not granted.\nTap 'Finish setting up' above to enable it.");
            }
        }
    }

    // ── onActivityResult (overlay permission) ──────────────────────────────────

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == OverlayPermissionHelper.REQUEST_CODE_OVERLAY) {
            if (OverlayPermissionHelper.hasPermission(this)) {
                Log.d(TAG, "Overlay permission granted — starting FloatingAssistantService.");
                startFloatingService();
            } else {
                Log.w(TAG, "Overlay permission still not granted.");
            }
            updateProtectionStatus();
        }
    }

    // =========================================================================
    // SMS Inbox — NEW
    // =========================================================================

    /**
     * Initialises the SMS inbox RecyclerView and triggers the first load.
     * Called once from onCreate().
     */
    private void setupSmsInboxList() {
        RecyclerView rv = findViewById(R.id.rv_sms_inbox);
        rv.setLayoutManager(new LinearLayoutManager(this));
        smsInboxAdapter = new SmsInboxAdapter(new ArrayList<>());
        rv.setAdapter(smsInboxAdapter);

        // Kick off the load (permission check is inside loadSmsInbox())
        loadSmsInbox();
    }

    /**
     * Checks READ_SMS permission, then either loads the inbox or requests the
     * permission and shows an appropriate empty state.
     *
     * Safe to call multiple times (e.g. after permission is granted mid-session).
     */
    private void loadSmsInbox() {
        // Gate: READ_SMS must be granted at runtime before querying the ContentProvider
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
                != PackageManager.PERMISSION_GRANTED) {

            Log.w(TAG, "READ_SMS not granted — requesting permission.");
            // Show an informational empty state while the dialog is up
            showSmsInboxEmptyState("Tap here to allow Scam Shield to scan your SMS inbox for scams.");

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.READ_SMS},
                    REQUEST_READ_SMS
            );
            return;
        }

        // Permission is granted — show loading indicator and start background fetch
        showSmsInboxLoading(true);

        SmsInboxLoader.load(this, messages -> {
            // Back on the main thread
            showSmsInboxLoading(false);

            if (messages.isEmpty()) {
                showSmsInboxEmptyState("No messages found in your SMS inbox.");
            } else {
                hideSmsInboxEmptyState();
                smsInboxAdapter.setItems(messages);
                findViewById(R.id.rv_sms_inbox).setVisibility(View.VISIBLE);
                Log.d(TAG, "SMS inbox loaded — " + messages.size() + " messages.");
            }
        });
    }

    private void showSmsInboxLoading(boolean show) {
        ProgressBar pb = findViewById(R.id.pb_sms_loading);
        if (pb != null) pb.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void showSmsInboxEmptyState(String message) {
        TextView tvEmpty = findViewById(R.id.tv_sms_inbox_empty);
        RecyclerView rv  = findViewById(R.id.rv_sms_inbox);
        if (tvEmpty != null) {
            tvEmpty.setText(message);
            tvEmpty.setVisibility(View.VISIBLE);
        }
        if (rv != null) rv.setVisibility(View.GONE);
    }

    private void hideSmsInboxEmptyState() {
        TextView tvEmpty = findViewById(R.id.tv_sms_inbox_empty);
        if (tvEmpty != null) tvEmpty.setVisibility(View.GONE);
    }

    // =========================================================================
    // Protection Status Card — unchanged from original
    // =========================================================================

    private void setupProtectionStatusCard() {
        updateProtectionStatus();
    }

    private void updateProtectionStatus() {
        TextView  tvStatus   = findViewById(R.id.tv_protection_status);
        android.widget.ImageView ivIcon = findViewById(R.id.iv_protection_icon);
        View      iconBadge  = findViewById(R.id.status_icon_badge);
        View      cardView   = findViewById(R.id.card_protection_status);
        TextView  tvScamsBlocked = findViewById(R.id.tv_scams_blocked_count);
        TextView  tvMessagesScanned = findViewById(R.id.tv_messages_scanned_count);
        TextView  tvProtectedDays = findViewById(R.id.tv_protected_days_count);

        boolean isAlert = false;
        try {
            isAlert = LocalDataStore.getInstance().isAlertModeActive();
        } catch (Exception ignored) {}

        applyHomeTheme(isAlert);

        // Calculate today's stats
        int scamsBlockedCount = getTodayScamCount();
        int messagesTodayCount = getTodayMessageCount();
        if (tvScamsBlocked != null) tvScamsBlocked.setText(String.valueOf(scamsBlockedCount));
        if (tvMessagesScanned != null) tvMessagesScanned.setText(String.valueOf(messagesTodayCount));

        // "Protected since X days" counter
        try {
            int days = LocalDataStore.getInstance().getProtectedDays();
            if (tvProtectedDays != null) tvProtectedDays.setText(String.valueOf(days));
        } catch (Exception ignored) {}

        if (isAlert) {
            if (ivIcon != null) {
                ivIcon.setImageResource(R.drawable.ic_warning);
                ivIcon.setColorFilter(android.graphics.Color.parseColor("#FF5252"));
            }
            if (iconBadge != null) iconBadge.setBackgroundResource(R.drawable.bg_badge_alert);
            tvStatus.setText("THREAT DETECTED");
            tvStatus.setTextColor(android.graphics.Color.parseColor("#FF5252"));
            cardView.setBackgroundResource(R.drawable.card_dark_gray);
            cardView.setOnClickListener(v ->
                startActivity(new Intent(this, IGotScammedActivity.class)));
            return;
        }

        boolean overlayOk = OverlayPermissionHelper.hasPermission(this);

        if (overlayOk) {
            if (ivIcon != null) {
                ivIcon.setImageResource(R.drawable.ic_shield_filled);
                ivIcon.setColorFilter(android.graphics.Color.parseColor("#FFFFFF"));
            }
            if (iconBadge != null) iconBadge.setBackgroundResource(R.drawable.bg_badge_green);
            tvStatus.setText("YOU'RE PROTECTED");
            tvStatus.setTextColor(android.graphics.Color.parseColor("#FFFFFF"));
            cardView.setBackgroundResource(R.drawable.card_elevated);
            cardView.setOnClickListener(null);
            startFloatingService();
        } else {
            if (ivIcon != null) {
                ivIcon.setImageResource(R.drawable.ic_warning);
                ivIcon.setColorFilter(android.graphics.Color.parseColor("#E65100"));
            }
            if (iconBadge != null) iconBadge.setBackgroundResource(R.drawable.bg_badge_amber);
            tvStatus.setText("FINISH SETUP");
            tvStatus.setTextColor(android.graphics.Color.parseColor("#FFFFFF"));
            cardView.setBackgroundResource(R.drawable.card_white);
            cardView.setOnClickListener(v ->
                OverlayPermissionHelper.launchOverlaySettings(this));
        }
    }

    /**
     * Get count of scams blocked today.
     */
    private int getTodayScamCount() {
        try {
            List<String> allDetections = LocalDataStore.getInstance().getDetectionHistory();
            if (allDetections == null || allDetections.isEmpty()) return 0;

            int count = 0;
            long today = System.currentTimeMillis() - (System.currentTimeMillis() % 86400000);
            for (String entry : allDetections) {
                try {
                    long timestamp = Long.parseLong(entry.split("\\|")[2]);
                    if (timestamp >= today && entry.contains("SCAM")) {
                        count++;
                    }
                } catch (Exception ignored) {}
            }
            return count;
        } catch (Exception e) {
            Log.w(TAG, "Error calculating scams blocked: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Get count of messages scanned today (in SMS inbox).
     */
    private int getTodayMessageCount() {
        try {
            if (smsInboxAdapter != null) {
                return smsInboxAdapter.getItemCount();
            }
        } catch (Exception e) {
            Log.w(TAG, "Error calculating messages scanned: " + e.getMessage());
        }
        return 0;
    }

    private void applyHomeTheme(boolean isAlert) {
        View root = findViewById(R.id.home_root_layout);
        TextView tvTitle = findViewById(R.id.tv_app_title);
        android.widget.ImageView ivShield = findViewById(R.id.iv_app_shield);
        View cardEmpty = findViewById(R.id.card_history_empty);
        TextView tvEmpty = findViewById(R.id.tv_history_empty);

        View fab = findViewById(R.id.fab_shield);
        View fabCircle = findViewById(R.id.fab_circle);

        View btnCheck = findViewById(R.id.btn_check_something);
        TextView tvCheckText = findViewById(R.id.tv_btn_check_text);
        TextView tvCheckChevron = findViewById(R.id.tv_btn_check_chevron);

        View btnLearn = findViewById(R.id.btn_learn);
        TextView tvLearnText = findViewById(R.id.tv_btn_learn_text);
        TextView tvLearnChevron = findViewById(R.id.tv_btn_learn_chevron);

        View btnScammed = findViewById(R.id.btn_i_got_scammed);
        TextView tvScammedText = findViewById(R.id.tv_btn_scammed_text);
        TextView tvScammedChevron = findViewById(R.id.tv_btn_scammed_chevron);

        if (root == null) return;

        if (isAlert) {
            root.setBackgroundColor(android.graphics.Color.parseColor("#2C2C2A"));
            if (tvTitle != null) tvTitle.setTextColor(android.graphics.Color.WHITE);
            if (ivShield != null) ivShield.setColorFilter(android.graphics.Color.parseColor("#E24B4A"));
            if (fab != null) fab.setBackgroundResource(R.drawable.fab_glow_red);
            if (fabCircle != null) fabCircle.setBackgroundResource(R.drawable.fab_circle_red);
            if (cardEmpty != null) {
                cardEmpty.setBackgroundResource(R.drawable.card_dark_gray);
            }
            if (tvEmpty != null) {
                tvEmpty.setTextColor(android.graphics.Color.parseColor("#B0BEC5"));
            }
            if (btnCheck != null) btnCheck.setBackgroundResource(R.drawable.card_dark_gray);
            if (tvCheckText != null) tvCheckText.setTextColor(android.graphics.Color.WHITE);
            if (tvCheckChevron != null) tvCheckChevron.setTextColor(android.graphics.Color.parseColor("#B0BEC5"));
            if (btnLearn != null) btnLearn.setBackgroundResource(R.drawable.card_dark_gray);
            if (tvLearnText != null) tvLearnText.setTextColor(android.graphics.Color.WHITE);
            if (tvLearnChevron != null) tvLearnChevron.setTextColor(android.graphics.Color.parseColor("#B0BEC5"));
            if (btnScammed != null) btnScammed.setBackgroundResource(R.drawable.card_dark_gray);
            if (tvScammedText != null) tvScammedText.setTextColor(android.graphics.Color.parseColor("#FF5252"));
            if (tvScammedChevron != null) tvScammedChevron.setTextColor(android.graphics.Color.parseColor("#FF5252"));
        } else {
            root.setBackgroundColor(android.graphics.Color.parseColor("#F7F8F6"));
            if (tvTitle != null) tvTitle.setTextColor(android.graphics.Color.parseColor("#1A1A1A"));
            if (ivShield != null) ivShield.setColorFilter(android.graphics.Color.parseColor("#3B6D11"));
            if (fab != null) fab.setBackgroundResource(R.drawable.fab_glow);
            if (fabCircle != null) fabCircle.setBackgroundResource(R.drawable.fab_circle_green);
            if (cardEmpty != null) {
                cardEmpty.setBackgroundResource(R.drawable.card_elevated);
            }
            if (tvEmpty != null) {
                tvEmpty.setTextColor(android.graphics.Color.parseColor("#757575"));
            }
            if (btnCheck != null) btnCheck.setBackgroundResource(R.drawable.card_white);
            if (tvCheckText != null) tvCheckText.setTextColor(android.graphics.Color.parseColor("#1A1A1A"));
            if (tvCheckChevron != null) tvCheckChevron.setTextColor(android.graphics.Color.parseColor("#9E9E9E"));
            if (btnLearn != null) btnLearn.setBackgroundResource(R.drawable.card_white);
            if (tvLearnText != null) tvLearnText.setTextColor(android.graphics.Color.parseColor("#1A1A1A"));
            if (tvLearnChevron != null) tvLearnChevron.setTextColor(android.graphics.Color.parseColor("#9E9E9E"));
            if (btnScammed != null) btnScammed.setBackgroundResource(R.drawable.card_light_red);
            if (tvScammedText != null) tvScammedText.setTextColor(android.graphics.Color.parseColor("#B71C1C"));
            if (tvScammedChevron != null) tvScammedChevron.setTextColor(android.graphics.Color.parseColor("#B71C1C"));
        }
    }

    // =========================================================================
    // Recent Scam Alerts (HistoryAdapter) — unchanged from original
    // =========================================================================

    private void setupHistoryList() {
        RecyclerView recyclerView = findViewById(R.id.rv_history);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        historyAdapter = new HistoryAdapter(new ArrayList<>());
        recyclerView.setAdapter(historyAdapter);

        setupHistoryFilterChips();
        refreshHistory();
        Log.d(TAG, "History RecyclerView wired with HistoryAdapter.");
    }

    /**
     * Wires the filter chips (All / Scams / Suspicious / Safe) above the history list.
     * The active chip gets a solid green/red tint; inactive chips are dimmed.
     */
    private void setupHistoryFilterChips() {
        android.widget.TextView chipAll        = findViewById(R.id.chip_filter_all);
        android.widget.TextView chipScam       = findViewById(R.id.chip_filter_scam);
        android.widget.TextView chipSuspicious = findViewById(R.id.chip_filter_suspicious);
        android.widget.TextView chipSafe       = findViewById(R.id.chip_filter_safe);

        if (chipAll == null) return; // layout doesn't have chips yet — safe guard

        View.OnClickListener chipClick = v -> {
            HistoryAdapter.Filter filter;
            if (v.getId() == R.id.chip_filter_scam) {
                filter = HistoryAdapter.Filter.SCAM;
            } else if (v.getId() == R.id.chip_filter_suspicious) {
                filter = HistoryAdapter.Filter.SUSPICIOUS;
            } else if (v.getId() == R.id.chip_filter_safe) {
                filter = HistoryAdapter.Filter.SAFE;
            } else {
                filter = HistoryAdapter.Filter.ALL;
            }
            historyAdapter.setFilter(filter);
            updateFilterChipHighlight(filter);

            // Show empty state if filtered list is now empty
            List<DetectionResult> history = historyAdapter.getItemCount() == 0
                    ? new java.util.ArrayList<>()
                    : LocalDataStore.getInstance().getHistory(); // just to check
            View cardEmpty = findViewById(R.id.card_history_empty);
            RecyclerView rv = findViewById(R.id.rv_history);
            if (historyAdapter.getItemCount() == 0) {
                if (cardEmpty != null) cardEmpty.setVisibility(View.VISIBLE);
                if (rv != null) rv.setVisibility(View.GONE);
            } else {
                if (cardEmpty != null) cardEmpty.setVisibility(View.GONE);
                if (rv != null) rv.setVisibility(View.VISIBLE);
            }
        };

        chipAll.setOnClickListener(chipClick);
        chipScam.setOnClickListener(chipClick);
        chipSuspicious.setOnClickListener(chipClick);
        chipSafe.setOnClickListener(chipClick);

        // Initial highlight — "All" is active by default
        updateFilterChipHighlight(HistoryAdapter.Filter.ALL);
    }

    /**
     * Visually highlights the active filter chip by changing its alpha.
     * Active = full opacity; inactive = 50% opacity.
     */
    private void updateFilterChipHighlight(HistoryAdapter.Filter active) {
        android.widget.TextView chipAll        = findViewById(R.id.chip_filter_all);
        android.widget.TextView chipScam       = findViewById(R.id.chip_filter_scam);
        android.widget.TextView chipSuspicious = findViewById(R.id.chip_filter_suspicious);
        android.widget.TextView chipSafe       = findViewById(R.id.chip_filter_safe);

        if (chipAll == null) return;
        chipAll.setAlpha(        active == HistoryAdapter.Filter.ALL        ? 1.0f : 0.45f);
        chipScam.setAlpha(       active == HistoryAdapter.Filter.SCAM       ? 1.0f : 0.45f);
        chipSuspicious.setAlpha( active == HistoryAdapter.Filter.SUSPICIOUS ? 1.0f : 0.45f);
        chipSafe.setAlpha(       active == HistoryAdapter.Filter.SAFE       ? 1.0f : 0.45f);
    }

    @Override
    public void onHistoryChanged() {
        refreshHistory();
    }

    private void refreshHistory() {
        if (historyAdapter == null) return;

        List<DetectionResult> history = LocalDataStore.getInstance().getHistory();
        historyAdapter.setItems(history);

        View cardEmpty = findViewById(R.id.card_history_empty);
        RecyclerView rv  = findViewById(R.id.rv_history);
        if (history.isEmpty()) {
            if (cardEmpty != null) cardEmpty.setVisibility(View.VISIBLE);
            rv.setVisibility(View.GONE);
        } else {
            if (cardEmpty != null) cardEmpty.setVisibility(View.GONE);
            rv.setVisibility(View.VISIBLE);
        }
    }

    // =========================================================================
    // Action Buttons — unchanged from original
    // =========================================================================

    private void setupActionButtons() {
        View btnCheck = findViewById(R.id.btn_check_something);
        setupCardWithAnimation(btnCheck, 0, v -> {
            startActivity(new Intent(this, CheckSomethingActivity.class));
        });

        View btnLearn = findViewById(R.id.btn_learn);
        setupCardWithAnimation(btnLearn, 1, v -> {
            startActivity(new Intent(this, LearnAboutScamsActivity.class));
        });

        View btnScammed = findViewById(R.id.btn_i_got_scammed);
        setupCardWithAnimation(btnScammed, 2, v -> {
            startActivity(new Intent(this, IGotScammedActivity.class));
        });

        View fabShield = findViewById(R.id.fab_shield);
        if (fabShield != null) {
            fabShield.setOnClickListener(v -> triggerScanNowGesture());
        }
    }

    /**
     * Setup card with staggered entrance animation and press feedback.
     */
    private void setupCardWithAnimation(View card, int delayMultiplier, View.OnClickListener onClickListener) {
        if (card == null) return;

        // Animate entrance with staggered timing
        Animation slideUpAnim = AnimationUtils.loadAnimation(this, R.anim.slide_up_fade_in);
        slideUpAnim.setStartOffset(delayMultiplier * 100); // Stagger by 100ms
        card.startAnimation(slideUpAnim);

        // Add press feedback animations
        card.setOnClickListener(v -> {
            Animation scaleDown = AnimationUtils.loadAnimation(this, R.anim.scale_down);
            card.startAnimation(scaleDown);
            card.postDelayed(() -> {
                Animation scaleUp = AnimationUtils.loadAnimation(this, R.anim.scale_up);
                card.startAnimation(scaleUp);
                onClickListener.onClick(v);
            }, 150);
        });
    }
    private void startFloatingService() {
        // Double-guard: check session flag first, then fall back to ActivityManager
        // so we never spawn two bubbles even if the flag was reset.
        if (floatingServiceStarted || isServiceRunning(FloatingAssistantService.class)) {
            Log.d(TAG, "FloatingAssistantService already running — skipping.");
            floatingServiceStarted = true; // sync the flag
            return;
        }
        Intent serviceIntent = new Intent(this, FloatingAssistantService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        floatingServiceStarted = true;
        Log.d(TAG, "FloatingAssistantService start requested.");
    }

    /**
     * Checks if a given Service class is currently running in this process.
     * Used as a belt-and-suspenders guard against duplicate service starts.
     */
    @SuppressWarnings("deprecation")
    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager =
                (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) return false;
        for (ActivityManager.RunningServiceInfo info :
                manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(info.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    // =========================================================================
    // Volume Gesture Scan — unchanged from original
    // =========================================================================

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (event.getRepeatCount() == 0) {
                triggerScanNowGesture();
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void triggerScanNowGesture() {
        Toast.makeText(this, "🛡️ Scam Shield: Gesture Scan Now triggered...", Toast.LENGTH_SHORT).show();

        String textToScan = "URGENT: Your SBI account is blocked. Share your OTP immediately to verify: http://fake-bank.xyz";

        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard != null && clipboard.hasPrimaryClip()) {
                ClipData clip = clipboard.getPrimaryClip();
                if (clip != null && clip.getItemCount() > 0) {
                    CharSequence clipText = clip.getItemAt(0).getText();
                    if (clipText != null && clipText.length() > 0) {
                        textToScan = clipText.toString();
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to read clipboard for Scan Now: " + e.getMessage());
        }

        try {
            DetectionResult result = ScamShieldApp.getEngine().analyze(textToScan, "SMS");
            ScamAlertManager.getInstance().onResult(result);
        } catch (Exception e) {
            Log.e(TAG, "Scan Now analysis failed: " + e.getMessage());
        }
    }
}
