package com.tencent.mobileqq.pb;

import java.io.IOException;

public final class PBInt32Field extends PBPrimitiveField<Integer> {
    private int value = 0;

    public PBInt32Field(int i, boolean z) {
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
            return CodedOutputStreamMicro.computeInt32Size(i, this.value);
        }
        return 0;
    }

    public int computeSizeDirectly(int i, Integer num) {
        return CodedOutputStreamMicro.computeInt32Size(i, num.intValue());
    }

    protected void copyFrom(PBField<Integer> pBField) {
        PBInt32Field pBInt32Field = (PBInt32Field) pBField;
        set(pBInt32Field.value, pBInt32Field.has());
    }

    public int get() {
        return this.value;
    }

    public void readFrom(CodedInputStreamMicro codedInputStreamMicro) throws IOException {
        this.value = codedInputStreamMicro.readInt32();
        setHasFlag(true);
    }

    public Integer readFromDirectly(CodedInputStreamMicro codedInputStreamMicro) throws IOException {
        return Integer.valueOf(codedInputStreamMicro.readInt32());
    }

    public void set(int i) {
        set(i, true);
    }

    public void writeTo(CodedOutputStreamMicro codedOutputStreamMicro, int i) throws IOException {
        if (has()) {
            codedOutputStreamMicro.writeInt32(i, this.value);
        }
    }

    public void writeToDirectly(CodedOutputStreamMicro codedOutputStreamMicro, int i, Integer num) throws IOException {
        codedOutputStreamMicro.writeInt32(i, num.intValue());
    }

    public void set(int i, boolean z) {
        this.value = i;
        setHasFlag(z);
    }
}
