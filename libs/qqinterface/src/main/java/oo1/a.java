package oo1;

import org.jetbrains.annotations.NotNull;
import java.util.Map;

public interface a {
    void onFail(@NotNull String str);

    void onSuccess(@NotNull Map<String, String> map);
}
