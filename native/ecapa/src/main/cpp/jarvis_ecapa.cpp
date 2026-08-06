// =============================================================================
// NOUS — ECAPA-TDNN Speaker Verification JNI Bridge (C++ side).
//
// Implements native methods declared in
// com/roshan/persona/ecapa/EcapaJni.kt.
//
// ## Stub Mode (ECAPA_STUB_MODE)
//   When ECAPA-TDNN source is not available (dev builds), all methods return
//   safe defaults: empty embeddings, false for verification, 0 for scores.
//   The Kotlin side checks these and falls back gracefully.
// =============================================================================

#include <jni.h>
#include <android/log.h>
#include <cstring>
#include <vector>

#define LOG_TAG "jarvis_ecapa"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#ifdef ECAPA_STUB_MODE
#warning "Building ECAPA-TDNN in stub mode — native functions will return defaults"
#endif

// =============================================================================
// JNI method implementations
// =============================================================================

extern "C" JNIEXPORT jlong JNICALL
Java_com_roshan_persona_ecapa_EcapaJni_nativeInitModel(
    JNIEnv* env, jobject /*thiz*/,
    jstring jmodel_path) {

#ifdef ECAPA_STUB_MODE
    (void)env;
    (void)jmodel_path;
    LOGI("nativeInitModel called (stub mode)");
    return 0;  // Stub — no model handle
#else
    // Real impl: Load ECAPA-TDNN model from path
    // ecapa_model* model = ecapa_load_model(model_path);
    // return reinterpret_cast<jlong>(model);
    return 0;
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_com_roshan_persona_ecapa_EcapaJni_nativeFreeModel(
    JNIEnv* /*env*/, jobject /*thiz*/, jlong model_handle) {

#ifdef ECAPA_STUB_MODE
    (void)model_handle;
    LOGI("nativeFreeModel called (stub mode)");
#else
    // if (model_handle != 0) {
    //     ecapa_model* model = reinterpret_cast<ecapa_model*>(model_handle);
    //     ecapa_free_model(model);
    // }
#endif
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_roshan_persona_ecapa_EcapaJni_nativeEmbed(
    JNIEnv* env, jobject /*thiz*/,
    jlong model_handle, jshortArray j_audio_samples,
    jint sample_rate, jint num_channels) {

#ifdef ECAPA_STUB_MODE
    (void)model_handle;
    (void)j_audio_samples;
    (void)sample_rate;
    (void)num_channels;
    
    // Return empty embedding (192-dim is standard for ECAPA-TDNN)
    jfloatArray result = env->NewFloatArray(0);
    return result ? result : env->NewFloatArray(0);
#else
    // Real impl:
    // 1. Convert audio to float
    // 2. Run through ECAPA encoder
    // 3. Return embedding vector (typically 192 floats)
    return env->NewFloatArray(0);
#endif
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_roshan_persona_ecapa_EcapaJni_nativeVerify(
    JNIEnv* env, jobject /*thiz*/,
    jlong model_handle,
    jfloatArray j_embedding1, jfloatArray j_embedding2,
    jdouble threshold) {

#ifdef ECAPA_STUB_MODE
    (void)model_handle;
    (void)j_embedding1;
    (void)j_embedding2;
    (void)threshold;
    
    // Return score below any reasonable threshold (not a match)
    return 0.0f;
#else
    // Real impl:
    // 1. Extract embeddings if not provided
    // 2. Compute cosine similarity
    // 3. Return similarity score (0.0 to 1.0)
    return 0.0f;
#endif
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_roshan_persona_ecapa_EcapaJni_nativeEnroll(
    JNIEnv* env, jobject /*thiz*/,
    jlong model_handle, jstring j_speaker_id,
    jfloatArray j_embedding) {

#ifdef ECAPA_STUB_MODE
    (void)model_handle;
    (void)j_speaker_id;
    (void)j_embedding;
    
    return JNI_FALSE;  // Stub — enrollment not available
#else
    // Real impl: Store embedding for speaker_id in internal database
    return JNI_FALSE;
#endif
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_roshan_persona_ecapa_EcapaJni_nativeIsAvailable(
    JNIEnv* /*env*/, jobject /*thiz*/) {
#ifdef ECAPA_STUB_MODE
    return JNI_FALSE;  // Stub mode — not really available
#else
    return JNI_TRUE;
#endif
}
