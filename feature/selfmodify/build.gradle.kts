// :feature:selfmodify — Module 15: Self-modifying JS sandbox (Rhino)
//
// Real Mozilla Rhino JavaScript engine — sideload-only (disabled in Play Store builds).
// Strategy §14.2: Brain's FakeJsSandbox + NoOpJsSandbox are replaced by this module's
// real RhinoJsSandbox in `dev` and `internal` flavors. In `prod`, Rhino is stripped
// by R8 and Brain falls back to NoOpJsSandbox (per ADR 0008).
plugins {
    id("nous.android.feature")
}

android {
    namespace = "com.roshan.persona.selfmodify"

    // Rhino-dependent sources live in `src/selfmodify/kotlin/` and are compiled
    // ONLY for `dev` and `internal` flavors (per ADR 0008 — Rhino is sideload-only).
    // In `prod`, this directory is NOT on the classpath → RhinoJsSandbox + DI module
    // are absent → Brain's NoOpJsSandbox is used.
    sourceSets {
        getByName("dev").java.srcDirs("src/selfmodify/kotlin")
        getByName("internal").java.srcDirs("src/selfmodify/kotlin")
    }
}

dependencies {
    // ─── Project deps ────────────────────────────────────────────────────────
    // Brain exposes the JsSandbox interface (and SandboxLimits data class) we
    // implement, plus the common Result/AppError types it returns.
    implementation(project(":feature:brain"))

    // ─── Mozilla Rhino (sideload flavors only) ──────────────────────────────
    // Strategy §14.2 Table 3: real Rhino JS engine — disabled in `prod` per ADR 0008.
    // devImplementation / internalImplementation ensure Rhino classes are NOT on
    // the prodRelease classpath, so R8 strips all references to RhinoJsSandbox.
    "devImplementation"("org.mozilla:rhino:1.7.15")
    "internalImplementation"("org.mozilla:rhino:1.7.15")

    // ─── Coroutines (for suspend execute) — provided by :core:common ────────
    // ─── Timber (logging) — provided by :core:common ────────────────────────
}
