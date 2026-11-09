#pragma once

namespace tcqt::zygisk {
// Exported so stripping/section GC cannot remove the marker read by installers.
__attribute__((visibility("default"))) extern const char injector_identity[];
}
