// Copyright (c) 2026 Roshan. All rights reserved.
// Proprietary license — see LICENSE file for details.

package com.roshan.persona.vision.ocr

import timber.log.Timber

/**
 * NOUS — Prescription Scanner (Module 6 v4 — Step 6.2, India-First).
 *
 * Extracts structured data from medical prescriptions — a critical use case
 * in India (elderly users, rural areas, low literacy) per strategy v3 §"Issue 7:
 * Missing Document Type — Medical Prescriptions".
 *
 * ## What it extracts
 *
 *  - Patient name, age, gender
 *  - Doctor name, qualification, clinic name + address
 *  - Prescription date
 *  - Medicines (name + dosage + frequency + duration + instructions)
 *  - Diagnosis
 *  - Follow-up date
 *
 * ## How it works
 *
 * ```
 * Raw OCR text →
 *   1. Identify "Rx" or "Dr." markers (confirms prescription)
 *   2. Extract doctor name via regex ("Dr. X")
 *   3. Extract patient name via context ("Patient: X" or first line after doctor)
 *   4. Extract prescription date (DD/MM/YYYY)
 *   5. Identify medicine names (lookup against COMMON_MEDICINES list)
 *   6. For each medicine, find nearby dosage ("500mg"), frequency ("BD" / "TID" / "1-0-1"),
 *     duration ("for 5 days"), and instructions ("after meals")
 *   7. Extract diagnosis (text after "Diagnosis:" or "Advice:")
 *   8. Extract follow-up date ("Review after X days" / "Follow-up: DD/MM/YYYY")
 * ```
 *
 * ## India-specific medical notation
 *
 * Indian prescriptions use Latin frequency abbreviations:
 *  - **OD** = once daily (morning)
 *  - **BD** = twice daily (morning + evening)
 *  - **TDS** / **TID** = three times daily
 *  - **QID** = four times daily
 *  - **HS** = at bedtime
 *  - **SOS** = as needed
 *  - **1-0-1** = morning + night (1 pill morning, 0 noon, 1 pill night)
 *  - **1-1-1** = morning + noon + night
 *
 * ## Privacy (per strategy v3 §"Privacy & Security")
 *
 *  - Prescription data is personal health information (PHI).
 *  - The structured [Prescription] is stored in encrypted Memory (Module 3) — never synced.
 *  - User can delete any prescription via Settings → Privacy → Medical Records.
 *  - The raw OCR text is discarded after structured extraction (only [Prescription] persisted).
 *
 * @see <a href="docs/strategy/module-6-vision-strategy-v4.md">§Issue 7: Prescription Scanner</a>
 */
class PrescriptionScanner {

    /**
     * Parse a prescription from OCR text + extracted fields.
     *
     * @param fullText the full OCR text from all blocks.
     * @param extractedFields pre-extracted fields (from [OcrFieldExtractor]).
     * @param blocks individual OCR blocks (for spatial context — dosage is
     *               typically near the medicine name).
     * @return the structured [Prescription], or null if the text doesn't
     *         look like a prescription.
     */
    fun parse(
        fullText: String,
        extractedFields: List<OcrField>,
        blocks: List<OcrBlock>,
    ): Prescription? {
        if (!looksLikePrescription(fullText, extractedFields)) {
            Timber.tag(TAG).d("Text doesn't look like a prescription — skipping.")
            return null
        }
        Timber.tag(TAG).i("Parsing prescription (${fullText.length} chars, ${blocks.size} blocks).")
        val doctorName = extractDoctorName(extractedFields, fullText)
        val doctorQualification = extractDoctorQualification(fullText)
        val clinicName = extractClinicName(fullText)
        val clinicAddress = extractClinicAddress(fullText)
        val patientName = extractPatientName(extractedFields, fullText)
        val patientAge = extractPatientAge(fullText)
        val patientGender = extractPatientGender(fullText)
        val prescriptionDate = extractPrescriptionDate(extractedFields, fullText)
        val medicines = extractMedicines(fullText, blocks)
        val diagnosis = extractDiagnosis(fullText)
        val followUpDate = extractFollowUpDate(fullText)

        val prescription = Prescription(
            patientName = patientName,
            patientAge = patientAge,
            patientGender = patientGender,
            doctorName = doctorName,
            doctorQualification = doctorQualification,
            clinicName = clinicName,
            clinicAddress = clinicAddress,
            prescriptionDate = prescriptionDate,
            medicines = medicines,
            diagnosis = diagnosis,
            followUpDate = followUpDate,
        )
        Timber.tag(TAG).i("Prescription parsed: doctor=$doctorName, patient=$patientName, " +
            "${medicines.size} medicines, diagnosis=$diagnosis.")
        return prescription
    }

    /**
     * Heuristic check: does this text look like a prescription?
     *
     * Returns true if any of:
     *  - Contains "Rx" marker (prescription symbol)
     *  - Contains "Dr." (doctor name)
     *  - Contains "Prescription" or "Rx Pad"
     *  - Has at least 1 medicine field extracted
     *  - Has at least 1 dosage field extracted
     */
    internal fun looksLikePrescription(text: String, fields: List<OcrField>): Boolean {
        if (text.contains("\u211E")) return true  // Rx symbol (℞)
        if (text.lowercase().contains("rx")) return true
        if (text.contains("Dr.", ignoreCase = true) || text.contains("Dr ")) return true
        if (text.contains("prescription", ignoreCase = true)) return true
        if (fields.any { it.type == OcrFieldType.MEDICINE_NAME }) return true
        if (fields.any { it.type == OcrFieldType.MEDICINE_DOSAGE }) return true
        return false
    }

    /**
     * Extract doctor name from fields (preferred) or text.
     */
    internal fun extractDoctorName(fields: List<OcrField>, text: String): String? {
        // Prefer extracted field (higher confidence).
        fields.firstOrNull { it.type == OcrFieldType.DOCTOR_NAME }?.let { return it.value }
        // Fallback: regex on text.
        val match = DOCTOR_NAME_REGEX.find(text)
        return match?.groupValues?.getOrNull(1)?.trim()
    }

    /**
     * Extract doctor qualification (e.g., "MBBS", "MD", "DM").
     */
    internal fun extractDoctorQualification(text: String): String? {
        val match = DOCTOR_QUALIFICATION_REGEX.find(text)
        return match?.value
    }

    /**
     * Extract clinic name (heuristic: line after doctor name, often in caps).
     */
    internal fun extractClinicName(text: String): String? {
        // Look for "Clinic:" or "Hospital:" prefix.
        val prefixMatch = CLINIC_PREFIX_REGEX.find(text)
        prefixMatch?.groupValues?.getOrNull(1)?.trim()?.let { return it }
        // Fallback: line after "Dr. X" line that's all caps.
        val lines = text.lines()
        for (i in 0 until lines.size - 1) {
            if (lines[i].startsWith("Dr.", ignoreCase = true)) {
                val nextLine = lines[i + 1].trim()
                if (nextLine.length in 3..60 && nextLine == nextLine.uppercase() && nextLine.any { it.isLetter() }) {
                    return nextLine
                }
            }
        }
        return null
    }

    /**
     * Extract clinic address (heuristic: text after "Address:" or line with pin code).
     */
    internal fun extractClinicAddress(text: String): String? {
        // Look for "Address:" prefix.
        val prefixMatch = ADDRESS_PREFIX_REGEX.find(text)
        prefixMatch?.groupValues?.getOrNull(1)?.trim()?.let { return it }
        // Fallback: line containing a 6-digit pin code (Indian postal).
        val pinCodeMatch = PIN_CODE_REGEX.find(text)
        return pinCodeMatch?.groupValues?.getOrNull(1)?.trim()
    }

    /**
     * Extract patient name from fields (preferred) or text.
     */
    internal fun extractPatientName(fields: List<OcrField>, text: String): String? {
        fields.firstOrNull { it.type == OcrFieldType.PERSON_NAME }?.let { return it.value }
        // Fallback: "Patient: X" or "Name: X".
        val match = PATIENT_NAME_REGEX.find(text)
        return match?.groupValues?.getOrNull(1)?.trim()
    }

    /**
     * Extract patient age (e.g., "Age: 45" or "45/M").
     */
    internal fun extractPatientAge(text: String): String? {
        val match = AGE_KEYWORD_REGEX.find(text)
            ?: AGE_GENDER_SLASH_REGEX.find(text)
        return match?.groupValues?.getOrNull(1)?.trim()
    }

    /**
     * Extract patient gender ("M", "F", "Male", "Female").
     */
    internal fun extractPatientGender(text: String): String? {
        val match = GENDER_REGEX.find(text)
        return match?.value?.let {
            when (it.uppercase()) {
                "M", "MALE" -> "Male"
                "F", "FEMALE" -> "Female"
                else -> null
            }
        }
    }

    /**
     * Extract prescription date from fields (preferred) or text.
     */
    internal fun extractPrescriptionDate(fields: List<OcrField>, text: String): String? {
        fields.firstOrNull { it.type == OcrFieldType.DATE }?.let { return it.value }
        // Fallback: "Date: DD/MM/YYYY".
        val match = DATE_PREFIX_REGEX.find(text)
        return match?.groupValues?.getOrNull(1)?.trim()
    }

    /**
     * Extract medicines from text + blocks.
     *
     * Algorithm:
     *  1. Find all known medicine names (lookup against COMMON_MEDICINES).
     *  2. For each medicine, search the next 50 characters for:
     *     - Dosage (e.g., "500mg", "250 mg")
     *     - Frequency (e.g., "BD", "1-0-1", "twice daily")
     *     - Duration (e.g., "for 5 days", "x7 days")
     *     - Instructions (e.g., "after meals", "before food")
     */
    internal fun extractMedicines(text: String, blocks: List<OcrBlock>): List<PrescriptionMedicine> {
        val medicines = mutableListOf<PrescriptionMedicine>()
        // Find all medicine name occurrences using pre-compiled Regex map.
        for ((medicineName, regex) in COMMON_MEDICINE_REGEXES) {
            regex.findAll(text).forEach { match ->
                val startPos = match.range.first
                val endPos = match.range.last
                // Look in the next 100 characters for dosage + frequency + duration + instructions.
                val contextEnd = (endPos + 100).coerceAtMost(text.length)
                val context = text.substring(startPos, contextEnd)
                val dosage = extractDosage(context)
                val frequency = extractFrequency(context)
                val duration = extractDuration(context)
                val instructions = extractInstructions(context)
                medicines.add(PrescriptionMedicine(
                    name = medicineName,
                    dosage = dosage,
                    frequency = frequency,
                    duration = duration,
                    instructions = instructions,
                ))
            }
        }
        return medicines.distinctBy { it.name + (it.dosage ?: "") }
    }

    /** Extract dosage from context (e.g., "500mg", "250 mg", "2 tablets"). */
    internal fun extractDosage(context: String): String? {
        val match = DOSAGE_REGEX.find(context)
        return match?.value?.trim()
    }

    /** Extract frequency from context (e.g., "BD", "1-0-1", "twice daily"). */
    internal fun extractFrequency(context: String): String? {
        // Indian Latin abbreviations.
        val abbrevMatch = FREQ_ABBREV_REGEX.find(context)
        if (abbrevMatch != null) return abbrevMatch.value
        // Numeric pattern (1-0-1, 1-1-1, 0-0-1).
        val numericMatch = FREQ_NUMERIC_REGEX.find(context)
        if (numericMatch != null) return numericMatch.value
        // English phrases.
        val phraseMatch = FREQ_PHRASE_REGEX.find(context)
        return phraseMatch?.value?.trim()
    }

    /** Extract duration from context (e.g., "for 5 days", "x7 days"). */
    internal fun extractDuration(context: String): String? {
        val match = DURATION_FOR_REGEX.find(context)
            ?: DURATION_X_REGEX.find(context)
        return match?.value?.trim()
    }

    /** Extract instructions from context (e.g., "after meals", "before food"). */
    internal fun extractInstructions(context: String): String? {
        val match = INSTRUCTIONS_REGEX.find(context)
        return match?.value?.trim()
    }

    /** Extract diagnosis (text after "Diagnosis:" or "Advice:"). */
    internal fun extractDiagnosis(text: String): String? {
        val match = DIAGNOSIS_PREFIX_REGEX.find(text)
        return match?.groupValues?.getOrNull(1)?.trim()
    }

    /** Extract follow-up date (e.g., "Review after 7 days", "Follow-up: 15/07/2026"). */
    internal fun extractFollowUpDate(text: String): String? {
        // "Review after X days"
        val reviewMatch = FOLLOW_UP_REVIEW_REGEX.find(text)
        if (reviewMatch != null) return reviewMatch.value.trim()
        // "Follow-up: DATE"
        val followUpMatch = FOLLOW_UP_PREFIX_REGEX.find(text)
        return followUpMatch?.groupValues?.getOrNull(1)?.trim()
    }

    private companion object {
        private const val TAG = "PrescriptionScanner"

        // Performance Optimization: Pre-compile Regex patterns in companion object to eliminate repeated Regex pattern compilation and reduce GC pressure during OCR prescription scanning.
        private val DOCTOR_NAME_REGEX = Regex("Dr\\.?\\s+([A-Z][a-z]+(?:\\s+[A-Z][a-z]+){0,3})")
        private val DOCTOR_QUALIFICATION_REGEX = Regex("\\b(MBBS|MD|MS|DM|MCh|BAMS|BHMS|BDS)\\b")
        private val CLINIC_PREFIX_REGEX = Regex(
            "(?:Clinic|Hospital|Centre|Center)[:\\s]+(.+?)(?:\\n|$)",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
        )
        private val ADDRESS_PREFIX_REGEX = Regex(
            "Address[:\\s]+(.+?)(?:\\n|$)",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
        )
        private val PIN_CODE_REGEX = Regex("(.+?\\b\\d{6}\\b)")
        private val PATIENT_NAME_REGEX = Regex("(?:Patient|Name)[:\\s]+([A-Z][a-z]+(?:\\s+[A-Z][a-z]+){0,3})")
        private val AGE_KEYWORD_REGEX = Regex("\\bAge[:\\s]+(\\d{1,3})\\s*(?:years|yrs|y)?\\b", RegexOption.IGNORE_CASE)
        private val AGE_GENDER_SLASH_REGEX = Regex("\\b(\\d{1,3})\\s*[/\\-]\\s*[MFmf]\\b")
        private val GENDER_REGEX = Regex("\\b(Male|Female|M|F)\\b")
        private val DATE_PREFIX_REGEX = Regex("\\bDate[:\\s]+(\\d{1,2}[/\\-]\\d{1,2}[/\\-]\\d{2,4})\\b", RegexOption.IGNORE_CASE)
        private val DOSAGE_REGEX = Regex("\\b(\\d{1,4})\\s*(mg|ml|mcg|tablets?|capsules?|drops?|teaspoons?)\\b", RegexOption.IGNORE_CASE)
        private val FREQ_ABBREV_REGEX = Regex("\\b(OD|BD|TDS|TID|QID|HS|SOS|QD|BID)\\b")
        private val FREQ_NUMERIC_REGEX = Regex("\\b([0-2]-[0-2]-[0-2])\\b")
        private val FREQ_PHRASE_REGEX = Regex("\\b(once daily|twice daily|thrice daily|every\\s+\\d+\\s+hours?|as needed|at bedtime)\\b", RegexOption.IGNORE_CASE)
        private val DURATION_FOR_REGEX = Regex("\\b(?:for\\s+)?(\\d+)\\s*(days?|weeks?|months?)\\b", RegexOption.IGNORE_CASE)
        private val DURATION_X_REGEX = Regex("\\bx\\s*(\\d+)\\s*(days?|weeks?)\\b", RegexOption.IGNORE_CASE)
        private val INSTRUCTIONS_REGEX = Regex("\\b(after meals?|before meals?|before food|after food|with food|empty stomach|on empty stomach)\\b", RegexOption.IGNORE_CASE)
        private val DIAGNOSIS_PREFIX_REGEX = Regex(
            "(?:Diagnosis|Advice|Provisional Diagnosis)[:\\s]+(.+?)(?:\\n|$)",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
        )
        private val FOLLOW_UP_REVIEW_REGEX = Regex("Review\\s+(?:after|in)\\s+(\\d+)\\s*(?:days?|weeks?)", RegexOption.IGNORE_CASE)
        private val FOLLOW_UP_PREFIX_REGEX = Regex("Follow[-\\s]?up[:\\s]+(\\d{1,2}[/\\-]\\d{1,2}[/\\-]\\d{2,4})", RegexOption.IGNORE_CASE)

        // Subset of common medicines for parsing (full list in OcrFieldExtractor).
        val COMMON_MEDICINES_FOR_PARSING = listOf(
            "Paracetamol", "Crocin", "Dolo", "Calpol", "Combiflam", "Voveran", "Brufen", "Ibuprofen",
            "Amoxicillin", "Azithromycin", "Ciprofloxacin", "Cefixime", "Augmentin", "Levofloxacin",
            "Pantop", "Pantoprazole", "Omeprazole", "Ranitidine", "Rantac",
            "Metformin", "Glimepiride", "Glycomet", "Amaryl",
            "Amlodipine", "Atenolol", "Losartan", "Telmisartan",
            "Shelcal", "Calcium", "Becosules", "Neurobion", "Limcee",
            "Cetirizine", "Montair", "Allegra",
            "Zincovit", "Evion",
        )

        private val COMMON_MEDICINE_REGEXES: Map<String, Regex> = COMMON_MEDICINES_FOR_PARSING.associateWith { medicineName ->
            Regex("\\b${Regex.escape(medicineName)}\\b", RegexOption.IGNORE_CASE)
        }
    }
}
