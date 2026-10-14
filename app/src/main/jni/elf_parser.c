#define _FILE_OFFSET_BITS 64
#include <string.h>
#include <errno.h>
#include <unistd.h>
#include <fcntl.h>
#include <stdint.h>
#include <linux/elf.h>
#include <stdio.h>
#include <stdlib.h>

/* DFRoot patches libc++.so from the device image. The watch's library is
 * ELF32/ARM, while upstream DFRoot only parsed ELF64/AArch64. Keep explicit
 * class-specific readers so host pointer size never changes the file format. */

static int read_at(int fd, uint64_t off, void *buf, size_t len)
{
    ssize_t n = pread64(fd, buf, len, (off64_t)off);
    return n == (ssize_t)len ? 0 : -1;
}

static int find_hook_32(int fd, const char *path, const char *symname,
                        uint64_t *hook_target, uint64_t *payload_target,
                        uint32_t *first_instruction, int *thumb_mode)
{
    Elf32_Ehdr h;
    if (read_at(fd, 0, &h, sizeof(h)) || h.e_phentsize != sizeof(Elf32_Phdr) ||
        h.e_shentsize != sizeof(Elf32_Shdr) || h.e_shstrndx >= h.e_shnum) {
        printf("invalid ELF32 layout: %s\n", path); return 1;
    }
    uint64_t exec_off = 0, exec_vaddr = 0;
    for (unsigned i = 0; i < h.e_phnum; ++i) {
        Elf32_Phdr p;
        if (read_at(fd, (uint64_t)h.e_phoff + (uint64_t)i * sizeof(p), &p, sizeof(p))) return 1;
        if (p.p_type == PT_LOAD && (p.p_flags & PF_X)) {
            *payload_target = (uint64_t)p.p_offset + p.p_filesz;
            exec_off = p.p_offset; exec_vaddr = p.p_vaddr; break;
        }
    }
    if (!exec_off && !exec_vaddr) { printf("no executable PT_LOAD: %s\n", path); return 1; }

    Elf32_Shdr names;
    uint64_t names_at = (uint64_t)h.e_shoff + (uint64_t)h.e_shstrndx * sizeof(names);
    if (read_at(fd, names_at, &names, sizeof(names))) return 1;
    uint32_t dynstr = 0, dynsym_off = 0, dynsym_size = 0;
    for (unsigned i = 0; i < h.e_shnum; ++i) {
        Elf32_Shdr s;
        if (read_at(fd, (uint64_t)h.e_shoff + (uint64_t)i * sizeof(s), &s, sizeof(s))) return 1;
        if (s.sh_name >= names.sh_size) continue;
        char name[64] = {0};
        size_t n = names.sh_size - s.sh_name;
        if (n >= sizeof(name)) n = sizeof(name) - 1;
        if (read_at(fd, (uint64_t)names.sh_offset + s.sh_name, name, n)) return 1;
        if (!strcmp(name, ".dynstr")) dynstr = s.sh_offset;
        if (!strcmp(name, ".dynsym")) { dynsym_off = s.sh_offset; dynsym_size = s.sh_size; }
    }
    if (!dynstr || !dynsym_off) { printf(".dynstr/.dynsym missing: %s\n", path); return 1; }

    int found = 0;
    for (uint32_t off = 0; off + sizeof(Elf32_Sym) <= dynsym_size; off += sizeof(Elf32_Sym)) {
        Elf32_Sym s;
        if (read_at(fd, (uint64_t)dynsym_off + off, &s, sizeof(s))) return 1;
        if (s.st_name >= UINT32_MAX - dynstr) continue;
        char name[256] = {0};
        if (read_at(fd, (uint64_t)dynstr + s.st_name, name, sizeof(name) - 1)) return 1;
        if (!strcmp(name, symname)) {
            /* Record Thumb state separately; map the even code address into
             * the file for the page-cache write. */
            *thumb_mode = (s.st_value & 1u) != 0;
            *hook_target = (uint64_t)(s.st_value & ~1u) - exec_vaddr + exec_off;
            found = 1; break;
        }
    }
    if (!found) { printf("symbol %s not found: %s\n", symname, path); return 1; }
    if (read_at(fd, *hook_target, first_instruction, sizeof(*first_instruction))) return 1;
    return 0;
}

static int find_hook_64(int fd, const char *path, const char *symname,
                        uint64_t *hook_target, uint64_t *payload_target,
                        uint32_t *first_instruction, int *thumb_mode)
{
    *thumb_mode = 0;
    Elf64_Ehdr h;
    if (read_at(fd, 0, &h, sizeof(h)) || h.e_phentsize != sizeof(Elf64_Phdr) ||
        h.e_shentsize != sizeof(Elf64_Shdr) || h.e_shstrndx >= h.e_shnum) {
        printf("invalid ELF64 layout: %s\n", path); return 1;
    }
    uint64_t exec_off = 0, exec_vaddr = 0;
    for (unsigned i = 0; i < h.e_phnum; ++i) {
        Elf64_Phdr p;
        if (read_at(fd, h.e_phoff + (uint64_t)i * sizeof(p), &p, sizeof(p))) return 1;
        if (p.p_type == PT_LOAD && (p.p_flags & PF_X)) {
            *payload_target = p.p_offset + p.p_filesz;
            exec_off = p.p_offset; exec_vaddr = p.p_vaddr; break;
        }
    }
    if (!exec_off && !exec_vaddr) { printf("no executable PT_LOAD: %s\n", path); return 1; }

    Elf64_Shdr names;
    if (read_at(fd, h.e_shoff + (uint64_t)h.e_shstrndx * sizeof(names), &names, sizeof(names))) return 1;
    uint64_t dynstr = 0, dynsym_off = 0, dynsym_size = 0;
    for (unsigned i = 0; i < h.e_shnum; ++i) {
        Elf64_Shdr s;
        if (read_at(fd, h.e_shoff + (uint64_t)i * sizeof(s), &s, sizeof(s))) return 1;
        if (s.sh_name >= names.sh_size) continue;
        char name[64] = {0};
        size_t n = names.sh_size - s.sh_name;
        if (n >= sizeof(name)) n = sizeof(name) - 1;
        if (read_at(fd, names.sh_offset + s.sh_name, name, n)) return 1;
        if (!strcmp(name, ".dynstr")) dynstr = s.sh_offset;
        if (!strcmp(name, ".dynsym")) { dynsym_off = s.sh_offset; dynsym_size = s.sh_size; }
    }
    if (!dynstr || !dynsym_off) { printf(".dynstr/.dynsym missing: %s\n", path); return 1; }
    for (uint64_t off = 0; off + sizeof(Elf64_Sym) <= dynsym_size; off += sizeof(Elf64_Sym)) {
        Elf64_Sym s;
        if (read_at(fd, dynsym_off + off, &s, sizeof(s))) return 1;
        char name[256] = {0};
        if (read_at(fd, dynstr + s.st_name, name, sizeof(name) - 1)) return 1;
        if (!strcmp(name, symname)) {
            *hook_target = s.st_value - exec_vaddr + exec_off;
            if (read_at(fd, *hook_target, first_instruction, sizeof(*first_instruction))) return 1;
            if (*first_instruction == 0xd503233fU || *first_instruction == 0xd503245fU) {
                printf("PACIASP/BTI found at hook site, advancing +4\n");
                *hook_target += 4;
                if (read_at(fd, *hook_target, first_instruction, sizeof(*first_instruction))) return 1;
            }
            return 0;
        }
    }
    printf("symbol %s not found: %s\n", symname, path);
    return 1;
}

int find_hook_target(const char *libcxx, const char *symname,
                     uint64_t *hook_target, uint64_t *payload_target,
                     uint32_t *first_instruction, int *thumb_mode)
{
    *hook_target = 0; *payload_target = 0; *first_instruction = 0; *thumb_mode = 0;
    int fd = open(libcxx, O_RDONLY | O_CLOEXEC);
    if (fd < 0) { printf("open %s failed: %s\n", libcxx, strerror(errno)); return 1; }
    unsigned char ident[EI_NIDENT];
    if (read_at(fd, 0, ident, sizeof(ident)) || memcmp(ident, ELFMAG, SELFMAG)) {
        printf("invalid ELF: %s\n", libcxx); close(fd); return 1;
    }
    int rc;
    if (ident[EI_CLASS] == ELFCLASS32)
        rc = find_hook_32(fd, libcxx, symname, hook_target, payload_target, first_instruction, thumb_mode);
    else if (ident[EI_CLASS] == ELFCLASS64)
        rc = find_hook_64(fd, libcxx, symname, hook_target, payload_target, first_instruction, thumb_mode);
    else { printf("unsupported ELF class %u: %s\n", ident[EI_CLASS], libcxx); rc = 1; }
    close(fd);
    return rc;
}
