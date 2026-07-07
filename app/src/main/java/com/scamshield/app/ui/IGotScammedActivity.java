package com.scamshield.app.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ViewFlipper;

import androidx.appcompat.app.AppCompatActivity;

import com.scamshield.app.R;
import com.scamshield.app.data.LocalDataStore;

/**
 * IGotScammedActivity — Part E: Recovery Mode
 * Package: com.scamshield.app.ui
 *
 * A calm, elderly-friendly guided flow for someone who has just been scammed.
 * Uses a ViewFlipper to present 3 sequential screens without launching new Activities:
 *
 *   Screen 0 — "Which bank do you use?"
 *              Tap your bank → stores the bank name + helpline number
 *
 *   Screen 1 — "What happened?"
 *              Tap the closest option → stores the situation text for custom advice
 *
 *   Screen 2 — Results
 *              Shows:
 *              • "Call Bank Helpline" button (pre-filled with real number from DataStore)
 *              • "Call 1930" button (National Cyber Crime Helpline)
 *              • "Open cybercrime.gov.in" button
 *              • Situation-specific advice (e.g. for OTP: "Change your PIN immediately")
 *
 * ── Entry points (all three wired) ─────────────────────────────────────────
 *  1. HomeActivity     → btn_i_got_scammed   (big red button on home screen)
 *  2. FloatingAssistantService → btn_menu_scammed (floating bubble menu)
 *  3. CheckSomethingActivity   → btn_get_help_now (result screen after manual check)
 *
 * ── Bank helpline lookup ────────────────────────────────────────────────────
 *  Uses LocalDataStore.getInstance().getBankHelpline(bankName) — the DataStore
 *  interface method locked in PROJECT_CONTEXT.md. Falls back to "Call your bank"
 *  if the bank isn't in the map (e.g. user selected "Other").
 */
public class IGotScammedActivity extends AppCompatActivity {

    private static final String TAG = "ScamShield.Recovery";

    // ── State: what the user selected ─────────────────────────────────────────
    private String selectedBankName     = "your bank";
    private String selectedBankHelpline = null;    // null → show generic message
    private String selectedSituation    = "";

    // ── UI references ──────────────────────────────────────────────────────────
    private ViewFlipper viewFlipper;
    private TextView    tvScreenTitle;
    private TextView    tvStepIndicator;

    // ── Screen titles for the top bar ─────────────────────────────────────────
    private static final String[] SCREEN_TITLES = {
        "🆘  I Got Scammed",
        "🆘  I Got Scammed",
        "🆘  What To Do Now"
    };
    private static final String[] STEP_LABELS = {
        "Step 1 of 3",
        "Step 2 of 3",
        "Step 3 of 3"
    };

    // =========================================================================
    // Lifecycle
    // =========================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_i_got_scammed);

        viewFlipper     = findViewById(R.id.view_flipper);
        tvScreenTitle   = findViewById(R.id.tv_screen_title);
        tvStepIndicator = findViewById(R.id.tv_step_indicator);

        wireScreen0_BankPicker();
        wireScreen1_WhatHappened();
        wireScreen2_Results();

        showScreen(0);
    }

    // =========================================================================
    // Screen 0 — Bank picker
    // =========================================================================

    private void wireScreen0_BankPicker() {
        wireBankButton(R.id.btn_bank_sbi,     "SBI");
        wireBankButton(R.id.btn_bank_hdfc,    "HDFC");
        wireBankButton(R.id.btn_bank_icici,   "ICICI");
        wireBankButton(R.id.btn_bank_axis,    "Axis");
        wireBankButton(R.id.btn_bank_paytm,   "Paytm");
        wireBankButton(R.id.btn_bank_gpay,    "Google Pay");
        wireBankButton(R.id.btn_bank_phonepe, "PhonePe");
        wireBankButton(R.id.btn_bank_other,   null);   // null → "Other / Not sure"
    }

    /**
     * Sets a click listener on a bank button.
     * @param buttonId   The view ID of the bank button.
     * @param bankKey    The bank name to look up in LocalDataStore, or null for "Other".
     */
    private void wireBankButton(int buttonId, String bankKey) {
        View btn = findViewById(buttonId);
        if (btn == null) return;

        btn.setOnClickListener(v -> {
            if (bankKey != null) {
                selectedBankName = bankKey;
                // Look up the real helpline from DataStore (Part D integration)
                try {
                    selectedBankHelpline = LocalDataStore.getInstance().getBankHelpline(bankKey);
                } catch (Exception e) {
                    selectedBankHelpline = null;
                }
            } else {
                // "Other / Not sure"
                selectedBankName     = "your bank";
                selectedBankHelpline = null;
            }
            showScreen(1);
        });
    }

    // =========================================================================
    // Screen 1 — What happened?
    // =========================================================================

    private void wireScreen1_WhatHappened() {
        View btnCall    = findViewById(R.id.btn_what_call);
        View btnMessage = findViewById(R.id.btn_what_message);
        View btnMoney   = findViewById(R.id.btn_what_money);
        View btnOtp     = findViewById(R.id.btn_what_otp);
        Button btnBack  = findViewById(R.id.btn_back_to_bank);

        btnCall.setOnClickListener(v -> {
            selectedSituation = "call";
            showScreen(2);
        });
        btnMessage.setOnClickListener(v -> {
            selectedSituation = "message";
            showScreen(2);
        });
        btnMoney.setOnClickListener(v -> {
            selectedSituation = "money";
            showScreen(2);
        });
        btnOtp.setOnClickListener(v -> {
            selectedSituation = "otp";
            showScreen(2);
        });
        btnBack.setOnClickListener(v -> showScreen(0));
    }

    // =========================================================================
    // Screen 2 — Results (call buttons + advice)
    // =========================================================================

    private void wireScreen2_Results() {
        // These buttons' click listeners reference selectedBankHelpline which
        // is set at runtime, so we wire them with lambdas that read the field
        // at the time of click (not at wire time).

        Button btnCallBank = findViewById(R.id.btn_call_bank);
        btnCallBank.setOnClickListener(v -> {
            String number = selectedBankHelpline != null
                    ? selectedBankHelpline : "1800111109";  // RBI generic fallback
            dialNumber(number);
        });

        Button btnCall1930 = findViewById(R.id.btn_call_1930);
        btnCall1930.setOnClickListener(v -> dialNumber("1930"));

        Button btnReport = findViewById(R.id.btn_report_online);
        btnReport.setOnClickListener(v -> {
            Intent web = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://cybercrime.gov.in"));
            startActivity(web);
        });

        Button btnBack = findViewById(R.id.btn_back_to_what);
        btnBack.setOnClickListener(v -> showScreen(1));
    }

    // =========================================================================
    // Screen transitions
    // =========================================================================

    /**
     * Switches the ViewFlipper to the given screen index (0, 1, or 2).
     * Also updates the top bar title and step label.
     * For screen 2, populates the dynamic content (bank name, advice text).
     */
    private void showScreen(int index) {
        viewFlipper.setDisplayedChild(index);
        tvScreenTitle.setText(SCREEN_TITLES[index]);
        tvStepIndicator.setText(STEP_LABELS[index]);

        if (index == 2) {
            populateResultsScreen();
        }
    }

    /**
     * Fills in the dynamic parts of Screen 2 just before it is shown.
     * Called every time the user navigates to Screen 2, so the content
     * always reflects the latest selectedBankName + selectedSituation.
     */
    private void populateResultsScreen() {
        // ── Bank call button label ─────────────────────────────────────────────
        Button btnCallBank = findViewById(R.id.btn_call_bank);
        TextView tvBankInstruction = findViewById(R.id.tv_bank_instruction);

        if (selectedBankHelpline != null) {
            btnCallBank.setText("📞  Call " + selectedBankName + "  (" + selectedBankHelpline + ")");
            tvBankInstruction.setText(
                "Tell them what happened and ask them to block any transfers "
                + "or freeze your account immediately.");
        } else {
            // "Other" or unknown bank
            btnCallBank.setText("📞  Call your bank helpline");
            tvBankInstruction.setText(
                "Find your bank's toll-free number on the back of your debit/credit card "
                + "or on your bank's official website. Tell them to block all transfers.");
        }

        // ── Situation-specific advice ──────────────────────────────────────────
        TextView tvAdvice = findViewById(R.id.tv_situation_advice);
        tvAdvice.setText(getAdviceForSituation(selectedSituation));
    }

    /**
     * Returns plain-language, situation-specific advice for elderly users.
     * Written calmly — the goal is to reassure and guide, not to alarm.
     */
    private String getAdviceForSituation(String situation) {
        switch (situation) {
            case "call":
                return "• Do not call back the number that called you — it may be fake.\n\n"
                     + "• Do not give your OTP, PIN, or Aadhaar number to anyone on the phone"
                     + " — your bank will NEVER ask for these.\n\n"
                     + "• Tell a trusted family member what happened as soon as possible.";

            case "message":
                return "• Do not tap any links in the message.\n\n"
                     + "• Do not reply to the message.\n\n"
                     + "• Screenshot the message — you may need it for your complaint.\n\n"
                     + "• Tell a trusted family member what happened.";

            case "money":
                return "• Call your bank RIGHT NOW — the faster you call,"
                     + " the higher the chance the bank can reverse the transfer.\n\n"
                     + "• Note the exact amount, time, and the account number you sent it to"
                     + " — you will need this for the complaint.\n\n"
                     + "• Do NOT send any more money, even if someone says it will get your"
                     + " money back — this is another scam.";

            case "otp":
                return "• Call your bank RIGHT NOW and ask them to block your card and account.\n\n"
                     + "• Change your UPI PIN and banking app password immediately.\n\n"
                     + "• Check your account for any transactions you did not make.\n\n"
                     + "• Do not share another OTP with anyone — not even someone claiming"
                     + " to be from the bank.";

            default:
                return "• Do not share any more personal information or money with anyone.\n\n"
                     + "• Call your bank immediately.\n\n"
                     + "• Tell a trusted family member what happened.";
        }
    }

    // =========================================================================
    // Utility
    // =========================================================================

    /**
     * Opens the phone dialler with the given number pre-filled.
     * Uses ACTION_DIAL (not ACTION_CALL) — the user still taps the green
     * call button themselves. This is intentional: it avoids needing
     * CALL_PHONE permission and gives the user a chance to confirm.
     */
    private void dialNumber(String number) {
        // Strip spaces and dashes so the dialler gets a clean number
        String cleanNumber = number.replaceAll("[\\s\\-]", "");
        Intent dial = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + cleanNumber));
        startActivity(dial);
    }
}
