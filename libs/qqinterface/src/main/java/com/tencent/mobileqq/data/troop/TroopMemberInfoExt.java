package com.tencent.mobileqq.data.troop;

import org.jetbrains.annotations.Nullable;
import kotlin.jvm.JvmField;

public final class TroopMemberInfoExt {
    @JvmField
    public int commonFrdCnt;

    @JvmField
    public long flagEx3;

    @JvmField
    public int hwIdentity;

    @JvmField
    public long lastMsgUpdateHonorRichTime;

    @JvmField
    @Nullable
    public String memberUin;

    @JvmField
    public byte @Nullable [] nickIconRepeatMsgBuffer;

    @JvmField
    @Nullable
    public String recommendRemark;

    @JvmField
    @Nullable
    public String showNameForPinyin;

    @JvmField
    @Nullable
    public String showNamePinyinAll;

    @JvmField
    @Nullable
    public String showNamePinyinFirst;

    @JvmField
    @Nullable
    public String troopUin;

    public TroopMemberInfoExt() {
        this.commonFrdCnt = Integer.MIN_VALUE;
    }
}
