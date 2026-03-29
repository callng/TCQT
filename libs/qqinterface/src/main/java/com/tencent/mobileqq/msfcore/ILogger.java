package com.tencent.mobileqq.msfcore;

import androidx.annotation.NonNull;

public interface ILogger {
    void d(@NonNull String str, @NonNull String str2);

    void e(@NonNull String str, @NonNull String str2);

    void i(@NonNull String str, @NonNull String str2);
}
