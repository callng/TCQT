# Zygisk injector only; excluded from the installable APK's lib/ directory.
add_library(tcqtzygisk SHARED
        zygisk_entry.cpp
        zygisk_companion.cpp
        bootstrap.cpp
        payload.cpp
        jni_bridge.cpp
        injector_identity.cpp
        art/art_hook.cpp
        art/art_layout.cpp
        art/art_symbols.cpp
        art/art_memory.cpp
        art/art_method_patch.cpp
        art/art_invoke.cpp
        art/elf_util.cpp
        art/trampoline.cpp
        plt/plt_hook.cpp
        plt/plt_elf.cpp
        plt/default_plt_hooks.cpp
        logging/log_file.cpp)

tcqt_configure_native_target(tcqtzygisk)
include("${PROJECT_SOURCE_DIR}/cmake/InjectorIdentity.cmake")
tcqt_embed_injector_identity(tcqtzygisk)
