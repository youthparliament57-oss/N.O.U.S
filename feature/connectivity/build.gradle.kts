// :feature:connectivity — Module 11: Connectivity Stack
plugins {
    id("nous.android.feature")
    id("nous.android.test")
    id("com.google.devtools.ksp")
    id("androidx.room")
}

android {
    namespace = "com.roshan.persona.connectivity"
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
    // Module 11 implements Brain's Call/SmsSkill stubs + uses Intent.Call/SendSms.
    implementation(project(":feature:brain"))
    // Module 11 uses Memory for contact relationship graph + episodic memory persistence.
    implementation(project(":feature:memory"))
    // Module 11 uses VoicePersona for persona-styled TTS messages.
    implementation(project(":feature:voice"))
    // Module 11 reads calendar events (CalendarContract) for isInMeeting context (Strategy §11.4).
    implementation(project(":feature:productivity"))
    // Module 11 persists spam DB + recording policies + contact FTS mirror in its own Room DB.
    implementation(project(":core:database"))

    // OkHttp for WebSearchTool + FetchUrlTool (strategy §11 + Table 3).
    implementation(libs.okhttp)

    // Room — for ConnectivityDatabase (SpamNumberEntity + RecordingPolicy + ContactFts).
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")

    // WorkManager — for RecordingExpiryWorker (daily 30-day purge) + ContactFtsSyncWorker.
    implementation(libs.androidx.work.runtime)

    // Google Play Services Location — bundles ActivityRecognitionClient for AutoReplyEngine
    // driving detection (Strategy §11.4 SmartReplyContext.isDriving).
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // ─── Tests ──────────────────────────────────────────────────────────────
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("com.google.truth:truth:1.4.4")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("app.cash.turbine:turbine:1.2.0")
    testImplementation("androidx.room:room-testing:2.7.1")
    testImplementation(libs.androidx.work.testing)
}
