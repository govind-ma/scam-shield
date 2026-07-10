package com.scamshield.app.ui;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.scamshield.app.R;
import com.scamshield.app.ScamShieldApp;
import com.scamshield.app.engine.DetectionEngine;
import com.scamshield.app.engine.DetectionResult;

/**
 * CheckSomethingActivity
 * Package: com.scamshield.app.ui
 *
 * Two entry points:
 *
 *   1. Internal navigation — HomeActivity / SmsInboxAdapter pass
 *      EXTRA_PREFILL_TEXT to pre-fill the input field.
 *
 *   2. System share sheet (ACTION_SEND, mimeType="text/plain") — any app
 *      (WhatsApp, Gmail, Chrome, etc.) can share selected text directly into
 *      Scam Shield. The shared text is placed in the input field and the check
 *      is auto-triggered immediately, with a "Checking shared message..."
 *      banner shown at the top until the result is ready.
 *
 * Part E: after a SCAM or SUSPICIOUS result, a "Get Help Now" button appears
 * that takes the user into IGotScammedActivity (Recovery Mode).
 */
public class CheckSomethingActivity extends AppCompatActivity {

    /**
     * Optional Intent extra: pre-fill the input field with this text.
     * Used by SmsInboxAdapter when the user taps a message row on HomeActivity.
     */
    public static final String EXTRA_PREFILL_TEXT = "com.scamshield.app.PREFILL_TEXT";

    private DetectionEngine engine;

    // ── View references ───────────────────────────────────────────────────────
    private LinearLayout bannerShared;
    private EditText     etInput;
    private Button       btnCheck;
    private LinearLayout cardResult;
    private View         vAccentBar;
    private TextView     tvVerdictPill;
    private TextView     tvReason;
    private TextView     tvScore;
    private Button       btnGetHelp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_check_something);

        // Use the shared global engine — same instance SmsReceiver uses.
        engine = ScamShieldApp.getEngine();

        bannerShared  = findViewById(R.id.banner_shared);
        etInput       = findViewById(R.id.et_check_input);
        btnCheck      = findViewById(R.id.btn_run_check);
        cardResult    = findViewById(R.id.card_result);
        vAccentBar    = findViewById(R.id.v_result_accent_bar);
        tvVerdictPill = findViewById(R.id.tv_result_verdict_pill);
        tvReason      = findViewById(R.id.tv_result_reason);
        tvScore       = findViewById(R.id.tv_result_score);
        btnGetHelp    = findViewById(R.id.btn_get_help_now);

        // ── Detect whether we were opened from the system share sheet ─────────
        Intent incoming = getIntent();
        boolean fromShare = Intent.ACTION_SEND.equals(incoming.getAction())
                && "text/plain".equals(incoming.getType());

        String initialText = null;

        if (fromShare) {
            // Shared from another app via ACTION_SEND
            initialText = incoming.getStringExtra(Intent.EXTRA_TEXT);
        } else {
            // Internal navigation (HomeActivity / SmsInboxAdapter)
            initialText = incoming.getStringExtra(EXTRA_PREFILL_TEXT);
        }

        if (initialText != null && !initialText.isEmpty()) {
            etInput.setText(initialText);
            etInput.setSelection(etInput.getText().length());

            if (fromShare) {
                // Show the "Checking shared message..." banner immediately
                bannerShared.setVisibility(View.VISIBLE);
                // Auto-trigger the detection without waiting for the user to tap
                performCheck(initialText, /* hideBannerWhenDone= */ true);
            }
        }

        // ── Manual check button ───────────────────────────────────────────────
        btnCheck.setOnClickListener(v -> {
            HapticManager.buttonTap(v);
            String input = etInput.getText().toString().trim();
            if (input.isEmpty()) {
                tvReason.setText("Please type or paste the message you want to check.");
                cardResult.setVisibility(View.VISIBLE);
                btnGetHelp.setVisibility(View.GONE);
                return;
            }
            performCheck(input, /* hideBannerWhenDone= */ false);
        });

        // ── "Get Help Now" ────────────────────────────────────────────────────
        btnGetHelp.setOnClickListener(v ->
                startActivity(new Intent(this, IGotScammedActivity.class)));

        // ── Bottom navigation ─────────────────────────────────────────────────
        NavigationHelper.setupBottomNavigation(this, NavigationHelper.TAB_HOME);
    }

    /**
     * Runs {@code text} through the DetectionEngine and populates the result
     * card.  If {@code hideBanner} is true the share-intent banner is hidden
     * once the result is ready.
     *
     * Does NOT modify RuleBasedEngine or DetectionEngine internals.
     */
    private void performCheck(String text, boolean hideBanner) {
        // Analyse — "MANUAL" source type distinguishes user-initiated checks
        // from live SMS so statistics and logs stay meaningful.
        DetectionResult result = engine.analyze(text, "MANUAL");

        // ── Populate result card ──────────────────────────────────────────────
        int verdictColor;
        String verdictLabel;

        switch (result.verdict) {
            case SCAM:
                verdictColor = Color.parseColor("#FF1744"); // scam_red
                verdictLabel = "SCAM";
                break;
            case SUSPICIOUS:
                verdictColor = Color.parseColor("#FFC107"); // suspicious_amber
                verdictLabel = "SUSPICIOUS";
                break;
            default: // SAFE
                verdictColor = Color.parseColor("#00E676"); // safe_green
                verdictLabel = "SAFE";
                break;
        }

        vAccentBar.setBackgroundColor(verdictColor);
        tvVerdictPill.setText(verdictLabel);
        tvVerdictPill.setBackgroundColor(verdictColor);
        // Keep pill text dark for readability on the bright badge colour
        tvVerdictPill.setTextColor(Color.parseColor("#0D1B2A")); // bg_primary

        tvReason.setText(result.reason);
        tvScore.setText("Confidence score: " + result.confidenceScore + " / 100");

        cardResult.setVisibility(View.VISIBLE);

        // ── Show "Get Help Now" for actionable results only ───────────────────
        if (result.verdict == DetectionResult.Verdict.SCAM
                || result.verdict == DetectionResult.Verdict.SUSPICIOUS) {
            btnGetHelp.setVisibility(View.VISIBLE);
        } else {
            btnGetHelp.setVisibility(View.GONE);
        }

        // ── Pass through ScamAlertManager for overlay / TTS / DataStore ──────
        ScamAlertManager.getInstance().onResult(result);

        // ── Dismiss the share banner now that the result is displayed ─────────
        if (hideBanner) {
            bannerShared.setVisibility(View.GONE);
        }
    }
}
