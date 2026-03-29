package com.tencent.mobileqq.aio.msg;

import com.tencent.qqnt.kernel.nativeinterface.MsgElement;
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AIOMsgItem {
    public long getMsgId() {
        throw new RuntimeException("Stub!");
    }

    @NotNull
    public final MsgRecord getMsgRecord() {
        throw new RuntimeException("Stub!");
    }

    public long getMsgSeq() {
        throw new RuntimeException("Stub!");
    }

    public int getViewType() {
        throw new RuntimeException("Stub!");
    }

    @Nullable
    public final MsgElement getFirstTypeElement(int i) {
        throw new RuntimeException("Stub!");
    }
}
