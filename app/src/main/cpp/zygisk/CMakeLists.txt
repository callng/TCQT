# Zygisk injector only; excluded from the installable APK's lib/ directory.
add_library(tcqtzygisk SHARED
        zygisk_entry.cpp
        zygisk_companion.cpp
        bootstrap.cpp
        payload.cpp
        jni_bridge.cpp
        art/art_hook.cpp
        art/elf_util.cpp
        art/trampoline.cpp
        plt/plt_hook.cpp
        plt/default_plt_hooks.cpp
        logging/log_file.cpp)

tcqt_configure_native_target(tcqtzygisk)
