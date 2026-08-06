# ADR 0011 — Maximum Privacy as Default

Date: 2026-07-04
Status: Accepted
Decision Owner: Roshan

## Context

NOUS's positioning is "on-device AI assistant — your data never leaves your device unless you opt in". This is a core brand promise. But "privacy by default" can mean many things:

- **All cloud off by default** — strictest interpretation
- **Cloud on for specific providers** (e.g., Gemini free tier on by default)
- **Cloud on with anonymized telemetry** — convenience + privacy
- **User prompted on first cloud use** — opt-in per provider

Different audiences want different defaults:
- Privacy enthusiasts → strictest (all off)
- Casual users → convenience (cloud on)
- Mixed audience (NOUS's case) → ???

## Decision

Adopt **maximum privacy as default**:

### Default Settings (First Launch)
| Setting | Default Value | User Override |
|---|---|---|
| Cloud LLM providers | All OFF | Toggle per provider |
| Crash reporting (Crashlytics) | OFF | Toggle in Settings → Privacy |
| Anonymous telemetry | OFF | Toggle in Settings → Privacy |
| Ambient awareness (mic/camera/location) | OFF | Toggle per feature |
| Voice wake word | OFF | Toggle (asks for mic permission) |
| Voice biometrics (Voice Lock) | OFF | Toggle (asks for mic + training) |
| Health sensor tracking | OFF | Toggle (asks for body sensors) |
| Bank SMS parsing | OFF | Toggle (asks for SMS permission) |
| Contact access | OFF | Toggle (asks for contacts) |
| Calendar access | OFF | Toggle (asks for calendar) |
| Email integration | OFF | Toggle (asks for account) |
| Notifications listener | OFF | Toggle (asks for notif access) |
| Accessibility service | OFF | Toggle (asks for a11y) |
| Background location | OFF | Toggle (asks for bg location) |
| Multi-device sync | OFF | Toggle (asks for account) |

### Principle
Every feature that accesses sensitive data or sends data off-device is **OFF by default**. User must explicitly turn it on, and is shown:
- What data the feature accesses
- Where that data goes (device-only vs cloud)
- What they get in return (the feature's benefit)

### Implementation
- `:core:datastore` holds `PrivacySettings` (Proto DataStore, encrypted)
- Every feature checks `PrivacySettings.isMyFeatureEnabled` before operating
- UI uses `PrivacyGate` Composable to show "Enable X" prompt when feature invoked but disabled
- Audit log records every privacy-setting change

## Consequences

**Positive:**
- Brand promise honored — "on-device first" is true by default
- User in control — no surprises about data flow
- Compliant with GDPR/CCPA "data minimization" principle
- Trust-building — user sees app respects their data

**Negative:**
- First-run experience is permission-heavy (mitigated by progressive disclosure)
- Some users may not discover features they want (mitigated by in-context prompts)
- Cloud providers (OpenAI, Anthropic) get less traffic (mitigated by easy opt-in)

**Mitigation:**
- Progressive onboarding — user enables features as they use them
- "Recommended setup" optional flow during onboarding (still requires explicit consent)
- In-context "Enable X to do Y" prompts (e.g., user says "call mom" → prompt to enable contacts)
- Privacy Dashboard in Settings — visual overview of what's enabled

## Alternatives Considered

- **Convenience default (cloud on)**: rejected — contradicts brand promise
- **Hybrid (Gemini on, others off)**: rejected — Gemini free tier limits, prefer neutral
- **User prompted per-provider on first use**: considered — too many prompts, deferred to v2
