# ADR 0010 — LLM Model Onboarding Strategy

Date: 2026-07-04
Status: Accepted
Decision Owner: Roshan

## Context

NOUS supports on-device LLM inference via llama.cpp + Vulkan. The original README mentioned bundling GGUF models, but this has multiple problems:

- **APK size bloat** — even a small Qwen 2.5 0.5B Q4 is ~500MB; bundling makes APK huge
- **Play Store 200MB APK limit** — must use AAB + Asset Packs (complex)
- **Model obsolescence** — models improve monthly; bundled model goes stale
- **User choice** — different users want different models (Qwen, Llama, Phi, Gemma, custom)
- **Storage waste** — user may not even use local LLM (cloud-only users)

## Decision

Adopt **on-demand model download + onboarding flow**:

### Onboarding Flow
1. **First launch** — onboarding screen explains local LLM benefits (offline, private, fast)
2. **Model picker** — user selects from curated catalog:
   - **Recommended**: Qwen 2.5 0.5B Instruct Q4 (~500MB) — fastest, lowest quality
   - **Balanced**: Qwen 2.5 1.5B Instruct Q4 (~1.2GB) — good quality/speed balance
   - **Powerful**: Llama 3.2 3B Instruct Q4 (~2.5GB) — best quality, slower
   - **Skip**: defer setup, use cloud LLM only
3. **Download** — with progress UI, resumable on network failure
4. **Vulkan check** — auto-detect GPU, show expected tokens/sec
5. **First inference test** — short "Hello, I am NOUS" to verify model works
6. **Completion** — user can change model later in Settings

### Custom Model Support
Power users can:
1. **Add custom URL** — paste any HTTP(S) URL to a `.gguf` file
2. **Local file import** — pick a `.gguf` from device storage
3. **Model catalog URL** — Settings → AI Models → Custom Catalog URL → loads JSON list of models

### Model Catalog Format (JSON)
```json
{
  "version": 1,
  "models": [
    {
      "id": "qwen2.5-0.5b-instruct-q4",
      "name": "Qwen 2.5 0.5B Instruct",
      "quantization": "Q4_K_M",
      "sizeBytes": 499981840,
      "downloadUrl": "https://...",
      "sha256": "...",
      "contextLength": 4096,
      "recommendedGpuVramMb": 1024,
      "tags": ["fast", "minimal", "recommended"]
    }
  ]
}
```

### Default Catalog
- Hosted at `https://raw.githubusercontent.com/roshan/nous-models/main/catalog.json`
- Updates pushed independently of app updates (new models as they release)
- User can override catalog URL in Settings

### Verification
- SHA-256 hash check on every downloaded model
- Failed verification → re-download (no half-broken models)
- Model integrity check on every app launch (detect file corruption)

## Consequences

**Positive:**
- APK stays small (~80MB for `prod` flavor)
- User chooses model based on their hardware (low-end phone = 0.5B, flagship = 3B)
- Models update independently of app (latest models available immediately)
- User can add custom/community models (future-proof)
- Cloud-only users skip the download entirely

**Negative:**
- First-run friction (download required before local LLM works)
- Storage management — user must delete old models manually (mitigated by "Manage models" UI)
- Network dependency for first setup (mitigated by skip option → cloud only)

**Mitigation:**
- Skip option for cloud-only users
- Clear UI showing expected tokens/sec per model per device
- Background download with progress
- Resumable downloads (network failures don't restart from 0)

## Alternatives Considered

- **Bundle small model + download larger**: rejected — even 500MB bundle is too much for Play 200MB limit
- **Asset Pack delivery via Play**: rejected — couples app to Play Store, doesn't work for sideload builds
- **Cloud-only (no local LLM)**: rejected — kills offline-first promise
- **User must manually place `.gguf` files**: rejected — too technical for mainstream users
