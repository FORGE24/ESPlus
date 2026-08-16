#ifndef ESPLUS_NATIVE_BRIDGE_H
#define ESPLUS_NATIVE_BRIDGE_H

#include <windows.h>
#include <jni.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jboolean JNICALL Java_com_esplus_bridge_NativeBridge_nativeInit
  (JNIEnv*, jclass, jlong pid, jstring configPath);

JNIEXPORT jstring JNICALL Java_com_esplus_bridge_NativeBridge_getHwid
  (JNIEnv*, jclass);

JNIEXPORT jint JNICALL Java_com_esplus_bridge_NativeBridge_getProcessIntegrity
  (JNIEnv*, jclass);

JNIEXPORT jboolean JNICALL Java_com_esplus_bridge_NativeBridge_checkDebugger
  (JNIEnv*, jclass);

JNIEXPORT jboolean JNICALL Java_com_esplus_bridge_NativeBridge_checkTiming
  (JNIEnv*, jclass);

JNIEXPORT jintArray JNICALL Java_com_esplus_bridge_NativeBridge_readHwBreaks
  (JNIEnv*, jclass);

JNIEXPORT jbyteArray JNICALL Java_com_esplus_bridge_NativeBridge_asmMemscan
  (JNIEnv*, jclass, jlong baseAddr, jint size, jbyteArray pattern, jint patternLen);

JNIEXPORT jlong JNICALL Java_com_esplus_bridge_NativeBridge_getProcessBase
  (JNIEnv*, jclass);

#ifdef __cplusplus
}
#endif

#endif
