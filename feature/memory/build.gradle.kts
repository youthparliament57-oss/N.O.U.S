// :feature:memory
plugins {
    id("nous.android.feature")
}

android {
    namespace = "com.roshan.persona.memory"
}

dependencies {
    // Memory module depends on Brain's MemoryInterface contract
    implementation(project(":feature:brain"))
    // And on the encrypted Room database (DAOs + entities)
    implementation(project(":core:database"))
}
