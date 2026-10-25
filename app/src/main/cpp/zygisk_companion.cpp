#include "zygisk_companion.h"

#include <fcntl.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/time.h>
#include <unistd.h>

#include <array>
#include <cerrno>
#include <cctype>
#include <climits>
#include <cstring>

namespace tcqt {
namespace {

constexpr uint32_t MAGIC = 0x54435154;
constexpr uint32_t VERSION = 1;
constexpr size_t MAX_HOOKS = 256;
constexpr uint32_t MODULE_DISABLED = 1, SCOPE_DISABLED = 2, COMPAT = 4, DEBUG = 8;
enum Stage : uint32_t { TRANSPORT, REQUEST, CONFIG, PAYLOAD_OPEN, PAYLOAD_STAT, HASH };

struct Request {
    uint32_t magic, version, target, uid;
};
struct Response {
    uint32_t magic = MAGIC, version = VERSION;
    int32_t error = 0;
    uint32_t stage = TRANSPORT, flags = 0, hook_count = 0;
};
static_assert(sizeof(Request) == 16 && sizeof(Response) == 24);

bool socket_timeout(int socket) {
    const timeval timeout{5, 0};
    return setsockopt(socket, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout)) == 0 &&
           setsockopt(socket, SOL_SOCKET, SO_SNDTIMEO, &timeout, sizeof(timeout)) == 0;
}

bool read_exact(int socket, void *buffer, size_t size) {
    auto *data = static_cast<char *>(buffer);
    while (size != 0) {
        ssize_t n = recv(socket, data, size, 0);
        if (n < 0 && errno == EINTR) continue;
        if (n <= 0) {
            if (n == 0) errno = ECONNRESET;
            return false;
        }
        data += n;
        size -= static_cast<size_t>(n);
    }
    return true;
}

bool write_exact(int socket, const void *buffer, size_t size) {
    const auto *data = static_cast<const char *>(buffer);
    while (size != 0) {
        ssize_t n = send(socket, data, size, MSG_NOSIGNAL);
        if (n < 0 && errno == EINTR) continue;
        if (n <= 0) {
            if (n == 0) errno = EIO;
            return false;
        }
        data += n;
        size -= static_cast<size_t>(n);
    }
    return true;
}

bool send_response(int socket, Response &response, int fd = -1) {
    iovec io{&response, sizeof(response)};
    alignas(cmsghdr) char control[CMSG_SPACE(sizeof(int))]{};
    msghdr message{};
    message.msg_iov = &io;
    message.msg_iovlen = 1;
    if (fd >= 0) {
        message.msg_control = control;
        message.msg_controllen = sizeof(control);
        auto *header = CMSG_FIRSTHDR(&message);
        header->cmsg_level = SOL_SOCKET;
        header->cmsg_type = SCM_RIGHTS;
        header->cmsg_len = CMSG_LEN(sizeof(int));
        memcpy(CMSG_DATA(header), &fd, sizeof(fd));
    }
    ssize_t n;
    do {
        n = sendmsg(socket, &message, MSG_NOSIGNAL);
    } while (n < 0 && errno == EINTR);
    if (n <= 0) return false;
    // Ancillary data must only be sent once, even after a short write.
    return write_exact(socket, reinterpret_cast<const char *>(&response) + n,
                       sizeof(response) - static_cast<size_t>(n));
}

bool receive_response(int socket, Response &response, int &fd) {
    iovec io{&response, sizeof(response)};
    alignas(cmsghdr) char control[CMSG_SPACE(4 * sizeof(int))]{};
    msghdr message{};
    message.msg_iov = &io;
    message.msg_iovlen = 1;
    message.msg_control = control;
    message.msg_controllen = sizeof(control);
    ssize_t n;
    do {
        n = recvmsg(socket, &message, MSG_CMSG_CLOEXEC);
    } while (n < 0 && errno == EINTR);
    if (n <= 0) {
        if (n == 0) errno = ECONNRESET;
        return false;
    }
    bool invalid = (message.msg_flags & (MSG_CTRUNC | MSG_TRUNC)) != 0;
    for (auto *header = CMSG_FIRSTHDR(&message); header != nullptr;
         header = CMSG_NXTHDR(&message, header)) {
        if (header->cmsg_level != SOL_SOCKET || header->cmsg_type != SCM_RIGHTS ||
            header->cmsg_len < CMSG_LEN(0)) {
            invalid = true;
            continue;
        }
        size_t bytes = header->cmsg_len - CMSG_LEN(0);
        if (bytes % sizeof(int) != 0) invalid = true;
        for (size_t i = 0; i + sizeof(int) <= bytes; i += sizeof(int)) {
            int received;
            memcpy(&received, CMSG_DATA(header) + i, sizeof(received));
            if (fd < 0) fd = received;
            else {
                close(received);
                invalid = true;
            }
        }
    }
    if (invalid) {
        errno = EPROTO;
        return false;
    }
    return read_exact(socket, reinterpret_cast<char *>(&response) + n,
                      sizeof(response) - static_cast<size_t>(n));
}

bool marker_exists(const std::string &path, bool &exists) {
    struct stat st{};
    if (lstat(path.c_str(), &st) == 0) {
        exists = true;
        return true;
    }
    exists = false;
    // EACCES/EPERM are errors, not an absent (and therefore enabled) marker.
    return errno == ENOENT;
}

bool read_hash(const std::string &path, std::array<char, 65> &hash) {
    int fd = open(path.c_str(), O_RDONLY | O_CLOEXEC | O_NOFOLLOW);
    if (fd < 0) return errno == ENOENT;  // Legacy installs refresh the cache.
    struct stat st{};
    if (fstat(fd, &st) != 0 || !S_ISREG(st.st_mode) || st.st_size > 128) {
        close(fd);
        errno = EINVAL;
        return false;
    }
    std::array<char, 129> data{};
    size_t used = 0;
    bool ok = true;
    while (used < data.size()) {
        ssize_t n = read(fd, data.data() + used, data.size() - used);
        if (n < 0 && errno == EINTR) continue;
        if (n < 0) { ok = false; break; }
        if (n == 0) break;
        used += static_cast<size_t>(n);
    }
    int error = errno;
    close(fd);
    if (!ok) { errno = error; return false; }
    while (used > 0 && std::isspace(static_cast<unsigned char>(data[used - 1]))) --used;
    if (used != 64) { errno = EINVAL; return false; }
    for (size_t i = 0; i < used; ++i) {
        if (!std::isxdigit(static_cast<unsigned char>(data[i]))) {
            errno = EINVAL;
            return false;
        }
    }
    memcpy(hash.data(), data.data(), used);
    return true;
}

}  // namespace

const char *companion_stage_name(uint32_t stage) {
    switch (stage) {
        case REQUEST: return "request";
        case CONFIG: return "configuration";
        case PAYLOAD_OPEN: return "payload-open";
        case PAYLOAD_STAT: return "payload-stat";
        case HASH: return "payload-hash";
        default: return "transport";
    }
}

bool handle_companion_request(int socket, std::span<const char *const> hook_ids,
                              const CompanionPaths &paths) {
    if (!socket_timeout(socket)) return false;
    Request request{};
    if (!read_exact(socket, &request, sizeof(request))) return false;
    Response response;
    auto fail = [&](Stage stage, int error) {
        response.stage = stage;
        response.error = error != 0 ? error : EIO;
        return send_response(socket, response);
    };
    if (request.magic != MAGIC || request.version != VERSION || request.uid > INT_MAX ||
        (request.target != static_cast<uint32_t>(InjectionTarget::QQ) &&
         request.target != static_cast<uint32_t>(InjectionTarget::TIM)) ||
        hook_ids.size() > MAX_HOOKS) return fail(REQUEST, EINVAL);

    // Always inspect the live directory: hot install can move the old module inode.
    bool disabled = false, removed = false;
    if (!marker_exists(paths.module_dir + "/disable", disabled) ||
        !marker_exists(paths.module_dir + "/remove", removed)) return fail(CONFIG, errno);
    if (disabled || removed) {
        response.flags = MODULE_DISABLED;
        return send_response(socket, response);
    }
    const char *host = request.target == static_cast<uint32_t>(InjectionTarget::QQ) ? "qq" : "tim";
    std::string scope = paths.data_dir;
    uint32_t user = request.uid / 100000;
    if (user != 0) scope += "/user_" + std::to_string(user);
    if (!marker_exists(scope + "/" + host + ".disable", disabled)) return fail(CONFIG, errno);
    if (disabled) {
        response.flags = SCOPE_DISABLED;
        return send_response(socket, response);
    }
    bool compat = false, legacy_compat = false, debug = false;
    if (!marker_exists(paths.data_dir + "/compat.enable", compat) ||
        !marker_exists(paths.data_dir + "/compat", legacy_compat) ||
        !marker_exists(paths.data_dir + "/debug.enable", debug)) return fail(CONFIG, errno);
    response.flags = (compat || legacy_compat ? COMPAT : 0) | (debug ? DEBUG : 0);
    std::vector<uint8_t> disabled_hooks(hook_ids.size());
    for (size_t i = 0; i < hook_ids.size(); ++i) {
        if (!marker_exists(scope + "/" + host + "." + hook_ids[i] + ".disable", disabled))
            return fail(CONFIG, errno);
        disabled_hooks[i] = disabled ? 1 : 0;
    }

    int fd = open((paths.data_dir + "/main.apk").c_str(), O_RDONLY | O_CLOEXEC | O_NOFOLLOW);
    if (fd < 0) return fail(PAYLOAD_OPEN, errno);
    struct stat st{};
    if (fstat(fd, &st) != 0 || !S_ISREG(st.st_mode) || st.st_size <= 0 ||
        static_cast<uint64_t>(st.st_size) > APK_MAX_BYTES) {
        close(fd);
        return fail(PAYLOAD_STAT, EINVAL);
    }
    std::array<char, 65> hash{};
    if (!read_hash(paths.data_dir + "/main.apk.sha256", hash)) {
        int error = errno;
        close(fd);
        return fail(HASH, error);
    }
    response.hook_count = static_cast<uint32_t>(hook_ids.size());
    bool ok = send_response(socket, response, fd) &&
              write_exact(socket, hash.data(), hash.size()) &&
              write_exact(socket, disabled_hooks.data(), disabled_hooks.size());
    int error = errno;
    close(fd);  // Each request opens its own file description; offsets are never shared.
    errno = error;
    return ok;
}

bool request_injection_state(int socket, InjectionTarget target, uid_t uid,
                             size_t hook_count, InjectionState &out) {
    out = {};
    if (hook_count > MAX_HOOKS) { errno = EINVAL; return false; }
    if (!socket_timeout(socket)) return false;
    const Request request{MAGIC, VERSION, static_cast<uint32_t>(target), uid};
    if (!write_exact(socket, &request, sizeof(request))) return false;
    Response response;
    int fd = -1;
    auto fail = [&](int error) {
        if (fd >= 0) close(fd);
        errno = error;
        return false;
    };
    if (!receive_response(socket, response, fd)) return fail(errno);
    if (response.magic != MAGIC || response.version != VERSION ||
        response.stage > HASH || (response.flags & ~15u) != 0) return fail(EPROTO);
    out.error_stage = response.stage;
    if (response.error != 0) return fail(response.error > 0 ? response.error : EPROTO);
    out.module_disabled = (response.flags & MODULE_DISABLED) != 0;
    out.scope_disabled = (response.flags & SCOPE_DISABLED) != 0;
    if (out.module_disabled || out.scope_disabled) {
        if (fd >= 0 || response.hook_count != 0) return fail(EPROTO);
        return true;
    }
    if (fd < 0 || response.hook_count != hook_count) return fail(EPROTO);
    std::array<char, 65> hash{};
    out.disabled_hooks.resize(hook_count);
    if (!read_exact(socket, hash.data(), hash.size()) ||
        !read_exact(socket, out.disabled_hooks.data(), hook_count)) return fail(errno);
    if (hash.back() != '\0') return fail(EPROTO);
    size_t hash_size = strlen(hash.data());
    if (hash_size != 0 && hash_size != 64) return fail(EPROTO);
    for (size_t i = 0; i < hash_size; ++i)
        if (!std::isxdigit(static_cast<unsigned char>(hash[i]))) return fail(EPROTO);
    for (uint8_t disabled : out.disabled_hooks)
        if (disabled > 1) return fail(EPROTO);
    struct stat st{};
    if (fstat(fd, &st) != 0 || !S_ISREG(st.st_mode) || st.st_size <= 0 ||
        static_cast<uint64_t>(st.st_size) > APK_MAX_BYTES) return fail(EINVAL);
    out.compat_mode = (response.flags & COMPAT) != 0;
    out.debug_logs = (response.flags & DEBUG) != 0;
    out.payload_hash.assign(hash.data(), hash_size);
    out.apk_fd = fd;
    return true;
}

}  // namespace tcqt
