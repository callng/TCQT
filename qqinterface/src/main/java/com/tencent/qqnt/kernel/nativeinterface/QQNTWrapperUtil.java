package com.tencent.qqnt.kernel.nativeinterface;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public interface QQNTWrapperUtil {
    final class CppProxy implements QQNTWrapperUtil {
        private final AtomicBoolean destroyed = new AtomicBoolean(false);
        private final long nativeRef;

        CppProxy(long j) {
            if (j != 0) {
                this.nativeRef = j;
                return;
            }
            throw new RuntimeException("nativeRef is zero");
        }

        private native void nativeDestroy(long j);

        public static native long findSourceOfReplyMsgFrom(ArrayList<ReplySourceMsgMainInfo> arrayList, ReplyMsgMainInfo replyMsgMainInfo);

        public void _djinni_private_destroy() {
            if (!this.destroyed.getAndSet(true)) {
                nativeDestroy(this.nativeRef);
            }
        }
    }
}
