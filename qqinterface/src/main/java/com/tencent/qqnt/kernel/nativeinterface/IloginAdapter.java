package com.tencent.qqnt.kernel.nativeinterface;

public interface IloginAdapter {
    void addWTLoginRecordFromNT(WTLoginRecord wTLoginRecord);

    void deleteWTLoginRecord(long j, int i);

    void getWTLoginRecord(long j, int i, IGetLoginRecordFromWTCallback iGetLoginRecordFromWTCallback);

    void onLoginTicketRefresh(WTLoginRecord wTLoginRecord);
}
