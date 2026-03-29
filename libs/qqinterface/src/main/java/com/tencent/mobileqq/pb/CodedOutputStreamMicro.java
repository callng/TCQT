package com.tencent.mobileqq.pb;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

public final class CodedOutputStreamMicro {

    public static class OutOfSpaceException extends IOException {
        OutOfSpaceException() {
            super("CodedOutputStream was writing to a flat byte array and ran out of space.");
        }
    }

    public static final int DEFAULT_BUFFER_SIZE = 4096;
    public static final int LITTLE_ENDIAN_32_SIZE = 4;
    public static final int LITTLE_ENDIAN_64_SIZE = 8;
    private final byte[] buffer;
    private final int limit;
    private final OutputStream output;
    private int position;

    private CodedOutputStreamMicro(OutputStream outputStream, byte[] bArr) {
        this.output = outputStream;
        this.buffer = bArr;
        this.position = 0;
        this.limit = bArr.length;
    }

    public static int computeBoolSize(int i, boolean z) {
        return computeTagSize(i) + computeBoolSizeNoTag(z);
    }

    public static int computeBoolSizeNoTag(boolean z) {
        return 1;
    }

    public static int computeByteArraySize(int i, byte[] bArr) {
        return computeTagSize(i) + computeByteArraySizeNoTag(bArr);
    }

    public static int computeByteArraySizeNoTag(byte[] bArr) {
        return computeRawVarint32Size(bArr.length) + bArr.length;
    }

    public static int computeBytesSize(int i, ByteStringMicro byteStringMicro) {
        return computeTagSize(i) + computeBytesSizeNoTag(byteStringMicro);
    }

    public static int computeBytesSizeNoTag(ByteStringMicro byteStringMicro) {
        return computeRawVarint32Size(byteStringMicro.size()) + byteStringMicro.size();
    }

    public static int computeDoubleSize(int i, double d) {
        return computeTagSize(i) + computeDoubleSizeNoTag(d);
    }

    public static int computeDoubleSizeNoTag(double d) {
        return 8;
    }

    public static int computeEnumSize(int i, int i2) {
        return computeTagSize(i) + computeEnumSizeNoTag(i2);
    }

    public static int computeEnumSizeNoTag(int i) {
        return computeRawVarint32Size(i);
    }

    public static int computeFixed32Size(int i, int i2) {
        return computeTagSize(i) + computeFixed32SizeNoTag(i2);
    }

    public static int computeFixed32SizeNoTag(int i) {
        return 4;
    }

    public static int computeFixed64Size(int i, long j) {
        return computeTagSize(i) + computeFixed64SizeNoTag(j);
    }

    public static int computeFixed64SizeNoTag(long j) {
        return 8;
    }

    public static int computeFloatSize(int i, float f) {
        return computeTagSize(i) + computeFloatSizeNoTag(f);
    }

    public static int computeFloatSizeNoTag(float f) {
        return 4;
    }

    public static int computeGroupSize(int i, MessageMicro<?> messageMicro) {
        return (computeTagSize(i) * 2) + computeGroupSizeNoTag(messageMicro);
    }

    public static int computeGroupSizeNoTag(MessageMicro<?> messageMicro) {
        return messageMicro.getCachedSize();
    }

    public static int computeInt32Size(int i, int i2) {
        return computeTagSize(i) + computeInt32SizeNoTag(i2);
    }

    public static int computeInt32SizeNoTag(int i) {
        if (i >= 0) {
            return computeRawVarint32Size(i);
        }
        return 10;
    }

    public static int computeInt64Size(int i, long j) {
        return computeTagSize(i) + computeInt64SizeNoTag(j);
    }

    public static int computeInt64SizeNoTag(long j) {
        return computeRawVarint64Size(j);
    }

    public static int computeMessageSize(int i, MessageMicro<?> messageMicro) {
        return computeTagSize(i) + computeMessageSizeNoTag(messageMicro);
    }

    public static int computeMessageSizeNoTag(MessageMicro<?> messageMicro) {
        int cachedSize = messageMicro.getCachedSize();
        return computeRawVarint32Size(cachedSize) + cachedSize;
    }

    public static int computeRawVarint32Size(int i) {
        if ((i & (-128)) == 0) {
            return 1;
        }
        if ((i & (-16384)) == 0) {
            return 2;
        }
        if (((-2097152) & i) == 0) {
            return 3;
        }
        return (i & (-268435456)) == 0 ? 4 : 5;
    }

    public static int computeRawVarint64Size(long j) {
        if (((-128) & j) == 0) {
            return 1;
        }
        if (((-16384) & j) == 0) {
            return 2;
        }
        if (((-2097152) & j) == 0) {
            return 3;
        }
        if (((-268435456) & j) == 0) {
            return 4;
        }
        if (((-34359738368L) & j) == 0) {
            return 5;
        }
        if (((-4398046511104L) & j) == 0) {
            return 6;
        }
        if (((-562949953421312L) & j) == 0) {
            return 7;
        }
        if (((-72057594037927936L) & j) == 0) {
            return 8;
        }
        return (j & Long.MIN_VALUE) == 0 ? 9 : 10;
    }

    public static int computeSFixed32Size(int i, int i2) {
        return computeTagSize(i) + computeSFixed32SizeNoTag(i2);
    }

    public static int computeSFixed32SizeNoTag(int i) {
        return 4;
    }

    public static int computeSFixed64Size(int i, long j) {
        return computeTagSize(i) + computeSFixed64SizeNoTag(j);
    }

    public static int computeSFixed64SizeNoTag(long j) {
        return 8;
    }

    public static int computeSInt32Size(int i, int i2) {
        return computeTagSize(i) + computeSInt32SizeNoTag(i2);
    }

    public static int computeSInt32SizeNoTag(int i) {
        return computeRawVarint32Size(encodeZigZag32(i));
    }

    public static int computeSInt64Size(int i, long j) {
        return computeTagSize(i) + computeSInt64SizeNoTag(j);
    }

    public static int computeSInt64SizeNoTag(long j) {
        return computeRawVarint64Size(encodeZigZag64(j));
    }

    public static int computeStringSize(int i, String str) {
        return computeTagSize(i) + computeStringSizeNoTag(str);
    }

    public static int computeStringSizeNoTag(String str) {
        try {
            byte[] bytes = str.getBytes("UTF-8");
            return computeRawVarint32Size(bytes.length) + bytes.length;
        } catch (UnsupportedEncodingException unused) {
            throw new RuntimeException("UTF-8 not supported.");
        }
    }

    public static int computeTagSize(int i) {
        return computeRawVarint32Size(WireFormatMicro.makeTag(i, 0));
    }

    public static int computeUInt32Size(int i, int i2) {
        return computeTagSize(i) + computeUInt32SizeNoTag(i2);
    }

    public static int computeUInt32SizeNoTag(int i) {
        return computeRawVarint32Size(i);
    }

    public static int computeUInt64Size(int i, long j) {
        return computeTagSize(i) + computeUInt64SizeNoTag(j);
    }

    public static int computeUInt64SizeNoTag(long j) {
        return computeRawVarint64Size(j);
    }

    public static int encodeZigZag32(int i) {
        return (i >> 31) ^ (i << 1);
    }

    public static long encodeZigZag64(long j) {
        return (j >> 63) ^ (j << 1);
    }

    public static CodedOutputStreamMicro newInstance(OutputStream outputStream) {
        return newInstance(outputStream, DEFAULT_BUFFER_SIZE);
    }

    private void refreshBuffer() throws IOException {
        OutputStream outputStream = this.output;
        if (outputStream == null) {
            throw new OutOfSpaceException();
        }
        outputStream.write(this.buffer, 0, this.position);
        this.position = 0;
    }

    public void checkNoSpaceLeft() {
        if (spaceLeft() != 0) {
            throw new IllegalStateException("Did not write as much data as expected.");
        }
    }

    public void flush() throws IOException {
        if (this.output != null) {
            refreshBuffer();
        }
    }

    public int spaceLeft() {
        if (this.output == null) {
            return this.limit - this.position;
        }
        throw new UnsupportedOperationException("spaceLeft() can only be called on CodedOutputStreams that are writing to a flat array.");
    }

    public void writeBool(int i, boolean z) throws IOException {
        writeTag(i, 0);
        writeBoolNoTag(z);
    }

    public void writeBoolNoTag(boolean z) throws IOException {
        writeRawByte(z ? 1 : 0);
    }

    public void writeByteArray(int i, byte[] bArr) throws IOException {
        writeTag(i, 2);
        writeByteArrayNoTag(bArr);
    }

    public void writeByteArrayNoTag(byte[] bArr) throws IOException {
        writeRawVarint32(bArr.length);
        writeRawBytes(bArr);
    }

    public void writeBytes(int i, ByteStringMicro byteStringMicro) throws IOException {
        writeTag(i, 2);
        writeBytesNoTag(byteStringMicro);
    }

    public void writeBytesNoTag(ByteStringMicro byteStringMicro) throws IOException {
        byte[] byteArray = byteStringMicro.toByteArray();
        writeRawVarint32(byteArray.length);
        writeRawBytes(byteArray);
    }

    public void writeDouble(int i, double d) throws IOException {
        writeTag(i, 1);
        writeDoubleNoTag(d);
    }

    public void writeDoubleNoTag(double d) throws IOException {
        writeRawLittleEndian64(Double.doubleToLongBits(d));
    }

    public void writeEnum(int i, int i2) throws IOException {
        writeTag(i, 0);
        writeEnumNoTag(i2);
    }

    public void writeEnumNoTag(int i) throws IOException {
        writeRawVarint32(i);
    }

    public void writeFixed32(int i, int i2) throws IOException {
        writeTag(i, 5);
        writeFixed32NoTag(i2);
    }

    public void writeFixed32NoTag(int i) throws IOException {
        writeRawLittleEndian32(i);
    }

    public void writeFixed64(int i, long j) throws IOException {
        writeTag(i, 1);
        writeFixed64NoTag(j);
    }

    public void writeFixed64NoTag(long j) throws IOException {
        writeRawLittleEndian64(j);
    }

    public void writeFloat(int i, float f) throws IOException {
        writeTag(i, 5);
        writeFloatNoTag(f);
    }

    public void writeFloatNoTag(float f) throws IOException {
        writeRawLittleEndian32(Float.floatToIntBits(f));
    }

    public void writeGroup(int i, MessageMicro<?> messageMicro) throws IOException {
        writeTag(i, 3);
        writeGroupNoTag(messageMicro);
        writeTag(i, 4);
    }

    public void writeGroupNoTag(MessageMicro<?> messageMicro) throws IOException {
        messageMicro.writeTo(this);
    }

    public void writeInt32(int i, int i2) throws IOException {
        writeTag(i, 0);
        writeInt32NoTag(i2);
    }

    public void writeInt32NoTag(int i) throws IOException {
        if (i >= 0) {
            writeRawVarint32(i);
        } else {
            writeRawVarint64(i);
        }
    }

    public void writeInt64(int i, long j) throws IOException {
        writeTag(i, 0);
        writeInt64NoTag(j);
    }

    public void writeInt64NoTag(long j) throws IOException {
        writeRawVarint64(j);
    }

    public void writeMessage(int i, MessageMicro<?> messageMicro) throws IOException {
        writeTag(i, 2);
        writeMessageNoTag(messageMicro);
    }

    public void writeMessageNoTag(MessageMicro<?> messageMicro) throws IOException {
        writeRawVarint32(messageMicro.getCachedSize());
        messageMicro.writeTo(this);
    }

    public void writeRawByte(byte b) throws IOException {
        if (this.position == this.limit) {
            refreshBuffer();
        }
        byte[] bArr = this.buffer;
        int i = this.position;
        this.position = i + 1;
        bArr[i] = b;
    }

    public void writeRawBytes(byte[] bArr) throws IOException {
        writeRawBytes(bArr, 0, bArr.length);
    }

    public void writeRawLittleEndian32(int i) throws IOException {
        writeRawByte(i & 255);
        writeRawByte((i >> 8) & 255);
        writeRawByte((i >> 16) & 255);
        writeRawByte((i >> 24) & 255);
    }

    public void writeRawLittleEndian64(long j) throws IOException {
        writeRawByte(((int) j) & 255);
        writeRawByte(((int) (j >> 8)) & 255);
        writeRawByte(((int) (j >> 16)) & 255);
        writeRawByte(((int) (j >> 24)) & 255);
        writeRawByte(((int) (j >> 32)) & 255);
        writeRawByte(((int) (j >> 40)) & 255);
        writeRawByte(((int) (j >> 48)) & 255);
        writeRawByte(((int) (j >> 56)) & 255);
    }

    public void writeRawVarint32(int i) throws IOException {
        while ((i & (-128)) != 0) {
            writeRawByte((i & 127) | 128);
            i >>>= 7;
        }
        writeRawByte(i);
    }

    public void writeRawVarint64(long j) throws IOException {
        while (((-128) & j) != 0) {
            writeRawByte((((int) j) & 127) | 128);
            j >>>= 7;
        }
        writeRawByte((int) j);
    }

    public void writeSFixed32(int i, int i2) throws IOException {
        writeTag(i, 5);
        writeSFixed32NoTag(i2);
    }

    public void writeSFixed32NoTag(int i) throws IOException {
        writeRawLittleEndian32(i);
    }

    public void writeSFixed64(int i, long j) throws IOException {
        writeTag(i, 1);
        writeSFixed64NoTag(j);
    }

    public void writeSFixed64NoTag(long j) throws IOException {
        writeRawLittleEndian64(j);
    }

    public void writeSInt32(int i, int i2) throws IOException {
        writeTag(i, 0);
        writeSInt32NoTag(i2);
    }

    public void writeSInt32NoTag(int i) throws IOException {
        writeRawVarint32(encodeZigZag32(i));
    }

    public void writeSInt64(int i, long j) throws IOException {
        writeTag(i, 0);
        writeSInt64NoTag(j);
    }

    public void writeSInt64NoTag(long j) throws IOException {
        writeRawVarint64(encodeZigZag64(j));
    }

    public void writeString(int i, String str) throws IOException {
        writeTag(i, 2);
        writeStringNoTag(str);
    }

    public void writeStringNoTag(String str) throws IOException {
        byte[] bytes = str.getBytes("UTF-8");
        writeRawVarint32(bytes.length);
        writeRawBytes(bytes);
    }

    public void writeTag(int i, int i2) throws IOException {
        writeRawVarint32(WireFormatMicro.makeTag(i, i2));
    }

    public void writeUInt32(int i, int i2) throws IOException {
        writeTag(i, 0);
        writeUInt32NoTag(i2);
    }

    public void writeUInt32NoTag(int i) throws IOException {
        writeRawVarint32(i);
    }

    public void writeUInt64(int i, long j) throws IOException {
        writeTag(i, 0);
        writeUInt64NoTag(j);
    }

    public void writeUInt64NoTag(long j) throws IOException {
        writeRawVarint64(j);
    }

    private CodedOutputStreamMicro(byte[] bArr, int i, int i2) {
        this.output = null;
        this.buffer = bArr;
        this.position = i;
        this.limit = i + i2;
    }

    public static CodedOutputStreamMicro newInstance(OutputStream outputStream, int i) {
        return new CodedOutputStreamMicro(outputStream, new byte[i]);
    }

    public void writeRawByte(int i) throws IOException {
        writeRawByte((byte) i);
    }

    public void writeRawBytes(byte[] bArr, int i, int i2) throws IOException {
        int i3 = this.limit;
        int i4 = this.position;
        if (i3 - i4 >= i2) {
            System.arraycopy(bArr, i, this.buffer, i4, i2);
            this.position += i2;
            return;
        }
        int i5 = i3 - i4;
        System.arraycopy(bArr, i, this.buffer, i4, i5);
        int i6 = i + i5;
        int i7 = i2 - i5;
        this.position = this.limit;
        refreshBuffer();
        if (i7 > this.limit) {
            this.output.write(bArr, i6, i7);
        } else {
            System.arraycopy(bArr, i6, this.buffer, 0, i7);
            this.position = i7;
        }
    }

    public static CodedOutputStreamMicro newInstance(byte[] bArr) {
        return newInstance(bArr, 0, bArr.length);
    }

    public static CodedOutputStreamMicro newInstance(byte[] bArr, int i, int i2) {
        return new CodedOutputStreamMicro(bArr, i, i2);
    }
}
