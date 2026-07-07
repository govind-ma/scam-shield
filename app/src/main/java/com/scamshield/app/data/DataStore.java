package com.scamshield.app.data;

import com.scamshield.app.engine.DetectionResult;

import java.util.List;

/**
 * DataStore
 * Package: com.scamshield.app.data
 *
 * Locked interface — exact shape from PROJECT_CONTEXT.md.
 * Do NOT change method signatures or field names.
 *
 * All implementations (local JSON, Firestore, etc.) must implement this
 * interface so the rest of the app never depends on a concrete storage layer.
 */
public interface DataStore {

    /**
     * Returns a list of known scam phone numbers.
     * Used by the engine to instantly flag calls from numbers on this list.
     *
     * @return List of phone number strings (e.g., "+91-xxxxxxxxxx", "1800xxxx").
     *         Never null — return an empty list if none are stored.
     */
    List<String> getScamNumbers();

    /**
     * Persists a DetectionResult to permanent storage so the user can review
     * their history in HomeActivity's RecyclerView.
     *
     * Called by ScamAlertManager.onResult() for every detection, including SAFE,
     * SUSPICIOUS, and SCAM verdicts.
     *
     * @param result The fully populated DetectionResult to save.
     */
    void logDetection(DetectionResult result);

    /**
     * Returns the official helpline number for the given bank.
     * Used by the Recovery Mode screen so elderly users can call their bank
     * with one tap instead of searching the web.
     *
     * @param bankName Case-insensitive bank name (e.g., "SBI", "HDFC", "Paytm").
     * @return Helpline number string, or null if the bank is not in the database.
     */
    String getBankHelpline(String bankName);
}
