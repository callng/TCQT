package com.tencent.qqnt.kernel.nativeinterface;

import java.io.Serializable;

public final class GroupAuthority implements Serializable {
    public long eventIntVal;
    public int switchVal;
    public byte[] eventBytesVal = new byte[0];

    public byte[] getEventBytesVal() {
        return this.eventBytesVal;
    }

    public long getEventIntVal() {
        return this.eventIntVal;
    }

    public int getSwitchVal() {
        return this.switchVal;
    }
}
