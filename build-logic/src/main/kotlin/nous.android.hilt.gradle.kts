// NOUS — Hilt convention plugin
// Plugin ID: `nous.android.hilt`

plugins {
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

dependencies {
    add("implementation", "com.google.dagger:hilt-android:2.52")
    add("ksp", "com.google.dagger:hilt-android-compiler:2.52")
    add("implementation", "androidx.hilt:hilt-work:1.2.0")
    add("ksp", "androidx.hilt:hilt-compiler:1.2.0")
    add("implementation", "androidx.hilt:hilt-navigation-compose:1.2.0")
    add("androidTestImplementation", "com.google.dagger:hilt-android-testing:2.52")
    add("kspAndroidTest", "com.google.dagger:hilt-android-compiler:2.52")
    add("kspTest", "com.google.dagger:hilt-android-compiler:2.52")
}
