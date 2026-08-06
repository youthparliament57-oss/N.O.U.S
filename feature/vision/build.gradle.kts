// :feature:vision — Module 6: Vision Stack (NOUS Eyes)
plugins {
    id("nous.android.feature")
    id("nous.android.test")
}

android {
    namespace = "com.roshan.persona.vision"
}

dependencies {
    // Module 6 REPLACES Brain's FakeEmbedder + null NER with real Omni-SLM.
    implementation(project(":feature:brain"))
    // Module 6 pipes vision results to Memory (Graph RAG, episodic memory).
    implementation(project(":feature:memory"))
    // Module 6 uses Module 10 DeviceCapabilityDetector for SoC/RAM tier detection.
    implementation(project(":feature:system"))

    // ─── ML Kit — REAL vision APIs (Strategy §6.2-6.6) ────────────────────
    // Text Recognition (OCR) — Latin + Devanagari + 60 scripts
    implementation(libs.mlkit.text.recognition)
    // Face Detection — face contours, landmarks, classification
    implementation(libs.mlkit.face.detection)
    // Object Detection — SSD MobileNetV3
    implementation(libs.mlkit.object.detection)
    // Barcode Scanning — QR, UPI, all formats
    implementation(libs.mlkit.barcode.scanning)
    // Image Labeling — general scene understanding
    implementation(libs.mlkit.image.labeling)

    // ─── CameraX — for real camera frame capture ──────────────────────────
    implementation(libs.bundles.camerax)

    // ─── OkHttp — for vision pack downloads ───────────────────────────────
    implementation(libs.okhttp)
}
