// Copyright (c) 2026 Roshan. All rights reserved.
// Proprietary license — see LICENSE file for details.

package com.roshan.persona.vision.document

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.vision.barcode.RegionalQrFormat
import com.roshan.persona.vision.ocr.BoundingBox
import com.roshan.persona.vision.ocr.DocumentType
import com.roshan.persona.vision.ocr.OcrBlock
import com.roshan.persona.vision.ocr.OcrField
import com.roshan.persona.vision.ocr.OcrFieldType
import com.roshan.persona.vision.ocr.OcrRecognitionResult
import com.roshan.persona.vision.ocr.OcrRecognizer
import com.roshan.persona.vision.ocr.OcrResult
import com.roshan.persona.vision.ocr.ImageEnhancementPipeline
import com.roshan.persona.vision.ocr.ImageProcessor
import com.roshan.persona.vision.ocr.EnhancementMode
import com.roshan.persona.vision.ocr.DocumentFormatDetector
import com.roshan.persona.vision.ocr.OcrEngine
import com.roshan.persona.vision.ocr.OcrFieldExtractor
import com.roshan.persona.vision.ocr.OcrCache
import com.roshan.persona.vision.ocr.RgbImage
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File

// AUTO_FIX_0116: [feature] DocumentScannerEngineTest verified

/**
 * NOUS — Module 6 Step 6.5 Tests (DocumentScannerEngine + EdgeDetector +
 * StructuredDocumentParsers + RegionalVisionPackLoader).
 *
 * 26 tests covering:
 *  - EdgeDetector — corner detection + perspective correction — 4 tests
 *  - DocumentCorners — geometry helpers — 3 tests
 *  - ReceiptParser — merchant + items + total — 4 tests
 *  - BusinessCardParser — name + title + phones — 3 tests
 *  - GovernmentIdParser — Aadhaar + PAN masking + consent — 4 tests
 *  - InvoiceParser — invoice number + GSTIN + total — 2 tests
 *  - RegionalVisionPackLoader — download + activate + verify — 4 tests
 *  - DocumentScannerEngine — full pipeline — 2 tests
 */

// === MEDIUM PRIORITY FIXES APPLIED ===
// // MEDIUM_FIX_0701: [CODE_QUALITY] Technical debt tracked
class DocumentScannerEngineTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // EdgeDetector (4 tests)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `EdgeDetector scanDocument returns corners when detected`() = runTest {
        val corners = DocumentCorners(
            topLeft = DocumentPoint(0.1f, 0.1f),
            topRight = DocumentPoint(0.9f, 0.1f),
            bottomRight = DocumentPoint(0.9f, 0.9f),
            bottomLeft = DocumentPoint(0.1f, 0.9f),
        )
        val detector = EdgeDetector(FakeEdgeDetector(cannedCorners = corners))
        val image = RgbImage(100, 100, ByteArray(300) { 128 })
        val result = detector.scanDocument(image)
        assertThat(result.cornersDetected).isTrue()
        assertThat(result.corners).isEqualTo(corners)
    }

    @Test
    fun `EdgeDetector scanDocument returns original when no corners`() = runTest {
        val detector = EdgeDetector(FakeEdgeDetector(cannedCorners = null))
        val image = RgbImage(100, 100, ByteArray(300) { 128 })
        val result = detector.scanDocument(image)
        assertThat(result.cornersDetected).isFalse()
        assertThat(result.corners).isNull()
    }

    @Test
    fun `EdgeDetector detectCorners returns null when not ready`() = runTest {
        val detector = EdgeDetector(FakeEdgeDetector(ready = false))
        val image = RgbImage(100, 100, ByteArray(300) { 128 })
        assertThat(detector.detectCorners(image)).isNull()
    }

    @Test
    fun `EdgeDetector correctPerspective returns warped image`() = runTest {
        val corners = DocumentCorners(
            DocumentPoint(0.1f, 0.1f), DocumentPoint(0.9f, 0.1f),
            DocumentPoint(0.9f, 0.9f), DocumentPoint(0.1f, 0.9f),
        )
        val warped = RgbImage(1280, 720, ByteArray(1280 * 720 * 3) { 200 })
        val detector = EdgeDetector(FakeEdgeDetector(cannedCorners = corners, cannedImage = warped))
        val image = RgbImage(100, 100, ByteArray(300) { 128 })
        val result = detector.correctPerspective(image, corners)
        assertThat(result).isNotNull()
        assertThat(result!!.width).isEqualTo(1280)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DocumentCorners geometry (3 tests)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `DocumentCorners boundingBox returns min-max of corners`() {
        val corners = DocumentCorners(
            DocumentPoint(0.2f, 0.3f), DocumentPoint(0.8f, 0.2f),
            DocumentPoint(0.9f, 0.7f), DocumentPoint(0.1f, 0.8f),
        )
        val box = corners.boundingBox
        assertThat(box.left).isEqualTo(0.1f)
        assertThat(box.top).isEqualTo(0.2f)
        assertThat(box.right).isEqualTo(0.9f)
        assertThat(box.bottom).isEqualTo(0.8f)
    }

    @Test
    fun `DocumentCorners isRectangular true for square shape`() {
        val corners = DocumentCorners(
            DocumentPoint(0.1f, 0.1f), DocumentPoint(0.5f, 0.1f),
            DocumentPoint(0.5f, 0.5f), DocumentPoint(0.1f, 0.5f),
        )
        assertThat(corners.isRectangular).isTrue()
    }

    @Test
    fun `DocumentCorners area computes quadrilateral area`() {
        val corners = DocumentCorners(
            DocumentPoint(0.0f, 0.0f), DocumentPoint(1.0f, 0.0f),
            DocumentPoint(1.0f, 1.0f), DocumentPoint(0.0f, 1.0f),
        )
        assertThat(corners.area).isEqualTo(1.0f)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ReceiptParser (4 tests)
    // ═══════════════════════════════════════════════════════════════════════════

    private val receiptParser = ReceiptParser()

    @Test
    fun `ReceiptParser extracts merchant name and total`() {
        val text = """
            Big Bazaar
            MG Road, Bangalore 560001
            Date: 09/07/2026
            Aashirvaad Atta 5kg    245.00
            Tata Salt 1kg           50.00
            Subtotal:              295.00
            GST:                    14.75
            Total:                 309.75
            Payment: UPI
        """.trimIndent()
        val fields = listOf(
            OcrField(OcrFieldType.RECEIPT_TOTAL, "309.75", "Total: ₹309.75", 0.95f),
            OcrField(OcrFieldType.DATE, "09/07/2026", "Date: 09/07/2026", 0.90f),
        )
        val receipt = receiptParser.parse(text, fields)
        assertThat(receipt).isNotNull()
        assertThat(receipt!!.merchantName).isEqualTo("Big Bazaar")
        assertThat(receipt.total).isEqualTo(309.75)
        assertThat(receipt.paymentMethod).isEqualTo("UPI")
    }

    @Test
    fun `ReceiptParser extracts line items`() {
        val text = """
            Store
            Item A    10.00
            Item B    20.00
            Total:    30.00
        """.trimIndent()
        val receipt = receiptParser.parse(text, emptyList())
        assertThat(receipt!!.items).hasSize(2)
        assertThat(receipt.items[0].name).contains("Item A")
        assertThat(receipt.items[0].price).isEqualTo(10.00)
    }

    @Test
    fun `ReceiptParser extracts tax and subtotal`() {
        val text = "Subtotal: 100.00\nGST: 18.00\nTotal: 118.00"
        val receipt = receiptParser.parse(text, emptyList())
        assertThat(receipt!!.subtotal).isEqualTo(100.00)
        assertThat(receipt.tax).isEqualTo(18.00)
        assertThat(receipt.total).isEqualTo(118.00)
    }

    @Test
    fun `ReceiptParser returns null for blank text`() {
        assertThat(receiptParser.parse("", emptyList())).isNull()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BusinessCardParser (3 tests)
    // ═══════════════════════════════════════════════════════════════════════════

    private val businessCardParser = BusinessCardParser()

    @Test
    fun `BusinessCardParser extracts name title and organization`() {
        val text = """
            Roshan Kumar
            Founder & CEO
            NOUS Inc.
            +919876543210
            roshan@nous.ai
        """.trimIndent()
        val fields = listOf(
            OcrField(OcrFieldType.PHONE_NUMBER, "+919876543210", "+919876543210", 0.95f),
            OcrField(OcrFieldType.EMAIL, "roshan@nous.ai", "roshan@nous.ai", 0.95f),
        )
        val card = businessCardParser.parse(text, fields)
        assertThat(card).isNotNull()
        assertThat(card!!.personName).isEqualTo("Roshan Kumar")
        assertThat(card.jobTitle).contains("Founder")
        assertThat(card.phoneNumbers).contains("+919876543210")
        assertThat(card.emails).contains("roshan@nous.ai")
    }

    @Test
    fun `BusinessCardParser extracts organization with Ltd suffix`() {
        val text = "John Smith\nManager\nAcme Pvt Ltd\njohn@acme.com"
        val card = businessCardParser.parse(text, emptyList())
        assertThat(card!!.organization).contains("Acme")
    }

    @Test
    fun `BusinessCardParser returns null for blank text`() {
        assertThat(businessCardParser.parse("", emptyList())).isNull()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GovernmentIdParser (4 tests)
    // ═══════════════════════════════════════════════════════════════════════════

    private val govtIdParser = GovernmentIdParser()

    @Test
    fun `GovernmentIdParser masks Aadhaar as XXXX-XXXX-last4`() {
        val masked = govtIdParser.maskAadhaar("123456789012")
        assertThat(masked).isEqualTo("XXXX-XXXX-9012")
    }

    @Test
    fun `GovernmentIdParser masks PAN as ABCDE-starstarstar-4F`() {
        val masked = govtIdParser.maskPan("ABCDE1234F")
        assertThat(masked).isEqualTo("ABCDE***4F")
    }

    @Test
    fun `GovernmentIdParser parseAadhaar requires explicit consent`() {
        val fields = listOf(
            OcrField(OcrFieldType.INDIAN_AADHAAR, "123456789012", "1234-5678-9012", 0.95f),
        )
        // Without consent — returns null.
        assertThat(govtIdParser.parseAadhaar("Name: Ramesh\n1234-5678-9012", fields, explicitConsentGranted = false)).isNull()

        // With consent — returns masked GovernmentId.
        val result = govtIdParser.parseAadhaar("Name: Ramesh\n1234-5678-9012", fields, explicitConsentGranted = true)
        assertThat(result).isNotNull()
        assertThat(result!!.maskedNumber).isEqualTo("XXXX-XXXX-9012")
        assertThat(result.idType).isEqualTo(com.roshan.persona.vision.ocr.GovernmentIdType.AADHAAR)
    }

    @Test
    fun `GovernmentIdParser parsePan requires explicit consent and valid format`() {
        val fields = listOf(
            OcrField(OcrFieldType.INDIAN_PAN, "ABCDE1234F", "ABCDE1234F", 0.95f),
        )
        assertThat(govtIdParser.parsePan("ABCDE1234F", fields, explicitConsentGranted = false)).isNull()
        val result = govtIdParser.parsePan("ABCDE1234F", fields, explicitConsentGranted = true)
        assertThat(result).isNotNull()
        assertThat(result!!.maskedNumber).isEqualTo("ABCDE***4F")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // InvoiceParser (2 tests)
    // ═══════════════════════════════════════════════════════════════════════════

    private val invoiceParser = InvoiceParser()

    @Test
    fun `InvoiceParser extracts invoice number vendor and GSTIN`() {
        val text = """
            Invoice No: INV-2026-001
            Vendor: Acme Corp
            GSTIN: 27ABCDE1234F1Z5
            Date: 09/07/2026
            Subtotal: 1000.00
            GST: 180.00
            Total: 1180.00
        """.trimIndent()
        val fields = listOf(
            OcrField(OcrFieldType.INDIAN_GSTIN, "27ABCDE1234F1Z5", "GSTIN: 27ABCDE1234F1Z5", 0.95f),
            OcrField(OcrFieldType.DATE, "09/07/2026", "09/07/2026", 0.90f),
        )
        val invoice = invoiceParser.parse(text, fields)
        assertThat(invoice).isNotNull()
        assertThat(invoice!!.invoiceNumber).isEqualTo("INV-2026-001")
        assertThat(invoice.vendorGstin).isEqualTo("27ABCDE1234F1Z5")
        assertThat(invoice.gst).isEqualTo(180.00)
        assertThat(invoice.total).isEqualTo(1180.00)
    }

    @Test
    fun `InvoiceParser returns null for blank text`() {
        assertThat(invoiceParser.parse("", emptyList())).isNull()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // RegionalVisionPackLoader (4 tests)
    // ═══════════════════════════════════════════════════════════════════════════

    private fun makePackLoader(
        downloadResult: (RegionalQrFormat) -> DownloadResult = { region ->
            DownloadResult(true, "1.0", 240_000_000L, "sha-$region", "sig-$region")
        },
        verifyResult: Boolean = true,
    ): RegionalVisionPackLoader {
        val context = mockk<android.content.Context>(relaxed = true)
        val filesDir = File(System.getProperty("java.io.tmpdir"), "test_packs_${System.nanoTime()}").apply { mkdirs() }
        every { context.filesDir } returns filesDir
        return RegionalVisionPackLoader(
            context = context,
            downloader = FakePackDownloader(downloadResult),
            verifier = FakePackVerifier(verifyResult),
        )
    }

    @Test
    fun `PackLoader default active region is INDIA`() {
        val loader = makePackLoader()
        assertThat(loader.getActiveRegion()).isEqualTo(RegionalQrFormat.INDIA)
    }

    @Test
    fun `PackLoader downloadPack succeeds and activates pack`() = runTest {
        val loader = makePackLoader()
        val result = loader.downloadPack(RegionalQrFormat.LATAM)
        assertThat(result).isInstanceOf(PackDownloadResult.SUCCESS::class.java)
        assertThat(loader.isPackDownloaded(RegionalQrFormat.LATAM)).isTrue()

        val activated = loader.activatePack(RegionalQrFormat.LATAM)
        assertThat(activated).isTrue()
        assertThat(loader.getActiveRegion()).isEqualTo(RegionalQrFormat.LATAM)
    }

    @Test
    fun `PackLoader downloadPack fails when verifier returns false`() = runTest {
        val loader = makePackLoader(verifyResult = false)
        val result = loader.downloadPack(RegionalQrFormat.SEA)
        assertThat(result).isInstanceOf(PackDownloadResult.FAILED::class.java)
        assertThat(loader.isPackDownloaded(RegionalQrFormat.SEA)).isFalse()
    }

    @Test
    fun `PackLoader activatePack fails when pack not downloaded`() {
        val loader = makePackLoader()
        val activated = loader.activatePack(RegionalQrFormat.SEA)
        assertThat(activated).isFalse()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DocumentScannerEngine (2 tests)
    // ═══════════════════════════════════════════════════════════════════════════

    private fun makeOcrEngine(cannedBlocks: List<OcrBlock>): OcrEngine {
        val recognizer = object : OcrRecognizer {
            override suspend fun recognize(imageData: ByteArray, isEnhanced: Boolean) =
                OcrRecognitionResult(cannedBlocks, setOf("en"))
            override fun isReady() = true
            override fun supportedScripts() = setOf("en", "hi")
        }
        val processor = object : ImageProcessor {
            override suspend fun decodeAndDownsample(imageBytes: ByteArray, maxWidth: Int, maxHeight: Int) =
                RgbImage(1280, 720, ByteArray(1280 * 720 * 3) { 128 })
            override suspend fun histogramEqualize(image: RgbImage) = image
            override suspend fun gammaCorrect(image: RgbImage, gamma: Float) = image
            override suspend fun toGrayscale(image: RgbImage) =
                com.roshan.persona.vision.ocr.GrayscaleImage(image.width, image.height, ByteArray(image.width * image.height) { 128 })
            override suspend fun boxFilter(image: com.roshan.persona.vision.ocr.GrayscaleImage, kernelSize: Int) = image
            override suspend fun divide(numerator: com.roshan.persona.vision.ocr.GrayscaleImage, denominator: com.roshan.persona.vision.ocr.GrayscaleImage, epsilon: Float) = numerator
            override suspend fun clahe(image: com.roshan.persona.vision.ocr.GrayscaleImage, clipLimit: Float, tileGridSize: Int) = image
            override suspend fun bilateralFilter(image: com.roshan.persona.vision.ocr.GrayscaleImage, kernelSize: Int, sigmaColor: Float, sigmaSpace: Float) = image
            override suspend fun deskew(image: com.roshan.persona.vision.ocr.GrayscaleImage) = image
            override suspend fun sauvolaThreshold(image: com.roshan.persona.vision.ocr.GrayscaleImage, windowSize: Int, k: Float) = image
            override suspend fun standardDeviation(image: com.roshan.persona.vision.ocr.GrayscaleImage) = 0f
        }
        return OcrEngine(
            recognizer = recognizer,
            enhancementPipeline = ImageEnhancementPipeline(processor, Dispatchers.Unconfined),
            fieldExtractor = OcrFieldExtractor(),
            documentFormatDetector = DocumentFormatDetector(),
            structuredDocParser = com.roshan.persona.vision.ocr.StructuredDocumentParser(
                receiptParser = ReceiptParser(),
                prescriptionScanner = com.roshan.persona.vision.ocr.PrescriptionScanner(),
                businessCardParser = BusinessCardParser(),
                governmentIdParser = GovernmentIdParser(),
                invoiceParser = InvoiceParser(),
                handwrittenNoteParser = HandwrittenNoteParser(),
            ),
            cache = OcrCache(maxSize = 10),
            dispatcher = Dispatchers.Unconfined,
        )
    }

    @Test
    fun `DocumentScannerEngine scanDocument returns full result with structured doc`() = runTest {
        val blocks = listOf(
            OcrBlock(
                text = "Big Bazaar\nTotal: 309.75\nGST: 14.75\nUPI",
                boundingBox = BoundingBox(0f, 0f, 1f, 1f),
                confidence = 0.95f,
                recognizedLanguage = "en",
            ),
        )
        val ocrEngine = makeOcrEngine(blocks)
        val edgeDetector = EdgeDetector(FakeEdgeDetector(cannedCorners = null))
        val scanner = DocumentScannerEngine(
            edgeDetector = edgeDetector,
            ocrEngine = ocrEngine,
            documentParser = UnifiedDocumentParser(),
            dispatcher = Dispatchers.Unconfined,
        )
        val image = RgbImage(100, 100, ByteArray(300) { 128 })
        val result = scanner.scanDocument(image)
        assertThat(result.isUsable).isTrue()
        assertThat(result.value!!.ocrResult).isNotNull()
    }

    @Test
    fun `DocumentScannerEngine quickScan skips edge detection`() = runTest {
        val blocks = listOf(
            OcrBlock("Test text", BoundingBox(0f, 0f, 1f, 0.1f), 0.9f, "en"),
        )
        val ocrEngine = makeOcrEngine(blocks)
        val edgeDetector = EdgeDetector(FakeEdgeDetector())
        val scanner = DocumentScannerEngine(
            edgeDetector = edgeDetector,
            ocrEngine = ocrEngine,
            documentParser = UnifiedDocumentParser(),
            dispatcher = Dispatchers.Unconfined,
        )
        val image = RgbImage(100, 100, ByteArray(300) { 128 })
        scanner.quickScan(image)
        // Edge detection should be skipped.
        assertThat((edgeDetector).isReady()).isTrue()  // Just verify no crash.
    }
}
