# ADR 0012 — License: Proprietary (Personal), Future Commercial

Date: 2026-07-04
Status: Accepted
Decision Owner: Roshan

## Context

NOUS is currently a personal project by Roshan. Future plans may include:
- Commercial release (paid app, subscription, or freemium)
- Open-sourcing some core modules
- Enterprise licensing
- Acquisition interest

The license choice affects all of these. Open-source now (MIT/Apache) means future commercialization is harder (can't revoke the OSS license). Proprietary now means future flexibility.

## Decision

Adopt **proprietary license** for now:

### Terms
```
Copyright (c) 2026 Roshan. All rights reserved.

NOUS is proprietary software. Unauthorized copying, modification, distribution,
or use of this software, via any medium, is strictly prohibited without
prior written consent from the copyright holder.

Personal use is granted to the original downloader for the purpose of
evaluation and personal productivity. Commercial use, redistribution, or
inclusion in other products requires a separate commercial license.
```

### File Header
Every Kotlin file has the copyright header (enforced by Spotless):
```kotlin
// Copyright (c) 2026 Roshan. All rights reserved.
// Proprietary license — see LICENSE file for details.
```

### LICENSE File
Root `LICENSE` file contains the full proprietary license text.

## Consequences

**Positive:**
- Full commercial flexibility in the future
- Can choose to open-source specific modules later without revoking
- No obligation to publish source code
- Can pursue enterprise licensing or acquisition without complication

**Negative:**
- No community contributions (PRs require CLA assigning copyright to Roshan)
- No F-Droid listing (F-Droid requires FOSS license)
- Some developers won't engage with proprietary code

**Mitigation:**
- Future plan: open-source `:core:*` modules under Apache 2.0 once architecture is stable
- Keep `:feature:*` and `:app` proprietary for commercial differentiation
- Document the dual-license plan in CONTRIBUTING.md

## Future Migration Path

### Phase 1 (Now — Personal)
- Full proprietary
- Solo development
- No external contributions

### Phase 2 (Growth — Community)
- Open-source `:core:*` modules under Apache 2.0
- Accept community PRs to core modules
- Feature modules remain proprietary
- CLA required for core contributions

### Phase 3 (Commercial — Production)
- Commercial license for enterprise
- Subscription tier for pro features
- Self-hosted option for privacy-focused enterprises

## Alternatives Considered

- **MIT**: rejected — too permissive, kills commercial potential
- **Apache 2.0**: rejected — same as MIT for commercial purposes
- **GPL v3**: rejected — copyleft传染性, restricts future closed-source use
- **AGPL v3**: rejected — even more restrictive, network-use triggers source disclosure
- **BSL (Business Source License)**: considered — interesting but complex
- **PolyForm Noncommercial**: considered — too restrictive for paid plans later
