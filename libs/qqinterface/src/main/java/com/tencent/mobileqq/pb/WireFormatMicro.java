package com.tencent.mobileqq.pb;

public final class WireFormatMicro {
    static final int MESSAGE_SET_ITEM = 1;
    static final int MESSAGE_SET_MESSAGE = 3;
    static final int MESSAGE_SET_TYPE_ID = 2;
    static final int TAG_TYPE_BITS = 3;
    static final int TAG_TYPE_MASK = 7;
    static final int WIRETYPE_FIXED32 = 5;
    static final int WIRETYPE_FIXED64 = 1;
    static final int WIRETYPE_LENGTH_DELIMITED = 2;
    static final int WIRETYPE_START_GROUP = 3;
    static final int WIRETYPE_VARINT = 0;
    static final int MESSAGE_SET_ITEM_TAG = makeTag(1, 3);
    static final int WIRETYPE_END_GROUP = 4;
    static final int MESSAGE_SET_ITEM_END_TAG = makeTag(1, WIRETYPE_END_GROUP);
    static final int MESSAGE_SET_TYPE_ID_TAG = makeTag(2, 0);
    static final int MESSAGE_SET_MESSAGE_TAG = makeTag(3, 2);

    private WireFormatMicro() {
    }

    public static int getTagFieldNumber(int i) {
        return i >>> 3;
    }

    static int getTagWireType(int i) {
        return i & TAG_TYPE_MASK;
    }

    static int makeTag(int i, int i2) {
        return (i << 3) | i2;
    }
}
