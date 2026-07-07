package com.scamshield.app.ui;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.scamshield.app.R;
import com.scamshield.app.ScamShieldApp;
import com.scamshield.app.engine.DetectionEngine;
import com.scamshield.app.engine.DetectionResult;

/**
 * ChatAssistantActivity — Part E: Interactive Chat Assistant
 * Package: com.scamshield.app.ui
 *
 * Provides a text-based dialogue interface where users can consult an AI assistant
 * about scams. Runs queries through the DetectionEngine.
 *
 * ── Theming (Safe/Alert Mode) ────────────────────────────────────────────────
 * Dynamically updates its header and tab highlights between Green and Red
 * on the fly when a scam is flagged.
 */
public class ChatAssistantActivity extends AppCompatActivity {

    private DetectionEngine engine;

    private LinearLayout layoutChatThread;
    private EditText etInput;
    private Button btnSend;
    private View chatTopbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_assistant);

        engine = ScamShieldApp.getEngine();

        layoutChatThread = findViewById(R.id.layout_chat_thread);
        etInput = findViewById(R.id.et_chat_input);
        btnSend = findViewById(R.id.btn_chat_send);
        chatTopbar = findViewById(R.id.chat_topbar);

        btnSend.setOnClickListener(v -> handleSendMessage());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Setup central bottom tab wiring & colors
        NavigationHelper.setupBottomNavigation(this, NavigationHelper.TAB_CHAT);
        NavigationHelper.applyTopBarTheme(this, chatTopbar);

        // Update send button tint based on theme status
        try {
            boolean isAlert = com.scamshield.app.data.LocalDataStore.getInstance().isAlertModeActive();
            btnSend.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                isAlert ? Color.parseColor("#B71C1C") : Color.parseColor("#00695C")
            ));
        } catch (Exception ignored) {}
    }

    private void handleSendMessage() {
        String input = etInput.getText().toString().trim();
        if (input.isEmpty()) {
            Toast.makeText(this, "Please type a message first.", Toast.LENGTH_SHORT).show();
            return;
        }

        // 1. Add User bubble to chat
        addUserMessage(input);
        etInput.setText("");

        // 2. Analyze input
        DetectionResult result = engine.analyze(input, "SMS");

        // 3. Add Assistant response bubble
        addAssistantResponse(result);
    }

    private void addUserMessage(String message) {
        LinearLayout userLayout = new LinearLayout(this);
        userLayout.setOrientation(LinearLayout.VERTICAL);
        userLayout.setGravity(Gravity.END);
        userLayout.setPadding(0, 0, 0, 16);

        TextView label = new TextView(this);
        label.setText("You");
        label.setTextSize(14);
        label.setTextColor(Color.GRAY);
        label.setPadding(0, 0, 8, 4);

        TextView bubble = new TextView(this);
        bubble.setText(message);
        bubble.setTextSize(18);
        bubble.setTextColor(Color.BLACK);
        bubble.setBackgroundColor(Color.parseColor("#E0E0E0"));
        bubble.setPadding(16, 12, 16, 12);

        userLayout.addView(label);
        userLayout.addView(bubble);
        layoutChatThread.addView(userLayout);
    }

    private void addAssistantResponse(DetectionResult result) {
        LinearLayout assistantLayout = new LinearLayout(this);
        assistantLayout.setOrientation(LinearLayout.VERTICAL);
        assistantLayout.setGravity(Gravity.START);
        assistantLayout.setPadding(0, 16, 0, 16);

        TextView label = new TextView(this);
        label.setText("🛡️ Scam Shield Assistant");
        label.setTextSize(14);
        label.setTypeface(null, android.graphics.Typeface.BOLD);
        label.setTextColor(Color.parseColor("#004D40"));
        label.setPadding(8, 0, 0, 4);

        TextView bubble = new TextView(this);
        bubble.setTextSize(18);
        bubble.setTextColor(Color.WHITE);
        bubble.setPadding(16, 12, 16, 12);

        boolean isScamOrSuspicious = (result.verdict == DetectionResult.Verdict.SCAM 
                || result.verdict == DetectionResult.Verdict.SUSPICIOUS);

        if (result.verdict == DetectionResult.Verdict.SCAM) {
            bubble.setText("🚨 WARNING: This looks like a SCAM!\n\n" + result.reason);
            bubble.setBackgroundColor(Color.parseColor("#B71C1C")); // Red
        } else if (result.verdict == DetectionResult.Verdict.SUSPICIOUS) {
            bubble.setText("⚠️ WARNING: This looks SUSPICIOUS!\n\n" + result.reason);
            bubble.setBackgroundColor(Color.parseColor("#E65100")); // Orange
        } else {
            bubble.setText("✓ Safe Message\n\nThis looks safe. I did not detect any standard scam warning flags.");
            bubble.setBackgroundColor(Color.parseColor("#2E7D32")); // Green
        }

        assistantLayout.addView(label);
        assistantLayout.addView(bubble);
        layoutChatThread.addView(assistantLayout);

        // 4. Add interactive "Get Help Now" button if scam/suspicious (Entry point auto-suggestion)
        if (isScamOrSuspicious) {
            Button btnHelp = new Button(this);
            btnHelp.setText("🆘 Get Help Now");
            btnHelp.setTextSize(17);
            btnHelp.setTextColor(Color.WHITE);
            btnHelp.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#B71C1C")));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 56 * 2 // 56dp height in pixels roughly
            );
            params.setMargins(0, 8, 0, 16);
            btnHelp.setOnClickListener(v -> {
                Intent i = new Intent(this, IGotScammedActivity.class);
                startActivity(i);
            });
            layoutChatThread.addView(btnHelp, params);

            // Trigger ScamAlertManager to update global theme and show overlay warnings
            ScamAlertManager.getInstance().onResult(result);
            
            // Refresh local Activity theme colors dynamically
            onResume();
        }
    }
}
