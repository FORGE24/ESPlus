#include "asmlib.h"
#include <jni.h>

extern "C" {

JNIEXPORT jlong JNICALL Java_com_esplus_bridge_NativeBridge_asmRdtsc(JNIEnv*, jclass) {
    return (jlong)asmlib_rdtsc();
}

JNIEXPORT jobject JNICALL Java_com_esplus_bridge_NativeBridge_asmCpuId(JNIEnv* env, jclass, jint leaf) {
    CpuIdInfo info;
    asmlib_cpu_id((uint32_t)leaf, &info);
    jclass cls = env->FindClass("[I");
    jintArray arr = env->NewIntArray(3);
    jint vals[3] = { (jint)info.ebx, (jint)info.edx, (jint)info.ecx };
    env->SetIntArrayRegion(arr, 0, 3, vals);
    return arr;
}

JNIEXPORT jlong JNICALL Java_com_esplus_bridge_NativeBridge_asmMemscan
  (JNIEnv* env, jclass, jbyteArray hay, jint hayLen, jbyteArray pat, jint patLen) {
    jbyte* hp = env->GetByteArrayElements(hay, nullptr);
    jbyte* pp = env->GetByteArrayElements(pat, nullptr);
    const uint8_t* r = asmlib_memscan_pattern(
        (const uint8_t*)hp, (size_t)hayLen,
        (const uint8_t*)pp, (size_t)patLen);
    env->ReleaseByteArrayElements(hay, hp, JNI_ABORT);
    env->ReleaseByteArrayElements(pat, pp, JNI_ABORT);
    return (jlong)(r ? (uintptr_t)r : 0);
}

}
