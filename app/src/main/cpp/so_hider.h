// so_hider.h
//
// Re-back selected /proc/self/maps VMAs with private memfd snapshots.
//
// Design:
//   1. Collect target VMAs from /proc/self/maps.
//   2. Snapshot ALL target VMAs before changing any mapping.
//   3. Re-check that the target VMAs still exist with the expected
//      address range / offset / permissions.
//   4. Replace each VMA with its private memfd snapshot.
//
// This operates on VMAs reported by /proc/self/maps, not ELF PT_LOAD
// segments. One memfd is therefore created per mapping.
//
// Runtime bytes are copied from the currently mapped memory, preserving
// relocations, GOT modifications and other runtime state.
//
// Important:
//   MAP_FIXED is inherently destructive. It replaces any existing mapping
//   covering the requested range. The implementation therefore minimizes
//   the time between validation and replacement, but cannot eliminate
//   races with unrelated threads modifying the address space.

#pragma once

#include <cstddef>

namespace tcqt {

    int hide_path(const char *needle);

    int hide_paths(const char *const *needles, std::size_t count);

}  // namespace tcqt