package com.scamshield.app.ui;

import android.Manifest;
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

    // ── Diagnostic call counter (remove after debugging) ──────────────────────
    private static final java.util.concurrent.atomic.AtomicInteger REFRESH_COUNT =
            new java.util.concurrent.atomic.AtomicInteger(0);

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

        // Register as history refresh listener so live detections update the list.
        HistoryAdapter.setRefreshListener(this);

        // Do NOT call refreshHistory() here — setupHistoryList() already did it once in onCreate().
        // Live updates go via: logDetection() → notifyHistoryChanged() → onHistoryChanged() → refreshHistory().
        Log.d(TAG, "[DIAG] onResume() — skipping redundant refreshHistory().");
    }

    @Override
    protected void onPause() {
        super.onPause();
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
        TextView  tvSubtitle = findViewById(R.id.tv_protection_subtitle);
        android.widget.ImageView ivIcon = findViewById(R.id.iv_protection_icon);
        View      iconBadge  = findViewById(R.id.status_icon_badge);
        View      cardView   = findViewById(R.id.card_protection_status);

        boolean isAlert = false;
        try {
            isAlert = LocalDataStore.getInstance().isAlertModeActive();
        } catch (Exception ignored) {}

        applyHomeTheme(isAlert);

        if (isAlert) {
            if (ivIcon != null) {
                ivIcon.setImageResource(R.drawable.ic_warning);
                ivIcon.setColorFilter(android.graphics.Color.parseColor("#FF5252"));
            }
            if (iconBadge != null) iconBadge.setBackgroundResource(R.drawable.bg_badge_alert);
            tvStatus.setText("Alert active");
            tvStatus.setTextColor(android.graphics.Color.parseColor("#FF5252"));
            if (tvSubtitle != null) {
                tvSubtitle.setText("Tap for emergency help");
                tvSubtitle.setTextColor(android.graphics.Color.parseColor("#B0BEC5"));
            }
            cardView.setBackgroundResource(R.drawable.card_dark_gray);
            cardView.setOnClickListener(v ->
                startActivity(new Intent(this, IGotScammedActivity.class)));
            return;
        }

        boolean overlayOk = OverlayPermissionHelper.hasPermission(this);

        if (overlayOk) {
            if (ivIcon != null) {
                ivIcon.setImageResource(R.drawable.ic_check);
                ivIcon.setColorFilter(android.graphics.Color.parseColor("#3B6D11"));
            }
            if (iconBadge != null) iconBadge.setBackgroundResource(R.drawable.bg_badge_green);
            tvStatus.setText("You're protected");
            tvStatus.setTextColor(android.graphics.Color.parseColor("#1A1A1A"));
            if (tvSubtitle != null) {
                tvSubtitle.setText("No threats found today");
                tvSubtitle.setTextColor(android.graphics.Color.parseColor("#6B7280"));
            }
            cardView.setBackgroundResource(R.drawable.card_white);
            cardView.setOnClickListener(null);
            startFloatingService();
        } else {
            if (ivIcon != null) {
                ivIcon.setImageResource(R.drawable.ic_warning);
                ivIcon.setColorFilter(android.graphics.Color.parseColor("#E65100"));
            }
            if (iconBadge != null) iconBadge.setBackgroundResource(R.drawable.bg_badge_amber);
            tvStatus.setText("Finish setting up");
            tvStatus.setTextColor(android.graphics.Color.parseColor("#1A1A1A"));
            if (tvSubtitle != null) {
                tvSubtitle.setText("Tap to turn on protection");
                tvSubtitle.setTextColor(android.graphics.Color.parseColor("#E65100"));
            }
            cardView.setBackgroundResource(R.drawable.card_white);
            cardView.setOnClickListener(v ->
                OverlayPermissionHelper.requestPermission(this));
        }
    }

    private void applyHomeTheme(boolean isAlert) {
        View root = findViewById(R.id.home_root_layout);
        TextView tvTitle = findViewById(R.id.tv_app_title);
        android.widget.ImageView ivShield = findViewById(R.id.iv_app_shield);
        TextView tvAlertsTitle = findViewById(R.id.tv_recent_alerts_title);
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
            if (ivShield != null) ivShield.setColorFilter(android.graphics.Color.parseColor("#3B6D11"));
            if (fab != null) fab.setBackgroundResource(R.drawable.fab_glow);
            if (fabCircle != null) fabCircle.setBackgroundResource(R.drawable.fab_circle_green);
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

    // =========================================================================
    // Recent Scam Alerts (HistoryAdapter) — unchanged from original
    // =========================================================================

    private void setupHistoryList() {
        RecyclerView recyclerView = findViewById(R.id.rv_history);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        historyAdapter = new HistoryAdapter(new ArrayList<>());
        recyclerView.setAdapter(historyAdapter);

        refreshHistory();
        Log.d(TAG, "History RecyclerView wired with HistoryAdapter.");
    }

    @Override
    public void onHistoryChanged() {
        refreshHistory();
    }

    private void refreshHistory() {
        int callNum = REFRESH_COUNT.incrementAndGet();
        Log.d(TAG, "[DIAG] refreshHistory() called — call #" + callNum
                + " | thread=" + Thread.currentThread().getName());

        if (historyAdapter == null) return;

        List<DetectionResult> history = LocalDataStore.getInstance().getHistory();
        Log.d(TAG, "[DIAG] refreshHistory #" + callNum + " — DataStore returned " + history.size() + " entries");
        historyAdapter.setItems(history);

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
