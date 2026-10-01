## 2026-03-31 - Pre-quantization & query norm caching for INT8 vector batch search
**Learning:** In vector search engines, performing on-the-fly float-to-int quantization and computing vector norms inside the candidate loop for $N$ candidates scales with $O(N \times d)$ redundant floating point operations and $N$ unnecessary `sqrt` calls.
**Action:** Pre-quantize query vectors and pre-calculate query norms ONCE before scanning candidates to reduce batch similarity scoring overhead.
