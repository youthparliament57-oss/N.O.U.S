// :feature:agent — Module 9: Agent Stack (NOUS Hands)
plugins {
    id("nous.android.feature")
    id("nous.android.test")
}

android {
    namespace = "com.roshan.persona.agent"
}

dependencies {
    // Module 9 implements Module 10's NousAccessibilityServiceInterface.
    implementation(project(":feature:system"))
    // Module 9 implements Brain's SystemOperationExecutor (real Android wiring).
    implementation(project(":feature:brain"))
    // Module 9 uses Module 11's service interfaces for real Android impls.
    implementation(project(":feature:connectivity"))

    // ─── AndroidX Security (EncryptedSharedPreferences) ───────────────────────
    // Used by AccessibilityPermissionManager to persist the 24h cooldown
    // timestamp (P2 fix). ESP wraps SharedPreferences with AES-256-GCM at rest
    // (master key in AndroidKeystore) — much lighter than DataStore for a
    // single Long value.
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // ─── BouncyCastle (for LADB SPAKE2+ pairing) ────────────────────────
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
}
