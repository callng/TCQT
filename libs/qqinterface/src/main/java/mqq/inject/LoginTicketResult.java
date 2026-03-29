package mqq.inject;

import androidx.annotation.NonNull;
import com.tencent.commonsdk.util.HexUtil;

public class LoginTicketResult {
    public byte[] a2 = new byte[0];
    public byte[] d2 = new byte[0];
    public byte[] d2Key = new byte[0];

    @NonNull
    public String toString() {
        return "LoginTicketResult{a2=" + HexUtil.bytes2HexStr(this.a2) + ", d2=" + HexUtil.bytes2HexStr(this.d2) + ", d2Key=" + HexUtil.bytes2HexStr(this.d2Key) + '}';
    }
}
