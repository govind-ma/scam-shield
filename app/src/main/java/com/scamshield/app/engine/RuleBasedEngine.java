package com.scamshield.app.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RuleBasedEngine is a FIRST-PASS scam detector that works entirely by matching
 * keywords and patterns — no internet, no AI, no external libraries needed.
 *
 * How it works (high level):
 *   1. Convert the raw text to lowercase so matching is case-insensitive.
 *   2. Run each rule category against the text and accumulate a "danger score".
 *   3. Map the final score to a verdict:
 *        0 – 24   → SAFE        (very likely legitimate)
 *       25 – 54   → SUSPICIOUS  (ambiguous; flag for AI review)
 *       55 – 100  → SCAM        (high confidence fraud)
 *   4. Build a plain-language reason string from every rule that fired.
 *
 * "implements DetectionEngine" means this class honours the interface contract
 * and provides a concrete analyze() method.
 */
public class RuleBasedEngine implements DetectionEngine {

    // =========================================================================
    // SCORE THRESHOLDS
    // =========================================================================

    /** Any total score below this is treated as SAFE. */
    private static final int THRESHOLD_SAFE_MAX       = 24;

    /**
     * Any total score above this is treated as a confirmed SCAM.
     * Scores between THRESHOLD_SAFE_MAX+1 and THRESHOLD_SCAM_MIN-1 are SUSPICIOUS.
     */
    private static final int THRESHOLD_SCAM_MIN       = 55;

    // =========================================================================
    // RULE WEIGHTS
    // Each rule category contributes a fixed number of points when it matches.
    // Higher weight = more dangerous signal.
    // The weights are tuned so that:
    //   - One strong signal alone puts you in SUSPICIOUS territory.
    //   - Two or more strong signals put you firmly in SCAM territory.
    // =========================================================================

    private static final int WEIGHT_URGENCY           = 20;  // "act now", "24 hours", etc.
    private static final int WEIGHT_OTP_PIN           = 30;  // asking for OTP / PIN / passwords
    private static final int WEIGHT_PRIZE_LOTTERY     = 25;  // "you have won", "lucky draw", etc.
    private static final int WEIGHT_SUSPICIOUS_LINK   = 25;  // URLs outside known safe domains
    private static final int WEIGHT_IMPERSONATION     = 35;  // family emergency + money demand

    // =========================================================================
    // RULE KEYWORD LISTS
    // Using Arrays.asList() gives us a fixed-size List from a literal array.
    // We store them as static final so they are built once and reused on every call.
    // =========================================================================

    // --- Category 1: Urgency language ---
    private static final List<String> URGENCY_KEYWORDS = Arrays.asList(
        "immediately",
        "act now",
        "24 hours",
        "account will be blocked",
        "account blocked",
        "urgent action required",
        "last chance",
        "expires today",
        "do not delay",
        "respond immediately",
        "action required",
        "limited time"
    );

    // --- Category 2: OTP / PIN / credential requests ---
    private static final List<String> OTP_PIN_KEYWORDS = Arrays.asList(
        "share your otp",
        "enter your otp",
        "your otp is",
        "enter your pin",
        "share your pin",
        "share your password",
        "verification code",
        "one-time password",
        "one time password",
        "do not share otp",          // ironic — scammers sometimes say this to seem legit
        "bank verification number",
        "cvv",
        "card number"
    );

    // --- Category 3: Prize / lottery / gift scams ---
    private static final List<String> PRIZE_KEYWORDS = Arrays.asList(
        "you have won",
        "you've won",
        "claim your prize",
        "lucky winner",
        "congratulations, you",
        "lottery winner",
        "selected as winner",
        "reward of",
        "gift voucher worth",
        "cash prize",
        "free iphone",
        "free gift"
    );

    // --- Category 4: Known-SAFE domain whitelist (partial matching) ---
    // URLs containing these strings are considered safe and ignored.
    // All others trigger the suspicious-link rule.
    private static final List<String> SAFE_DOMAINS = Arrays.asList(
        "sbi.co.in",
        "hdfcbank.com",
        "icicibank.com",
        "axisbank.com",
        "kotak.com",
        "bankofbaroda.in",
        "pnbindia.in",
        "canarabank.com",
        "gov.in",
        "nic.in",
        "npci.org.in",
        "upihandle",
        "incometax.gov",
        "epfindia.gov"
    );

    // Regex pattern to detect any URL (http/https/www prefix)
    // Pattern is compiled once (expensive operation) and reused.
    private static final Pattern URL_PATTERN = Pattern.compile(
        "\\b(https?://|www\\.)[^\\s]+",   // matches http://, https://, or www. followed by non-space chars
        Pattern.CASE_INSENSITIVE
    );

    // --- Category 5: Impersonation + money pressure ---
    // This category triggers only when BOTH an emergency claim AND a money demand appear.
    private static final List<String> IMPERSONATION_EMERGENCY = Arrays.asList(
        "your son",
        "your daughter",
        "your husband",
        "your wife",
        "your father",
        "your mother",
        "your child",
        "family member",
        "arrested",
        "accident",
        "hospital",
        "icu",
        "emergency",
        "police station",
        "custody"
    );

    private static final List<String> MONEY_DEMAND_KEYWORDS = Arrays.asList(
        "send money",
        "transfer money",
        "pay now",
        "send now",
        "need money",
        "money required",
        "payment required",
        "transfer immediately",
        "send funds"
    );

    // =========================================================================
    // analyze()  — the main entry point (required by the DetectionEngine interface)
    // =========================================================================

    /**
     * Analyzes the given raw text and returns a DetectionResult.
     *
     * Steps:
     *   1. Guard against null/empty input.
     *   2. Normalise to lowercase so matching is case-insensitive.
     *   3. Run each rule category; accumulate score and triggered labels.
     *   4. Cap score at 100.
     *   5. Determine verdict from score thresholds.
     *   6. Build plain-language reason.
     *   7. Return a new DetectionResult.
     *
     * @param rawText    The message / call text to analyse.
     * @param sourceType "SMS", "CALL", or "PAYMENT".
     * @return A fully populated DetectionResult — never null.
     */
    @Override
    public DetectionResult analyze(String rawText, String sourceType) {

        // --- Step 1: Handle null or empty input ---
        if (rawText == null || rawText.trim().isEmpty()) {
            return new DetectionResult(
                DetectionResult.Verdict.SAFE,
                0,
                "No content was found in this message, so it appears safe.",
                sourceType,
                System.currentTimeMillis()
            );
        }

        // --- Step 2: Normalise ---
        // Locale.ROOT makes toLowerCase() consistent regardless of the device's language.
        String text = rawText.toLowerCase(Locale.ROOT);

        // We'll collect human-readable labels for every rule that fires,
        // then combine them into the 'reason' string.
        // ArrayList is a resizable array — perfect for collecting an unknown number of items.
        List<String> triggeredLabels = new ArrayList<>();
        int totalScore = 0;

        // --- Step 3: Run each rule category ---

        int urgencyScore = scoreUrgency(text, triggeredLabels);
        totalScore += urgencyScore;

        int otpScore = scoreOtpPin(text, triggeredLabels);
        totalScore += otpScore;

        int prizeScore = scorePrizeLottery(text, triggeredLabels);
        totalScore += prizeScore;

        int linkScore = scoreSuspiciousLinks(text, rawText, triggeredLabels);
        totalScore += linkScore;

        int impersonationScore = scoreImpersonation(text, triggeredLabels);
        totalScore += impersonationScore;

        // --- Step 4: Cap at 100 ---
        // Math.min() returns the smaller of the two numbers —
        // this prevents absurd scores like 150 when multiple rules fire together.
        totalScore = Math.min(totalScore, 100);

        // --- Step 5: Determine verdict ---
        DetectionResult.Verdict verdict;
        boolean needsAiReview = false;

        if (totalScore <= THRESHOLD_SAFE_MAX) {
            verdict = DetectionResult.Verdict.SAFE;
        } else if (totalScore >= THRESHOLD_SCAM_MIN) {
            verdict = DetectionResult.Verdict.SCAM;
        } else {
            // Score is in the ambiguous middle range (25–54).
            // We return SUSPICIOUS and flag it so a future AI pass can decide.
            verdict = DetectionResult.Verdict.SUSPICIOUS;
            needsAiReview = true;
        }

        // --- Step 6: Build reason string ---
        String reason = buildReason(verdict, triggeredLabels, needsAiReview);

        // --- Step 7: Return result ---
        return new DetectionResult(
            verdict,
            totalScore,
            reason,
            sourceType,
            System.currentTimeMillis()
        );
    }

    // =========================================================================
    // PRIVATE SCORING HELPERS
    // These methods each handle one rule category.
    // They return the points earned and, as a side-effect, add a label to
    // 'triggeredLabels' if the rule fired. (Side effects via List are a common
    // Java pattern when you need a method to return two things at once.)
    // =========================================================================

    /**
     * CATEGORY 1 — Urgency language.
     * Scammers create artificial time pressure to stop victims from thinking clearly.
     */
    private int scoreUrgency(String text, List<String> triggeredLabels) {
        for (String keyword : URGENCY_KEYWORDS) {
            if (text.contains(keyword)) {
                triggeredLabels.add("urgency pressure (\"" + keyword + "\")");
                return WEIGHT_URGENCY;  // We return as soon as one keyword fires —
                                        // no need to count every match in this category.
            }
        }
        return 0;
    }

    /**
     * CATEGORY 2 — OTP / PIN / credential requests.
     * Legitimate banks NEVER ask you to share your OTP or PIN over SMS.
     */
    private int scoreOtpPin(String text, List<String> triggeredLabels) {
        for (String keyword : OTP_PIN_KEYWORDS) {
            if (text.contains(keyword)) {
                triggeredLabels.add("OTP/PIN request (\"" + keyword + "\")");
                return WEIGHT_OTP_PIN;
            }
        }
        return 0;
    }

    /**
     * CATEGORY 3 — Prize / lottery / gift scams.
     * "Congratulations, you have won!" is the oldest trick in the book.
     */
    private int scorePrizeLottery(String text, List<String> triggeredLabels) {
        for (String keyword : PRIZE_KEYWORDS) {
            if (text.contains(keyword)) {
                triggeredLabels.add("prize/lottery claim (\"" + keyword + "\")");
                return WEIGHT_PRIZE_LOTTERY;
            }
        }
        return 0;
    }

    /**
     * CATEGORY 4 — Suspicious links.
     * We find all URLs in the text and check each one against the safe-domain whitelist.
     * Any URL NOT on the whitelist earns a suspicious-link score.
     *
     * @param lowerText  Lowercase version of the raw text (for keyword matching)
     * @param rawText    Original text (URLs are case-sensitive, so we use this for URL extraction)
     */
    private int scoreSuspiciousLinks(String lowerText, String rawText, List<String> triggeredLabels) {
        Matcher matcher = URL_PATTERN.matcher(rawText);
        int score = 0;

        // matcher.find() advances through the text finding each URL one at a time.
        while (matcher.find()) {
            String url = matcher.group().toLowerCase(Locale.ROOT);
            boolean isSafe = false;

            for (String safeDomain : SAFE_DOMAINS) {
                if (url.contains(safeDomain)) {
                    isSafe = true;
                    break;  // Found a safe domain — no need to check the rest
                }
            }

            if (!isSafe) {
                triggeredLabels.add("suspicious link (\"" + matcher.group() + "\")");
                score = WEIGHT_SUSPICIOUS_LINK;
                // We set score once and don't multiply — a message with 10 dodgy
                // links is still just "suspicious links"; we don't want 200 points.
            }
        }
        return score;
    }

    /**
     * CATEGORY 5 — Impersonation combined with money pressure.
     * This rule fires ONLY when the text contains BOTH an emergency/family claim
     * AND an explicit demand for money. Either alone might be innocent (e.g., a
     * genuine family text saying "dad is in hospital"). Together, they're a red flag.
     */
    private int scoreImpersonation(String text, List<String> triggeredLabels) {
        boolean hasEmergency = false;
        String emergencyKeyword = "";

        for (String keyword : IMPERSONATION_EMERGENCY) {
            if (text.contains(keyword)) {
                hasEmergency = true;
                emergencyKeyword = keyword;
                break;
            }
        }

        if (!hasEmergency) {
            return 0;  // No emergency claim — no need to check money demand
        }

        for (String keyword : MONEY_DEMAND_KEYWORDS) {
            if (text.contains(keyword)) {
                triggeredLabels.add(
                    "family emergency + money demand (\""
                    + emergencyKeyword + "\" + \"" + keyword + "\")"
                );
                return WEIGHT_IMPERSONATION;
            }
        }

        return 0;  // Emergency alone, no money demand — could be genuine
    }

    // =========================================================================
    // REASON STRING BUILDER
    // =========================================================================

    /**
     * Converts the list of triggered rule labels into one friendly, plain-English
     * sentence that a non-technical elderly user can understand.
     *
     * Examples:
     *   → "This message looks safe — no suspicious patterns were found."
     *   → "This message may be a scam because it contains: urgency pressure
     *      ("act now") and OTP/PIN request ("share your otp")."
     *   → "This message could not be fully assessed and needs a closer look
     *      because it contains: suspicious link (...). [needs_ai_review]"
     *
     * @param verdict         The verdict already determined from the score
     * @param triggeredLabels Everything that fired during scoring
     * @param needsAiReview   True if the score fell in the ambiguous middle range
     */
    private String buildReason(DetectionResult.Verdict verdict,
                                List<String> triggeredLabels,
                                boolean needsAiReview) {

        // No rules fired → it looks clean
        if (triggeredLabels.isEmpty()) {
            return "This message looks safe — no suspicious patterns were found.";
        }

        // Join all triggered labels with " and " between them.
        // String.join() is cleaner than a manual loop + StringBuilder for this case.
        String labelList = String.join(", and ", triggeredLabels);

        switch (verdict) {
            case SCAM:
                return "This message is very likely a scam because it contains: " + labelList + ". "
                     + "Please do not respond, click any links, or share any information.";

            case SUSPICIOUS:
                String base = "This message has some warning signs and needs a closer look "
                            + "because it contains: " + labelList + ". ";
                if (needsAiReview) {
                    // Marker that the calling code (or future LLM layer) can detect
                    base += "[needs_ai_review]";
                }
                return base;

            case SAFE:
            default:
                // Some mild signals fired but score stayed low
                return "This message appears safe, though it contained a minor pattern "
                     + "worth noting: " + labelList + ".";
        }
    }
}
