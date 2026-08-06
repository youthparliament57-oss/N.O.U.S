# :native:whisper consumer ProGuard rules.
# No Kotlin classes are exposed by this module — it only ships a native .so
# loaded via System.loadLibrary("jarvis_whisper") in :feature:voice's
# WhisperJni.kt. Nothing to keep here.
