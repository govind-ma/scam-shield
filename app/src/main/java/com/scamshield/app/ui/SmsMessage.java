package com.scamshield.app.ui;

import com.scamshield.app.engine.DetectionResult;

/**
 * SmsMessage — plain data container for one SMS inbox message fetched from
 * the device ContentProvider (content://sms/inbox).
 *
 * After SmsInboxLoader.load() runs, each message has:
 *   • address  — sender (phone number or sender ID, e.g. "VM-SBIBNK")
 *   • body     — full message text
 *   • date     — Unix timestamp in milliseconds (from the OS)
 *   • result   — DetectionResult from RuleBasedEngine.analyze() on the body
 *
 * This class is intentionally a dumb POJO — no logic, just data.
 */
public class SmsMessage {

    /** Sender address (phone number or alphanumeric sender ID). */
    public final String address;

    /** Full body text of the SMS. */
    public final String body;

    /** Timestamp the message was received, in ms since epoch. */
    public final long date;

    /**
     * The verdict from DetectionEngine.analyze(body, "SMS").
     * Set by SmsInboxLoader after running the engine.
     * Null only if the engine threw an unexpected exception (treated as SAFE).
     */
    public DetectionResult result;

    public SmsMessage(String address, String body, long date) {
        this.address = (address != null) ? address : "Unknown";
        this.body    = (body    != null) ? body    : "";
        this.date    = date;
    }
}
