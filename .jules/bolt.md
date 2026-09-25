## 2026-03-28 - Precompiled Regex Instances in OCR Parsers
**Learning:** Instantiating Kotlin `Regex` objects inside frequently called parsing functions (such as `extractMedicines` or `extractDoctorName`) causes repeated regex string compilation and excessive temporary heap allocations per scanned document.
**Action:** Always extract and pre-compile regular expressions into static companion objects (`companion object`) or top-level constants when writing text parsing utilities or OCR scanners.
