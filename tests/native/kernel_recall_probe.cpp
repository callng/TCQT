// Runs the production interceptors against a real ARM64 CBZ instruction.
// Build/run using tools/test-native-recall.ps1. No QQ or TCQT settings changed.
#include "../../app/src/main/cpp/recall/kernel_recall.cpp"
#include <cstddef>
#include <cstdio>

extern "C" int recall_probe(void *, void *, void *, uint64_t, void *, uint64_t);
extern "C" char recall_probe_branch;
asm(R"(
.text
.align 4
.global recall_probe
.type recall_probe,%function
recall_probe:
 stp x29,x30,[sp,#-16]!
 mov x29,sp
 mov x8,x4
.global recall_probe_branch
recall_probe_branch:
 cbz x8,1f
 mov w0,#1
 ldp x29,x30,[sp],#16
 ret
1:
 mov w0,#0
 ldp x29,x30,[sp],#16
 ret
.size recall_probe,.-recall_probe
)");

struct Manager { uint64_t vtable{}; std::string session; };
const std::string account = "u_account_with_long_native_string";
struct Notify {
    std::string peer;
    uint8_t chat; uint8_t pad[7]{};
    uint64_t seq{123}, client{456}, random{789}, id{987}, time{123456};
    std::string sender, recipient;
    uint32_t op_type{}; uint32_t pad2{};
    std::string op;
    uint64_t pad3[2]{};
    std::string extra;
    bool offline{}, traceless{true}, silence{};
};
static_assert(sizeof(std::string) == 24);
static_assert(offsetof(Manager, session) == 8);
static_assert(offsetof(Notify, seq) == 0x20);
static_assert(offsetof(Notify, sender) == 0x48);
static_assert(offsetof(Notify, op) == 0x80);
static_assert(offsetof(Notify, traceless) == 0xc1);

struct Record { uintptr_t *vtable; uint8_t chat; std::string peer, sender; };
int mode;
void interceptor(void *address, DobbyRegisterContext *ctx) {
    if (mode == 0) tcqt::recall::on_notify(address, ctx);
    if (mode == 1) tcqt::recall::on_replace(address, ctx);
    if (mode == 2) tcqt::recall::on_traceless(address, ctx);
}

int main() {
    using namespace tcqt::recall;
    const int result = DobbyInstrument(&recall_probe_branch, interceptor);
    if (result != 0) { std::printf("intercept result=%d\n", result); return 3; }
    Manager manager{0, "123456789"};
    get_session_parameter = +[](const char *session, size_t length, uint32_t module,
                                uint32_t method, const uint32_t *param, std::string *out) {
        if (std::string_view(session, length) != "123456789" || module != 0x10 ||
            method != 1 || *param != 0x138b) return false;
        *out = account;
        return true;
    };
    Notify notify;
    notify.peer = "u_peer_with_long_native_string";
    notify.sender = "u_sender_with_long_native_string";
    notify.op = "u_operator_with_long_native_string";
    notify.chat = 1;
    auto test = [](const char *name, bool ok) {
        std::printf("%s %s\n", ok ? "PASS" : "FAIL", name);
        return ok ? 0 : 1;
    };
    int failures = 0;
    struct Notes { Elf64_Nhdr header{4, 20, NT_GNU_BUILD_ID}; char name[4]{'G','N','U',0}; uint8_t id[20]{}; } notes;
    Elf64_Phdr phdr{};
    phdr.p_type = PT_NOTE;
    phdr.p_vaddr = reinterpret_cast<uintptr_t>(&notes);
    phdr.p_memsz = sizeof(notes);
    dl_phdr_info info{};
    info.dlpi_phdr = &phdr;
    info.dlpi_phnum = 1;
    for (const auto &profile : KERNEL_PROFILES) {
        auto digit = [](char c) { return c <= '9' ? c - '0' : c - 'a' + 10; };
        for (size_t i = 0; i < 20; ++i)
            notes.id[i] = (digit(profile.build_id[i*2]) << 4) | digit(profile.build_id[i*2+1]);
        failures += test(profile.version, identify_profile(&info) == &profile);
    }
    std::memset(notes.id, 0, sizeof(notes.id));
    failures += test("unknown kernel build rejected", identify_profile(&info) == nullptr);
    active.store(false);
    failures += test("inactive original branch", recall_probe(&manager, &notify, nullptr, 0, &notify, 1) == 1);
    active.store(true);
    failures += test("friend notify blocked", recall_probe(&manager, &notify, nullptr, 0, &notify, 1) == 0);
    notify.op = account;
    failures += test("own notify allowed", recall_probe(&manager, &notify, nullptr, 0, &notify, 1) == 1);
    notify.op = "u_other";
    notify.chat = 2;
    failures += test("group administrator notify blocked", recall_probe(&manager, &notify, nullptr, 0, &notify, 1) == 0);
    notify.chat = 100;
    failures += test("group temporary notify blocked", recall_probe(&manager, &notify, nullptr, 0, &notify, 1) == 0);
    notify.chat = 4;
    failures += test("guild left to original", recall_probe(&manager, &notify, nullptr, 0, &notify, 1) == 1);
    notify.chat = 1;
    failures += test("session resolved to account UID", events.front().account == account &&
        events.front().account != manager.session && events.front().peer == notify.peer && events.front().seq == 123);
    failures += test("server UID not passed as local message ID", events.front().id == 0);
    failures += test("timestamp separated from server UID", events.front().time == notify.time &&
        events.front().time != notify.id);
    uintptr_t vtable[21]{};
    vtable[0] = reinterpret_cast<uintptr_t>(+[](void *) -> uint64_t { return 987; });
    vtable[1] = reinterpret_cast<uintptr_t>(+[](void *p) -> uint8_t { return static_cast<Record *>(p)->chat; });
    vtable[3] = reinterpret_cast<uintptr_t>(+[](void *) -> uint64_t { return 123; });
    vtable[8] = reinterpret_cast<uintptr_t>(+[](void *p) -> std::string { return static_cast<Record *>(p)->sender; });
    vtable[20] = reinterpret_cast<uintptr_t>(+[](void *p) -> std::string { return static_cast<Record *>(p)->peer; });
    Record record{vtable, 100, notify.peer, notify.sender};
    mode = 1;
    failures += test("local outgoing recall allowed", recall_probe(&manager, nullptr, &notify.op, 0, &record, 0) == 1);
    failures += test("incoming record replacement blocked", recall_probe(&manager, nullptr, &notify.op, 0, &record, 1) == 0);
    failures += test("record scalar and string return ABI", events.back().chat_type == 100 &&
        events.back().id == 987 && events.back().peer == notify.peer && events.back().sender == notify.sender);
    mode = 2;
    failures += test("traceless mutation blocked", recall_probe(&manager, nullptr, &notify, 0, &record, 1) == 0);
    notify.seq = 0;
    failures += test("client-seq-only traceless local sequence", recall_probe(&manager, nullptr, &notify, 0, &record, 1) == 0 &&
        events.back().seq == 123 && events.back().chat_type == 100);
    notify.op = account;
    failures += test("own traceless recall allowed", recall_probe(&manager, nullptr, &notify, 0, &record, 1) == 1);
    failures += test("original null branch unchanged", recall_probe(&manager, nullptr, &notify, 0, nullptr, 1) == 0);
    DobbyDestroy(&recall_probe_branch);
    failures += test("original code restored", recall_probe(&manager, &notify, nullptr, 0, &notify, 1) == 1);
    std::printf("failures=%d\n", failures);
    return failures ? 1 : 0;
}
