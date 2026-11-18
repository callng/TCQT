// Runs the production interceptors against a real ARM64 CBZ instruction.
// Build/run using tools/test-native-recall.ps1. No QQ or TCQT settings changed.
#include <cstddef>
#include <cstdio>
#include <cerrno>
#include <cstring>
#include <new>
#include <pthread.h>
#include <sys/mman.h>
#include <dobby.h>

bool mock_install{}, mock_thread_failure{};
int instrument_calls{}, destroy_calls{}, fail_index{-1}, no_patch_index{-1};
bool fail_rollback{};
uint32_t *mock_sites[3]{};
uint32_t mock_original[3]{};
int probe_instrument(void *address, dobby_instrument_callback_t callback) {
    if (!mock_install) return DobbyInstrument(address, callback);
    const int index = instrument_calls++;
    if (index == fail_index) return -1;
    if (index != no_patch_index) *static_cast<uint32_t *>(address) = 0x14000000;
    return 0;
}
int probe_destroy(void *address) {
    if (!mock_install) return DobbyDestroy(address);
    ++destroy_calls;
    if (fail_rollback) return -1;
    for (size_t i = 0; i < 3; ++i)
        if (mock_sites[i] == address) *mock_sites[i] = mock_original[i];
    return 0;
}
int probe_pthread_create(pthread_t *thread, const pthread_attr_t *attr,
                         void *(*entry)(void *), void *arg) {
    if (mock_thread_failure) return EAGAIN;
    return pthread_create(thread, attr, entry, arg);
}

#define DobbyInstrument probe_instrument
#define DobbyDestroy probe_destroy
#define pthread_create probe_pthread_create
#include "../../app/src/main/cpp/recall/kernel_recall.cpp"
#undef pthread_create
#undef DobbyDestroy
#undef DobbyInstrument

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
// Independent native ABI fixture: explicitly consumes X8 as result storage.
std::string probe_peer_getter(void *) asm("probe_peer_getter");
std::string probe_sender_getter(void *) asm("probe_sender_getter");
extern "C" void probe_construct_peer(std::string *result, void *record) {
    new (result) std::string(static_cast<Record *>(record)->peer);
}
extern "C" void probe_construct_sender(std::string *result, void *record) {
    new (result) std::string(static_cast<Record *>(record)->sender);
}
asm(R"(
.text
.align 4
.global probe_peer_getter
.type probe_peer_getter,%function
probe_peer_getter:
 mov x1,x0
 mov x0,x8
 b probe_construct_peer
.size probe_peer_getter,.-probe_peer_getter
.global probe_sender_getter
.type probe_sender_getter,%function
probe_sender_getter:
 mov x1,x0
 mov x0,x8
 b probe_construct_sender
.size probe_sender_getter,.-probe_sender_getter
)");

int jni_failure{}, global_created{}, global_deleted{};
JavaVM fake_vm{};
jint fake_get_vm(JNIEnv *, JavaVM **out) {
    if (jni_failure == 1) return JNI_ERR;
    *out = &fake_vm;
    return JNI_OK;
}
jmethodID fake_method(JNIEnv *, jclass, const char *, const char *) {
    return jni_failure == 2 ? nullptr : reinterpret_cast<jmethodID>(1);
}
jobject fake_global(JNIEnv *, jobject cls) {
    if (jni_failure == 3) return nullptr;
    ++global_created;
    return cls;
}
void fake_delete_global(JNIEnv *, jobject) { ++global_deleted; }
jint fake_attach(JavaVM *, JNIEnv **, void *) { return JNI_ERR; }
JNIEnv *delivery_env{};
bool delivery_exception{};
int callback_calls{}, exceptions_cleared{}, refs_deleted{}, daemon_detaches{};
jint fake_attach_ready(JavaVM *, JNIEnv **out, void *) { *out = delivery_env; return JNI_OK; }
jint fake_detach(JavaVM *) { ++daemon_detaches; return JNI_OK; }
jbyteArray fake_new_array(JNIEnv *, jsize) { return reinterpret_cast<jbyteArray>(1); }
void fake_set_array(JNIEnv *, jbyteArray, jsize, jsize, const jbyte *) {}
void fake_delete_local(JNIEnv *, jobject) { ++refs_deleted; }
jboolean fake_exception_check(JNIEnv *) { return delivery_exception; }
void fake_exception_describe(JNIEnv *) {}
void fake_exception_clear(JNIEnv *) { delivery_exception = false; ++exceptions_cleared; }
void fake_callback(JNIEnv *, jclass, jmethodID, va_list) {
    if (++callback_calls == 1) delivery_exception = true;
    else tcqt::recall::stop_delivery();
}
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
    const auto &profile = KERNEL_PROFILES[std::size(KERNEL_PROFILES) - 1];
    auto digit = [](char c) { return c <= '9' ? c - '0' : c - 'a' + 10; };
    for (size_t i = 0; i < 20; ++i)
        notes.id[i] = (digit(profile.build_id[i*2]) << 4) | digit(profile.build_id[i*2+1]);
    Elf64_Nhdr malformed{0xffffffff, 20, NT_GNU_BUILD_ID};
    Elf64_Phdr note_phdrs[2]{phdr, phdr};
    note_phdrs[0].p_vaddr = reinterpret_cast<uintptr_t>(&malformed);
    note_phdrs[0].p_memsz = sizeof(malformed);
    info.dlpi_phdr = note_phdrs;
    info.dlpi_phnum = 2;
    failures += test("malformed note segment does not hide later Build ID", identify_profile(&info) == &profile);

    JNINativeInterface jni_functions{};
    jni_functions.GetJavaVM = fake_get_vm;
    jni_functions.GetStaticMethodID = fake_method;
    jni_functions.NewGlobalRef = fake_global;
    jni_functions.DeleteGlobalRef = fake_delete_global;
    JNIEnv fake_env{&jni_functions};
    JNIInvokeInterface vm_functions{};
    vm_functions.AttachCurrentThreadAsDaemon = fake_attach;
    fake_vm.functions = &vm_functions;
    auto cls = reinterpret_cast<jclass>(1);
    for (jni_failure = 1; jni_failure <= 3; ++jni_failure) {
        failures += test("incomplete JNI binding not published",
            Java_com_owo233_tcqt_host_service_NativeRecallBridge_nativeStart(&fake_env, cls) == JNI_FALSE &&
            !prepared && global_created == 0 && bridge == nullptr && vm == nullptr);
    }
    jni_failure = 0;
    mock_thread_failure = true;
    failures += test("consumer thread creation failure releases global ref",
        Java_com_owo233_tcqt_host_service_NativeRecallBridge_nativeStart(&fake_env, cls) == JNI_FALSE &&
        !prepared && global_created == global_deleted && bridge == nullptr);
    mock_thread_failure = false;
    for (int attempt = 0; attempt < 2; ++attempt) {
        failures += test("consumer JNI attach failure is retryable without reference leak",
            Java_com_owo233_tcqt_host_service_NativeRecallBridge_nativeStart(&fake_env, cls) == JNI_FALSE &&
            !prepared && global_created == global_deleted && bridge == nullptr);
    }
    delivery_state = DeliveryState::Running;

    const size_t image_size = profile.offsets[0] + 4096;
    auto image = mmap(nullptr, image_size, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    if (image == MAP_FAILED) return 4;
    info.dlpi_addr = reinterpret_cast<uintptr_t>(image);
    info.dlpi_name = "libkernel.so";
    phdr.p_vaddr = reinterpret_cast<uintptr_t>(&notes) - info.dlpi_addr;
    info.dlpi_phdr = &phdr;
    info.dlpi_phnum = 1;
    auto reset_install = [&] {
        kernel_state = KernelState::Waiting;
        prepared = true;
        active.store(false);
        delivery_state = DeliveryState::Running;
        fail_index = no_patch_index = -1;
        fail_rollback = false;
        instrument_calls = destroy_calls = 0;
        const uint32_t loads[kSites] = {0xf8420ea8, 0xf9400028, 0xf9400028};
        for (size_t i = 0; i < kSites; ++i) {
            mock_sites[i] = reinterpret_cast<uint32_t *>(info.dlpi_addr + profile.offsets[i]);
            mock_original[i] = profile.instructions[i];
            *mock_sites[i] = mock_original[i];
            mock_sites[i][-1] = loads[i];
        }
        mock_sites[1][2] = 0x2a0503f5;
        mock_sites[2][1] = 0x39430449;
        *reinterpret_cast<uint32_t *>(info.dlpi_addr + profile.session_query) = profile.query_instruction;
    };
    mock_install = true;
    reset_install();
    mock_sites[1][2] = 0;
    install_kernel(&info);
    failures += test("W5 argument mismatch rejected before installation",
        kernel_state == KernelState::Failed && instrument_calls == 0 && !active.load());
    reset_install();
    fail_index = 1;
    install_kernel(&info);
    install_kernel(&info);
    failures += test("partial install rolled back and never retried",
        kernel_state == KernelState::Failed && instrument_calls == 2 && destroy_calls == 1 &&
        *mock_sites[0] == mock_original[0] && !active.load() && delivery_state == DeliveryState::Stopped);
    failures += test("failed installation reports failure on repeated nativeStart",
        Java_com_owo233_tcqt_host_service_NativeRecallBridge_nativeStart(&fake_env, cls) == JNI_FALSE);
    reset_install();
    fail_index = 1;
    fail_rollback = true;
    install_kernel(&info);
    install_kernel(&info);
    failures += test("rollback failure leaves hooks inactive and retries disabled",
        kernel_state == KernelState::Failed && instrument_calls == 2 && destroy_calls == 1 && !active.load());
    reset_install();
    no_patch_index = 1;
    install_kernel(&info);
    failures += test("Dobby success without code patch rejected and rolled back",
        kernel_state == KernelState::Failed && instrument_calls == 2 && destroy_calls == 2 &&
        *mock_sites[0] == mock_original[0] && !active.load());
    reset_install();
    install_kernel(&info);
    install_kernel(&info);
    failures += test("complete installation activates exactly once",
        kernel_state == KernelState::Installed && instrument_calls == kSites && active.load());
    munmap(image, image_size);
    mock_install = false;
    prepared = false;
    kernel_state = KernelState::Waiting;
    get_session_parameter = +[](const char *, size_t, uint32_t, uint32_t, const uint32_t *, std::string *out) {
        *out = account;
        return true;
    };

    failures += test("null string address returns empty", string_at(0).empty());
    struct InvalidLongString { uint64_t capacity{1}, length{9}; const char *data{}; } bad_string;
    failures += test("null long-string storage returns empty", string_at(reinterpret_cast<uintptr_t>(&bad_string)).empty());
    active.store(false);
    failures += test("inactive original branch", recall_probe(&manager, &notify, nullptr, 0, &notify, 1) == 1);
    active.store(true);
    failures += test("null manager leaves original instruction", recall_probe(nullptr, &notify, nullptr, 0, &notify, 1) == 1);
    failures += test("null notify leaves original instruction", recall_probe(&manager, nullptr, nullptr, 0, &notify, 1) == 1);
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
    vtable[8] = reinterpret_cast<uintptr_t>(probe_sender_getter);
    vtable[20] = reinterpret_cast<uintptr_t>(probe_peer_getter);
    Record record{vtable, 100, notify.peer, notify.sender};
    mode = 1;
    failures += test("null replacement operator leaves original instruction", recall_probe(&manager, nullptr, nullptr, 0, &record, 1) == 1);
    failures += test("local outgoing recall allowed", recall_probe(&manager, nullptr, &notify.op, 0, &record, 0) == 1);
    failures += test("incoming record replacement blocked", recall_probe(&manager, nullptr, &notify.op, 0, &record, 1) == 0);
    failures += test("record scalar and string return ABI", events.back().chat_type == 100 &&
        events.back().id == 987 && events.back().peer == notify.peer && events.back().sender == notify.sender);
    record.peer = "u_short";
    failures += test("native X8 string getter supports short and long results",
        record_string(reinterpret_cast<uintptr_t>(&record), 0xa0) == record.peer &&
        record_string(reinterpret_cast<uintptr_t>(&record), 0x40) == record.sender);
    mode = 2;
    failures += test("null traceless notify leaves original instruction", recall_probe(&manager, nullptr, nullptr, 0, &record, 1) == 1);
    notify.traceless = false;
    const auto count_before = events.size();
    failures += test("nontraceless notify is not intercepted or published",
        recall_probe(&manager, nullptr, &notify, 0, &record, 1) == 1 && events.size() == count_before);
    notify.traceless = true;
    failures += test("traceless mutation blocked", recall_probe(&manager, nullptr, &notify, 0, &record, 1) == 0);
    notify.seq = 0;
    failures += test("client-seq-only traceless local sequence", recall_probe(&manager, nullptr, &notify, 0, &record, 1) == 0 &&
        events.back().seq == 123 && events.back().chat_type == 100);
    notify.op = account;
    failures += test("own traceless recall allowed", recall_probe(&manager, nullptr, &notify, 0, &record, 1) == 1);
    failures += test("original null branch unchanged", recall_probe(&manager, nullptr, &notify, 0, nullptr, 1) == 0);
    DobbyDestroy(&recall_probe_branch);
    failures += test("original code restored", recall_probe(&manager, &notify, nullptr, 0, &notify, 1) == 1);
    get_session_parameter = +[](const char *, size_t, uint32_t, uint32_t, const uint32_t *, std::string *out) {
        *out = "unusable_partial_output";
        return false;
    };
    failures += test("failed session query discards partial UID", current_uid(reinterpret_cast<uintptr_t>(&manager)).empty());
    events.clear();
    delivery_state = DeliveryState::Running;
    for (size_t i = 0; i < kMaxQueuedEvents + 2; ++i) {
        Event e;
        e.seq = i;
        publish(std::move(e));
    }
    failures += test("queue overflow retains newest 1024 reminders",
        events.size() == kMaxQueuedEvents && events.front().seq == 2 &&
        events.back().seq == kMaxQueuedEvents + 1);
    stop_delivery();
    publish(Event{});
    failures += test("stopped delivery clears queue and rejects new reminders", events.empty());
    delivery_state = DeliveryState::Running;
    publish(Event{});
    publish(Event{});
    jni_functions.NewByteArray = fake_new_array;
    jni_functions.SetByteArrayRegion = fake_set_array;
    jni_functions.DeleteLocalRef = fake_delete_local;
    jni_functions.CallStaticVoidMethodV = fake_callback;
    jni_functions.ExceptionCheck = fake_exception_check;
    jni_functions.ExceptionDescribe = fake_exception_describe;
    jni_functions.ExceptionClear = fake_exception_clear;
    vm_functions.AttachCurrentThreadAsDaemon = fake_attach_ready;
    vm_functions.DetachCurrentThread = fake_detach;
    delivery_env = &fake_env;
    vm = &fake_vm;
    bridge = cls;
    on_event = reinterpret_cast<jmethodID>(1);
    const auto globals_before = global_deleted;
    deliver_events(nullptr);
    failures += test("JNI failure discards one event and continues with the next",
        callback_calls == 2 && exceptions_cleared == 1 && refs_deleted == 2 && events.empty());
    failures += test("stopped consumer releases binding and detaches daemon",
        global_deleted == globals_before + 1 && daemon_detaches == 1);
    std::printf("failures=%d\n", failures);
    return failures ? 1 : 0;
}
