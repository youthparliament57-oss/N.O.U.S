// :feature:security — Module 12: Security Stack (6 Pillars + 2 Defense Layers)
//
// Real Android security APIs only — no stubs, no fakes, no placeholders.
//   • AndroidKeystore (hardware-backed AES-256-GCM, StrongBox/TEE auto-select)
//   • EncryptedSharedPreferences (double-layer encrypted storage)
//   • BiometricPrompt (biometric MFA)
//   • Google Play Integrity API (zero-trust attestation)
//   • Credential Manager API (FIDO2/WebAuthn passkeys)
//   • Argon2 (memory-hard PIN hashing)
//   • SQLCipher (Room database encryption migration)
//   • BouncyCastle (post-quantum ML-KEM hybrid encryption)
plugins {
    id("nous.android.feature")
    id("nous.android.test")
    id("com.google.devtools.ksp")
    id("androidx.room")
}

android {
    namespace = "com.roshan.persona.security"
    buildFeatures { buildConfig = true }
}

room {
    schemaDirectory("$projectDir/schemas")
}

ksp {
    arg("room.incremental", "true")
    arg("room.generateKotlin", "true")
}

dependencies {
    // ─── Project deps ────────────────────────────────────────────────────────
    // Brain exposes the interfaces we override: PermissionChecker, CredentialVaultInterface,
    // AuditLogPersistence.
    implementation(project(":feature:brain"))
    // Core database exposes NousDatabase + Migrations (for SqlCipherMigrationManager).
    implementation(project(":core:database"))
    // Datastore exposes NousPreferences (for app lock settings).
    implementation(project(":core:datastore"))

    // ─── AndroidX Security (EncryptedSharedPreferences + MasterKey) ─────────
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // ─── Biometric (BiometricPrompt) ────────────────────────────────────────
    implementation("androidx.biometric:biometric:1.2.0-alpha05")

    // ─── Google Play Integrity API (zero-trust attestation) ─────────────────
    implementation(libs.play.integrity)

    // ─── Credential Manager API (FIDO2/WebAuthn passkeys) ───────────────────
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // ─── Argon2 (memory-hard PIN hashing) ───────────────────────────────────
    implementation("de.mkammerer:argon2-jvm:2.11")

    // ─── BouncyCastle (post-quantum ML-KEM primitives) ──────────────────────
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")

    // ─── SQLCipher (Room database encryption — already pulled in via :core:database) ─
    implementation("net.zetetic:android-database-sqlcipher:4.5.4")
    implementation("androidx.sqlite:sqlite-ktx:2.5.0")
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")

    // ─── WorkManager (10-min periodic SupplyChainScanWorker) ─────────────────
    implementation(libs.androidx.work.runtime)

    // ─── Coroutines (StateFlow for AppLock) — provided by :core:common ──────

    // ─── Tests ──────────────────────────────────────────────────────────────
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("com.google.truth:truth:1.4.4")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("app.cash.turbine:turbine:1.2.0")
    testImplementation("org.robolectric:robolectric:4.13")
}
