package com.tencent.qqnt.kernel.nativeinterface;

import com.tencent.qqnt.kernelpublic.nativeinterface.MsgAbstractElement;
import java.util.ArrayList;

public final class GuildAbstractPiece {
    public ArrayList<MsgAbstractElement> abstractContent = new ArrayList<>();
    public int canCutOff;

    public ArrayList<MsgAbstractElement> getAbstractContent() {
        return this.abstractContent;
    }

    public int getCanCutOff() {
        return this.canCutOff;
    }
}
