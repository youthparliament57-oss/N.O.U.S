## 2026-09-22 - Pre-compiled Regex patterns in PrescriptionScanner
**Learning:** Instantiating `Regex` instances dynamically inside methods (and especially loops) creates significant object allocation churn and repeated regex parsing/compilation overhead during OCR parsing tasks. Pre-compiling static Regex patterns and storing them in companion object or map structures eliminates compilation latency.
**Action:** When performing string pattern matching in frequently called scanning or parsing utilities, always define Regex instances as pre-compiled `private val` in the `companion object`.
