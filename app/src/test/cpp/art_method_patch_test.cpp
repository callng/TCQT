#include "zygisk/art/art_hook.h"
#include "zygisk/art/art_quick_redirect.h"
#include "zygisk/art/art_runtime.h"
#include "zygisk/android_support.h"

#include <sys/mman.h>
#include <unistd.h>
#include <array>
#include <cstdio>
#include <cstdlib>
#include <cstring>

using namespace tcqt::zygisk;
using namespace tcqt::zygisk::art_detail;

#define CHECK(condition) do { if (!(condition)) { \
    std::fprintf(stderr, "FAIL line %d: %s\n", __LINE__, #condition); std::exit(1); \
} } while (false)

// These tests exercise the real patch/restore code against mapped ArtMethod
// fixtures, without pretending to initialize ART in a shell native process.
namespace tcqt::zygisk::art_detail {
RuntimeState &state() { static RuntimeState value; return value; }
}
namespace tcqt::zygisk {
bool art_hook_initialized() { return true; }
}

namespace {
void suspend_noop(void *, const char *, bool) {}
void resume_noop(void *) {}

struct alignas(8) Method {
    uint32_t declaring_class = 0x100;
    uint32_t flags = ACC_PUBLIC;
    uint64_t dex_metadata = 0x1234;
    uintptr_t data = 0;
    void *entry = nullptr;
};
static_assert(sizeof(Method) == 32);

struct Fixture {
    Method target;
    std::array<Method, 2> generated;
    Method foreign_bridge;
    uint8_t *code;
    size_t page_size;
    Fixture() : page_size(static_cast<size_t>(sysconf(_SC_PAGESIZE))) {
        code = static_cast<uint8_t *>(mmap(nullptr, page_size, PROT_READ | PROT_WRITE | PROT_EXEC,
                                          MAP_PRIVATE | MAP_ANONYMOUS, -1, 0));
        CHECK(code != MAP_FAILED);
        target.entry = code + 64;
        generated[0].entry = generated[1].entry = code + 96;
        foreign_bridge.entry = code + 128;
        state().symbols.quick_to_interpreter = code + 192;
    }
    ~Fixture() { munmap(code, page_size); }
    uintptr_t target_art() { return reinterpret_cast<uintptr_t>(&target); }
    uintptr_t backup_art() { return reinterpret_cast<uintptr_t>(&generated[0]); }
    uintptr_t bridge_art() { return reinterpret_cast<uintptr_t>(&generated[1]); }
    int hook() { return art_hook_method(nullptr, target_art(), backup_art(), bridge_art()); }
    int unhook() { return art_unhook_method(nullptr, target_art(), backup_art()); }
    void foreign_stub() {
        // Exact LSPlant ARM64 quick trampoline, including its unaligned literal.
        const uint32_t words[] = {0x58000060u, 0xf8418010u, 0xd61f0200u};
        memcpy(code, words, sizeof(words));
        const uintptr_t bridge = reinterpret_cast<uintptr_t>(&foreign_bridge);
        memcpy(code + 12, &bridge, sizeof(bridge));
        target.entry = code;
    }
};
}

int main() {
    for (int api : {-1, 0, 27, 32, 38}) CHECK(!supports_android_api(api));
    for (int api = 33; api <= 37; ++api) CHECK(supports_android_api(api));
    std::puts("PASS: Android 13..17 support boundary");
    state().layout.method_size = 32;
    state().layout.access_flags_offset = 4;
    state().layout.entry_point_offset = 24;
    state().symbols.suspend_ctor = reinterpret_cast<void *>(suspend_noop);
    state().symbols.suspend_dtor = reinterpret_cast<void *>(resume_noop);
    {
        Fixture f;
        f.foreign_stub();
        CHECK(quick_redirect_literal(std::span(f.code, 12), 24) == 12);
        CHECK(quick_redirect_literal(std::span(f.code, 12), 16) == 0);
        CHECK(f.hook() == 0);
        CHECK(f.generated[0].entry == f.code);
        CHECK(f.unhook() == 0);
        CHECK(f.target.entry == f.code && f.target.flags == ACC_PUBLIC);
    }
    std::puts("PASS: preserve and restore an existing LSPlant quick entry");
    {
        Fixture f;
        // Newer framework engines install compiled dispatch code rather than
        // an LDR/BR stub and retain it with the non-compilable method flag.
        f.target.flags |= ACC_COMPILE_DONT_BOTHER;
        const void *original = f.target.entry;
        CHECK(f.hook() == 0);
        CHECK(f.generated[0].entry == original);
        CHECK(f.unhook() == 0);
        CHECK(f.target.entry == original && f.target.flags == (ACC_PUBLIC | ACC_COMPILE_DONT_BOTHER));
    }
    std::puts("PASS: preserve a non-compilable foreign compiled dispatcher");
    {
        Fixture f;
        CHECK(f.hook() == 0);
        CHECK(f.generated[0].entry == f.generated[1].entry);
        CHECK(f.unhook() == 0);
        CHECK(f.target.entry == f.generated[1].entry && f.target.flags == ACC_PUBLIC);
    }
    std::puts("PASS: managed backup and unhook use a safe interpreter entry");
    {
        Fixture f;
        f.target.flags |= ACC_NATIVE;
        f.target.data = 0x12345678;
        const void *original = f.target.entry;
        CHECK(f.hook() == 0);
        CHECK(f.generated[0].entry == original && f.generated[0].data == 0x12345678);
        CHECK(f.unhook() == 0);
        CHECK(f.target.entry == original && f.target.flags == (ACC_PUBLIC | ACC_NATIVE));
    }
    std::puts("PASS: native backup retains JNI entry and data");
    {
        Fixture f;
        CHECK(f.hook() == 0);
        void *tcqt_entry = f.target.entry;
        // A later framework has taken the outer entry and retained TCQT in its backup.
        f.target.entry = f.code + 160;
        CHECK(f.unhook() == 1);
        CHECK(f.target.entry == f.code + 160);
        // Once that framework restores TCQT, the retained passthrough can be removed.
        f.target.entry = tcqt_entry;
        CHECK(f.unhook() == 0);
    }
    std::puts("PASS: unhook does not overwrite a later framework's outer entry");
    {
        Fixture f;
        constexpr uint32_t default_method = 0x00400000;
        f.target.flags |= default_method | ACC_NTERP_ENTRY_FAST_PATH |
                          ACC_NTERP_INVOKE_FAST_PATH | ACC_MEMORY_SHARED_METHOD;
        CHECK(f.hook() == 0);
        for (const Method *m : {&f.target, &f.generated[0], &f.generated[1]}) {
            CHECK((m->flags & (ACC_NTERP_ENTRY_FAST_PATH | ACC_NTERP_INVOKE_FAST_PATH |
                               ACC_MEMORY_SHARED_METHOD | ACC_PRECOMPILED)) == 0);
            CHECK((m->flags & ACC_COMPILE_DONT_BOTHER) != 0);
        }
        CHECK((f.generated[0].flags & default_method) != 0);
        CHECK(f.generated[1].entry == state().symbols.quick_to_interpreter);
        CHECK(quick_redirect_literal(std::span(static_cast<const uint8_t *>(f.target.entry), 32), 24) == 24);
        CHECK(f.unhook() == 0);
    }
    std::puts("PASS: Nterp fast paths cleared, default semantics retained, BTI redirect recognized");
    {
        Fixture f;
        f.target.flags |= ACC_NATIVE | ACC_NTERP_ENTRY_FAST_PATH | 0x00080000;
        CHECK(f.hook() == 0);
        CHECK((f.generated[0].flags & (ACC_NTERP_ENTRY_FAST_PATH | 0x00080000)) ==
              (ACC_NTERP_ENTRY_FAST_PATH | 0x00080000));
        CHECK(f.unhook() == 0);
    }
    std::puts("PASS: native calling-convention flags are not treated as Nterp flags");
    {
        Fixture f;
        f.target.flags |= ACC_COMPILE_DONT_BOTHER | ACC_PRECOMPILED;
        const void *old_entry = f.target.entry;
        CHECK(f.hook() == 0);
        CHECK(f.generated[0].entry != old_entry);
        CHECK(f.generated[0].entry == state().symbols.quick_to_interpreter);
        CHECK(f.unhook() == 0);
    }
    std::puts("PASS: precompiled ART methods are not mistaken for foreign dispatchers");
    {
        Fixture f;
        f.target.flags |= ACC_INTRINSIC | ACC_COMPILE_DONT_BOTHER;
        const Method before = f.target;
        const Method backup = f.generated[0];
        const Method bridge = f.generated[1];
        CHECK(f.hook() == -14);
        CHECK(memcmp(&f.target, &before, sizeof(Method)) == 0);
        CHECK(memcmp(&f.generated[0], &backup, sizeof(Method)) == 0);
        CHECK(memcmp(&f.generated[1], &bridge, sizeof(Method)) == 0);
    }
    std::puts("PASS: missing intrinsic API leaves all method bytes unchanged");
}
