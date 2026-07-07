package com.scamshield.app.sensors;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

import com.scamshield.app.engine.DetectionEngine;
import com.scamshield.app.engine.DetectionListener;
import com.scamshield.app.engine.DetectionResult;

/**
 * SmsReceiver
 * Package: com.scamshield.app.sensors
 *
 * A BroadcastReceiver that intercepts every incoming SMS, reassembles
 * multi-part messages from raw PDU bytes, runs them through the
 * DetectionEngine, and reports the result.
 *
 * ── What is a BroadcastReceiver? ──────────────────────────────────────────
 * Android works like a radio station system. When something important
 * happens (an SMS arrives, the battery gets low, the phone boots), the
 * Android OS "broadcasts" a signal. Any app that has tuned in to that
 * frequency (registered the right <receiver> in AndroidManifest.xml) gets
 * woken up and handed the broadcast data.
 *
 * Our SmsReceiver is tuned to "android.provider.Telephony.SMS_RECEIVED".
 *
 * ── What is a PDU? ────────────────────────────────────────────────────────
 * PDU stands for "Protocol Data Unit" — it's the raw binary format that the
 * GSM mobile network uses to transmit an SMS. A single SMS can arrive as
 * multiple PDU parts when the message is longer than 160 characters (or ~70
 * characters in Unicode). Android bundles all parts together in one broadcast,
 * so we decode and concatenate them before passing to the engine.
 * ──────────────────────────────────────────────────────────────────────────
 */
public class SmsReceiver extends BroadcastReceiver {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    /** Tag for Android Logcat. In Android Studio: filter with "ScamShield" */
    private static final String TAG = "ScamShield.SmsReceiver";

    /**
     * The exact action string the Android OS uses when an SMS arrives.
     * Must match the <action> we declare in AndroidManifest.xml.
     */
    public static final String ACTION_SMS_RECEIVED =
            "android.provider.Telephony.SMS_RECEIVED";

    /**
     * Key inside the broadcast Intent's Bundle that holds the raw PDU array.
     * "pdus" is a standard Android key — do not change this string.
     */
    private static final String EXTRA_PDUS   = "pdus";

    /**
     * Key that tells us which encoding format was used (e.g., "3gpp" or "3gpp2").
     * Only present on API 19+. Safe to pass as null on older devices —
     * the older createFromPdu() overload handles decoding without it.
     */
    private static final String EXTRA_FORMAT = "format";

    // -------------------------------------------------------------------------
    // Injected dependencies
    // These are static so they survive across the multiple short-lived
    // instances Android creates for each broadcast.
    // -------------------------------------------------------------------------

    /**
     * The engine that scores incoming messages.
     *
     * Why static?
     *   BroadcastReceivers are not persistent objects. Android creates a fresh
     *   instance for each broadcast and destroys it immediately after onReceive()
     *   returns. A static reference is the standard way to pass a long-lived
     *   object (like our engine) into a receiver without recreating it each time.
     *
     * How to set it:
     *   Call SmsReceiver.setEngine(new RuleBasedEngine()) from your
     *   Application.onCreate() — see ScamShieldApp.java for the wiring.
     */
    private static DetectionEngine engine;

    /**
     * The listener that will receive DetectionResults when the UI is ready.
     *
     * For now this is null. The Log.d block inside onReceive() is the
     * temporary stand-in. When Part C (UI/Dashboard) is built, call
     * SmsReceiver.setDetectionListener(yourListener) and the TODO block
     * below will activate automatically.
     */
    private static DetectionListener detectionListener;

    // ── Diagnostic call counter (remove after debugging) ─────────────────────
    private static final java.util.concurrent.atomic.AtomicInteger RECEIVE_COUNT =
            new java.util.concurrent.atomic.AtomicInteger(0);

    // -------------------------------------------------------------------------
    // Setters — call these from ScamShieldApp or MainActivity
    // -------------------------------------------------------------------------

    /**
     * Plugs in the DetectionEngine (RuleBasedEngine from Part A).
     * Must be called before any SMS can arrive — do it in Application.onCreate().
     *
     * @param detectionEngine A fully initialised DetectionEngine. Must not be null.
     */
    public static void setEngine(DetectionEngine detectionEngine) {
        engine = detectionEngine;
        Log.d(TAG, "DetectionEngine set: " + detectionEngine.getClass().getSimpleName());
    }

    /**
     * Plugs in the DetectionListener (implemented by the Dashboard in Part C).
     * Safe to call at any time — if null, the receiver just logs and skips the callback.
     *
     * @param listener The DetectionListener implementation from the UI layer.
     */
    public static void setDetectionListener(DetectionListener listener) {
        detectionListener = listener;
        Log.d(TAG, "DetectionListener set: " + listener.getClass().getSimpleName());
    }

    // =========================================================================
    // onReceive — called by Android for every SMS_RECEIVED broadcast
    // =========================================================================

    /**
     * The one method every BroadcastReceiver must implement.
     *
     * ⚠ THREADING RULE: This runs on the MAIN (UI) thread.
     *   You have at most 10 seconds before Android kills the process.
     *   Our RuleBasedEngine is pure in-memory — fast, safe to call here.
     *   When you add the AI second-pass (LLM API call), move that work
     *   to a background thread (AsyncTask, ExecutorService, or WorkManager).
     *
     * @param context The application context — gives access to system services.
     * @param intent  The broadcast Intent carrying the raw PDU data.
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        int callNum = RECEIVE_COUNT.incrementAndGet();
        Log.d(TAG, "[DIAG] onReceive() fired — call #" + callNum
                + " | action=" + (intent != null ? intent.getAction() : "null"));

        // Guard: only handle the action we registered for
        if (intent == null || !ACTION_SMS_RECEIVED.equals(intent.getAction())) {
            return;
        }

        // ── Step 1: Unpack the Bundle ────────────────────────────────────────
        // The Bundle is Android's equivalent of a dictionary — key/value pairs
        // attached to the Intent. The "pdus" key holds all the message parts.
        Bundle bundle = intent.getExtras();
        if (bundle == null) {
            Log.w(TAG, "SMS_RECEIVED intent has no extras — ignoring.");
            return;
        }

        Object[] pduArray = (Object[]) bundle.get(EXTRA_PDUS);
        if (pduArray == null || pduArray.length == 0) {
            Log.w(TAG, "PDU array is null or empty — cannot parse SMS.");
            return;
        }

        // "format" tells createFromPdu() how to decode the bytes.
        // It is only present on API 19+; null is safe on older versions.
        String format = bundle.getString(EXTRA_FORMAT);

        // ── Step 2: Decode each PDU into an SmsMessage object ───────────────
        // SmsMessage is Android's built-in wrapper around a single PDU part.
        // We use the API-level-appropriate overload to decode it correctly.
        SmsMessage[] messageParts = new SmsMessage[pduArray.length];

        for (int i = 0; i < pduArray.length; i++) {
            byte[] pduBytes = (byte[]) pduArray[i];

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // API 23+ — pass format string for correct 3GPP/3GPP2 decoding
                messageParts[i] = SmsMessage.createFromPdu(pduBytes, format);
            } else {
                // API < 23 — older overload, format is inferred automatically
                //noinspection deprecation
                messageParts[i] = SmsMessage.createFromPdu(pduBytes);
            }
        }

        // ── Step 3: Reassemble all PDU parts into one complete message ───────
        // Long messages are split into fragments by the network. We join them
        // back in order to give the engine the full unbroken text.
        //
        // StringBuilder is used instead of the + operator in a loop because
        // each + on a String creates a brand-new String object in memory.
        // StringBuilder appends in-place — much more efficient.
        StringBuilder fullBodyBuilder = new StringBuilder();
        String senderAddress = null;

        for (SmsMessage part : messageParts) {
            if (part == null) continue;

            // Capture sender number from the first non-null part only
            if (senderAddress == null) {
                senderAddress = part.getDisplayOriginatingAddress();
            }

            String fragment = part.getMessageBody();
            if (fragment != null) {
                fullBodyBuilder.append(fragment);
            }
        }

        String messageBody = fullBodyBuilder.toString().trim();

        Log.d(TAG, "SMS received | from=" + senderAddress
                + " | parts=" + pduArray.length
                + " | length=" + messageBody.length() + " chars");

        if (messageBody.isEmpty()) {
            Log.w(TAG, "Assembled SMS body is empty — skipping analysis.");
            return;
        }

        // ── Step 4: Guard — engine must be set before we can analyse ─────────
        if (engine == null) {
            Log.e(TAG, "DetectionEngine is null. "
                    + "Call SmsReceiver.setEngine() from ScamShieldApp.onCreate() "
                    + "before any SMS can arrive.");
            return;
        }

        // ── Step 5: Analyse ───────────────────────────────────────────────────
        // This is the bridge between the SMS sensor and the engine from Part A.
        // "SMS" is the sourceType stored in DetectionResult.sourceType as defined
        // in the locked interface — do not change this string.
        DetectionResult result = engine.analyze(messageBody, "SMS");

        // ── Step 6: Deliver the result ────────────────────────────────────────

        // TEMPORARY — Log to Logcat until Part C (UI/Dashboard) is wired up.
        // Open Android Studio → Logcat → filter by tag: ScamShield
        logResult(senderAddress, result);

        // ╔══════════════════════════════════════════════════════════════════╗
        // ║  TODO — REPLACE THIS BLOCK WITH LISTENER CALL (Part C)         ║
        // ║                                                                  ║
        // ║  When the Dashboard/UI is ready:                                ║
        // ║    1. Implement DetectionListener in your UI class              ║
        // ║    2. Call SmsReceiver.setDetectionListener(this) from          ║
        // ║       your Activity/Service onCreate()                          ║
        // ║    3. The block below will then call onResult() automatically   ║
        // ╚══════════════════════════════════════════════════════════════════╝
        if (detectionListener != null) {
            detectionListener.onResult(result);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Logs a DetectionResult to Logcat in a readable, structured format.
     * This method is TEMPORARY — it exists only until Part C wires up the
     * DetectionListener. It will be removed or demoted to debug-only then.
     *
     * How to view in Android Studio:
     *   Logcat → search bar → type:  tag:ScamShield
     */
    private void logResult(String sender, DetectionResult result) {
        String line = "──────────────────────────────────────────────";
        Log.d(TAG, line);
        Log.d(TAG, "📨 ScamShield SMS Analysis");
        Log.d(TAG, "   Sender     : " + sender);
        Log.d(TAG, "   Verdict    : " + result.verdict);
        Log.d(TAG, "   Confidence : " + result.confidenceScore + "/100");
        Log.d(TAG, "   Source     : " + result.sourceType);
        Log.d(TAG, "   Reason     : " + result.reason);
        Log.d(TAG, "   Timestamp  : " + result.timestamp);
        Log.d(TAG, line);

        // Extra high-visibility log for SCAM so it stands out even at Warn level
        if (result.verdict == DetectionResult.Verdict.SCAM) {
            Log.w(TAG, "🚨 SCAM DETECTED from " + sender
                    + " — confidence " + result.confidenceScore + "/100");
        }
    }
}
