package com.tencent.mobileqq.pb;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

public final class ByteStringMicro {
    public static final ByteStringMicro EMPTY = new ByteStringMicro(new byte[0]);
    private final byte[] bytes;
    private volatile int hash = 0;

    private ByteStringMicro(byte[] bArr) {
        this.bytes = bArr;
    }

    public static ByteStringMicro copyFrom(String str, String str2) throws UnsupportedEncodingException {
        return new ByteStringMicro(str.getBytes(str2));
    }

    public static ByteStringMicro copyFromUtf8(String str) {
        return new ByteStringMicro(str.getBytes(StandardCharsets.UTF_8));
    }

    public byte byteAt(int i) {
        return this.bytes[i];
    }

    public void copyTo(byte[] bArr, int i) {
        byte[] bArr2 = this.bytes;
        System.arraycopy(bArr2, 0, bArr, i, bArr2.length);
    }

    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof ByteStringMicro)) {
            return false;
        }
        byte[] bArr = this.bytes;
        int length = bArr.length;
        byte[] bArr2 = ((ByteStringMicro) obj).bytes;
        if (length != bArr2.length) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            if (bArr[i] != bArr2[i]) {
                return false;
            }
        }
        return true;
    }

    public int hashCode() {
        int i = this.hash;
        if (i == 0) {
            byte[] bArr = this.bytes;
            int length = bArr.length;
            for (byte b : bArr) {
                length = (length * 31) + b;
            }
            i = length == 0 ? 1 : length;
            this.hash = i;
        }
        return i;
    }

    public boolean isEmpty() {
        return this.bytes.length == 0;
    }

    public int size() {
        return this.bytes.length;
    }

    public byte[] toByteArray() {
        byte[] bArr = this.bytes;
        int length = bArr.length;
        byte[] bArr2 = new byte[length];
        System.arraycopy(bArr, 0, bArr2, 0, length);
        return bArr2;
    }

    public String toString(String str) throws UnsupportedEncodingException {
        return new String(this.bytes, str);
    }

    public String toStringUtf8() {
        return new String(this.bytes, StandardCharsets.UTF_8);
    }

    public static ByteStringMicro copyFrom(byte[] bArr) {
        return copyFrom(bArr, 0, bArr.length);
    }

    public void copyTo(byte[] bArr, int i, int i2, int i3) {
        System.arraycopy(this.bytes, i, bArr, i2, i3);
    }

    public static ByteStringMicro copyFrom(byte[] bArr, int i, int i2) {
        byte[] bArr2 = new byte[i2];
        System.arraycopy(bArr, i, bArr2, 0, i2);
        return new ByteStringMicro(bArr2);
    }
}
