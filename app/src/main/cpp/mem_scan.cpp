#include <jni.h>
#include <android/log.h>

#include <cerrno>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>

#include <fcntl.h>
#include <sys/prctl.h>
#include <sys/types.h>
#include <unistd.h>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "GetSign", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "GetSign", __VA_ARGS__)

namespace {

    constexpr size_t kSource32Length = 32;
    constexpr size_t kChunkSize = 1024 * 1024;

    constexpr uint8_t kP256Order[kSource32Length] = {
            0xff, 0xff, 0xff, 0xff,
            0x00, 0x00, 0x00, 0x00,
            0xff, 0xff, 0xff, 0xff,
            0xff, 0xff, 0xff, 0xff,
            0xbc, 0xe6, 0xfa, 0xad,
            0xa7, 0x17, 0x9e, 0x84,
            0xf3, 0xb9, 0xca, 0xc2,
            0xfc, 0x63, 0x25, 0x51,
    };

    struct Region {
        uint64_t start;
        uint64_t end;
    };

    bool is_source32_candidate(uint64_t address, const uint8_t* candidate) {
        if (address < 0x10 || candidate == nullptr) {
            return false;
        }

        const uint32_t encoded_pointer =
                static_cast<uint32_t>(candidate[0]) |
                (static_cast<uint32_t>(candidate[1]) << 8) |
                (static_cast<uint32_t>(candidate[2]) << 16) |
                (static_cast<uint32_t>(candidate[3]) << 24);

        if (encoded_pointer != static_cast<uint32_t>(address - 0x10)) {
            return false;
        }

        for (size_t i = 18; i < kSource32Length; ++i) {
            if ((candidate[i] & 1U) == 0) {
                return false;
            }
        }

        bool non_zero = false;
        for (size_t i = 0; i < kSource32Length; ++i) {
            if (candidate[i] != 0) {
                non_zero = true;
                break;
            }
        }

        if (!non_zero) {
            return false;
        }

        for (size_t i = 0; i < kSource32Length; ++i) {
            if (candidate[i] != kP256Order[i]) {
                return candidate[i] < kP256Order[i];
            }
        }

        return false;
    }

    size_t read_proc_mem(uint8_t* dst, uint64_t address, size_t length, int fd) {
        if (dst == nullptr || fd < 0 || length == 0) {
            return 0;
        }

        const ssize_t read = pread(
                fd,
                dst,
                length,
                static_cast<off_t>(address));

        return read > 0 ? static_cast<size_t>(read) : 0;
    }

    int scan_regions(
            const Region* regions,
            size_t region_count,
            uint8_t* buffer,
            int mem_fd,
            char* out) {

        if (regions == nullptr ||
            buffer == nullptr ||
            mem_fd < 0 ||
            out == nullptr) {
            return 0;
        }

        static constexpr char kHex[] = "0123456789abcdef";

        for (size_t region_index = 0;
             region_index < region_count;
             ++region_index) {

            const uint64_t region_start = regions[region_index].start;
            const uint64_t region_end = regions[region_index].end;

            uint64_t chunk_start = region_start;

            while (chunk_start < region_end) {
                const uint64_t remaining = region_end - chunk_start;

                const uint64_t max_read =
                        static_cast<uint64_t>(
                                kChunkSize + kSource32Length - 1);

                const size_t read_length =
                        static_cast<size_t>(
                                remaining < max_read
                                ? remaining
                                : max_read);

                const size_t read = read_proc_mem(
                        buffer,
                        chunk_start,
                        read_length,
                        mem_fd);

                if (read < kSource32Length) {
                    break;
                }

                size_t scan_length =
                        read - (kSource32Length - 1);

                if (scan_length > kChunkSize) {
                    scan_length = kChunkSize;
                }

                for (size_t offset = 0;
                     offset < scan_length;
                     ++offset) {

                    const uint64_t address =
                            chunk_start + offset;

                    const uint8_t* candidate =
                            buffer + offset;

                    if (!is_source32_candidate(
                            address,
                            candidate)) {
                        continue;
                    }

                    for (size_t i = 0;
                         i < kSource32Length;
                         ++i) {

                        const uint8_t byte = candidate[i];

                        out[i * 2] =
                                kHex[byte >> 4];

                        out[i * 2 + 1] =
                                kHex[byte & 0x0f];
                    }

                    out[kSource32Length * 2] = '\0';
                    LOGI("source32 hit at 0x%llx", static_cast<unsigned long long>(address));
                    return 1;
                }

                chunk_start += kChunkSize;
            }
        }

        return 0;
    }

    int scan_source32(char* out) {
        if (out == nullptr) {
            return 0;
        }

        FILE* maps = fopen("/proc/self/maps", "r");
        if (maps == nullptr) {
            LOGE("open /proc/self/maps failed: %s (errno=%d)", strerror(errno), errno);
            return 0;
        }

        size_t region_capacity = 1024;

        auto* regions =
                static_cast<Region*>(
                        malloc(region_capacity * sizeof(Region)));

        if (regions == nullptr) {
            fclose(maps);
            LOGE("allocate region list failed");
            return 0;
        }

        size_t region_count = 0;

        char line[1024];

        while (fgets(line, sizeof(line), maps) != nullptr) {
            unsigned long long start = 0;
            unsigned long long end = 0;

            char permissions[8] = {0};

            if (sscanf(
                    line,
                    "%llx-%llx %7s",
                    &start,
                    &end,
                    permissions) != 3) {
                continue;
            }

            if (permissions[0] != 'r' ||
                permissions[1] != 'w') {
                continue;
            }

            if (end <= start) {
                continue;
            }

            const uint64_t region_start =
                    static_cast<uint64_t>(start);

            const uint64_t region_end =
                    static_cast<uint64_t>(end);

            if (region_end - region_start <
                kSource32Length) {
                continue;
            }

            if (region_count == region_capacity) {
                const size_t new_capacity =
                        region_capacity * 2;

                auto* grown =
                        static_cast<Region*>(
                                realloc(
                                        regions,
                                        new_capacity * sizeof(Region)));

                if (grown == nullptr) {
                    LOGE("resize region list failed");
                    break;
                }

                regions = grown;
                region_capacity = new_capacity;
            }

            regions[region_count].start = region_start;
            regions[region_count].end = region_end;

            ++region_count;
        }

        fclose(maps);

        if (region_count == 0) {
            free(regions);
            LOGI("no rw regions found");
            return 0;
        }

        LOGI("rw region collection completed, regions=%zu", region_count);

        const size_t buffer_size =
                kChunkSize + kSource32Length - 1;

        auto* buffer =
                static_cast<uint8_t*>(
                        malloc(buffer_size));

        if (buffer == nullptr) {
            free(regions);
            LOGE("allocate scan buffer failed, size=%zu", buffer_size);
            return 0;
        }

        const int original_dumpable =
                prctl(PR_GET_DUMPABLE, 0, 0, 0, 0);

        bool dumpable_changed = false;

        if (original_dumpable >= 0 &&
            original_dumpable != 1) {

            if (prctl(
                    PR_SET_DUMPABLE,
                    1,
                    0,
                    0,
                    0) == 0) {
                dumpable_changed = true;
            } else {
                LOGE("set dumpable failed: %s (errno=%d)", strerror(errno), errno);
            }
        }

        const int mem_fd =
                open(
                        "/proc/self/mem",
                        O_RDONLY | O_CLOEXEC);

        if (mem_fd < 0) {
            LOGE("open /proc/self/mem failed: %s (errno=%d)", strerror(errno), errno);

            if (dumpable_changed) {
                prctl(
                        PR_SET_DUMPABLE,
                        original_dumpable,
                        0,
                        0,
                        0);
            }

            free(buffer);
            free(regions);
            return 0;
        }

        LOGI("open /proc/self/mem completed");

        const int hit =
                scan_regions(
                        regions,
                        region_count,
                        buffer,
                        mem_fd,
                        out);

        close(mem_fd);

        if (dumpable_changed) {
            if (prctl(
                    PR_SET_DUMPABLE,
                    original_dumpable,
                    0,
                    0,
                    0) != 0) {

                LOGE("restore dumpable failed: %s (errno=%d)", strerror(errno), errno);
            }
        }

        free(buffer);
        free(regions);

        if (hit) {
            LOGI("source32 scan completed, hit=1");
        } else {
            LOGI("source32 scan completed, hit=0");
        }

        return hit;
    }
}  // namespace

extern "C"
JNIEXPORT jstring
Java_com_owo233_tcqt_features_debug_GetSign_nativeScanSource32(
        JNIEnv* env,
        jobject /* thiz */) {

    char out[kSource32Length * 2 + 1] = {0};

    if (scan_source32(out)) {
        return env->NewStringUTF(out);
    }

    return env->NewStringUTF("");
}
