package com.tencent.qqnt.kernel.nativeinterface;

public final class GroupGame {
    public int gameId;
    public GroupGameInfo gameInfo = new GroupGameInfo();
    public int notAllowSelected;

    public int getGameId() {
        return this.gameId;
    }

    public GroupGameInfo getGameInfo() {
        return this.gameInfo;
    }

    public int getNotAllowSelected() {
        return this.notAllowSelected;
    }
}
