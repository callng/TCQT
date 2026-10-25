#include "zygisk_companion.h"

#include <dirent.h>
#include <fcntl.h>
#include <linux/filter.h>
#include <linux/seccomp.h>
#include <sys/prctl.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/wait.h>
#include <unistd.h>

#include <array>
#include <atomic>
#include <cerrno>
#include <cstddef>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <filesystem>
#include <fstream>
#include <string>
#include <thread>
#include <utility>

namespace fs = std::filesystem;
using namespace tcqt;

#define CHECK(condition) do { if (!(condition)) { \
    fprintf(stderr, "FAIL line %d: %s (errno=%d)\n", __LINE__, #condition, errno); \
    abort(); \
} } while (0)

constexpr std::array<const char *, 2> HOOKS{"key1", "future_hook"};
constexpr const char *PAYLOAD = "TCQT companion payload fixture";
const std::string HASH(64, 'a');
int passed = 0;

void pass(const char *name) { printf("PASS %s\n", name); ++passed; }
void put(const std::string &path, const std::string &value = "") {
    std::ofstream file(path, std::ios::binary);
    CHECK(file.good());
    file << value;
    file.close();
    CHECK(file.good());
}

struct Fixture {
    std::string root;
    CompanionPaths paths;
    explicit Fixture(const char *parent) {
        std::string pattern = std::string(parent) + "/tcqt-companion-XXXXXX";
        CHECK(mkdtemp(pattern.data()) != nullptr);
        root = pattern;
        paths = {root + "/module", root + "/data"};
        fs::create_directory(paths.module_dir);
        fs::create_directory(paths.data_dir);
        put(paths.data_dir + "/main.apk", PAYLOAD);
        put(paths.data_dir + "/main.apk.sha256", HASH);
    }
    ~Fixture() { fs::remove_all(root); }
};

struct Result {
    bool ok;
    int error;
    InjectionState state;
    Result(bool success, int code, InjectionState value)
        : ok(success), error(code), state(std::move(value)) {}
    Result(const Result &) = delete;
    ~Result() { if (state.apk_fd >= 0) close(state.apk_fd); }
};

Result exchange(const CompanionPaths &paths, InjectionTarget target = InjectionTarget::QQ,
                uid_t uid = 10348) {
    int sockets[2];
    CHECK(socketpair(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0, sockets) == 0);
    bool served = false;
    std::thread server([&] {
        served = handle_companion_request(sockets[1], HOOKS, paths);
        close(sockets[1]);
    });
    InjectionState state;
    bool ok = request_injection_state(sockets[0], target, uid, HOOKS.size(), state);
    int error = errno;
    close(sockets[0]);
    server.join();
    CHECK(served);
    return {ok, error, std::move(state)};
}

void check_payload(int fd) {
    char data[128]{};
    CHECK(read(fd, data, sizeof(data)) == static_cast<ssize_t>(strlen(PAYLOAD)));
    CHECK(strcmp(data, PAYLOAD) == 0);
    CHECK((fcntl(fd, F_GETFD) & FD_CLOEXEC) != 0);
}

int fd_count() {
    DIR *dir = opendir("/proc/self/fd");
    CHECK(dir != nullptr);
    int count = 0;
    while (readdir(dir) != nullptr) ++count;
    closedir(dir);
    return count;
}

void test_config(Fixture &f) {
    {
        auto result = exchange(f.paths);
        CHECK(result.ok && result.state.apk_fd >= 0);
        CHECK(!result.state.compat_mode && !result.state.debug_logs);
        CHECK(result.state.payload_hash == HASH);
        CHECK(result.state.disabled_hooks == std::vector<uint8_t>({0, 0}));
        check_payload(result.state.apk_fd);
    }
    {
        auto result = exchange(f.paths);
        CHECK(result.ok);
        check_payload(result.state.apk_fd);  // A fresh open, not a shared offset.
    }
    pass("payload transfer, CLOEXEC, and independent file offsets");
    put(f.paths.data_dir + "/compat.enable");
    put(f.paths.data_dir + "/debug.enable");
    put(f.paths.data_dir + "/qq.key1.disable");
    {
        auto result = exchange(f.paths);
        CHECK(result.ok && result.state.compat_mode && result.state.debug_logs);
        CHECK(result.state.disabled_hooks == std::vector<uint8_t>({1, 0}));
    }
    {
        auto result = exchange(f.paths, InjectionTarget::TIM);
        CHECK(result.ok && result.state.disabled_hooks == std::vector<uint8_t>({0, 0}));
    }
    pass("global compatibility/debug and QQ/TIM hook isolation");
    fs::remove(f.paths.data_dir + "/compat.enable");
    put(f.paths.data_dir + "/compat");
    { auto r = exchange(f.paths); CHECK(r.ok && r.state.compat_mode); }
    fs::remove(f.paths.data_dir + "/compat");
    fs::remove(f.paths.data_dir + "/debug.enable");
    pass("legacy compatibility marker");
    fs::create_directory(f.paths.data_dir + "/user_10");
    put(f.paths.data_dir + "/user_10/qq.future_hook.disable");
    {
        auto r = exchange(f.paths, InjectionTarget::QQ, 1010348);
        CHECK(r.ok && r.state.disabled_hooks == std::vector<uint8_t>({0, 1}));
    }
    put(f.paths.data_dir + "/qq.disable");
    { auto r = exchange(f.paths); CHECK(r.ok && r.state.scope_disabled && r.state.apk_fd < 0); }
    { auto r = exchange(f.paths, InjectionTarget::TIM); CHECK(r.ok && !r.state.scope_disabled); }
    { auto r = exchange(f.paths, InjectionTarget::QQ, 1010348); CHECK(r.ok && !r.state.scope_disabled); }
    put(f.paths.data_dir + "/user_10/tim.disable");
    { auto r = exchange(f.paths, InjectionTarget::TIM, 1010348); CHECK(r.ok && r.state.scope_disabled); }
    fs::remove(f.paths.data_dir + "/qq.disable");
    pass("per-user scope and hook isolation");
    for (const char *marker : {"disable", "remove"}) {
        put(f.paths.module_dir + "/" + marker);
        auto r = exchange(f.paths);
        CHECK(r.ok && r.state.module_disabled && r.state.apk_fd < 0);
        fs::remove(f.paths.module_dir + "/" + marker);
    }
    pass("live module disable/remove markers");
    fs::rename(f.paths.module_dir, f.root + "/old-module");
    fs::create_directory(f.paths.module_dir);
    put(f.paths.module_dir + "/disable");
    { auto r = exchange(f.paths); CHECK(r.ok && r.state.module_disabled); }
    fs::remove(f.paths.module_dir + "/disable");
    { auto r = exchange(f.paths); CHECK(r.ok && !r.state.module_disabled); }
    pass("hot-install module-directory replacement");
}

void test_files(Fixture &f) {
    fs::remove(f.paths.data_dir + "/main.apk.sha256");
    { auto r = exchange(f.paths); CHECK(r.ok && r.state.payload_hash.empty()); }
    pass("missing legacy hash requests cache refresh");
    put(f.paths.data_dir + "/main.apk.sha256", std::string(64, 'z'));
    { auto r = exchange(f.paths); CHECK(!r.ok && r.error == EINVAL && r.state.error_stage == 5); }
    put(f.paths.data_dir + "/main.apk.sha256", HASH + "\n");
    { auto r = exchange(f.paths); CHECK(r.ok && r.state.payload_hash == HASH); }
    pass("hash validation and trailing newline");
    fs::remove(f.paths.data_dir + "/main.apk");
    { auto r = exchange(f.paths); CHECK(!r.ok && r.error == ENOENT && r.state.error_stage == 3); }
    put(f.paths.module_dir + "/disable");
    { auto r = exchange(f.paths); CHECK(r.ok && r.state.module_disabled); }
    fs::remove(f.paths.module_dir + "/disable");
    pass("missing payload and disabled module without payload");
    put(f.root + "/other.apk", PAYLOAD);
    fs::create_symlink(f.root + "/other.apk", f.paths.data_dir + "/main.apk");
    { auto r = exchange(f.paths); CHECK(!r.ok && r.error == ELOOP); }
    fs::remove(f.paths.data_dir + "/main.apk");
    put(f.paths.data_dir + "/main.apk");
    { auto r = exchange(f.paths); CHECK(!r.ok && r.error == EINVAL); }
    fs::resize_file(f.paths.data_dir + "/main.apk", APK_MAX_BYTES + 1);
    { auto r = exchange(f.paths); CHECK(!r.ok && r.error == EINVAL); }
    put(f.paths.data_dir + "/main.apk", PAYLOAD);
    pass("symlink, empty, and oversized payload rejection");
    const std::string malformed = f.root + "/not-a-directory";
    put(malformed);
    { auto r = exchange({malformed, f.paths.data_dir}); CHECK(!r.ok && r.error == ENOTDIR); }
    pass("configuration I/O failure aborts instead of enabling injection");
    CHECK(chmod(f.paths.data_dir.c_str(), 0000) == 0);
    // Drop credentials in a separate process when tests run as root. The server
    // in this case must report EACCES, not reinterpret markers as absent.
    pid_t child = fork();
    CHECK(child >= 0);
    if (child == 0) {
        if (geteuid() == 0) CHECK(setuid(65534) == 0);
        auto r = exchange(f.paths);
        CHECK(!r.ok && r.error == EACCES && r.state.error_stage == 2);
        _exit(0);
    }
    int status;
    CHECK(waitpid(child, &status, 0) == child);
    CHECK(chmod(f.paths.data_dir.c_str(), 0700) == 0);
    CHECK(WIFEXITED(status) && WEXITSTATUS(status) == 0);
    pass("permission-denied marker access is an explicit failure");
}

void test_bad_responses(Fixture &f) {
    for (int kind = 0; kind < 6; ++kind) {
        int before = fd_count(), sockets[2];
        CHECK(socketpair(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0, sockets) == 0);
        std::thread server([&] {
            uint32_t request[4];
            CHECK(recv(sockets[1], request, sizeof(request), MSG_WAITALL) == sizeof(request));
            uint32_t response[6]{0x54435154, 1, 0, 0, 0, 2};
            if (kind == 0) response[1] = 99;  // Unsupported protocol.
            if (kind == 4) response[5] = 3;   // Wrong hook count.
            int fd = open((f.paths.data_dir + "/main.apk").c_str(), O_RDONLY | O_CLOEXEC);
            CHECK(fd >= 0);
            iovec io{response, kind == 2 ? 5u : sizeof(response)};
            alignas(cmsghdr) char control[CMSG_SPACE(2 * sizeof(int))]{};
            msghdr message{};
            message.msg_iov = &io;
            message.msg_iovlen = 1;
            if (kind != 3) {
                message.msg_control = control;
                message.msg_controllen = CMSG_SPACE((kind == 1 ? 2 : 1) * sizeof(int));
                auto *header = CMSG_FIRSTHDR(&message);
                header->cmsg_level = SOL_SOCKET;
                header->cmsg_type = SCM_RIGHTS;
                header->cmsg_len = CMSG_LEN((kind == 1 ? 2 : 1) * sizeof(int));
                int descriptors[2]{fd, fd};
                memcpy(CMSG_DATA(header), descriptors, (kind == 1 ? 2 : 1) * sizeof(int));
            }
            CHECK(sendmsg(sockets[1], &message, MSG_NOSIGNAL) > 0);
            if (kind == 5) {
                std::array<char, 67> body{};
                body.fill('x');  // Missing hash terminator and invalid hook flags.
                CHECK(send(sockets[1], body.data(), body.size(), MSG_NOSIGNAL) == body.size());
            }
            close(fd);
            close(sockets[1]);
        });
        InjectionState state;
        CHECK(!request_injection_state(sockets[0], InjectionTarget::QQ, 10348, HOOKS.size(), state));
        CHECK(state.apk_fd < 0);
        close(sockets[0]);
        server.join();
        CHECK(fd_count() == before);
    }
    pass("malformed/truncated IPC and surplus FD rejection without leaks");
    int sockets[2];
    CHECK(socketpair(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0, sockets) == 0);
    close(sockets[1]);
    InjectionState state;
    CHECK(!request_injection_state(sockets[0], InjectionTarget::QQ, 10348, HOOKS.size(), state));
    close(sockets[0]);
    pass("closed peer does not raise SIGPIPE");
    { auto r = exchange(f.paths, static_cast<InjectionTarget>(999)); CHECK(!r.ok && r.error == EINVAL); }
    pass("non-whitelisted host rejection");
}

void test_concurrent(Fixture &f) {
    int before = fd_count();
    std::array<std::thread, 12> threads;
    for (auto &thread : threads) thread = std::thread([&] {
        auto r = exchange(f.paths);
        CHECK(r.ok);
        check_payload(r.state.apk_fd);
    });
    for (auto &thread : threads) thread.join();
    CHECK(fd_count() == before);
    pass("concurrent requests have independent payload cursors and no FD leaks");
}

void test_privilege_boundary(Fixture &f) {
    int sockets[2];
    CHECK(socketpair(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0, sockets) == 0);
    fflush(stdout);
    pid_t child = fork();
    CHECK(child >= 0);
    if (child == 0) {
        close(sockets[1]);
        if (geteuid() == 0) {
            CHECK(setuid(65534) == 0);
        } else {
            // A production emulator has no root. Restrict only this child:
            // filesystem opens fail with EACCES, while received FDs remain usable.
            sock_filter instructions[]{
                BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(seccomp_data, nr)),
                BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, __NR_openat, 0, 1),
                BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ERRNO | EACCES),
#ifdef __NR_open
                BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, __NR_open, 0, 1),
                BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ERRNO | EACCES),
#endif
                BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ALLOW),
            };
            sock_fprog program{static_cast<unsigned short>(std::size(instructions)), instructions};
            CHECK(prctl(PR_SET_NO_NEW_PRIVS, 1, 0, 0, 0) == 0);
            CHECK(prctl(PR_SET_SECCOMP, SECCOMP_MODE_FILTER, &program) == 0);
        }
        CHECK(open((f.paths.data_dir + "/main.apk").c_str(), O_RDONLY) == -1 && errno == EACCES);
        InjectionState state;
        CHECK(request_injection_state(sockets[0], InjectionTarget::QQ, 10348, HOOKS.size(), state));
        check_payload(state.apk_fd);
        close(state.apk_fd);
        close(sockets[0]);
        _exit(0);
    }
    close(sockets[0]);
    CHECK(handle_companion_request(sockets[1], HOOKS, f.paths));
    close(sockets[1]);
    int status;
    CHECK(waitpid(child, &status, 0) == child);
    CHECK(WIFEXITED(status) && WEXITSTATUS(status) == 0);
    pass("restricted client reads companion FD despite denied filesystem traversal");
}

int main(int argc, char **argv) {
    Fixture fixture(argc > 1 ? argv[1] : "/data/local/tmp");
    test_config(fixture);
    test_files(fixture);
    test_bad_responses(fixture);
    test_concurrent(fixture);
    test_privilege_boundary(fixture);
    printf("PASS all %d companion regression groups\n", passed);
}
