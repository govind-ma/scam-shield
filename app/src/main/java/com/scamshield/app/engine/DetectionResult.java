package com.scamshield.app.engine;

/**
 * DetectionResult — plain data container for one analysed message or call.
 *
 * LOCKED SHAPE — defined in PROJECT_CONTEXT.md.
 * All fields are public (no getters/setters). Do not make them private.
 */
public class DetectionResult {

    public enum Verdict { SAFE, SUSPICIOUS, SCAM }

    public Verdict verdict;
    public int confidenceScore;      // 0-100
    public String reason;            // plain-language explanation for elderly, non-technical user
    public String sourceType;        // "SMS", "CALL", or "PAYMENT"
    public long timestamp;

    /** Default no-arg constructor. */
    public DetectionResult() {}

    /**
     * Convenience constructor — sets all fields in one call.
     * Not part of the locked interface; just a shorthand used by RuleBasedEngine.
     */
    public DetectionResult(Verdict verdict,
                           int confidenceScore,
                           String reason,
                           String sourceType,
                           long timestamp) {
        this.verdict         = verdict;
        this.confidenceScore = confidenceScore;
        this.reason          = reason;
        this.sourceType      = sourceType;
        this.timestamp       = timestamp;
    }

    @Override
    public String toString() {
        return "DetectionResult {"
                + "\n  verdict       = " + verdict
                + "\n  confidence    = " + confidenceScore + "/100"
                + "\n  sourceType    = " + sourceType
                + "\n  reason        = " + reason
                + "\n  timestamp     = " + timestamp
                + "\n}";
    }
}
