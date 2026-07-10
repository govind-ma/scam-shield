package com.scamshield.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import androidx.appcompat.app.AppCompatActivity;

import com.scamshield.app.R;

import java.util.Calendar;

/**
 * LearnAboutScamsActivity
 * Package: com.scamshield.app.ui
 *
 * A browsable list of learning topics with a 2x2 Bento Grid layout and
 * a Daily Challenge card on Screen 0. Uses a ViewFlipper to manage navigation
 * between checklist screens and card layouts.
 *
 * Links directly to:
 * - IGotScammedActivity (for recovery mode help)
 * - QuizActivity (to test scam spotting skills)
 */
public class LearnAboutScamsActivity extends AppCompatActivity {

    // ── Daily Challenge data ────────────────────────────────────────────────
    private static final String[] CHALLENGE_MESSAGES = {
        "Your SBI account will be BLOCKED. Share your OTP immediately to verify: 1234. Call 9876543210 now.",
        "CONGRATULATIONS! You have won Rs 10,00,000 in KBC Lucky Draw. Send Rs 500 processing fee to claim. Reply WIN to 56161.",
        "Dear Customer, your electricity bill is overdue. Pay now via this link to avoid disconnection: bit.ly/payELEC",
        "Hi Mom, I lost my phone. This is my new number +919999000011. Please send Rs 2000 urgently, I am in trouble.",
        "Your Aadhaar card has been linked to illegal activity. Contact cybercrime officer on 9871234567 immediately to avoid arrest."
    };

    private static final boolean[] CHALLENGE_IS_SCAM = { true, true, true, true, true };

    private static final String[] CHALLENGE_EXPLANATIONS = {
        "Correct! This was a scam. Legitimate banks never ask you to share your OTP. Sharing your OTP can drain your account.",
        "Correct! This was a scam. You cannot win a lottery you never entered. Processing fee requests are a classic trick.",
        "Correct! This was a scam. Electricity departments do not send shortened links. Always pay via the official website or app.",
        "Correct! This was a scam. Always call the family member on their known number before sending money to a new number.",
        "Correct! This was a scam. No government officer will ask you to call a mobile number. Official contact is always through official channels."
    };

    private static final String[] SAFE_WRONG_EXPLANATION = {
        "That was actually a scam! Banks never ask for your OTP.",
        "That was actually a scam! No real lottery asks for a processing fee.",
        "That was actually a scam! Electricity bills use official websites, not short links.",
        "That was actually a scam! Always verify by calling on the known number first.",
        "That was actually a scam! Government officers do not call on mobile numbers like this."
    };

    private int currentChallengeIndex = 0;

    // ── Activity references ─────────────────────────────────────────────────
    private ViewFlipper viewFlipper;
    private TextView tvTopbarTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_learn);

        viewFlipper = findViewById(R.id.learn_view_flipper);
        tvTopbarTitle = findViewById(R.id.tv_learn_title);

        setupTopicButtons();
        setupBackButtons();
        setupDailyChallenge();

        // Take the Quiz button
        Button btnTakeQuiz = findViewById(R.id.btn_take_quiz);
        btnTakeQuiz.setOnClickListener(v -> {
            Intent quizIntent = new Intent(this, QuizActivity.class);
            startActivity(quizIntent);
        });
    }

    // ── Daily Challenge ─────────────────────────────────────────────────────
    private void setupDailyChallenge() {
        // Pick challenge based on day of week (rotates Mon-Sun)
        int dayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        currentChallengeIndex = dayOfWeek % CHALLENGE_MESSAGES.length;

        TextView tvMessage = findViewById(R.id.tv_challenge_message);
        TextView tvDay = findViewById(R.id.tv_challenge_day);

        if (tvMessage != null) {
            tvMessage.setText(CHALLENGE_MESSAGES[currentChallengeIndex]);
        }
        if (tvDay != null) {
            String[] dayNames = { "", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat" };
            tvDay.setText(dayNames[dayOfWeek]);
        }

        Button btnScam = findViewById(R.id.btn_challenge_scam);
        Button btnSafe = findViewById(R.id.btn_challenge_safe);

        if (btnScam != null) {
            btnScam.setOnClickListener(v -> evaluateChallenge(true));
        }
        if (btnSafe != null) {
            btnSafe.setOnClickListener(v -> evaluateChallenge(false));
        }
    }

    private void evaluateChallenge(boolean userSaysScam) {
        boolean actuallyScam = CHALLENGE_IS_SCAM[currentChallengeIndex];
        String explanation;
        if (userSaysScam == actuallyScam) {
            explanation = CHALLENGE_EXPLANATIONS[currentChallengeIndex];
        } else {
            explanation = userSaysScam
                ? "Not quite! This message is actually safe. Look for specific red flags like OTP requests or urgency."
                : SAFE_WRONG_EXPLANATION[currentChallengeIndex];
        }
        Toast.makeText(this, explanation, Toast.LENGTH_LONG).show();
    }

    // ── Topic Buttons ───────────────────────────────────────────────────────
    private void setupTopicButtons() {
        // Topic 1: How to make a safe payment
        View btnSafePayment = findViewById(R.id.btn_topic_safe_payment);
        btnSafePayment.setOnClickListener(v -> showScreen(1, "\uD83D\uDEE1\uFE0F Safe Payments"));

        // Topic 2: What to keep in mind before paying
        View btnCheckBeforePay = findViewById(R.id.btn_topic_check_before_pay);
        btnCheckBeforePay.setOnClickListener(v -> showScreen(2, "\u26A0\uFE0F Before You Pay"));

        // Topic 3: What to do if a scam happens (links to Recovery Mode)
        View btnScamRecovery = findViewById(R.id.btn_topic_scam_recovery);
        btnScamRecovery.setOnClickListener(v -> {
            Intent intent = new Intent(this, IGotScammedActivity.class);
            startActivity(intent);
        });

        // Topic 4: Common scam patterns
        View btnCommonPatterns = findViewById(R.id.btn_topic_common_patterns);
        btnCommonPatterns.setOnClickListener(v -> showScreen(3, "\uD83D\uDD0D Scam Patterns"));
    }

    // ── Back Buttons ────────────────────────────────────────────────────────
    private void setupBackButtons() {
        Button btnBack1 = findViewById(R.id.btn_back_from_safe_payment);
        btnBack1.setOnClickListener(v -> showScreen(0, "\uD83D\uDCD6 Learn & Protect"));

        Button btnBack2 = findViewById(R.id.btn_back_from_check_before_pay);
        btnBack2.setOnClickListener(v -> showScreen(0, "\uD83D\uDCD6 Learn & Protect"));

        Button btnBack3 = findViewById(R.id.btn_back_from_common_patterns);
        btnBack3.setOnClickListener(v -> showScreen(0, "\uD83D\uDCD6 Learn & Protect"));
    }

    private void showScreen(int index, String title) {
        viewFlipper.setDisplayedChild(index);
        tvTopbarTitle.setText(title);
    }

    @Override
    protected void onResume() {
        super.onResume();
        NavigationHelper.setupBottomNavigation(this, NavigationHelper.TAB_LEARN);
        NavigationHelper.applyTopBarTheme(this, findViewById(R.id.learn_topbar));

        boolean isAlert = false;
        try {
            isAlert = com.scamshield.app.data.LocalDataStore.getInstance().isAlertModeActive();
        } catch (Exception ignored) {}
        applyLearnTheme(isAlert);
    }

    private void applyLearnTheme(boolean isAlert) {
        android.view.View root = findViewById(R.id.learn_root_layout);
        android.view.View quizCard = findViewById(R.id.quiz_banner_card);
        TextView tvQuizTitle = findViewById(R.id.tv_quiz_card_title);
        TextView tvQuizDesc = findViewById(R.id.tv_quiz_card_desc);

        android.view.View safePaymentCard = findViewById(R.id.learn_card_safe_payment);
        android.view.View checkBeforePayCard = findViewById(R.id.learn_card_check_before_pay);
        android.view.View cardPattern1 = findViewById(R.id.learn_card_pattern1);
        android.view.View cardPattern2 = findViewById(R.id.learn_card_pattern2);
        android.view.View cardPattern3 = findViewById(R.id.learn_card_pattern3);
        android.view.View cardPattern4 = findViewById(R.id.learn_card_pattern4);

        if (root == null) return;

        if (isAlert) {
            root.setBackgroundColor(android.graphics.Color.parseColor("#2C2C2A"));

            if (quizCard != null) quizCard.setBackgroundResource(R.drawable.card_dark_gray);
            if (tvQuizTitle != null) tvQuizTitle.setTextColor(android.graphics.Color.parseColor("#FF5252"));
            if (tvQuizDesc != null) tvQuizDesc.setTextColor(android.graphics.Color.parseColor("#B0BEC5"));

            if (safePaymentCard != null) safePaymentCard.setBackgroundResource(R.drawable.card_dark_gray);
            if (checkBeforePayCard != null) checkBeforePayCard.setBackgroundResource(R.drawable.card_dark_gray);
            if (cardPattern1 != null) cardPattern1.setBackgroundResource(R.drawable.card_dark_gray);
            if (cardPattern2 != null) cardPattern2.setBackgroundResource(R.drawable.card_dark_gray);
            if (cardPattern3 != null) cardPattern3.setBackgroundResource(R.drawable.card_dark_gray);
            if (cardPattern4 != null) cardPattern4.setBackgroundResource(R.drawable.card_dark_gray);
        } else {
            root.setBackgroundColor(android.graphics.Color.parseColor("#0D1B2A"));

            if (quizCard != null) quizCard.setBackgroundResource(R.drawable.bg_bento_card);
            if (tvQuizTitle != null) tvQuizTitle.setTextColor(android.graphics.Color.WHITE);
            if (tvQuizDesc != null) tvQuizDesc.setTextColor(android.graphics.Color.parseColor("#B0BEC5"));

            if (safePaymentCard != null) safePaymentCard.setBackgroundResource(R.drawable.card_white);
            if (checkBeforePayCard != null) checkBeforePayCard.setBackgroundResource(R.drawable.card_white);
            if (cardPattern1 != null) cardPattern1.setBackgroundResource(R.drawable.card_white);
            if (cardPattern2 != null) cardPattern2.setBackgroundResource(R.drawable.card_white);
            if (cardPattern3 != null) cardPattern3.setBackgroundResource(R.drawable.card_white);
            if (cardPattern4 != null) cardPattern4.setBackgroundResource(R.drawable.card_white);
        }
    }

    @Override
    public void onBackPressed() {
        // If we are showing a sub-screen, go back to the main topic menu (index 0)
        if (viewFlipper.getDisplayedChild() > 0) {
            showScreen(0, "\uD83D\uDCD6 Learn & Protect");
        } else {
            super.onBackPressed();
        }
    }
}
