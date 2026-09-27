package com.tencent.qqnt.msg.api;

import com.tencent.mobileqq.qroute.QRouteApi;
import com.tencent.qqnt.kernel.nativeinterface.MsgElement;
import com.tencent.qqnt.kernel.nativeinterface.TextElement;

import java.util.ArrayList;

/**
 * 宿主的消息元素工厂（只有脚本用得到的子集）。
 *
 * 完整接口在宿主里有 30 多个方法，这里只声明脚本会调用的那几个：每一个的签名都来自
 * 真机 dex（QQ 9.3.70 classes5.dex）核对，**不要凭猜添加**。
 *
 * 通过 `QRoute.api(IMsgUtilApi.class)` 取实例。
 */
public interface IMsgUtilApi extends QRouteApi {

    /** 纯文本元素。 */
    MsgElement createTextElement(String text);

    /** 用已有 TextElement 构造元素（保留 atType 等字段）。 */
    MsgElement createTextElement(TextElement textElement);

    /**
     * 艾特元素。
     *
     * @param text   展示文本，如 `@全体成员`
     * @param uid    被艾特者 UID；`"0"` 表示全体
     * @param atType 1 = 全体，2 = 单人
     */
    MsgElement createAtTextElement(String text, String uid, int atType);

    /**
     * 图片元素。
     *
     * @param path   本地图片路径
     * @param origin 是否原图
     * @param type   0 = 普通图片
     */
    MsgElement createPicElement(String path, boolean origin, int type);

    /** 视频元素。 */
    MsgElement createVideoElement(String path);

    /** 文件元素。 */
    MsgElement createFileElement(String path);

    /**
     * 语音元素。
     *
     * @param path      本地语音路径
     * @param durationMs 时长（毫秒，宿主会换算成秒）
     */
    MsgElement createPttElement(String path, int durationMs);

    /**
     * 语音元素（带波形）。
     *
     * @param waveAmplitudes 波形振幅；为空时部分版本语音条不显示
     */
    MsgElement createPttElement(String path, int durationMs, ArrayList<Byte> waveAmplitudes);

    /** 引用回复元素。 */
    MsgElement createReplyElement(long replyMsgId);
}
