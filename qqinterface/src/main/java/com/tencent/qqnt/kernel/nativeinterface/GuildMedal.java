package com.tencent.qqnt.kernel.nativeinterface;

public final class GuildMedal {
    public long endTime;
    public long startTime;
    public String url = "";
    public String desc = "";

    public String getDesc() {
        return this.desc;
    }

    public long getEndTime() {
        return this.endTime;
    }

    public long getStartTime() {
        return this.startTime;
    }

    public String getUrl() {
        return this.url;
    }

}
