cmake_minimum_required(VERSION 4.1.2)
project(tcqt_native CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

if(NOT CMAKE_BUILD_TYPE AND NOT CMAKE_CONFIGURATION_TYPES)
    set(CMAKE_BUILD_TYPE Release CACHE STRING "" FORCE)
endif()

set(CMAKE_CXX_FLAGS_RELEASE "-O2 -g0 -fno-exceptions -fno-rtti -DNDEBUG")
set(CMAKE_INTERPROCEDURAL_OPTIMIZATION_RELEASE ON)

function(tcqt_configure_native_target target)
    target_compile_features(${target} PRIVATE cxx_std_20)
    target_compile_options(${target} PRIVATE
            -fvisibility=hidden
            -ffunction-sections
            -fdata-sections
            -Wall
            -Wno-unused-parameter)
    target_link_options(${target} PRIVATE -Wl,--gc-sections)
    target_include_directories(${target} PRIVATE "${PROJECT_SOURCE_DIR}")
    target_link_libraries(${target} log)
    add_custom_command(TARGET ${target} POST_BUILD
            COMMAND ${CMAKE_STRIP} --strip-unneeded $<TARGET_FILE:${target}>)
endfunction()

add_subdirectory(zygisk)

# GetSign JNI scanner, also used outside Zygisk mode.
add_library(tcqtmem SHARED source32_scanner.cpp)
tcqt_configure_native_target(tcqtmem)
