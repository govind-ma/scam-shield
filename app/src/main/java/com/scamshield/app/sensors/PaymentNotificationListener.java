package com.scamshield.app.sensors;

import android.app.Notification;
import android.content.Intent;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import com.scamshield.app.ScamShieldApp;
import com.scamshield.app.engine.DetectionEngine;
import com.scamshield.app.engine.DetectionResult;
import com.scamshield.app.ui.ScamAlertManager;

import java.util.HashSet;
import java.util.Set;

/**
 * PaymentNotificationListener
 * Package: com.scamshield.app.sensors
 *
 * A NotificationListenerService that captures incoming notifications from
 * major Indian UPI and payment apps (Google Pay, PhonePe, Paytm, etc.).
 * It extracts notification text and runs it through the DetectionEngine.
 *
 * ── Why NotificationListenerService? ─────────────────────────────────────────
 * Modern Android does not allow apps to read other apps' notifications without
 * a special system permission. The user must explicitly enable this in
 * Settings -> Notification Access.
 *
 * ── Payment app package filter ──────────────────────────────────────────────
 * To avoid unnecessary processing and protect user privacy, we only analyze
 * notifications originating from known UPI/payment packages.
 */
public class PaymentNotificationListener extends NotificationListenerService {

    private static final String TAG = "ScamShield.PaySensor";

    // Supported UPI/Payment app packages
    private static final Set<String> PAYMENT_PACKAGES = new HashSet<>();
    static {
        PAYMENT_PACKAGES.add("com.google.android.apps.nbu.paisa.user"); // Google Pay / GPay
        PAYMENT_PACKAGES.add("com.phonepe.app");                        // PhonePe
        PAYMENT_PACKAGES.add("net.one97.paytm");                       // Paytm
        PAYMENT_PACKAGES.add("in.org.npci.upiapp");                    // BHIM UPI
        PAYMENT_PACKAGES.add("com.sbi.upi");                           // BHIM SBI Pay
        PAYMENT_PACKAGES.add("com.csg.axispay");                       // Axis Pay
        PAYMENT_PACKAGES.add("com.msf.kbank.upi");                     // KayPay / Kotak UPI
        PAYMENT_PACKAGES.add("com.icicibank.pockets");                 // Pockets by ICICI
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.d(TAG, "Notification Listener connected and active.");
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        Log.d(TAG, "Notification Listener disconnected.");
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;

        String packageName = sbn.getPackageName();

        // 1. Filter: only process notifications from payment apps
        if (!PAYMENT_PACKAGES.contains(packageName)) {
            return;
        }

        Notification notification = sbn.getNotification();
        if (notification == null) return;

        Bundle extras = notification.extras;
        if (extras == null) return;

        // 2. Extract title and text
        CharSequence titleSequence = extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence textSequence = extras.getCharSequence(Notification.EXTRA_TEXT);

        String title = titleSequence != null ? titleSequence.toString().trim() : "";
        String text = textSequence != null ? textSequence.toString().trim() : "";

        if (title.isEmpty() && text.isEmpty()) {
            return;
        }

        String combinedText = (title + " " + text).trim();
        Log.d(TAG, "Payment notification intercepted from: " + packageName + " | Content: " + combinedText);

        // 3. Run through the DetectionEngine
        DetectionEngine engine = ScamShieldApp.getEngine();
        if (engine == null) {
            Log.e(TAG, "DetectionEngine not initialized in ScamShieldApp.");
            return;
        }

        // Analyze with source type "PAYMENT"
        DetectionResult result = engine.analyze(combinedText, "PAYMENT");

        // 4. Pass the result to ScamAlertManager which logs it and shows alerts if needed
        try {
            ScamAlertManager.getInstance().onResult(result);
        } catch (Exception e) {
            Log.e(TAG, "Failed to deliver result to ScamAlertManager: " + e.getMessage());
        }
    }
}
