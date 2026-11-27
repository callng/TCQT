include(FetchContent)
set(Plugin.SymbolResolver OFF CACHE BOOL "" FORCE)
set(Plugin.Android.BionicLinkerUtil OFF CACHE BOOL "" FORCE)
set(NearBranch OFF CACHE BOOL "" FORCE)
set(FullFloatingPointRegisterPack ON CACHE BOOL "" FORCE)
FetchContent_Declare(dobby
    URL https://codeload.github.com/jmpews/Dobby/tar.gz/0932d69c320e786672361ab53825ba8f4245e9d3
    URL_HASH SHA256=1f5b1e612d54f21d40aefb5d0c15fc21d6699b5b402fb657886ec9a0f5d5b829)
FetchContent_GetProperties(dobby)
if(NOT dobby_POPULATED)
    cmake_policy(PUSH)
    cmake_policy(SET CMP0169 OLD)
    FetchContent_Populate(dobby)
    cmake_policy(POP)
endif()
# Upstream's CMake 3.5 baseline and shared target need adaptation for CMake 4
# and this single-library package. Patch only the downloaded build directory.
set(CMAKE_POLICY_VERSION_MINIMUM 3.10)
file(READ "${dobby_SOURCE_DIR}/CMakeLists.txt" dobby_cmake)
string(REPLACE "add_library(dobby SHARED" "add_library(dobby STATIC" dobby_cmake "${dobby_cmake}")
file(WRITE "${dobby_SOURCE_DIR}/CMakeLists.txt" "${dobby_cmake}")

# Each logged byte needs two hex digits, a space and a terminating NUL.
set(dobby_routing_path "${dobby_SOURCE_DIR}/source/InterceptRouting/InterceptRouting.cpp")
file(READ "${dobby_routing_path}" dobby_routing)
string(REPLACE
    "for (int i = 0; i < buffer_size && i < sizeof(output); i++)"
    "for (uint32_t i = 0; i < buffer_size && i < (sizeof(output) - 1) / 3; i++)"
    dobby_routing "${dobby_routing}")
string(REPLACE
    "snprintf(output + strlen(output), 3, \"%02x \", *((uint8_t *)buffer + i));"
    "snprintf(output + i * 3, 4, \"%02x \", buffer[i]);"
    dobby_routing "${dobby_routing}")
file(WRITE "${dobby_routing_path}" "${dobby_routing}")

# Keep Dobby's 2048-byte line limit without redefining the platform's LINE_MAX.
set(dobby_runtime_path "${dobby_SOURCE_DIR}/source/Backend/UserMode/PlatformUtil/Linux/ProcessRuntimeUtility.cc")
file(READ "${dobby_runtime_path}" dobby_runtime)
string(REPLACE "#define LINE_MAX 2048" "constexpr int kDobbyLineMax = 2048;" dobby_runtime "${dobby_runtime}")
string(REPLACE "LINE_MAX" "kDobbyLineMax" dobby_runtime "${dobby_runtime}")
file(WRITE "${dobby_runtime_path}" "${dobby_runtime}")

add_subdirectory(${dobby_SOURCE_DIR} ${dobby_BINARY_DIR} EXCLUDE_FROM_ALL)
set_target_properties(dobby PROPERTIES OUTPUT_NAME dobby_unused)
target_compile_options(dobby_static PRIVATE -UDOBBY_DEBUG -DDOBBY_LOGGING_DISABLE)
add_library(tcqtrecall SHARED kernel_recall.cpp kernel_scan.cpp)
tcqt_configure_native_target(tcqtrecall)
target_include_directories(tcqtrecall PRIVATE "${dobby_SOURCE_DIR}/include")
target_link_libraries(tcqtrecall dobby_static dl)
target_link_options(tcqtrecall PRIVATE -Wl,--exclude-libs,ALL)
