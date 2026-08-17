// so_hider.cpp

#include "so_hider.h"

#include <cerrno>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <fcntl.h>
#include <string>
#include <sys/mman.h>
#include <sys/syscall.h>
#include <unistd.h>
#include <vector>

#include "log.h"

#ifndef MFD_CLOEXEC
#define MFD_CLOEXEC 0x0001U
#endif

namespace tcqt {
    namespace {

        constexpr const char *kMemfdName = "libfekit.so";

        struct Mapping {
            uintptr_t start = 0;
            uintptr_t end = 0;
            uintptr_t offset = 0;
            int prot = PROT_NONE;
            std::string path;

            size_t size() const {
                return end - start;
            }
        };

        struct Snapshot {
            Mapping mapping;
            int fd = -1;
        };

        class ScopedFd {
        public:
            explicit ScopedFd(int fd = -1) : fd_(fd) {}

            ~ScopedFd() {
                reset();
            }

            ScopedFd(const ScopedFd &) = delete;
            ScopedFd &operator=(const ScopedFd &) = delete;

            ScopedFd(ScopedFd &&other) noexcept : fd_(other.fd_) {
                other.fd_ = -1;
            }

            ScopedFd &operator=(ScopedFd &&other) noexcept {
                if (this != &other) {
                    reset();
                    fd_ = other.fd_;
                    other.fd_ = -1;
                }
                return *this;
            }

            int get() const {
                return fd_;
            }

            int release() {
                int fd = fd_;
                fd_ = -1;
                return fd;
            }

            void reset(int fd = -1) {
                if (fd_ >= 0) {
                    close(fd_);
                }
                fd_ = fd;
            }

        private:
            int fd_;
        };

        bool parse_maps_line(const char *line, Mapping *out) {
            if (line == nullptr || out == nullptr) {
                return false;
            }

            unsigned long long start = 0;
            unsigned long long end = 0;
            unsigned long long offset = 0;
            unsigned long long inode = 0;

            char perms[8] = {};
            char dev[64] = {};
            int consumed = 0;

            const int matched = std::sscanf(
                    line,
                    "%llx-%llx %7s %llx %63s %llu %n",
                    &start,
                    &end,
                    perms,
                    &offset,
                    dev,
                    &inode,
                    &consumed
            );

            if (matched != 6 || consumed <= 0) {
                return false;
            }

            const char *path = line + consumed;

            while (*path == ' ' ||
                   *path == '\t' ||
                   *path == '\r' ||
                   *path == '\n') {
                ++path;
            }

            // We only care about file-backed mappings.
            if (*path == '\0' || *path == '[') {
                return false;
            }

            int prot = PROT_NONE;

            if (std::strchr(perms, 'r') != nullptr) {
                prot |= PROT_READ;
            }

            if (std::strchr(perms, 'w') != nullptr) {
                prot |= PROT_WRITE;
            }

            if (std::strchr(perms, 'x') != nullptr) {
                prot |= PROT_EXEC;
            }

            out->start = static_cast<uintptr_t>(start);
            out->end = static_cast<uintptr_t>(end);
            out->offset = static_cast<uintptr_t>(offset);
            out->prot = prot;
            out->path = path;

            return out->start < out->end;
        }

        std::vector<Mapping> collect_matching_mappings(const char *needle) {
            std::vector<Mapping> result;

            if (needle == nullptr || *needle == '\0') {
                return result;
            }

            FILE *fp = std::fopen("/proc/self/maps", "re");
            if (fp == nullptr) {
                LOGE(
                        "SoHider: fopen(/proc/self/maps) failed errno=%d",
                        errno
                );
                return result;
            }

            char *line = nullptr;
            size_t capacity = 0;

            while (getline(&line, &capacity, fp) >= 0) {
                Mapping mapping;

                if (!parse_maps_line(line, &mapping)) {
                    continue;
                }

                if (mapping.path.find(needle) == std::string::npos) {
                    continue;
                }

                result.emplace_back(std::move(mapping));
            }

            free(line);
            std::fclose(fp);

            return result;
        }

        bool write_full(
                int fd,
                const void *buffer,
                size_t size,
                off_t offset
        ) {
            const auto *src =
                    static_cast<const unsigned char *>(buffer);

            while (size != 0) {
                const ssize_t written =
                        pwrite(fd, src, size, offset);

                if (written < 0) {
                    if (errno == EINTR) {
                        continue;
                    }

                    return false;
                }

                if (written == 0) {
                    errno = EIO;
                    return false;
                }

                src += written;
                size -= static_cast<size_t>(written);
                offset += written;
            }

            return true;
        }

        int create_memfd(size_t size) {
            const int fd = static_cast<int>(
                    syscall(
                            SYS_memfd_create,
                            kMemfdName,
                            MFD_CLOEXEC
                    )
            );

            if (fd < 0) {
                return -1;
            }

            if (ftruncate(fd, static_cast<off_t>(size)) != 0) {
                const int saved_errno = errno;
                close(fd);
                errno = saved_errno;
                return -1;
            }

            return fd;
        }

        bool snapshot_mapping(const Mapping &mapping, Snapshot *out) {
            if (out == nullptr) {
                return false;
            }

            if (mapping.start == 0 ||
                mapping.end <= mapping.start) {
                return false;
            }

            const size_t size = mapping.size();

            if (size == 0) {
                return false;
            }

            ScopedFd fd(create_memfd(size));

            if (fd.get() < 0) {
                LOGE(
                        "SoHider: memfd_create/ftruncate failed "
                        "@%p size=%zu errno=%d",
                        reinterpret_cast<void *>(mapping.start),
                        size,
                        errno
                );
                return false;
            }

            bool temporarily_readable = false;

            if ((mapping.prot & PROT_READ) == 0) {
                const int temporary_prot =
                        mapping.prot | PROT_READ;

                if (mprotect(
                        reinterpret_cast<void *>(mapping.start),
                        size,
                        temporary_prot
                ) != 0) {
                    LOGE(
                            "SoHider: temporary mprotect(+R) failed "
                            "@%p size=%zu errno=%d",
                            reinterpret_cast<void *>(mapping.start),
                            size,
                            errno
                    );
                    return false;
                }

                temporarily_readable = true;
            }

            const bool copied = write_full(
                    fd.get(),
                    reinterpret_cast<const void *>(mapping.start),
                    size,
                    0
            );

            int restore_errno = 0;

            if (temporarily_readable) {
                if (mprotect(
                        reinterpret_cast<void *>(mapping.start),
                        size,
                        mapping.prot
                ) != 0) {
                    restore_errno = errno;
                }
            }

            if (restore_errno != 0) {
                LOGE(
                        "SoHider: restore original protection failed "
                        "@%p size=%zu prot=%d errno=%d",
                        reinterpret_cast<void *>(mapping.start),
                        size,
                        mapping.prot,
                        restore_errno
                );

                return false;
            }

            if (!copied) {
                LOGE(
                        "SoHider: snapshot failed "
                        "@%p size=%zu errno=%d",
                        reinterpret_cast<void *>(mapping.start),
                        size,
                        errno
                );
                return false;
            }

            out->mapping = mapping;
            out->fd = fd.release();

            return true;
        }

        bool mapping_still_matches(const Mapping &expected) {
            FILE *fp = std::fopen("/proc/self/maps", "re");
            if (fp == nullptr) {
                return false;
            }

            char *line = nullptr;
            size_t capacity = 0;

            bool found = false;

            while (getline(&line, &capacity, fp) >= 0) {
                Mapping current;

                if (!parse_maps_line(line, &current)) {
                    continue;
                }

                if (current.start != expected.start ||
                    current.end != expected.end) {
                    continue;
                }

                if (current.offset != expected.offset) {
                    continue;
                }

                if (current.prot != expected.prot) {
                    continue;
                }

                if (current.path != expected.path) {
                    continue;
                }

                found = true;
                break;
            }

            free(line);
            std::fclose(fp);

            return found;
        }

        bool remap_snapshot(Snapshot &snapshot) {
            const Mapping &mapping = snapshot.mapping;

            if (snapshot.fd < 0) {
                return false;
            }

            if (!mapping_still_matches(mapping)) {
                LOGW(
                        "SoHider: mapping changed before remap "
                        "@%p-%p, skip",
                        reinterpret_cast<void *>(mapping.start),
                        reinterpret_cast<void *>(mapping.end)
                );
                return false;
            }

            void *target =
                    reinterpret_cast<void *>(mapping.start);

            const size_t size = mapping.size();

            void *result = mmap(
                    target,
                    size,
                    mapping.prot,
                    MAP_PRIVATE | MAP_FIXED,
                    snapshot.fd,
                    0
            );

            if (result == MAP_FAILED) {
                LOGE(
                        "SoHider: mmap(MAP_FIXED) failed "
                        "@%p size=%zu prot=%d errno=%d",
                        target,
                        size,
                        mapping.prot,
                        errno
                );
                return false;
            }

            if (result != target) {
                LOGE(
                        "SoHider: mmap returned unexpected address "
                        "@%p expected=%p",
                        result,
                        target
                );

                return false;
            }

            close(snapshot.fd);
            snapshot.fd = -1;

            return true;
        }

        void destroy_snapshot(Snapshot &snapshot) {
            if (snapshot.fd >= 0) {
                close(snapshot.fd);
                snapshot.fd = -1;
            }
        }

        int hide_mappings(const std::vector<Mapping> &mappings) {
            if (mappings.empty()) {
                return 0;
            }

            /*
             * Phase 1:
             *
             * Snapshot every mapping BEFORE touching any original mapping.
             *
             * This is the critical difference from the original implementation.
             */
            std::vector<Snapshot> snapshots;
            snapshots.reserve(mappings.size());

            for (const Mapping &mapping : mappings) {
                if (mapping.prot == PROT_NONE) {
                    continue;
                }

                Snapshot snapshot;

                if (!snapshot_mapping(mapping, &snapshot)) {
                    // No remapping has happened yet. Previously prepared snapshots
                    // are discarded and the original mappings remain untouched.
                    for (Snapshot &prepared : snapshots) {
                        destroy_snapshot(prepared);
                    }

                    return 0;
                }

                snapshots.emplace_back(std::move(snapshot));
            }

            /*
             * Phase 2:
             *
             * All runtime bytes are already captured.
             *
             * From here on we only replace mappings.
             */
            int remapped = 0;

            for (Snapshot &snapshot : snapshots) {
                if (remap_snapshot(snapshot)) {
                    ++remapped;
                }
            }

            for (Snapshot &snapshot : snapshots) {
                destroy_snapshot(snapshot);
            }

            return remapped;
        }

    }  // namespace

    int hide_path(const char *needle) {
        if (needle == nullptr || *needle == '\0') {
            return 0;
        }

        const std::vector<Mapping> mappings =
                collect_matching_mappings(needle);

        if (mappings.empty()) {
            return 0;
        }

        const int remapped = hide_mappings(mappings);

        LOGI(
                "SoHider: hide_path(%s): %d/%zu mappings remapped",
                needle,
                remapped,
                mappings.size()
        );

        return remapped;
    }

    int hide_paths(
            const char *const *needles,
            std::size_t count
    ) {
        if (needles == nullptr || count == 0) {
            return 0;
        }

        int total = 0;

        for (std::size_t i = 0; i < count; ++i) {
            if (needles[i] == nullptr ||
                needles[i][0] == '\0') {
                continue;
            }

            total += hide_path(needles[i]);
        }

        return total;
    }

}  // namespace tcqt