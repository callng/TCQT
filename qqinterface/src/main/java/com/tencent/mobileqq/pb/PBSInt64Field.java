package com.tencent.mobileqq.pb;

import java.io.IOException;

public final class PBSInt64Field extends PBPrimitiveField<Long> {
    private long value = 0;

    public PBSInt64Field(long j, boolean z) {
        set(j, z);
    }

    public void clear(Object obj) {
        this.value = obj instanceof Long ? ((Long) obj).longValue() : 0L;
        setHasFlag(false);
    }

    public int computeSize(int i) {
        if (has()) {
            return CodedOutputStreamMicro.computeSInt64Size(i, this.value);
        }
        return 0;
    }

    public int computeSizeDirectly(int i, Long l) {
        return CodedOutputStreamMicro.computeSInt64Size(i, l.longValue());
    }

    protected void copyFrom(PBField<Long> pBField) {
        PBSInt64Field pBSInt64Field = (PBSInt64Field) pBField;
        set(pBSInt64Field.value, pBSInt64Field.has());
    }

    public long get() {
        return this.value;
    }

    public void readFrom(CodedInputStreamMicro codedInputStreamMicro) throws IOException {
        this.value = codedInputStreamMicro.readSInt64();
        setHasFlag(true);
    }

    public Long readFromDirectly(CodedInputStreamMicro codedInputStreamMicro) throws IOException {
        return Long.valueOf(codedInputStreamMicro.readSInt64());
    }

    public void set(long j) {
        set(j, true);
    }

    public void writeTo(CodedOutputStreamMicro codedOutputStreamMicro, int i) throws IOException {
        if (has()) {
            codedOutputStreamMicro.writeSInt64(i, this.value);
        }
    }

    public void writeToDirectly(CodedOutputStreamMicro codedOutputStreamMicro, int i, Long l) throws IOException {
        codedOutputStreamMicro.writeSInt64(i, l.longValue());
    }

    public void set(long j, boolean z) {
        this.value = j;
        setHasFlag(z);
    }
}
