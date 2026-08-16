# x86-64 ASM primitives for ESPlus anti-cheat (GNU as / MinGW)

    .text
    .intel_syntax noprefix

# uint64_t asmlib_rdtsc(void)
    .globl  asmlib_rdtsc
asmlib_rdtsc:
    rdtsc
    shl     rdx, 32
    or      rax, rdx
    ret

# void asmlib_cpu_id(uint32_t leaf, CpuIdInfo* out)
# rcx = leaf, rdx = out*
    .globl  asmlib_cpu_id
asmlib_cpu_id:
    push    rbx
    mov     eax, ecx
    cpuid
    mov     [rdx+0], ebx
    mov     [rdx+4], edx
    mov     [rdx+8], ecx
    pop     rbx
    ret

# const uint8_t* asmlib_memscan_pattern(hay, hay_len, pat, pat_len)
    .globl  asmlib_memscan_pattern
asmlib_memscan_pattern:
    test    r9, r9
    je      .Lmp_notfound
    cmp     rdx, r9
    jl      .Lmp_notfound

    push    rbp
    push    r12
    push    r13
    push    r14

    mov     rbp, rcx            # haystack
    mov     r12, rdx            # haystack_len
    mov     r13, r8             # pattern
    mov     r14, r9             # pattern_len

    sub     r12, r14
    inc     r12                 # r12 = search_count

    mov     al, byte ptr [r13]
    mov     rcx, r12
    mov     rdi, rbp
    repne   scasb
    jne     .Lmp_end

.Lmp_check:
    lea     rax, [rdi-1]
    cmp     r14, 1
    je      .Lmp_hit
    # compare remaining bytes
    mov     rcx, r14
    sub     rcx, 1
    mov     r8, r13
    inc     r8
    mov     r9, rax
    inc     r9
.Lmp_cmp:
    mov     dl, byte ptr [r8]
    cmp     dl, byte ptr [r9]
    jne     .Lmp_next
    inc     r8
    inc     r9
    dec     rcx
    jnz     .Lmp_cmp
.Lmp_hit:
    pop     r14
    pop     r13
    pop     r12
    pop     rbp
    ret
.Lmp_next:
    # compute remaining distance from rdi
    mov     rcx, r12
    lea     rdx, [rbp+r12]
    sub     rdx, rdi
    mov     rcx, rdx
    mov     al, byte ptr [r13]
    repne   scasb
    je      .Lmp_check
.Lmp_end:
    xor     rax, rax
    pop     r14
    pop     r13
    pop     r12
    pop     rbp
    ret
.Lmp_notfound:
    xor     rax, rax
    ret

# const uint8_t* asmlib_memscan_masked(hay, hay_len, pat, mask, pat_len)
    .globl  asmlib_memscan_masked
asmlib_memscan_masked:
    mov     rax, [rsp+40]       # pat_len (5th arg on stack)
    test    rax, rax
    je      .Lmm_notfound
    cmp     rdx, rax
    jl      .Lmm_notfound

    push    rbp
    push    r12
    push    r13
    push    r14
    push    r15
    push    rsi

    mov     rbp, rcx            # haystack
    mov     r12, rax            # pat_len
    mov     r13, rdx            # haystack_len
    mov     r14, r8             # pattern
    mov     r15, r9             # mask
    sub     r13, r12
    inc     r13                 # search_count
    xor     rsi, rsi            # outer cursor
.Lmm_outer:
    cmp     rsi, r13
    jae     .Lmm_false
    xor     r11, r11
.Lmm_inner:
    cmp     r11, r12
    jae     .Lmm_true
    mov     al, byte ptr [r15+r11]
    test    al, al
    jz      .Lmm_skip
    mov     al, byte ptr [r14+r11]
    mov     dl, byte ptr [rbp+rsi]
    add     rsi, r11
    cmp     al, byte ptr [rbp+rsi]
    sub     rsi, r11
    jne     .Lmm_fail
.Lmm_skip:
    inc     r11
    jmp     .Lmm_inner
.Lmm_fail:
    inc     rsi
    jmp     .Lmm_outer
.Lmm_true:
    lea     rax, [rbp+rsi]
    pop     rsi
    pop     r15
    pop     r14
    pop     r13
    pop     r12
    pop     rbp
    ret
.Lmm_false:
    xor     rax, rax
    pop     rsi
    pop     r15
    pop     r14
    pop     r13
    pop     r12
    pop     rbp
    ret
.Lmm_notfound:
    xor     rax, rax
    ret
