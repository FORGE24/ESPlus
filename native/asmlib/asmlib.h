#ifndef ESPLUS_ASMLIB_H
#define ESPLUS_ASMLIB_H

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct {
    uint32_t ebx;
    uint32_t edx;
    uint32_t ecx;
} CpuIdInfo;

uint64_t asmlib_rdtsc(void);
void asmlib_cpu_id(uint32_t leaf, CpuIdInfo* out);

const uint8_t* asmlib_memscan_pattern(const uint8_t* haystack, size_t haystack_len,
                                      const uint8_t* pattern, size_t pattern_len);

const uint8_t* asmlib_memscan_masked(const uint8_t* haystack, size_t haystack_len,
                                     const uint8_t* pattern, const uint8_t* mask,
                                     size_t pattern_len);

#ifdef __cplusplus
}
#endif

#endif
