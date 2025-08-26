package com.tencent.qqnt.msg.api;

import com.tencent.mobileqq.qroute.QRouteApi;
import com.tencent.qqnt.kernel.nativeinterface.IOperateCallback;
import com.tencent.qqnt.kernel.nativeinterface.MsgElement;
import com.tencent.qqnt.kernelpublic.nativeinterface.Contact;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public interface IMsgService extends QRouteApi {
    void sendMsgWithMsgId(@NotNull Contact contact, long j, @NotNull ArrayList<MsgElement> arrayList, @Nullable IOperateCallback iOperateCallback);
}
