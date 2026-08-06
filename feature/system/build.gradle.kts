// :feature:system — Module 10: System Stack (hardware execution layer)
plugins {
    id("nous.android.feature")
    id("nous.android.test")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.roshan.persona.system"
}

dependencies {
    // Module 10 implements Brain's SystemOperationExecutor interface and
    // uses Brain's SystemOperation sealed class + PermissionChecker.
    implementation(project(":feature:brain"))

    // Module 10 persists undo actions in the shared Room database (:core:database).
    // PersistentUndoStack (Step 10.2) uses UndoActionDao + UndoActionEntity.
    implementation(project(":core:database"))
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
}
