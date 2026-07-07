package com.scamshield.app.ui;

import android.content.Intent;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.scamshield.app.R;
import com.scamshield.app.ScamShieldApp;
import com.scamshield.app.data.LocalDataStore;
import com.scamshield.app.engine.DetectionResult;

import java.util.ArrayList;

/**
 * HomeActivity
 * Package: com.scamshield.app.ui
 *
 * The main home screen of Scam Shield. Designed with elderly, non-technical
 * users in mind:
 *   • Large text (no smaller than 18sp in the layout)
 *   • High contrast (dark text on light background or vice versa)
 *   • Minimal clutter — only the three most important actions are prominent
 *   • No small icons without labels
 *
 * Current placeholders (will be filled in later Parts):
 *   • History RecyclerView — empty list, real data from DataStore in Part D
 *   • Learning Modules section — placeholder text, content in Part E
 *   • Settings — opens system permissions for now
 *
 * Also handles:
 *   • Checking and requesting overlay permission (SYSTEM_ALERT_WINDOW)
 *   • Starting FloatingAssistantService once permissions are confirmed
 */
public class HomeActivity extends AppCompatActivity
        implements HistoryAdapter.HistoryRefreshListener {

    private static final String TAG = "ScamShield.Home";

    /** Adapter for the Recent Alerts RecyclerView — populated from LocalDataStore. */
    private HistoryAdapter historyAdapter;

    /**
     * Tracks whether FloatingAssistantService has been started this session.
     */
    private boolean floatingServiceStarted = false;

    // ── Diagnostic call counter (remove after debugging) ─────────────────────
    private static final java.util.concurrent.atomic.AtomicInteger REFRESH_COUNT =
            new java.util.concurrent.atomic.AtomicInteger(0);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        Log.d(TAG, "HomeActivity created.");

        setupProtectionStatusCard();
        // setupHistoryList() already calls refreshHistory() once internally.
        // Do NOT call refreshHistory() again here — it would produce 2x display entries.
        setupHistoryList();
        setupActionButtons();
    }

    // -------------------------------------------------------------------------
    // onResume — check overlay permission every time screen comes back into view
    // -------------------------------------------------------------------------
    @Override
    protected void onResume() {
        super.onResume();

        // Setup bottom navigation tabs & active highlights
        NavigationHelper.setupBottomNavigation(this, NavigationHelper.TAB_HOME);

        // Re-check overlay permission here because the user may have just
        // returned from the system Settings screen where they toggled it.
        updateProtectionStatus();

        // Register as history refresh listener so live detections update the list.
        HistoryAdapter.setRefreshListener(this);

        // NOTE: We deliberately do NOT call refreshHistory() here a second time.
        // setupHistoryList() (in onCreate) already populates the adapter once.
        // The live-update path is: logDetection() → notifyHistoryChanged() → onHistoryChanged() → refreshHistory().
        // Calling it here too would cause a redundant adapter refresh on every onResume().
        Log.d(TAG, "[DIAG] onResume() — NOT calling refreshHistory() here.");
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Unregister to prevent ScamAlertManager from holding a reference to
        // a paused Activity (would leak memory if the Activity is destroyed).
        HistoryAdapter.clearRefreshListener();
    }

    // -------------------------------------------------------------------------
    // onActivityResult — called after user returns from overlay Settings screen
    // -------------------------------------------------------------------------
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == OverlayPermissionHelper.REQUEST_CODE_OVERLAY) {
            // User returned from the "Draw over other apps" Settings screen.
            // Check if they granted the permission.
            if (OverlayPermissionHelper.hasPermission(this)) {
                Log.d(TAG, "Overlay permission granted — starting FloatingAssistantService.");
                startFloatingService();
            } else {
                Log.w(TAG, "Overlay permission still not granted after Settings visit.");
                // Status card will show "Partially Protected" in updateProtectionStatus()
            }
            updateProtectionStatus();
        }
    }

    // =========================================================================
    // Screen setup helpers
    // =========================================================================

    /**
     * Sets up the protection status card at the top of the screen.
     * Shows a green "Protected" or amber "Action Needed" banner.
     */
    private void setupProtectionStatusCard() {
        updateProtectionStatus(); // set initial state
    }

    /**
     * Updates the status card to reflect current permission state.
     * Called in onResume() so it reflects any changes made in Settings.
     */
    private void updateProtectionStatus() {
        TextView tvStatus = findViewById(R.id.tv_protection_status);
        TextView tvIcon   = findViewById(R.id.tv_protection_icon);
        View     cardView = findViewById(R.id.card_protection_status);

        // Check if Alert Mode is active (scam detected)
        boolean isAlert = false;
        try {
            isAlert = LocalDataStore.getInstance().isAlertModeActive();
        } catch (Exception ignored) {}

        applyHomeTheme(isAlert);

        if (isAlert) {
            if (tvIcon != null) tvIcon.setText("🚨");
            tvStatus.setText("ALERT ACTIVE — Tap for emergency help");
            tvStatus.setTextColor(android.graphics.Color.parseColor("#FF5252"));
            cardView.setBackgroundResource(R.drawable.card_dark_gray);
            cardView.setOnClickListener(v -> {
                startActivity(new Intent(this, IGotScammedActivity.class));
            });
            return;
        }

        boolean overlayOk = OverlayPermissionHelper.hasPermission(this);

        if (overlayOk) {
            if (tvIcon != null) tvIcon.setText("🛡️");
            tvStatus.setText("Scam Shield is protecting you");
            tvStatus.setTextColor(android.graphics.Color.parseColor("#2E7D32"));
            cardView.setBackgroundResource(R.drawable.card_white);
            cardView.setOnClickListener(null); // Clear click listener
            // Start the floating service if not already running
            startFloatingService();
        } else {
            if (tvIcon != null) tvIcon.setText("⚠️");
            tvStatus.setText("Action needed — tap to complete setup");
            tvStatus.setTextColor(android.graphics.Color.parseColor("#E65100"));
            cardView.setBackgroundResource(R.drawable.card_white);
            cardView.setOnClickListener(v ->
                OverlayPermissionHelper.requestPermission(this));
        }
    }

    private void applyHomeTheme(boolean isAlert) {
        View root = findViewById(R.id.home_root_layout);
        TextView tvTitle = findViewById(R.id.tv_app_title);
        TextView tvAlertsTitle = findViewById(R.id.tv_recent_alerts_title);
        TextView tvEmpty = findViewById(R.id.tv_history_empty);
        
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
            if (tvAlertsTitle != null) tvAlertsTitle.setTextColor(android.graphics.Color.parseColor("#B0BEC5"));
            if (tvEmpty != null) {
                tvEmpty.setBackgroundResource(R.drawable.card_dark_gray);
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
            if (tvAlertsTitle != null) tvAlertsTitle.setTextColor(android.graphics.Color.parseColor("#555555"));
            if (tvEmpty != null) {
                tvEmpty.setBackgroundResource(R.drawable.card_white);
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

    /**
     * Sets up the History RecyclerView with a real HistoryAdapter backed by LocalDataStore.
     * Shows the empty-state label when there are no items.
     */
    private void setupHistoryList() {
        RecyclerView recyclerView = findViewById(R.id.rv_history);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        historyAdapter = new HistoryAdapter(new ArrayList<>());
        recyclerView.setAdapter(historyAdapter);

        // Load initial history
        refreshHistory();

        Log.d(TAG, "History RecyclerView wired with HistoryAdapter.");
    }

    /**
     * Loads the latest history from LocalDataStore and updates the adapter.
     * Also toggles the empty-state label.
     * Implements HistoryAdapter.HistoryRefreshListener — called both on creation
     * and whenever ScamAlertManager logs a new detection.
     */
    @Override
    public void onHistoryChanged() {
        refreshHistory();
    }

    private void refreshHistory() {
        int callNum = REFRESH_COUNT.incrementAndGet();
        Log.d(TAG, "[DIAG] refreshHistory() called — call #" + callNum
                + " | thread=" + Thread.currentThread().getName());

        if (historyAdapter == null) return;

        java.util.List<com.scamshield.app.engine.DetectionResult> history =
                LocalDataStore.getInstance().getHistory();
        Log.d(TAG, "[DIAG] refreshHistory #" + callNum + " — DataStore returned " + history.size() + " entries");
        historyAdapter.setItems(history);

        // Show/hide the empty-state label
        TextView tvEmpty = findViewById(R.id.tv_history_empty);
        RecyclerView rv  = findViewById(R.id.rv_history);
        if (history.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            rv.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            rv.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Wires the three main action buttons.
     */
    private void setupActionButtons() {

        // ── "Check something" ─────────────────────────────────────────────────
        View btnCheck = findViewById(R.id.btn_check_something);
        btnCheck.setOnClickListener(v -> {
            startActivity(new Intent(this, CheckSomethingActivity.class));
        });

        // ── "Learn about scams" ───────────────────────────────────────────────
        View btnLearn = findViewById(R.id.btn_learn);
        btnLearn.setOnClickListener(v -> {
            startActivity(new Intent(this, LearnAboutScamsActivity.class));
        });

        // ── "I got scammed" ───────────────────────────────────────────────────
        View btnScammed = findViewById(R.id.btn_i_got_scammed);
        btnScammed.setOnClickListener(v -> {
            startActivity(new Intent(this, IGotScammedActivity.class));
        });
    }

    /**
     * Starts FloatingAssistantService.
     * Uses startForegroundService() on API 26+ (required for foreground services).
     * Falls back to regular startService() on older versions.
     */
    private void startFloatingService() {
        // Guard: only start the service once per HomeActivity session.
        // Previously, this was called on every onResume() → updateProtectionStatus() → here,
        // causing the foreground service to receive multiple onStartCommand() calls and
        // re-registering the SMS sensor pipeline, which produced duplicate log entries.
        if (floatingServiceStarted) {
            Log.d(TAG, "FloatingAssistantService already started — skipping.");
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
     * Part E Volume Gesture Scan Now trigger:
     * Overrides key down event to capture Volume Up/Down button clicks and run a
     * quick manual check of the clipboard content (or fallback mock SMS).
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (event.getRepeatCount() == 0) {
                triggerScanNowGesture();
            }
            return true; // Consume volume event so it doesn't adjust system volume during scan
        }
        return super.onKeyDown(keyCode, event);
    }

    private void triggerScanNowGesture() {
        Toast.makeText(this, "🛡️ Scam Shield: Gesture Scan Now triggered...", Toast.LENGTH_SHORT).show();

        String textToScan = "URGENT: Your SBI account is blocked. Share your OTP immediately to verify: http://fake-bank.xyz";

        // Attempt to read from user's clipboard
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

        // Run analysis on clipboard or mock text
        try {
            DetectionResult result = ScamShieldApp.getEngine().analyze(textToScan, "SMS");
            ScamAlertManager.getInstance().onResult(result);
        } catch (Exception e) {
            Log.e(TAG, "Scan Now analysis failed: " + e.getMessage());
        }
    }
}
