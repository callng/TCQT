package com.tencent.mobileqq.pb;

import java.io.IOException;

public final class PBUInt64Field extends PBPrimitiveField<Long> {
    private long value = 0;

    public PBUInt64Field(long j, boolean z) {
        set(j, z);
    }

    public void clear(Object obj) {
        this.value = obj instanceof Long ? ((Long) obj).longValue() : 0L;
        setHasFlag(false);
    }

    public int computeSize(int i) {
        if (has()) {
            return CodedOutputStreamMicro.computeUInt64Size(i, this.value);
        }
        return 0;
    }

    public int computeSizeDirectly(int i, Long l) {
        return CodedOutputStreamMicro.computeUInt64Size(i, l.longValue());
    }

    protected void copyFrom(PBField<Long> pBField) {
        PBUInt64Field pBUInt64Field = (PBUInt64Field) pBField;
        set(pBUInt64Field.value, pBUInt64Field.has());
    }

    public long get() {
        return this.value;
    }

    public void readFrom(CodedInputStreamMicro codedInputStreamMicro) throws IOException {
        this.value = codedInputStreamMicro.readUInt64();
        setHasFlag(true);
    }

    public Long readFromDirectly(CodedInputStreamMicro codedInputStreamMicro) throws IOException {
        return Long.valueOf(codedInputStreamMicro.readUInt64());
    }

    public void set(long j) {
        set(j, true);
    }

    public void writeTo(CodedOutputStreamMicro codedOutputStreamMicro, int i) throws IOException {
        if (has()) {
            codedOutputStreamMicro.writeUInt64(i, this.value);
        }
    }

    public void writeToDirectly(CodedOutputStreamMicro codedOutputStreamMicro, int i, Long l) throws IOException {
        codedOutputStreamMicro.writeUInt64(i, l.longValue());
    }

    public void set(long j, boolean z) {
        this.value = j;
        setHasFlag(z);
    }
}
