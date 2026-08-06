// AUTO_FIX_0117: [feature] VisionTestFakes verified
// Copyright (c) 2026 Roshan. All rights reserved.
package com.roshan.persona.vision.fakes

// All Fake classes moved from main source to test source.
// These are test-only helpers — never used in production DI graph.
// See individual files in main source for real implementations:
// - MlKitOcrRecognizer (replaces FakeOcrRecognizer)
// - MlKitObjectDetector (replaces FakeSsdDelegate)
// - MlKitFaceDetector (replaces FakeFaceDetectorDelegate)
// - MlKitBarcodeScanner (replaces FakeBarcodeDetectorDelegate)
// - AndroidGrayscaleConverter (replaces FakeGrayscaleConverter)
// - AndroidFrameDifferencer (replaces FakeFrameDifferencer)
// - AndroidEdgeDetector (replaces FakeEdgeDetector)
// - AndroidScreenshotCapturer (replaces FakeScreenshotCapturer)
// - AccessibilityUiElementDetector (replaces FakeUiElementDetector)
// - LlmScreenSummarizer (replaces FakeScreenSummarizer)
// - AndroidBatteryProbe (replaces FakeBatteryProbe)
// - AndroidThermalProbe (replaces FakeThermalProbe)
// - AndroidAccelerometerProbe (replaces FakeAccelerometerProbe)
// - OkHttpPackDownloader (replaces FakePackDownloader)
// - Sha256PackVerifier (replaces FakePackVerifier)
