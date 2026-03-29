package com.tencent.mobileqq.pb;

import java.io.IOException;

public abstract class PBField<T> {
    public static PBBoolField initBool(boolean z) {
        return new PBBoolField(z, false);
    }

    public static PBBytesField initBytes(ByteStringMicro byteStringMicro) {
        return new PBBytesField(byteStringMicro, false);
    }

    public static PBDoubleField initDouble(double d) {
        return new PBDoubleField(d, false);
    }

    public static PBEnumField initEnum(int i) {
        return new PBEnumField(i, false);
    }

    public static PBFixed32Field initFixed32(int i) {
        return new PBFixed32Field(i, false);
    }

    public static PBFixed64Field initFixed64(long j) {
        return new PBFixed64Field(j, false);
    }

    public static PBFloatField initFloat(float f) {
        return new PBFloatField(f, false);
    }

    public static PBInt32Field initInt32(int i) {
        return new PBInt32Field(i, false);
    }

    public static PBInt64Field initInt64(long j) {
        return new PBInt64Field(j, false);
    }

    public static <T> PBRepeatField<T> initRepeat(PBField<T> pBField) {
        return new PBRepeatField<>(pBField);
    }

    public static <T extends MessageMicro<T>> PBRepeatMessageField<T> initRepeatMessage(Class<T> cls) {
        return new PBRepeatMessageField<>(cls);
    }

    public static PBSFixed32Field initSFixed32(int i) {
        return new PBSFixed32Field(i, false);
    }

    public static PBSFixed64Field initSFixed64(long j) {
        return new PBSFixed64Field(j, false);
    }

    public static PBSInt32Field initSInt32(int i) {
        return new PBSInt32Field(i, false);
    }

    public static PBSInt64Field initSInt64(long j) {
        return new PBSInt64Field(j, false);
    }

    public static PBStringField initString(String str) {
        return new PBStringField(str, false);
    }

    public static PBUInt32Field initUInt32(int i) {
        return new PBUInt32Field(i, false);
    }

    public static PBUInt64Field initUInt64(long j) {
        return new PBUInt64Field(j, false);
    }

    public final void clear() {
        clear(null);
    }

    public abstract void clear(Object obj);

    public abstract int computeSize(int i);

    protected abstract int computeSizeDirectly(int i, T t);

    protected abstract void copyFrom(PBField<T> pBField);

    public abstract void readFrom(CodedInputStreamMicro codedInputStreamMicro) throws IOException;

    protected abstract T readFromDirectly(CodedInputStreamMicro codedInputStreamMicro) throws IOException;

    public abstract void writeTo(CodedOutputStreamMicro codedOutputStreamMicro, int i) throws IOException;

    protected abstract void writeToDirectly(CodedOutputStreamMicro codedOutputStreamMicro, int i, T t) throws IOException;

    public static PBBytesField initBytes(byte[] bArr) {
        return new PBBytesField(ByteStringMicro.copyFrom(bArr), false);
    }
}
