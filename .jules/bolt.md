## 2026-08-30 - Pre-compiled Regex in Hot Path Embedding Logic
**Learning:** Instantiating `Regex("\\W+")` dynamically inside hot paths like `TfIdfHashEmbedder.embed()` forces pattern compilation on every call, creating unnecessary CPU overhead and allocation churn during vector embedding generation.
**Action:** Always pre-compile regular expressions in static fields or companion objects when used in frequently executed utility methods or loops.
