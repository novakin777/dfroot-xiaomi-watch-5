#define _FILE_OFFSET_BITS 64
#include <stdlib.h>
#include <stdio.h>
#include <fcntl.h>
#include <unistd.h>
#include <stdint.h>
#include <sys/types.h>

#if defined(__arm__)
#define __NR_exit 1
#define __NR_read 3
#define __NR_write 4
#define __NR_exit_group 248
#define __NR__llseek 140
#define __NR_splice 340
#define __NR_openat 322
#else
#include <sys/syscall.h>
#endif

/* The freestanding Android helper does not depend on libc's off64_t typedef. */
typedef int64_t df_off64_t;
#ifndef SPLICE_F_MOVE
#define SPLICE_F_MOVE 1
#endif

// argv[0] = program name
// argv[1] = file offset (decimal string)
// argv[2] = file path
// argv[3] = optional "r" — read mode: write 16 bytes of file content to fd 0 (OUT_FD)
//           if absent — splice mode: splice 16 bytes of file page into fd 1 (PIPE_FD)

#define OUT_FD  0
#define PIPE_FD 1

#if defined(__arm__)
/* Linux ARM EABI: r7 carries the syscall number. Preserve it for AAPCS callers. */
__attribute__((naked)) static long mysyscall1(unsigned long arg0, unsigned long nr) {
    asm volatile("push {r7, lr}\nmov r7, r1\nsvc #0\npop {r7, pc}\n":::"memory");
}

__attribute__((naked)) static long mysyscall3(
    unsigned long a0, unsigned long a1, unsigned long a2, unsigned long nr) {
    asm volatile("push {r7, lr}\nmov r7, r3\nsvc #0\npop {r7, pc}\n":::"memory");
}

__attribute__((naked)) static long mysyscall6(
    unsigned long a0, unsigned long a1, unsigned long a2,
    unsigned long a3, unsigned long a4, unsigned long a5, unsigned long nr) {
    asm volatile("push {r4, r5, r7, lr}\n"
                 "ldr r4, [sp, #16]\nldr r5, [sp, #20]\nldr r7, [sp, #24]\n"
                 "svc #0\n"
                 "pop {r4, r5, r7, pc}\n":::"memory");
}

static long my_llseek(int fd, df_off64_t offset, int whence, df_off64_t *result) {
    return mysyscall6((unsigned long)fd,
                      (unsigned long)((uint64_t)offset >> 32),
                      (unsigned long)(uint32_t)offset,
                      (unsigned long)result,
                      (unsigned long)whence, 0, __NR__llseek);
}

__attribute__((naked)) void _start() {
    asm("mov r0, sp\nb start_c\n");
}
#else
__attribute__((naked)) static long mysyscall1(unsigned long arg0, unsigned long nr) {
    asm volatile("mov x8, x1\nsvc 0\nret\n":::"x8");
}

__attribute__((naked)) static long mysyscall3(
    unsigned long a0, unsigned long a1, unsigned long a2, unsigned long nr) {
    asm volatile("mov x8, x3\nsvc 0\nret\n":::"x8");
}

__attribute__((naked)) static long mysyscall6(
    unsigned long a0, unsigned long a1, unsigned long a2,
    unsigned long a3, unsigned long a4, unsigned long a5, unsigned long nr) {
    asm volatile("mov x8, x6\nsvc 0\nret\n":::"x8");
}

__attribute__((naked)) void _start() {
    asm(".extern start_c\nmov x0, sp\nb start_c\n");
}
#endif

static unsigned long parse_int(char *s) {
    unsigned long val = 0;
    while (*s) { val = val * 10 + (unsigned long)(*s - '0'); s++; }
    return val;
}

static int streq(const char *a, const char *b) {
    while (*a && *b && *a == *b) { a++; b++; }
    return *a == *b;
}

void start_c(void *argblock) {
    int argc = (int)*(long *)argblock;
    char **argv = (char **)argblock + 1;

    if (argc < 3 || argc > 4)
        mysyscall1(1, __NR_exit_group);

    df_off64_t off = (df_off64_t)parse_int(argv[1]);
    char *target = argv[2];
    char *mode = (argc >= 4) ? argv[3] : (char *)0;

    int file_fd = (int)mysyscall3(
        (unsigned long)AT_FDCWD,
        (unsigned long)target,
        (unsigned long)O_RDONLY,
        __NR_openat);
    if (file_fd < 0)
        mysyscall1(1, __NR_exit_group);

    if (mode && streq(mode, "r")) {
        /* Read mode: lseek to offset, read 16 bytes, write to OUT_FD */
        /* Exit codes: 0=ok, 1=read<16, 2=write<16, 3=OUT_FD not a pipe (fd sanitized) */
#if defined(__arm__)
        df_off64_t newpos;
        long seek_rc = my_llseek(file_fd, off, SEEK_SET, &newpos);
        if (seek_rc != 0) mysyscall1(1, __NR_exit_group);
#else
        mysyscall3((unsigned long)file_fd, (unsigned long)off, SEEK_SET, __NR_lseek);
#endif
        unsigned char buf[16];
        long n = mysyscall3((unsigned long)file_fd, (unsigned long)buf, 16, __NR_read);
        if (n != 16)
            mysyscall1(1, __NR_exit_group);
#if defined(__arm__)
        df_off64_t pipepos;
        if (my_llseek(OUT_FD, 0, SEEK_CUR, &pipepos) != -29L) /* ESPIPE */
#else
        if (mysyscall3(OUT_FD, 0, SEEK_CUR, __NR_lseek) != (long)-29L) /* ESPIPE: fd is a pipe */
#endif
            mysyscall1(3, __NR_exit_group);
        long w = mysyscall3(OUT_FD, (unsigned long)buf, 16, __NR_write);
        mysyscall1((unsigned long)(w == 16 ? 0 : 2), __NR_exit_group);
    }

    /* Splice mode: splice 16-byte page into PIPE_FD */
    long ret = mysyscall6(
        (unsigned long)file_fd,
        (unsigned long)&off,
        PIPE_FD,
        (unsigned long)NULL,
        16,
        SPLICE_F_MOVE,
        __NR_splice);
    mysyscall1((unsigned long)(ret == 16 ? 0 : 1), __NR_exit_group);
}
