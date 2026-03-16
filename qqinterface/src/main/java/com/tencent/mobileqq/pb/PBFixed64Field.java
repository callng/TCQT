package com.tencent.mobileqq.pb;

import java.io.IOException;

public final class PBFixed64Field extends PBPrimitiveField<Long> {
    private long value = 0;

    public PBFixed64Field(long j, boolean z) {
        set(j, z);
    }

    public void clear(Object obj) {
        this.value = obj instanceof Long ? ((Long) obj).longValue() : 0L;
        setHasFlag(false);
    }

    public int computeSize(int i) {
        if (has()) {
            return CodedOutputStreamMicro.computeFixed64Size(i, this.value);
        }
        return 0;
    }

    public int computeSizeDirectly(int i, Long l) {
        return CodedOutputStreamMicro.computeFixed64Size(i, l.longValue());
    }

    protected void copyFrom(PBField<Long> pBField) {
        PBFixed64Field pBFixed64Field = (PBFixed64Field) pBField;
        set(pBFixed64Field.value, pBFixed64Field.has());
    }

    public long get() {
        return this.value;
    }

    public void readFrom(CodedInputStreamMicro codedInputStreamMicro) throws IOException {
        this.value = codedInputStreamMicro.readFixed64();
        setHasFlag(true);
    }

    public Long readFromDirectly(CodedInputStreamMicro codedInputStreamMicro) throws IOException {
        return Long.valueOf(codedInputStreamMicro.readFixed64());
    }

    public void set(long j) {
        set(j, true);
    }

    public void writeTo(CodedOutputStreamMicro codedOutputStreamMicro, int i) throws IOException {
        if (has()) {
            codedOutputStreamMicro.writeFixed64(i, this.value);
        }
    }

    public void writeToDirectly(CodedOutputStreamMicro codedOutputStreamMicro, int i, Long l) throws IOException {
        codedOutputStreamMicro.writeFixed64(i, l.longValue());
    }

    public void set(long j, boolean z) {
        this.value = j;
        setHasFlag(z);
    }
}
