package com.scamshield.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
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
 * Lets the user paste or type suspicious text (a message, a link, a phone number)
 * and manually run it through the DetectionEngine.
 *
 * Useful for checking a message BEFORE deciding to reply to it — rather than
 * waiting for the sensor to catch it automatically.
 *
 * Part E addition: after a SCAM or SUSPICIOUS result, a red "Get Help Now" button
 * appears that takes the user directly into the Recovery Mode flow (IGotScammedActivity).
 */
public class CheckSomethingActivity extends AppCompatActivity {

    /**
     * Optional Intent extra: pre-fill the input field with this text.
     * Used by SmsInboxAdapter when the user taps a message row on HomeActivity.
     * Example: intent.putExtra(EXTRA_PREFILL_TEXT, smsBody);
     */
    public static final String EXTRA_PREFILL_TEXT = "com.scamshield.app.PREFILL_TEXT";

    private DetectionEngine engine;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_check_something);

        // Use the shared global engine from ScamShieldApp — same instance
        // that SmsReceiver uses. No wasteful duplicate creation.
        engine = ScamShieldApp.getEngine();

        EditText etInput         = findViewById(R.id.et_check_input);
        Button   btnCheck        = findViewById(R.id.btn_run_check);
        TextView tvResult        = findViewById(R.id.tv_check_result);
        Button   btnGetHelp      = findViewById(R.id.btn_get_help_now);

        // ── Pre-fill from Intent extra (e.g. when opened from SMS inbox row tap) ─
        String prefill = getIntent().getStringExtra(EXTRA_PREFILL_TEXT);
        if (prefill != null && !prefill.isEmpty()) {
            etInput.setText(prefill);
            etInput.setSelection(etInput.getText().length());
        }

        // ── Handle Share-to-Check: text shared from another app via ACTION_SEND ─
        if (Intent.ACTION_SEND.equals(getIntent().getAction())
                && "text/plain".equals(getIntent().getType())) {
            String shared = getIntent().getStringExtra(Intent.EXTRA_TEXT);
            if (shared != null && !shared.isEmpty()) {
                etInput.setText(shared);
                etInput.setSelection(shared.length());
                // Auto-trigger the check so the result appears immediately
                btnCheck.performClick();
            }
        }

        // ── Run check ─────────────────────────────────────────────────────────
        btnCheck.setOnClickListener(v -> {
            String input = etInput.getText().toString().trim();
            if (input.isEmpty()) {
                tvResult.setText("Please type or paste the message you want to check.");
                btnGetHelp.setVisibility(View.GONE);
                return;
            }

            // Analyse using "SMS" as source type — the user is manually entering text
            DetectionResult result = engine.analyze(input, "SMS");

            // Display the result in plain language
            String display =
                "Result: " + result.verdict + "\n\n"
                + result.reason + "\n\n"
                + "(Confidence: " + result.confidenceScore + "/100)";
            tvResult.setText(display);

            // Also pass through ScamAlertManager so the full alert/TTS fires
            // and DataStore logs the result (Part D integration).
            ScamAlertManager.getInstance().onResult(result);

            // ── Part E: show "Get Help Now" for SCAM or SUSPICIOUS results ────
            // The button is hidden for SAFE results so it doesn't cause alarm
            // when a normal message was checked.
            if (result.verdict == DetectionResult.Verdict.SCAM
                    || result.verdict == DetectionResult.Verdict.SUSPICIOUS) {
                btnGetHelp.setVisibility(View.VISIBLE);
            } else {
                btnGetHelp.setVisibility(View.GONE);
            }
        });

        // ── Entry point 3: "Get Help Now" → IGotScammedActivity ──────────────
        btnGetHelp.setOnClickListener(v -> {
            startActivity(new Intent(this, IGotScammedActivity.class));
        });

        // Bottom nav wired in onResume() so theme updates correctly on re-entry.
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Highlight Home tab — this screen is accessed from Home.
        // Called here (not just onCreate) so the nav bar accent color updates
        // correctly if the theme switches between Safe Mode and Alert Mode while
        // the user was on another screen.
        NavigationHelper.setupBottomNavigation(this, NavigationHelper.TAB_HOME);
    }
}
