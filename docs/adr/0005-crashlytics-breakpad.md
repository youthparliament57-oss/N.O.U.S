# ADR 0005 — Firebase Crashlytics + Breakpad for Crash Reporting

Date: 2026-07-04
Status: Accepted
Decision Owner: Roshan

## Context

NOUS has both Kotlin (JVM) crash surface and native C/C++ crash surface (llama.cpp JNI, ONNX Runtime, TFLite). A complete crash reporting strategy must cover both:

- **Kotlin exceptions** — uncaught in main thread, async exceptions in coroutines
- **Native crashes** — SIGSEGV, SIGABRT, SIGBUS in JNI code (libjarvis_llm.so, libonnxruntime.so)
- **ANRs** — main thread blocks > 5s
- **OOM kills** — both Java heap and native heap
- **App Not Responding (ANR)** — main thread blocking

## Decision

Adopt **Firebase Crashlytics** as primary crash reporter, with **Breakpad** for native stack symbolication.

### Setup
- `firebase-crashlytics` Gradle plugin
- `firebase-crashlytics-ndk` for native crashes
- Breakpad symbol files (`*.sym`) uploaded to Crashlytics per release build
- Custom `CrashHandler` (`Thread.setDefaultUncaughtExceptionHandler`) for state snapshot before crash
- Custom `ANRWatchdog` thread for main-thread block detection

### Telemetry Layers
1. **Crashlytics** — crashes, ANRs, OOMs (free, Google-backed)
2. **Custom metrics** — via `:core:telemetry` (local aggregation, optional OTel export)
3. **Audit log** — encrypted, local, user-accessible (separate from Crashlytics)

## Consequences

**Positive:**
- Free tier covers most volume
- Native NDK crash support with symbolication
- Real-time crash alerts
- Per-user crash insights (with consent)
- Firebase App Check integration (anti-tamper)

**Negative:**
- Firebase SDK adds ~2MB to APK
- Google dependency — privacy-conscious users wary
- Crashlytics sends crash data to Google servers (user consent required)

**Mitigation:**
- Crashlytics **disabled by default** in `dev` flavor (verbose logging instead)
- In `prod`, Crashlytics enabled only after user opt-in (privacy default = maximum privacy)
- Crash data contains no PII (PII scrubbing in `:core:telemetry` before any report)
- Document data flow in `docs/data-flow.md`

## Future Migration Path

If Crashlytics becomes a blocker (cost, policy, scale), migrate to:
- **Bugsnag** (paid, better UX, similar feature set)
- **Sentry** (open-source self-host option, strongest native support)

Architecture is abstracted behind `:core:telemetry` so migration is local.

## Alternatives Considered

- **Bugsnag (now)**: rejected — paid, premature
- **Sentry (now)**: rejected — would self-host, ops overhead
- **ACRA**: rejected — needs self-hosted backend
- **No crash reporter**: rejected — unacceptable for production app
