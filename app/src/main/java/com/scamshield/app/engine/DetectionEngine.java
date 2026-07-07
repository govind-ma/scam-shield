package com.scamshield.app.engine;

/**
 * DetectionEngine — the contract every analysis engine must honour.
 *
 * LOCKED SHAPE — defined in PROJECT_CONTEXT.md.
 * Do not rename this method or change its signature.
 */
public interface DetectionEngine {

    /**
     * Analyse a piece of raw text and return a DetectionResult.
     *
     * @param rawText    The full text to analyse (SMS body, spoken words, etc.)
     * @param sourceType Where the text came from — "SMS", "CALL", or "PAYMENT"
     * @return A non-null DetectionResult with verdict, score, and reason filled in
     */
    DetectionResult analyze(String rawText, String sourceType);
}
