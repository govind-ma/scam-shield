package com.scamshield.app.ui;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.util.Base64;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.scamshield.app.BuildConfig;
import com.scamshield.app.R;
import com.scamshield.app.data.LocalDataStore;
import com.scamshield.app.engine.DetectionResult;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Locale;

/**
 * ChatAssistantActivity — Cyber Help Agent
 * Package: com.scamshield.app.ui
 *
 * Multimodal AI assistant powered by Gemini 1.5 Flash.
 * Three input modes:
 *   1. Text  — user types or pastes a message/link → Gemini text analysis
 *   2. Image — user picks a screenshot from gallery → Gemini vision analysis
 *   3. Voice — user taps mic, speaks once → SpeechRecognizer → auto-sent as text
 *
 * The local RuleBasedEngine still runs in parallel for text inputs so that
 * SCAM / SUSPICIOUS detections can trigger the overlay and update LocalDataStore
 * without waiting for the Gemini network response.
 */
public class ChatAssistantActivity extends AppCompatActivity {

    private static final String TAG = "ScamShield.Chat";

    // Request codes for activity results
    private static final int REQ_IMAGE_PICK  = 1001;
    private static final int REQ_SPEECH      = 1002;
    private static final int REQ_AUDIO_PERM  = 2001;
    private static final int REQ_STORAGE_PERM = 2002;

    // Max JPEG size sent to Gemini (keep base64 payload reasonable)
    private static final int MAX_IMAGE_PX = 800;

    private GeminiApiClient geminiClient;

    private LinearLayout layoutChatThread;
    private ScrollView   chatScrollView;
    private EditText     etInput;
    private ImageView    btnAttach;
    private ImageView    btnMic;
    private ImageView    btnSend;
    private View         chatTopbar;
    private View         layoutChipsContainer;
    private boolean      chipsVisible = true; // Hide after first message

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_assistant);

        geminiClient = new GeminiApiClient(BuildConfig.GEMINI_API_KEY);

        chatTopbar         = findViewById(R.id.chat_topbar);
        layoutChatThread   = findViewById(R.id.layout_chat_thread);
        chatScrollView     = findViewById(R.id.chat_scroll_view);
        etInput            = findViewById(R.id.et_chat_input);
        btnAttach          = findViewById(R.id.btn_attach);
        btnMic             = findViewById(R.id.btn_mic);
        btnSend            = findViewById(R.id.btn_chat_send);
        layoutChipsContainer = findViewById(R.id.layout_chips_container);

        btnSend.setOnClickListener(v -> handleSendText());
        btnAttach.setOnClickListener(v -> handleAttachImage());
        btnMic.setOnClickListener(v -> handleVoiceInput());

        // Quick action chips
        findViewById(R.id.chip_check_message).setOnClickListener(v -> handleChipTap("Check a message: "));
        findViewById(R.id.chip_got_scammed).setOnClickListener(v -> handleChipTap("I got scammed. Help me: "));
        findViewById(R.id.chip_teach_me).setOnClickListener(v -> handleChipTap("Teach me about scams"));
    }

    @Override
    protected void onResume() {
        super.onResume();
        NavigationHelper.setupBottomNavigation(this, NavigationHelper.TAB_CHAT);
        NavigationHelper.applyTopBarTheme(this, chatTopbar);

        // Tint send button to match mode
        try {
            boolean isAlert = LocalDataStore.getInstance().isAlertModeActive();
            int tint = isAlert
                ? ContextCompat.getColor(this, R.color.scam_red)
                : ContextCompat.getColor(this, R.color.safe_green);
            btnSend.setBackgroundTintList(android.content.res.ColorStateList.valueOf(tint));
        } catch (Exception ignored) {}
    }

    // =========================================================================
    // 1. Text input → Gemini text analysis
    // =========================================================================

    /**
     * Handle quick action chip tap — populate input and send.
     */
    private void handleChipTap(String chipText) {
        etInput.setText(chipText);
        handleSendText();
    }

    private void handleSendText() {
        String input = etInput.getText().toString().trim();
        if (input.isEmpty()) {
            Toast.makeText(this, "Please type a message first.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Hide chips after first message
        if (chipsVisible && layoutChipsContainer != null) {
            layoutChipsContainer.setVisibility(View.GONE);
            chipsVisible = false;
        }

        etInput.setText("");
        addUserTextBubble(input);

        // Show "thinking" placeholder while API call is in flight
        View thinkingBubble = addThinkingBubble();

        // Do NOT run the local scam engine on chat input.
        // Users will describe scam scenarios ("I got scammed", "I sent money") to get help.
        // Scanning their own words would wrongly fire the SCAM overlay on them.

        // Call Gemini API
        geminiClient.askText(input, new GeminiApiClient.ApiCallback() {
            @Override
            public void onSuccess(String reply) {
                replaceThinkingBubble(thinkingBubble, reply, false);
                scrollToBottom();
            }

            @Override
            public void onError(String errorMessage) {
                replaceThinkingBubble(thinkingBubble, "⚠️ " + errorMessage, true);
                scrollToBottom();
            }
        });
    }

    // =========================================================================
    // 2. Image/screenshot → Gemini vision analysis
    // =========================================================================

    private void handleAttachImage() {
        // On API 33+ use READ_MEDIA_IMAGES; below 33 use READ_EXTERNAL_STORAGE
        String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            ? Manifest.permission.READ_MEDIA_IMAGES
            : Manifest.permission.READ_EXTERNAL_STORAGE;

        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{permission}, REQ_STORAGE_PERM);
            return;
        }
        launchImagePicker();
    }

    private void launchImagePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "Select screenshot"), REQ_IMAGE_PICK);
    }

    // =========================================================================
    // 3. Voice input → SpeechRecognizer → auto-send as text
    // =========================================================================

    private void handleVoiceInput() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO_PERM);
            return;
        }
        launchSpeechRecognizer();
    }

    private void launchSpeechRecognizer() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Describe the message or ask your question…");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        try {
            startActivityForResult(intent, REQ_SPEECH);
            btnMic.setAlpha(0.5f); // visual feedback: listening
        } catch (Exception e) {
            Toast.makeText(this, "Voice input is not available on this device.", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "SpeechRecognizer unavailable: " + e.getMessage());
        }
    }

    // =========================================================================
    // Activity results — image + voice
    // =========================================================================

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != Activity.RESULT_OK || data == null) {
            btnMic.setAlpha(1.0f); // restore mic if cancelled
            return;
        }

        if (requestCode == REQ_SPEECH) {
            // Voice result → fill EditText → auto-send
            btnMic.setAlpha(1.0f);
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                String spokenText = results.get(0);
                etInput.setText(spokenText);
                handleSendText(); // auto-send immediately
            }
        } else if (requestCode == REQ_IMAGE_PICK) {
            Uri imageUri = data.getData();
            if (imageUri != null) {
                processSelectedImage(imageUri);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (requestCode == REQ_AUDIO_PERM && granted)   launchSpeechRecognizer();
        if (requestCode == REQ_STORAGE_PERM && granted) launchImagePicker();
        if (!granted) Toast.makeText(this, "Permission needed for this feature.", Toast.LENGTH_SHORT).show();
    }

    // =========================================================================
    // Image processing — compress → base64 → Gemini vision
    // =========================================================================

    private void processSelectedImage(Uri imageUri) {
        try {
            // Decode and downscale
            InputStream is = getContentResolver().openInputStream(imageUri);
            Bitmap original = BitmapFactory.decodeStream(is);
            if (original == null) { Toast.makeText(this, "Could not read image.", Toast.LENGTH_SHORT).show(); return; }

            Bitmap scaled = scaleBitmap(original, MAX_IMAGE_PX);
            original.recycle();

            // Compress to JPEG byte array
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            scaled.compress(Bitmap.CompressFormat.JPEG, 80, baos);
            byte[] jpegBytes = baos.toByteArray();
            String base64Jpeg = Base64.encodeToString(jpegBytes, Base64.NO_WRAP);

            // Show image thumbnail in chat + "thinking" bubble
            addUserImageBubble(scaled);
            View thinkingBubble = addThinkingBubble();

            // Send to Gemini vision
            geminiClient.askWithImage(base64Jpeg, new GeminiApiClient.ApiCallback() {
                @Override
                public void onSuccess(String reply) {
                    replaceThinkingBubble(thinkingBubble, reply, false);
                    scrollToBottom();
                }

                @Override
                public void onError(String errorMessage) {
                    replaceThinkingBubble(thinkingBubble, "⚠️ " + errorMessage, true);
                    scrollToBottom();
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Image processing failed: " + e.getMessage());
            Toast.makeText(this, "Could not process image. Please try again.", Toast.LENGTH_SHORT).show();
        }
    }

    /** Scales a bitmap so its longest side is ≤ maxPx, preserving aspect ratio. */
    private Bitmap scaleBitmap(Bitmap src, int maxPx) {
        int w = src.getWidth(), h = src.getHeight();
        if (w <= maxPx && h <= maxPx) return src;
        float scale = (float) maxPx / Math.max(w, h);
        return Bitmap.createScaledBitmap(src, (int)(w * scale), (int)(h * scale), true);
    }

    // =========================================================================
    // Local rule engine — parallel analysis for overlay + DataStore
    // =========================================================================

    /**
     * Runs the local RuleBasedEngine on text input in parallel with the Gemini call.
     * If SCAM or SUSPICIOUS: triggers the overlay + DataStore log.
     * Does NOT show a separate chat response — Gemini's reply is shown instead.
     */
    private void runLocalEngine(String input) {
        try {
            DetectionResult result = com.scamshield.app.ScamShieldApp.getEngine().analyze(input, "SMS");
            if (result.verdict != DetectionResult.Verdict.SAFE) {
                ScamAlertManager.getInstance().onResult(result);
            }
        } catch (Exception e) {
            Log.w(TAG, "Local engine failed: " + e.getMessage());
        }
    }

    // =========================================================================
    // Chat bubble builders
    // =========================================================================

    /** User text bubble — right-aligned, gray background. */
    private void addUserTextBubble(String message) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setGravity(Gravity.END);
        row.setPadding(0, 0, 0, 14);

        TextView label = makeLabel("You", Gravity.END);
        TextView bubble = new TextView(this);
        bubble.setText(message);
        bubble.setTextSize(17);
        bubble.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        bubble.setBackgroundColor(ContextCompat.getColor(this, R.color.bg_tertiary));
        bubble.setPadding(36, 26, 36, 26);

        row.addView(label);
        row.addView(bubble);
        layoutChatThread.addView(row);
        scrollToBottom();
    }

    /** User image bubble — right-aligned with thumbnail. */
    private void addUserImageBubble(Bitmap bitmap) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setGravity(Gravity.END);
        row.setPadding(0, 0, 0, 14);

        TextView label = makeLabel("You (screenshot)", Gravity.END);

        ImageView imageView = new ImageView(this);
        imageView.setImageBitmap(bitmap);
        imageView.setAdjustViewBounds(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            (int)(200 * getResources().getDisplayMetrics().density),
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        imageView.setLayoutParams(lp);

        row.addView(label);
        row.addView(imageView);
        layoutChatThread.addView(row);
        scrollToBottom();
    }

    /**
     * Adds a temporary "thinking" bubble while the Gemini API call is in flight.
     * Returns the View so it can be replaced when the response arrives.
     */
    private View addThinkingBubble() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setGravity(Gravity.START);
        row.setPadding(0, 4, 0, 14);
        row.setTag("thinking_bubble");

        TextView label = makeLabel("🤖 Cyber Help Agent", Gravity.START);
        label.setTextColor(getResources().getColor(R.color.text_secondary, getTheme()));

        TextView bubble = new TextView(this);
        bubble.setText("●●●");
        bubble.setTextSize(17);
        bubble.setTextColor(getResources().getColor(R.color.text_secondary, getTheme()));
        bubble.setBackgroundColor(getResources().getColor(R.color.bg_secondary, getTheme()));
        bubble.setPadding(16, 12, 16, 12);
        // Add fade animation
        bubble.setAlpha(0.6f);

        row.addView(label);
        row.addView(bubble);
        layoutChatThread.addView(row);
        scrollToBottom();
        return row;
    }

    /**
     * Replaces the thinking bubble View with the actual Gemini response.
     * If isError=true, uses a muted warning style.
     */
    private void replaceThinkingBubble(View thinkingBubble, String responseText, boolean isError) {
        int index = layoutChatThread.indexOfChild(thinkingBubble);
        layoutChatThread.removeView(thinkingBubble);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setGravity(Gravity.START);
        row.setPadding(0, 4, 0, 14);

        TextView label = makeLabel("🤖 Cyber Help Agent", Gravity.START);
        label.setTextColor(getResources().getColor(R.color.text_secondary, getTheme()));

        TextView bubble = new TextView(this);
        bubble.setText(responseText);
        bubble.setTextSize(17);
        bubble.setLineSpacing(4, 1.0f);
        bubble.setPadding(16, 12, 16, 12);
        bubble.setTextColor(getResources().getColor(R.color.text_primary, getTheme()));

        if (isError) {
            bubble.setBackgroundColor(Color.parseColor("#FFEBEE"));
        } else {
            bubble.setBackgroundColor(getResources().getColor(R.color.bg_secondary, getTheme()));
        }

        row.addView(label);
        row.addView(bubble);

        if (index >= 0 && index <= layoutChatThread.getChildCount()) {
            layoutChatThread.addView(row, index);
        } else {
            layoutChatThread.addView(row);
        }
    }

    private TextView makeLabel(String text, int gravity) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(11);
        tv.setTextColor(getResources().getColor(R.color.text_secondary, getTheme()));
        tv.setGravity(gravity);
        tv.setPadding(4, 0, 4, 4);
        return tv;
    }

    private void scrollToBottom() {
        chatScrollView.post(() -> chatScrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }
}
