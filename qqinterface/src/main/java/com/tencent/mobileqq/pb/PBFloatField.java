package com.tencent.mobileqq.pb;

import java.io.IOException;

public final class PBFloatField extends PBPrimitiveField<Float> {
    private float value = 0.0f;

    public PBFloatField(float f, boolean z) {
        set(f, z);
    }

    public void clear(Object obj) {
        this.value = obj instanceof Float ? ((Float) obj).floatValue() : 0.0f;
        setHasFlag(false);
    }

    public int computeSize(int i) {
        if (has()) {
            return CodedOutputStreamMicro.computeFloatSize(i, this.value);
        }
        return 0;
    }

    public int computeSizeDirectly(int i, Float f) {
        return CodedOutputStreamMicro.computeFloatSize(i, f.floatValue());
    }

    protected void copyFrom(PBField<Float> pBField) {
        PBFloatField pBFloatField = (PBFloatField) pBField;
        set(pBFloatField.value, pBFloatField.has());
    }

    public float get() {
        return this.value;
    }

    public void readFrom(CodedInputStreamMicro codedInputStreamMicro) throws IOException {
        this.value = codedInputStreamMicro.readFloat();
        setHasFlag(true);
    }

    public Float readFromDirectly(CodedInputStreamMicro codedInputStreamMicro) throws IOException {
        return Float.valueOf(codedInputStreamMicro.readFloat());
    }

    public void set(float f) {
        set(f, true);
    }

    public void writeTo(CodedOutputStreamMicro codedOutputStreamMicro, int i) throws IOException {
        if (has()) {
            codedOutputStreamMicro.writeFloat(i, this.value);
        }
    }

    public void writeToDirectly(CodedOutputStreamMicro codedOutputStreamMicro, int i, Float f) throws IOException {
        codedOutputStreamMicro.writeFloat(i, f.floatValue());
    }

    public void set(float f, boolean z) {
        this.value = f;
        setHasFlag(z);
    }
}
