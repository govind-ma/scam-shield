# Scam Shield — Project Context

> **This file is the single source of truth for the project.**
> Update the Status section after each part is completed.
> All contributors (human or AI) must read this before writing any code.

---

## App Identity

| Field | Value |
|-------|-------|
| App name | Scam Shield |
| Language | Java (not Kotlin) |
| Base package | `com.scamshield.app` |

---

## Package Structure

```
com.scamshield.app.engine   →  DetectionEngine, DetectionResult, RuleBasedEngine
com.scamshield.app.sensors  →  SmsReceiver, CallListener, PaymentNotificationListener
com.scamshield.app.ui       →  all screens, overlay, DetectionListener implementation
com.scamshield.app.data     →  DataStore, Firestore/JSON access
```

---

## Locked Interfaces — FINAL. Do not rename fields or change method signatures.

### `com.scamshield.app.engine`

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

### `com.scamshield.app.data`

```java
public interface DataStore {
    java.util.List<String> getScamNumbers();
    void logDetection(DetectionResult result);
    String getBankHelpline(String bankName);
}
```

---

## Build Status

| Part | Description | Status |
|------|-------------|--------|
| A | DetectionEngine + RuleBasedEngine | ✅ DONE |
| B | SmsReceiver (SMS sensor) | ✅ DONE |
| C | Dashboard/UI + DetectionListener implementation | ✅ DONE |
| D | DataStore (Firestore + local JSON) | ✅ DONE |
| E | Recovery Mode, Pause-and-Verify, Family Alert | 🔶 PARTIAL (Recovery, Learn, Quiz, Sensor, & Countdown built) |

---

## Rules for Every Part

1. Only import/reference the interfaces above — never redefine them in a new file.
2. Never rename a field or method in the locked interfaces section above.
3. If a new part needs to call an existing part (e.g., UI needs to call `DetectionEngine`),
   assume it already exists with the exact shape above — write the calling code accordingly,
   do not stub out a fake version.
4. End every response with a one-line note confirming which package/class names were used,
   so it can be pasted back into this file to keep it updated.
