package com.scamshield.app;

import android.app.Application;
import android.util.Log;

import com.scamshield.app.data.LocalDataStore;
import com.scamshield.app.engine.DetectionEngine;
import com.scamshield.app.engine.RuleBasedEngine;
import com.scamshield.app.sensors.SmsReceiver;
import com.scamshield.app.ui.ScamAlertManager;

/**
 * ScamShieldApp — Application subclass for Scam Shield.
 * Package: com.scamshield.app
 *
 * ── What is an Application subclass? ─────────────────────────────────────────
 * Every Android process has exactly one Application object. Its onCreate()
 * runs ONCE — before any Activity, Service, or BroadcastReceiver starts.
 * This makes it the correct and only safe place to initialise global singletons
 * like our DetectionEngine and ScamAlertManager.
 *
 * If we initialised the engine inside SmsReceiver.onReceive() instead,
 * we'd rebuild it from scratch on every incoming SMS — wasteful and slow.
 *
 * ── How to activate this class ────────────────────────────────────────────────
 * Add android:name=".ScamShieldApp" to the <application> tag in
 * AndroidManifest.xml. Without that attribute, Android ignores this class
 * and uses the default Application instead.
 * ─────────────────────────────────────────────────────────────────────────────
 */
public class ScamShieldApp extends Application {

    private static final String TAG = "ScamShield.App";

    /** Shared engine instance — accessible app-wide for manual checks. */
    private static DetectionEngine engine;

    /** Returns the global DetectionEngine. Use this instead of creating new instances. */
    public static DetectionEngine getEngine() {
        return engine;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Log.d(TAG, "Scam Shield process starting — initialising engine...");

        // Build the DetectionEngine once; it lives for the full process lifetime.
        // To swap in an AI engine later, change only this one line.
        engine = new RuleBasedEngine();

        // Hand the engine to SmsReceiver via its static setter.
        // SmsReceiver uses a static reference because the OS creates and destroys
        // a fresh BroadcastReceiver instance for every SMS broadcast — it cannot
        // hold its own long-lived state.
        SmsReceiver.setEngine(engine);

        // ── Part D: Initialise DataStore ──────────────────────────────────────
        // Must be initialised before ScamAlertManager because the manager will
        // call LocalDataStore.getInstance().logDetection() on every result.
        LocalDataStore.init(this);

        // ── Wire the UI listener (Part C) ────────────────────────────────────
        // ScamAlertManager is a singleton that shows overlays and speaks TTS.
        // It implements DetectionListener, so SmsReceiver (and future sensors)
        // can hand results directly to it without knowing about the UI.
        ScamAlertManager.init(this);
        SmsReceiver.setDetectionListener(ScamAlertManager.getInstance());

        Log.d(TAG, "Engine ready: " + engine.getClass().getSimpleName());
        Log.d(TAG, "LocalDataStore ready.");
        Log.d(TAG, "ScamAlertManager wired as DetectionListener.");
    }
}
