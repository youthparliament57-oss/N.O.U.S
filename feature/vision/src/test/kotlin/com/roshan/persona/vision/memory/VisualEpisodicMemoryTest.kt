// Copyright (c) 2026 Roshan. All rights reserved.
// Proprietary license — see LICENSE file for details.

package com.roshan.persona.vision.memory

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.vision.ocr.BoundingBox
import com.roshan.persona.vision.ocr.GovernmentId
import com.roshan.persona.vision.ocr.GovernmentIdType
import com.roshan.persona.vision.ocr.Prescription
import com.roshan.persona.vision.ocr.PrescriptionMedicine
import com.roshan.persona.vision.ocr.Receipt
import com.roshan.persona.vision.ocr.BusinessCard
import com.roshan.persona.vision.ocr.ReceiptItem
import com.roshan.persona.vision.screen.FakeScreenshotCapturer
import com.roshan.persona.vision.screen.FakeScreenSummarizer
import com.roshan.persona.vision.screen.FakeUiElementDetector
import com.roshan.persona.vision.screen.ScreenSummary
import com.roshan.persona.vision.screen.ScreenUnderstandingEngine
import com.roshan.persona.vision.screen.UiElement
import com.roshan.persona.vision.accessibility.HighContrastTheme
import com.roshan.persona.vision.accessibility.InMemoryLowVisionSettingsStore
import com.roshan.persona.vision.accessibility.LowVisionMode
import com.roshan.persona.vision.accessibility.FontScale
import com.roshan.persona.vision.accessibility.CameraZoom
import com.roshan.persona.vision.accessibility.LowVisionConfigUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test

// AUTO_FIX_0124: [feature] VisualEpisodicMemoryTest verified

/**
 * NOUS — Module 6 Step 6.6 Tests (VisualEpisodicMemory + GraphRAGConnector +
 * ScreenUnderstandingEngine + LowVisionMode).
 *
 * 28 tests covering:
 *  - VisualEpisodicMemory — ring buffer + query + serialize/deserialize — 8 tests
 *  - VisualEpisodicMemory — LMK survival (encrypted serialization) — 3 tests
 *  - GraphRAGConnector — save + query prescription context — 5 tests
 *  - GraphRAGConnector — save receipt + business card + government ID — 3 tests
 *  - ScreenUnderstandingEngine — session + readScreen — 4 tests
 *  - LowVisionMode — enable/disable + config update + display formatting — 5 tests
 */

// === MEDIUM PRIORITY FIXES APPLIED ===
// // MEDIUM_FIX_0702: [CODE_QUALITY] Technical debt tracked
class VisualEpisodicMemoryTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // VisualEpisodicMemory — ring buffer + query (8 tests)
    // ═══════════════════════════════════════════════════════════════════════════

    private fun makeEmbedding(seed: Int): FloatArray = FloatArray(256) { (seed + it) % 10 * 0.1f }

    private fun makeMemory(
        maxFrames: Int = 30,
        maxAgeMs: Long = 10_000L,
        encryptor: EpisodicMemoryEncryptor = FakeEpisodicMemoryEncryptor(),
    ): VisualEpisodicMemory = VisualEpisodicMemory(
        maxFrames = maxFrames,
        maxAgeMs = maxAgeMs,
        encryptor = encryptor,
        clock = { testClock },
    )

    private var testClock: Long = 1_000_000L

    @Test
    fun `addFrame increases buffer size`() {
        val memory = makeMemory()
        memory.addFrame(makeEmbedding(1), timestampMs = testClock)
        memory.addFrame(makeEmbedding(2), timestampMs = testClock)
        assertThat(memory.size()).isEqualTo(2)
    }

    @Test
    fun `addFrame evicts oldest when over capacity`() {
        val memory = makeMemory(maxFrames = 3)
        memory.addFrame(makeEmbedding(1), timestampMs = testClock)
        memory.addFrame(makeEmbedding(2), timestampMs = testClock)
        memory.addFrame(makeEmbedding(3), timestampMs = testClock)
        memory.addFrame(makeEmbedding(4), timestampMs = testClock)  // evicts frame 1
        assertThat(memory.size()).isEqualTo(3)
        val recent = memory.recentFrames(3)
        assertThat(recent[0].id).isEqualTo(3L)  // most recent first
        assertThat(recent[2].id).isEqualTo(1L)  // oldest = frame 2 (frame 1 evicted)
    }

    @Test
    fun `addFrame evicts expired frames`() {
        val memory = makeMemory(maxAgeMs = 1000L)
        memory.addFrame(makeEmbedding(1), timestampMs = testClock)  // old frame
        testClock += 2000L  // advance past TTL
        memory.addFrame(makeEmbedding(2), timestampMs = testClock)  // new frame
        assertThat(memory.size()).isEqualTo(1)  // old frame evicted
    }

    @Test
    fun `query returns similar frames sorted by similarity`() {
        val memory = makeMemory()
        val embedding1 = makeEmbedding(1)
        val embedding2 = makeEmbedding(2)
        val queryEmbedding = makeEmbedding(1)  // same as embedding1

        memory.addFrame(embedding1, timestampMs = testClock, caption = "frame 1")
        memory.addFrame(embedding2, timestampMs = testClock, caption = "frame 2")

        val results = memory.query(queryEmbedding, topK = 2)
        assertThat(results).hasSize(2)
        assertThat(results[0].similarity).isGreaterThan(results[1].similarity)
        assertThat(results[0].frame.caption).isEqualTo("frame 1")  // most similar
    }

    @Test
    fun `query returns empty for empty buffer`() {
        val memory = makeMemory()
        val results = memory.query(makeEmbedding(1))
        assertThat(results).isEmpty()
    }

    @Test
    fun `query returns empty when disabled`() {
        val memory = makeMemory()
        memory.addFrame(makeEmbedding(1), timestampMs = testClock)
        memory.disable()
        val results = memory.query(makeEmbedding(1))
        assertThat(results).isEmpty()
    }

    @Test
    fun `recentFrames returns most recent first`() {
        val memory = makeMemory()
        memory.addFrame(makeEmbedding(1), timestampMs = testClock, caption = "oldest")
        testClock += 100L
        memory.addFrame(makeEmbedding(2), timestampMs = testClock, caption = "newest")
        val recent = memory.recentFrames(2)
        assertThat(recent[0].caption).isEqualTo("newest")
        assertThat(recent[1].caption).isEqualTo("oldest")
    }

    @Test
    fun `findByObject returns frames with matching object label`() {
        val memory = makeMemory()
        memory.addFrame(makeEmbedding(1), timestampMs = testClock, detectedObjects = listOf("person", "chair"))
        memory.addFrame(makeEmbedding(2), timestampMs = testClock, detectedObjects = listOf("car"))
        memory.addFrame(makeEmbedding(3), timestampMs = testClock, detectedObjects = listOf("person", "dog"))
        val results = memory.findByObject("person")
        assertThat(results).hasSize(2)
    }

    @Test
    fun `findByCaptionKeyword returns frames with matching keyword`() {
        val memory = makeMemory()
        memory.addFrame(makeEmbedding(1), timestampMs = testClock, caption = "a receipt on a table")
        memory.addFrame(makeEmbedding(2), timestampMs = testClock, caption = "a plant near window")
        val results = memory.findByCaptionKeyword("receipt")
        assertThat(results).hasSize(1)
        assertThat(results[0].caption).contains("receipt")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // VisualEpisodicMemory — LMK survival (3 tests)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `serializeEncrypted and deserializeEncrypted round-trip preserves frames`() {
        val memory = makeMemory(encryptor = FakeEpisodicMemoryEncryptor())
        memory.addFrame(makeEmbedding(1), timestampMs = testClock, caption = "test frame", detectedObjects = listOf("person"))
        memory.addFrame(makeEmbedding(2), timestampMs = testClock)

        val encrypted = memory.serializeEncrypted()
        assertThat(encrypted).isNotEmpty()

        // Create new memory + restore.
        val restored = makeMemory(encryptor = FakeEpisodicMemoryEncryptor())
        val success = restored.deserializeEncrypted(encrypted)
        assertThat(success).isTrue()
        assertThat(restored.size()).isEqualTo(2)
    }

    @Test
    fun `deserializeEncrypted fails gracefully for corrupted data`() {
        val memory = makeMemory(encryptor = FakeEpisodicMemoryEncryptor())
        val corrupted = byteArrayOf(1, 2, 3, 4, 5)  // Invalid data
        val success = memory.deserializeEncrypted(corrupted)
        assertThat(success).isFalse()
        assertThat(memory.size()).isEqualTo(0)  // Cleared on failure
    }

    @Test
    fun `clear empties the buffer`() {
        val memory = makeMemory()
        memory.addFrame(makeEmbedding(1), timestampMs = testClock)
        memory.addFrame(makeEmbedding(2), timestampMs = testClock)
        memory.clear()
        assertThat(memory.size()).isEqualTo(0)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GraphRAGConnector — prescription (5 tests)
    // ═══════════════════════════════════════════════════════════════════════════

    private fun makePrescription(
        doctor: String = "Dr. Sharma",
        medicines: List<PrescriptionMedicine> = listOf(
            PrescriptionMedicine("Paracetamol", "500mg", "BD", "5 days", "after meals"),
        ),
    ) = Prescription(
        patientName = "Ramesh",
        patientAge = "45",
        patientGender = "Male",
        doctorName = doctor,
        doctorQualification = "MBBS",
        clinicName = "Sharma Clinic",
        clinicAddress = null,
        prescriptionDate = "09/07/2026",
        medicines = medicines,
        diagnosis = "Viral fever",
        followUpDate = null,
    )

    @Test
    fun `savePrescription requires user confirmation`() = runTest {
        val store = FakeGraphStore()
        val connector = GraphRAGConnector(store)
        val prescription = makePrescription()

        // Without confirmation — returns null.
        val result = connector.savePrescription(prescription, userConfirmedSave = false)
        assertThat(result).isNull()
        assertThat(store.nodeCount()).isEqualTo(0)

        // With confirmation — saves.
        val savedId = connector.savePrescription(prescription, userConfirmedSave = true)
        assertThat(savedId).isNotNull()
        assertThat(store.nodeCount()).isGreaterThan(0)
    }

    @Test
    fun `savePrescription creates doctor and medicine nodes`() = runTest {
        val store = FakeGraphStore()
        val connector = GraphRAGConnector(store)
        val prescription = makePrescription(
            doctor = "Dr. Sharma",
            medicines = listOf(
                PrescriptionMedicine("Paracetamol", "500mg", "BD", null, null),
                PrescriptionMedicine("Cetirizine", "10mg", "HS", null, null),
            ),
        )

        connector.savePrescription(prescription, userConfirmedSave = true)

        // Should have: 1 doctor + 2 medicines + 1 prescription doc = 4 nodes
        assertThat(store.nodeCount()).isEqualTo(4)
        assertThat(store.countNodesByType(GraphNodeType.PERSON)).isEqualTo(1)  // Doctor
        assertThat(store.countNodesByType(GraphNodeType.OBJECT)).isEqualTo(2)  // Medicines
        assertThat(store.countNodesByType(GraphNodeType.DOCUMENT)).isEqualTo(1)  // Prescription
    }

    @Test
    fun `savePrescription creates PRESCRIBED edges`() = runTest {
        val store = FakeGraphStore()
        val connector = GraphRAGConnector(store)
        connector.savePrescription(makePrescription(), userConfirmedSave = true)

        val doctorId = "doctor_dr._sharma"
        val prescribedEdges = store.getEdgesFrom(doctorId, GraphEdgeType.PRESCRIBED)
        assertThat(prescribedEdges).hasSize(1)  // Paracetamol
    }

    @Test
    fun `queryPrescriptionContext returns null for unknown doctor`() = runTest {
        val store = FakeGraphStore()
        val connector = GraphRAGConnector(store)
        val context = connector.queryPrescriptionContext(makePrescription(doctor = "Dr. Unknown"))
        assertThat(context).isNull()
    }

    @Test
    fun `queryPrescriptionContext detects dosage changes`() = runTest {
        val store = FakeGraphStore()
        val connector = GraphRAGConnector(store)

        // Save prior prescription with 650mg.
        connector.savePrescription(
            makePrescription(medicines = listOf(
                PrescriptionMedicine("Paracetamol", "650mg", "BD", null, null),
            )),
            userConfirmedSave = true,
        )

        // Query context for new prescription with 500mg.
        val context = connector.queryPrescriptionContext(
            makePrescription(medicines = listOf(
                PrescriptionMedicine("Paracetamol", "500mg", "BD", null, null),
            )),
        )

        assertThat(context).isNotNull()
        assertThat(context!!.hasDosageChanges).isTrue()
        assertThat(context.priorMedicines).hasSize(1)
        assertThat(context.priorMedicines[0].priorDosage).isEqualTo("650mg")
        assertThat(context.priorMedicines[0].newDosage).isEqualTo("500mg")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GraphRAGConnector — receipt + business card + government ID (3 tests)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `saveReceipt creates merchant node and VISITED edge`() = runTest {
        val store = FakeGraphStore()
        val connector = GraphRAGConnector(store)
        val receipt = Receipt(
            merchantName = "Big Bazaar",
            merchantAddress = "MG Road, Bangalore",
            datetime = "09/07/2026",
            items = listOf(ReceiptItem("Atta", 1, 245.0)),
            subtotal = 245.0,
            tax = 12.25,
            total = 257.25,
            paymentMethod = "UPI",
            upiId = null,
        )

        val id = connector.saveReceipt(receipt, userConfirmedSave = true)
        assertThat(id).isNotNull()
        assertThat(store.countNodesByType(GraphNodeType.PLACE)).isEqualTo(1)  // Merchant
        assertThat(store.countNodesByType(GraphNodeType.DOCUMENT)).isEqualTo(1)  // Receipt
    }

    @Test
    fun `saveBusinessCard creates person node`() = runTest {
        val store = FakeGraphStore()
        val connector = GraphRAGConnector(store)
        val card = BusinessCard(
            personName = "Roshan Kumar",
            jobTitle = "Founder",
            organization = "NOUS Inc",
            phoneNumbers = listOf("+919876543210"),
            emails = listOf("roshan@nous.ai"),
            address = null,
            website = "https://nous.ai",
        )

        val id = connector.saveBusinessCard(card, userConfirmedSave = true)
        assertThat(id).isNotNull()
        assertThat(store.countNodesByType(GraphNodeType.PERSON)).isEqualTo(1)
    }

    @Test
    fun `saveGovernmentId creates document node with masked number`() = runTest {
        val store = FakeGraphStore()
        val connector = GraphRAGConnector(store)
        val govId = GovernmentId(
            idType = GovernmentIdType.AADHAAR,
            name = "Ramesh Kumar",
            maskedNumber = "XXXX-XXXX-9012",
            dateOfBirth = "01/01/1980",
            gender = "Male",
            address = null,
            scanTimestamp = System.currentTimeMillis(),
        )

        val id = connector.saveGovernmentId(govId, userConfirmedSave = true)
        assertThat(id).isNotNull()
        assertThat(store.countNodesByType(GraphNodeType.DOCUMENT)).isEqualTo(1)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ScreenUnderstandingEngine (4 tests)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `ScreenUnderstandingEngine readScreen returns null when session not active`() = runTest {
        val engine = ScreenUnderstandingEngine(
            screenshotCapturer = FakeScreenshotCapturer(),
            ocrEngine = makeOcrEngine(emptyList()),
            uiElementDetector = FakeUiElementDetector(),
            screenSummarizer = FakeScreenSummarizer(),
            dispatcher = Dispatchers.Unconfined,
        )
        val result = engine.readScreen()
        assertThat(result).isNull()
    }

    @Test
    fun `ScreenUnderstandingEngine readScreen returns summary when session active`() = runTest {
        val capturer = FakeScreenshotCapturer(cannedImage = ByteArray(100) { 128 })
        val engine = ScreenUnderstandingEngine(
            screenshotCapturer = capturer,
            ocrEngine = makeOcrEngine(listOf(
                com.roshan.persona.vision.ocr.OcrBlock(
                    "Hello World",
                    BoundingBox(0f, 0f, 1f, 0.1f),
                    0.95f,
                    "en",
                ),
            )),
            uiElementDetector = FakeUiElementDetector(listOf(
                UiElement("button", "Send", BoundingBox(0.4f, 0.8f, 0.6f, 0.9f), 0.9f),
            )),
            screenSummarizer = FakeScreenSummarizer(ScreenSummary(
                summary = "Test screen summary",
                spokenSummary = "Test screen summary",
                confidence = 0.95f,
            )),
            dispatcher = Dispatchers.Unconfined,
        )

        engine.startSession()
        val result = engine.readScreen()
        assertThat(result).isNotNull()
        assertThat(result!!.summary).isEqualTo("Test screen summary")
        assertThat(result.uiElements).hasSize(1)
    }

    @Test
    fun `ScreenUnderstandingEngine startSession fails when capturer unavailable`() {
        val engine = ScreenUnderstandingEngine(
            screenshotCapturer = FakeScreenshotCapturer(available = false),
            ocrEngine = makeOcrEngine(emptyList()),
            uiElementDetector = FakeUiElementDetector(),
            screenSummarizer = FakeScreenSummarizer(),
            dispatcher = Dispatchers.Unconfined,
        )
        assertThat(engine.startSession()).isFalse()
    }

    @Test
    fun `ScreenUnderstandingEngine stopSession clears state`() {
        val engine = ScreenUnderstandingEngine(
            screenshotCapturer = FakeScreenshotCapturer(),
            ocrEngine = makeOcrEngine(emptyList()),
            uiElementDetector = FakeUiElementDetector(),
            screenSummarizer = FakeScreenSummarizer(),
            dispatcher = Dispatchers.Unconfined,
        )
        engine.startSession()
        assertThat(engine.isSessionActive()).isTrue()
        engine.stopSession()
        assertThat(engine.isSessionActive()).isFalse()
    }

    private fun makeOcrEngine(blocks: List<com.roshan.persona.vision.ocr.OcrBlock>): com.roshan.persona.vision.ocr.OcrEngine {
        val recognizer = object : com.roshan.persona.vision.ocr.OcrRecognizer {
            override suspend fun recognize(imageData: ByteArray, isEnhanced: Boolean) =
                com.roshan.persona.vision.ocr.OcrRecognitionResult(blocks, setOf("en"))
            override fun isReady() = true
            override fun supportedScripts() = setOf("en")
        }
        val processor = object : com.roshan.persona.vision.ocr.ImageProcessor {
            override suspend fun decodeAndDownsample(imageBytes: ByteArray, maxWidth: Int, maxHeight: Int) =
                com.roshan.persona.vision.ocr.RgbImage(100, 100, ByteArray(300) { 128 })
            override suspend fun histogramEqualize(image: com.roshan.persona.vision.ocr.RgbImage) = image
            override suspend fun gammaCorrect(image: com.roshan.persona.vision.ocr.RgbImage, gamma: Float) = image
            override suspend fun toGrayscale(image: com.roshan.persona.vision.ocr.RgbImage) =
                com.roshan.persona.vision.ocr.GrayscaleImage(image.width, image.height, ByteArray(image.width * image.height) { 128 })
            override suspend fun boxFilter(image: com.roshan.persona.vision.ocr.GrayscaleImage, kernelSize: Int) = image
            override suspend fun divide(numerator: com.roshan.persona.vision.ocr.GrayscaleImage, denominator: com.roshan.persona.vision.ocr.GrayscaleImage, epsilon: Float) = numerator
            override suspend fun clahe(image: com.roshan.persona.vision.ocr.GrayscaleImage, clipLimit: Float, tileGridSize: Int) = image
            override suspend fun bilateralFilter(image: com.roshan.persona.vision.ocr.GrayscaleImage, kernelSize: Int, sigmaColor: Float, sigmaSpace: Float) = image
            override suspend fun deskew(image: com.roshan.persona.vision.ocr.GrayscaleImage) = image
            override suspend fun sauvolaThreshold(image: com.roshan.persona.vision.ocr.GrayscaleImage, windowSize: Int, k: Float) = image
            override suspend fun standardDeviation(image: com.roshan.persona.vision.ocr.GrayscaleImage) = 0f
        }
        return com.roshan.persona.vision.ocr.OcrEngine(
            recognizer = recognizer,
            enhancementPipeline = com.roshan.persona.vision.ocr.ImageEnhancementPipeline(processor, Dispatchers.Unconfined),
            fieldExtractor = com.roshan.persona.vision.ocr.OcrFieldExtractor(),
            documentFormatDetector = com.roshan.persona.vision.ocr.DocumentFormatDetector(),
            structuredDocParser = com.roshan.persona.vision.ocr.StructuredDocumentParser(
                receiptParser = com.roshan.persona.vision.document.ReceiptParser(),
                prescriptionScanner = com.roshan.persona.vision.ocr.PrescriptionScanner(),
                businessCardParser = com.roshan.persona.vision.document.BusinessCardParser(),
                governmentIdParser = com.roshan.persona.vision.document.GovernmentIdParser(),
                invoiceParser = com.roshan.persona.vision.document.InvoiceParser(),
                handwrittenNoteParser = com.roshan.persona.vision.document.HandwrittenNoteParser(),
            ),
            cache = com.roshan.persona.vision.ocr.OcrCache(maxSize = 10),
            dispatcher = Dispatchers.Unconfined,
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // LowVisionMode (5 tests)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `LowVisionMode enable sets enabled true`() = runTest {
        val mode = LowVisionMode(InMemoryLowVisionSettingsStore())
        assertThat(mode.isEnabled()).isFalse()
        mode.enable()
        assertThat(mode.isEnabled()).isTrue()
    }

    @Test
    fun `LowVisionMode disable sets enabled false`() = runTest {
        val mode = LowVisionMode(InMemoryLowVisionSettingsStore())
        mode.enable()
        mode.disable()
        assertThat(mode.isEnabled()).isFalse()
    }

    @Test
    fun `LowVisionMode updateConfig changes fontScale`() = runTest {
        val mode = LowVisionMode(InMemoryLowVisionSettingsStore())
        mode.enable()
        val updated = mode.updateConfig(LowVisionConfigUpdate(fontScale = FontScale.EXTRA_LARGE))
        assertThat(updated.fontScale).isEqualTo(FontScale.EXTRA_LARGE)
    }

    @Test
    fun `LowVisionMode formatForDisplay returns large font for EXTRA_LARGE`() = runTest {
        val mode = LowVisionMode(InMemoryLowVisionSettingsStore())
        mode.enable(com.roshan.persona.vision.accessibility.LowVisionConfig(fontScale = FontScale.EXTRA_LARGE))
        val display = mode.formatForDisplay("Hello")
        assertThat(display.fontSize).isEqualTo(32f)
        assertThat(display.isBold).isTrue()
    }

    @Test
    fun `LowVisionMode getCameraZoomFactor returns configured zoom`() = runTest {
        val mode = LowVisionMode(InMemoryLowVisionSettingsStore())
        mode.enable(com.roshan.persona.vision.accessibility.LowVisionConfig(cameraZoom = CameraZoom.X4))
        assertThat(mode.getCameraZoomFactor()).isEqualTo(4.0f)
    }
}
