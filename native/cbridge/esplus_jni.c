#include "esplus_jni.h"
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <inttypes.h>
#include <psapi.h>
#include <tlhelp32.h>
#include <iphlpapi.h>
#include <winternl.h>

#pragma comment(lib, "iphlpapi.lib")
#pragma comment(lib, "advapi32.lib")
#pragma comment(lib, "psapi.lib")
#pragma comment(lib, "ws2_32.lib")

#ifndef NT_SUCCESS
#define NT_SUCCESS(Status) ((NTSTATUS)(Status) >= 0)
#endif

typedef LONG NTSTATUS;
#ifndef NTAPI
#define NTAPI __stdcall
#endif
typedef NTSTATUS (NTAPI* pfnNtQueryInformationProcess)(
    HANDLE, UINT, PVOID, ULONG, PULONG
);

#ifndef PROCESS_DEBUG_PORT
#define PROCESS_DEBUG_PORT          7
#endif
#ifndef PROCESS_DEBUG_OBJECT_HANDLE
#define PROCESS_DEBUG_OBJECT_HANDLE 30
#endif

#define BUF_MAX 1024

static HMODULE g_ntdll = NULL;
static pfnNtQueryInformationProcess g_NtQueryInformationProcess = NULL;
static DWORD g_minecraft_pid = 0;

static void lazy_init_nt(void) {
    if (g_ntdll) return;
    g_ntdll = GetModuleHandleA("ntdll.dll");
    if (!g_ntdll) g_ntdll = LoadLibraryA("ntdll.dll");
    if (g_ntdll) {
        g_NtQueryInformationProcess = (pfnNtQueryInformationProcess)
            GetProcAddress(g_ntdll, "NtQueryInformationProcess");
    }
}

JNIEXPORT jboolean JNICALL Java_com_esplus_bridge_NativeBridge_nativeInit
  (JNIEnv* env, jclass cls, jlong pid, jstring configPath) {
    (void)env; (void)cls; (void)configPath;
    g_minecraft_pid = (DWORD)pid;
    lazy_init_nt();
    return JNI_TRUE;
}

static void sha256_soft(const uint8_t* in, size_t len, uint8_t out[32]) {
    memset(out, 0, 32);
    for (size_t i = 0; i < len; i++) {
        out[i % 32] ^= in[i];
        out[i % 32] = (uint8_t)(out[i % 32] * 31 + in[i]);
    }
    for (int r = 0; r < 8; r++) {
        for (int k = 0; k < 32; k++) {
            out[k] = (uint8_t)(out[k] * 31 + out[(k + 1) % 32]);
        }
    }
}

static void bytes_to_hex(const uint8_t* b, int n, char* out) {
    static const char hex[] = "0123456789abcdef";
    for (int i = 0; i < n; i++) {
        out[i * 2]     = hex[(b[i] >> 4) & 0xF];
        out[i * 2 + 1] = hex[b[i] & 0xF];
    }
    out[n * 2] = 0;
}

JNIEXPORT jstring JNICALL Java_com_esplus_bridge_NativeBridge_getHwid
  (JNIEnv* env, jclass cls) {
    (void)cls;
    char macs[2048] = {0};
    PIP_ADAPTER_INFO pInfo = NULL;
    ULONG bufSize = 0;
    GetAdaptersInfo(NULL, &bufSize);
    if (bufSize > 0) {
        pInfo = (PIP_ADAPTER_INFO)malloc(bufSize);
        if (pInfo && GetAdaptersInfo(pInfo, &bufSize) == NO_ERROR) {
            PIP_ADAPTER_INFO cur = pInfo;
            while (cur) {
                if (cur->AddressLength == 6) {
                    int has_data = 0;
                    for (int i = 0; i < 6; i++) if (cur->Address[i]) has_data = 1;
                    if (has_data && cur->Type != IF_TYPE_SOFTWARE_LOOPBACK) {
                        char h[18];
                        snprintf(h, sizeof(h), "%02X:%02X:%02X:%02X:%02X:%02X|",
                                 cur->Address[0], cur->Address[1], cur->Address[2],
                                 cur->Address[3], cur->Address[4], cur->Address[5]);
                        strncat(macs, h, sizeof(macs) - strlen(macs) - 1);
                    }
                }
                cur = cur->Next;
            }
        }
        free(pInfo);
    }

    char disk_id[16] = {0};
    DWORD volSerial = 0;
    if (GetVolumeInformationA("C:\\", NULL, 0, &volSerial, NULL, NULL, NULL, 0)) {
        snprintf(disk_id, sizeof(disk_id), "%08X", volSerial);
    }

    HKEY hk = NULL;
    char machine_guid[64] = {0};
    if (RegOpenKeyExA(HKEY_LOCAL_MACHINE,
        "SOFTWARE\\Microsoft\\Cryptography", 0, KEY_READ | KEY_WOW64_64KEY, &hk) == ERROR_SUCCESS) {
        DWORD vlen = sizeof(machine_guid);
        RegQueryValueExA(hk, "MachineGuid", NULL, NULL, (LPBYTE)machine_guid, &vlen);
        RegCloseKey(hk);
    }

    char combined[BUF_MAX * 2] = {0};
    snprintf(combined, sizeof(combined), "%s|%s|%s", macs, disk_id, machine_guid);

    uint8_t digest[32];
    sha256_soft((const uint8_t*)combined, strlen(combined), digest);

    char hex[65];
    bytes_to_hex(digest, 32, hex);

    return (*env)->NewStringUTF(env, hex);
}

static int count_unsigned_modules(DWORD pid) {
    HANDLE snap = CreateToolhelp32Snapshot(TH32CS_SNAPMODULE, pid);
    if (snap == INVALID_HANDLE_VALUE) return 0;
    MODULEENTRY32 me = { sizeof(me) };
    int unsigned_count = 0;

    static const char* safe[] = {
        "jvm.dll","ntdll.dll","kernel32.dll","kernelbase.dll",
        "user32.dll","advapi32.dll","gdi32.dll","gdi32full.dll",
        "winmm.dll","d3d11.dll","dxgi.dll","opengl32.dll",
        "dwmapi.dll","ole32.dll","shell32.dll","combase.dll",
        "ucrtbase.dll","msvcrt.dll",NULL
    };

    if (Module32First(snap, &me)) {
        do {
            const char* base = strrchr(me.szModule, '\\');
            const char* name = base ? base + 1 : me.szModule;
            int known = 0;
            for (int i = 0; safe[i]; i++) {
                if (_stricmp(name, safe[i]) == 0) { known = 1; break; }
            }
            if (!known) unsigned_count++;
        } while (Module32Next(snap, &me));
    }
    CloseHandle(snap);
    return unsigned_count;
}

JNIEXPORT jint JNICALL Java_com_esplus_bridge_NativeBridge_getProcessIntegrity
  (JNIEnv* env, jclass cls) {
    (void)env; (void)cls;
    int unsigned_mods = count_unsigned_modules(g_minecraft_pid);
    int score = 100 - unsigned_mods * 15;
    if (score < 0) score = 0;
    if (score > 100) score = 100;
    return (jint)score;
}

JNIEXPORT jboolean JNICALL Java_com_esplus_bridge_NativeBridge_checkDebugger
  (JNIEnv* env, jclass cls) {
    (void)env; (void)cls;

    if (IsDebuggerPresent()) return JNI_TRUE;

    BOOL remote = FALSE;
    if (CheckRemoteDebuggerPresent(GetCurrentProcess(), &remote) && remote)
        return JNI_TRUE;

    lazy_init_nt();
    if (g_NtQueryInformationProcess) {
        HANDLE hPort = NULL;
        ULONG retLen = 0;
        NTSTATUS st = g_NtQueryInformationProcess(
            GetCurrentProcess(), PROCESS_DEBUG_PORT,
            &hPort, sizeof(hPort), &retLen);
        if (NT_SUCCESS(st) && hPort != NULL)
            return JNI_TRUE;
    }

    return JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_com_esplus_bridge_NativeBridge_checkTiming
  (JNIEnv* env, jclass cls) {
    (void)env; (void)cls;
    DWORD tick1 = GetTickCount();
    LARGE_INTEGER qpc1, qpf;
    QueryPerformanceCounter(&qpc1);
    QueryPerformanceFrequency(&qpf);

    int volatile x = 0;
    for (int i = 0; i < 500000; i++) x += i;
    (void)x;

    DWORD tick2 = GetTickCount();
    LARGE_INTEGER qpc2;
    QueryPerformanceCounter(&qpc2);

    DWORD tickDelta = (tick2 >= tick1) ? (tick2 - tick1) : ((DWORD)-1 - tick1 + tick2);
    double qpcMs = (double)(qpc2.QuadPart - qpc1.QuadPart) * 1000.0 / (double)qpf.QuadPart;

    if ((double)tickDelta > qpcMs + 100.0) return JNI_TRUE;
    if ((double)tickDelta + 100.0 < qpcMs) return JNI_TRUE;

    return JNI_FALSE;
}

JNIEXPORT jintArray JNICALL Java_com_esplus_bridge_NativeBridge_readHwBreaks
  (JNIEnv* env, jclass cls) {
    (void)cls;
    jintArray arr = (*env)->NewIntArray(env, 4);
    jint dr[4] = {0, 0, 0, 0};
#if defined(_WIN64)
    unsigned long long d0, d1, d2, d3;
    __asm__ __volatile__ (
        "mov %%dr0, %0\n"
        "mov %%dr1, %1\n"
        "mov %%dr2, %2\n"
        "mov %%dr3, %3\n"
        : "=r"(d0), "=r"(d1), "=r"(d2), "=r"(d3)
        :
        : "memory"
    );
    dr[0] = (jint)d0;
    dr[1] = (jint)d1;
    dr[2] = (jint)d2;
    dr[3] = (jint)d3;
#elif defined(_WIN32)
    __asm__ __volatile__ (
        "mov %%dr0, %0\n"
        "mov %%dr1, %1\n"
        "mov %%dr2, %2\n"
        "mov %%dr3, %3\n"
        : "=r"(dr[0]), "=r"(dr[1]), "=r"(dr[2]), "=r"(dr[3])
        :
        : "memory"
    );
#else
    (void)dr;
#endif
    (*env)->SetIntArrayRegion(env, arr, 0, 4, dr);
    return arr;
}

JNIEXPORT jlong JNICALL Java_com_esplus_bridge_NativeBridge_getProcessBase
  (JNIEnv* env, jclass cls) {
    (void)env; (void)cls;
    HANDLE snap = CreateToolhelp32Snapshot(TH32CS_SNAPMODULE, g_minecraft_pid);
    if (snap == INVALID_HANDLE_VALUE) return 0;
    MODULEENTRY32 me = { sizeof(me) };
    uintptr_t base = 0;
    if (Module32First(snap, &me)) {
        base = (uintptr_t)me.modBaseAddr;
    }
    CloseHandle(snap);
    return (jlong)base;
}
