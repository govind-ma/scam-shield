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

        // ── Wire bottom navigation ──────────────────────────────────────────────
        // Highlight Home tab since this screen is accessed from Home
        NavigationHelper.setupBottomNavigation(this, NavigationHelper.TAB_HOME);
    }
}
