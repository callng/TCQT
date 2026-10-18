// Device test, linked against the real native logger; run in an isolated temp dir.
#include "log_file.h"
#include <android/log.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <unistd.h>
#include <cassert>
#include <cstdio>
#include <fstream>
#include <iterator>
#include <string>

std::string read(const std::string &path) {
    std::ifstream stream(path);
    return {std::istreambuf_iterator<char>(stream), std::istreambuf_iterator<char>()};
}
int main(int argc, char **argv) {
    assert(argc == 2);
    const std::string dir = argv[1];
    tcqt::tcqt_log_write(ANDROID_LOG_INFO, "early sentinel");
    if (fork() == 0) {
        const auto debug_dir = dir + "-debug";
        tcqt::log_file_init(debug_dir, "test:MSF", true);
        assert(tcqt::log_debug_enabled());
        tcqt::tcqt_log_write(ANDROID_LOG_DEBUG, "debug sentinel");
        assert(read(debug_dir + "/test_MSF.debug.log").find("debug sentinel") != std::string::npos);
        assert(read(debug_dir + "/test_MSF.events.log").find("debug sentinel") == std::string::npos);
        _exit(0);
    }
    int debug_status;
    wait(&debug_status);
    assert(WIFEXITED(debug_status) && WEXITSTATUS(debug_status) == 0);
    tcqt::log_file_init(dir, "test:MSF", false);
    assert(!tcqt::log_debug_enabled());
    tcqt::tcqt_log_write(ANDROID_LOG_DEBUG, "debug should be excluded");
    const std::string long_message = std::string(8000, 'S') + " STACK_END";
    tcqt::tcqt_log_write(ANDROID_LOG_ERROR, "%s", long_message.c_str());
    const std::string path = dir + "/test_MSF.events.log";
    auto text = read(path);
    assert(text.find("early sentinel") != std::string::npos);
    assert(text.find("STACK_END") != std::string::npos);
    assert(text.find("debug should") == std::string::npos);
    assert(text.find("# TCQT Zygisk") == 0);
    tcqt::tcqt_log_write(ANDROID_LOG_ERROR, "%s", std::string(40000, 'T').c_str());
    assert(read(path).find("[truncated at 32 KiB]") != std::string::npos);
    const std::string payload(2000, 'R');
    // Separate address spaces share the same filename/lock as overlapping restarts.
    for (int child = 0; child < 3; ++child) {
        if (fork() == 0) {
            for (int i = 0; i < 1100; ++i) tcqt::tcqt_log_write(ANDROID_LOG_INFO, "%s END_RECORD", payload.c_str());
            _exit(0);
        }
    }
    for (int i = 0; i < 3; ++i) { int status; wait(&status); assert(WIFEXITED(status) && WEXITSTATUS(status) == 0); }
    for (int backup = 0; backup <= 3; ++backup) {
        const auto file = path + (backup == 0 ? "" : "." + std::to_string(backup));
        struct stat st {};
        assert(stat(file.c_str(), &st) == 0);
        assert(st.st_size <= 1024 * 1024);
        text = read(file);
        assert(text.find("# TCQT Zygisk") == 0);
        // Every log record remains intact despite concurrent processes + rotation.
        size_t line = text.find('\n') + 1;
        while (line < text.size()) {
            size_t end = text.find('\n', line);
            assert(end != std::string::npos);
            assert(text.substr(end - 10, 10) == "END_RECORD");
            line = end + 1;
        }
    }
    assert(access((path + ".4").c_str(), F_OK) != 0);
    puts("PASS: early replay, long errors, debug filtering, concurrent rotation, bounded files, headers");
}
