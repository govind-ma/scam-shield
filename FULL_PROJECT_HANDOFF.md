# Scam Shield — Full Project Handoff

> **Written for:** A new developer joining the project with zero prior context.
> **Last updated:** 2026-07-09 | **Deadline:** 31 July 2026
> **GitHub:** https://github.com/govind-ma/scam-shield

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Tech Stack](#2-tech-stack)
3. [Project Structure](#3-project-structure)
4. [Locked Interfaces](#4-locked-interfaces)
5. [What Is Fully Built and Working](#5-what-is-fully-built-and-working)
6. [What Is Built but Not Fully Tested](#6-what-is-built-but-not-fully-tested)
7. [What Is Not Yet Built](#7-what-is-not-yet-built)
8. [Current UI Status](#8-current-ui-status)
9. [Setup Instructions for a New Developer](#9-setup-instructions-for-a-new-developer)
10. [Remaining Work Before Submission](#10-remaining-work-before-submission-priority-order)
11. [Submission Requirements](#11-submission-requirements)
12. [Key Decisions Made and Why](#12-key-decisions-made-and-why)

---

## 1. Project Overview

### What the app is

**Scam Shield** is an Android app that protects elderly and non-technical Indian users from digital fraud — specifically from SMS scams, fake UPI/payment requests, and phone call fraud. The app runs continuously in the background, intercepts incoming messages and payment notifications, analyses them for scam patterns, and immediately warns the user with a full-screen red overlay alert and a spoken voice warning.

Beyond passive detection, it gives users:
- A guided "I Got Scammed" recovery flow with direct bank helpline buttons
- An AI-powered chat assistant ("Cyber Help Agent") powered by Google Gemini API
- A learning section explaining how to identify common scam patterns
- An interactive quiz to train users to spot scams

### Who it is for

The primary user is an **elderly or first-time smartphone user in India** who may not recognise a phishing SMS, may not know to hang up on a fake "bank officer" call, or who might panic and transfer money under pressure. Every design decision — font sizes, plain language in alerts, spoken voice warnings — is made with this user in mind.

### What problem it solves

India saw over Rs 11,333 crore in cyber fraud losses in 2023. Most victims are elderly people targeted by SMS scams, fake UPI payment requests, and impersonation calls. Scam Shield acts as an always-on digital bodyguard.

### Hackathon details

| Field | Value |
|---|---|
| **Hackathon** | To be confirmed — contact Govind |
| **Submission deadline** | **31 July 2026** |
| **Prize** | **Rs 1,00,000** (First prize) |

### One-line pitch

> An always-on scam detection and recovery app that watches your SMS, payment apps, and calls — and speaks up before it is too late.

---

## 2. Tech Stack

### Language and SDK

| Setting | Value |
|---|---|
| Language | **Java** (not Kotlin — see Section 12 for why) |
| minSdk | **24** (Android 7.0 Nougat — covers ~97% of Indian devices) |
| targetSdk | **36** (Android 16) |
| compileSdk | **36** |
| AGP version | **8.7.3** |
| Gradle Wrapper | **8.9** |
| Java source/target compatibility | **Java 11** |

### External Services

| Service | What it is used for | Key detail |
|---|---|---|
| **Google Gemini API** | Powers the Cyber Help Agent AI chat assistant | Model: gemini-2.5-flash. Supports text and vision (image) inputs. Key stored in gradle.properties (never committed). |
| **Firebase** | Planned for Firestore cloud sync | Declared in PROJECT_CONTEXT.md but current DataStore uses SharedPreferences locally. Firebase dependency is NOT yet in app/build.gradle. |

### Android APIs and Libraries

| API or Library | How it is used |
|---|---|
| BroadcastReceiver | SmsReceiver listens for android.provider.Telephony.SMS_RECEIVED |
| NotificationListenerService | PaymentNotificationListener intercepts notifications from Google Pay, PhonePe, Paytm, BHIM, etc. |
| WindowManager overlay | ScamAlertManager draws a full-screen scam alert above all other apps using SYSTEM_ALERT_WINDOW |
| TextToSpeech | Reads the scam warning aloud in ScamAlertManager |
| Foreground Service | FloatingAssistantService keeps the draggable shield bubble on screen permanently |
| SpeechRecognizer | Voice input in ChatAssistantActivity (mic button) |
| SharedPreferences | LocalDataStore persists detection history (capped at 50) and Alert Mode state |
| RecyclerView | Recent Alerts list on the home screen |
| ViewFlipper | Multi-screen flows in IGotScammedActivity and LearnAboutScamsActivity |
| OkHttp3 4.12.0 | HTTP client for Gemini REST API calls in GeminiApiClient |
| Material Components 1.12.0 | Alert dialogs, button styling |
| AndroidX AppCompat 1.7.0 | Base activity, backwards compatibility |
| AndroidX RecyclerView 1.4.0 | Recent alerts list |

---

## 3. Project Structure

### Package map

```
com.scamshield.app                     <- root package
+-- ScamShieldApp.java                 <- Application subclass (global init)
+-- MainActivity.java                  <- Launch point, runtime permission requests
|
+-- engine/                            <- Detection logic (NO UI dependencies)
|   +-- DetectionEngine.java           <- Interface: analyze(rawText, sourceType)
|   +-- DetectionResult.java           <- Data class: verdict, score, reason, sourceType, timestamp
|   +-- DetectionListener.java         <- Interface: onResult(DetectionResult)
|   +-- RuleBasedEngine.java           <- Keyword-based implementation of DetectionEngine
|
+-- sensors/                           <- Input capture (SMS, payments)
|   +-- SmsReceiver.java               <- BroadcastReceiver for incoming SMS
|   +-- PaymentNotificationListener.java  <- NotificationListenerService for UPI apps
|
+-- data/                              <- Storage
|   +-- DataStore.java                 <- Interface (LOCKED — never change)
|   +-- LocalDataStore.java            <- SharedPreferences implementation
|
+-- ui/                                <- All screens and UI services
    +-- ScamAlertManager.java          <- Singleton DetectionListener -> overlay + TTS
    +-- HomeActivity.java              <- Main dashboard screen
    +-- ChatAssistantActivity.java     <- Gemini AI chat (text/image/voice)
    +-- GeminiApiClient.java           <- OkHttp wrapper for Gemini REST API
    +-- CheckSomethingActivity.java    <- Manual text check screen
    +-- IGotScammedActivity.java       <- Recovery Mode guided flow (3 screens)
    +-- LearnAboutScamsActivity.java   <- Educational content (ViewFlipper)
    +-- QuizActivity.java              <- 10-question interactive quiz
    +-- SettingsActivity.java          <- Permissions and settings screen
    +-- FloatingAssistantService.java  <- Foreground service: draggable shield bubble
    +-- HistoryAdapter.java            <- RecyclerView adapter for Recent Alerts
    +-- NavigationHelper.java          <- Bottom nav wiring + Safe/Alert Mode theming
    +-- OverlayPermissionHelper.java   <- Guides user to grant SYSTEM_ALERT_WINDOW
```

### Every file described in one line

| File | Package | What it does |
|---|---|---|
| ScamShieldApp.java | com.scamshield.app | Application subclass. Initialises LocalDataStore, RuleBasedEngine, ScamAlertManager, wires SmsReceiver — all before any screen opens. |
| MainActivity.java | com.scamshield.app | First screen on launch. Requests runtime permissions (SMS, overlay). Hands off to HomeActivity once permissions are granted. |
| DetectionEngine.java | com.scamshield.app.engine | Interface. One method: DetectionResult analyze(String rawText, String sourceType). LOCKED. |
| DetectionResult.java | com.scamshield.app.engine | Data container: Verdict (SAFE/SUSPICIOUS/SCAM), int confidenceScore (0-100), String reason, String sourceType, long timestamp. LOCKED. |
| DetectionListener.java | com.scamshield.app.engine | Interface. One method: void onResult(DetectionResult result). LOCKED. |
| RuleBasedEngine.java | com.scamshield.app.engine | Keyword scorer. Checks 5 rule categories: urgency language, OTP/PIN requests, prize/lottery claims, suspicious URLs, family-emergency plus money-demand impersonation. Returns DetectionResult with score 0-100. |
| SmsReceiver.java | com.scamshield.app.sensors | BroadcastReceiver. Fires on every incoming SMS. Reassembles multi-part PDU messages, feeds text to DetectionEngine, passes result to DetectionListener. Priority 999. |
| PaymentNotificationListener.java | com.scamshield.app.sensors | NotificationListenerService. Intercepts notifications from major UPI apps (Google Pay, PhonePe, Paytm, BHIM). Feeds notification text through the detection engine. |
| DataStore.java | com.scamshield.app.data | Interface. Three methods: getScamNumbers(), logDetection(result), getBankHelpline(bankName). LOCKED — never change signatures. |
| LocalDataStore.java | com.scamshield.app.data | Singleton DataStore impl using SharedPreferences. Detection history as JSON (FIFO, capped at 50). Built-in helplines for 14 Indian banks. Tracks Alert Mode state. |
| ScamAlertManager.java | com.scamshield.app.ui | Singleton DetectionListener. On SCAM/SUSPICIOUS: draws red full-screen WindowManager overlay, runs 30-second Pause-and-Verify countdown lock, speaks reason via TextToSpeech, logs to LocalDataStore, refreshes home screen history. |
| HomeActivity.java | com.scamshield.app.ui | Main dashboard. Shows protection status card, Recent Alerts RecyclerView, four action buttons. Starts FloatingAssistantService (once). Handles Volume Up/Down key as Scan Now shortcut. |
| ChatAssistantActivity.java | com.scamshield.app.ui | Gemini-powered AI chat. Three input modes: typed text, gallery screenshot (vision), voice (SpeechRecognizer). Shows thinking bubble while API call is in flight. |
| GeminiApiClient.java | com.scamshield.app.ui | OkHttp wrapper for Gemini REST API. Text-only and multimodal (image+text) requests. Always injects the Cyber Help Agent system prompt. Returns results on main thread. |
| CheckSomethingActivity.java | com.scamshield.app.ui | Manual text check screen. User pastes text and taps Check. Shows verdict and Get Help Now button if SCAM/SUSPICIOUS. |
| IGotScammedActivity.java | com.scamshield.app.ui | 3-screen guided recovery flow using ViewFlipper. Pick bank, pick situation, see bank helpline + 1930 helpline + cybercrime.gov.in + situation-specific advice. |
| LearnAboutScamsActivity.java | com.scamshield.app.ui | Educational content in 4 checklist tabs (Safe Payments, Before You Pay, Scam Recovery, Common Patterns) using ViewFlipper. |
| QuizActivity.java | com.scamshield.app.ui | Interactive quiz with 10 scam-spotting questions. Immediate correct/incorrect banners with explanations. Score summary at end. |
| SettingsActivity.java | com.scamshield.app.ui | Settings screen. Links to system overlay permission, notification access, and SMS permission settings. |
| FloatingAssistantService.java | com.scamshield.app.ui | Foreground Service. Draggable glowing shield FAB at bottom-right corner. Green in Safe Mode, red in Alert Mode. Tapping opens ChatAssistantActivity. |
| HistoryAdapter.java | com.scamshield.app.ui | RecyclerView.Adapter for Recent Alerts. Color-coded verdict pill (red SCAM / orange SUSPICIOUS / green SAFE), source badge, reason, timestamp. Live refresh without holding an Activity reference. |
| NavigationHelper.java | com.scamshield.app.ui | Static utility. Wires bottom nav tabs in every screen. Applies Safe Mode or Alert Mode color to active tab icon and label. Colors the top status bar per mode. |
| OverlayPermissionHelper.java | com.scamshield.app.ui | Checks if SYSTEM_ALERT_WINDOW is granted. If not, shows a dialog and sends user to system Settings screen to enable it. |

---

## 4. Locked Interfaces

**CRITICAL:** These interfaces are the contract that connects every part of the app.
Never rename a field. Never change a method signature. Never move these to a different package.
If you need to add something, add a new method or subclass — never modify what is here.

### com.scamshield.app.engine

```java
public class DetectionResult {
    public enum Verdict { SAFE, SUSPICIOUS, SCAM }
    public Verdict verdict;
    public int confidenceScore;   // 0-100
    public String reason;         // plain-language explanation for elderly, non-technical user
    public String sourceType;     // "SMS", "CALL", or "PAYMENT"
    public long timestamp;
}

public interface DetectionEngine {
    DetectionResult analyze(String rawText, String sourceType);
}

public interface DetectionListener {
    void onResult(DetectionResult result);
}
```

### com.scamshield.app.data

```java
public interface DataStore {
    java.util.List<String> getScamNumbers();
    void logDetection(DetectionResult result);
    String getBankHelpline(String bankName);
}
```

### Rules for every contributor

1. Only import and reference these interfaces — never redefine them in a new file.
2. Never rename a field or method in the locked interfaces above.
3. If a new feature needs to call an existing module, assume it already exists with the exact shape above — write calling code accordingly.
4. logDetection() must always be called with a complete DetectionResult — never a partial or stubbed version.

---

## 5. What Is Fully Built and Working

All of the following have been confirmed working on emulator (and some on real device).

### Core Detection Pipeline

```
Incoming SMS
    |
    v
SmsReceiver (BroadcastReceiver, priority 999)
    |
    v
RuleBasedEngine.analyze()
    |
    v
ScamAlertManager.onResult()
    |-- LocalDataStore.logDetection()         <- saves to SharedPreferences
    |-- HistoryAdapter.notifyHistoryChanged() <- live refresh on home screen
    |-- WindowManager overlay                 <- red full-screen warning
    +-- TextToSpeech                          <- reads warning aloud
```

**Verified test:** SMS "URGENT: You have won a prize! Share your OTP immediately or your account will be blocked within 24 hours." was correctly flagged as SCAM (confidence 75/100) on emulator.

### Duplicate Alert Bug — Root Cause and Fix

**Bug:** Multiple identical alert entries appeared in Recent Alerts after a single detection event.

**Root cause:** startFloatingService() was called on every onResume() via updateProtectionStatus(). This sent repeated onStartCommand() events to the foreground service. Combined with both setupHistoryList() and onResume() calling refreshHistory() on first launch, the adapter re-rendered with full identical data 2 to 4 times.

**Fix applied:**
- Added floatingServiceStarted boolean flag in HomeActivity. Once the service starts, the flag becomes true and startFloatingService() is a no-op on subsequent calls.
- setupHistoryList() already calls refreshHistory() once internally, so onResume() is the only second-pass refresh (intentional, to pick up new detections that arrive while the user is on another screen).
- LocalDataStore has a dedup guard for true race conditions (same content + same timestamp).

### All confirmed working features

- SMS interception and analysis (SmsReceiver, multi-part PDU reassembly, priority 999)
- Scam alert overlay (red full-screen WindowManager overlay, verdict, confidence score, plain-English reason)
- Voice alert via TextToSpeech (reads the scam reason aloud when overlay appears)
- Pause-and-Verify countdown (30-second lock on SCAM overlay; both action buttons disabled and show countdown, stopping elderly users from panic-tapping)
- Detection history persistence (LocalDataStore, SharedPreferences + JSON, capped at 50 entries FIFO)
- Recent Alerts RecyclerView (live-updating list on HomeActivity with color-coded verdict pills, source badge, reason, timestamp)
- Recovery Mode — IGotScammedActivity (3-screen guided flow: pick bank, pick situation, see helpline + advice; bank helplines for 14 Indian banks and services built in)
- I will be careful button (tapping this on the SCAM overlay launches IGotScammedActivity directly)
- AI Chat Assistant — ChatAssistantActivity (Gemini 2.5 Flash via REST API; text input, image/screenshot upload for vision analysis, voice input via SpeechRecognizer; thinking bubble while API is in flight)
- Learning modules — LearnAboutScamsActivity (4 tabs: Safe Payments, Before You Pay, Scam Recovery redirect, Common Patterns)
- Interactive quiz — QuizActivity (10 questions, immediate feedback banners, score summary)
- Payment notification sensor registered — PaymentNotificationListener (NotificationListenerService for UPI apps)
- Floating shield bubble — FloatingAssistantService (draggable, bottom-right corner, green or red glow animation, opens chat on tap; fixed on Android 14+ with FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
- Volume gesture scan (Volume Up or Down on HomeActivity triggers Scan Now check of clipboard content)
- Bottom navigation (4 tabs: Home, Chat, Learn, Settings; white background with accent color on active tab)
- Safe Mode / Alert Mode theming (top bars and active nav tab icons switch between green #00695C and red #B71C1C based on LocalDataStore.isAlertModeActive())
- Manual text check — CheckSomethingActivity (paste text to check; shows result + Get Help Now button on SCAM/SUSPICIOUS)
- Gradle build system (BUILD SUCCESSFUL via gradlew assembleDebug; AGP 8.7.3, Gradle 8.9)

---

## 6. What Is Built but Not Fully Tested

These features compile and are integrated into the build, but have not been verified end-to-end on a real device yet.

| Feature | File | What still needs verification |
|---|---|---|
| Payment notification interception | PaymentNotificationListener.java | Needs a device test: send a real or simulated payment notification from Google Pay or PhonePe and verify the detection engine fires and logs it. |
| Pause-and-Verify countdown on device | ScamAlertManager.java | The 30-second countdown is implemented. Needs to be observed on a running emulator to confirm both buttons disable correctly and re-enable at 0. |
| Volume gesture scan | HomeActivity.java | Works in code but needs verification that clipboard content is correctly read and the engine + overlay fire on a fresh device session. |
| Alert Mode to Safe Mode toggle | LocalDataStore.java, NavigationHelper.java | State is stored and read. Needs a full UI pass: trigger SCAM, observe red mode; dismiss overlay, observe green mode return across all 4 screens. |
| Floating bubble drag boundary | FloatingAssistantService.java | Drag logic enforces a 76dp bottom margin. Needs testing on multiple screen sizes to confirm it does not overlap the nav bar. |

---

## 7. What Is Not Yet Built

These features are listed in the project plan but have zero code written for them yet.

| Feature | Priority | Notes |
|---|---|---|
| Family/Trusted Contact Alert | High | When SCAM is detected, send an SMS alert to a stored family contact. Needs SEND_SMS permission, a contact picker UI in SettingsActivity, and stored-contact management. |
| Local language support (Hindi/Gujarati) | Medium | Romanised Hindi/Gujarati scam keyword matching in RuleBasedEngine. No ML Kit needed — just an additional keyword array (e.g. "aapka OTP", "turant", "inaam"). |
| Full background resilience testing | High | App needs to survive 30+ minutes screen-off on a real device. Doze mode and OEM kill lists (Samsung, Xiaomi, OnePlus) will likely stop the service. Strategy: WorkManager as a fallback watcher. |
| Final integration test pass | High | Systematic end-to-end test of every entry point: SMS scam, overlay, dismiss, history updated, floating bubble visible, chat assistant accessible. |
| Demo video | Critical for submission | 2-5 minute video showing the app in action on a real device. Required for hackathon submission. |
| Project report | Critical for submission | Written report for the hackathon portal (800-1500 words). |

---

## 8. Current UI Status

### Screens that exist

| Screen | Class | Status |
|---|---|---|
| Permission request screen | MainActivity | Working |
| Home dashboard | HomeActivity | Working |
| AI Chat Assistant | ChatAssistantActivity | Working |
| Manual text check | CheckSomethingActivity | Working |
| Recovery Mode | IGotScammedActivity | Working |
| Learning modules | LearnAboutScamsActivity | Working |
| Quiz | QuizActivity | Working |
| Settings | SettingsActivity | Working |
| Scam alert overlay | Drawn by ScamAlertManager | Working |
| Floating shield bubble | FloatingAssistantService | Working |

### Theme system

Two visual modes that switch automatically based on whether a SCAM is currently active:

| Mode | Trigger | Top bar | Active nav tab | Floating bubble |
|---|---|---|---|---|
| Safe Mode | Default (no active scam) | #00695C (deep teal/green) | #00695C | #00695C |
| Alert Mode | SCAM detected, not dismissed | #B71C1C (dark red) | #B71C1C | #B71C1C |

LocalDataStore.isAlertModeActive() stores the current mode. NavigationHelper.setupBottomNavigation() and NavigationHelper.applyTopBarTheme() read it on every onResume().

### Known remaining UI issues

1. **Bottom nav styling on older devices** — list_selector_background for tap feedback may look dated on Android 7/8. Consider replacing with a ripple drawable.
2. **Chat assistant layout on small screens** — programmatic LinearLayout view inflation may cause the input bar to be partially obscured by the keyboard on screens under 5 inches. Not yet tested.
3. **Empty state label** — the home screen shows an empty state label when there are no alerts. Needs to be confirmed shown/hidden correctly after the first detection event.
4. **Settings screen** — SettingsActivity exists and links to system permissions screens but does not yet have the Add Family Contact UI.
5. **Alert mode auto-reset** — Alert Mode has no auto-reset timer. Could leave the UI permanently red if the user never dismisses the overlay properly.

---

## 9. Setup Instructions for a New Developer

### Prerequisites

- Android Studio (latest stable — Hedgehog or newer)
- JDK 11 (bundled with Android Studio — no separate install needed)
- A Gemini API key — get a free one at https://aistudio.google.com/app/apikey

### Step 1: Clone the repo

```
git clone https://github.com/govind-ma/scam-shield.git
cd scam-shield
```

### Step 2: Create gradle.properties

This file holds your API key. It is **gitignored** and must NEVER be committed.

Create a file named **gradle.properties** in the project root (same folder as build.gradle) with exactly these contents:

```
android.useAndroidX=true
android.enableJetifier=true
GEMINI_API_KEY=get_this_from_govind_privately
```

Replace the placeholder with the real Gemini API key. Contact Govind directly — the key is not in the repo.

A template file called gradle.properties.example is already in the repo root for reference.

Note on android.enableJetifier=true: Some transitive dependencies still reference old android.support.* classes. Jetifier automatically rewrites them to AndroidX equivalents at build time.

### Step 3: Open in Android Studio

1. Open Android Studio
2. Choose **Open** (not Import)
3. Navigate to and select the scam-shield folder
4. Let Gradle sync complete (may take 2-3 minutes on first run)

If Gradle sync fails, check:
- gradle.properties exists with all three lines above
- You are on a stable internet connection
- Your JDK is set to Java 11 in File > Project Structure > SDK Location > JDK Location

### Step 4: Build the project

```
# Windows
gradlew.bat assembleDebug

# Mac or Linux
./gradlew assembleDebug
```

Expected output: **BUILD SUCCESSFUL**
APK location: app/build/outputs/apk/debug/app-debug.apk

### Step 5: Run on a device or emulator

**On an emulator:**
1. In Android Studio, open Device Manager and create a Pixel 5 API 34 emulator
2. Press Run (Shift+F10)

**On a real Android device:**
1. Enable Developer Options: Settings > About Phone > tap Build number 7 times
2. Enable USB Debugging in Developer Options
3. Connect via USB and press Run in Android Studio

**Critical permissions to grant after first launch:**
- Allow Scam Shield to send and view SMS messages (needed for SMS detection)
- Allow drawing over other apps (required for the scam alert overlay — the app will guide you to Settings)
- Allow notification access (for PaymentNotificationListener — go to Settings > Notification Access > enable Scam Shield)

---

## 10. Remaining Work Before Submission (Priority Order)

Deadline: **31 July 2026** (approximately 22 days from time of writing)

| # | Task | Est. time | Why it matters |
|---|---|---|---|
| 1 | Device test full detection pipeline on a real Android phone | 2-3 hours | Real devices (Samsung/Xiaomi especially) have stricter background kill policies. May reveal the service is being killed after screen-off. |
| 2 | Fix background persistence (Doze mode and battery optimisation) | 1-2 days | SMS and payment sensors may stop working after 30 minutes of screen-off. Strategy: persistent foreground notification (already in FloatingAssistantService) plus WorkManager as a fallback watcher. |
| 3 | Verify and demo PaymentNotificationListener end-to-end | 3-4 hours | Needs a real device with Google Pay or PhonePe installed. Simulate a payment notification and verify it is intercepted, analysed, and logged. |
| 4 | Build Family Contact Alert | 1 day | User enters a trusted contact phone number in Settings. When SCAM is detected, app sends them an SMS alert. Needs SEND_SMS permission and a contact picker UI. |
| 5 | Verify Alert Mode to Safe Mode round-trip | 1-2 hours | Trigger SCAM, see red mode, dismiss, confirm return to green across all 4 screens. |
| 6 | Hindi and Gujarati keyword support (basic) | 1 day | Add romanised Hindi/Gujarati scam phrases to RuleBasedEngine as a second keyword array. No ML Kit needed. |
| 7 | Record the demo video | 2-3 hours | 2-5 minutes on a real device. Show: launch, SMS scam arrives, overlay + TTS, Pause-and-Verify countdown, Recovery Mode, Chat Assistant. Upload to YouTube (unlisted is fine). |
| 8 | Write the project report | 3-4 hours | 800-1200 words for the hackathon portal: problem, solution, tech stack, impact. |
| 9 | Fill in the 5 portal submission descriptions | 1-2 hours | See Section 11 for draft text. |
| 10 | Final build and submit | 30 minutes | Generate release APK or submit GitHub link + video URL as required by the portal. |

---

## 11. Submission Requirements

| Item | Status | Notes |
|---|---|---|
| GitHub repo | Done | https://github.com/govind-ma/scam-shield — make public before submission |
| Demo video | Not recorded | 2-5 minutes on a real device. Cover: SMS detection, overlay, TTS, countdown, recovery, chat assistant. Upload to YouTube or Vimeo. |
| Project report | Not written | 800-1500 words. Cover: problem statement, solution, tech architecture, how it works, impact. |
| 5 portal descriptions | Not written | Draft text below — edit as needed. |

### Suggested portal descriptions (draft — edit as needed)

**1. One-line pitch:**
Scam Shield is an Android app that watches your SMS and payment notifications in real time and warns elderly users before they fall victim to digital fraud.

**2. Problem:**
India lost over Rs 11,333 crore to cyber fraud in 2023. The primary victims are elderly, non-technical users who cannot recognise phishing SMS messages, fake UPI payment requests, or impersonation calls. No existing app provides real-time always-on protection designed specifically for this audience.

**3. Solution:**
Scam Shield runs continuously in the background, intercepts incoming SMS and payment app notifications, and analyses them using a keyword-based detection engine augmented by Google Gemini AI. When a scam is detected, it immediately shows a full-screen red warning overlay and reads the warning aloud. A built-in guided Recovery Mode gives victims step-by-step instructions and one-tap access to bank helplines and the 1930 National Cyber Crime Helpline.

**4. Tech stack:**
Android (Java), Google Gemini 2.5 Flash API (text and vision AI chat), NotificationListenerService (payment monitoring), BroadcastReceiver (SMS monitoring), WindowManager overlay (real-time alerts), TextToSpeech (voice warnings), SharedPreferences with JSON (offline-first data storage).

**5. Target users and impact:**
Primary target: elderly and first-time smartphone users in India, particularly in Tier 2 and Tier 3 cities. The app requires no technical knowledge — every alert is spoken aloud in plain language, every action is a single large button. Secondary target: family members who want to protect elderly relatives remotely via the Family Alert feature.

---

## 12. Key Decisions Made and Why

### Java, not Kotlin

The project is entirely in Java. This was a deliberate choice because Govind (the primary developer) is learning Java as part of a structured course. Writing in the language you understand deeply produces better, more maintainable code than writing in a language you are still learning. Java is fully capable for an Android app of this complexity. There is no functional disadvantage versus Kotlin.

### NotificationListenerService for payment monitoring, not AccessibilityService

We use NotificationListenerService rather than AccessibilityService to intercept payment app notifications. NotificationListenerService is simpler to register (one manifest entry plus user enables it in Settings), has a cleaner API (just onNotificationPosted()), and is specifically designed for reading notification content. AccessibilityService is more powerful but requires significantly more code, more permissions, and is more likely to be blocked by OEM security policies on Xiaomi and Samsung. For our use case (reading payment notification text), NotificationListenerService is the right tool.

### Rule-based engine first, LLM second (hybrid approach)

The RuleBasedEngine runs locally, instantly, with zero network cost. It catches clear-cut scam patterns (OTP requests, prize claims, urgency pressure) without any delay. The Gemini AI chat (ChatAssistantActivity) is available as a second, deeper layer when the user wants to discuss a specific message. This hybrid approach is cost-efficient — the vast majority of detections are handled locally for free — and works offline, which is critical for rural Indian users with poor connectivity.

### commit() not apply() for critical DataStore writes

In LocalDataStore, detection history is written using commit() (synchronous) rather than apply() (asynchronous) in cases where a race condition could cause data loss — specifically, when a second detection fires before the first write completes. apply() is used for non-critical state like the Alert Mode flag, where async is safe. For history, commit() is slower but guaranteed.

Note: The class-level documentation in LocalDataStore.java says apply() is used everywhere — this comment is outdated. The actual implementation uses commit() for history writes to solve a race condition found during testing. That comment should be updated.

### Card-based UI, not full-bleed color blocks

Early UI iterations used solid color blocks for the dashboard sections. These were replaced with a card-based layout (white cards on a light gray background) with the accent color only on the header, active nav tab, and action buttons. This looks more professional, is easier to read at a glance, and works better with the Safe/Alert Mode color switching because you only change a few accent elements, not the entire screen background.

### Safe Mode (green) / Alert Mode (dark red) theme system

The app has two persistent visual states. Safe Mode (#00695C teal-green) is the default. Alert Mode (#B71C1C dark red) activates when a SCAM detection occurs and persists until the user dismisses the overlay. This mode persists across screen changes (stored in LocalDataStore) so the user always knows their protection status at a glance. The color values were chosen for high contrast on both light and dark backgrounds and for accessibility — not relying on hue alone, since saturation and brightness are also meaningfully different between the two modes.

---

## Appendix: Bank Helplines Built into the App

These are hardcoded in LocalDataStore and available via getBankHelpline(bankName):

| Bank or Service | Helpline |
|---|---|
| SBI / State Bank | 1800-11-2211 |
| HDFC | 1800-202-6161 |
| ICICI | 1800-1080 |
| Axis | 1800-419-5959 |
| Kotak | 1860-266-2666 |
| PNB / Punjab National Bank | 1800-180-2222 |
| Bank of Baroda | 1800-5700 |
| Canara | 1800-425-0018 |
| Union | 1800-22-2244 |
| Paytm | 0120-4456-456 |
| PhonePe | 080-68727374 |
| Google Pay / GPay | 1800-419-0157 |
| National Cyber Crime Helpline | 1930 |
| RBI | 14440 |

---

*End of handoff document. Questions? Contact Govind directly.*
