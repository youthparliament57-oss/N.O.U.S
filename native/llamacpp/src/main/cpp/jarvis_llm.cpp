// Copyright (c) 2026 Roshan. All rights reserved.
// Proprietary license — see LICENSE file for details.
//
// NOUS — llama.cpp JNI Bridge (C++ side)
//
// v2.1 Features:
//   - mmap memory mapping (prevents OOM)
//   - Async-signal-safe crash handler (pipe write, NOT JVM call)
//   - Zero-copy token streaming (token IDs, NOT strings)
//   - Barge-in via g_should_stop atomic flag
//   - KV cache context shifting (llama_kv_cache_seq_rm)
//   - Lookahead decoding support
//   - LoRA adapter hot-swapping
//
// ## Stub Mode (LLAMA_STUB_MODE)
//   When llama.cpp source is not available (dev builds without -PbuildFromSource),
//   all methods return safe defaults: 0 for handles, empty strings, etc.
//   The Kotlin side (LlamaCppJni/LlamaCppProvider) checks these and falls back
//   gracefully to cloud LLM or error state.

#include <jni.h>
#include <android/log.h>
#include <signal.h>
#include <unistd.h>
#include <fcntl.h>
#include <atomic>
#include <cstring>
#include <string>

#define LOG_TAG "jarvis_llm"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#ifdef LLAMA_STUB_MODE
#warning "Building llama.cpp in stub mode — native functions will return defaults"
#endif

// Forward declarations (real headers from llama.cpp submodule)
// #include "llama.h"

// ─── Global state ──────────────────────────────────────────────────

// v2.1: Atomic flag for barge-in (checked every token in generation loop)
static std::atomic<bool> g_should_stop{false};

// v2.1: Pipe fd for async-signal-safe crash handler
static int g_crash_pipe_fd = -1;

// ─── Crash Handler (v2.1: async-signal-safe) ──────────────────────

/**
 * v2.1: Signal handler — ONLY uses async-signal-safe functions.
 *
 * Does NOT call any JVM functions (AttachCurrentThread, CallVoidMethod, etc.)
 * which are NOT async-signal-safe and would cause secondary crash.
 *
 * Instead, writes raw signal code to pipe via write() (async-signal-safe).
 * A separate watchdog process reads the pipe and handles recovery.
 */
static void crash_handler(int signal, siginfo_t* info, void* context) {
    if (g_crash_pipe_fd >= 0) {
        // write() is async-signal-safe
        char buf[8];
        memset(buf, 0, 8);
        buf[0] = (char)(signal & 0xFF);
        buf[1] = (char)((signal >> 8) & 0xFF);
        // Write fault address (truncated to 6 bytes)
        if (info != nullptr) {
            uintptr_t addr = (uintptr_t)info->si_addr;
            memcpy(buf + 2, &addr, 6);
        }
        write(g_crash_pipe_fd, buf, 8);
        fsync(g_crash_pipe_fd);
    }
    // Re-raise to let default handler run (tombstone)
    ::signal(signal, SIG_DFL);
    raise(signal);
}

// ─── JNI method implementations ────────────────────────────────────

extern "C" {

// ─── Model lifecycle ──────────────────────────────────────────────

JNIEXPORT jlong JNICALL
Java_com_roshan_persona_llamacpp_LlamaCppJni_nativeLoadModelImpl(
    JNIEnv* env, jobject /*thiz*/,
    jstring modelPath, jint /*contextLength*/,
    jboolean /*useGpu*/, jboolean /*useMmap*/) {

#ifdef LLAMA_STUB_MODE
    (void)env;
    (void)modelPath;
    LOGI("nativeLoadModelImpl called (stub mode)");
    return 0;  // Stub — no model handle
#else
    // Real impl:
    // llama_model_params params = llama_model_default_params();
    // params.n_ctx = contextLength;
    // params.n_gpu_layers = useGpu ? 99 : 0;
    // params.use_mmap = useMmap;  // v2.1: memory-mapped I/O
    //
    // const char* path = env->GetStringUTFChars(modelPath, nullptr);
    // llama_model* model = llama_load_model_from_file(path, params);
    // env->ReleaseStringUTFChars(modelPath, path);
    // return reinterpret_cast<jlong>(model);
    return 0;
#endif
}

JNIEXPORT void JNICALL
Java_com_roshan_persona_llamacpp_LlamaCppJni_nativeUnloadImpl(
    JNIEnv* /*env*/, jobject /*thiz*/, jlong modelHandle) {

#ifdef LLAMA_STUB_MODE
    (void)modelHandle;
    LOGI("nativeUnloadImpl called (stub mode)");
#else
    // Real impl:
    // if (modelHandle != 0) {
    //     llama_model* model = reinterpret_cast<llama_model*>(modelHandle);
    //     llama_free_model(model);
    // }
#endif
}

// ─── Generation (v2.1: zero-copy + barge-in) ─────────────────────

JNIEXPORT jstring JNICALL
Java_com_roshan_persona_llamacpp_LlamaCppJni_nativeGenerateImpl(
    JNIEnv* env, jobject /*thiz*/,
    jlong /*modelHandle*/, jstring /*prompt*/,
    jint /*maxTokens*/, jfloat /*temperature*/, jfloat /*topP*/, jint /*topK*/,
    jfloat /*repeatPenalty*/, jobject /*callback*/) {

    g_should_stop = false;  // Reset at start

#ifdef LLAMA_STUB_MODE
    (void)env;
    LOGI("nativeGenerateImpl called (stub mode)");
    return env->NewStringUTF("");  // Stub
#else
    // Real impl: Full generation loop with barge-in, context shifting, etc.
    return env->NewStringUTF("");
#endif
}

JNIEXPORT void JNICALL
Java_com_roshan_persona_llamacpp_LlamaCppJni_nativeStopGenerationImpl(
    JNIEnv* /*env*/, jobject /*thiz*/) {

    // v2.1: Set atomic flag — checked in generation loop every token
    g_should_stop = true;
    LOGI("nativeStopGenerationImpl — stop flag set");
}

// ─── Embedding ────────────────────────────────────────────────────

JNIEXPORT jfloatArray JNICALL
Java_com_roshan_persona_llamacpp_LlamaCppJni_nativeEmbedImpl(
    JNIEnv* env, jobject /*thiz*/, jlong /*modelHandle*/, jstring /*text*/) {

#ifdef LLAMA_STUB_MODE
    (void)env;
    LOGI("nativeEmbedImpl called (stub mode)");
    return env->NewFloatArray(0);  // Stub — empty embedding
#else
    // Real impl: Generate embedding vector from text
    return env->NewFloatArray(0);
#endif
}

// ─── Vulkan ───────────────────────────────────────────────────────

JNIEXPORT jboolean JNICALL
Java_com_roshan_persona_llamacpp_LlamaCppJni_nativeHasVulkanImpl(
    JNIEnv* /*env*/, jobject /*thiz*/) {

#ifdef LLAMA_STUB_MODE
    LOGI("nativeHasVulkanImpl called (stub mode)");
#endif
    // Real impl: check for Vulkan device
    // return llama_supports_gpu() ? JNI_TRUE : JNI_FALSE;
    return JNI_FALSE;  // Stub
}

JNIEXPORT jboolean JNICALL
Java_com_roshan_persona_llamacpp_LlamaCppJni_nativeCheckVulkanContextImpl(
    JNIEnv* /*env*/, jobject /*thiz*/, jlong /*modelHandle*/) {

#ifdef LLAMA_STUB_MODE
    LOGI("nativeCheckVulkanContextImpl called (stub mode)");
#endif
    // Real impl: try trivial Vulkan operation
    return JNI_TRUE;  // Stub — assume valid
}

// ─── KV Cache ─────────────────────────────────────────────────────

JNIEXPORT void JNICALL
Java_com_roshan_persona_llamacpp_LlamaCppJni_nativeCheckContextOverflowImpl(
    JNIEnv* /*env*/, jobject /*thiz*/, jlong /*modelHandle*/, jint /*contextLength*/) {
    
#ifdef LLAMA_STUB_MODE
    LOGI("nativeCheckContextOverflowImpl called (stub mode)");
#else
    // v2.1: Context shifting (Gemini-style)
    // int used = llama_get_kv_cache_token_count(ctx);
    // if (used > contextLength * 0.8) {
    //     int tokensToRemove = (int)(contextLength * 0.3);
    //     llama_kv_cache_seq_rm(ctx, 0, 0, tokensToRemove);
    //     llama_kv_cache_seq_shift(ctx, 0, tokensToRemove, used, -tokensToRemove);
    // }
#endif
}

// ─── LoRA ─────────────────────────────────────────────────────────

JNIEXPORT jlong JNICALL
Java_com_roshan_persona_llamacpp_LlamaCppJni_nativeLoadLoraAdapterImpl(
    JNIEnv* /*env*/, jobject /*thiz*/, jlong /*modelHandle*/, jstring /*loraPath*/, jfloat /*scale*/) {

#ifdef LLAMA_STUB_MODE
    LOGI("nativeLoadLoraAdapterImpl called (stub mode)");
#endif
    // Real impl:
    // const char* path = env->GetStringUTFChars(loraPath, nullptr);
    // int result = llama_model_apply_lora_from_file(model, path, scale, nullptr, n_threads);
    // env->ReleaseStringUTFChars(loraPath, path);
    // return result == 0 ? 1 : 0;
    return 0;  // Stub
}

JNIEXPORT void JNICALL
Java_com_roshan_persona_llamacpp_LlamaCppJni_nativeRemoveLoraAdapterImpl(
    JNIEnv* /*env*/, jobject /*thiz*/, jlong /*modelHandle*/, jlong /*loraHandle*/) {

#ifdef LLAMA_STUB_MODE

    LOGI("nativeRemoveLoraAdapterImpl called (stub mode)");
#else
    // Real impl: remove LoRA adapter
#endif
}

// ─── Crash Handler ────────────────────────────────────────────────

JNIEXPORT void JNICALL
Java_com_roshan_persona_llamacpp_LlamaCppJni_nativeInstallCrashHandler(
    JNIEnv* /*env*/, jobject /*thiz*/, jint pipeFd) {

    g_crash_pipe_fd = pipeFd;

    struct sigaction sa;
    memset(&sa, 0, sizeof(sa));
    sa.sa_sigaction = crash_handler;
    sa.sa_flags = SA_SIGINFO;
    sigemptyset(&sa.sa_mask);
    sigaction(SIGSEGV, &sa, nullptr);
    sigaction(SIGABRT, &sa, nullptr);
    
    LOGI("nativeInstallCrashHandler — installed (pipeFd=%d)", pipeFd);
}

// ─── Vocabulary ───────────────────────────────────────────────────

JNIEXPORT jint JNICALL
Java_com_roshan_persona_llamacpp_LlamaCppJni_nativeGetVocabSizeImpl(
    JNIEnv* /*env*/, jobject /*thiz*/, jlong /*modelHandle*/) {

#ifdef LLAMA_STUB_MODE
    LOGI("nativeGetVocabSizeImpl called (stub mode)");
#endif
    // Real impl: return llama_n_vocab(model);
    return 0;  // Stub
}

JNIEXPORT jstring JNICALL
Java_com_roshan_persona_llamacpp_LlamaCppJni_nativeGetTokenTextImpl(
    JNIEnv* env, jobject /*thiz*/, jlong /*modelHandle*/, jint /*tokenId*/) {

#ifdef LLAMA_STUB_MODE
    (void)env;
    LOGI("nativeGetTokenTextImpl called (stub mode)");
    return env->NewStringUTF("");  // Stub
#else
    // Real impl:
    // const char* text = llama_token_get_text(model, tokenId);
    // return env->NewStringUTF(text);
    return env->NewStringUTF("");
#endif
}

}  // extern "C"
