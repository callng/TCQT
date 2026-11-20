#pragma once
#include <link.h>
#include "kernel_profiles.h"

namespace tcqt::recall {
// Resolver rule changes invalidate the native cache, independently of QQ versions.
constexpr uint32_t kKernelScanRevision = 1;
bool read_kernel_build_id(const dl_phdr_info &info, char (&id)[41]);
bool scan_kernel(const dl_phdr_info &info, KernelProfile &result, const char *&error);
bool validate_kernel_targets(const dl_phdr_info &info, const KernelProfile &profile);
bool read_kernel_cache(const char *path, const char *id, KernelProfile &profile);
bool write_kernel_cache(const char *path, const char *id, const KernelProfile &profile);
} // namespace tcqt::recall
