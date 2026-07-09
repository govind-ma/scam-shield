package com.scamshield.app.ui;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
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
 * background thread, resolves each sender's phone number to a contact name
 * from the device Contacts app (if READ_CONTACTS is granted), runs each
 * message body through the existing DetectionEngine, and posts the result
 * list back to the main thread via a callback.
 *
 * Design choices:
 *   • Uses plain Java Thread + main-thread Handler — no deprecated AsyncTask,
 *     no coroutines, compatible with minSdk 24.
 *   • Caps fetch at MAX_MESSAGES (150) sorted newest-first.
 *   • Calls ScamShieldApp.getEngine() — the same singleton RuleBasedEngine
 *     used everywhere. Zero duplication of detection logic.
 *   • Contact lookup: PhoneLookup.CONTENT_FILTER_URI is the official Android
 *     API for number → name resolution. Falls back gracefully if denied.
 *   • Does NOT write to LocalDataStore — inbox items are display-only.
 *
 * READ_SMS permission must be granted before calling load(); callers are
 * responsible for checking and requesting permissions first.
 * READ_CONTACTS is optional — if denied, contactName is left null and the
 * raw number is shown instead.
 */
public class SmsInboxLoader {

    private static final String TAG          = "ScamShield.SmsInboxLoader";
    private static final int    MAX_MESSAGES = 150;

    /** Callback delivered on the main thread after load() completes. */
    public interface Callback {
        void onLoaded(List<SmsMessage> messages);
    }

    /**
     * Starts a background thread that reads the SMS inbox, resolves contact names,
     * and analyses each message. Calls {@code callback.onLoaded()} on the main thread.
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

            String[] projection = {"address", "body", "date"};
            String sortOrder    = "date DESC LIMIT " + MAX_MESSAGES;

            Cursor cursor = cr.query(uri, projection, null, null, sortOrder);

            if (cursor == null) {
                Log.w(TAG, "SMS ContentProvider returned null cursor.");
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

                // ── Resolve contact name ──────────────────────────────────────
                msg.contactName = resolveContactName(cr, address);

                // ── Run through detection engine ──────────────────────────────
                try {
                    msg.result = engine.analyze(
                            (body != null ? body : ""),
                            "SMS"
                    );
                } catch (Exception e) {
                    Log.w(TAG, "Engine analysis failed for " + address + ": " + e.getMessage());
                    msg.result = new DetectionResult(
                            DetectionResult.Verdict.SAFE, 0,
                            "Could not analyse message.", "SMS", date
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

    /**
     * Resolves a phone number or sender ID to a contact name from the device
     * Contacts database using ContactsContract.PhoneLookup.
     *
     * Returns null if:
     *   • READ_CONTACTS permission is not granted
     *   • The number is not found in contacts (e.g. alphanumeric sender IDs like "VM-SBIBNK")
     *   • Any exception occurs
     *
     * @param cr      ContentResolver from the caller's context
     * @param address The raw phone number or sender ID from the SMS
     * @return Contact display name, or null if not resolvable
     */
    private static String resolveContactName(ContentResolver cr, String address) {
        if (address == null || address.isEmpty()) return null;

        try {
            Uri lookupUri = Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode(address)
            );

            Cursor cursor = cr.query(
                    lookupUri,
                    new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME},
                    null, null, null
            );

            if (cursor == null) return null;

            String name = null;
            if (cursor.moveToFirst()) {
                name = cursor.getString(0);
            }
            cursor.close();
            return (name != null && !name.isEmpty()) ? name : null;

        } catch (SecurityException e) {
            // READ_CONTACTS not granted — silently return null
            return null;
        } catch (Exception e) {
            Log.w(TAG, "Contact lookup failed for " + address + ": " + e.getMessage());
            return null;
        }
    }
}
