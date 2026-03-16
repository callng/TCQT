package com.tencent.mobileqq.pb;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

public final class CodedInputStreamMicro {
    private static final int BUFFER_SIZE = 4096;
    private static final int DEFAULT_RECURSION_LIMIT = 64;
    private static final int DEFAULT_SIZE_LIMIT = 0x04000000;
    private final byte[] buffer;
    private int bufferPos;
    private int bufferSize;
    private int bufferSizeAfterLimit;
    private int currentLimit = Integer.MAX_VALUE;
    private final InputStream input;
    private int lastTag;
    private int recursionDepth;
    private int recursionLimit = 64;
    private int sizeLimit = 0x04000000;
    private int totalBytesRetired;

    private CodedInputStreamMicro(InputStream arg0) {
        this.buffer = new byte[4096];
        this.bufferSize = 0;
        this.bufferPos = 0;
        this.input = arg0;
    }

    private CodedInputStreamMicro(byte[] arg0, int arg1, int arg2) {
        this.buffer = arg0;
        this.bufferSize = arg2 + arg1;
        this.bufferPos = arg1;
        this.input = null;
    }

    public static int decodeZigZag32(int arg0) {
        return -(arg0 & 1) ^ arg0 >>> 1;
    }

    public static long decodeZigZag64(long arg0) {
        return -(arg0 & 1L) ^ arg0 >>> 1;
    }

    public static CodedInputStreamMicro newInstance(InputStream arg0) {
        return new CodedInputStreamMicro(arg0);
    }

    public static CodedInputStreamMicro newInstance(byte[] arg0) {
        return newInstance(arg0, 0, arg0.length);
    }

    public static CodedInputStreamMicro newInstance(byte[] arg0, int arg1, int arg2) {
        return new CodedInputStreamMicro(arg0, arg1, arg2);
    }

    static int readRawVarint32(InputStream arg0) throws IOException {
        int i = 0;

        int i1;
        for (i1 = 0; i < 32; i += 7) {
            int i3 = arg0.read();
            if (i3 == -1) {
                throw InvalidProtocolBufferMicroException.truncatedMessage();
            }

            i1 |= (i3 & 127) << i;
            if ((i3 & 128) == 0) {
                return i1;
            }
        }

        while (i < 64) {
            int i2 = arg0.read();
            if (i2 == -1) {
                throw InvalidProtocolBufferMicroException.truncatedMessage();
            }

            if ((i2 & 128) == 0) {
                return i1;
            }

            i += 7;
        }

        throw InvalidProtocolBufferMicroException.malformedVarint();
    }

    private void recomputeBufferSizeAfterLimit() {
        int i = this.bufferSize + this.bufferSizeAfterLimit;
        this.bufferSize = i;
        int i1 = this.totalBytesRetired + i;
        int i2 = this.currentLimit;
        if (i1 > i2) {
            i1 -= i2;
            this.bufferSizeAfterLimit = i1;
            this.bufferSize = i - i1;
        } else {
            this.bufferSizeAfterLimit = 0;
        }
    }

    private boolean refillBuffer(boolean i) throws IOException {
        int i1 = this.bufferPos;
        int i2 = this.bufferSize;
        if (i1 >= i2) {
            i1 = this.totalBytesRetired;
            if (i1 + i2 == this.currentLimit) {
                if (!i) {
                    return false;
                } else {
                    throw InvalidProtocolBufferMicroException.truncatedMessage();
                }
            } else {
                this.totalBytesRetired = i1 + i2;
                this.bufferPos = 0;
                InputStream inputStream = this.input;
                if (inputStream == null) {
                    i2 = -1;
                } else {
                    i2 = inputStream.read(this.buffer);
                }

                this.bufferSize = i2;
                if (i2 == 0 || i2 < -1) {
                    StringBuilder stringBuilder = new StringBuilder("InputStream#read(byte[]) returned invalid result: ");
                    stringBuilder.append(this.bufferSize);
                    stringBuilder.append("\nThe InputStream implementation is buggy.");
                    throw new IllegalStateException(stringBuilder.toString());
                } else if (i2 == -1) {
                    this.bufferSize = 0;
                    if (!i) {
                        return false;
                    } else {
                        throw InvalidProtocolBufferMicroException.truncatedMessage();
                    }
                } else {
                    this.recomputeBufferSizeAfterLimit();
                    i2 = this.totalBytesRetired + this.bufferSize + this.bufferSizeAfterLimit;
                    if (i2 <= this.sizeLimit && i2 >= 0) {
                        return true;
                    } else {
                        throw InvalidProtocolBufferMicroException.sizeLimitExceeded();
                    }
                }
            }
        } else {
            throw new IllegalStateException("refillBuffer() called when buffer wasn't empty.");
        }
    }

    public void checkLastTagWas(int arg0) throws InvalidProtocolBufferMicroException {
        if (this.lastTag != arg0) {
            throw InvalidProtocolBufferMicroException.invalidEndTag();
        }
    }

    public int getBytesUntilLimit() {
        int i = this.currentLimit;
        return i == Integer.MAX_VALUE ? -1 : i - (this.totalBytesRetired + this.bufferPos);
    }

    public boolean isAtEnd() throws IOException {
        return this.bufferPos == this.bufferSize && !this.refillBuffer(false);
    }

    public void popLimit(int arg0) {
        this.currentLimit = arg0;
        this.recomputeBufferSizeAfterLimit();
    }

    public int pushLimit(int arg0) throws InvalidProtocolBufferMicroException {
        if (arg0 >= 0) {
            arg0 += this.totalBytesRetired + this.bufferPos;
            int i = this.currentLimit;
            if (arg0 <= i) {
                this.currentLimit = arg0;
                this.recomputeBufferSizeAfterLimit();
                return i;
            } else {
                throw InvalidProtocolBufferMicroException.truncatedMessage();
            }
        } else {
            throw InvalidProtocolBufferMicroException.negativeSize();
        }
    }

    public boolean readBool() throws IOException {
        return this.readRawVarint32() != 0;
    }

    public ByteStringMicro readBytes() throws IOException {
        int i = this.readRawVarint32();
        int i1 = this.bufferSize;
        int i2 = this.bufferPos;
        if (i <= i1 - i2 && i > 0) {
            ByteStringMicro byteStringMicro = ByteStringMicro.copyFrom(this.buffer, i2, i);
            this.bufferPos += i;
            return byteStringMicro;
        } else {
            return ByteStringMicro.copyFrom(this.readRawBytes(i));
        }
    }

    public double readDouble() throws IOException {
        return Double.longBitsToDouble(this.readRawLittleEndian64());
    }

    public int readEnum() throws IOException {
        return this.readRawVarint32();
    }

    public int readFixed32() throws IOException {
        return this.readRawLittleEndian32();
    }

    public long readFixed64() throws IOException {
        return this.readRawLittleEndian64();
    }

    public float readFloat() throws IOException {
        return Float.intBitsToFloat(this.readRawLittleEndian32());
    }

    public void readGroup(MessageMicro<?> arg0, int arg1) throws IOException {
        int i = this.recursionDepth;
        if (i < this.recursionLimit) {
            this.recursionDepth = i + 1;
            arg0.mergeFrom(this);
            this.checkLastTagWas(WireFormatMicro.makeTag(arg1, 4));
            this.recursionDepth--;
        } else {
            throw InvalidProtocolBufferMicroException.recursionLimitExceeded();
        }
    }

    public int readInt32() throws IOException {
        return this.readRawVarint32();
    }

    public long readInt64() throws IOException {
        return this.readRawVarint64();
    }

    public void readMessage(MessageMicro<?> arg0) throws IOException {
        int i = this.readRawVarint32();
        if (this.recursionDepth < this.recursionLimit) {
            i = this.pushLimit(i);
            this.recursionDepth++;
            arg0.mergeFrom(this);
            this.checkLastTagWas(0);
            this.recursionDepth--;
            this.popLimit(i);
        } else {
            throw InvalidProtocolBufferMicroException.recursionLimitExceeded();
        }
    }

    public byte readRawByte() throws IOException {
        if (this.bufferPos == this.bufferSize) {
            this.refillBuffer(true);
        }

        byte[] bArray = this.buffer;
        int i = this.bufferPos++;
        return bArray[i];
    }

    public byte[] readRawBytes(int arg0) throws IOException {
        if (arg0 < 0) {
            throw InvalidProtocolBufferMicroException.negativeSize();
        } else {
            int i = this.totalBytesRetired;
            int i1 = this.bufferPos;
            int i2 = this.currentLimit;
            if (i + i1 + arg0 > i2) {
                this.skipRawBytes(i2 - i - i1);
                throw InvalidProtocolBufferMicroException.truncatedMessage();
            } else {
                i2 = this.bufferSize;
                if (arg0 <= i2 - i1) {
                    byte[] bArray = new byte[arg0];
                    System.arraycopy(this.buffer, i1, bArray, 0, arg0);
                    this.bufferPos += arg0;
                    return bArray;
                } else if (arg0 < 4096) {
                    byte[] bArray1 = new byte[arg0];
                    i = i2 - i1;
                    System.arraycopy(this.buffer, i1, bArray1, 0, i);
                    this.bufferPos = this.bufferSize;

                    while (true) {
                        this.refillBuffer(true);
                        int i3 = arg0 - i;
                        i2 = this.bufferSize;
                        if (i3 <= i2) {
                            System.arraycopy(this.buffer, 0, bArray1, i, i3);
                            this.bufferPos = i3;
                            return bArray1;
                        }

                        System.arraycopy(this.buffer, 0, bArray1, i, i2);
                        i2 = this.bufferSize;
                        i += i2;
                        this.bufferPos = i2;
                    }
                } else {
                    this.totalBytesRetired = i + i2;
                    this.bufferPos = 0;
                    this.bufferSize = 0;
                    int i4 = i2 - i1;
                    i = arg0 - i4;
                    ArrayList arrayList = new ArrayList();

                    while (i > 0) {
                        int i5 = Math.min(i, 4096);
                        byte[] bArray4 = new byte[i5];
                        i2 = 0;

                        while (i2 < i5) {
                            InputStream inputStream = this.input;
                            int i6;
                            if (inputStream == null) {
                                i6 = -1;
                            } else {
                                i6 = inputStream.read(bArray4, i2, i5 - i2);
                            }

                            if (i6 == -1) {
                                throw InvalidProtocolBufferMicroException.truncatedMessage();
                            }

                            this.totalBytesRetired += i6;
                            i2 += i6;
                        }

                        i -= i5;
                        arrayList.add(bArray4);
                    }

                    byte[] bArray2 = new byte[arg0];
                    System.arraycopy(this.buffer, i1, bArray2, 0, i4);
                    arg0 = 0;

                    for (int var13 = i4; arg0 < arrayList.size(); arg0++) {
                        byte[] bArray3 = (byte[])arrayList.get(arg0);
                        System.arraycopy(bArray3, 0, bArray2, var13, bArray3.length);
                        var13 += bArray3.length;
                    }

                    return bArray2;
                }
            }
        }
    }

    public int readRawLittleEndian32() throws IOException {
        return this.readRawByte() & 0xFF | (this.readRawByte() & 0xFF) << 8 | (this.readRawByte() & 0xFF) << 16 | (this.readRawByte() & 0xFF) << 24;
    }

    public long readRawLittleEndian64() throws IOException {
        int i = this.readRawByte();
        int i1 = this.readRawByte();
        int i2 = this.readRawByte();
        int i3 = this.readRawByte();
        int i4 = this.readRawByte();
        int i5 = this.readRawByte();
        int i6 = this.readRawByte();
        int i7 = this.readRawByte();
        long l = (long)i;
        return ((long)i1 & 255L) << 8
                | l & 255L
                | ((long)i2 & 255L) << 16
                | ((long)i3 & 255L) << 24
                | ((long)i4 & 255L) << 32
                | ((long)i5 & 255L) << 40
                | ((long)i6 & 255L) << 48
                | ((long)i7 & 255L) << 56;
    }

    public int readRawVarint32() throws IOException {
        int i = this.readRawByte();
        if (i >= 0) {
            return i;
        } else {
            i &= 127;
            int i1 = this.readRawByte();
            if (i1 >= 0) {
                i1 <<= 7;
            } else {
                i |= (i1 & 127) << 7;
                int var8 = this.readRawByte();
                if (var8 >= 0) {
                    i1 = var8 << 14;
                } else {
                    i |= (var8 & 127) << 14;
                    var8 = this.readRawByte();
                    if (var8 < 0) {
                        int i2 = this.readRawByte();
                        var8 = i | (var8 & 127) << 21 | i2 << 28;
                        if (i2 < 0) {
                            for (int var6 = 0; var6 < 5; var6++) {
                                if (this.readRawByte() >= 0) {
                                    return var8;
                                }
                            }

                            throw InvalidProtocolBufferMicroException.malformedVarint();
                        }

                        return var8;
                    }

                    i1 = var8 << 21;
                }
            }

            return i | i1;
        }
    }

    public long readRawVarint64() throws IOException {
        int i = 0;

        for (long l = 0L; i < 64; i += 7) {
            int i1 = this.readRawByte();
            l |= (long)(i1 & 127) << i;
            if ((i1 & 128) == 0) {
                return l;
            }
        }

        throw InvalidProtocolBufferMicroException.malformedVarint();
    }

    public int readSFixed32() throws IOException {
        return this.readRawLittleEndian32();
    }

    public long readSFixed64() throws IOException {
        return this.readRawLittleEndian64();
    }

    public int readSInt32() throws IOException {
        return decodeZigZag32(this.readRawVarint32());
    }

    public long readSInt64() throws IOException {
        return decodeZigZag64(this.readRawVarint64());
    }

    public String readString() throws IOException {
        int i = this.readRawVarint32();
        int i1 = this.bufferSize;
        int i2 = this.bufferPos;
        if (i <= i1 - i2 && i > 0) {
            String string = new String(this.buffer, i2, i, "UTF-8");
            this.bufferPos += i;
            return string;
        } else {
            return new String(this.readRawBytes(i), "UTF-8");
        }
    }

    public int readTag() throws IOException {
        if (this.isAtEnd()) {
            this.lastTag = 0;
            return 0;
        } else {
            int i = this.readRawVarint32();
            this.lastTag = i;
            if (i != 0) {
                return i;
            } else {
                throw InvalidProtocolBufferMicroException.invalidTag();
            }
        }
    }

    public int readUInt32() throws IOException {
        return this.readRawVarint32();
    }

    public long readUInt64() throws IOException {
        return this.readRawVarint64();
    }

    public void resetSizeCounter() {
        this.totalBytesRetired = 0;
    }

    public int setRecursionLimit(int arg0) {
        if (arg0 >= 0) {
            int i = this.recursionLimit;
            this.recursionLimit = arg0;
            return i;
        } else {
            StringBuilder stringBuilder = new StringBuilder("Recursion limit cannot be negative: ");
            stringBuilder.append(arg0);
            throw new IllegalArgumentException(stringBuilder.toString());
        }
    }

    public int setSizeLimit(int arg0) {
        if (arg0 >= 0) {
            int i = this.sizeLimit;
            this.sizeLimit = arg0;
            return i;
        } else {
            StringBuilder stringBuilder = new StringBuilder("Size limit cannot be negative: ");
            stringBuilder.append(arg0);
            throw new IllegalArgumentException(stringBuilder.toString());
        }
    }

    public boolean skipField(int arg0) throws IOException {
        int i = WireFormatMicro.getTagWireType(arg0);
        if (i != 0) {
            if (i != 1) {
                if (i != 2) {
                    if (i != 3) {
                        if (i != 4) {
                            if (i == 5) {
                                this.readRawLittleEndian32();
                                return true;
                            } else {
                                throw InvalidProtocolBufferMicroException.invalidWireType();
                            }
                        } else {
                            return false;
                        }
                    } else {
                        this.skipMessage();
                        this.checkLastTagWas(WireFormatMicro.makeTag(WireFormatMicro.getTagFieldNumber(arg0), 4));
                        return true;
                    }
                } else {
                    this.skipRawBytes(this.readRawVarint32());
                    return true;
                }
            } else {
                this.readRawLittleEndian64();
                return true;
            }
        } else {
            this.readInt32();
            return true;
        }
    }

    public void skipMessage() throws IOException {
        int i;
        do {
            i = this.readTag();
        } while (i != 0 && this.skipField(i));
    }

    public void skipRawBytes(int arg0) throws IOException {
        if (arg0 >= 0) {
            int i = this.totalBytesRetired;
            int i1 = this.bufferPos;
            int i2 = this.currentLimit;
            if (i + i1 + arg0 > i2) {
                this.skipRawBytes(i2 - i - i1);
                throw InvalidProtocolBufferMicroException.truncatedMessage();
            } else {
                i2 = this.bufferSize;
                if (arg0 <= i2 - i1) {
                    this.bufferPos = i1 + arg0;
                } else {
                    i1 = i2 - i1;
                    this.totalBytesRetired = i + i1;
                    this.bufferPos = 0;
                    this.bufferSize = 0;

                    while (i1 < arg0) {
                        InputStream inputStream = this.input;
                        if (inputStream == null) {
                            i = -1;
                        } else {
                            i = (int)inputStream.skip((long)(arg0 - i1));
                        }

                        if (i <= 0) {
                            throw InvalidProtocolBufferMicroException.truncatedMessage();
                        }

                        i1 += i;
                        this.totalBytesRetired += i;
                    }
                }
            }
        } else {
            throw InvalidProtocolBufferMicroException.negativeSize();
        }
    }
}
