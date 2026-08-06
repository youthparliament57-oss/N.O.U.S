# ADR 0009 — Hacker Module — Flavor-Gated

Date: 2026-07-04
Status: Accepted
Decision Owner: Roshan

## Context

NOUS includes an ethical-hacking toolkit:
- **Network Scanner** (port scan, OS fingerprint)
- **Terminal Executor** (shell commands, root optional)
- **WiFi Password Reader** (extracts saved WiFi passwords, requires root)
- **IP Camera Scanner** (discovers IP cameras on LAN)
- **Steganography Engine** (hide data in images)
- **Tor SOCKS5 Proxy** (anonymized traffic)
- **Ultron Network Hopper** (auto-hops WiFi/cellular/Tor)

These features serve legitimate use cases:
- Security professionals pentesting their own networks
- Defensive security (detecting rogue devices on home network)
- Privacy advocates (Tor for censorship circumvention)
- Power users (terminal access on their own device)

But Play Policy risk:
- "Apps that facilitate or encourage unauthorized access to systems" — borderline
- WiFi password extraction — almost certainly rejected
- Terminal with root access — high review scrutiny
- Network scanning — flagged as potential attack tool

## Decision

Adopt **flavor gating + dynamic feature delivery** for the hacker module:

### Flavor Behavior
| Flavor | Hacker Module? | Delivery |
|---|---|---|
| `dev` | ✅ Yes | Bundled |
| `internal` | ✅ Yes | Dynamic feature (on-demand) |
| `prod` (Play Store) | ❌ No | Not available at all |

### Implementation
1. `:dynamicfeature:hacker` is a **Dynamic Feature Module** (DFM)
2. In `prod` flavor, DFM excluded from bundle (not installable from Play)
3. In `internal`/`dev` flavor, DFM available for on-demand install
4. `BuildConfig.ENABLE_HACKER` flag gates runtime access
5. UI hides Hacker screen when flag is false
6. Module uses its own process (`android:process=":hacker"`) for isolation
7. Module is its own WorkManager scheduler — won't block main app

### Per-Feature Gating (Within Hacker Module)
Some sub-features are even more sensitive. Additional gating:
- **WiFi Password Reader**: requires root, gated by `BuildConfig.ENABLE_WIFI_PASSWORD`
- **Steganography**: gated by `BuildConfig.ENABLE_STEGANOGRAPHY`
- **Tor**: gated by `BuildConfig.ENABLE_TOR`
- **Terminal**: gated by `BuildConfig.ENABLE_TERMINAL`

Each can be independently disabled via Remote Config kill switch.

## Consequences

**Positive:**
- Play Store submission safe from policy rejection on this axis
- Power users get full hacker module via sideload
- DFM keeps base APK small (~5MB saved)
- Per-feature kill switch enables surgical disable if vulnerability found

**Negative:**
- Play Store users miss entire module category
- DFM complexity (Play Install Library, deferred install UX)
- Module isolation adds IPC overhead

**Mitigation:**
- Hacker module clearly labeled "for authorized security testing only"
- Disclaimer on first launch (user must accept)
- Audit log records every hacker operation (encrypted)
- In-app banner explains why feature is disabled in Play build

## Alternatives Considered

- **Ship in Play Store**: rejected — near-certain rejection, account risk
- **Remove entirely**: rejected — core to NOUS identity
- **Ship via separate app (NOUS Hacker)**: considered — deferred, marketing overhead
