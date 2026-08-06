// :feature:automation — Module 8: Automation Engine
//
// Strategy §2.2 — Automation is one of four Brain input modalities
// (Voice/Text/Gesture/Automation). This module provides the engine that
// stores + schedules + fires user-defined "WHEN X THEN Y" rules.
plugins {
    id("nous.android.feature")
}

android {
    namespace = "com.roshan.persona.automation"
}

dependencies {
    // Automation skills implement Brain's BrainSkill interface + handle the
    // four automation intents (Create/List/Enable/Disable).
    implementation(project(":feature:brain"))
    // Automation actions delegate SystemAction to Brain's SystemOperationExecutor
    // — the real Android implementation lives in :feature:system.
    implementation(project(":feature:system"))

    // WorkManager — for TimeTrigger scheduling (PeriodicWorkRequest).
    implementation(libs.androidx.work.runtime)

    // Google Play Services Location — for LocationTrigger (GeofencingClient).
    // This is the canonical Android geofencing API; rolling our own via
    // LocationManager would be reinventing a well-tested wheel + would miss
    // out on Play Services' automatic geofence re-registration after reboot.
    implementation("com.google.android.gms:play-services-location:21.3.0")
}
