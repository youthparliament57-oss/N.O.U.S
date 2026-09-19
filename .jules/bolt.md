# Bolt's Journal

## 2026-03-02 - Pre-compiled Regexes in PrescriptionScanner
**Learning:** Instantiating `Regex` instances inside parsing functions and loops (e.g., iterating through 36+ common medicine names during OCR prescription scanning) causes repeated regex pattern compilation and allocation overhead on every scan call.
**Action:** Always pre-compile invariant regular expressions into `companion object` constants when performing document parsing or string pattern matching.
