## 2026-08-20 - Fast-Path Bypasses for Regex Heavy Scrubbers
**Learning:** Hot-path components like `PiiScrubber` evaluate multiple heavy regular expressions on every logging call. Adding light O(N) pre-checks for required character triggers (`hasDigits`, `hasAt`, `hasBearer`, `hasApiKey`) allows non-PII log messages to bypass regex matching completely with zero allocations.
**Action:** Always place quick character or substring pre-checks before executing multi-regex pipelines on hot string processing paths.
