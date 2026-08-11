#pragma once

#include <android/log.h>

#define TCQT_TAG "TCQT-Zygisk"

#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TCQT_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TCQT_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TCQT_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TCQT_TAG, __VA_ARGS__)
