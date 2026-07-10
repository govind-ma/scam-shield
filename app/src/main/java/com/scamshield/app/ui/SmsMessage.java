package com.scamshield.app.ui;

import com.scamshield.app.engine.DetectionResult;

/**
 * SmsMessage — plain data container for one SMS inbox message fetched from
 * the device ContentProvider (content://sms/inbox).
 *
 * After SmsInboxLoader.load() runs, each message has:
 *   • address      — sender phone number or sender ID (e.g. "VM-SBIBNK")
 *   • contactName  — resolved contact name from ContactsContract, or null if not in contacts
 *   • body         — full message text
 *   • date         — Unix timestamp in milliseconds (from the OS)
 *   • result       — DetectionResult from RuleBasedEngine.analyze() on the body
 *   • trusted      — user has explicitly marked this sender as trusted
 *
 * This class is intentionally a dumb POJO — no logic, just data.
 */
public class SmsMessage {

    /** Sender address (phone number or alphanumeric sender ID). */
    public final String address;

    /**
     * Resolved contact name from the device Contacts app.
     * Null if READ_CONTACTS was denied or the number is not in contacts.
     * SmsInboxLoader fills this after querying ContactsContract.
     */
    public String contactName;

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

    /**
     * True if the user has explicitly trusted this message/sender.
     * Trusted messages are visually de-emphasised regardless of the verdict.
     * This is transient (in-memory only — not persisted between sessions).
     */
    public boolean trusted = false;

    /**
     * True once HapticManager.safeConfirmed() has been fired for this item.
     * Prevents the safe haptic from re-triggering every time the user scrolls
     * the RecyclerView and the view holder is rebound.
     * Transient — in-memory only.
     */
    public boolean hapticFired = false;

    public SmsMessage(String address, String body, long date) {
        this.address = (address != null) ? address : "Unknown";
        this.body    = (body    != null) ? body    : "";
        this.date    = date;
    }

    /**
     * Returns the display name for this message:
     * Contact name if available, otherwise the raw address (number).
     */
    public String getDisplayName() {
        if (contactName != null && !contactName.isEmpty()) {
            return contactName;
        }
        return address;
    }
}
