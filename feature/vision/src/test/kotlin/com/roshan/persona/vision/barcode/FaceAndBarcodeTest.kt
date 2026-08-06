// Copyright (c) 2026 Roshan. All rights reserved.
// Proprietary license — see LICENSE file for details.

package com.roshan.persona.vision.barcode

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.vision.face.FaceOperation
import com.roshan.persona.vision.face.FacePrivacyGuard
import com.roshan.persona.vision.face.FacePrivacyViolationException
import com.roshan.persona.vision.ocr.BoundingBox
import org.junit.Test

// AUTO_FIX_0121: [feature] FaceAndBarcodeTest verified

/**
 * NOUS — Module 6 Step 6.4 Tests (FaceDetectionEngine + BarcodeScannerEngine +
 * QrPayloadParser + UpiQrHandler + FacePrivacyGuard).
 *
 * 26 tests covering:
 *  - FacePrivacyGuard — hard line enforcement (5 tests)
 *  - QrPayloadParser — URL/UPI/WiFi/vCard/SMS/Email/Phone/Geo/EMV (12 tests)
 *  - RegionalQrFormat — region detection (3 tests)
 *  - BarcodeModels — helper methods (2 tests)
 *  - FaceDetectionModels — DetectedFace helpers (4 tests)
 */
class FaceAndBarcodeTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // FacePrivacyGuard (5 tests)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `FacePrivacyGuard allows DETECT operation`() {
        assertThat(FacePrivacyGuard.isAllowed(FaceOperation.DETECT)).isTrue()
    }

    @Test
    fun `FacePrivacyGuard allows CROP operation`() {
        assertThat(FacePrivacyGuard.isAllowed(FaceOperation.CROP)).isTrue()
    }

    @Test
    fun `FacePrivacyGuard blocks RECOGNITION operation`() {
        assertThat(FacePrivacyGuard.isAllowed(FaceOperation.RECOGNITION)).isFalse()
    }

    @Test
    fun `FacePrivacyGuard blocks IDENTIFICATION operation`() {
        assertThat(FacePrivacyGuard.isAllowed(FaceOperation.IDENTIFICATION)).isFalse()
    }

    @Test
    fun `FacePrivacyGuard assertAllowed throws for RECOGNITION`() {
        try {
            FacePrivacyGuard.assertAllowed(FaceOperation.RECOGNITION)
            assert(false) { "Should have thrown FacePrivacyViolationException" }
        } catch (e: FacePrivacyViolationException) {
            assertThat(e.operation).isEqualTo(FaceOperation.RECOGNITION)
        }
    }

    @Test
    fun `FacePrivacyGuard enableFaceTagging returns false in v4`() {
        // v4: face-tagging not available — always returns false.
        assertThat(FacePrivacyGuard.enableFaceTagging()).isFalse()
        assertThat(FacePrivacyGuard.isFaceTaggingEnabled()).isFalse()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // QrPayloadParser — URL (2 tests)
    // ═══════════════════════════════════════════════════════════════════════════

    private val parser = QrPayloadParser()

    @Test
    fun `parseUrl returns UrlPayload with isSecure true for https`() {
        val payload = parser.parseUrl("https://nous.ai/example")
        assertThat(payload.isSecure).isTrue()
        assertThat(payload.url).isEqualTo("https://nous.ai/example")
    }

    @Test
    fun `parseUrl returns UrlPayload with isSecure false for http`() {
        val payload = parser.parseUrl("http://example.com")
        assertThat(payload.isSecure).isFalse()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // QrPayloadParser — UPI (India-first) (3 tests)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `parseUpi extracts UPI ID and payee name`() {
        val payload = parser.parseUpi("upi://pay?pa=roshan@okhdfcbank&pn=Roshan%20Kumar")
        assertThat(payload.upiId).isEqualTo("roshan@okhdfcbank")
        assertThat(payload.payeeName).isEqualTo("Roshan Kumar")
        assertThat(payload.currency).isEqualTo("INR")
        assertThat(payload.isValidUpiId).isTrue()
    }

    @Test
    fun `parseUpi extracts amount and note`() {
        val payload = parser.parseUpi("upi://pay?pa=merchant@upi&am=100.50&cu=INR&tn=Lunch")
        assertThat(payload.amount).isEqualTo(100.50)
        assertThat(payload.note).isEqualTo("Lunch")
    }

    @Test
    fun `parseUpi returns empty UPI ID for malformed URI`() {
        val payload = parser.parseUpi("upi://pay")
        assertThat(payload.upiId).isEmpty()
        assertThat(payload.isValidUpiId).isFalse()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // QrPayloadParser — WiFi (2 tests)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `parseWifi extracts WPA network with password`() {
        val payload = parser.parseWifi("WIFI:S:MyNetwork;T:WPA;P:password123;;")
        assertThat(payload.ssid).isEqualTo("MyNetwork")
        assertThat(payload.password).isEqualTo("password123")
        assertThat(payload.encryptionType).isEqualTo(WifiEncryptionType.WPA)
    }

    @Test
    fun `parseWifi extracts open network without password`() {
        val payload = parser.parseWifi("WIFI:S:OpenNetwork;T:NOPASS;;")
        assertThat(payload.ssid).isEqualTo("OpenNetwork")
        assertThat(payload.password).isNull()
        assertThat(payload.encryptionType).isEqualTo(WifiEncryptionType.NONE)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // QrPayloadParser — vCard (1 test)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `parseVCard extracts name phone email and org`() {
        val vcard = """
            BEGIN:VCARD
            VERSION:3.0
            FN:Roshan Kumar
            ORG:NOUS Inc
            TITLE:Founder
            TEL:+919876543210
            EMAIL:roshan@nous.ai
            URL:https://nous.ai
            END:VCARD
        """.trimIndent()
        val payload = parser.parseVCard(vcard)
        assertThat(payload.name).isEqualTo("Roshan Kumar")
        assertThat(payload.organization).isEqualTo("NOUS Inc")
        assertThat(payload.title).isEqualTo("Founder")
        assertThat(payload.phoneNumbers).contains("+919876543210")
        assertThat(payload.emails).contains("roshan@nous.ai")
        assertThat(payload.url).isEqualTo("https://nous.ai")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // QrPayloadParser — SMS / Email / Phone / Geo (4 tests)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `parseSms extracts phone and message from smsto format`() {
        val payload = parser.parseSms("smsto:+919876543210:Hello there")
        assertThat(payload.phoneNumber).isEqualTo("+919876543210")
        assertThat(payload.message).isEqualTo("Hello there")
    }

    @Test
    fun `parseEmail extracts email subject and body`() {
        val payload = parser.parseEmail("mailto:roshan@nous.ai?subject=Hello&body=Test")
        assertThat(payload.email).isEqualTo("roshan@nous.ai")
        assertThat(payload.subject).isEqualTo("Hello")
        assertThat(payload.body).isEqualTo("Test")
    }

    @Test
    fun `parsePhone extracts phone number`() {
        val payload = parser.parsePhone("tel:+919876543210")
        assertThat(payload.phoneNumber).isEqualTo("+919876543210")
    }

    @Test
    fun `parseGeo extracts latitude longitude and query`() {
        val payload = parser.parseGeo("geo:12.9716,77.5946?q=Bangalore")
        assertThat(payload.latitude).isEqualTo(12.9716)
        assertThat(payload.longitude).isEqualTo(77.5946)
        assertThat(payload.query).isEqualTo("Bangalore")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // QrPayloadParser — dispatch (2 tests)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `parse dispatches to UPI for upi scheme`() {
        val result = parser.parse("upi://pay?pa=test@upi&pn=Test")
        assertThat(result?.first).isEqualTo(QrPayloadType.UPI)
        assertThat(result?.second).isInstanceOf(QrPayload.UpiPayload::class.java)
    }

    @Test
    fun `parse returns TEXT for unknown format`() {
        val result = parser.parse("just some plain text")
        assertThat(result?.first).isEqualTo(QrPayloadType.TEXT)
        assertThat(result?.second).isInstanceOf(QrPayload.TextPayload::class.java)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // RegionalQrFormat (3 tests)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `RegionalQrFormat INDIA supports UPI`() {
        assertThat(RegionalQrFormat.INDIA.supports(QrPayloadType.UPI)).isTrue()
        assertThat(RegionalQrFormat.INDIA.supports(QrPayloadType.PIX)).isFalse()
    }

    @Test
    fun `RegionalQrFormat LATAM supports Pix and Boleto`() {
        assertThat(RegionalQrFormat.LATAM.supports(QrPayloadType.PIX)).isTrue()
        assertThat(RegionalQrFormat.LATAM.supports(QrPayloadType.BOLETO)).isTrue()
        assertThat(RegionalQrFormat.LATAM.supports(QrPayloadType.UPI)).isFalse()
    }

    @Test
    fun `RegionalQrFormat forCountryCode returns correct region`() {
        assertThat(RegionalQrFormat.forCountryCode("IN")).isEqualTo(RegionalQrFormat.INDIA)
        assertThat(RegionalQrFormat.forCountryCode("BR")).isEqualTo(RegionalQrFormat.LATAM)
        assertThat(RegionalQrFormat.forCountryCode("TH")).isEqualTo(RegionalQrFormat.SEA)
        assertThat(RegionalQrFormat.forCountryCode("US")).isEqualTo(RegionalQrFormat.INDIA)  // Default
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BarcodeModels helpers (2 tests)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `BarcodeResult isQrCode returns true for QR_CODE format`() {
        val barcode = BarcodeResult(
            format = BarcodeFormat.QR_CODE,
            rawValue = "upi://pay?pa=test@upi",
            boundingBox = BoundingBox(0.1f, 0.1f, 0.9f, 0.9f),
            confidence = 0.95f,
        )
        assertThat(barcode.isQrCode).isTrue()
    }

    @Test
    fun `BarcodeScanResult upiQrCodes filters UPI payload type`() {
        val upiBarcode = BarcodeResult(
            format = BarcodeFormat.QR_CODE,
            rawValue = "upi://pay?pa=test@upi",
            boundingBox = BoundingBox(0f, 0f, 1f, 1f),
            confidence = 0.95f,
            payloadType = QrPayloadType.UPI,
            parsedPayload = QrPayload.UpiPayload("test@upi", null, null, "INR", null, null),
        )
        val urlBarcode = BarcodeResult(
            format = BarcodeFormat.QR_CODE,
            rawValue = "https://nous.ai",
            boundingBox = BoundingBox(0f, 0f, 1f, 1f),
            confidence = 0.90f,
            payloadType = QrPayloadType.URL,
        )
        val result = BarcodeScanResult(listOf(upiBarcode, urlBarcode), processingTimeMs = 50)
        assertThat(result.upiQrCodes).hasSize(1)
        assertThat(result.upiQrCodes[0].rawValue).contains("upi://")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FaceDetectionModels helpers (4 tests)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `DetectedFace isSmiling returns true for probability above 0_75`() {
        val face = com.roshan.persona.vision.face.DetectedFace(
            boundingBox = BoundingBox(0.1f, 0.1f, 0.4f, 0.4f),
            confidence = 0.95f,
            smileProbability = 0.85f,
        )
        assertThat(face.isSmiling).isTrue()
    }

    @Test
    fun `DetectedFace areEyesOpen returns true when both eyes above 0_5`() {
        val face = com.roshan.persona.vision.face.DetectedFace(
            boundingBox = BoundingBox(0.1f, 0.1f, 0.4f, 0.4f),
            confidence = 0.95f,
            leftEyeOpenProbability = 0.8f,
            rightEyeOpenProbability = 0.7f,
        )
        assertThat(face.areEyesOpen).isTrue()
    }

    @Test
    fun `DetectedFace isFrontal returns true for small euler angles`() {
        val face = com.roshan.persona.vision.face.DetectedFace(
            boundingBox = BoundingBox(0.1f, 0.1f, 0.4f, 0.4f),
            confidence = 0.95f,
            headEulerAngleX = 5f,
            headEulerAngleY = 8f,
        )
        assertThat(face.isFrontal).isTrue()
    }

    @Test
    fun `DetectedFace sizeCategory returns LARGE for big bounding box`() {
        val face = com.roshan.persona.vision.face.DetectedFace(
            boundingBox = BoundingBox(0.1f, 0.1f, 0.7f, 0.7f),  // 0.36 area > 0.25
            confidence = 0.95f,
        )
        assertThat(face.sizeCategory).isEqualTo(com.roshan.persona.vision.face.FaceSizeCategory.LARGE)
    }

    @Test
    fun `FaceDetectionResult largestFace returns face with max area`() {
        val smallFace = com.roshan.persona.vision.face.DetectedFace(
            boundingBox = BoundingBox(0.1f, 0.1f, 0.2f, 0.2f),
            confidence = 0.9f,
        )
        val largeFace = com.roshan.persona.vision.face.DetectedFace(
            boundingBox = BoundingBox(0.1f, 0.1f, 0.6f, 0.6f),
            confidence = 0.85f,
        )
        val result = com.roshan.persona.vision.face.FaceDetectionResult(
            faces = listOf(smallFace, largeFace),
            imageWidth = 1000,
            imageHeight = 1000,
            processingTimeMs = 50,
        )
        assertThat(result.largestFace).isEqualTo(largeFace)
    }
}
