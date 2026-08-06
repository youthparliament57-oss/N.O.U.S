// =============================================================================
// NOUS — Wake Word Detection JNI Bridge (C++ side).
//
// Implements native methods declared in
// com/roshan/persona/wakeword/WakewordJni.kt.
//
// ## Stub Mode (WAKEWORD_STUB_MODE)
//   When wake word model source is not available (dev builds), all methods return
//   safe defaults: false for detection, 0 for confidence. The Kotlin side
//   checks these and falls back gracefully.
// =============================================================================

#include <jni.h>
#include <android/log.h>
#include <cstring>

#define LOG_TAG "jarvis_wakeword"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#ifdef WAKEWORD_STUB_MODE
#warning "Building Wake Word detector in stub mode — native functions will return defaults"
#endif

// ─── Global state ────────────────────────────────────────────────────────────

static bool g_initialized = false;

// =============================================================================
// JNI method implementations
// =============================================================================

extern "C" JNIEXPORT jboolean JNICALL
Java_com_roshan_persona_wakeword_WakewordJni_nativeInitDetector(
    JNIEnv* env, jobject /*thiz*/,
    jstring jmodel_path, jint sample_rate) {

#ifdef WAKEWORD_STUB_MODE
    (void)env;
    (void)jmodel_path;
    (void)sample_rate;
    LOGI("nativeInitDetector called (stub mode)");
    g_initialized = true;
    return JNI_TRUE;  // Report success but detection won't work
#else
    // Real impl: Initialize wake word detector with model
    // detector = wakeword_detector_create(model_path, sample_rate);
    // g_initialized = (detector != nullptr);
    // return g_initialized ? JNI_TRUE : JNI_FALSE;
    return JNI_FALSE;
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_com_roshan_persona_wakeword_WakewordJni_nativeDestroyDetector(
    JNIEnv* /*env*/, jobject /*thiz*/) {
    g_initialized = false;
#ifndef WAKEWORD_STUB_MODE
    // wakeword_detector_destroy(detector);
#endif
    LOGI("nativeDestroyDetector called");
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_roshan_persona_wakeword_WakewordJni_nativeProcessAudio(
    JNIEnv* env, jobject /*thiz*/,
    jshortArray j_audio_samples, jint sample_rate) {

#ifdef WAKEWORD_STUB_MODE
    (void)sample_rate;
    
    // Return array with single value: 0.0 confidence (no detection)
    jfloatArray result = env->NewFloatArray(1);
    if (result) {
        float confidence = 0.0f;
        env->SetFloatArrayRegion(result, 0, 1, &confidence);
    }
    return result;
#else
    // Real impl:
    // 1. Feed audio samples to detector
    // 2. Return confidence scores for each frame/window
    // 3. High confidence (> threshold) indicates wake word detected
    return env->NewFloatArray(0);
#endif
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_roshan_persona_wakeword_WakewordJni_nativeDetect(
    JNIEnv* env, jobject /*thiz*/,
    jshortArray j_audio_samples, jint sample_rate,
    jfloat threshold) {

#ifdef WAKEWORD_STUB_MODE
    (void)env;
    (void)j_audio_samples;
    (void)sample_rate;
    (void)threshold;
    
    // Never detect in stub mode
    return JNI_FALSE;
#else
    // Real impl: Process audio and check if wake word detected
    // float confidence = wakeword_detect(detector, audio, num_samples);
    // return (confidence >= threshold) ? JNI_TRUE : JNI_FALSE;
    return JNI_FALSE;
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_com_roshan_persona_wakeword_WakewordJni_nativeReset(
    JNIEnv* /*env*/, jobject /*thiz*/) {
#ifndef WAKEWORD_STUB_MODE
    // Reset internal state (clear buffers, etc.)
#endif
    LOGI("nativeReset called");
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_roshan_persona_wakeword_WakewordJni_nativeIsAvailable(
    JNIEnv* /*env*/, jobject /*thiz*/) {
#ifdef WAKEWORD_STUB_MODE
    return JNI_FALSE;  // Stub mode — not really available
#else
    return g_initialized ? JNI_TRUE : JNI_FALSE;
#endif
}

extern "C" JNIEXPORT jint JNICALL
Java_com_roshan_persona_wakeword_WakewordJni_nativeGetRequiredSampleRate(
    JNIEnv* /*env*/, jobject /*thiz*/) {
    // Standard sample rate for most wake word models (16kHz)
    return 16000;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_roshan_persona_wakeword_WakewordJni_nativeGetRequiredFrameSize(
    JNIEnv* /*env*/, jobject /*thiz*/) {
    // Typical frame size for wake word processing (10ms at 16kHz = 160 samples)
    return 160;
}
