// :core:database
plugins {
    id("nous.android.library")
    id("nous.android.hilt")
    id("com.google.devtools.ksp")
    id("androidx.room")
}

android {
    namespace = "com.roshan.persona.database"
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
    implementation(project(":core:common"))

    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")
    implementation("net.zetetic:android-database-sqlcipher:4.5.4")
    implementation("androidx.sqlite:sqlite-ktx:2.5.0")

    // EncryptedSharedPreferences for SQLCipher passphrase storage
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Timber for logging
    implementation("com.jakewharton.timber:timber:5.0.1")
}
