## 2026-03-31 - Re-compiling Regex and Looking Up MessageDigest Per Token in Embedding Hot Path

**Learning:** TF-IDF fallback hash embedders tokenized string inputs by instantiating `Regex("\\W+")` on every embed call and looking up `MessageDigest.getInstance("SHA-256")` via reflection inside the token loop.
**Action:** Always pre-compile static tokenization regex patterns in a `companion object` and obtain a single `MessageDigest` instance per `embed()` execution pass, resetting it with `digest.reset()` between tokens.
