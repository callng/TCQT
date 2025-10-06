package com.tencent.qqnt.kernel.nativeinterface;

import java.util.ArrayList;

public interface IQQNTWrapperSession {
    final class CppProxy implements IQQNTWrapperSession {
        @Override
        public void onMsfPush(String str, byte[] bArr, PushExtraInfo pushExtraInfo) {

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
        public IKernelGroupService getGroupService() {
            return null;
        }

        @Override
        public ArrayList<String> getCacheErrLog() {
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
            return null;
        }

        @Override
        public ArrayList<String> getShortLinkBlacklist() {
            return null;
        }

        @Override
        public boolean offLineSync(boolean z) {
            return false;
        }

        @Override
        public void onDispatchPush(int i2, byte[] bArr) {

        }

        @Override
        public void onDispatchRequestReply(long j2, int i2, byte[] bArr) {

        }

        @Override
        public void switchToBackGround() {

        }

        @Override
        public void switchToFront() {

        }

        @Override
        public void updateTicket(SessionTicket sessionTicket) {

        }

        @Override
        public void setQimei36(String str) {

        }
    }

    void onMsfPush(String str, byte[] bArr, PushExtraInfo pushExtraInfo);

    IKernelTicketService getTicketService();

    IKernelUixConvertService getUixConvertService();

    IKernelGroupService getGroupService();

    ArrayList<String> getCacheErrLog();

    IKernelMsgService getMsgService();

    IKernelRichMediaService getRichMediaService();

    String getSessionId();

    ArrayList<String> getShortLinkBlacklist();

    boolean offLineSync(boolean z);

    void onDispatchPush(int i2, byte[] bArr);

    void onDispatchRequestReply(long j2, int i2, byte[] bArr);

    void switchToBackGround();

    void switchToFront();

    void updateTicket(SessionTicket sessionTicket);

    void setQimei36(String str);
}
