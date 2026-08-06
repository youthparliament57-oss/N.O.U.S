# ADR 0007 — Play Integrity Soft Enforcement

Date: 2026-07-04
Status: Accepted
Decision Owner: Roshan

## Context

NOUS uses several security-sensitive features that should not run on compromised devices:

- **Password Vault** (Argon2 + AES-256)
- **Voice biometrics** (MFCC + ECAPA-TDNN)
- **Cloud LLM OAuth tokens** (stored in Keystore)
- **Bank SMS parsing** (financial data)
- **Voice cloning** (could be misused)

However, NOUS's target audience includes **power users** who legitimately root their devices for:
- Terminal access (legitimate dev use)
- Better backup tooling
- Custom ROMs (de-Googled phones)
- Pentesting their own devices (the hacker module exists for this)

Hard-blocking root would alienate the primary audience.

## Decision

Adopt **soft enforcement** of Play Integrity:

### Policy
1. On launch, run **Play Integrity API** attestation
2. If device fails (rooted, unlocked bootloader, tampered app):
   - **Block** sensitive modules:
     - Password Vault (disabled — encrypted at rest, but won't unlock)
     - Voice biometrics (disabled — fallback to PIN)
     - Cloud LLM OAuth (disabled — local LLM only)
     - Bank SMS parsing (disabled)
     - Voice cloning (disabled)
   - **Allow** all other features:
     - Local LLM inference
     - Voice STT/TTS
     - Vision
     - Personas
     - Brain
     - Hacker module (ironically — power users root specifically for this)
3. **No app block** — user is informed, not locked out
4. Result cached for 24 hours (don't spam Play Integrity API)

### Implementation
- `:core:security` module exposes `IntegrityAttestation` Flow
- Each sensitive feature checks `IntegrityAttestation.isValid` before operation
- UI shows warning chip when integrity is compromised
- User can dismiss warning per-session

## Consequences

**Positive:**
- Power users not alienated — root users get most features
- Sensitive data protected on compromised devices
- Transparent — user knows when integrity is compromised
- Compliant with Play Policy (we don't ship root detection that hard-blocks)

**Negative:**
- Sensitive features unavailable on legitimate rooted devices (e.g., developer's own Pixel for testing)
- Play Integrity API has quota limits (10k requests/day free)
- Cache could be stale (24h window for device state change)

**Mitigation:**
- `dev` flavor skips integrity check entirely (for developer testing)
- Cache TTL configurable via Remote Config
- Document which features are gated by integrity in user-facing docs

## Alternatives Considered

- **Hard block on root**: rejected — alienates primary audience
- **No integrity check**: rejected — sensitive data at risk on tampered devices
- **SafetyNet (legacy)**: rejected — deprecated, replaced by Play Integrity
- **Self-implemented root detection**: rejected — easy to bypass, doesn't attest to cloud
