package mqq.app.api.impl;

import android.content.Intent;
import android.os.Bundle;
import com.tencent.qphone.base.remote.FromServiceMsg;
import com.tencent.qphone.base.remote.ToServiceMsg;
import mqq.app.MSFServlet;
import mqq.app.Packet;

public class SSOEasyServlet extends MSFServlet {
    private static final String TAG = "SSOEasyServlet";

    public void onReceive(Intent intent, FromServiceMsg fromServiceMsg) {
        ToServiceMsg toServiceMsg;
        if (intent != null) {
            toServiceMsg = intent.getParcelableExtra("ToServiceMsg");
            fromServiceMsg.attributes.put("FromServiceMsg", toServiceMsg);
        } else {
            toServiceMsg = new ToServiceMsg("", fromServiceMsg.getUin(), fromServiceMsg.getServiceCmd());
        }
        Bundle bundle = new Bundle();
        bundle.putParcelable("ToServiceMsg", toServiceMsg);
        bundle.putParcelable("FromServiceMsg", fromServiceMsg);
        notifyObserver(intent, 0, fromServiceMsg.isSuccess(), bundle, null);
    }

    public void onSend(Intent intent, Packet packet) {
        ToServiceMsg parcelableExtra;
        if (intent == null || (parcelableExtra = intent.getParcelableExtra("ToServiceMsg")) == null) {
            return;
        }
        packet.setSSOCommand(parcelableExtra.getServiceCmd());
        packet.putSendData(parcelableExtra.getWupBuffer());
        packet.setTimeout(parcelableExtra.getTimeout());
        packet.setAttributes(parcelableExtra.getAttributes());
        if (parcelableExtra.isNeedCallback()) {
            return;
        }
        packet.setNoResponse();
    }
}
