cmake_minimum_required(VERSION 3.22.1)
project(tcqtzygisk CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Zygisk modules are loaded into the Zygote process. Statically link libc++
# so the module never depends on a runtime libc++ shared object that could
# conflict with the host process.
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fvisibility=hidden -ffunction-sections -fdata-sections -Wall -Wno-unused-parameter")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--gc-sections")

add_library(tcqtzygisk SHARED
        zygisk_entry.cpp
        payload.cpp
        art_hook.cpp
        elf_util.cpp
        trampoline.cpp
        jni_bridge.cpp
        plt_hook.cpp)

target_include_directories(tcqtzygisk PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
target_link_libraries(tcqtzygisk log)
