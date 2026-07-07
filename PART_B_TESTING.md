# Part B — Testing Guide
# How to safely test SmsReceiver end-to-end without spamming real people

---

## Option 1 — Android Emulator (Recommended, zero real-phone risk)

The Android Emulator has a built-in SMS simulator.
You never need a second SIM card or real phone number.

### Steps

1. **Start your emulator** in Android Studio
   (AVD Manager → pick any device → Run).

2. **Install and open** Scam Shield on the emulator.
   - Tap "Allow" on the SMS permission dialog.

3. **Open Extended Controls**
   - In the emulator toolbar on the right, click the `...` (three-dot) button.
   - This opens the "Extended Controls" panel.

4. **Go to the Phone tab**
   - In the left sidebar, click "Phone".

5. **Send a test SMS**
   - In the "From" field, type any fake phone number, e.g. `9999999999`.
   - In the "Message" field, paste one of the test strings below.
   - Click **Send Message**.

6. **Watch Logcat**
   - In Android Studio, open Logcat (View → Tool Windows → Logcat).
   - In the filter bar at the top, type:  `tag:ScamShield`
   - You will see the full DetectionResult printed within 1-2 seconds.

---

## Option 2 — ADB command line (fast, scriptable)

If you prefer the terminal over the GUI, ADB can inject SMS directly:

```bash
# Make sure your emulator is running, then:
adb emu sms send 9999999999 "URGENT: Your account will be blocked. Share your OTP now: http://fake-bank.xyz/verify"
```

Replace the message text with any of the test strings below.
The emulator receives it as a real incoming SMS — SmsReceiver fires immediately.

---

## Option 3 — Real second phone (for final validation only)

Use a second phone (or a cheap prepaid SIM in your own phone's second slot)
to send an SMS to the device running the debug build.

> ⚠ Only do this for final smoke-testing.
> Use the emulator for all day-to-day development.
> Never send scam-like strings to a number you don't own.

---

## Test SMS strings to paste

Copy-paste these into the emulator's Extended Controls or the adb command.
Each one exercises a different scoring path in RuleBasedEngine.

```
# Expected: SAFE (score 0)
INR 500.00 debited from A/c XXXX5678 on 06-Jul-26 at Amazon. Bal: INR 8,200. Not you? Call 1800-XXX-XXXX.

# Expected: SCAM (score ~75) — OTP + urgency + fake link
URGENT: Your SBI account will be blocked in 24 hours! Share your OTP immediately to verify: http://sbi-secure-update.xyz/verify

# Expected: SCAM (score ~70) — prize + fake link + urgency
Congratulations! You have won Rs 5,00,000 in the National Lottery 2026. Claim your prize before offer expires today: http://win-india-prize.net/claim

# Expected: SUSPICIOUS (score ~25) — unknown link only, context benign
Hi! Your order #8821 has been shipped. Track it at http://myshop-delivery-track.in/8821. Expected by 9 Jul.

# Expected: SCAM (score ~55) — family emergency + money demand
This is Andheri Police Station. Your son has been arrested. Send money now to bail_help@ybl before the court closes. Act now.
```

---

## What you should see in Logcat for a SCAM message

```
D  ScamShield.SmsReceiver: ──────────────────────────────────────────────
D  ScamShield.SmsReceiver: 📨 ScamShield SMS Analysis
D  ScamShield.SmsReceiver:    Sender     : 9999999999
D  ScamShield.SmsReceiver:    Verdict    : SCAM
D  ScamShield.SmsReceiver:    Confidence : 75/100
D  ScamShield.SmsReceiver:    Source     : SMS
D  ScamShield.SmsReceiver:    Reason     : This message is very likely a scam because it contains: urgency pressure ("immediately"), and OTP/PIN request ("share your otp"), and suspicious link ("http://..."). Please do not respond, click any links, or share any information.
D  ScamShield.SmsReceiver:    Timestamp  : 1783353102010
D  ScamShield.SmsReceiver: ──────────────────────────────────────────────
W  ScamShield.SmsReceiver: 🚨 SCAM DETECTED from 9999999999 — confidence 75/100
```

---

## Where the TODO hook lives

When Part C (Dashboard/UI) is ready, find this block in SmsReceiver.java
and wire in your DetectionListener:

```java
// ╔══════════════════════════════════════════════════════════════════╗
// ║  TODO — REPLACE THIS BLOCK WITH LISTENER CALL (Part C)         ║
// ╚══════════════════════════════════════════════════════════════════╝
if (detectionListener != null) {
    detectionListener.onResult(result);
}
```

Steps:
1. Implement `DetectionListener` in your Dashboard Activity or Service.
2. Call `SmsReceiver.setDetectionListener(this)` from that class's `onCreate()`.
3. The `if` block above will now call `onResult()` automatically on every SMS.
4. Remove the `logResult()` call above it (or keep it for debug builds).
