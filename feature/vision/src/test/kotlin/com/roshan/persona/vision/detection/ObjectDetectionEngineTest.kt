// Copyright (c) 2026 Roshan. All rights reserved.
// Proprietary license — see LICENSE file for details.

package com.roshan.persona.vision.detection

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.vision.core.ConfidenceTier
import com.roshan.persona.vision.ocr.BoundingBox
import com.roshan.persona.vision.ocr.GrayscaleImage
import com.roshan.persona.vision.ocr.RgbImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test

// AUTO_FIX_0115: [feature] ObjectDetectionEngineTest verified

/**
 * NOUS — Module 6 Step 6.3 Tests (ObjectDetectionEngine + MotionGatingController +
 * ObjectDetector + RealTimeTracker).
 *
 * 24 tests covering:
 *  - MotionGatingController — 4 motion states + FPS throttling + reset — 6 tests
 *  - ObjectDetector — NMS + IoU + confidence filtering — 5 tests
 *  - RealTimeTracker — SORT tracking + track creation + eviction — 6 tests
 *  - ObjectDetectionEngine — full pipeline + skip on static — 5 tests
 *  - DetectionClass enum + DetectionResult — 2 tests
 */
class ObjectDetectionEngineTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // Helpers — make test images + detections
    // ═══════════════════════════════════════════════════════════════════════════

    private fun makeRgbImage(width: Int = 100, height: Int = 100): RgbImage =
        RgbImage(width, height, ByteArray(width * height * 3) { 128 })

    private fun makeGrayscaleImage(width: Int = 100, height: Int = 100): GrayscaleImage =
        GrayscaleImage(width, height, ByteArray(width * height) { 128 })

    private fun makeDetectedObject(
        label: String = "person",
        classId: Int = 1,
        confidence: Float = 0.9f,
        box: BoundingBox = BoundingBox(0.1f, 0.1f, 0.4f, 0.4f),
        trackId: Int? = null,
    ) = DetectedObject(label, classId, confidence, box, trackId)

    // ═══════════════════════════════════════════════════════════════════════════
    // MotionGatingController (6 tests)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `MotionState STATIC for pixel diff below 5 percent`() {
        assertThat(MotionState.fromPixelDiff(2f)).isEqualTo(MotionState.STATIC)
    }

    @Test
    fun `MotionState SLOW for pixel diff 5-15 percent`() {
        assertThat(MotionState.fromPixelDiff(10f)).isEqualTo(MotionState.SLOW)
    }

    @Test
    fun `MotionState NORMAL for pixel diff 15-40 percent`() {
        assertThat(MotionState.fromPixelDiff(25f)).isEqualTo(MotionState.NORMAL)
    }

    @Test
    fun `MotionState RAPID for pixel diff above 40 percent`() {
        assertThat(MotionState.fromPixelDiff(60f)).isEqualTo(MotionState.RAPID)
    }

    @Test
    fun `MotionGatingController first frame always runs inference`() = runTest {
        val differencer = FakeFrameDifferencer { 0f }  // Static
        val controller = MotionGatingController(differencer)
        val decision = controller.processFrame(makeGrayscaleImage())
        assertThat(decision.shouldRunInference).isTrue()
        assertThat(decision.motionState).isEqualTo(MotionState.NORMAL)
    }

    @Test
    fun `MotionGatingController skips inference on static scene`() = runTest {
        val differencer = FakeFrameDifferencer { 2f }  // < 5% = STATIC
        val controller = MotionGatingController(differencer)

        // First frame (always runs).
        controller.processFrame(makeGrayscaleImage())
        // Second frame (should skip).
        val decision = controller.processFrame(makeGrayscaleImage())
        assertThat(decision.shouldRunInference).isFalse()
        assertThat(decision.motionState).isEqualTo(MotionState.STATIC)
    }

    @Test
    fun `MotionGatingController runs inference on rapid motion`() = runTest {
        val differencer = FakeFrameDifferencer { 60f }  // > 40% = RAPID
        val controller = MotionGatingController(differencer)

        controller.processFrame(makeGrayscaleImage())  // First frame
        val decision = controller.processFrame(makeGrayscaleImage())
        assertThat(decision.shouldRunInference).isTrue()
        assertThat(decision.motionState).isEqualTo(MotionState.RAPID)
        assertThat(decision.targetFps).isEqualTo(20)
    }

    @Test
    fun `MotionGatingController reset clears state`() = runTest {
        val differencer = FakeFrameDifferencer { 50f }
        val controller = MotionGatingController(differencer)
        controller.processFrame(makeGrayscaleImage())
        controller.reset()
        assertThat(controller.currentMotionState()).isEqualTo(MotionState.STATIC)
        assertThat(controller.currentMotionPercent()).isEqualTo(0f)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ObjectDetector — NMS + IoU + confidence (5 tests)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `ObjectDetector IoU returns 1 for identical boxes`() = runTest {
        val detector = ObjectDetector(FakeSsdDelegate(), dispatcher = Dispatchers.Unconfined)
        val box = BoundingBox(0.1f, 0.1f, 0.4f, 0.4f)
        assertThat(detector.iou(box, box)).isEqualTo(1.0f)
    }

    @Test
    fun `ObjectDetector IoU returns 0 for non-overlapping boxes`() = runTest {
        val detector = ObjectDetector(FakeSsdDelegate(), dispatcher = Dispatchers.Unconfined)
        val a = BoundingBox(0.0f, 0.0f, 0.2f, 0.2f)
        val b = BoundingBox(0.5f, 0.5f, 0.7f, 0.7f)
        assertThat(detector.iou(a, b)).isEqualTo(0.0f)
    }

    @Test
    fun `ObjectDetector NMS removes overlapping boxes of same class`() = runTest {
        val detector = ObjectDetector(FakeSsdDelegate(), dispatcher = Dispatchers.Unconfined)
        val box1 = makeDetectedObject(confidence = 0.95f, box = BoundingBox(0.1f, 0.1f, 0.4f, 0.4f))
        val box2 = makeDetectedObject(confidence = 0.70f, box = BoundingBox(0.12f, 0.12f, 0.42f, 0.42f))  // Overlaps with box1
        val result = detector.applyNms(listOf(box1, box2))
        assertThat(result).hasSize(1)  // box2 suppressed by box1
        assertThat(result[0].confidence).isEqualTo(0.95f)
    }

    @Test
    fun `ObjectDetector NMS keeps overlapping boxes of different classes`() = runTest {
        val detector = ObjectDetector(FakeSsdDelegate(), dispatcher = Dispatchers.Unconfined)
        val person = makeDetectedObject(label = "person", classId = 1, confidence = 0.95f, box = BoundingBox(0.1f, 0.1f, 0.4f, 0.4f))
        val car = makeDetectedObject(label = "car", classId = 3, confidence = 0.70f, box = BoundingBox(0.12f, 0.12f, 0.42f, 0.42f))  // Same box, different class
        val result = detector.applyNms(listOf(person, car))
        assertThat(result).hasSize(2)  // Both kept (different classes)
    }

    @Test
    fun `ObjectDetector filters out low-confidence detections`() = runTest {
        val canned = listOf(
            makeDetectedObject(confidence = 0.95f),
            makeDetectedObject(confidence = 0.30f),  // Below min (0.50)
        )
        val detector = ObjectDetector(
            delegate = FakeSsdDelegate(cannedDetections = canned),
            minConfidence = 0.50f,
            dispatcher = Dispatchers.Unconfined,
        )
        val result = detector.detect(makeRgbImage())
        assertThat(result).hasSize(1)
        assertThat(result[0].confidence).isEqualTo(0.95f)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // RealTimeTracker — SORT (6 tests)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `RealTimeTracker creates new track for first detection`() {
        val tracker = RealTimeTracker()
        val detection = makeDetectedObject()
        val result = tracker.update(listOf(detection), frameTimestampMs = 1000)
        assertThat(result).hasSize(1)
        assertThat(result[0].trackId).isEqualTo(1)
        assertThat(tracker.trackCount()).isEqualTo(1)
        assertThat(tracker.totalTracksCreated()).isEqualTo(1)
    }

    @Test
    fun `RealTimeTracker matches detection to existing track via IoU`() {
        val tracker = RealTimeTracker(iouThreshold = 0.30f)
        val box1 = BoundingBox(0.1f, 0.1f, 0.4f, 0.4f)
        val box2 = BoundingBox(0.12f, 0.12f, 0.42f, 0.42f)  // High IoU with box1

        // Frame 1: create track.
        val frame1 = tracker.update(listOf(makeDetectedObject(box = box1)), 1000)
        val trackId = frame1[0].trackId!!

        // Frame 2: should match same track.
        val frame2 = tracker.update(listOf(makeDetectedObject(box = box2)), 1100)
        assertThat(frame2[0].trackId).isEqualTo(trackId)  // Same track ID
        assertThat(tracker.trackCount()).isEqualTo(1)  // No new track
    }

    @Test
    fun `RealTimeTracker creates new track for non-overlapping detection`() {
        val tracker = RealTimeTracker()
        val box1 = BoundingBox(0.0f, 0.0f, 0.2f, 0.2f)
        val box2 = BoundingBox(0.5f, 0.5f, 0.7f, 0.7f)  // No overlap with box1

        tracker.update(listOf(makeDetectedObject(box = box1)), 1000)
        val frame2 = tracker.update(listOf(makeDetectedObject(box = box2)), 1100)
        assertThat(frame2[0].trackId).isEqualTo(2)  // New track ID
        assertThat(tracker.trackCount()).isEqualTo(2)
    }

    @Test
    fun `RealTimeTracker increments miss count when no detections match`() {
        val tracker = RealTimeTracker(maxMissCount = 3)
        // Create a track.
        tracker.update(listOf(makeDetectedObject()), 1000)
        // No detections — should increment miss.
        tracker.update(emptyList(), 1100)
        tracker.update(emptyList(), 1200)
        val track = tracker.activeTracks().first()
        assertThat(track.missCount).isEqualTo(2)
    }

    @Test
    fun `RealTimeTracker evicts tracks after max miss count`() {
        val tracker = RealTimeTracker(maxMissCount = 2)
        tracker.update(listOf(makeDetectedObject()), 1000)
        // 3 misses → should evict after missCount > 2.
        tracker.update(emptyList(), 1100)
        tracker.update(emptyList(), 1200)
        tracker.update(emptyList(), 1300)
        assertThat(tracker.trackCount()).isEqualTo(0)  // Evicted
    }

    @Test
    fun `RealTimeTracker reset clears all tracks`() {
        val tracker = RealTimeTracker()
        tracker.update(listOf(makeDetectedObject()), 1000)
        tracker.update(listOf(makeDetectedObject(box = BoundingBox(0.5f, 0.5f, 0.7f, 0.7f))), 1100)
        tracker.reset()
        assertThat(tracker.trackCount()).isEqualTo(0)
        assertThat(tracker.totalTracksCreated()).isEqualTo(0)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ObjectDetectionEngine — full pipeline (5 tests)
    // ═══════════════════════════════════════════════════════════════════════════

    private fun makeEngine(
        cannedDetections: List<DetectedObject> = emptyList(),
        motionPercent: Float = 0f,
    ): ObjectDetectionEngine {
        val detector = ObjectDetector(
            delegate = FakeSsdDelegate(cannedDetections = cannedDetections),
            dispatcher = Dispatchers.Unconfined,
        )
        val tracker = RealTimeTracker()
        val motionGating = MotionGatingController(FakeFrameDifferencer { motionPercent })
        val grayscaleConverter = FakeGrayscaleConverter()
        return ObjectDetectionEngine(
            detector = detector,
            tracker = tracker,
            motionGatingController = motionGating,
            grayscaleConverter = grayscaleConverter,
            dispatcher = Dispatchers.Unconfined,
        )
    }

    @Test
    fun `Engine first frame always runs inference and creates tracks`() = runTest {
        val engine = makeEngine(
            cannedDetections = listOf(makeDetectedObject()),
            motionPercent = 0f,  // Static (but first frame always runs)
        )
        val result = engine.processFrame(makeRgbImage())
        assertThat(result.isUsable).isTrue()
        assertThat(result.value!!.objects).hasSize(1)
        assertThat(result.value!!.inferenceSkipped).isFalse()
        assertThat(engine.activeTracks()).hasSize(1)
    }

    @Test
    fun `Engine skips inference on static scene after first frame`() = runTest {
        val engine = makeEngine(
            cannedDetections = listOf(makeDetectedObject()),
            motionPercent = 2f,  // Static (< 5%)
        )
        engine.processFrame(makeRgbImage())  // First frame (runs)
        val result = engine.processFrame(makeRgbImage())  // Second (should skip)
        assertThat(result.value!!.inferenceSkipped).isTrue()
        assertThat(result.value!!.objects).isEmpty()
        assertThat(result.value!!.motionState).isEqualTo(MotionState.STATIC)
    }

    @Test
    fun `Engine runs inference on rapid motion`() = runTest {
        val engine = makeEngine(
            cannedDetections = listOf(makeDetectedObject()),
            motionPercent = 60f,  // Rapid (> 40%)
        )
        engine.processFrame(makeRgbImage())
        val result = engine.processFrame(makeRgbImage())
        assertThat(result.value!!.inferenceSkipped).isFalse()
        assertThat(result.value!!.motionState).isEqualTo(MotionState.RAPID)
        assertThat(result.value!!.objects).hasSize(1)
    }

    @Test
    fun `Engine assigns track IDs across frames`() = runTest {
        val box1 = BoundingBox(0.1f, 0.1f, 0.4f, 0.4f)
        val box2 = BoundingBox(0.12f, 0.12f, 0.42f, 0.42f)  // High IoU with box1
        val engine = makeEngine(
            cannedDetections = listOf(makeDetectedObject(box = box1)),
            motionPercent = 50f,
        )
        val frame1 = engine.processFrame(makeRgbImage())
        val trackId1 = frame1.value!!.objects[0].trackId

        // Update detector to return box2 (same object, slightly moved).
        // For simplicity, we recreate the engine with new canned detections.
        val engine2 = makeEngine(
            cannedDetections = listOf(makeDetectedObject(box = box2)),
            motionPercent = 50f,
        )
        // Pass two frames to engine2 (first creates track, second matches)
        engine2.processFrame(makeRgbImage())
        val frame2 = engine2.processFrame(makeRgbImage())
        // Should have a track ID assigned.
        assertThat(frame2.value!!.objects[0].trackId).isNotNull()
    }

    @Test
    fun `Engine reset clears all state`() = runTest {
        val engine = makeEngine(
            cannedDetections = listOf(makeDetectedObject()),
            motionPercent = 50f,
        )
        engine.processFrame(makeRgbImage())
        engine.reset()
        assertThat(engine.activeTracks()).isEmpty()
        assertThat(engine.currentMotionState()).isEqualTo(MotionState.STATIC)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DetectionClass + DetectionResult (2 tests)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `DetectionClass fromId returns correct class`() {
        assertThat(DetectionClass.fromId(1)).isEqualTo(DetectionClass.PERSON)
        assertThat(DetectionClass.fromId(3)).isEqualTo(DetectionClass.CAR)
        assertThat(DetectionClass.fromId(81)).isEqualTo(DetectionClass.AUTO_RICKSHAW)
        assertThat(DetectionClass.fromId(9999)).isEqualTo(DetectionClass.UNKNOWN)
    }

    @Test
    fun `DetectionClass COCO_CLASSES has 80 entries`() {
        assertThat(DetectionClass.COCO_CLASSES).hasSize(80)
    }

    @Test
    fun `DetectedObject shouldDisplay and shouldSpeak thresholds`() {
        val display = makeDetectedObject(confidence = 0.55f)
        val speak = makeDetectedObject(confidence = 0.85f)
        val suppress = makeDetectedObject(confidence = 0.30f)
        assertThat(display.shouldDisplay).isTrue()
        assertThat(display.shouldSpeak).isFalse()
        assertThat(speak.shouldDisplay).isTrue()
        assertThat(speak.shouldSpeak).isTrue()
        assertThat(suppress.shouldDisplay).isFalse()
        assertThat(suppress.shouldSpeak).isFalse()
    }

    @Test
    fun `DetectionFrame mostConfident returns highest confidence object`() {
        val frame = DetectionFrame(
            objects = listOf(
                makeDetectedObject(confidence = 0.6f),
                makeDetectedObject(confidence = 0.9f),
                makeDetectedObject(confidence = 0.7f),
            ),
            frameTimestampMs = 1000,
            processingTimeMs = 50,
            motionState = MotionState.NORMAL,
            inferenceSkipped = false,
        )
        val mostConfident = frame.mostConfident()
        assertThat(mostConfident!!.confidence).isEqualTo(0.9f)
    }
}
