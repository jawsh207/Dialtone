// JNI bridge between Dialtone (app.dialtone.phone.transcription.MoonshineEngine) and
// transcribe.cpp. One session per engine instance; calls are serialized by the Kotlin side.

#include <jni.h>

#include <cmath>
#include <cstring>
#include <string>
#include <vector>

#include "transcribe.h"

#ifdef __ANDROID__
#include <android/log.h>
#define LOG_TAG "DialtoneTranscriber"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#else
#include <cstdio>
#define LOGI(...) (fprintf(stderr, __VA_ARGS__), fputc('\n', stderr))
#define LOGE(...) (fprintf(stderr, __VA_ARGS__), fputc('\n', stderr))
#endif

namespace {

std::string g_last_error;

void set_error(const char * where, transcribe_status st) {
    g_last_error = std::string(where) + ": " + transcribe_status_string(st);
    LOGE("%s", g_last_error.c_str());
}

jstring to_jstring(JNIEnv * env, const char * utf8) {
    // NewStringUTF expects modified UTF-8; go through a byte array to keep emoji and
    // other supplementary characters intact.
    if (utf8 == nullptr) return env->NewStringUTF("");
    const jsize len = static_cast<jsize>(strlen(utf8));
    jbyteArray bytes = env->NewByteArray(len);
    env->SetByteArrayRegion(bytes, 0, len, reinterpret_cast<const jbyte *>(utf8));
    jclass string_class = env->FindClass("java/lang/String");
    jmethodID ctor = env->GetMethodID(string_class, "<init>", "([BLjava/lang/String;)V");
    jstring charset = env->NewStringUTF("UTF-8");
    auto result = static_cast<jstring>(env->NewObject(string_class, ctor, bytes, charset));
    env->DeleteLocalRef(bytes);
    env->DeleteLocalRef(charset);
    env->DeleteLocalRef(string_class);
    return result;
}

}  // namespace

extern "C" {

// Returns a session handle, or 0 on failure (see nativeLastError).
JNIEXPORT jlong JNICALL
Java_app_dialtone_phone_transcription_MoonshineEngine_nativeOpen(
        JNIEnv * env, jclass, jstring model_path, jint n_threads) {
    const char * path = env->GetStringUTFChars(model_path, nullptr);

    transcribe_session_params session_params;
    transcribe_session_params_init(&session_params);
    session_params.n_threads = n_threads;

    transcribe_session * session = nullptr;
    transcribe_status st = transcribe_open(path, nullptr, &session_params, &session);
    env->ReleaseStringUTFChars(model_path, path);
    if (st != TRANSCRIBE_OK) {
        set_error("open", st);
        return 0;
    }
    LOGI("model loaded (transcribe.cpp %s)", transcribe_version());
    return reinterpret_cast<jlong>(session);
}

// Longest single input the session accepts, in ms; 0 = no practical limit.
JNIEXPORT jlong JNICALL
Java_app_dialtone_phone_transcription_MoonshineEngine_nativeMaxAudioMs(
        JNIEnv *, jclass, jlong handle) {
    auto * session = reinterpret_cast<transcribe_session *>(handle);
    if (session == nullptr) return 0;
    transcribe_session_limits limits;
    transcribe_session_limits_init(&limits);
    if (transcribe_session_get_limits(session, &limits) != TRANSCRIBE_OK) return 0;
    return static_cast<jlong>(limits.effective_max_audio_ms);
}

// pcm: mono float32 at 16 kHz in [-1, 1]. Returns the transcript, or null on failure.
JNIEXPORT jstring JNICALL
Java_app_dialtone_phone_transcription_MoonshineEngine_nativeTranscribe(
        JNIEnv * env, jclass, jlong handle, jfloatArray pcm) {
    auto * session = reinterpret_cast<transcribe_session *>(handle);
    if (session == nullptr || pcm == nullptr) return nullptr;
    const jsize n = env->GetArrayLength(pcm);
    if (n <= 0) return to_jstring(env, "");

    std::vector<float> samples(static_cast<size_t>(n));
    env->GetFloatArrayRegion(pcm, 0, n, samples.data());
    for (float & s : samples) {  // the library rejects NaN/Inf
        if (!std::isfinite(s)) s = 0.0f;
    }

    transcribe_status st = transcribe_run(session, samples.data(), n, nullptr);
    if (st != TRANSCRIBE_OK) {
        set_error("run", st);
        return nullptr;
    }
    return to_jstring(env, transcribe_full_text(session));
}

JNIEXPORT void JNICALL
Java_app_dialtone_phone_transcription_MoonshineEngine_nativeClose(
        JNIEnv *, jclass, jlong handle) {
    auto * session = reinterpret_cast<transcribe_session *>(handle);
    if (session != nullptr) transcribe_session_free(session);
}

JNIEXPORT jstring JNICALL
Java_app_dialtone_phone_transcription_MoonshineEngine_nativeLastError(JNIEnv * env, jclass) {
    return to_jstring(env, g_last_error.c_str());
}

}  // extern "C"
