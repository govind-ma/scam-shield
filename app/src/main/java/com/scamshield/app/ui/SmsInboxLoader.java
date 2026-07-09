package com.scamshield.app.ui;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.scamshield.app.ScamShieldApp;
import com.scamshield.app.engine.DetectionEngine;
import com.scamshield.app.engine.DetectionResult;

import java.util.ArrayList;
import java.util.List;

/**
 * SmsInboxLoader
 * Package: com.scamshield.app.ui
 *
 * Queries the device's SMS content provider (content://sms/inbox) on a
 * background thread, runs each message body through the existing DetectionEngine,
 * and posts the result list back to the main thread via a callback.
 *
 * Design choices:
 *   • Uses a plain Java Thread + main-thread Handler to avoid AsyncTask (deprecated
 *     in API 30) and to remain compatible with minSdk 24 without any extra libs.
 *   • Caps fetch at MAX_MESSAGES (150) sorted newest-first to bound memory use.
 *   • Calls ScamShieldApp.getEngine() — the same singleton RuleBasedEngine used
 *     everywhere else. Zero duplication of detection logic.
 *   • Does NOT write results to LocalDataStore — these are read-only, display-only
 *     labels for the inbox view. Only real new detections go to DataStore.
 *
 * READ_SMS permission must be granted before calling load(); callers are
 * responsible for checking and requesting the permission first.
 */
public class SmsInboxLoader {

    private static final String TAG          = "ScamShield.SmsInboxLoader";
    private static final int    MAX_MESSAGES = 150;

    /** Callback delivered on the main thread after load() completes. */
    public interface Callback {
        void onLoaded(List<SmsMessage> messages);
    }

    /**
     * Starts a background thread that reads the SMS inbox and analyses each
     * message. Calls {@code callback.onLoaded()} on the main thread when done.
     *
     * @param context  Application or Activity context (used for ContentResolver).
     * @param callback Receives the result list on the main thread.
     */
    public static void load(Context context, Callback callback) {
        Handler mainHandler = new Handler(Looper.getMainLooper());

        new Thread(() -> {
            List<SmsMessage> result = fetchAndAnalyse(context);
            mainHandler.post(() -> callback.onLoaded(result));
        }, "SmsInboxLoader-Thread").start();
    }

    // ── Private implementation ─────────────────────────────────────────────────

    private static List<SmsMessage> fetchAndAnalyse(Context context) {
        List<SmsMessage> messages = new ArrayList<>();

        try {
            ContentResolver cr  = context.getContentResolver();
            Uri             uri = Uri.parse("content://sms/inbox");

            // Projection — only the columns we need (keeps cursor lightweight)
            String[] projection = {"address", "body", "date"};

            // Sort newest first, cap at MAX_MESSAGES via LIMIT
            String sortOrder = "date DESC LIMIT " + MAX_MESSAGES;

            Cursor cursor = cr.query(uri, projection, null, null, sortOrder);

            if (cursor == null) {
                Log.w(TAG, "SMS ContentProvider returned null cursor — READ_SMS denied or device unsupported.");
                return messages;
            }

            int colAddress = cursor.getColumnIndexOrThrow("address");
            int colBody    = cursor.getColumnIndexOrThrow("body");
            int colDate    = cursor.getColumnIndexOrThrow("date");

            DetectionEngine engine = ScamShieldApp.getEngine();

            while (cursor.moveToNext()) {
                String address = cursor.getString(colAddress);
                String body    = cursor.getString(colBody);
                long   date    = cursor.getLong(colDate);

                SmsMessage msg = new SmsMessage(address, body, date);

                // Run through the existing engine — pure string matching, fast even for 150 messages
                try {
                    msg.result = engine.analyze(
                            (body != null ? body : ""),
                            "SMS"
                    );
                } catch (Exception e) {
                    Log.w(TAG, "Engine analysis failed for message from " + address + ": " + e.getMessage());
                    // Treat engine failure as SAFE to avoid false positives
                    msg.result = new DetectionResult(
                            DetectionResult.Verdict.SAFE,
                            0,
                            "Could not analyse message.",
                            "SMS",
                            date
                    );
                }

                messages.add(msg);
            }

            cursor.close();
            Log.d(TAG, "Fetched and analysed " + messages.size() + " SMS messages.");

        } catch (SecurityException e) {
            Log.w(TAG, "READ_SMS permission denied: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error reading SMS inbox: " + e.getMessage());
        }

        return messages;
    }
}
