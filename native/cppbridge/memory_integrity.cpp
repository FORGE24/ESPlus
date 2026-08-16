#include "memory_integrity.hpp"

#include <jni.h>
#include <windows.h>
#include <winternl.h>
#include <psapi.h>
#include <bcrypt.h>
#include <wincrypt.h>
#include <wintrust.h>

#pragma comment(lib, "bcrypt.lib")
#pragma comment(lib, "crypt32.lib")
#pragma comment(lib, "wintrust.lib")

#pragma pack(push, 1)
struct pe_dos_header {
    uint16_t e_magic;
    uint8_t  e_pad[58];
    uint32_t e_lfanew;
};
struct pe_nt_header_file {
    uint16_t Machine;
    uint16_t NumberOfSections;
    uint32_t TimeDateStamp;
    uint32_t PointerToSymbolTable;
    uint32_t NumberOfSymbols;
    uint16_t SizeOfOptionalHeader;
    uint16_t Characteristics;
};
struct pe_section_header {
    char     Name[8];
    uint32_t VirtualSize;
    uint32_t VirtualAddress;
    uint32_t SizeOfRawData;
    uint32_t PointerToRawData;
    uint32_t PointerToRelocations;
    uint32_t PointerToLinenumbers;
    uint16_t NumberOfRelocations;
    uint16_t NumberOfLinenumbers;
    uint32_t Characteristics;
};
#pragma pack(pop)

namespace {

using namespace esplus;

static bool computeSha256Raw(const uint8_t* data, size_t len, uint8_t out[32]) {
    BCRYPT_ALG_HANDLE alg = nullptr;
    if (BCryptOpenAlgorithmProvider(&alg, BCRYPT_SHA256_ALGORITHM, nullptr, 0) < 0)
        return false;
    BCRYPT_HASH_HANDLE hash = nullptr;
    if (BCryptCreateHash(alg, &hash, nullptr, 0, nullptr, 0, 0) < 0) {
        BCryptCloseAlgorithmProvider(alg, 0);
        return false;
    }
    BCryptHashData(hash, const_cast<uint8_t*>(data), (ULONG)len, 0);
    BCryptFinishHash(hash, out, 32, 0);
    BCryptDestroyHash(hash);
    BCryptCloseAlgorithmProvider(alg, 0);
    return true;
}

static bool walkSectionsInternal(HMODULE hMod, std::vector<SectionHash>& out) {
    auto base = reinterpret_cast<uint8_t*>(hMod);
    auto dos  = reinterpret_cast<pe_dos_header*>(base);
    if (dos->e_magic != 0x5A4D) return false;
    auto pe = reinterpret_cast<uint8_t*>(base + dos->e_lfanew);
    if (*reinterpret_cast<uint32_t*>(pe) != 0x4550) return false;
    auto file = reinterpret_cast<pe_nt_header_file*>(pe + 4);
    auto opt  = reinterpret_cast<uint8_t*>(file) + sizeof(pe_nt_header_file);
    auto sections = reinterpret_cast<pe_section_header*>(opt + file->SizeOfOptionalHeader);
    for (uint16_t i = 0; i < file->NumberOfSections; i++) {
        pe_section_header& s = sections[i];
        SectionHash sh{};
        char cn[9] = {0};
        for (int c = 0; c < 8 && s.Name[c]; c++) cn[c] = s.Name[c];
        sh.name = cn;
        sh.virtualSize = s.VirtualSize;
        sh.virtualAddr = s.VirtualAddress;
        uint32_t sz = s.VirtualSize ? s.VirtualSize : s.SizeOfRawData;
        if (sz > 0) computeSha256Raw(base + s.VirtualAddress, sz, sh.sha256);
        out.push_back(sh);
    }
    return true;
}

static std::vector<HookReport> scanIatHooksInternal(HMODULE hMod) {
    std::vector<HookReport> out;
    auto base = reinterpret_cast<uint8_t*>(hMod);
    auto dos = reinterpret_cast<pe_dos_header*>(base);
    if (dos->e_magic != 0x5A4D) return out;
    auto pe = reinterpret_cast<uint8_t*>(base + dos->e_lfanew);
    auto file = reinterpret_cast<pe_nt_header_file*>(pe + 4);
    auto opt  = reinterpret_cast<uint8_t*>(file) + sizeof(pe_nt_header_file);
    bool is64 = (*reinterpret_cast<uint16_t*>(opt) == 0x20b);
    uint32_t importRVA = 0, importSize = 0;
    if (is64) {
        struct opt64 { uint8_t pad[112]; uint32_t drva[16]; uint32_t drvs[16]; };
        auto o = reinterpret_cast<opt64*>(opt);
        importRVA  = o->drva[1];
        importSize = o->drvs[1];
    } else {
        struct opt32 { uint8_t pad[96]; uint32_t drva[16]; uint32_t drvs[16]; };
        auto o = reinterpret_cast<opt32*>(opt);
        importRVA  = o->drva[1];
        importSize = o->drvs[1];
    }
    if (!importRVA) return out;

    auto descs = reinterpret_cast<IMAGE_IMPORT_DESCRIPTOR*>(base + importRVA);
    for (int i = 0; descs[i].Name || descs[i].OriginalFirstThunk; i++) {
        const char* dllName = reinterpret_cast<const char*>(base + descs[i].Name);
        uint32_t origRVA = descs[i].OriginalFirstThunk;
        uint32_t firstRVA = descs[i].FirstThunk;
        if (!firstRVA) continue;
        auto curThunk = reinterpret_cast<uint8_t*>(base + firstRVA);
        auto refThunk = origRVA ? reinterpret_cast<uint8_t*>(base + origRVA) : curThunk;
        for (int k = 0; ; k++) {
            uintptr_t curVal = is64
                ? *reinterpret_cast<uint64_t*>(curThunk + k * 8)
                : *reinterpret_cast<uint32_t*>(curThunk + k * 4);
            uintptr_t refVal = is64
                ? *reinterpret_cast<uint64_t*>(refThunk + k * 8)
                : *reinterpret_cast<uint32_t*>(refThunk + k * 4);
            if (!refVal) break;
            uintptr_t exp = is64 ? refVal & 0x7FFFFFFFFFFFULL : refVal;
            uintptr_t got = is64 ? curVal & 0x7FFFFFFFFFFFULL : curVal;
            if (got && got != exp) {
                HookReport hr{};
                hr.moduleName = dllName;
                hr.functionName = "iat_entry[" + std::to_string(k) + "]";
                hr.originalAddr = exp;
                hr.currentAddr  = got;
                hr.isHooked = true;
                hr.hookType = "iat";
                out.push_back(hr);
            }
        }
    }
    return out;
}

static std::vector<HookReport> scanInlineHooksInternal(HMODULE hMod) {
    std::vector<HookReport> out;
    MEMORY_BASIC_INFORMATION mbi{};
    uintptr_t addr = reinterpret_cast<uintptr_t>(hMod);
    while (VirtualQuery(reinterpret_cast<void*>(addr), &mbi, sizeof(mbi)) == sizeof(mbi)) {
        if (mbi.State == MEM_COMMIT && mbi.Protect == PAGE_EXECUTE_READWRITE) {
            auto p = reinterpret_cast<uint8_t*>(mbi.BaseAddress);
            for (size_t i = 0; i + 5 <= mbi.RegionSize; i++) {
                if (p[i] == 0xE9 || p[i] == 0xEB) {
                    HookReport hr{};
                    hr.moduleName = "rwx_region";
                    hr.functionName = "branch_at_" + std::to_string(addr - reinterpret_cast<uintptr_t>(hMod));
                    hr.currentAddr = addr;
                    hr.isHooked = true;
                    hr.hookType = "e9tramp";
                    out.push_back(hr);
                    if (out.size() > 200) return out;
                }
            }
        }
        addr += mbi.RegionSize;
    }
    return out;
}

}

namespace esplus {

MemoryIntegrityChecker& MemoryIntegrityChecker::instance() {
    static MemoryIntegrityChecker s;
    return s;
}

void MemoryIntegrityChecker::setTargetPid(uint32_t pid, const wchar_t* moduleName) {
    pid_ = pid;
    if (moduleName) wcsncpy_s(targetModule_, moduleName, 259);
}

bool MemoryIntegrityChecker::computeSha256(const uint8_t* data, size_t len, uint8_t out[32]) {
    return computeSha256Raw(data, len, out);
}

bool MemoryIntegrityChecker::verifyAuthenticode(const wchar_t* filePath) {
    WINTRUST_FILE_INFO wfi = { sizeof(wfi) };
    wfi.pcwszFilePath = filePath;
    WINTRUST_DATA wd = { sizeof(wd) };
    wd.dwUIChoice = WTD_UI_NONE;
    wd.fdwRevocationChecks = WTD_REVOKE_NONE;
    wd.dwUnionChoice = WTD_CHOICE_FILE;
    wd.pFile = &wfi;
    GUID guid = { 0x1a10b268, 0x1624, 0x11d0,
        { 0x81, 0xee, 0x00, 0xaa, 0x00, 0x4b, 0xf9, 0xae } };
    return SUCCEEDED(WinVerifyTrust(nullptr, &guid, &wd));
}

bool MemoryIntegrityChecker::verifyModule(const wchar_t* modulePath, MemoryIntegrityReport& out) {
    out = {};
    HMODULE h = GetModuleHandleW(modulePath);
    if (!h) h = GetModuleHandleW(nullptr);
    if (!h) return false;

    auto base = reinterpret_cast<uint8_t*>(h);
    out.baseAddr = reinterpret_cast<uintptr_t>(h);
    MODULEINFO mi{};
    if (GetModuleInformation(GetCurrentProcess(), h, &mi, sizeof(mi)))
        out.moduleSize = mi.SizeOfImage;

    auto dos = reinterpret_cast<pe_dos_header*>(base);
    out.peHeaderOK = (dos->e_magic == 0x5A4D);
    if (!out.peHeaderOK) return false;

    wchar_t fullPath[MAX_PATH] = {0};
    GetModuleFileNameW(h, fullPath, MAX_PATH);
    out.signatureOK = verifyAuthenticode(fullPath);

    walkSectionsInternal(h, out.sectionHashes);
    auto iatHooks = scanIatHooksInternal(h);
    auto inlineHooks = scanInlineHooksInternal(h);
    out.hooks.insert(out.hooks.end(), iatHooks.begin(), iatHooks.end());
    out.hooks.insert(out.hooks.end(), inlineHooks.begin(), inlineHooks.end());
    out.totalHooked = (uint32_t)out.hooks.size();
    return true;
}

} // namespace esplus

extern "C" {

JNIEXPORT jboolean JNICALL Java_com_esplus_bridge_NativeBridge_cppVerifyModule
  (JNIEnv* env, jclass, jstring jPath) {
    const jchar* raw = jPath ? env->GetStringChars(jPath, nullptr) : nullptr;
    const wchar_t* path = reinterpret_cast<const wchar_t*>(raw);
    esplus::MemoryIntegrityReport rep{};
    bool ok = esplus::MemoryIntegrityChecker::instance().verifyModule(path, rep);
    if (raw) env->ReleaseStringChars(jPath, raw);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL Java_com_esplus_bridge_NativeBridge_cppScanHooks
  (JNIEnv*, jclass) {
    esplus::MemoryIntegrityReport rep{};
    if (esplus::MemoryIntegrityChecker::instance().verifyModule(nullptr, rep)) {
        return (jint)rep.totalHooked;
    }
    return -1;
}

}
