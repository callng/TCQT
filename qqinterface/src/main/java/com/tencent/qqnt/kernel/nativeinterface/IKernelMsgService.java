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
        public void sendMsg(long j, Contact contact, ArrayList<MsgElement> arrayList, HashMap<Integer, MsgAttributeInfo> hashMap, IOperateCallback iOperateCallback) {

        }

        @Override
        public void sendMsg(long j, com.tencent.qqnt.kernelpublic.nativeinterface.Contact contact, ArrayList<MsgElement> arrayList, HashMap<Integer, MsgAttributeInfo> hashMap, IOperateCallback iOperateCallback) {

        }

        @Override
        public long getMsgUniqueId(long serverTimeMillis) {
            return 0;
        }

        @Override
        public long generateMsgUniqueId(int chatType, long serverTimeMillis) {
            return 0;
        }

        @Override
        public void getMsgsByMsgId(Contact contact, ArrayList<Long> msgIds, IMsgOperateCallback iMsgOperateCallback) {

        }

        @Override
        public void getMsgsByMsgId(com.tencent.qqnt.kernelpublic.nativeinterface.Contact contact, ArrayList<Long> msgIds, IMsgOperateCallback iMsgOperateCallback) {

        }

        @Override
        public void forwardMsg(ArrayList<Long> msgIds, Contact contact, ArrayList<Contact> arrayList2, HashMap<Integer, MsgAttributeInfo> hashMap, IForwardOperateCallback iForwardOperateCallback) {

        }

        @Override
        public void forwardMsg(ArrayList<Long> msgIds, com.tencent.qqnt.kernelpublic.nativeinterface.Contact contact, ArrayList<com.tencent.qqnt.kernelpublic.nativeinterface.Contact> arrayList2, HashMap<Integer, MsgAttributeInfo> hashMap, IForwardOperateCallback iForwardOperateCallback) {

        }

        @Override
        public void recallMsg(Contact contact, ArrayList<Long> arrayList, IOperateCallback iOperateCallback) {

        }

        @Override
        public void recallMsg(com.tencent.qqnt.kernelpublic.nativeinterface.Contact contact, ArrayList<Long> arrayList, IOperateCallback iOperateCallback) {

        }

        @Override
        public void getSingleMsg(Contact contact, long j, IMsgOperateCallback iMsgOperateCallback) {

        }

        @Override
        public void getSingleMsg(com.tencent.qqnt.kernelpublic.nativeinterface.Contact contact, long j, IMsgOperateCallback iMsgOperateCallback) {

        }
    }

    /**
     * 已被删除的旧版本接口,新版本不要使用
     */
    void addLocalJsonGrayTipMsg(Contact contact, JsonGrayElement jsonGrayElement, boolean z, boolean z2, IAddJsonGrayTipMsgCallback iAddJsonGrayTipMsgCallback);

    void addLocalJsonGrayTipMsg(com.tencent.qqnt.kernelpublic.nativeinterface.Contact contact, com.tencent.qqnt.kernelpublic.nativeinterface.JsonGrayElement jsonGrayElement, boolean z, boolean z2, IAddJsonGrayTipMsgCallback iAddJsonGrayTipMsgCallback);

    /**
     * 已被删除的旧版本接口,新版本不要使用
     */
    void sendMsg(long j, Contact contact, ArrayList<MsgElement> arrayList, HashMap<Integer, MsgAttributeInfo> hashMap, IOperateCallback iOperateCallback);

    void sendMsg(long j, com.tencent.qqnt.kernelpublic.nativeinterface.Contact contact, ArrayList<MsgElement> arrayList, HashMap<Integer, MsgAttributeInfo> hashMap, IOperateCallback iOperateCallback);

    /**
     * 已被删除的旧版本接口,新版本不要使用
     */
    long getMsgUniqueId(long serverTimeMillis);

    long generateMsgUniqueId(int chatType, long serverTimeMillis);

    /**
     * 已被删除的旧版本接口,新版本不要使用
     */
    void getMsgsByMsgId(Contact contact, ArrayList<Long> msgIds, IMsgOperateCallback iMsgOperateCallback);

    void getMsgsByMsgId(com.tencent.qqnt.kernelpublic.nativeinterface.Contact contact, ArrayList<Long> msgIds, IMsgOperateCallback iMsgOperateCallback);

    /**
     * 已被删除的旧版本接口,新版本不要使用
     */
    void forwardMsg(ArrayList<Long> msgIds, Contact contact, ArrayList<Contact> arrayList2, HashMap<Integer, MsgAttributeInfo> hashMap, IForwardOperateCallback iForwardOperateCallback);

    void forwardMsg(ArrayList<Long> msgIds, com.tencent.qqnt.kernelpublic.nativeinterface.Contact contact, ArrayList<com.tencent.qqnt.kernelpublic.nativeinterface.Contact> arrayList2, HashMap<Integer, MsgAttributeInfo> hashMap, IForwardOperateCallback iForwardOperateCallback);

    /**
     * 已被删除的旧版本接口,新版本不要使用
     */
    void recallMsg(Contact contact, ArrayList<Long> arrayList, IOperateCallback iOperateCallback);

    void recallMsg(com.tencent.qqnt.kernelpublic.nativeinterface.Contact contact, ArrayList<Long> arrayList, IOperateCallback iOperateCallback);

    void getSingleMsg(Contact contact, long j, IMsgOperateCallback iMsgOperateCallback);

    void getSingleMsg(com.tencent.qqnt.kernelpublic.nativeinterface.Contact contact, long j, IMsgOperateCallback iMsgOperateCallback);
}
