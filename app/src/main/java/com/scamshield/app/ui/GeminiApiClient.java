package com.scamshield.app.ui;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * GeminiApiClient
 * Package: com.scamshield.app.ui
 *
 * Thin OkHttp wrapper around the Gemini REST API.
 * Supports:
 *   - Text-only prompts (askText)
 *   - Multimodal prompts with an inline base64 JPEG image (askWithImage)
 *
 * All network calls are async (OkHttp enqueue). Results are posted back to
 * the main thread so callers can safely update UI directly in the callback.
 *
 * The Cyber Help Agent system prompt is baked in here so every request
 * automatically carries the correct persona regardless of which screen calls it.
 */
public class GeminiApiClient {

    private static final String TAG = "ScamShield.Gemini";

    // Gemini 2.5 Flash — fast, multimodal, confirmed available on this key
    private static final String BASE_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent";

    private static final MediaType JSON_TYPE =
        MediaType.parse("application/json; charset=utf-8");

    /**
     * Cyber Help Agent system prompt.
     * Injected into every Gemini request as the "system" role so the model
     * always responds as a calm, simple, India-focused scam safety specialist.
     */
    static final String SYSTEM_PROMPT =
        "You are Cyber Help Agent, a calm and knowledgeable specialist in scam detection " +
        "and cyber safety for Indian users. " +
        "IMPORTANT RULES: " +
        "(1) Do NOT start responses with greetings like 'Namaste', 'Hello', 'Hi', or any salutation. Jump straight to the answer. " +
        "(2) Do NOT repeat greetings in follow-up messages. " +
        "(3) Never use 'Namaste' — it feels robotic and repetitive. " +
        "You speak in simple, clear language suitable for senior citizens and first-time smartphone users. " +
        "You help users who have received suspicious SMS messages, calls, UPI/payment requests, or who have already been scammed. " +
        "When a user says they sent money by mistake or got scammed, respond with empathy and clear next steps (who to call, what to do immediately). " +
        "You always explain clearly: (1) what the situation is, (2) what to do next. " +
        "You never ask the user for personal information, OTPs, PINs, or bank details. " +
        "Keep responses concise — 3 to 6 sentences. " +
        "Use simple bullet points when listing steps. " +
        "Always end with ONE clear action line (e.g. 'Call your bank now.' or 'Do NOT click the link.').";

    /** Callback interface — delivered on the main (UI) thread. */
    public interface ApiCallback {
        void onSuccess(String reply);
        void onError(String errorMessage);
    }

    private final OkHttpClient client;
    private final String apiKey;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public GeminiApiClient(String apiKey) {
        this.apiKey = apiKey;
        this.client = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
    }

    // =========================================================================
    // Text-only request
    // =========================================================================

    /**
     * Sends a plain-text message to Gemini with the Cyber Help Agent system prompt.
     *
     * @param userMessage  The user's typed or speech-to-text input.
     * @param callback     Delivered on the main thread.
     */
    public void askText(String userMessage, ApiCallback callback) {
        try {
            JSONObject body = buildTextRequestBody(userMessage);
            executeAsync(body, callback);
        } catch (JSONException e) {
            Log.e(TAG, "Failed to build text request: " + e.getMessage());
            mainHandler.post(() -> callback.onError("Could not prepare the request. Please try again."));
        }
    }

    // =========================================================================
    // Multimodal image + text request
    // =========================================================================

    /**
     * Sends a base64-encoded JPEG image plus a scam-analysis prompt to Gemini.
     * Uses the vision/multimodal capability of Gemini 1.5 Flash.
     *
     * @param base64Jpeg   JPEG image bytes encoded as a base64 string (no data: prefix).
     * @param callback     Delivered on the main thread.
     */
    public void askWithImage(String base64Jpeg, ApiCallback callback) {
        try {
            JSONObject body = buildImageRequestBody(base64Jpeg);
            executeAsync(body, callback);
        } catch (JSONException e) {
            Log.e(TAG, "Failed to build image request: " + e.getMessage());
            mainHandler.post(() -> callback.onError("Could not process the image. Please try again."));
        }
    }

    // =========================================================================
    // Request body builders
    // =========================================================================

    private JSONObject buildTextRequestBody(String userMessage) throws JSONException {
        // Shape:
        // {
        //   "system_instruction": { "parts": [{ "text": "..." }] },
        //   "contents": [{ "role": "user", "parts": [{ "text": "..." }] }],
        //   "generationConfig": { "temperature": 0.3, "maxOutputTokens": 512 }
        // }

        JSONObject systemPart = new JSONObject().put("text", SYSTEM_PROMPT);
        JSONObject systemInstruction = new JSONObject()
            .put("parts", new JSONArray().put(systemPart));

        JSONObject userPart = new JSONObject().put("text", userMessage);
        JSONObject userContent = new JSONObject()
            .put("role", "user")
            .put("parts", new JSONArray().put(userPart));

        JSONObject genConfig = new JSONObject()
            .put("temperature", 0.3);

        return new JSONObject()
            .put("system_instruction", systemInstruction)
            .put("contents", new JSONArray().put(userContent))
            .put("generationConfig", genConfig);
    }

    private JSONObject buildImageRequestBody(String base64Jpeg) throws JSONException {
        String imagePrompt =
            "Analyze this screenshot for scam indicators. Look for: " +
            "suspicious sender names or numbers, urgent threatening language, " +
            "requests for OTP or PIN or passwords, fake payment or prize claims, " +
            "suspicious or misspelled links, impersonation of banks or government. " +
            "Explain your findings in simple language suitable for an elderly Indian user.";

        JSONObject textPart = new JSONObject().put("text", imagePrompt);
        JSONObject inlineData = new JSONObject()
            .put("mime_type", "image/jpeg")
            .put("data", base64Jpeg);
        JSONObject imagePart = new JSONObject().put("inline_data", inlineData);

        JSONObject systemPart = new JSONObject().put("text", SYSTEM_PROMPT);
        JSONObject systemInstruction = new JSONObject()
            .put("parts", new JSONArray().put(systemPart));

        JSONObject userContent = new JSONObject()
            .put("role", "user")
            .put("parts", new JSONArray().put(imagePart).put(textPart));

        JSONObject genConfig = new JSONObject()
            .put("temperature", 0.3);

        return new JSONObject()
            .put("system_instruction", systemInstruction)
            .put("contents", new JSONArray().put(userContent))
            .put("generationConfig", genConfig);
    }

    // =========================================================================
    // Async HTTP execution + response parsing
    // =========================================================================

    private void executeAsync(JSONObject requestBody, ApiCallback callback) {
        String url = BASE_URL + "?key=" + apiKey;

        Request request = new Request.Builder()
            .url(url)
            .post(RequestBody.create(requestBody.toString(), JSON_TYPE))
            .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Network failure: " + e.getMessage());
                mainHandler.post(() -> callback.onError(
                    "Could not reach the server. Please check your internet connection."));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String bodyStr = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    Log.e(TAG, "API error " + response.code() + ": " + bodyStr);
                    String msg = "The assistant is unavailable right now (error " + response.code() + "). "
                               + "Please try again in a moment.";
                    mainHandler.post(() -> callback.onError(msg));
                    return;
                }
                try {
                    // Gemini response shape:
                    // { "candidates": [{ "content": { "parts": [{ "text": "..." }] } }] }
                    JSONObject json      = new JSONObject(bodyStr);
                    JSONArray candidates = json.getJSONArray("candidates");
                    JSONObject content   = candidates.getJSONObject(0).getJSONObject("content");
                    String text          = content.getJSONArray("parts").getJSONObject(0).getString("text");
                    Log.d(TAG, "Gemini reply (" + text.length() + " chars): " + text);
                    mainHandler.post(() -> callback.onSuccess(text.trim()));
                } catch (JSONException e) {
                    Log.e(TAG, "Failed to parse response: " + e.getMessage() + " body=" + bodyStr);
                    mainHandler.post(() -> callback.onError(
                        "Received an unexpected response. Please try again."));
                }
            }
        });
    }
}
