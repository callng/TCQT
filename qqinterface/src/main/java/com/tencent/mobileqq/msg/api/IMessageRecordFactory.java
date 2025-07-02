package com.tencent.mobileqq.msg.api;

import androidx.annotation.Nullable;

import com.tencent.common.app.AppInterface;
import com.tencent.common.app.business.BaseQQAppInterface;
import com.tencent.mobileqq.data.MessageRecord;
import com.tencent.mobileqq.qroute.QRouteApi;

import mqq.app.AppRuntime;

public interface IMessageRecordFactory extends QRouteApi {
    @Nullable
    MessageRecord createMessageForStructing(BaseQQAppInterface baseQQAppInterface, String str, int i2, String str2, String str3);

    MessageRecord createMsgRecordByMsgType(int msgtype);

    MessageRecord createMsgRecordFromDB(int i2, byte[] bArr, int i3, String str, int i4);

    MessageRecord createResendMsg(MessageRecord messageRecord);

    MessageRecord createSendMSg_BlessPTV(BaseQQAppInterface baseQQAppInterface, String str, String str2, int i2);

    MessageRecord createSendMSg_Pic(AppInterface appInterface, String str, String str2, int i2);

    MessageRecord createSendMSg_ShortVideo(BaseQQAppInterface baseQQAppInterface, String str, String str2, int i2);

    MessageRecord createSendMSg_VideoEmoticon(BaseQQAppInterface baseQQAppInterface, String str, String str2, int i2);

    void setSendingMsgRecordBaseInfo(AppRuntime appRuntime, MessageRecord messageRecord, String str, String str2, int i2);
}
