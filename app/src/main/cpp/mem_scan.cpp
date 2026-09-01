#include <jni.h>
#include <android/log.h>

#include <cerrno>
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/prctl.h>
#include <unistd.h>

#include <algorithm>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <string>
#include <utility>
#include <vector>

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

/**
 * 通用扫描：reader 把 [chunk_start, chunk_start+read_len) 读入 buffer，
 * 返回实际读到的字节数；返回值 < kSource32Length 视为该块读取失败，跳过整个区域
 */
template <typename Reader>
std::string scan_regions(
    const std::vector<std::pair<uint64_t, uint64_t>>& regions,
    std::vector<uint8_t>& buffer, Reader reader) {
    for (const auto& region : regions) {
        uint64_t chunk_start = region.first;
        const uint64_t end = region.second;
        while (chunk_start < end) {
            size_t read_len = static_cast<size_t>(
                std::min<uint64_t>(end - chunk_start,
                                   kChunkSize + kSource32Length - 1));
            size_t got = reader(buffer.data(), chunk_start, read_len);
            if (got < kSource32Length) break;
            size_t scan_len =
                std::min(got - (kSource32Length - 1), kChunkSize);
            for (size_t offset = 0; offset < scan_len; ++offset) {
                if (is_source32_candidate(chunk_start + offset,
                                          buffer.data() + offset)) {
                    static const char hex[] = "0123456789abcdef";
                    std::string out;
                    out.reserve(kSource32Length * 2);
                    for (size_t i = 0; i < kSource32Length; ++i) {
                        uint8_t b = buffer[offset + i];
                        out.push_back(hex[b >> 4]);
                        out.push_back(hex[b & 0x0f]);
                    }
                    // LOGI("hit at 0x%llx", static_cast<unsigned long long>(chunk_start + offset));
                    return out;
                }
            }
            chunk_start += kChunkSize;
        }
    }
    return "";
}

std::vector<std::pair<uint64_t, uint64_t>> read_rw_regions() {
    std::vector<std::pair<uint64_t, uint64_t>> regions;
    FILE* maps = fopen("/proc/self/maps", "r");
    if (maps == nullptr) {
        // LOGE("open /proc/self/maps failed");
        return regions;
    }
    char line[1024];
    while (fgets(line, sizeof(line), maps) != nullptr) {
        unsigned long long start = 0, end = 0;
        char perms[8] = {0};
        // 行格式: start-end perms offset dev inode pathname
        if (sscanf(line, "%llx-%llx %7s", &start, &end, perms) != 3) {
            continue;
        }
        if (strncmp(perms, "rw", 2) != 0) continue;
        if (end - start < kSource32Length) continue;
        regions.emplace_back(static_cast<uint64_t>(start),
                             static_cast<uint64_t>(end));
    }
    fclose(maps);
    return regions;
}

/**
 * 方式一：/proc/self/mem + prctl(PR_SET_DUMPABLE, 1)。
 * mem_opened 输出 fd 是否成功打开（打开成功即以本次结果为准，含未命中）。
 */
std::string scan_via_proc_mem(
    const std::vector<std::pair<uint64_t, uint64_t>>& regions,
    std::vector<uint8_t>& buffer, bool* mem_opened) {
    *mem_opened = false;
    int original_dumpable = prctl(PR_GET_DUMPABLE, 0, 0, 0, 0);
    if (original_dumpable != 1) {
        prctl(PR_SET_DUMPABLE, 1, 0, 0, 0);
    }
    // LOGI("dumpable original=%d", original_dumpable);

    std::string result;
    int fd = open("/proc/self/mem", O_RDONLY | O_CLOEXEC);
    if (fd >= 0) {
        *mem_opened = true;
        result = scan_regions(
            regions, buffer,
            [fd](uint8_t* dst, uint64_t addr, size_t len) -> size_t {
                ssize_t n = pread(fd, dst, len, static_cast<off_t>(addr));
                return n > 0 ? static_cast<size_t>(n) : 0;
            });
        close(fd);
        // LOGI("proc mem scan done, result len=%zu", result.size());
    } else {
        // LOGE("open /proc/self/mem failed: %s (errno=%d)", strerror(errno), errno);
    }

    if (original_dumpable != 1) {
        prctl(PR_SET_DUMPABLE, original_dumpable, 0, 0, 0);
    }
    return result;
}

/**
 * 方式二：mincore 校验后直读（无信号方案）。
 * mincore 对含未映射页的范围返回 ENOMEM（不会 SIGSEGV）；映射完整才 memcpy，
 * 从根本上避免踩空页崩溃。映射但换出的页读时会自动换入，不丢数据。
 */
std::string scan_via_mincore(
    const std::vector<std::pair<uint64_t, uint64_t>>& regions,
    std::vector<uint8_t>& buffer) {
    // LOGI("mincore fallback active, regions=%zu", regions.size());
    // maps 中的区域天然页对齐，mincore 的 addr 要求页对齐，天然满足
    size_t vec_pages =
        (kChunkSize + kSource32Length - 1 + kPageSize - 1) / kPageSize;
    std::vector<unsigned char> vec(vec_pages);
    return scan_regions(
        regions, buffer,
        [&vec](uint8_t* dst, uint64_t addr, size_t len) -> size_t {
            if (mincore(reinterpret_cast<void*>(addr), len, vec.data()) != 0) {
                return 0;  // 含未映射页，跳过该块
            }
            memcpy(dst, reinterpret_cast<void*>(addr), len);
            return len;
        });
}

std::string scan_source32() {
    std::vector<std::pair<uint64_t, uint64_t>> regions = read_rw_regions();
    if (regions.empty()) {
        // LOGI("no rw regions");
        return "";
    }
    // LOGI("native scan start, regions=%zu", regions.size());

    std::vector<uint8_t> buffer(kChunkSize + kSource32Length - 1);

    bool mem_opened = false;
    std::string result = scan_via_proc_mem(regions, buffer, &mem_opened);
    if (mem_opened) return result;  // /proc/self/mem 可用即以它为准（含未命中）

    return scan_via_mincore(regions, buffer);
}

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_owo233_tcqt_hooks_func_fekit_GetSign_nativeScanSource32(
    JNIEnv* env, jobject /*thiz*/) {
    std::string result = scan_source32();
    return env->NewStringUTF(result.c_str());
}
