## 2026-03-30 - Vector Similarity Sqrt & Type Optimization

**Learning:** In vector dot-product and cosine similarity hot loops (such as INT8 search and HNSW graph operations), performing `sqrt(queryNorm) * sqrt(storedNorm)` doubles square root calculations. Combining them into `sqrt(normA * normB)` reduces operations by ~50%. Also, accumulating floats directly avoids implicit double conversion overhead in Kotlin on Android.

**Action:** Look for duplicate `sqrt()` calls and implicit primitive widening (e.g. Float to Double) in mathematical inner loops and optimize them to single-pass arithmetic.
