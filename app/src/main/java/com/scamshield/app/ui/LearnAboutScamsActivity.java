package com.scamshield.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ViewFlipper;

import androidx.appcompat.app.AppCompatActivity;

import com.scamshield.app.R;

/**
 * LearnAboutScamsActivity
 * Package: com.scamshield.app.ui
 *
 * A browsable list of learning topics, styled in a simple, high-contrast,
 * elderly-friendly layout. Uses a ViewFlipper to manage navigation between
 * checklist screens and card layouts.
 *
 * Links directly to:
 * - IGotScammedActivity (for recovery mode help)
 * - QuizActivity (to test scam spotting skills)
 */
public class LearnAboutScamsActivity extends AppCompatActivity {

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

        // Take the Quiz button
        Button btnTakeQuiz = findViewById(R.id.btn_take_quiz);
        btnTakeQuiz.setOnClickListener(v -> {
            Intent quizIntent = new Intent(this, QuizActivity.class);
            startActivity(quizIntent);
        });
    }

    private void setupTopicButtons() {
        // Topic 1: How to make a safe payment
        View btnSafePayment = findViewById(R.id.btn_topic_safe_payment);
        btnSafePayment.setOnClickListener(v -> showScreen(1, "🛡️ Safe Payments"));

        // Topic 2: What to keep in mind before paying
        View btnCheckBeforePay = findViewById(R.id.btn_topic_check_before_pay);
        btnCheckBeforePay.setOnClickListener(v -> showScreen(2, "🤔 Before You Pay"));

        // Topic 3: What to do if a scam happens (links to Recovery Mode)
        View btnScamRecovery = findViewById(R.id.btn_topic_scam_recovery);
        btnScamRecovery.setOnClickListener(v -> {
            Intent intent = new Intent(this, IGotScammedActivity.class);
            startActivity(intent);
        });

        // Topic 4: Common scam patterns
        View btnCommonPatterns = findViewById(R.id.btn_topic_common_patterns);
        btnCommonPatterns.setOnClickListener(v -> showScreen(3, "🔍 Scam Patterns"));
    }

    private void setupBackButtons() {
        Button btnBack1 = findViewById(R.id.btn_back_from_safe_payment);
        btnBack1.setOnClickListener(v -> showScreen(0, "📖 Learn & Protect"));

        Button btnBack2 = findViewById(R.id.btn_back_from_check_before_pay);
        btnBack2.setOnClickListener(v -> showScreen(0, "📖 Learn & Protect"));

        Button btnBack3 = findViewById(R.id.btn_back_from_common_patterns);
        btnBack3.setOnClickListener(v -> showScreen(0, "📖 Learn & Protect"));
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
        TextView tvMenuSubtitle = findViewById(R.id.tv_learn_menu_subtitle);
        android.view.View btnSafe = findViewById(R.id.btn_topic_safe_payment);
        android.view.View btnCheck = findViewById(R.id.btn_topic_check_before_pay);
        android.view.View btnScam = findViewById(R.id.btn_topic_scam_recovery);
        android.view.View btnPatterns = findViewById(R.id.btn_topic_common_patterns);
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
            if (tvMenuSubtitle != null) tvMenuSubtitle.setTextColor(android.graphics.Color.parseColor("#B0BEC5"));
            
            if (btnSafe != null) btnSafe.setBackgroundResource(R.drawable.card_dark_gray);
            if (btnCheck != null) btnCheck.setBackgroundResource(R.drawable.card_dark_gray);
            if (btnScam != null) btnScam.setBackgroundResource(R.drawable.card_dark_gray);
            if (btnPatterns != null) btnPatterns.setBackgroundResource(R.drawable.card_dark_gray);
            
            if (quizCard != null) quizCard.setBackgroundResource(R.drawable.card_dark_gray);
            if (tvQuizTitle != null) tvQuizTitle.setTextColor(android.graphics.Color.parseColor("#FF5252"));
            if (tvQuizDesc != null) tvQuizDesc.setTextColor(android.graphics.Color.parseColor("#B0BEC5"));

            if (safePaymentCard != null) safePaymentCard.setBackgroundResource(R.drawable.card_dark_gray);
            if (checkBeforePayCard != null) checkBeforePayCard.setBackgroundResource(R.drawable.card_dark_gray);
            if (cardPattern1 != null) cardPattern1.setBackgroundResource(R.drawable.card_dark_gray);
            if (cardPattern2 != null) cardPattern2.setBackgroundResource(R.drawable.card_dark_gray);
            if (cardPattern3 != null) cardPattern3.setBackgroundResource(R.drawable.card_dark_gray);
            if (cardPattern4 != null) cardPattern4.setBackgroundResource(R.drawable.card_dark_gray);

            setTextsColorRecursive(viewFlipper, android.graphics.Color.WHITE);
        } else {
            root.setBackgroundColor(android.graphics.Color.parseColor("#F7F8F6"));
            if (tvMenuSubtitle != null) tvMenuSubtitle.setTextColor(android.graphics.Color.parseColor("#555555"));
            
            if (btnSafe != null) btnSafe.setBackgroundResource(R.drawable.card_white);
            if (btnCheck != null) btnCheck.setBackgroundResource(R.drawable.card_white);
            if (btnScam != null) btnScam.setBackgroundResource(R.drawable.card_light_red);
            if (btnPatterns != null) btnPatterns.setBackgroundResource(R.drawable.card_white);
            
            if (quizCard != null) quizCard.setBackgroundResource(R.drawable.card_white);
            if (tvQuizTitle != null) tvQuizTitle.setTextColor(android.graphics.Color.parseColor("#004D40"));
            if (tvQuizDesc != null) tvQuizDesc.setTextColor(android.graphics.Color.parseColor("#555555"));

            if (safePaymentCard != null) safePaymentCard.setBackgroundResource(R.drawable.card_white);
            if (checkBeforePayCard != null) checkBeforePayCard.setBackgroundResource(R.drawable.card_white);
            if (cardPattern1 != null) cardPattern1.setBackgroundResource(R.drawable.card_white);
            if (cardPattern2 != null) cardPattern2.setBackgroundResource(R.drawable.card_white);
            if (cardPattern3 != null) cardPattern3.setBackgroundResource(R.drawable.card_white);
            if (cardPattern4 != null) cardPattern4.setBackgroundResource(R.drawable.card_white);

            setTextsColorRecursive(viewFlipper, android.graphics.Color.parseColor("#1A1A1A"));
        }
    }

    private void setTextsColorRecursive(android.view.View view, int color) {
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                setTextsColorRecursive(group.getChildAt(i), color);
            }
        } else if (view instanceof TextView) {
            TextView tv = (TextView) view;
            if (tv.getId() != R.id.tv_learn_title && tv.getId() != R.id.btn_back_from_safe_payment 
                && tv.getId() != R.id.btn_back_from_check_before_pay && tv.getId() != R.id.btn_back_from_common_patterns
                && tv.getId() != R.id.btn_take_quiz) {
                tv.setTextColor(color);
            }
        }
    }

    @Override
    public void onBackPressed() {
        // If we are showing a sub-screen, go back to the main topic menu (index 0)
        if (viewFlipper.getDisplayedChild() > 0) {
            showScreen(0, "📖 Learn & Protect");
        } else {
            super.onBackPressed();
        }
    }
}
