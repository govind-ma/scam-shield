package com.scamshield.app.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ViewFlipper;

import androidx.appcompat.app.AppCompatActivity;

import com.scamshield.app.R;

import java.util.ArrayList;
import java.util.List;

/**
 * QuizActivity — Part E: Interactive Quiz
 * Package: com.scamshield.app.ui
 *
 * An interactive, simple, high-contrast quiz that allows users to test their
 * scam spotting capabilities. It shows one scenario message at a time.
 * The user taps "SCAM" or "SAFE".
 *
 * Immediate color-coded feedback with an explanation is shown.
 * Once the 10 scenarios are completed, a summary score page is displayed.
 */
public class QuizActivity extends AppCompatActivity {

    private static class QuizItem {
        final String scenarioText;
        final boolean isScam; // true = SCAM, false = SAFE
        final String explanation;

        QuizItem(String scenarioText, boolean isScam, String explanation) {
            this.scenarioText = scenarioText;
            this.isScam = isScam;
            this.explanation = explanation;
        }
    }

    private final List<QuizItem> quizItems = new ArrayList<>();
    private int currentItemIndex = 0;
    private int score = 0;

    // UI elements
    private ViewFlipper viewFlipper;
    private TextView tvProgress;
    private TextView tvScenarioText;
    private Button btnScam;
    private Button btnSafe;

    private LinearLayout layoutFeedback;
    private TextView tvFeedbackTitle;
    private TextView tvFeedbackExplanation;
    private Button btnNext;

    private TextView tvFinalScore;
    private TextView tvScoreFeedback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiz);

        initializeQuizData();

        viewFlipper = findViewById(R.id.quiz_view_flipper);
        tvProgress = findViewById(R.id.tv_quiz_progress);
        tvScenarioText = findViewById(R.id.tv_scenario_text);
        btnScam = findViewById(R.id.btn_answer_scam);
        btnSafe = findViewById(R.id.btn_answer_safe);

        layoutFeedback = findViewById(R.id.layout_feedback);
        tvFeedbackTitle = findViewById(R.id.tv_feedback_title);
        tvFeedbackExplanation = findViewById(R.id.tv_feedback_explanation);
        btnNext = findViewById(R.id.btn_next_scenario);

        tvFinalScore = findViewById(R.id.tv_final_score);
        tvScoreFeedback = findViewById(R.id.tv_score_feedback);

        btnScam.setOnClickListener(v -> handleAnswer(true));
        btnSafe.setOnClickListener(v -> handleAnswer(false));

        btnNext.setOnClickListener(v -> showNextScenario());

        Button btnRetake = findViewById(R.id.btn_retake_quiz);
        btnRetake.setOnClickListener(v -> resetQuiz());

        Button btnExit = findViewById(R.id.btn_exit_quiz);
        btnExit.setOnClickListener(v -> finish());

        showScenario(0);
    }

    private void initializeQuizData() {
        quizItems.clear();
        quizItems.add(new QuizItem(
                "URGENT: Your SBI account is blocked! Share your OTP immediately to verify: http://sbi-secure-update.xyz/verify",
                true,
                "Banks will NEVER send warning messages with unofficial links or ask you to share your OTP."
        ));
        quizItems.add(new QuizItem(
                "Hi Dad, I lost my phone. I need Rs 5,000 urgently to buy a new one, please transfer to this UPI: relative@ybl",
                true,
                "Always call your relative directly on their regular, known number to verify any sudden emergency requests."
        ));
        quizItems.add(new QuizItem(
                "Congratulations! You won Rs 10 Lakhs in the Diwali Lucky Draw! Send a Rs 2,500 tax processing fee to claim it now.",
                true,
                "Legitimate lucky draws and lotteries never ask you to pay any 'processing fee' or 'taxes' before sending the prize."
        ));
        quizItems.add(new QuizItem(
                "Your Zomato delivery agent is arriving in 5 minutes with your order. Track it online at http://zoma.to/t/abcde",
                false,
                "This is a standard order confirmation and tracking update pointing to an official domain."
        ));
        quizItems.add(new QuizItem(
                "DEAR CUSTOMER, YOUR SIM CARD KYC UPDATE IS PENDING. CALL OFFICE AT 9876543210 IMMEDIATELY OR THE SIM WILL BE DEACTIVATED.",
                true,
                "Mobile operators never ask you to call random private numbers to complete SIM verification or threaten immediate blocks."
        ));
        quizItems.add(new QuizItem(
                "Dear customer, you have received a cash refund of Rs 500. Enter your UPI PIN to accept the cash refund now.",
                true,
                "You NEVER need to enter your UPI PIN or scan a QR code to receive a refund or receive money."
        ));
        quizItems.add(new QuizItem(
                "Alert: Rs 1,500 has been debited from your account XXXX5678 at Amazon India. Current Balance: Rs 12,450.",
                false,
                "This is a standard, safe transaction confirmation sent by your bank to update you on account activity."
        ));
        quizItems.add(new QuizItem(
                "Hi! Your order #8821 has been shipped. Track it at http://myshop-delivery-track.in/8821. Expected by 9 Jul.",
                false,
                "Standard tracking message containing order numbers and shipping dates without any scam urgency flags."
        ));
        quizItems.add(new QuizItem(
                "WARNING: A login attempt was made on your NetBanking from New Delhi. If this was not you, block card at http://fakebank-alert.in",
                true,
                "The link directs to an unofficial website designed to steal your NetBanking username and password."
        ));
        quizItems.add(new QuizItem(
                "Your electricity bill of Rs 1,420 is due by tomorrow. You can pay online at https://billpay.electricity.gov.in",
                false,
                "This is a safe notification referencing an official government payment domain (.gov.in) with no scam indicators."
        ));
    }

    private void showScenario(int index) {
        currentItemIndex = index;
        QuizItem item = quizItems.get(index);

        tvProgress.setText("Scenario " + (index + 1) + " of " + quizItems.size());
        tvScenarioText.setText(item.scenarioText);

        // Hide feedback, show buttons
        layoutFeedback.setVisibility(View.GONE);
        btnScam.setEnabled(true);
        btnSafe.setEnabled(true);
        btnScam.setVisibility(View.VISIBLE);
        btnSafe.setVisibility(View.VISIBLE);
    }

    private void handleAnswer(boolean userChoice) {
        // Disable answering again
        btnScam.setEnabled(false);
        btnSafe.setEnabled(false);

        QuizItem item = quizItems.get(currentItemIndex);
        boolean isCorrect = (userChoice == item.isScam);

        if (isCorrect) {
            score++;
            tvFeedbackTitle.setText("✓ Correct!");
            tvFeedbackTitle.setTextColor(Color.parseColor("#2E7D32")); // dark green
            layoutFeedback.setBackgroundColor(Color.parseColor("#E8F5E9")); // light green
        } else {
            tvFeedbackTitle.setText("❌ Incorrect");
            tvFeedbackTitle.setTextColor(Color.parseColor("#B71C1C")); // dark red
            layoutFeedback.setBackgroundColor(Color.parseColor("#FFEBEE")); // light red
        }

        tvFeedbackExplanation.setText(item.explanation);
        layoutFeedback.setVisibility(View.VISIBLE);

        // Hide main action buttons to draw attention to feedback
        btnScam.setVisibility(View.GONE);
        btnSafe.setVisibility(View.GONE);

        // If it's the last question, change next button text
        if (currentItemIndex == quizItems.size() - 1) {
            btnNext.setText("Show Results  ➔");
        } else {
            btnNext.setText("Next Scenario  ➔");
        }
    }

    private void showNextScenario() {
        if (currentItemIndex < quizItems.size() - 1) {
            showScenario(currentItemIndex + 1);
        } else {
            showResults();
        }
    }

    private void showResults() {
        viewFlipper.setDisplayedChild(1);
        tvProgress.setText("Results");

        tvFinalScore.setText("You scored: " + score + " / " + quizItems.size());

        // Custom feedback text based on score
        if (score == quizItems.size()) {
            tvScoreFeedback.setText("🏆 Excellent! You got a perfect score. You are fully capable of protecting yourself against online scams!");
        } else if (score >= 7) {
            tvScoreFeedback.setText("👍 Good job! You spotted most of the scams. Keep reading our tips to learn how to catch the rest.");
        } else {
            tvScoreFeedback.setText("⚠️ Be careful! You missed several scam warnings. We highly recommend reviewing our learning topics before making payments.");
        }
    }

    private void resetQuiz() {
        score = 0;
        viewFlipper.setDisplayedChild(0);
        showScenario(0);
    }
}
