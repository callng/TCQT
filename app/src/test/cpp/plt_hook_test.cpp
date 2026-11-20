#include "zygisk/plt/plt_hook.h"

#include <dlfcn.h>
#include <unistd.h>
#include <atomic>
#include <chrono>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <string>
#include <thread>
#include <vector>

using namespace tcqt::zygisk;
namespace {
constexpr int kDelta = 700000;
pid_t (*real_pid)() = nullptr;
uid_t (*real_uid)() = nullptr;
std::atomic<int> pid_calls{0}, uid_calls{0};
pid_t replacement_pid() { ++pid_calls; return real_pid() + kDelta; }
uid_t replacement_uid() { ++uid_calls; return real_uid() + kDelta; }
void require(bool ok, const char *message) {
    if (!ok) { fprintf(stderr, "FAIL: %s\n", message); std::exit(1); }
}
void *load(const std::string &path) {
    void *handle = dlopen(path.c_str(), RTLD_NOW | RTLD_LOCAL);
    if (handle == nullptr) fprintf(stderr, "dlopen: %s\n", dlerror());
    require(handle != nullptr, "load fixture with real Android linker");
    return handle;
}
template<typename T> T symbol(void *handle, const char *name) {
    auto value = reinterpret_cast<T>(dlsym(handle, name));
    require(value != nullptr, name);
    return value;
}
std::vector<std::string> protections(const std::string &path) {
    std::ifstream maps("/proc/self/maps");
    std::vector<std::string> result;
    for (std::string line; std::getline(maps, line);) {
        if (line.find(path) != std::string::npos) result.push_back(line);
    }
    require(!result.empty(), "fixture mappings visible");
    bool readonly = false;
    for (const auto &line : result) if (line.find(" r--p ") != std::string::npos) readonly = true;
    require(readonly, "fixture has read-only mappings including RELRO");
    return result;
}
}

int main(int argc, char **argv) {
    setbuf(stdout, nullptr);
    require(argc == 4, "usage: test directory fixture mode");
    const std::string dir = argv[1], lib = argv[2], mode = argv[3];
    const std::string path = dir + "/" + lib;
    printf("PLT_TEST_PID=%d uid=%d page_size=%ld mode=%s library=%s\n",
           getpid(), getuid(), sysconf(_SC_PAGESIZE), mode.c_str(), lib.c_str());
    if (mode == "default" || mode == "disabled") {
        void *handle = load(path);
        auto read_file = symbol<int (*)(const char *)>(handle, "fixture_read_file");
        require(read_file("/proc/self/smaps") >= 0, "unhooked smaps readable");
        const int version = read_file("/proc/version");
        auto before = protections(path);
        install_default_plt_hooks(mode == "disabled"
                ? std::vector<std::string>{default_plt_hooks()[0].id} : std::vector<std::string>{});
        require(mode == "disabled" ? read_file("/proc/self/smaps") >= 0
                                   : read_file("/proc/self/smaps") == EOF,
                "default fopen hook obeys enabled/disabled scope");
        require(read_file("/proc/version") == version, "other paths retain real fopen behavior");
        require(protections(path) == before, "RELRO protection restored");
        void *decoy = load(dir + "/libtcqt_plt_decoy.so");
        require(symbol<int (*)(const char *)>(decoy, "fixture_read_file")("/proc/self/smaps") >= 0,
                "non-target library unaffected");
        printf("PASS: production default hook, scope, original fopen and RELRO\n");
    } else {
        const int original_pid = getpid(), original_uid = static_cast<int>(getuid());
        bool late = mode == "late" || mode == "retry";
        void *handle = late ? nullptr : load(path);
        std::vector<std::string> before;
        if (handle != nullptr) before = protections(path);
        if (mode == "retry") load(path + ".noimports");
        PltHookSpec specs[] = {
            {"pid", lib.c_str(), "getpid", reinterpret_cast<void *>(replacement_pid),
             reinterpret_cast<void **>(&real_pid)},
            {"uid", lib.c_str(), "getuid", reinterpret_cast<void *>(replacement_uid),
             reinterpret_cast<void **>(&real_uid)},
        };
        if (mode == "concurrent") {
            std::atomic<bool> start{false};
            std::vector<std::thread> threads;
            for (int i = 0; i < 8; ++i) threads.emplace_back([&] {
                while (!start.load()) std::this_thread::yield();
                install_plt_hooks(specs, 2);
            });
            start.store(true);
            for (auto &thread : threads) thread.join();
        } else {
            install_plt_hooks(specs, 2);
            install_plt_hooks(specs, 2);
        }
        std::atomic<bool> churn{true};
        std::thread loader;
        if (late) {
            loader = std::thread([&] {
                while (churn.load()) {
                    void *other = load(dir + "/libtcqt_plt_decoy.so");
                    dlclose(other);
                    usleep(1000);
                }
            });
            usleep(120000);
            handle = load(path);
        }
        auto call_plt = symbol<int (*)()>(handle, "fixture_call_plt");
        auto call_got = symbol<int (*)()>(handle, "fixture_call_got");
        const auto deadline = std::chrono::steady_clock::now() + std::chrono::seconds(3);
        while ((call_plt() != original_pid + kDelta || call_got() != original_uid + kDelta) &&
               std::chrono::steady_clock::now() < deadline) usleep(10000);
        churn.store(false);
        if (loader.joinable()) loader.join();
        require(call_plt() == original_pid + kDelta, "JUMP_SLOT replacement calls real function");
        require(call_got() == original_uid + kDelta, "GLOB_DAT replacement calls real function");
        require(getpid() == original_pid && static_cast<int>(getuid()) == original_uid,
                "caller outside selected library unaffected");
        require(symbol<int (*)()>(handle, "fixture_relative")() == 42,
                "relative relocations (including RELR) unaffected");
        if (!before.empty()) require(protections(path) == before, "all mapping protections restored");
        std::vector<std::thread> callers;
        for (int i = 0; i < 4; ++i) callers.emplace_back([&] {
            for (int j = 0; j < 10000; ++j) {
                require(call_plt() == original_pid + kDelta, "concurrent PLT call");
                require(call_got() == original_uid + kDelta, "concurrent GOT call");
            }
        });
        for (auto &thread : callers) thread.join();
        require(pid_calls >= 40000 && uid_calls >= 40000, "replacement callback counts");
        printf("PASS: PLT/GOT, original calls, scope, relative data and 80000 concurrent calls\n");
    }
    printf("PLT_RUNTIME_PASS %s %s\n", lib.c_str(), mode.c_str());
    return 0;
}
