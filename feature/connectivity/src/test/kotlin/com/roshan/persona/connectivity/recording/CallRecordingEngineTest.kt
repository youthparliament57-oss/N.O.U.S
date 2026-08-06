// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.connectivity.recording

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test

// CONNECTIVITY_FIX_008: Recording engine resource safe

// ─── Fakes ─────────────────────────────────────────────────────────────────

private class FakeAudioRecorder(
    private val audioData: ByteArray = ByteArray(1024) { it.toByte() },
    private val startResult: Boolean = true,
) : AudioRecorder {
    private var recording = false
    private var startMs = 0L

    override fun startRecording(): Boolean {
        if (recording) return false
        recording = startResult
        startMs = System.currentTimeMillis()
        return startResult
    }

    override fun stopRecording(): ByteArray? {
        if (!recording) return null
        recording = false
        return audioData
    }

    override fun isRecording(): Boolean = recording

    override fun getDurationSeconds(): Long {
        return if (recording) (System.currentTimeMillis() - startMs) / 1000 else 0
    }
}

private class FakeTranscriptionLlm(
    private val transcript: String? = "Hello Mom, how are you?",
    private val ready: Boolean = true,
) : TranscriptionLlm {
    var transcribeCallCount = 0
        private set

    override suspend fun transcribe(audioData: ByteArray): String? {
        transcribeCallCount++
        return transcript
    }

    override fun isReady(): Boolean = ready
}

private class FakeRecordingStore : RecordingStore {
    private val recordings = mutableMapOf<String, CallRecording>()

    override suspend fun store(recording: CallRecording): String {
        recordings[recording.id] = recording
        return recording.id
    }

    override suspend fun get(recordingId: String): CallRecording? = recordings[recordingId]

    override suspend fun searchByTranscript(query: String, limit: Int): List<CallRecording> {
        val lower = query.lowercase()
        return recordings.values
            .filter { it.transcript?.lowercase()?.contains(lower) == true }
            .sortedByDescending { it.timestampMs }
            .take(limit)
    }

    override suspend fun getRecent(limit: Int): List<CallRecording> =
        recordings.values.sortedByDescending { it.timestampMs }.take(limit)

    override suspend fun delete(recordingId: String): Boolean = recordings.remove(recordingId) != null

    override suspend fun deleteAll(): Int {
        val n = recordings.size
        recordings.clear()
        return n
    }

    override suspend fun count(): Int = recordings.size
}

private class FakeAes256Encryptor : Aes256Encryptor(
    keyProvider = { "test_key_32_bytes_long_1234567890".toByteArray().copyOf(32) },
    fileWriter = { data, name -> "/tmp/$name" },
)

// ─── CallRecordingEngine tests ────────────────────────────────────────────

class CallRecordingEngineTest {

    private fun makeEngine(
        recorder: AudioRecorder = FakeAudioRecorder(),
        transcriber: TranscriptionLlm = FakeTranscriptionLlm(),
        store: RecordingStore = FakeRecordingStore(),
    ): CallRecordingEngine {
        return CallRecordingEngine(
            recorder = recorder,
            transcriber = transcriber,
            store = store,
            encryptor = FakeAes256Encryptor(),
            dispatcher = Dispatchers.Unconfined,
        )
    }

    @Test
    fun `startRecording returns true when recorder starts`() {
        val engine = makeEngine()
        assertThat(engine.startRecording("Mom", "+919876543210")).isTrue()
        assertThat(engine.isRecording()).isTrue()
    }

    @Test
    fun `startRecording returns false when already recording`() {
        val engine = makeEngine()
        engine.startRecording("Mom", "+919876543210")
        assertThat(engine.startRecording("Dad", "+919988776655")).isFalse()
    }

    @Test
    fun `startRecording returns false when recorder fails`() {
        val engine = makeEngine(recorder = FakeAudioRecorder(startResult = false))
        assertThat(engine.startRecording("Mom", "+919876543210")).isFalse()
    }

    @Test
    fun `stopRecording returns recording ID when successful`() = runTest {
        val engine = makeEngine()
        engine.startRecording("Mom", "+919876543210")
        val id = engine.stopRecording()
        assertThat(id).isNotNull()
        assertThat(id).startsWith("rec_")
    }

    @Test
    fun `stopRecording returns null when not recording`() = runTest {
        val engine = makeEngine()
        assertThat(engine.stopRecording()).isNull()
    }

    @Test
    fun `stopRecording stores recording with transcript`() = runTest {
        val store = FakeRecordingStore()
        val engine = makeEngine(store = store)
        engine.startRecording("Mom", "+919876543210")
        val id = engine.stopRecording()
        assertThat(id).isNotNull()

        val recording = store.get(id!!)
        assertThat(recording).isNotNull()
        assertThat(recording!!.contactName).isEqualTo("Mom")
        assertThat(recording.phoneNumber).isEqualTo("+919876543210")
        assertThat(recording.isTranscribed).isTrue()
        assertThat(recording.transcript).contains("Hello Mom")
    }

    @Test
    fun `stopRecording stores recording without transcript when LLM not ready`() = runTest {
        val store = FakeRecordingStore()
        val engine = makeEngine(
            store = store,
            transcriber = FakeTranscriptionLlm(ready = false),
        )
        engine.startRecording("Mom", "+919876543210")
        val id = engine.stopRecording()
        assertThat(id).isNotNull()

        val recording = store.get(id!!)
        assertThat(recording!!.isTranscribed).isFalse()
        assertThat(recording.transcript).isNull()
    }

    @Test
    fun `stopRecording returns null when audio data is empty`() = runTest {
        val engine = makeEngine(recorder = FakeAudioRecorder(audioData = ByteArray(0)))
        engine.startRecording("Mom", "+919876543210")
        val id = engine.stopRecording()
        assertThat(id).isNull()
    }

    @Test
    fun `searchTranscripts finds matching recordings`() = runTest {
        val store = FakeRecordingStore()
        val engine = makeEngine(store = store)

        // Store a recording with "trip" in transcript.
        store.store(CallRecording(
            id = "rec_1", contactName = "Mom", phoneNumber = "+91",
            timestampMs = 1000, durationSeconds = 60,
            encryptedAudioPath = "/tmp/rec.enc",
            transcript = "Let's plan the trip to Goa.",
            isTranscribed = true,
        ))
        store.store(CallRecording(
            id = "rec_2", contactName = "Dad", phoneNumber = "+91",
            timestampMs = 2000, durationSeconds = 30,
            encryptedAudioPath = "/tmp/rec2.enc",
            transcript = "How was your day?",
            isTranscribed = true,
        ))

        val results = engine.searchTranscripts("trip")
        assertThat(results).hasSize(1)
        assertThat(results[0].contactName).isEqualTo("Mom")
    }

    @Test
    fun `getRecentRecordings returns most-recent-first`() = runTest {
        val store = FakeRecordingStore()
        val engine = makeEngine(store = store)

        store.store(CallRecording("r1", "A", "+91", 1000, 10, "/tmp/a.enc", "Hi", true))
        store.store(CallRecording("r2", "B", "+91", 2000, 20, "/tmp/b.enc", "Hello", true))

        val recent = engine.getRecentRecordings()
        assertThat(recent[0].id).isEqualTo("r2") // most recent first
        assertThat(recent[1].id).isEqualTo("r1")
    }

    @Test
    fun `getRecording returns recording by ID`() = runTest {
        val store = FakeRecordingStore()
        val engine = makeEngine(store = store)
        store.store(CallRecording("r1", "Mom", "+91", 1000, 10, "/tmp/a.enc", "Hi", true))

        val recording = engine.getRecording("r1")
        assertThat(recording).isNotNull()
        assertThat(recording!!.contactName).isEqualTo("Mom")
    }

    @Test
    fun `getRecording returns null for unknown ID`() = runTest {
        val engine = makeEngine()
        assertThat(engine.getRecording("nonexistent")).isNull()
    }

    @Test
    fun `deleteRecording returns true when deleted`() = runTest {
        val store = FakeRecordingStore()
        val engine = makeEngine(store = store)
        store.store(CallRecording("r1", "Mom", "+91", 1000, 10, "/tmp/a.enc", "Hi", true))

        assertThat(engine.deleteRecording("r1")).isTrue()
        assertThat(engine.getRecording("r1")).isNull()
    }

    @Test
    fun `deleteRecording returns false for unknown ID`() = runTest {
        val engine = makeEngine()
        assertThat(engine.deleteRecording("nonexistent")).isFalse()
    }

    @Test
    fun `deleteAllRecordings removes all recordings`() = runTest {
        val store = FakeRecordingStore()
        val engine = makeEngine(store = store)
        store.store(CallRecording("r1", "A", "+91", 1000, 10, "/tmp/a.enc", "Hi", true))
        store.store(CallRecording("r2", "B", "+91", 2000, 20, "/tmp/b.enc", "Hello", true))

        val deleted = engine.deleteAllRecordings()
        assertThat(deleted).isEqualTo(2)
    }

    @Test
    fun `isRecording returns false when not recording`() {
        val engine = makeEngine()
        assertThat(engine.isRecording()).isFalse()
    }
}

// ─── CallRecording model tests ────────────────────────────────────────────

class CallRecordingModelTest {

    @Test
    fun `CallRecording validates non-blank id`() {
        try {
            CallRecording("", "Mom", "+91", 0, 10, "/tmp/a.enc")
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("id")
        }
    }

    @Test
    fun `CallRecording validates non-blank contactName`() {
        try {
            CallRecording("r1", "", "+91", 0, 10, "/tmp/a.enc")
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("Contact name")
        }
    }

    @Test
    fun `CallRecording validates non-negative duration`() {
        try {
            CallRecording("r1", "Mom", "+91", 0, -1, "/tmp/a.enc")
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("Duration")
        }
    }
}

// ─── Aes256Encryptor tests ─────────────────────────────────────────────────

class Aes256EncryptorTest {

    private val encryptor = FakeAes256Encryptor()

    @Test
    fun `encryptAndSave returns file path`() {
        val path = encryptor.encryptAndSave(ByteArray(100), "rec_test")
        assertThat(path).isEqualTo("/tmp/recording_rec_test.enc")
    }

    @Test
    fun `deriveKey returns 32 bytes (256 bits)`() {
        val key = Aes256Encryptor.deriveKey("device_secret_123")
        assertThat(key.size).isEqualTo(32)
    }

    @Test
    fun `deriveKey is deterministic`() {
        val key1 = Aes256Encryptor.deriveKey("same_secret")
        val key2 = Aes256Encryptor.deriveKey("same_secret")
        assertThat(key1).isEqualTo(key2)
    }

    @Test
    fun `deriveKey differs for different secrets`() {
        val key1 = Aes256Encryptor.deriveKey("secret1")
        val key2 = Aes256Encryptor.deriveKey("secret2")
        assertThat(key1).isNotEqualTo(key2)
    }
}
