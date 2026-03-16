package com.tencent.mobileqq.pb;

import java.io.IOException;

public final class PBSInt32Field extends PBPrimitiveField<Integer> {
    private int value = 0;

    public PBSInt32Field(int i, boolean z) {
        set(i, z);
    }

    public void clear(Object obj) {
        if (obj instanceof Integer) {
            this.value = ((Integer) obj).intValue();
        } else {
            this.value = 0;
        }
        setHasFlag(false);
    }

    public int computeSize(int i) {
        if (has()) {
            return CodedOutputStreamMicro.computeSInt32Size(i, this.value);
        }
        return 0;
    }

    public int computeSizeDirectly(int i, Integer num) {
        return CodedOutputStreamMicro.computeSInt32Size(i, num.intValue());
    }

    protected void copyFrom(PBField<Integer> pBField) {
        PBSInt32Field pBSInt32Field = (PBSInt32Field) pBField;
        set(pBSInt32Field.value, pBSInt32Field.has());
    }

    public int get() {
        return this.value;
    }

    public void readFrom(CodedInputStreamMicro codedInputStreamMicro) throws IOException {
        this.value = codedInputStreamMicro.readSInt32();
        setHasFlag(true);
    }

    public Integer readFromDirectly(CodedInputStreamMicro codedInputStreamMicro) throws IOException {
        return Integer.valueOf(codedInputStreamMicro.readSInt32());
    }

    public void set(int i) {
        set(i, true);
    }

    public void writeTo(CodedOutputStreamMicro codedOutputStreamMicro, int i) throws IOException {
        if (has()) {
            codedOutputStreamMicro.writeSInt32(i, this.value);
        }
    }

    public void writeToDirectly(CodedOutputStreamMicro codedOutputStreamMicro, int i, Integer num) throws IOException {
        codedOutputStreamMicro.writeSInt32(i, num.intValue());
    }

    public void set(int i, boolean z) {
        this.value = i;
        setHasFlag(z);
    }
}
