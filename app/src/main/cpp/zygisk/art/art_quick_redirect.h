#pragma once

#include <cstddef>
#include <cstdint>
#include <cstring>
#include <span>

namespace tcqt::zygisk::art_detail {

// Recognize an ARM64 quick-entry stub which replaces x0 (ArtMethod*) before
// branching. Such code carries its own method identity, so a backup must keep
// it. Sending the copied method to the interpreter would bypass its callbacks.
// LSPlant uses LDR X0, literal; LDUR X16, [X0, #entry]; BR X16. TCQT adds a hint.
// Ordinary AOT/JIT method bodies are deliberately not classified as redirects.
inline size_t quick_redirect_literal(std::span<const uint8_t> code, size_t entry_offset) {
    auto word = [&](size_t offset) {
        uint32_t value = 0;
        if (offset + sizeof(value) <= code.size())
            std::memcpy(&value, code.data() + offset, sizeof(value));
        return value;
    };
    size_t start = 0;
    if (word(0) == 0xd503245fu || word(0) == 0xd503249fu || word(0) == 0xd50324dfu)
        start = 4; // optional BTI c/j/jc landing pad
    const uint32_t load_method = word(start);
    if ((load_method & 0xff00001fu) != 0x58000000u) return 0; // LDR X0, literal
    const uint32_t load_entry = word(start + 4);
    const bool unscaled = entry_offset <= 255 &&
            load_entry == (0xf8400010u | (static_cast<uint32_t>(entry_offset) << 12));
    const bool scaled = entry_offset % 8 == 0 && entry_offset / 8 <= 4095 &&
            load_entry == (0xf9400010u | (static_cast<uint32_t>(entry_offset / 8) << 10));
    if (!unscaled && !scaled) return 0;
    size_t branch = start + 8;
    if (word(branch) == 0xdac143f0u || word(branch) == 0xd503241fu)
        branch += 4; // XPACI X16 or the existing TCQT hint
    if (word(branch) != 0xd61f0200u) return 0; // BR X16
    const uint32_t literal_words = (load_method >> 5) & 0x7ffffu;
    if (literal_words & 0x40000u) return 0; // only forward, inline literals
    const size_t literal = start + literal_words * 4;
    return literal >= branch + 4 && literal <= 24 ? literal : 0;
}

} // namespace tcqt::zygisk::art_detail
