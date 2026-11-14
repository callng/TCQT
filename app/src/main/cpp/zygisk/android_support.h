#pragma once

namespace tcqt::zygisk {
// Keep in sync with customize.sh and ZygiskEntry. APK/LSPosed minSdk is separate.
inline constexpr int MIN_ZYGISK_API = 33;
inline constexpr int MAX_ZYGISK_API = 37;
constexpr bool supports_android_api(int api) {
    return api >= MIN_ZYGISK_API && api <= MAX_ZYGISK_API;
}
} // namespace tcqt::zygisk
