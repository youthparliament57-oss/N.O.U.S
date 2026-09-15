## 2026-03-30 - Pre-compile Regex in Hot OCR Parsing Loops
**Learning:** Re-instantiating `Regex(...)` within hot OCR parsing loops (such as matching ~35 medicine names against prescription text) causes repeated regex pattern compilation and excessive short-lived heap allocations on Android.
**Action:** Always pre-compile `Regex` instances into `companion object` constants or pre-mapped collections when regex matching runs repeatedly or inside loops.
