package com.tencent.mobileqq.pb;

import java.io.IOException;

public final class PBDoubleField extends PBPrimitiveField<Double> {
    private double value = 0.0d;

    public PBDoubleField(double d, boolean z) {
        set(d, z);
    }

    public void clear(Object obj) {
        this.value = obj instanceof Double ? ((Double) obj).doubleValue() : 0.0d;
        setHasFlag(false);
    }

    public int computeSize(int i) {
        if (has()) {
            return CodedOutputStreamMicro.computeDoubleSize(i, this.value);
        }
        return 0;
    }

    public int computeSizeDirectly(int i, Double d) {
        return CodedOutputStreamMicro.computeDoubleSize(i, d.doubleValue());
    }

    protected void copyFrom(PBField<Double> pBField) {
        PBDoubleField pBDoubleField = (PBDoubleField) pBField;
        set(pBDoubleField.value, pBDoubleField.has());
    }

    public double get() {
        return this.value;
    }

    public void readFrom(CodedInputStreamMicro codedInputStreamMicro) throws IOException {
        this.value = codedInputStreamMicro.readDouble();
        setHasFlag(true);
    }

    public Double readFromDirectly(CodedInputStreamMicro codedInputStreamMicro) throws IOException {
        return Double.valueOf(codedInputStreamMicro.readDouble());
    }

    public void set(double d) {
        set(d, true);
    }

    public void writeTo(CodedOutputStreamMicro codedOutputStreamMicro, int i) throws IOException {
        if (has()) {
            codedOutputStreamMicro.writeDouble(i, this.value);
        }
    }

    public void writeToDirectly(CodedOutputStreamMicro codedOutputStreamMicro, int i, Double d) throws IOException {
        codedOutputStreamMicro.writeDouble(i, d.doubleValue());
    }

    public void set(double d, boolean z) {
        this.value = d;
        setHasFlag(z);
    }
}
