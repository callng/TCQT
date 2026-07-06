package com.tencent.qqnt.kernel.nativeinterface;

import java.util.ArrayList;

public interface IQQNTWrapperSession {

    final class CppProxy implements IQQNTWrapperSession {

        @Override
        public void close(String str) {
        }

        @Override
        public void disableIpDirect(boolean z) {
        }

        @Override
        public IKernelAVSDKService getAVSDKService() {
            return null;
        }

        @Override
        public String getAccountPath(PathType pathType) {
            return "";
        }

        @Override
        public IKernelAddBuddyService getAddBuddyService() {
            return null;
        }

        @Override
        public IKernelAlbumService getAlbumService() {
            return null;
        }

        @Override
        public ArrayList<String> getCacheErrLog() {
            return null;
        }

        @Override
        public IKernelGroupService getGroupService() {
            return null;
        }

        @Override
        public IKernelMsgService getMsgService() {
            return null;
        }

        @Override
        public IKernelRichMediaService getRichMediaService() {
            return null;
        }

        @Override
        public String getSessionId() {
            return "";
        }

        @Override
        public ArrayList<String> getShortLinkBlacklist() {
            return null;
        }

        @Override
        public IKernelTicketService getTicketService() {
            return null;
        }

        @Override
        public IKernelUixConvertService getUixConvertService() {
            return null;
        }

        @Override
        public void onMsfPush(String str, byte[] bArr, PushExtraInfo pushExtraInfo) {
        }

        @Override
        public void onSendOidbReply(long j, int i, int i2, String str, MsfRspInfo msfRspInfo) {
        }

        @Override
        public void onSendSSOReply(long requestId, String cmd, int result, String errorMsg, MsfRspInfo msfRspInfo) {
        }
    }

    void close(String str);

    void disableIpDirect(boolean z);

    IKernelAVSDKService getAVSDKService();

    String getAccountPath(PathType pathType);

    IKernelAddBuddyService getAddBuddyService();

    IKernelAlbumService getAlbumService();

    ArrayList<String> getCacheErrLog();

    IKernelGroupService getGroupService();

    IKernelMsgService getMsgService();

    IKernelRichMediaService getRichMediaService();

    String getSessionId();

    ArrayList<String> getShortLinkBlacklist();

    IKernelTicketService getTicketService();

    IKernelUixConvertService getUixConvertService();

    void onMsfPush(String str, byte[] bArr, PushExtraInfo pushExtraInfo);

    void onSendOidbReply(long requestId, int cmd, int result, String errorMsg, MsfRspInfo msfRspInfo);

    void onSendSSOReply(long requestId, String cmd, int result, String errorMsg, MsfRspInfo msfRspInfo);
}
