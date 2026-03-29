package com.tencent.qqnt.ntrelation.friendsinfo.bean;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class d {
    public static final int GATHER_TYPE_GATHERED = 1;
    public static final int GATHER_TYPE_NORMAL = 0;
    public static final int GATHER_TYPE_RECOMMENDED = 2;
    public static final int MULTI_FLAGS_MASK_SHIELD = 1;

    @NotNull
    public static final String TAG = "NTFriendSimpleInfo";
    private int age;

    @Nullable
    private String alias;

    @Nullable
    private Byte cSpecialFlag;
    private int categoryId;
    private int comparePartInt;

    @Nullable
    private String compareSpell;
    private long datetime;
    private int friendType;

    @Nullable
    private Byte gathtertype;

    @Nullable
    private Boolean isAdded2C2C;

    @Nullable
    private Boolean isBlock;

    @Nullable
    private Boolean isBlocked;

    @Nullable
    private Boolean isMsgDisturb;

    @Nullable
    private Boolean isSpecialCareOpen;

    @Nullable
    private Boolean isSpecialCareZone;
    private int multiFlags;

    @Nullable
    private String nick;

    @Nullable
    private String recommendReason;

    @Nullable
    private String remark;

    @Nullable
    private byte[] richBuffer;
    private long richTime;

    @Nullable
    private String ringId;
    private int sex;
    private int status;

    @NotNull
    private String uid;

    @NotNull
    private String uin;

    public int getAge() {
        throw new RuntimeException("Stub!");
    }

    @Nullable
    public String getAlias() {
        throw new RuntimeException("Stub!");
    }

    @Nullable
    public Byte getCSpecialFlag() {
        throw new RuntimeException("Stub!");
    }

    public int getCategoryId() {
        throw new RuntimeException("Stub!");
    }

    public int getComparePartInt() {
        throw new RuntimeException("Stub!");
    }

    @Nullable
    public String getCompareSpell() {
        throw new RuntimeException("Stub!");
    }

    public long getDatetime() {
        throw new RuntimeException("Stub!");
    }

    @Nullable
    public String getFriendName() {
        throw new RuntimeException("Stub!");
    }

    @Nullable
    public final String getFriendNick() {
        throw new RuntimeException("Stub!");
    }

    @Nullable
    public String getFriendNickWithAlias() {
        throw new RuntimeException("Stub!");
    }

    public int getFriendType() {
        throw new RuntimeException("Stub!");
    }

    @Nullable
    public Byte getGathtertype() {
        throw new RuntimeException("Stub!");
    }

    public int getMultiFlags() {
        throw new RuntimeException("Stub!");
    }

    @Nullable
    public String getNick() {
        throw new RuntimeException("Stub!");
    }

    @Nullable
    public String getRecommendReason() {
        throw new RuntimeException("Stub!");
    }

    @Nullable
    public String getRemark() {
        throw new RuntimeException("Stub!");
    }

    @Nullable
    public byte[] getRichBuffer() {
        throw new RuntimeException("Stub!");
    }

    public long getRichTime() {
        throw new RuntimeException("Stub!");
    }

    @Nullable
    public String getRingId() {
        throw new RuntimeException("Stub!");
    }

    public int getSex() {
        throw new RuntimeException("Stub!");
    }

    public int getStatus() {
        throw new RuntimeException("Stub!");
    }

    @NotNull
    public String getUid() {
        throw new RuntimeException("Stub!");
    }

    @NotNull
    public String getUin() {
        throw new RuntimeException("Stub!");
    }

    @Nullable
    public Boolean isAdded2C2C() {
        throw new RuntimeException("Stub!");
    }

    @Nullable
    public Boolean isBlock() {
        throw new RuntimeException("Stub!");
    }

    @Nullable
    public Boolean isBlocked() {
        throw new RuntimeException("Stub!");
    }

    public final boolean isFriend() {
        throw new RuntimeException("Stub!");
    }

    @Nullable
    public Boolean isMsgDisturb() {
        throw new RuntimeException("Stub!");
    }

    public final boolean isShield() {
        throw new RuntimeException("Stub!");
    }

    @Nullable
    public Boolean isSpecialCareOpen() {
        throw new RuntimeException("Stub!");
    }

    @Nullable
    public Boolean isSpecialCareZone() {
        throw new RuntimeException("Stub!");
    }

    public void resetAccurateUpdateData() {
        setNick(null);
        setRemark(null);
        setStatus(Integer.MIN_VALUE);
        setCSpecialFlag(null);
        setCategoryId(Integer.MIN_VALUE);
        setDatetime(Long.MIN_VALUE);
        setAlias(null);
        setGathtertype(null);
        setAge(Integer.MIN_VALUE);
        setSex(Integer.MIN_VALUE);
        setRecommendReason(null);
        setComparePartInt(Integer.MIN_VALUE);
        setCompareSpell(null);
        setMultiFlags(Integer.MIN_VALUE);
        setBlock(null);
        setBlocked(null);
        setRichTime(Long.MIN_VALUE);
        setRichBuffer(null);
        setAdded2C2C(null);
        setRingId(null);
        setMsgDisturb(null);
        setSpecialCareZone(null);
        setSpecialCareOpen(null);
    }

    public void setAdded2C2C(@Nullable Boolean bool) {
        throw new RuntimeException("Stub!");
    }

    public void setAge(int i) {
        throw new RuntimeException("Stub!");
    }

    public void setAlias(@Nullable String str) {
        throw new RuntimeException("Stub!");
    }

    public void setBlock(@Nullable Boolean bool) {
        throw new RuntimeException("Stub!");
    }

    public void setBlocked(@Nullable Boolean bool) {
        throw new RuntimeException("Stub!");
    }

    public void setCSpecialFlag(@Nullable Byte b) {
        throw new RuntimeException("Stub!");
    }

    public void setCategoryId(int i) {
        throw new RuntimeException("Stub!");
    }

    public void setComparePartInt(int i) {
        throw new RuntimeException("Stub!");
    }

    public void setCompareSpell(@Nullable String str) {
        throw new RuntimeException("Stub!");
    }

    public void setDatetime(long j) {
        throw new RuntimeException("Stub!");
    }

    public void setFriendType(int i) {
        throw new RuntimeException("Stub!");
    }

    public void setGathtertype(@Nullable Byte b) {
        throw new RuntimeException("Stub!");
    }

    public void setMsgDisturb(@Nullable Boolean bool) {
        throw new RuntimeException("Stub!");
    }

    public void setMultiFlags(int i) {
        throw new RuntimeException("Stub!");
    }

    public void setNick(@Nullable String str) {
        throw new RuntimeException("Stub!");
    }

    public void setRecommendReason(@Nullable String str) {
        throw new RuntimeException("Stub!");
    }

    public void setRemark(@Nullable String str) {
        throw new RuntimeException("Stub!");
    }

    public void setRichBuffer(@Nullable byte[] bArr) {
        throw new RuntimeException("Stub!");
    }

    public void setRichTime(long j) {
        throw new RuntimeException("Stub!");
    }

    public void setRingId(@Nullable String str) {
        throw new RuntimeException("Stub!");
    }

    public void setSex(int i) {
        throw new RuntimeException("Stub!");
    }

    public final void setShieldFlag(boolean z) {
        throw new RuntimeException("Stub!");
    }

    public void setSpecialCareOpen(@Nullable Boolean bool) {
        throw new RuntimeException("Stub!");
    }

    public void setSpecialCareZone(@Nullable Boolean bool) {
        throw new RuntimeException("Stub!");
    }

    public void setStatus(int i) {
        throw new RuntimeException("Stub!");
    }

    public void setUid(@NotNull String str) {
        throw new RuntimeException("Stub!");
    }

    public void setUin(@NotNull String str) {
        throw new RuntimeException("Stub!");
    }
}
