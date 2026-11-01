#pragma once

#include <array>
#include <cstddef>

namespace tcqt::source32 {

inline constexpr std::size_t kSource32Length = 32;

// Scans this process using the libfekit source32 invariants.
int scan_source32(char *out);

}  // namespace tcqt::source32
