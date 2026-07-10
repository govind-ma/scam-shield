package com.scamshield.app.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.scamshield.app.engine.DetectionResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LocalDataStore
 * Package: com.scamshield.app.data
 *
 * Concrete implementation of DataStore using Android SharedPreferences
 * to store detection history as a JSON array.
 *
 * ── Storage strategy ───────────────────────────────────────────────────────
 * We store all detection results in a single SharedPreferences key as a
 * JSON string. SharedPreferences is simple, reliable, and works offline —
 * which is critical for this app (elderly users may have poor connectivity).
 *
 * A real production version would use Room (SQLite) for large history sets,
 * but SharedPreferences is correct for this phase and avoids a Room dependency.
 *
 * ── Thread safety ──────────────────────────────────────────────────────────
 * SharedPreferences.apply() is asynchronous and thread-safe — the OS commits
 * the write in the background without blocking the caller.
 *
 * ── History cap ────────────────────────────────────────────────────────────
 * We cap history at MAX_HISTORY_ITEMS (50). When the limit is reached, the
 * oldest entry is dropped (FIFO). This prevents the JSON blob from growing
 * unboundedly in SharedPreferences.
 *
 * ── Singleton ──────────────────────────────────────────────────────────────
 * LocalDataStore is a singleton, initialised once in ScamShieldApp.onCreate().
 * Use LocalDataStore.init(context) then LocalDataStore.getInstance().
 */
public class LocalDataStore implements DataStore {

    private static final String TAG              = "ScamShield.DataStore";
    private static final String PREFS_NAME           = "scamshield_prefs";
    private static final String KEY_HISTORY          = "detection_history";
    private static final String KEY_ALERT_MODE       = "alert_mode_active";
    private static final String KEY_INSTALL_TIMESTAMP = "install_timestamp_ms";
    private static final int    MAX_HISTORY_ITEMS    = 50;

    // ── Singleton ─────────────────────────────────────────────────────────────
    private static volatile LocalDataStore instance;

    private final SharedPreferences prefs;

    // ── Built-in bank helpline database ──────────────────────────────────────
    // Hardcoded list of India's major banks and helplines. Non-exhaustive but
    // covers the most common ones elderly users interact with. Keyed lowercase.
    private static final Map<String, String> BANK_HELPLINES = new HashMap<>();
    static {
        BANK_HELPLINES.put("sbi",          "1800-11-2211");
        BANK_HELPLINES.put("state bank",   "1800-11-2211");
        BANK_HELPLINES.put("hdfc",         "1800-202-6161");
        BANK_HELPLINES.put("icici",        "1800-1080");
        BANK_HELPLINES.put("axis",         "1800-419-5959");
        BANK_HELPLINES.put("kotak",        "1860-266-2666");
        BANK_HELPLINES.put("pnb",          "1800-180-2222");
        BANK_HELPLINES.put("punjab",       "1800-180-2222");
        BANK_HELPLINES.put("bob",          "1800-5700");
        BANK_HELPLINES.put("bank of baroda","1800-5700");
        BANK_HELPLINES.put("canara",       "1800-425-0018");
        BANK_HELPLINES.put("union",        "1800-22-2244");
        BANK_HELPLINES.put("paytm",        "0120-4456-456");
        BANK_HELPLINES.put("phonepe",      "080-68727374");
        BANK_HELPLINES.put("gpay",         "1800-419-0157");
        BANK_HELPLINES.put("google pay",   "1800-419-0157");
        BANK_HELPLINES.put("cybercrime",   "1930");
        BANK_HELPLINES.put("cyber",        "1930");
        BANK_HELPLINES.put("rbi",          "14440");
    }

    // ── Known scam numbers (initial seed list) ────────────────────────────────
    // In a production app this would be fetched from Firestore or a CDN.
    // These are placeholder patterns — replace with real reported numbers.
    private static final List<String> KNOWN_SCAM_NUMBERS = new ArrayList<>();
    static {
        KNOWN_SCAM_NUMBERS.add("+91-8000000000");  // placeholder
        KNOWN_SCAM_NUMBERS.add("+91-9000000000");  // placeholder
        KNOWN_SCAM_NUMBERS.add("1800000000");       // placeholder
    }

    // =========================================================================
    // Singleton init
    // =========================================================================

    public static void init(Context applicationContext) {
        if (instance == null) {
            synchronized (LocalDataStore.class) {
                if (instance == null) {
                    instance = new LocalDataStore(applicationContext);
                    Log.d(TAG, "LocalDataStore initialised.");
                }
            }
        }
    }

    public static LocalDataStore getInstance() {
        if (instance == null) {
            throw new IllegalStateException(
                "LocalDataStore.init(context) must be called in ScamShieldApp.onCreate() first.");
        }
        return instance;
    }

    // =========================================================================
    // Constructor
    // =========================================================================

    private LocalDataStore(Context applicationContext) {
        // MODE_PRIVATE: only this app can read these preferences.
        this.prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // =========================================================================
    // DataStore interface implementation
    // =========================================================================

    /**
     * Returns the built-in list of known scam phone numbers.
     * In Part E, this can be extended to merge with a server-side blocklist.
     */
    @Override
    public List<String> getScamNumbers() {
        return Collections.unmodifiableList(KNOWN_SCAM_NUMBERS);
    }

    /**
     * Saves a DetectionResult into SharedPreferences as a JSON object.
     * Keeps only the most recent MAX_HISTORY_ITEMS entries.
     *
     * JSON shape of each stored entry:
     * {
     *   "verdict":         "SCAM",
     *   "confidenceScore": 85,
     *   "reason":          "Urgency + OTP request detected",
     *   "sourceType":      "SMS",
     *   "timestamp":       1720000000000
     * }
     */
    // ── Diagnostic write counter (remove after debugging) ─────────────────────
    private static final java.util.concurrent.atomic.AtomicInteger LOG_CALL_COUNT =
            new java.util.concurrent.atomic.AtomicInteger(0);

    @Override
    public synchronized void logDetection(DetectionResult result) {
        int callNum = LOG_CALL_COUNT.incrementAndGet();
        Log.d(TAG, "[DIAG] logDetection() called — call #" + callNum
                + " | verdict=" + (result != null ? result.verdict : "null")
                + " | thread=" + Thread.currentThread().getName());

        if (result == null) {
            Log.w(TAG, "logDetection() called with null result — skipping.");
            return;
        }

        try {
            // 1. Load existing history
            JSONArray history = loadHistoryArray();
            Log.d(TAG, "[DIAG] logDetection #" + callNum + " — history has " + history.length() + " entries before write");

            // Deduplication guard: Check if the last entry is identical
            if (history.length() > 0) {
                JSONObject lastEntry = history.getJSONObject(0);
                String lastVerdict = lastEntry.optString("verdict", "");
                String lastSource = lastEntry.optString("sourceType", "");
                String lastReason = lastEntry.optString("reason", "");
                long lastTimestamp = lastEntry.optLong("timestamp", 0L);

                if (lastVerdict.equals(result.verdict.name())
                        && lastSource.equals(result.sourceType)
                        && lastReason.equals(result.reason != null ? result.reason : "")
                        && (Math.abs(result.timestamp - lastTimestamp) < 2000 || result.timestamp == lastTimestamp)) {
                    Log.d(TAG, "[DIAG] Dedup guard fired — SKIPPING call #" + callNum);
                    return;
                }
            }

            // 2. Build new entry JSON
            JSONObject entry = new JSONObject();
            entry.put("verdict",         result.verdict.name());
            entry.put("confidenceScore", result.confidenceScore);
            entry.put("reason",          result.reason != null ? result.reason : "");
            entry.put("sourceType",      result.sourceType != null ? result.sourceType : "");
            entry.put("timestamp",       result.timestamp);

            // 3. Prepend (newest first)
            JSONArray updated = new JSONArray();
            updated.put(entry);
            for (int i = 0; i < history.length() && updated.length() < MAX_HISTORY_ITEMS; i++) {
                updated.put(history.get(i));
            }

            // 4. Persist SYNCHRONOUSLY with commit() — NOT apply().
            //
            //    CRITICAL FIX: apply() is asynchronous — it queues the write to a
            //    background thread and returns immediately. The NEXT call to
            //    loadHistoryArray() (from a second logDetection() call on the main
            //    thread) may run BEFORE that background write completes, reading the
            //    old (pre-write) SharedPrefs value. This makes the dedup guard see
            //    an empty history and allow duplicate entries through.
            //
            //    commit() writes synchronously on the calling thread and returns only
            //    after the write is durably stored. This is safe here because:
            //    a) logDetection() is synchronized — only one call runs at a time
            //    b) It's called from the main thread, but the write is fast (< 1ms)
            //    c) No deadlock risk since no other synchronized block holds this lock
            boolean writeOk = prefs.edit().putString(KEY_HISTORY, updated.toString()).commit();
            Log.d(TAG, "[DIAG] logDetection #" + callNum + " — written OK=" + writeOk
                    + " | history now has " + updated.length() + " entries");

        } catch (JSONException e) {
            Log.e(TAG, "Failed to log detection: " + e.getMessage());
        }
    }

    /**
     * Looks up a bank helpline by name (case-insensitive substring match).
     *
     * @param bankName e.g. "SBI", "HDFC Bank", "paytm"
     * @return Helpline number or null if not found.
     */
    @Override
    public String getBankHelpline(String bankName) {
        if (bankName == null || bankName.isEmpty()) {
            return null;
        }
        String key = bankName.toLowerCase().trim();

        // Exact match first
        if (BANK_HELPLINES.containsKey(key)) {
            return BANK_HELPLINES.get(key);
        }

        // Substring match — handles "HDFC Bank" matching "hdfc"
        for (Map.Entry<String, String> entry : BANK_HELPLINES.entrySet()) {
            if (key.contains(entry.getKey()) || entry.getKey().contains(key)) {
                return entry.getValue();
            }
        }

        Log.w(TAG, "No helpline found for bank: " + bankName);
        return null;
    }

    // =========================================================================
    // Public helpers (used by HomeActivity/HistoryAdapter)
    // =========================================================================

    /**
     * Returns the stored detection history as a list of DetectionResult objects,
     * newest first. Called by HistoryAdapter to populate the RecyclerView.
     *
     * @return List of DetectionResult; empty list if no history exists.
     */
    public List<DetectionResult> getHistory() {
        JSONArray array = loadHistoryArray();
        List<DetectionResult> results = new ArrayList<>();

        for (int i = 0; i < array.length(); i++) {
            try {
                JSONObject obj = array.getJSONObject(i);
                DetectionResult r = new DetectionResult();
                r.verdict         = DetectionResult.Verdict.valueOf(
                        obj.getString("verdict"));
                r.confidenceScore = obj.getInt("confidenceScore");
                r.reason          = obj.optString("reason", "");
                r.sourceType      = obj.optString("sourceType", "");
                r.timestamp       = obj.optLong("timestamp", 0L);
                results.add(r);
            } catch (JSONException | IllegalArgumentException e) {
                Log.w(TAG, "Skipping malformed history entry at index " + i + ": " + e.getMessage());
            }
        }

        return results;
    }

    /**
     * Returns the raw stored detection entries as pipe-delimited strings for
     * lightweight stats calculations (e.g. counting today's scams in HomeActivity).
     *
     * Format of each entry: "<verdict>|<sourceType>|<timestamp>"
     *
     * Consumers must handle malformed entries gracefully (try/catch around split).
     * Prefer getHistory() when full DetectionResult objects are needed.
     */
    public List<String> getDetectionHistory() {
        JSONArray array = loadHistoryArray();
        List<String> results = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            try {
                JSONObject obj = array.getJSONObject(i);
                String verdict   = obj.optString("verdict", "SAFE");
                String source    = obj.optString("sourceType", "");
                long   timestamp = obj.optLong("timestamp", 0L);
                results.add(verdict + "|" + source + "|" + timestamp);
            } catch (JSONException e) {
                Log.w(TAG, "Skipping malformed entry at getDetectionHistory() index " + i);
            }
        }
        return results;
    }

    /**
     * Clears all stored detection history.
     * Called from Settings if the user wants to reset their history.
     */
    public void clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply();
        Log.d(TAG, "Detection history cleared.");
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    /** Loads the raw JSON array from SharedPreferences, or returns an empty array. */
    private JSONArray loadHistoryArray() {
        String json = prefs.getString(KEY_HISTORY, null);
        if (json == null || json.isEmpty()) {
            return new JSONArray();
        }
        try {
            return new JSONArray(json);
        } catch (JSONException e) {
            Log.e(TAG, "History JSON corrupted — resetting: " + e.getMessage());
            return new JSONArray();
        }
    }

    /**
     * Returns the number of whole days since Scam Shield was first set up on this device.
     * On first call ever, records the current timestamp and returns 0.
     * Used by HomeActivity to display "Protected for X days".
     */
    public int getProtectedDays() {
        long installTs = prefs.getLong(KEY_INSTALL_TIMESTAMP, 0L);
        if (installTs == 0L) {
            // First launch — record install time and show day 0 → "Today"
            installTs = System.currentTimeMillis();
            prefs.edit().putLong(KEY_INSTALL_TIMESTAMP, installTs).apply();
            return 0;
        }
        long diffMs = System.currentTimeMillis() - installTs;
        return (int) (diffMs / 86_400_000L); // ms → whole days
    }

    /** Returns whether the application is in Alert Mode (scam detected). */
    public boolean isAlertModeActive() {
        return prefs.getBoolean(KEY_ALERT_MODE, false);
    }

    /** Sets the Alert Mode state (red/green theme controller). */
    public void setAlertModeActive(boolean active) {
        prefs.edit().putBoolean(KEY_ALERT_MODE, active).apply();
        Log.d(TAG, "Alert mode active changed to: " + active);
    }
}
