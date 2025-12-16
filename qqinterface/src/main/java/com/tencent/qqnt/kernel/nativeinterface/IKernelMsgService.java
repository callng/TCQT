package com.tencent.qqnt.kernel.nativeinterface;

import java.util.ArrayList;
import java.util.HashMap;

public interface IKernelMsgService {

    final class CppProxy implements IKernelMsgService {

        @Override
        public void addLocalJsonGrayTipMsg(Contact contact, JsonGrayElement jsonGrayElement, boolean z, boolean z2, IAddJsonGrayTipMsgCallback iAddJsonGrayTipMsgCallback) {

        }

        @Override
        public void addLocalJsonGrayTipMsg(com.tencent.qqnt.kernelpublic.nativeinterface.Contact contact, com.tencent.qqnt.kernelpublic.nativeinterface.JsonGrayElement jsonGrayElement, boolean z, boolean z2, IAddJsonGrayTipMsgCallback iAddJsonGrayTipMsgCallback) {

        }

        @Override
        public void sendMsg(long j, com.tencent.qqnt.kernelpublic.nativeinterface.Contact contact, ArrayList<MsgElement> arrayList, HashMap<Integer, MsgAttributeInfo> hashMap, IOperateCallback iOperateCallback) {

        }
    }

    /**
     * 已被删除的旧版本接口,新版本不要使用
     */
    void addLocalJsonGrayTipMsg(Contact contact, JsonGrayElement jsonGrayElement, boolean z, boolean z2, IAddJsonGrayTipMsgCallback iAddJsonGrayTipMsgCallback);

    void addLocalJsonGrayTipMsg(com.tencent.qqnt.kernelpublic.nativeinterface.Contact contact, com.tencent.qqnt.kernelpublic.nativeinterface.JsonGrayElement jsonGrayElement, boolean z, boolean z2, IAddJsonGrayTipMsgCallback iAddJsonGrayTipMsgCallback);

    void sendMsg(long j, com.tencent.qqnt.kernelpublic.nativeinterface.Contact contact, ArrayList<MsgElement> arrayList, HashMap<Integer, MsgAttributeInfo> hashMap, IOperateCallback iOperateCallback);
}
