# ARMv8A / AArch64 anti-cheat primitives
# Target: GAS (Linux / Android / iOS cross-compile)
# Build:  aarch64-linux-gnu-gcc -c arm64_primitives.S -o arm64_primitives.o

    .text
    .syntax unified
    .globl  arm64_get_cntvct
    .type   arm64_get_cntvct, %function
arm64_get_cntvct:
    mrs     x0, cntvct_el0
    ret

    .globl  arm64_read_debug_regs
    .type   arm64_read_debug_regs, %function
arm64_read_debug_regs:
    # x0 = out[8] pointer
    mrs     x1, dbgbreg0_el0
    str     x1, [x0, #0]
    mrs     x1, dbgbreg1_el0
    str     x1, [x0, #8]
    mrs     x1, dbgbreg2_el0
    str     x1, [x0, #16]
    mrs     x1, dbgbreg3_el0
    str     x1, [x0, #24]
    mrs     x1, dbgbreg4_el0
    str     x1, [x0, #32]
    mrs     x1, dbgbreg5_el0
    str     x1, [x0, #40]
    mrs     x1, dbgbreg6_el0
    str     x1, [x0, #48]
    mrs     x1, dbgbreg7_el0
    str     x1, [x0, #56]
    ret

    .globl  arm64_get_pac_mask
    .type   arm64_get_pac_mask, %function
arm64_get_pac_mask:
    mrs     x0, id_aa64isar1_el1
    # bits [31:28] = API (PAC)
    lsr     x0, x0, #28
    and     x0, x0, #0xF
    ret

    .globl  arm64_get_el0
    .type   arm64_get_el0, %function
arm64_get_el0:
    mrs     x1, CurrentEL
    lsr     x1, x1, #2
    str     w1, [x0]
    ret

    .globl  arm64_is_jailbreak_procd
    .type   arm64_is_jailbreak_procd, %function
arm64_is_jailbreak_procd:
    # Try to open /proc/self/mem (inaccessible on non-jailbroken Android SELinux)
    mov     x0, #-100      # AT_FDCWD
    adrp    x1, .Lpath
    add     x1, x1, :lo12:.Lpath
    mov     x2, #0         # O_RDONLY
    mov     x8, #56        # openat
    svc     #0
    cmn     x0, #4096      # if syscall error -> not jailbroken
    b.hs    .Lnotjb
    mov     x16, x0
    mov     x0, x16
    mov     x8, #57        # close
    svc     #0
    mov     x0, #1         # jailbreak detected
    ret
.Lnotjb:
    mov     x0, #0
    ret

    .section    .rodata
    .align      2
.Lpath:
    .string     "/proc/self/mem"
    .size       arm64_get_cntvct, . - arm64_get_cntvct
    .size       arm64_read_debug_regs, . - arm64_read_debug_regs
    .size       arm64_get_pac_mask, . - arm64_get_pac_mask
    .size       arm64_get_el0, . - arm64_get_el0
    .size       arm64_is_jailbreak_procd, . - arm64_is_jailbreak_procd
    .section    .text
