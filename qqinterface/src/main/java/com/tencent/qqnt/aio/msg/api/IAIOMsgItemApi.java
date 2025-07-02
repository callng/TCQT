package com.tencent.qqnt.aio.msg.api;

import com.tencent.mobileqq.qroute.QRouteApi;
//import com.tencent.qqnt.kernelpublic.nativeinterface.MsgElement;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public interface IAIOMsgItemApi extends QRouteApi {
    //@NotNull
    //CharSequence buildContent(@NotNull List<MsgElement> list);

    @NotNull
    byte[] generateFlashPicExtBuf(boolean z);

    //@NotNull
    //byte[] generateVideoExtBuf(@NotNull aa aaVar);

    //@NotNull
   // ArrayList<MsgElement> getForwardCommentElement(@NotNull String str);

    //int getLocalIdOfAniStickerMsg(@NotNull AIOMsgItem aIOMsgItem);

    //@NotNull
   // String getLocalPath(@NotNull AIOMsgItem aIOMsgItem);

    //@Nullable
    //CharSequence getPreviewText(@NotNull Context context, @NotNull AIOMsgItem aIOMsgItem);

    //@NotNull
    //String getResultIdOfAniStickerMsg(@NotNull AIOMsgItem aIOMsgItem);

    //@Nullable
    //CharSequence getTextFromMsgItem(@NotNull AIOMsgItem aIOMsgItem);

    //@Nullable
    //HashMap<Integer, CharSequence> getTextMapFromMsgItem(@NotNull AIOMsgItem aIOMsgItem);

    boolean isNtArkAppContainer(@NotNull Object obj);

    //@Nullable
    ///b parseArkModel(@NotNull AIOMsgItem aIOMsgItem);

    //@NotNull
    //AIOMsgItem transformMsgRecordWithType(@NotNull MsgRecord msgRecord);
}
