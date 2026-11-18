#include <android/log.h>
#include <elf.h>
#include <jni.h>
#include <link.h>
#include <dobby.h>

#include <atomic>
#include <condition_variable>
#include <chrono>
#include <cstring>
#include <deque>
#include <mutex>
#include <string>
#include <string_view>
#include <thread>
#include <vector>
#include "kernel_profiles.h"

// QQ 9.2.30–9.3.85 ARM64. See docs/native-recall-kernel-9.3.85.md for the
// instruction sites, native layouts and caller evidence. No JNI push hooks.
namespace tcqt::recall {
namespace {
constexpr char TAG[] = "TCQT.NativeRecall";

struct Event {
    std::string account, peer, sender, operator_uid;
    uint64_t seq{}, client_seq{}, random{}, time{}, id{};
    uint8_t chat_type{};
};

JavaVM *vm{};
jclass bridge{};
jmethodID on_event{};
std::mutex install_mutex, event_mutex;
std::condition_variable event_ready;
std::deque<Event> events;
std::atomic<bool> active{false};
bool prepared{}, installed{}, seen_kernel{};
void *stubs[3]{};
using GetSessionParameter = bool (*)(const char *, size_t, uint32_t, uint32_t,
                                    const uint32_t *, std::string *);
GetSessionParameter get_session_parameter{};

// libc++'s default ARM64 string layout is verified by kernel's 0x1572bf0.
// Read views only: never construct/destroy or modify QQ-owned std::strings.
std::string_view string_at(uintptr_t address) {
    auto p = reinterpret_cast<const unsigned char *>(address);
    if ((p[0] & 1) == 0)
        return {reinterpret_cast<const char *>(p + 1), size_t(p[0] >> 1)};
    size_t length;
    const char *data;
    std::memcpy(&length, p + 8, sizeof(length));
    std::memcpy(&data, p + 16, sizeof(data));
    return {data, length};
}

template<typename T> T value_at(uintptr_t address) {
    T value;
    std::memcpy(&value, reinterpret_cast<const void *>(address), sizeof(value));
    return value;
}

bool supported_chat(uint8_t type) {
    // kernel IsC2CChatType: 1,42,100,101,102; group: 2.
    return type == 1 || type == 2 || type == 42 || (type >= 100 && type <= 102);
}

bool other_recall(std::string_view account, std::string_view op, uint8_t chat) {
    return supported_chat(chat) && !account.empty() && !op.empty() && account != op;
}

std::string current_uid(uintptr_t manager) {
    // manager+8 is the kernel session ID, not the UID. Match the C2C worker's
    // native session query (module 0x10, method 1, parameter 5003).
    const auto session = string_at(manager + 8);
    const uint32_t parameter = 0x138b;
    std::string uid;
    get_session_parameter(session.data(), session.size(), 0x10, 1, &parameter, &uid);
    return uid;
}

Event notify_event(std::string account, uintptr_t notify) {
    Event e;
    e.account = std::move(account);
    e.peer = string_at(notify);
    e.chat_type = value_at<uint8_t>(notify + 0x18);
    e.seq = value_at<uint64_t>(notify + 0x20);
    e.client_seq = value_at<uint64_t>(notify + 0x28);
    e.random = value_at<uint64_t>(notify + 0x30);
    // +0x38 is the server message UID. It is not QQ's local msgId, which the
    // Java lookup treats as authoritative. Locate this snapshot by sequence.
    e.time = value_at<uint64_t>(notify + 0x40);
    e.sender = string_at(notify + 0x48);
    e.operator_uid = string_at(notify + 0x80);
    return e;
}

void publish(Event &&event) {
    {
        std::lock_guard lock(event_mutex);
        events.push_back(std::move(event));
    }
    event_ready.notify_one();
}

void on_notify(void *, DobbyRegisterContext *ctx) {
    if (!active.load(std::memory_order_acquire) || ctx->general.x[8] == 0) return;
    const auto manager = ctx->general.x[0], notify = ctx->general.x[1];
    auto account = current_uid(manager);
    auto op = string_at(notify + 0x80);
    auto chat = value_at<uint8_t>(notify + 0x18);
    if (!other_recall(account, op, chat)) return;
    // CBZ x8 branches to the function's existing epilogue, before lookup,
    // offline/silence caching, message mutation, DB work and recall signals.
    ctx->general.x[8] = 0;
    publish(notify_event(std::move(account), notify));
}

// Only known record virtual getters; no Djinni/Java proxy calls. Scalar getters
// preserve the actual native ABI. String-returning getters use AAPCS64 x8.
template<typename T> T record_get(uintptr_t record, size_t slot) {
    auto vtable = value_at<uintptr_t>(record);
    auto fn = value_at<uintptr_t>(vtable + slot);
    return reinterpret_cast<T (*)(void *)>(fn)(reinterpret_cast<void *>(record));
}

Event record_event(std::string account, uintptr_t record, std::string_view op) {
    Event e;
    e.account = std::move(account);
    e.chat_type = record_get<uint8_t>(record, 0x08);
    e.id = record_get<uint64_t>(record, 0x00);
    e.seq = record_get<uint64_t>(record, 0x18);
    e.peer = record_get<std::string>(record, 0xa0);
    e.sender = record_get<std::string>(record, 0x40);
    e.operator_uid = op;
    return e;
}

void on_replace(void *, DobbyRegisterContext *ctx) {
    if (!active.load(std::memory_order_acquire) || ctx->general.x[8] == 0 ||
        (ctx->general.x[5] & 1) == 0) return; // OnRecallMsgs (locally requested): by_notify=0.
    const auto manager = ctx->general.x[0], record = ctx->general.x[8];
    auto op = string_at(ctx->general.x[2]);
    auto account = current_uid(manager);
    if (!other_recall(account, op, record_get<uint8_t>(record, 8))) return;
    ctx->general.x[8] = 0;
    publish(record_event(std::move(account), record, op));
}

void on_traceless(void *, DobbyRegisterContext *ctx) {
    if (!active.load(std::memory_order_acquire) || ctx->general.x[8] == 0) return;
    const auto manager = ctx->general.x[0], notify = ctx->general.x[2];
    auto op = string_at(notify + 0x80);
    auto account = current_uid(manager);
    if (!other_recall(account, op,
                      value_at<uint8_t>(notify + 0x18))) return;
    const auto record = ctx->general.x[8];
    ctx->general.x[8] = 0;
    auto event = notify_event(std::move(account), notify);
    event.chat_type = record_get<uint8_t>(record, 8);
    // Client-seq-only traceless notifications acquire a local seq in this path.
    if (event.seq == 0) event.seq = record_get<uint64_t>(record, 0x18);
    publish(std::move(event));
}

// Transport a metadata snapshot; Java decodes it into the existing Recall model.
// No host packet parser and no Java result is used to decide whether to block.
void varint(std::vector<uint8_t> &out, uint64_t value) {
    while (value >= 128) { out.push_back(uint8_t(value) | 0x80); value >>= 7; }
    out.push_back(uint8_t(value));
}
void string_field(std::vector<uint8_t> &out, unsigned field, const std::string &value) {
    varint(out, (field << 3) | 2);
    varint(out, value.size());
    out.insert(out.end(), value.begin(), value.end());
}
void scalar_field(std::vector<uint8_t> &out, unsigned field, uint64_t value) {
    varint(out, field << 3);
    varint(out, value);
}

void deliver_events() {
    JNIEnv *env{};
    if (vm->AttachCurrentThreadAsDaemon(&env, nullptr) != JNI_OK) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "reminder thread attach failed");
        return;
    }
    for (;;) {
        Event event;
        {
            std::unique_lock lock(event_mutex);
            event_ready.wait(lock, [] { return !events.empty(); });
            event = std::move(events.front());
            events.pop_front();
        }
        std::vector<uint8_t> bytes;
        string_field(bytes, 1, event.account);
        scalar_field(bytes, 2, event.chat_type);
        string_field(bytes, 3, event.peer);
        scalar_field(bytes, 4, event.seq);
        string_field(bytes, 5, event.operator_uid);
        string_field(bytes, 6, event.sender);
        scalar_field(bytes, 7, event.id);
        scalar_field(bytes, 8, event.client_seq);
        scalar_field(bytes, 9, event.time);
        scalar_field(bytes, 10, event.random);
        auto data = env->NewByteArray(static_cast<jsize>(bytes.size()));
        if (data != nullptr) {
            env->SetByteArrayRegion(data, 0, static_cast<jsize>(bytes.size()),
                                    reinterpret_cast<const jbyte *>(bytes.data()));
            if (!env->ExceptionCheck()) env->CallStaticVoidMethod(bridge, on_event, data);
            env->DeleteLocalRef(data);
        }
        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
            __android_log_print(ANDROID_LOG_ERROR, TAG, "reminder callback failed; recall remains blocked");
        }
    }
}

const KernelProfile *identify_profile(const dl_phdr_info *info) {
    for (size_t i = 0; i < info->dlpi_phnum; ++i) {
        const auto &ph = info->dlpi_phdr[i];
        if (ph.p_type != PT_NOTE) continue;
        auto p = reinterpret_cast<const uint8_t *>(info->dlpi_addr + ph.p_vaddr);
        auto end = p + ph.p_memsz;
        while (size_t(end - p) >= sizeof(Elf64_Nhdr)) {
            Elf64_Nhdr note;
            std::memcpy(&note, p, sizeof(note));
            p += sizeof(note);
            const size_t name_size = (size_t(note.n_namesz) + 3) & ~size_t(3);
            const size_t desc_size = (size_t(note.n_descsz) + 3) & ~size_t(3);
            if (name_size > size_t(end - p) || desc_size > size_t(end - p) - name_size) return nullptr;
            if (note.n_type == NT_GNU_BUILD_ID && note.n_namesz == 4 &&
                std::memcmp(p, "GNU", 4) == 0 && note.n_descsz == 20) {
                constexpr char hex[] = "0123456789abcdef";
                char id[40];
                for (size_t j = 0; j < 20; ++j) {
                    id[j * 2] = hex[p[name_size + j] >> 4];
                    id[j * 2 + 1] = hex[p[name_size + j] & 15];
                }
                for (const auto &profile : KERNEL_PROFILES)
                    if (std::string_view(id, sizeof(id)) == profile.build_id) return &profile;
                return nullptr;
            }
            p += name_size + desc_size;
        }
    }
    return nullptr;
}

void install_kernel(const dl_phdr_info *info) {
    if (!std::string_view(info->dlpi_name).ends_with("/libkernel.so") &&
        std::string_view(info->dlpi_name) != "libkernel.so") return;
    std::lock_guard lock(install_mutex);
    if (!prepared || installed || seen_kernel) return;
    seen_kernel = true;
    const auto *profile = identify_profile(info);
    if (profile == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "unsupported libkernel build ID; hooks not installed");
        return;
    }
    constexpr dobby_instrument_callback_t callbacks[] = {on_notify, on_replace, on_traceless};
    for (size_t i = 0; i < 3; ++i) {
        if (value_at<uint32_t>(info->dlpi_addr + profile->offsets[i]) != profile->instructions[i]) {
            __android_log_print(ANDROID_LOG_ERROR, TAG, "kernel instruction mismatch at %zx", profile->offsets[i]);
            return;
        }
    }
    if (value_at<uint32_t>(info->dlpi_addr + profile->session_query) != profile->query_instruction) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "kernel session query instruction mismatch");
        return;
    }
    get_session_parameter = reinterpret_cast<GetSessionParameter>(info->dlpi_addr + profile->session_query);
    for (size_t i = 0; i < 3; ++i) {
        stubs[i] = reinterpret_cast<void *>(info->dlpi_addr + profile->offsets[i]);
        const int result = DobbyInstrument(stubs[i], callbacks[i]);
        if (result != 0) {
            __android_log_print(ANDROID_LOG_ERROR, TAG, "intercept install failed index=%zu result=%d",
                                i, result);
            for (size_t j = 0; j < i; ++j) DobbyDestroy(stubs[j]);
            return;
        }
    }
    installed = true;
    active.store(true, std::memory_order_release);
    __android_log_print(ANDROID_LOG_INFO, TAG, "kernel instruction hooks active: QQ %s notify/replace/traceless", profile->version);
}

int enumerate(struct dl_phdr_info *info, size_t, void *) { install_kernel(info); return 0; }
void lsposed_loaded(const char *name, void *) {
    if (name != nullptr && std::string_view(name).ends_with("libkernel.so"))
        dl_iterate_phdr(enumerate, nullptr);
}
} // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_owo233_tcqt_host_service_NativeRecallBridge_nativeStart(JNIEnv *env, jclass cls) {
    {
        std::lock_guard lock(install_mutex);
        if (prepared) return JNI_TRUE;
        env->GetJavaVM(&vm);
        bridge = reinterpret_cast<jclass>(env->NewGlobalRef(cls));
        on_event = env->GetStaticMethodID(cls, "onNativeRecall", "([B)V");
        if (bridge == nullptr || on_event == nullptr) return JNI_FALSE;
        prepared = true;
        std::thread(deliver_events).detach();
    }
    dl_iterate_phdr(enumerate, nullptr);
    // Discover late loads in Zygisk/modern Xposed without hooking the Android
    // linker or dlopen. Legacy LSPosed additionally gives an immediate callback.
    std::thread([] {
        for (;;) {
            {
                std::lock_guard lock(install_mutex);
                if (seen_kernel) return;
            }
            std::this_thread::sleep_for(std::chrono::milliseconds(50));
            dl_iterate_phdr(enumerate, nullptr);
        }
    }).detach();
    return JNI_TRUE;
}

// Official LSPosed Native-Hook ABI: notification only. The engine is shared
// with Zygisk, and never uses LSPosed hook_func or the module's PLT engine.
struct NativeAPIEntries {
    uint32_t version;
    int (*hook_func)(void *, void *, void **);
    int (*unhook_func)(void *);
};
extern "C" [[gnu::visibility("default"), gnu::used]]
auto native_init(const NativeAPIEntries *) -> void (*)(const char *, void *) {
    return lsposed_loaded;
}
} // namespace tcqt::recall
