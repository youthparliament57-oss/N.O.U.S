// :feature:productivity — Module 14: Productivity Stack (NOUS Planner)
plugins {
    id("nous.android.feature")
    id("nous.android.test")
    id("com.google.devtools.ksp")
    id("androidx.room")
}

android {
    namespace = "com.roshan.persona.productivity"
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
    // Module 14 implements Brain's stub skills (SetAlarm/SetTimer/SetReminder/CreateNote/CreateCalendarEvent).
    implementation(project(":feature:brain"))
    // Module 14 uses Module 12's KeystoreManager + SqlCipherMigrationManager for encrypted storage.
    // (Module 12 not yet built; NoteEncryptionHelper + ReminderEncryptionHelper use REAL AndroidKeystore directly.)
    implementation(project(":feature:security"))
    // Module 14 uses :core:database for Room DAOs.
    implementation(project(":core:database"))
    // Module 14 uses :core:common for Result type.
    implementation(project(":core:common"))

    // WorkManager — for ReminderEngine (Step 14.2: reminders use WorkManager ONLY, no AlarmManager).
    implementation(libs.androidx.work.runtime)
    // WorkManager testing — for ReminderEngine unit tests.
    testImplementation(libs.androidx.work.testing)

    // Room — for NoteEngine (Step 14.3: encrypted note database + FTS5 full-text search).
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")
    // SQLCipher — for encrypted note body storage (per strategy v1.2 §"Polish 6").
    implementation("net.zetetic:android-database-sqlcipher:4.5.4")
    implementation("androidx.sqlite:sqlite-ktx:2.5.0")
    // Room testing — for NoteDao in-memory DB tests.
    testImplementation("androidx.room:room-testing:2.7.1")
}
