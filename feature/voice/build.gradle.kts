// :feature:voice — Module 5: Voice & NLP Stack
plugins {
    id("nous.android.feature")
    id("nous.android.test")
}

android {
    namespace = "com.roshan.persona.voice"
}

dependencies {
    // Voice module depends on Brain for context types (UserActivity, AmbientSnapshot,
    // MemoryInterface) used by proactive engine, emotional TTS, and conversation engine.
    implementation(project(":feature:brain"))
    // Voice module also uses types from :core:database (for persistence, if needed).
    // Native whisper.cpp lib comes from :native:whisper at the app level (we don't
    // depend on it directly — the .so is bundled via app's dependency on :native:whisper).
    // The Kotlin-side WhisperJni lives in this module; the .so is loaded via
    // System.loadLibrary("jarvis_whisper") at runtime.

    // ─── EncryptedSharedPreferences (for EncryptedVoicePrintStore) ────────
    // Strategy §5: "Voice prints stored encrypted (SQLCipher)".
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
}
