/* ARM32 / Thumb-2 version of the DFRoot libc++ hook.
 * The C hook writer must install a Thumb-2 B.W at the symbol and replace the
 * first four original bytes in libcxx_first_inst_copy before this is used. */

    .syntax unified
    .arch armv7-a
    .thumb

    .global libcxx_start
    .global libcxx_data
    .global libcxx_len
    .global libcxx_first_inst_copy
    .global libcxx_ko_target_off

    .section .data
    .balign 4
libcxx_data:
mutex_file:       .asciz "/dev/df"
selinux_ctx:      .asciz "u:r:vendor_modprobe:s0"
selinux_ctx_end:
attr_exec:        .asciz "/proc/self/attr/exec"
exe_path:         .asciz "/vendor/bin/insmod"
ko_target:        .asciz "/vendor/lib/libbinderdebug.so"
    .space 32, 0
    .balign 4
libcxx_ko_target_off:
    .word ko_target - libcxx_data
bootstrap_path:   .asciz "/data/user_de/0/df.root/bootstrap"
hook_h0:          .asciz "/dev/dfh0"
hook_h1:          .asciz "/dev/dfh1"
hook_h2:          .asciz "/dev/dfh2"
hook_h3:          .asciz "/dev/dfh3"
hook_h4:          .asciz "/dev/dfh4"
hook_h5:          .asciz "/dev/dfh5"
hook_h6:          .asciz "/dev/dfh6"
hook_h7:          .asciz "/dev/dfh7"
hook_h8:          .asciz "/dev/dfh8"
hook_h9:          .asciz "/dev/dfh9"
    .balign 4

    .equ AT_FDCWD, -100
    .equ O_WRONLY_CLOEXEC, 0x80001
    .equ O_CREAT_EXCL_CLOEXEC, 0x800c0
    .equ SIGCHLD, 17
    .equ CLONE_CHILD_CLEARTID, 0x00200000
    .equ CLONE_CHILD_SETTID, 0x01000000

    .equ NR_exit, 1
    .equ NR_write, 4
    .equ NR_execve, 11
    .equ NR_wait4, 114
    .equ NR_clone, 120
    .equ NR_getuid32, 199
    .equ NR_gettid, 224
    .equ NR_openat, 322
    .equ NR_close, 6

    .thumb_func
libcxx_start:
    /* Keep AAPCS-visible state intact while using r7 for syscall numbers. */
    push.w  {r0-r7, r12, lr}

    movs    r7, #NR_getuid32
    svc     #0
    cmp     r0, #0
    bne     .Lreturn

    movs    r7, #NR_gettid
    svc     #0
    cmp     r0, #1
    bne     .Lreturn

    adr     r0, hook_h0
    bl      .Ltouch_marker

    /* openat(AT_FDCWD, "/dev/df", O_CREAT|O_EXCL|O_CLOEXEC, 0) */
    mvn     r0, #99
    adr     r1, mutex_file
    movw    r2, #0x00c0
    movt    r2, #0x0008
    movs    r3, #0
    movw    r7, #NR_openat
    svc     #0
    cmp     r0, #0
    bmi     .Lreturn

    adr     r0, hook_h1
    bl      .Ltouch_marker

    sub     sp, sp, #16
    movs    r7, #NR_gettid
    svc     #0
    str     r0, [sp, #8]

    /* clone(CHILD_SETTID|CHILD_CLEARTID|SIGCHLD, ..., child_tid=&ctid) */
    ldr     r0, =0x01200011
    movs    r1, #0
    movs    r2, #0
    movs    r3, #0
    add     r4, sp, #8
    movs    r7, #NR_clone
    svc     #0
    str     r0, [sp, #8]
    add     sp, sp, #16
    cmp     r0, #0
    beq     .Lworker_entry
    bmi     .Lexit
    adr     r0, hook_h2
    bl      .Ltouch_marker
    b       .Lreturn

.Lworker_entry:

    /* Worker: fork the context-setting child. */
    sub     sp, sp, #32
    movs    r0, #SIGCHLD
    movs    r1, #0
    movs    r2, #0
    movs    r3, #0
    movs    r4, #0
    movs    r7, #NR_clone
    svc     #0
    cbnz    r0, .Lworker_wait

    /* Child: set SELinux exec context, then exec the module loader. */
    adr     r0, hook_h3
    bl      .Ltouch_marker
    mvn     r0, #99
    adr     r1, attr_exec
    movs    r2, #1
    movt    r2, #0x0008
    movs    r7, #NR_openat
    svc     #0
    adr     r1, selinux_ctx
    movs    r2, #23
    movs    r7, #NR_write
    svc     #0
    cmp     r0, #23
    bne     .Lexit
    adr     r0, hook_h4
    bl      .Ltouch_marker

    adr     r1, exe_path
    str     r1, [sp]
    adr     r1, ko_target
    str     r1, [sp, #4]
    movs    r1, #0
    str     r1, [sp, #8]
    adr     r0, exe_path
    mov     r1, sp
    movs    r2, #0
    adr     r0, hook_h5
    bl      .Ltouch_marker
    adr     r0, exe_path
    mov     r1, sp
    movs    r2, #0
    movs    r7, #NR_execve
    svc     #0
    adr     r0, hook_h6
    bl      .Ltouch_marker
    b       .Lexit

.Lworker_wait:
    mov     r1, #0
    mov     r2, #0
    mov     r3, #0
    movs    r7, #NR_wait4
    svc     #0
    adr     r0, hook_h7
    bl      .Ltouch_marker

    adr     r0, hook_h8
    bl      .Ltouch_marker
    adr     r1, bootstrap_path
    str     r1, [sp]
    movs    r1, #0
    str     r1, [sp, #4]
    adr     r0, bootstrap_path
    movs    r2, #0
    movs    r7, #NR_execve
    svc     #0
    adr     r0, hook_h9
    bl      .Ltouch_marker

.Lexit:
    movs    r0, #1
    movs    r7, #NR_exit
    svc     #0
    b       .Lexit

.Lreturn:
    ldmia.w sp!, {r0-r7, r12, lr}
    /* Replay the overwritten function entry and branch back into libc++. */
    b.w     libcxx_first_inst_copy

/* Best-effort durable-in-/dev stage marker. Preserve all registers used by hook. */
.Ltouch_marker:
    push.w  {r0-r7, r12, lr}
    mvn     r0, #99
    mov     r1, sp
    ldr     r1, [r1, #0]
    movw    r2, #0x00c0
    movt    r2, #0x0008
    movs    r3, #0
    movw    r7, #NR_openat
    svc     #0
    cmp     r0, #0
    bmi     .Ltouch_done
    mov     r1, r0
    movs    r7, #NR_close
    svc     #0
.Ltouch_done:
    pop.w   {r0-r7, r12, lr}
    bx      lr

    .ltorg
    .balign 4
libcxx_first_inst_copy:
    /* C code replaces these 4 bytes with the original Thumb instructions. */
    nop
    nop
    b.w     .Lend
.Lend:
libcxx_end:
libcxx_len:
    .word libcxx_end - libcxx_data
