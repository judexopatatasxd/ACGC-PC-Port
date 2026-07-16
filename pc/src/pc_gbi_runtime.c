#include <stdint.h>
#include <stdio.h>

#define PC_GBI_ODD_PTR_TOKEN_BASE 0x02F00000u
#define PC_GBI_ODD_PTR_TOKEN_COUNT 8192u

static uintptr_t s_odd_ptr_tokens[PC_GBI_ODD_PTR_TOKEN_COUNT];
static unsigned int s_odd_ptr_token_next = 0;
static int s_warned_odd_ptr = 0;

uintptr_t pc_gbi_pack_runtime_ptr(uintptr_t addr, int is_ptr, const char* expr, const char* file, int line) {
#ifdef PC_64BIT
    (void)is_ptr;
    (void)expr;
    (void)file;
    (void)line;
    return addr;
#else
    unsigned int slot;

    if (!is_ptr) {
        return (unsigned int)addr;
    }

    if ((addr & 1u) == 0) {
        return (unsigned int)(addr | 1u);
    }

    slot = s_odd_ptr_token_next++ & (PC_GBI_ODD_PTR_TOKEN_COUNT - 1u);
    s_odd_ptr_tokens[slot] = addr;

    if (!s_warned_odd_ptr) {
        fprintf(stderr,
                "[GBI] odd pointer alignment: %s at %s:%d = 0x%08x; using fallback token table\n",
                expr, file, line, (unsigned int)addr);
        s_warned_odd_ptr = 1;
    }

    return PC_GBI_ODD_PTR_TOKEN_BASE + slot * 2u;
#endif
}

uintptr_t pc_gbi_unpack_runtime_ptr(uintptr_t packed) {
#ifdef PC_64BIT
    (void)packed;
    return 0;
#else
    unsigned int token = packed - PC_GBI_ODD_PTR_TOKEN_BASE;

    if (token < PC_GBI_ODD_PTR_TOKEN_COUNT * 2u && (token & 1u) == 0) {
        return s_odd_ptr_tokens[token / 2u];
    }

    return 0;
#endif
}
