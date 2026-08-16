#ifndef ESPLUS_ARM64_H
#define ESPLUS_ARM64_H

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

#if defined(__aarch64__)

uint64_t arm64_get_cntvct(void);
void     arm64_read_debug_regs(uint64_t dbg[8]);
uint32_t arm64_get_pac_mask(void);
void     arm64_get_el0(uint32_t* el);
int      arm64_is_jailbreak_procd(void);

#endif

#ifdef __cplusplus
}
#endif

#endif
