package com.tencent.mobileqq.data.troop;

import java.io.Serializable;

public class TroopClipPic implements Serializable {
    public String clipInfo;
    public String id;
    public long ts;
    public int type;

    public TroopClipPic() {
        this.type = 0;
    }
}
