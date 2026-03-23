package mqq.app;

import com.qq.taf.jce.JceStruct;
import com.tencent.qphone.base.remote.ToServiceMsg;
import java.util.HashMap;

public class Packet {

    Packet(String str) {
        throw new RuntimeException("Stub!");
    }

    public static <T> T decodePacket(byte[] bArr, String str, T t) {
        throw new RuntimeException("Stub!");
    }

    public Object addAttribute(String str, Object obj) {
        throw new RuntimeException("Stub!");
    }

    public void addRequestPacket(String str, JceStruct jceStruct) {
        throw new RuntimeException("Stub!");
    }

    public HashMap<String, Object> getAttributes() {
        throw new RuntimeException("Stub!");
    }

    public String getFuncName() {
        throw new RuntimeException("Stub!");
    }

    public String getServantName() {
        throw new RuntimeException("Stub!");
    }

    public void putSendData(byte[] bArr) {
        throw new RuntimeException("Stub!");
    }

    public void setAccount(String str) {
        throw new RuntimeException("Stub!");
    }

    public void setAttributes(HashMap<String, Object> hashMap) {
        throw new RuntimeException("Stub!");
    }

    public void setFuncName(String str) {
        throw new RuntimeException("Stub!");
    }

    public void setIsSupportRetry(boolean z) {
        throw new RuntimeException("Stub!");
    }

    public void setNoResponse() {
        throw new RuntimeException("Stub!");
    }

    public void setQuickSend(boolean z, int i) {
        throw new RuntimeException("Stub!");
    }

    public void setSSOCommand(String str) {
        throw new RuntimeException("Stub!");
    }

    public void setServantName(String str) {
        throw new RuntimeException("Stub!");
    }

    public void setTimeout(long j) {
        throw new RuntimeException("Stub!");
    }

    public void setTraceInfo(String str) {
        throw new RuntimeException("Stub!");
    }

    public void setTransInfo(HashMap<String, String> hashMap) {
        throw new RuntimeException("Stub!");
    }

    public ToServiceMsg toMsg() {
        throw new RuntimeException("Stub!");
    }
}
