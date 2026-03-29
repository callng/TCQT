package com.tencent.mobileqq.aio.event;

import com.tencent.mvi.base.route.MsgIntent;

public class AIOMsgSendEvent implements MsgIntent {

    public final class MsgOnClickReplyEvent extends AIOMsgSendEvent {
    }

    @Override
    public boolean isSticky() {
        throw new RuntimeException("Stub!");
    }
}
