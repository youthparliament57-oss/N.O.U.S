## 2026-09-18 - Pre-compile Regex Patterns in Hot-Path String Tokenization
**Learning:** Re-instantiating `Regex("\\W+")` on every `embed()` invocation in memory embedding / TF-IDF tokenization causes repeated regex parsing and `java.util.regex.Pattern` object allocations, increasing GC overhead during bulk embedding operations.
**Action:** Extract non-word splitting regex patterns into static compiled companion object properties (`private val NON_WORD_REGEX = Regex("\\W+")`) for zero-allocation regex reuse.
