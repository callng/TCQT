//
// Decompiled by Jadx - 471ms
//
package com.tencent.mobileqq.pb;

import java.io.IOException;

public final class PBInt64Field extends PBPrimitiveField<Long> {
    public static final PBInt64Field __repeatHelper__ = new PBInt64Field(0, false);
    private long value = 0;

    public PBInt64Field(long j, boolean z) {
        set(j, z);
    }

    public void clear(Object obj) {
        this.value = obj instanceof Long ? ((Long) obj).longValue() : 0L;
        setHasFlag(false);
    }

    public int computeSize(int i) {
        if (has()) {
            return CodedOutputStreamMicro.computeInt64Size(i, this.value);
        }
        return 0;
    }

    public int computeSizeDirectly(int i, Long l) {
        return CodedOutputStreamMicro.computeInt64Size(i, l.longValue());
    }

    protected void copyFrom(PBField<Long> pBField) {
        PBInt64Field pBInt64Field = (PBInt64Field) pBField;
        set(pBInt64Field.value, pBInt64Field.has());
    }

    public long get() {
        return this.value;
    }

    public void readFrom(CodedInputStreamMicro codedInputStreamMicro) throws IOException {
        this.value = codedInputStreamMicro.readInt64();
        setHasFlag(true);
    }

    public Long readFromDirectly(CodedInputStreamMicro codedInputStreamMicro) throws IOException {
        return Long.valueOf(codedInputStreamMicro.readInt64());
    }

    public void set(long j) {
        set(j, true);
    }

    public void writeTo(CodedOutputStreamMicro codedOutputStreamMicro, int i) throws IOException {
        if (has()) {
            codedOutputStreamMicro.writeInt64(i, this.value);
        }
    }

    public void writeToDirectly(CodedOutputStreamMicro codedOutputStreamMicro, int i, Long l) throws IOException {
        codedOutputStreamMicro.writeInt64(i, l.longValue());
    }

    public void set(long j, boolean z) {
        this.value = j;
        setHasFlag(z);
    }
}
