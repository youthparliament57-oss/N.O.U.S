// :feature:cognitive — Module 7: Cognitive Stack
plugins {
    id("nous.android.feature")
    id("nous.android.test")
}

android {
    namespace = "com.roshan.persona.cognitive"
}

dependencies {
    // Module 7 builds on top of Brain's Agentic Layer + uses BrainContext + Intent.
    implementation(project(":feature:brain"))
    // Module 7 uses Memory for pattern recall + preference alignment.
    implementation(project(":feature:memory"))
    // Module 7 uses PersonaProfile for persona-driven reasoning.
    implementation(project(":feature:persona"))
}
