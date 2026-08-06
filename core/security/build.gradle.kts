// :core:security
plugins {
    id("nous.android.library")
    id("nous.android.hilt")
    id("nous.android.test")
}

android {
    namespace = "com.roshan.persona.security"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:telemetry"))

    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.google.crypto.tink:tink-android:1.15.0")
    implementation("de.mkammerer:argon2-jvm:2.11")
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")

    implementation("com.google.android.play:integrity:1.4.0")
}
