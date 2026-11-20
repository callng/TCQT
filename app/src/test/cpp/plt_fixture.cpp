#include <cstdio>
#include <unistd.h>

// Built several times with different ELF relocation encodings. No host libraries
// or QQ/TIM state are used by this fixture.
#ifdef TCQT_NO_IMPORTS
extern "C" int fixture_no_imports() { return 42; }
#else
extern "C" int fixture_call_plt() { return getpid(); }
extern "C" int fixture_call_got() {
    auto function = &getuid;
    // Keep an address-taking import (GLOB_DAT), rather than a direct PLT call.
    asm volatile("" : "+r"(function));
    return static_cast<int>(function());
}
extern "C" int fixture_read_file(const char *path) {
    FILE *file = fopen(path, "r");
    if (file == nullptr) return -2;
    int value = fgetc(file);
    fclose(file);
    return value;
}
static int value = 42;
static int *volatile relative_pointers[] = {&value, &value, &value, &value};
extern "C" int fixture_relative() { return *relative_pointers[2]; }
#endif
