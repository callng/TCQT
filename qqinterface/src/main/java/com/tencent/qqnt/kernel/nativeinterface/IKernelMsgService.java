package com.tencent.qqnt.kernel.nativeinterface;

public interface IKernelMsgService {
/*    void deleteMsg(Contact contact, ArrayList<Long> msgIdList, IOperateCallback callback);

    void fetchLongMsg(Contact contact, long msgId);

    void fetchLongMsgWithCb(Contact contact, long msgId, IOperateCallback back);

    void getRecallMsgsByMsgId(Contact contact, ArrayList<Long> msgIdList, IMsgOperateCallback callback);

    void recallMsg(Contact contact, ArrayList<Long> msgIdList, IOperateCallback callback);*/

    void addLocalJsonGrayTipMsg(Contact contact, JsonGrayElement jsonGrayElement, boolean z, boolean z2, IAddJsonGrayTipMsgCallback iAddJsonGrayTipMsgCallback);

    void addLocalJsonGrayTipMsg(com.tencent.qqnt.kernelpublic.nativeinterface.Contact contact, com.tencent.qqnt.kernelpublic.nativeinterface.JsonGrayElement jsonGrayElement, boolean z, boolean z2, IAddJsonGrayTipMsgCallback iAddJsonGrayTipMsgCallback);

/*    void addLocalRecordMsg(Contact contact, long msgId, MsgElement elem, HashMap<Integer, MsgAttributeInfo> hashMap, boolean z, IOperateCallback callback);

    long getMsgUniqueId(long time);

    void addSendMsg(long msgId, Contact contact, ArrayList<MsgElement> msgList, HashMap<Integer, MsgAttributeInfo> hashMap);

    void getMsgs(@NotNull Contact contact, long startMsgId, int cnt, boolean queryOrder, @NotNull IMsgOperateCallback iMsgOperateCallback);

    void getMsgsIncludeSelf(Contact contact, long startMsgId, int count, boolean queryOrder, IMsgOperateCallback iMsgOperateCallback);

    void translatePtt2Text(long j2, Contact contact, MsgElement msgElement, IOperateCallback iOperateCallback);

    void getMultiMsg(Contact contact, long rootMsgId, long parentMsgId, IGetMultiMsgCallback cb);

    void multiForwardMsg(ArrayList<MultiMsgInfo> arrayList, Contact from, Contact to, IOperateCallback cb);

    void setAllC2CAndGroupMsgRead(IOperateCallback cb);

    void clearMsgRecords(Contact contact, IClearMsgRecordsCallback cb);

    String createUidFromTinyId(long j2, long j3);

    void switchBackGround(BackGroundInfo backGroundInfo, IOperateCallback cb);

    void switchBackGroundForMqq(byte[] bArr, IOperateCallback cb);

    void switchForeGround(IOperateCallback cb);

    void switchForeGroundForMqq(byte[] bArr, IOperateCallback cb);*/
}
