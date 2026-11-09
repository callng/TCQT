# Identify the injector's build inputs, not incidental bytes in its ELF output.
# Git revisions, Java/resources, checkout paths and line endings are excluded.
function(tcqt_embed_injector_identity target)
    file(GLOB_RECURSE inputs CONFIGURE_DEPENDS
            "${PROJECT_SOURCE_DIR}/zygisk/*.cpp"
            "${PROJECT_SOURCE_DIR}/zygisk/*.h"
            "${PROJECT_SOURCE_DIR}/zygisk/*.hpp"
            "${PROJECT_SOURCE_DIR}/zygisk/*.S"
            "${PROJECT_SOURCE_DIR}/zygisk/*.inc"
            "${PROJECT_SOURCE_DIR}/zygisk/*.cmake"
            "${PROJECT_SOURCE_DIR}/zygisk/CMakeLists.txt")
    list(APPEND inputs "${PROJECT_SOURCE_DIR}/CMakeLists.txt" "${CMAKE_CURRENT_FUNCTION_LIST_FILE}")
    list(SORT inputs)
    # Source/header edits must refresh the identity on incremental Ninja builds,
    # even when Gradle reuses its CMake configuration.
    set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS ${inputs})
    set(manifest "TCQT_INJECTOR_ID_V1\n")
    foreach(input IN LISTS inputs)
        file(READ "${input}" content)
        string(REPLACE "\r\n" "\n" content "${content}")
        string(REPLACE "\r" "\n" content "${content}")
        string(SHA256 digest "${content}")
        file(RELATIVE_PATH relative "${PROJECT_SOURCE_DIR}" "${input}")
        string(APPEND manifest "source:${relative}=${digest}\n")
    endforeach()

    string(TOUPPER "${CMAKE_BUILD_TYPE}" config)
    # CMake exposes only major.minor for some NDKs (e.g. 30.0). Retain the
    # package revision too so a compiler/STL update cannot reuse the old ID.
    set(ndk_metadata "${CMAKE_ANDROID_NDK}/source.properties")
    if(NOT EXISTS "${ndk_metadata}")
        message(FATAL_ERROR "Cannot identify the injector's Android NDK revision")
    endif()
    file(STRINGS "${ndk_metadata}" ndk_revision REGEX "^Pkg\\.Revision[ \t]*=")
    string(STRIP "${ndk_revision}" ndk_revision)
    set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${ndk_metadata}")
    # Keep toolchain/ABI/optimization changes conservative: they can change the
    # native implementation even if the repository's C++ sources are unchanged.
    set(settings
            ndk_revision CMAKE_VERSION CMAKE_BUILD_TYPE CMAKE_SYSTEM_NAME CMAKE_SYSTEM_VERSION
            CMAKE_SYSTEM_PROCESSOR CMAKE_ANDROID_ARCH_ABI CMAKE_ANDROID_NDK_VERSION
            CMAKE_ANDROID_STL_TYPE ANDROID_ABI ANDROID_PLATFORM ANDROID_STL
            CMAKE_CXX_COMPILER_ID CMAKE_CXX_COMPILER_VERSION CMAKE_CXX_COMPILER_TARGET
            CMAKE_CXX_STANDARD CMAKE_CXX_STANDARD_REQUIRED CMAKE_CXX_EXTENSIONS
            CMAKE_CXX_FLAGS CMAKE_CXX_FLAGS_${config} CMAKE_CXX_STANDARD_LIBRARIES
            CMAKE_SHARED_LINKER_FLAGS CMAKE_SHARED_LINKER_FLAGS_${config} CMAKE_LINKER_TYPE)
    foreach(property IN ITEMS COMPILE_DEFINITIONS COMPILE_OPTIONS INCLUDE_DIRECTORIES
            LINK_OPTIONS LINK_LIBRARIES POSITION_INDEPENDENT_CODE
            INTERPROCEDURAL_OPTIMIZATION INTERPROCEDURAL_OPTIMIZATION_${config})
        get_target_property(value ${target} ${property})
        if(value STREQUAL "value-NOTFOUND")
            set(value "")
        endif()
        set(target_${property} "${value}")
        list(APPEND settings target_${property})
    endforeach()
    foreach(setting IN LISTS settings)
        set(value "${${setting}}")
        string(REPLACE "\\" "/" value "${value}")
        foreach(root IN ITEMS CMAKE_CURRENT_BINARY_DIR CMAKE_BINARY_DIR
                CMAKE_CURRENT_SOURCE_DIR PROJECT_SOURCE_DIR CMAKE_ANDROID_NDK ANDROID_NDK)
            if(DEFINED ${root} AND NOT "${${root}}" STREQUAL "")
                file(TO_CMAKE_PATH "${${root}}" prefix)
                string(REPLACE "${prefix}" "<${root}>" value "${value}")
            endif()
        endforeach()
        string(APPEND manifest "config:${setting}=${value}\n")
    endforeach()
    string(SHA256 identity "${manifest}")
    target_compile_definitions(${target} PRIVATE TCQT_INJECTOR_COMPAT_ID="${identity}")
    file(WRITE "${CMAKE_CURRENT_BINARY_DIR}/injector-identity.inputs" "${manifest}")
    file(WRITE "${CMAKE_CURRENT_BINARY_DIR}/injector-identity.txt" "${identity}\n")
endfunction()
