# ADR 0002 — Hilt for Dependency Injection

Date: 2026-07-04
Status: Accepted
Decision Owner: Roshan

## Context

The original README declared "Manual `lazy` singletons (Application-scoped)" as the DI strategy. With 216+ Kotlin files across 30+ modules, this approach has several proven failure modes:

- **No compile-time verification** — circular dependencies discovered only at runtime
- **No scoping** — everything is effectively `@Singleton` or per-call; no `@FeatureScope`, `@ActivityScoped`
- **No multibinding** — feature modules cannot contribute plugins (personas, skills, brain layers) without manual registration
- **Testing pain** — no easy swap of real implementations for fakes
- **Initialization order bugs** — `lazy` chains can deadlock or fail when one singleton depends on another that isn't yet initialized

## Decision

Adopt **Dagger Hilt** (`com.google.dagger:hilt-android:2.52`) as the sole DI framework.

### Specifics
- `@HiltAndroidApp` on `NousApplication`
- Scopes: `@Singleton`, `@FeatureScope` (per-feature module), `@ViewModelScoped`, `@ActivityScoped`, `@ServiceScoped`
- Multibinding for plugin-style registries:
  - Brain layers (`@IntoSet` with `@BrainLayer` qualifier)
  - Personas (`@IntoSet` with `@PersonaDef` qualifier)
  - Skills (`@IntoMap` with `@SkillName` string key)
- Custom qualifiers for dispatchers: `@IoDispatcher`, `@DefaultDispatcher`, `@MainImmediateDispatcher`, `@LlmDispatcher`, `@CvDispatcher`, `@AudioDispatcher`
- Hilt-Work integration for `WorkManager`
- Hilt-Compose integration via `hiltViewModel()`
- Hilt-Navigation for type-safe navigation

## Consequences

**Positive:**
- Compile-time dependency graph verification — catches circular deps before runtime
- Standard scopes — clear lifecycle semantics
- Multibinding enables true plugin architecture for brain layers, personas, skills
- First-class testing support (`@HiltAndroidTest`, `@UninstallModules`, `@TestInstallIn`)
- Google-backed — long-term support guarantee

**Negative:**
- KSP/KAPT annotation processor adds ~3-5s to clean builds
- Steeper learning curve for new contributors
- Hilt's component hierarchy is fixed — some custom scopes not possible

**Mitigation:**
- KSP (not KAPT) for faster processing
- Document Hilt patterns in CONTRIBUTING.md
- Use `@FeatureScope` for feature-level isolation

## Alternatives Considered

- **Koin** (runtime DI): rejected — no compile-time safety, slower startup
- **Anvil**: considered — adds Kotlin compiler plugin complexity, less ecosystem
- **kotlin-inject**: considered — too low-level, no Hilt-Work/Compose integration
- **Manual `lazy`**: rejected — proven failure at this scale
