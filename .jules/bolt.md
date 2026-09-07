## 2026-09-07 - INT8 Vector Cosine Similarity Scale Cancellation

**Learning:** When performing INT8 quantized vector cosine similarity calculations against float32 query vectors, the linear scale factor `scale` cancels out mathematically in the cosine similarity numerator and denominator ratio (`dotProduct / (normA * normB)`). Attempting to re-quantize the query vector inside the inner loop using the candidate's scale factor introduces redundant per-element multiplications, conversions, and clamping calls.

**Action:** Directly calculate dot products between float query dimensions and int8 vector elements without inner-loop re-quantization or float-to-int conversion. Precompute query norm across batch vector candidate evaluations.
