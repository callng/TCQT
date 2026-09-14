cmake_minimum_required(VERSION 3.22.1)
project(tcqtzygisk CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

if(NOT CMAKE_BUILD_TYPE AND NOT CMAKE_CONFIGURATION_TYPES)
    set(CMAKE_BUILD_TYPE Release CACHE STRING "" FORCE)
endif()

set(CMAKE_CXX_FLAGS_RELEASE "-O2 -g0 -fno-exceptions -fno-rtti -DNDEBUG")
set(CMAKE_INTERPROCEDURAL_OPTIMIZATION_RELEASE ON)

add_library(tcqtzygisk SHARED
        zygisk_entry.cpp
        payload.cpp
        art_hook.cpp
        elf_util.cpp
        trampoline.cpp
        jni_bridge.cpp
        plt_hook.cpp
        log_file.cpp)

target_compile_features(tcqtzygisk PRIVATE cxx_std_20)

target_compile_options(tcqtzygisk PRIVATE
        -fvisibility=hidden
        -ffunction-sections
        -fdata-sections
        -Wall
        -Wno-unused-parameter)

target_link_options(tcqtzygisk PRIVATE
        -Wl,--gc-sections)

target_include_directories(tcqtzygisk PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR})

target_link_libraries(tcqtzygisk
        log)

add_custom_command(TARGET tcqtzygisk POST_BUILD
        COMMAND ${CMAKE_STRIP} --strip-unneeded $<TARGET_FILE:tcqtzygisk>)


# libtcqtmem.so
add_library(tcqtmem SHARED
        mem_scan.cpp)

target_compile_features(tcqtmem PRIVATE cxx_std_20)

target_compile_options(tcqtmem PRIVATE
        -fvisibility=hidden
        -ffunction-sections
        -fdata-sections
        -Wall
        -Wno-unused-parameter)

target_link_options(tcqtmem PRIVATE
        -Wl,--gc-sections)

target_include_directories(tcqtmem PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR})

target_link_libraries(tcqtmem
        log)

add_custom_command(TARGET tcqtmem POST_BUILD
        COMMAND ${CMAKE_STRIP} --strip-unneeded $<TARGET_FILE:tcqtmem>)