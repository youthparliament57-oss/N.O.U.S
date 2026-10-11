# Bolt's Performance Journal

## 2026-03-31 - Pre-compile Regex instances in OCR/Text Scanners
**Learning:** Instantiating `Regex` instances inside parsing methods or loops (such as iterating over medicine lists in `PrescriptionScanner`) repeatedly recompiles pattern strings on every invocation. This causes heavy CPU overhead and garbage collection pressure on hot OCR document extraction paths.
**Action:** Always pre-compile static `Regex` patterns in `companion object` constants or pre-computed lists/maps in Kotlin text processing components.
