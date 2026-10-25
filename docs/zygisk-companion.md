# Zygisk root companion

preAppSpecialize runs in the Zygote SELinux domain. UID 0 does not imply that
the process can traverse /data/adb. A denial of zygote -> adb_data_file:dir search
used to make main.apk inaccessible and silently hide configuration markers.

zygisk_companion.cpp now handles privileged reads through
REGISTER_ZYGISK_COMPANION and Api::connectCompanion():

- The request contains only a QQ/TIM identifier and Android UID. Paths are fixed
  by the module, and user scope is derived from uid / 100000.
- Every request reads the live module disable/remove markers, host scope,
  compatibility/debug markers, per-host/user PLT switches, and payload hash.
  Only ENOENT counts as an absent marker; other I/O failures abort injection.
- Each request opens a fresh, read-only, non-symlink main.apk, validates its
  type/size, and transfers one FD with SCM_RIGHTS. There is no persistent payload
  FD or shared file cursor, so concurrent processes and payload updates stay separate.
- The receiver validates protocol version, response sizes, flags, hash, and FD.
  IPC has five-second send/receive timeouts, rejects surplus FDs, and closes
  received FDs on errors. The framework owns the handler-side socket.
- The existing exemptFd, app-private APK cache, and Dex loader remain in use.
  Post-specialization validation compares device/inode/size to avoid mistaking a
  reused FD number for the payload.

## Regression checks

On Windows, with the project's pinned NDK and an explicitly selected Android
test device or emulator:

~~~powershell
./tools/test-zygisk-companion.ps1 -Serial emulator-5556
~~~

The tests cover host/user isolation, live module replacement, missing/malformed
payload metadata, permission failures, malformed IPC, independent file cursors,
and FD cleanup. A separate child cannot open the payload path but can read the
companion's FD: root tests drop its UID, and unprivileged tests deny its open
syscalls using a child-only seccomp filter.
This validates native IPC; it does not establish successful QQ injection on the
affected user's SELinux policy and Zygisk implementation.

## Device verification

The injector library changes, so install the rebuilt dual-format package and
reboot the device. Then start QQ/TIM and export the module's WebUI logs.
Expected checkpoints are companion ready, session start, and the subsequent
Java/host bootstrap messages. Failures report the companion stage and errno;
an unavailable framework companion is reported explicitly. Configuration changes
continue to apply on the next host start without rebooting.
