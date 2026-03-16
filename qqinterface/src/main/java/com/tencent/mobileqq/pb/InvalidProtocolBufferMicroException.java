package com.tencent.mobileqq.pb;

import java.io.IOException;

public class InvalidProtocolBufferMicroException extends IOException {

    public InvalidProtocolBufferMicroException(String str) {
        super(str);
    }

    static InvalidProtocolBufferMicroException invalidEndTag() {
        return new InvalidProtocolBufferMicroException("Protocol message end-group tag did not match expected tag.");
    }

    static InvalidProtocolBufferMicroException invalidTag() {
        return new InvalidProtocolBufferMicroException("Protocol message contained an invalid tag (zero).");
    }

    static InvalidProtocolBufferMicroException invalidWireType() {
        return new InvalidProtocolBufferMicroException("Protocol message tag had invalid wire type.");
    }

    static InvalidProtocolBufferMicroException malformedVarint() {
        return new InvalidProtocolBufferMicroException("CodedInputStream encountered a malformed varint.");
    }

    static InvalidProtocolBufferMicroException negativeSize() {
        return new InvalidProtocolBufferMicroException("CodedInputStream encountered an embedded string or message which claimed to have negative size.");
    }

    static InvalidProtocolBufferMicroException recursionLimitExceeded() {
        return new InvalidProtocolBufferMicroException("Protocol message had too many levels of nesting.  May be malicious.  Use CodedInputStream.setRecursionLimit() to increase the depth limit.");
    }

    static InvalidProtocolBufferMicroException sizeLimitExceeded() {
        return new InvalidProtocolBufferMicroException("Protocol message was too large.  May be malicious.  Use CodedInputStream.setSizeLimit() to increase the size limit.");
    }

    static InvalidProtocolBufferMicroException truncatedMessage() {
        return new InvalidProtocolBufferMicroException("While parsing a protocol message, the input ended unexpectedly in the middle of a field.  This could mean either than the input has been truncated or that an embedded message misreported its own length.");
    }
}
