# ADR 0006 — Distribution: Play Store + GitHub Releases

Date: 2026-07-04
Status: Accepted
Decision Owner: Roshan

## Context

NOUS includes features that conflict with Google Play Policies:

- **Self-modifying JS engine (Rhino)** — Play Policy: "Apps must not download executable code" (with narrow exceptions for interpreted JS in WebView). Rhino is NOT in WebView — high policy risk.
- **Hacker module** (network scanner, terminal executor, WiFi password reader) — Play Policy: cannot be used to attack devices you don't own; high review scrutiny.
- **Steganography** — can be flagged as covert channel tool.
- **Tor SOCKS5 proxy** — generally OK, but flagged for review.
- **APK signature verification bypass** — explicitly forbidden.

Power users (the actual target audience for hacker/self-modify features) want sideload capability anyway.

## Decision

Adopt **dual distribution** strategy:

### Channel 1: Google Play Store (`prod` flavor)
- Sensitive features **disabled at compile time** via `BuildConfig` flags
- R8 resource shrinking removes their code paths
- Remote Config kill switch for runtime fallback
- AAB (App Bundle) format, Play App Signing
- Phased rollout: 10% → 50% → 100%
- Models delivered via Play Asset Delivery (separate from APK)

### Channel 2: GitHub Releases (`internal` flavor, same signing key)
- All features enabled
- APK format (sideload), signed with same upload key
- Versioned with Conventional Commits auto-changelog
- Direct download from GitHub Releases page
- OTA model updates still work (no Play dependency)

### Signing
- Same `keystore.properties` for both channels (Play App Signing only changes the release key — upload key is same)
- Self-signing for sideload builds (no Google review)

## Consequences

**Positive:**
- Play Store presence for mainstream users
- Power users get full-featured sideload build
- No compromise on features (Play build loses some, sideload keeps all)
- Same codebase, same tests — only flavor differs
- GitHub Releases provides free CDN for sideload APKs

**Negative:**
- Two channels to maintain (release notes, version sync)
- Sideload users miss Play security updates (mitigated by in-app update check)
- Two sets of Crashlytics data (mitigated by `BuildConfig.NOUS_ENVIRONMENT` tag)

**Mitigation:**
- Automated release workflow (GitHub Action builds both APK + AAB on tag push)
- In-app update checker for sideload builds (compares to GitHub Releases latest)
- Single changelog source (Conventional Commits)

## Alternatives Considered

- **Play Store only (drop sensitive features)**: rejected — defeats purpose for power users
- **Sideload only**: rejected — misses mainstream audience
- **F-Droid**: considered — would be additional channel, deferred for now
- **Amazon Appstore / Samsung Galaxy Store**: deferred — minor reach, low priority
