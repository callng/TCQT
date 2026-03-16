package com.tencent.mobileqq.pb;

public abstract class PBPrimitiveField<T> extends PBField<T> {
    private boolean hasFlag = false;

    public final boolean has() {
        return this.hasFlag;
    }

    public final void setHasFlag(boolean z) {
        this.hasFlag = z;
    }
}
