// :core:datastore
plugins {
    id("nous.android.library")
    id("nous.android.hilt")
}

android {
    namespace = "com.roshan.persona.datastore"
}

dependencies {
    implementation(project(":core:common"))

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.datastore:datastore:1.1.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
