#pragma once

#include <android/log.h>

#include "log_file.h"

#define TCQT_TAG "TCQT-Zygisk"

#define LOGD(...)                                                          \
    do {                                                                   \
        tcqt_log_write(ANDROID_LOG_DEBUG, __VA_ARGS__);                    \
        __android_log_print(ANDROID_LOG_DEBUG, TCQT_TAG, __VA_ARGS__);     \
    } while (0)
#define LOGI(...)                                                          \
    do {                                                                   \
        tcqt_log_write(ANDROID_LOG_INFO, __VA_ARGS__);                     \
        __android_log_print(ANDROID_LOG_INFO, TCQT_TAG, __VA_ARGS__);      \
    } while (0)
#define LOGW(...)                                                          \
    do {                                                                   \
        tcqt_log_write(ANDROID_LOG_WARN, __VA_ARGS__);                     \
        __android_log_print(ANDROID_LOG_WARN, TCQT_TAG, __VA_ARGS__);      \
    } while (0)
#define LOGE(...)                                                          \
    do {                                                                   \
        tcqt_log_write(ANDROID_LOG_ERROR, __VA_ARGS__);                    \
        __android_log_print(ANDROID_LOG_ERROR, TCQT_TAG, __VA_ARGS__);     \
    } while (0)
