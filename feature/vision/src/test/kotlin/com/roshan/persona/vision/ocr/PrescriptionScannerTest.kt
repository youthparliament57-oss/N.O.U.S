// Copyright (c) 2026 Roshan. All rights reserved.
// Proprietary license — see LICENSE file for details.

package com.roshan.persona.vision.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrescriptionScannerTest {

    private val scanner = PrescriptionScanner()

    @Test
    fun testLooksLikePrescription() {
        val fields = listOf(OcrField(OcrFieldType.MEDICINE_NAME, "Paracetamol", 0.9f, null))
        assertTrue(scanner.looksLikePrescription("Rx Paracetamol 500mg", emptyList()))
        assertTrue(scanner.looksLikePrescription("Dr. Smith Consultation", emptyList()))
        assertTrue(scanner.looksLikePrescription("Patient prescription details", emptyList()))
        assertTrue(scanner.looksLikePrescription("Some text", fields))
    }

    @Test
    fun testParsePrescription() {
        val sampleText = """
            Dr. Sharma MBBS
            Apollo Clinic
            Address: MG Road, Bangalore 560001
            Patient: Rajesh Kumar
            Age: 45 years / M
            Date: 15/03/2026

            Rx
            Tab Paracetamol 500mg BD for 5 days after meals
            Tab Pantop 40mg OD before food

            Diagnosis: Acute Fever
            Review after 7 days
        """.trimIndent()

        val prescription = scanner.parse(sampleText, emptyList(), emptyList())

        assertNotNull(prescription)
        assertEquals("Sharma", prescription?.doctorName)
        assertEquals("MBBS", prescription?.doctorQualification)
        assertEquals("Apollo Clinic", prescription?.clinicName)
        assertEquals("MG Road, Bangalore 560001", prescription?.clinicAddress)
        assertEquals("Rajesh Kumar", prescription?.patientName)
        assertEquals("45", prescription?.patientAge)
        assertEquals("Male", prescription?.patientGender)
        assertEquals("15/03/2026", prescription?.prescriptionDate)
        assertEquals("Acute Fever", prescription?.diagnosis)
        assertEquals("Review after 7 days", prescription?.followUpDate)

        val medicines = prescription?.medicines ?: emptyList()
        assertTrue("Expected at least 2 medicines parsed", medicines.size >= 2)

        val paracetamol = medicines.find { it.name.equals("Paracetamol", ignoreCase = true) }
        assertNotNull(paracetamol)
        assertEquals("500mg", paracetamol?.dosage)
        assertEquals("BD", paracetamol?.frequency)
        assertEquals("for 5 days", paracetamol?.duration)
        assertEquals("after meals", paracetamol?.instructions)

        val pantop = medicines.find { it.name.equals("Pantop", ignoreCase = true) }
        assertNotNull(pantop)
        assertEquals("40mg", pantop?.dosage)
        assertEquals("OD", pantop?.frequency)
    }
}
