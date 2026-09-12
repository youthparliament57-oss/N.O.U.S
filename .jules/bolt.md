## 2026-09-12 - [Pre-compile Regex Patterns in OCR Scanners]
**Learning:** Instantiating `Regex` patterns inside hot loops or per-parsing methods in Kotlin (such as OCR document parsing like `PrescriptionScanner`) causes repeated regex pattern compilation and unnecessary heap allocations / GC pressure on Android.
**Action:** Always pre-compile `Regex` instances into `private companion object` properties (and `Map<String, Regex>` for dynamic lists) in document scanners and parsers.
