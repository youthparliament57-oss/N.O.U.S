# Contributing to NOUS

> *"The mind that perceives."*

Thanks for your interest in contributing to NOUS! This document covers everything you need to know to set up the project, make changes, and submit PRs.

## 📋 Table of Contents
1. [Quick Start](#quick-start)
2. [Project Architecture](#project-architecture)
3. [Code Style](#code-style)
4. [Commit Convention](#commit-convention)
5. [Pull Request Process](#pull-request-process)
6. [Testing](#testing)
7. [License Header](#license-header)
8. [Architecture Decision Records](#architecture-decision-records)

---

## Quick Start

### Prerequisites
- Android Studio (latest stable)
- JDK 17
- Android SDK 36
- NDK 27.1.12297006
- CMake 3.22.1+

### Setup
```bash
# 1. Clone
git clone https://github.com/roshan/nous-android.git
cd nous-android

# 2. Copy example secrets (no real keys needed for dev builds)
cp secrets.gradle.example secrets.gradle
cp local.properties.example local.properties
# Edit local.properties to point to your Android SDK

# 3. Install pre-commit hook
cp scripts/pre-commit .git/hooks/pre-commit
chmod +x .git/hooks/pre-commit

# 4. Build dev debug APK
./gradlew :app:assembleDevDebug

# 5. Run tests
./gradlew test
```

### Common Gradle Commands
```bash
./gradlew assembleDevDebug                  # Dev debug APK (all features enabled)
./gradlew assembleProdInternalRelease       # Sideload release APK (all features, signed)
./gradlew assembleProdRelease               # Play Store release AAB (sensitive features disabled)

./gradlew detekt                            # Static analysis
./gradlew spotlessApply                     # Auto-format code
./gradlew spotlessCheck                     # Verify formatting
./gradlew lint                              # Android Lint

./gradlew test                              # Unit tests
./gradlew connectedAndroidTest              # Instrumentation tests (needs device)
./gradlew koverReport                       # Coverage report

./gradlew dependencyUpdates                 # Check for outdated deps
./gradlew dependencyCheck                   # CVE scan
```

---

## Project Architecture

NOUS is a **multi-module Gradle project** with 34 modules organized in 5 layers:

```
:core:*        (11) — Foundation, no feature logic
:feature:*     (15) — Vertical features (brain, voice, vision, ...)
:dynamicfeature:* (4) — Installable on-demand via Play
:native:*      (3)  — NDK / JNI bridges
:app           (1)  — Application entry point
```

### Dependency Rules (Enforced by Detekt)
1. **Core never depends on Feature**
2. **Feature never depends on another Feature directly** (only via interfaces in `:core:common` or via the Brain bus)
3. **UI never depends on Data layer** (only via ViewModel)
4. **Native modules wrapped by Feature** (never used directly by UI)

See [ADR 0003](docs/adr/0003-multi-module-architecture.md) for full rationale.

### Convention Plugins
Each module applies a convention plugin that bundles shared config:
- `nous.android.application` — for `:app` (flavors, signing, R8)
- `nous.android.library` — for `:core:*`, `:native:*`
- `nous.android.feature` — for `:feature:*` (includes Compose + Hilt + Test)
- `nous.android.compose` — opt-in for Compose-using modules
- `nous.android.hilt` — opt-in for Hilt-using modules

Module `build.gradle.kts` files should be ~5 lines. If yours is longer, ask why.

---

## Code Style

NOUS enforces style via three tools:
1. **Spotless** + **ktlint** — formatting (run `./gradlew spotlessApply`)
2. **Detekt** — code smells + custom rules (run `./gradlew detekt`)
3. **Android Lint** — Android-specific issues (run `./gradlew lint`)

### Key Rules
- **No `lateinit var`** — use `by lazy` or constructor injection
- **No magic numbers** — extract to `companion object` constants
- **No `it` for complex lambdas** — use named parameter
- **No `printStackTrace()`** — use Timber
- **No `Dispatchers.IO` directly** — inject via `@IoDispatcher` qualifier
- **No wildcard imports** (except `androidx.compose.material3.*` and similar)
- **Max line length: 140 chars**
- **Max method length: 80 lines**
- **Max cyclomatic complexity: 15**

### Naming Conventions
- Classes: `PascalCase`
- Functions: `camelCase` (except `@Composable` functions which are `PascalCase`)
- Constants: `UPPER_SNAKE_CASE`
- Packages: `alllowercase`
- Compose previews: suffixed with `Preview` (e.g., `MyScreenPreview`)

### Coroutines
- Always inject dispatchers via Hilt qualifiers
- Use structured concurrency (parent-child relationship)
- Use `CoroutineExceptionHandler` for top-level scopes
- Use `Result<T>` for fallible operations (not try/catch)

### Dependency Injection
- Use Hilt everywhere
- Scopes: `@Singleton`, `@FeatureScope`, `@ViewModelScoped`, `@ActivityScoped`
- Multibinding for plugin-style registration (personas, skills, brain layers)
- Qualifiers for dispatchers: `@IoDispatcher`, `@DefaultDispatcher`, `@LlmDispatcher`, etc.

---

## Commit Convention

NOUS follows [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <description>

[optional body]

[optional footer]
```

### Types
- `feat` — new feature
- `fix` — bug fix
- `docs` — documentation only
- `style` — formatting (no code change)
- `refactor` — code change that neither fixes a bug nor adds a feature
- `perf` — performance improvement
- `test` — adding or correcting tests
- `build` — build system or external dependencies
- `ci` — CI configuration
- `chore` — other chores

### Scopes
Module names: `core-common`, `feature-brain`, `app`, `build-logic`, etc.

### Examples
```
feat(feature-brain): add IntentClassifier layer
fix(core-network): correct cert pinning for OpenAI
docs(adr): add ADR 0013 about memory schema
build(gradle): bump Hilt to 2.53
```

---

## Pull Request Process

1. **Branch** from `develop` (not `main`)
   ```bash
   git checkout develop
   git pull
   git checkout -b feat/my-feature
   ```

2. **Commit** with conventional commits
3. **Push** and open PR against `develop`
4. **CI must pass** — all checks green
5. **Code review** — at least one approval required
6. **Squash merge** to `develop`

### PR Template
```markdown
## What
<one-line summary>

## Why
<motivation — link to issue if applicable>

## How
<bullet list of changes>

## Testing
- [ ] Unit tests added/updated
- [ ] Integration tests added/updated (if applicable)
- [ ] Manual smoke test on device

## Screenshots (if UI change)
<before/after>

## Checklist
- [ ] License header added to new files
- [ ] No secrets committed
- [ ] No `lateinit var` introduced
- [ ] No magic numbers introduced
- [ ] Public APIs documented with KDoc
- [ ] ADR added if architectural decision made
```

---

## Testing

NOUS follows the test pyramid:

| Layer | Tool | When |
|---|---|---|
| Unit | JUnit5 + MockK + Turbine | Most tests (Brain layers, parsers, Result types) |
| Integration | Robolectric | Room DAOs, Hilt wiring |
| UI | Compose UI Test | Per-screen smoke tests |
| Snapshot | Paparazzi | Visual regression |
| E2E | UI Automator | Voice → Brain → Action → Response |
| Performance | Macrobenchmark | Cold start, brain latency |

### Coverage Gates
- `:core:*` modules: **80%+ line coverage** required
- `:feature:*` modules: **60%+ line coverage** required
- `:app`: no gate (mostly wiring)

CI fails PR if coverage drops below gate.

---

## License Header

Every Kotlin file must start with:
```kotlin
// Copyright (c) 2026 Roshan. All rights reserved.
// Proprietary license — see LICENSE file for details.
```

This is enforced by:
- Pre-commit hook
- Detekt `AbsentOrWrongFileLicense` rule
- CI check

---

## Architecture Decision Records

Major architectural decisions are documented in [docs/adr/](docs/adr/).
Add a new ADR when making a decision that:
- Affects multiple modules
- Has long-term implications
- Has viable alternatives that were considered
- Would be expensive to reverse

See [docs/adr/README.md](docs/adr/README.md) for format.

---

## Getting Help

- **Issues**: open a GitHub issue
- **Discussions**: GitHub Discussions for questions
- **Slack**: [invite link TBD]

---

> *"I am NOUS. The mind that perceives — and the mind that responds."*
