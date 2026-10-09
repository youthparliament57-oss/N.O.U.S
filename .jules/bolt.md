## 2026-10-09 - Avoid Regex Recompilation in Text Processing
**Learning:** Re-instantiating `Regex` instances inside frequently executed functions (such as text tokenization or embedder fallback methods) creates unnecessary object allocation and pattern compilation overhead on every execution.
**Action:** Always extract regex patterns into a `companion object` or top-level `val` to compile the pattern once statically.
