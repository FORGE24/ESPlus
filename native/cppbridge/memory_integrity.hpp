#ifndef ESPLUS_CPPBRIDGE_H
#define ESPLUS_CPPBRIDGE_H

#include <cstdint>
#include <cstddef>
#include <string>
#include <vector>

namespace esplus {

struct SectionHash {
    std::string name;
    uint32_t virtualSize;
    uint32_t virtualAddr;
    uint8_t  sha256[32];
};

struct HookReport {
    std::string moduleName;
    std::string functionName;
    uintptr_t originalAddr;
    uintptr_t currentAddr;
    bool isHooked;
    std::string hookType;
};

struct MemoryIntegrityReport {
    std::string moduleName;
    uintptr_t baseAddr;
    size_t moduleSize;
    bool peHeaderOK;
    bool signatureOK;
    std::vector<SectionHash> sectionHashes;
    uint32_t totalHooked;
    std::vector<HookReport> hooks;
};

class MemoryIntegrityChecker {
public:
    static MemoryIntegrityChecker& instance();

    void setTargetPid(uint32_t pid, const wchar_t* moduleName);

    bool verifyModule(const wchar_t* modulePath, MemoryIntegrityReport& out);

    static bool computeSha256(const uint8_t* data, size_t len, uint8_t out[32]);
    static bool verifyAuthenticode(const wchar_t* filePath);

private:
    MemoryIntegrityChecker() = default;
    uint32_t pid_ = 0;
    wchar_t targetModule_[260] = {0};
};

}

#endif
