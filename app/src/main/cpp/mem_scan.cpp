#include <jni.h>
#include <android/log.h>

#include <cerrno>
#include <fcntl.h>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <sys/mman.h>
#include <sys/prctl.h>
#include <unistd.h>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "GetSign", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "GetSign", __VA_ARGS__)

namespace {

constexpr size_t kSource32Length = 32;
constexpr size_t kChunkSize = 1024 * 1024;  // 1 MiB
constexpr size_t kPageSize = 4096;

// P-256 椭圆曲线群的阶 n
constexpr uint8_t kP256Order[kSource32Length] = {
    0xff, 0xff, 0xff, 0xff, 0x00, 0x00, 0x00, 0x00,
    0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
    0xbc, 0xe6, 0xfa, 0xad, 0xa7, 0x17, 0x9e, 0x84,
    0xf3, 0xb9, 0xca, 0xc2, 0xfc, 0x63, 0x25, 0x51,
};

struct Region {
    uint64_t start;
    uint64_t end;
};

bool is_source32_candidate(uint64_t address, const uint8_t* c) {
    if (address < 0x10) return false;
    uint32_t encoded =
        static_cast<uint32_t>(c[0]) |
        (static_cast<uint32_t>(c[1]) << 8) |
        (static_cast<uint32_t>(c[2]) << 16) |
        (static_cast<uint32_t>(c[3]) << 24);
    // address.wrapping_sub(0x10) as u32（截断到低 32 位）
    if (encoded != static_cast<uint32_t>(address - 0x10)) return false;
    for (int i = 18; i < 32; ++i) {
        if ((c[i] & 1) == 0) return false;
    }
    bool non_zero = false;
    for (int i = 0; i < 32; ++i) {
        if (c[i] != 0) {
            non_zero = true;
            break;
        }
    }
    if (!non_zero) return false;
    for (int i = 0; i < 32; ++i) {
        if (c[i] != kP256Order[i]) return c[i] < kP256Order[i];
    }
    return false;
}

// reader：把 [addr, addr+len) 读入 dst，返回实际读到的字节数（< kSource32Length 视为读取失败）
typedef size_t (*ReaderFn)(uint8_t* dst, uint64_t addr, size_t len, void* ctx);

// 在 rw 区域内按 1 MiB 分块（31 字节重叠）扫描候选块
// 返回 1=命中（out 写入 64 位小写 hex + NUL），0=未命中
int scan_regions(const Region* regions, size_t region_count,
                 uint8_t* buffer, ReaderFn reader, void* ctx,
                 char* out) {
    for (size_t r = 0; r < region_count; ++r) {
        uint64_t chunk_start = regions[r].start;
        const uint64_t end = regions[r].end;
        while (chunk_start < end) {
            uint64_t remaining = end - chunk_start;
            size_t read_len =
                remaining < (kChunkSize + kSource32Length - 1)
                    ? static_cast<size_t>(remaining)
                    : kChunkSize + kSource32Length - 1;
            size_t got = reader(buffer, chunk_start, read_len, ctx);
            if (got < kSource32Length) break;  // 读取失败，跳过整个区域
            size_t scan_len = got - (kSource32Length - 1);
            if (scan_len > kChunkSize) scan_len = kChunkSize;
            for (size_t offset = 0; offset < scan_len; ++offset) {
                if (is_source32_candidate(chunk_start + offset,
                                          buffer + offset)) {
                    static const char hex[] = "0123456789abcdef";
                    for (size_t i = 0; i < kSource32Length; ++i) {
                        uint8_t b = buffer[offset + i];
                        out[i * 2] = hex[b >> 4];
                        out[i * 2 + 1] = hex[b & 0x0f];
                    }
                    out[kSource32Length * 2] = '\0';
                    // LOGI("hit at 0x%llx", static_cast<unsigned long long>(chunk_start + offset));
                    return 1;
                }
            }
            chunk_start += kChunkSize;
        }
    }
    return 0;
}

size_t read_proc_mem(uint8_t* dst, uint64_t addr, size_t len, void* ctx) {
    int fd = static_cast<int>(reinterpret_cast<intptr_t>(ctx));
    ssize_t n = pread(fd, dst, len, static_cast<off_t>(addr));
    return n > 0 ? static_cast<size_t>(n) : 0;
}

struct MincoreCtx {
    unsigned char* vec;
};

size_t read_mincore(uint8_t* dst, uint64_t addr, size_t len, void* ctx) {
    if (mincore(reinterpret_cast<void*>(addr), len,
                static_cast<MincoreCtx*>(ctx)->vec) != 0) {
        return 0;  // 含未映射页，跳过该块
    }
    memcpy(dst, reinterpret_cast<void*>(addr), len);
    return len;
}

// 扫描自身内存，命中则 out 写入 64 位小写 hex 并返回 1，否则返回 0
int scan_source32(char* out) {
    // 1. 解析 /proc/self/maps 的 rw 区域
    FILE* maps = fopen("/proc/self/maps", "r");
    if (maps == nullptr) {
        LOGE("open /proc/self/maps failed");
        return 0;
    }
    size_t cap = 1024;
    auto* regions = static_cast<Region*>(malloc(cap * sizeof(Region)));
    if (regions == nullptr) {
        fclose(maps);
        return 0;
    }
    size_t region_count = 0;
    char line[1024];
    while (fgets(line, sizeof(line), maps) != nullptr) {
        unsigned long long start = 0, end = 0;
        char perms[8] = {0};
        if (sscanf(line, "%llx-%llx %7s", &start, &end, perms) != 3) {
            continue;
        }
        if (strncmp(perms, "rw", 2) != 0) continue;
        if (end - start < kSource32Length) continue;
        if (region_count == cap) {
            cap *= 2;
            auto* grown =
                static_cast<Region*>(realloc(regions, cap * sizeof(Region)));
            if (grown == nullptr) break;
            regions = grown;
        }
        regions[region_count].start = static_cast<uint64_t>(start);
        regions[region_count].end = static_cast<uint64_t>(end);
        ++region_count;
    }
    fclose(maps);

    if (region_count == 0) {
        free(regions);
        // LOGI("no rw regions");
        return 0;
    }
    // LOGI("native scan start, regions=%zu", region_count);

    auto* buffer =
        static_cast<uint8_t*>(malloc(kChunkSize + kSource32Length - 1));
    if (buffer == nullptr) {
        free(regions);
        return 0;
    }

    int hit = 0;

    // 2. 方式一：/proc/self/mem + prctl(PR_SET_DUMPABLE, 1)
    int original_dumpable = prctl(PR_GET_DUMPABLE, 0, 0, 0, 0);
    if (original_dumpable != 1) {
        prctl(PR_SET_DUMPABLE, 1, 0, 0, 0);
    }
    // LOGI("dumpable original=%d", original_dumpable);

    int fd = open("/proc/self/mem", O_RDONLY | O_CLOEXEC);
    if (fd >= 0) {
        hit = scan_regions(regions, region_count, buffer, read_proc_mem,
                           reinterpret_cast<void*>(static_cast<intptr_t>(fd)),
                           out);
        close(fd);
        // LOGI("proc mem scan done, hit=%d", hit);
    } else {
        LOGE("open /proc/self/mem failed: %s (errno=%d)", strerror(errno),
             errno);

        // 3. 方式二：mincore 校验后直读
        size_t vec_pages =
            (kChunkSize + kSource32Length - 1 + kPageSize - 1) / kPageSize;
        auto* vec = static_cast<unsigned char*>(malloc(vec_pages));
        if (vec != nullptr) {
            MincoreCtx ctx = {vec};
            // LOGI("mincore fallback active");
            hit = scan_regions(regions, region_count, buffer, read_mincore,
                               &ctx, out);
            free(vec);
        }
    }

    if (original_dumpable != 1) {
        prctl(PR_SET_DUMPABLE, original_dumpable, 0, 0, 0);
    }

    free(buffer);
    free(regions);
    // if (!hit) LOGI("scan finished, no hit");
    return hit;
}

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_owo233_tcqt_hooks_func_fekit_GetSign_nativeScanSource32(
    JNIEnv* env, jobject /*thiz*/) {
    char out[64 + 1];
    return env->NewStringUTF(scan_source32(out) ? out : "");
}
