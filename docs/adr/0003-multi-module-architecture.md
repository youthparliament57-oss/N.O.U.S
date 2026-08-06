# ADR 0003 — Multi-Module Architecture (30+ Gradle Modules)

Date: 2026-07-04
Status: Accepted
Decision Owner: Roshan

## Context

The original README described a monolithic structure: 216 Kotlin files in a single `app` module. At this scale:

- **Incremental builds become slow** — any change recompiles a large portion
- **Dependency boundaries leak** — feature modules freely import from each other, creating tangled webs
- **Dynamic Feature Delivery impossible** — drone/AR/hacker modules cannot be split out for on-demand install
- **Parallel CI builds impossible** — single module = single build task
- **Code ownership unclear** — no clear "this is voice module's territory"

## Decision

Adopt **strict multi-module architecture** with 34 Gradle modules organized into 5 layers:

```
:core:*        (11 modules) — foundation, no feature logic
:feature:*     (15 modules) — vertical features
:dynamicfeature:* (4 modules) — installable on-demand via Play
:native:*      (3 modules) — NDK / JNI bridges
:app           (1 module) — application entry point
```

### Dependency Rules (Enforced via Detekt custom rule)
1. **Core never depends on Feature** — core is foundation
2. **Feature never depends on another Feature directly** — features communicate only via:
   - Interfaces declared in `:core:common`
   - The Brain bus (event channel) in `:feature:brain`
   - Hilt multibinding (plugin-style)
3. **UI never depends on Data layer** — only via ViewModel
4. **Native modules wrapped by Feature** — never used directly by UI or other features
5. **Dynamic features can depend on :core:* but not :feature:*** (Play policy)

### Convention Plugins
Each layer has a convention plugin that applies shared config:
- `nous.android.application` — for `:app`
- `nous.android.feature` — for `:feature:*` (includes Compose + Hilt + Test)
- `nous.android.library` — for `:core:*`, `:native:*`
- `nous.android.compose` — opt-in for Compose-using modules
- `nous.android.hilt` — opt-in for Hilt-using modules

## Consequences

**Positive:**
- Fast incremental builds — change in one module only rebuilds dependents
- Clear ownership — every file belongs to exactly one module
- DFM support — drone/AR/hacker/voiceclone installed only on demand
- Parallel CI — modules build in parallel via Gradle
- Testability — feature modules can be unit-tested in isolation
- Compile-time boundary enforcement — Gradle's `implementation` vs `api` controls visibility

**Negative:**
- More boilerplate (34 `build.gradle.kts` files)
- Initial setup effort higher (mitigated by convention plugins)
- Module boundaries sometimes require awkward interface hops (mitigated by Brain bus)

**Mitigation:**
- Convention plugins minimize per-module boilerplate (~5 lines each)
- Module README files document public API
- Binary compatibility validator locks public API surface

## Alternatives Considered

- **15 modules (grouped features)**: rejected — too coarse, defeats parallel build benefits
- **100+ modules (one per class)**: rejected — excessive boilerplate, build config overhead
- **Monolith with package conventions**: rejected — no compile-time enforcement
