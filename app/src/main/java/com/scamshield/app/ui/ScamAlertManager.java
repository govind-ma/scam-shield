package com.scamshield.app.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.scamshield.app.R;
import com.scamshield.app.data.LocalDataStore;
import com.scamshield.app.engine.DetectionListener;
import com.scamshield.app.engine.DetectionResult;

import java.util.Locale;

/**
 * ScamAlertManager
 * Package: com.scamshield.app.ui
 *
 * The ONE class responsible for turning a DetectionResult into something the
 * user actually sees and hears. It implements DetectionListener, so any sensor
 * (SmsReceiver, CallListener, PaymentNotificationListener) can hand a result
 * directly to this class without knowing anything about the UI.
 *
 * ── How it connects to Part B ──────────────────────────────────────────────
 * In SmsReceiver.onReceive(), there is a TODO block that currently reads:
 *
 *     if (detectionListener != null) {
 *         detectionListener.onResult(result);
 *     }
 *
 * To wire Part B → Part C:
 *   SmsReceiver.setDetectionListener(ScamAlertManager.getInstance());
 *
 * That one line, added to ScamShieldApp.onCreate(), completes the pipeline:
 *   SMS → SmsReceiver → DetectionEngine → ScamAlertManager → user sees alert
 *
 * ── Singleton pattern ─────────────────────────────────────────────────────
 * ScamAlertManager is a singleton — one instance lives for the app's lifetime.
 * A singleton means you create the object once and every part of the app shares
 * the same copy. This is important here because:
 *   • TextToSpeech is expensive to initialise (takes ~0.5s)
 *   • WindowManager overlays must be tracked to avoid duplicates
 *   • All sensors must talk to the SAME manager, not separate ones
 *
 * Usage:
 *   ScamAlertManager.init(applicationContext);  ← call once in ScamShieldApp
 *   ScamAlertManager.getInstance();             ← call anywhere after that
 * ─────────────────────────────────────────────────────────────────────────────
 */
public class ScamAlertManager implements DetectionListener {

    private static final String TAG = "ScamShield.AlertManager";

    // ── Singleton ─────────────────────────────────────────────────────────────
    // 'volatile' ensures that if two threads check instance at the same moment,
    // they both see the most up-to-date value (prevents a subtle threading bug).
    private static volatile ScamAlertManager instance;

    /** Application context — safe to hold long-term (won't leak an Activity). */
    private final Context appContext;

    /** Gives us access to the system's overlay drawing layer. */
    private final WindowManager windowManager;

    /**
     * Handler tied to the MAIN thread.
     * onResult() can be called from any thread (future AI engine may use a
     * background thread). All UI operations must run on the main thread.
     * mainHandler.post(runnable) safely marshals work onto it.
     */
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ── TextToSpeech ──────────────────────────────────────────────────────────
    private TextToSpeech tts;
    private boolean ttsReady = false;

    // ── Diagnostic call counter (remove after debugging) ─────────────────────
    private static final java.util.concurrent.atomic.AtomicInteger ON_RESULT_COUNT =
            new java.util.concurrent.atomic.AtomicInteger(0);

    // ── Currently shown overlay (kept so we can remove it on dismiss) ─────────
    private View currentOverlayView = null;

    // ── Active countdown for Pause-and-Verify ─────────────────────────────────
    private Runnable activeCountdownRunnable = null;

    // ── Auto-dismiss delay for SAFE confirmations (milliseconds) ─────────────
    private static final long SAFE_AUTO_DISMISS_MS = 3_000;   // 3 seconds

    // =========================================================================
    // Singleton initialisation
    // =========================================================================

    /**
     * Must be called once from ScamShieldApp.onCreate() before any sensor fires.
     * Passing the application context (not an Activity context) is important —
     * an Activity context holds a reference to the Activity, which would prevent
     * garbage collection and leak memory when the Activity closes.
     *
     * @param applicationContext Use getApplicationContext() or 'this' inside Application.
     */
    public static void init(Context applicationContext) {
        if (instance == null) {
            // 'synchronized' ensures only one thread can enter this block at
            // a time — prevents two threads from both seeing null and both
            // creating an instance simultaneously.
            synchronized (ScamAlertManager.class) {
                if (instance == null) {
                    instance = new ScamAlertManager(applicationContext);
                    Log.d(TAG, "ScamAlertManager initialised.");
                }
            }
        }
    }

    /**
     * Returns the singleton instance. Crashes loudly if init() was not called
     * first — this is intentional. A NullPointerException here tells you exactly
     * what is missing during development, rather than failing silently later.
     */
    public static ScamAlertManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException(
                "ScamAlertManager.init(context) must be called in ScamShieldApp.onCreate() "
                + "before getInstance() can be used.");
        }
        return instance;
    }

    // =========================================================================
    // Constructor (private — enforces singleton)
    // =========================================================================

    private ScamAlertManager(Context applicationContext) {
        this.appContext    = applicationContext;
        this.windowManager = (WindowManager)
                applicationContext.getSystemService(Context.WINDOW_SERVICE);

        initTextToSpeech();
    }

    // =========================================================================
    // DetectionListener — the ONE method Part B (and all sensors) will call
    // =========================================================================

    /**
     * Called by a sensor (SmsReceiver, CallListener, etc.) every time a message
     * has been analysed. This is the exact method signature from the locked
     * DetectionListener interface — do not change it.
     *
     * All UI work is posted to the main thread via mainHandler.post() so this
     * method is safe to call from any background thread.
     *
     * @param result The fully populated DetectionResult from the engine.
     */
    @Override
    public void onResult(final DetectionResult result) {
        int callNum = ON_RESULT_COUNT.incrementAndGet();
        Log.d(TAG, "[DIAG] onResult() called — call #" + callNum
                + " | verdict=" + result.verdict
                + " | score=" + result.confidenceScore
                + " | source=" + result.sourceType);

        // Marshal onto the main thread — UI views can ONLY be created/modified
        // on the main (UI) thread. Calling from a background thread crashes.
        mainHandler.post(() -> {
            // ── Part D: Persist every detection result ─────────────────────────
            // We log SAFE results too so the user can review their full history.
            try {
                LocalDataStore.getInstance().logDetection(result);
                HistoryAdapter.notifyHistoryChanged(); // refresh HomeActivity list
            } catch (Exception e) {
                // DataStore errors must never crash the UI pipeline.
                Log.e(TAG, "Failed to log detection to DataStore: " + e.getMessage());
            }

            // ── Part C+: Update centralized theme state ──────────────────────────
            // When a scam/suspicious detection occurs, switch to Alert Mode (red theme).
            // SAFE verdicts leave the theme unchanged (they don't clear Alert Mode).
            if (result.verdict == DetectionResult.Verdict.SCAM
                    || result.verdict == DetectionResult.Verdict.SUSPICIOUS) {
                ThemeManager.setAlertMode(appContext, result.verdict);
            }

            switch (result.verdict) {
                case SCAM:
                    showScamOverlay(result);
                    speakAloud("Warning! This looks like a scam. " + result.reason);
                    break;

                case SUSPICIOUS:
                    showSuspiciousOverlay(result);
                    speakAloud("Be careful. " + result.reason);
                    break;

                case SAFE:
                    showSafeConfirmation(result);
                    break;

                default:
                    Log.w(TAG, "Unknown verdict: " + result.verdict);
            }
        });
    }

    // =========================================================================
    // Overlay display — SCAM
    // =========================================================================

    /**
     * Shows a large, full-attention overlay for SCAM verdicts.
     * Red background, large text, two action buttons.
     * Does NOT auto-dismiss — the user must consciously tap a button.
     * This is intentional: for a scam, we want them to pause and read.
     */
    private void showScamOverlay(DetectionResult result) {
        // Set Alert Mode globally (updates top bars & tab highlights to red)
        try {
            LocalDataStore.getInstance().setAlertModeActive(true);
        } catch (Exception ignored) {}

        // Remove any existing overlay before adding a new one.
        // If the user gets two scam SMSes in quick succession, we only show one.
        dismissCurrentOverlay();

        // Inflate the overlay layout from XML (see res/layout/overlay_scam_alert.xml)
        View overlayView = LayoutInflater.from(appContext)
                .inflate(R.layout.overlay_scam_alert, null);

        // Populate the text fields
        TextView tvSource = overlayView.findViewById(R.id.tv_alert_source);
        TextView tvReason = overlayView.findViewById(R.id.tv_alert_reason);
        TextView tvScore  = overlayView.findViewById(R.id.tv_alert_score);

        tvSource.setText("⚠  " + result.sourceType + " SCAM ALERT");
        tvReason.setText(result.reason);
        tvScore.setText("Confidence: " + result.confidenceScore + "/100");

        // Wire dismiss button
        Button btnDismiss = overlayView.findViewById(R.id.btn_alert_dismiss);
        btnDismiss.setOnClickListener(v -> dismissCurrentOverlay());

        // Wire "I'll be careful" button — redirects to Recovery Mode flow
        Button btnSafe = overlayView.findViewById(R.id.btn_alert_safe);
        btnSafe.setOnClickListener(v -> {
            dismissCurrentOverlay();
            try {
                Intent recoveryIntent = new Intent(appContext, IGotScammedActivity.class);
                recoveryIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                appContext.startActivity(recoveryIntent);
            } catch (Exception e) {
                Log.e(TAG, "Failed to launch IGotScammedActivity: " + e.getMessage());
            }
        });

        // ── Part E: Pause-and-Verify timed countdown lock ────────────────────
        // Disable both buttons for 30 seconds to force the user to pause and read.
        btnDismiss.setEnabled(false);
        btnSafe.setEnabled(false);
        final String originalDismissText = btnDismiss.getText().toString();
        final String originalSafeText = btnSafe.getText().toString();

        activeCountdownRunnable = new Runnable() {
            private int countdown = 30;

            @Override
            public void run() {
                if (countdown > 0) {
                    btnDismiss.setText(originalDismissText + " (" + countdown + "s)");
                    btnSafe.setText(originalSafeText + " (" + countdown + "s)");
                    countdown--;
                    mainHandler.postDelayed(this, 1000);
                } else {
                    btnDismiss.setText(originalDismissText);
                    btnSafe.setText(originalSafeText);
                    btnDismiss.setEnabled(true);
                    btnSafe.setEnabled(true);
                    activeCountdownRunnable = null;
                }
            }
        };
        mainHandler.post(activeCountdownRunnable);

        addOverlayToWindow(overlayView, true /* fullAttention */);

        // Trigger strong triple-pulse haptic so the user feels the alert
        // even if the phone is face-down or the screen is locked.
        HapticManager.scamDetected(overlayView);
    }

    // =========================================================================
    // Overlay display — SUSPICIOUS
    // =========================================================================

    /**
     * Shows a less alarming but still prominent overlay for SUSPICIOUS verdicts.
     * Orange/amber colouring, slightly smaller footprint than the SCAM overlay.
     * Auto-dismisses after 8 seconds — less urgent, but still visible.
     */
    private void showSuspiciousOverlay(DetectionResult result) {
        // Set Alert Mode globally
        try {
            LocalDataStore.getInstance().setAlertModeActive(true);
        } catch (Exception ignored) {}

        dismissCurrentOverlay();

        View overlayView = LayoutInflater.from(appContext)
                .inflate(R.layout.overlay_suspicious_alert, null);

        TextView tvSource = overlayView.findViewById(R.id.tv_suspicious_source);
        TextView tvReason = overlayView.findViewById(R.id.tv_suspicious_reason);

        tvSource.setText("⚠  " + result.sourceType + " — Please Check");
        tvReason.setText(result.reason);

        Button btnDismiss = overlayView.findViewById(R.id.btn_suspicious_dismiss);
        btnDismiss.setOnClickListener(v -> dismissCurrentOverlay());

        addOverlayToWindow(overlayView, false /* not full attention */);

        // Auto-dismiss after 8 seconds
        mainHandler.postDelayed(this::dismissCurrentOverlay, 8_000);
    }

    // =========================================================================
    // Safe confirmation — SAFE
    // =========================================================================

    /**
     * Shows a brief, reassuring green confirmation for SAFE verdicts.
     * Uses a small overlay that auto-dismisses in 3 seconds.
     * Intentionally unobtrusive — safe messages shouldn't interrupt the user.
     */
    private void showSafeConfirmation(DetectionResult result) {
        // For SAFE we use a simple Toast — lightweight and non-intrusive.
        // No overlay permission needed for Toast.
        // If you later want a subtle branded overlay instead, replace this block.
        String msg = "✓  Message looks safe (" + result.sourceType + ")";
        Toast.makeText(appContext, msg, Toast.LENGTH_SHORT).show();

        Log.d(TAG, "SAFE result from " + result.sourceType + " — Toast shown.");
    }

    // =========================================================================
    // WindowManager helpers
    // =========================================================================

    /**
     * Adds a View to the system overlay layer (above all other apps).
     *
     * ── How SYSTEM_ALERT_WINDOW works ─────────────────────────────────────────
     * TYPE_APPLICATION_OVERLAY (API 26+) lets us draw a window that floats
     * above every other app — including the phone dialler, SMS apps, etc.
     * This is how apps like Facebook Messenger's Chat Heads work.
     *
     * The user must grant "Draw over other apps" permission first. That check
     * lives in OverlayPermissionHelper (see that class). If permission is not
     * granted, this method logs a warning and returns without crashing.
     *
     * @param view          The inflated View to show.
     * @param fullAttention If true, overlay is centred and large; false = smaller banner.
     */
    private void addOverlayToWindow(View view, boolean fullAttention) {
        if (!canDrawOverlays()) {
            Log.w(TAG, "SYSTEM_ALERT_WINDOW not granted — cannot show overlay. "
                    + "Direct user to Settings via OverlayPermissionHelper.");
            // Fallback: a Toast always works without special permission
            Toast.makeText(appContext,
                    "Scam Shield alert — check Logcat for details.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        // WindowManager.LayoutParams controls the size, position, and behaviour
        // of the overlay window.
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            // Width: full screen for SCAM, 90% width for SUSPICIOUS
            fullAttention
                ? WindowManager.LayoutParams.MATCH_PARENT
                : WindowManager.LayoutParams.WRAP_CONTENT,

            // Height: wrap content (size driven by the layout XML)
            WindowManager.LayoutParams.WRAP_CONTENT,

            // Type: TYPE_APPLICATION_OVERLAY is the modern (API 26+) type for
            // drawing above other apps. Older type TYPE_PHONE is deprecated.
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE,

            // Flags: NOT_TOUCH_MODAL allows touches outside our window to pass
            // through to the app below (important for the small SUSPICIOUS banner).
            // For SCAM (fullAttention), we intentionally block touches.
            fullAttention
                ? 0
                : WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,

            // Format: TRANSLUCENT supports rounded corners and alpha in our layouts.
            PixelFormat.TRANSLUCENT
        );

        // Gravity: centre for SCAM, top-centre banner for SUSPICIOUS
        params.gravity = fullAttention ? Gravity.CENTER : (Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        if (!fullAttention) {
            params.y = 80; // pixels from top — gives room for status bar
        }

        windowManager.addView(view, params);
        currentOverlayView = view;
        Log.d(TAG, "Overlay added to WindowManager (fullAttention=" + fullAttention + ")");
    }

    /**
     * Safely removes the current overlay from the screen.
     * Checks that the view is non-null and is actually attached to a window
     * before calling removeView — calling removeView on a detached view crashes.
     */
    private void dismissCurrentOverlay() {
        // Cancel any pending active countdowns first to prevent memory leak/crash
        if (activeCountdownRunnable != null) {
            mainHandler.removeCallbacks(activeCountdownRunnable);
            activeCountdownRunnable = null;
        }

        // Clear Alert Mode globally (returns theme to safe green)
        try {
            LocalDataStore.getInstance().setAlertModeActive(false);
        } catch (Exception ignored) {}

        // ── Update centralized theme state ──────────────────────────────────────
        // When an alert is dismissed, return to Safe Mode unless another alert
        // is pending. ThemeManager handles persistence to SharedPreferences.
        ThemeManager.dismissAlert(appContext);

        if (currentOverlayView != null) {
            try {
                // isAttachedToWindow() returns true if the view is currently
                // visible on screen and managed by WindowManager.
                if (currentOverlayView.isAttachedToWindow()) {
                    windowManager.removeView(currentOverlayView);
                    Log.d(TAG, "Overlay dismissed.");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error removing overlay: " + e.getMessage());
            } finally {
                currentOverlayView = null;
            }
        }
    }

    /**
     * Returns true if the app currently has "Draw over other apps" permission.
     * Uses the correct API for the device's Android version.
     */
    private boolean canDrawOverlays() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return android.provider.Settings.canDrawOverlays(appContext);
        }
        // On API < 23, SYSTEM_ALERT_WINDOW is granted at install for all apps
        return true;
    }

    // =========================================================================
    // TextToSpeech
    // =========================================================================

    /**
     * Initialises the Android TextToSpeech engine.
     * TTS initialisation is asynchronous — we set ttsReady=true in the callback
     * so we only speak after the engine has finished loading.
     */
    private void initTextToSpeech() {
        tts = new TextToSpeech(appContext, status -> {
            if (status == TextToSpeech.SUCCESS) {
                // Set language to the device's default locale.
                // For Indian users this often falls back to en-IN or en-US.
                int result = tts.setLanguage(Locale.getDefault());

                if (result == TextToSpeech.LANG_MISSING_DATA
                        || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // Fallback to English if device locale isn't supported
                    tts.setLanguage(Locale.ENGLISH);
                }

                // Slow down speech slightly — easier for elderly users to follow.
                tts.setSpeechRate(0.85f);
                ttsReady = true;
                Log.d(TAG, "TextToSpeech ready.");
            } else {
                Log.e(TAG, "TextToSpeech initialisation failed with status: " + status);
            }
        });
    }

    /**
     * Speaks the given text aloud using TextToSpeech.
     * QUEUE_FLUSH means new speech immediately replaces anything currently playing.
     * This is correct behaviour — a SCAM alert should interrupt any previous speech.
     *
     * @param text The plain-language string to read aloud.
     */
    private void speakAloud(String text) {
        if (ttsReady && tts != null) {
            // QUEUE_FLUSH stops current speech and starts this immediately.
            // The null utteranceId means we don't need completion callbacks.
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
            Log.d(TAG, "TTS speaking: " + text.substring(0, Math.min(60, text.length())) + "...");
        } else {
            Log.w(TAG, "TTS not ready — skipping speech for: " + text.substring(0, Math.min(40, text.length())));
        }
    }

    /**
     * Releases TextToSpeech resources.
     * Call this from ScamShieldApp.onTerminate() or if you ever need to
     * shut down the manager cleanly. In practice, Android apps rarely
     * reach onTerminate() — this is here for correctness.
     */
    public void shutdown() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
            ttsReady = false;
        }
        dismissCurrentOverlay();
        Log.d(TAG, "ScamAlertManager shut down.");
    }
}
