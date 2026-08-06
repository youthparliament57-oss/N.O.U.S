# ADR 0004 — CI/CD on GitHub Actions

Date: 2026-07-04
Status: Accepted
Decision Owner: Roshan

## Context

NOUS requires continuous integration with:
- Static analysis (ktlint, detekt, Android Lint)
- Unit + integration + UI tests across multiple API levels
- Macrobenchmark enforcement of performance budgets
- Release builds signed and uploaded to Play Store Internal Testing
- Cost-effective (project is currently self-funded)

## Decision

Adopt **GitHub Actions** as the primary CI/CD platform.

### Pipeline (11 stages, defined in `.github/workflows/ci.yml`)
1. Checkout + cache (Gradle, NDK, SDK)
2. Static analysis (ktlint + detekt + Lint)
3. Unit tests + coverage
4. Build debug APK
5. Integration tests (Robolectric)
6. UI tests (emulator matrix: API 29, 31, 34)
7. Macrobenchmark (cold start, brain latency)
8. Build release AAB
9. Sign
10. Upload to Play Internal Testing
11. Notify Slack/Discord

### Release Strategy
- `develop` → nightly build → Internal Testing
- `release/x.y.z` → release candidate → Closed Testing
- `main` → production → Phased rollout (10% → 50% → 100%)

### Versioning
- Hybrid CalVer + SemVer: `2026.7.4-rc1`
- `versionCode` monotonic integer
- Conventional Commits → auto changelog

## Consequences

**Positive:**
- Free for public repos, 2000 min/month free for private
- Tight integration with GitHub (PR checks, branch protection)
- Self-hosted runners available for heavier jobs (native build)
- Large ecosystem of prebuilt Actions

**Negative:**
- macOS runners (needed for iOS parity / iOS apps) are 10x cost — not relevant for Android-only
- Emulator startup is slow (~5 min) — mitigated by AVD caching
- 6-hour job timeout — heavy native builds may need splitting

**Mitigation:**
- Aggressive caching (Gradle, NDK, AVD snapshots, ccache for native)
- Self-hosted runner for native build stage (future)
- Bitrise orFirebase Test Lab for device farm when paid budget available

## Future Migration Path

When budget allows, migrate to **Bitrise** for:
- Mobile-specialized stack (faster emulators, device farm included)
- Better native build caching
- Visual pipeline editor

GitHub Actions will remain as PR-check layer; Bitrise for release pipeline.

## Alternatives Considered

- **Bitrise (now)**: rejected — paid, premature for current phase
- **CircleCI**: rejected — less Android-specialized than Bitrise
- **GitLab CI**: rejected — would require repo migration
- **Jenkins (self-hosted)**: rejected — ops overhead
