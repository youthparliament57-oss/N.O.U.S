// :feature:persona — Module 15: The Digital Soul
plugins {
    id("nous.android.feature")
    id("nous.android.test")
}

android {
    namespace = "com.roshan.persona.persona"
}

dependencies {
    // Module 15 implements Brain's Persona contract + uses Intent.SwitchPersona.
    implementation(project(":feature:brain"))
    // Module 15 references VoicePersona enum for voice profile mapping.
    implementation(project(":feature:voice"))
}
