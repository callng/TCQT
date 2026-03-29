package com.tencent.mobileqq.pb;

import java.io.IOException;

public final class PBStringField extends PBPrimitiveField<String> {
    private String value = "";

    public PBStringField(String str, boolean z) {
        set(str, z);
    }

    public void clear(Object obj) {
        this.value = obj instanceof String ? (String) obj : "";
        setHasFlag(false);
    }

    public int computeSize(int i) {
        if (!has()) {
            return 0;
        }
        String str = this.value;
        if (str != null) {
            return CodedOutputStreamMicro.computeStringSize(i, str);
        }
        throw new RuntimeException(this + " encounter string null, null for PBStringField is forbidden!");
    }

    protected void copyFrom(PBField<String> pBField) {
        PBStringField pBStringField = (PBStringField) pBField;
        set(pBStringField.value, pBStringField.has());
    }

    public String get() {
        return this.value;
    }

    public void readFrom(CodedInputStreamMicro codedInputStreamMicro) throws IOException {
        this.value = codedInputStreamMicro.readString();
        setHasFlag(true);
    }

    public void set(String str) {
        set(str, true);
    }

    public void writeTo(CodedOutputStreamMicro codedOutputStreamMicro, int i) throws IOException {
        if (has()) {
            codedOutputStreamMicro.writeString(i, this.value);
        }
    }

    public int computeSizeDirectly(int i, String str) {
        return CodedOutputStreamMicro.computeStringSize(i, str);
    }

    public String readFromDirectly(CodedInputStreamMicro codedInputStreamMicro) throws IOException {
        return codedInputStreamMicro.readString();
    }

    public void set(String str, boolean z) {
        this.value = str;
        setHasFlag(z);
    }

    public void writeToDirectly(CodedOutputStreamMicro codedOutputStreamMicro, int i, String str) throws IOException {
        codedOutputStreamMicro.writeString(i, str);
    }
}
