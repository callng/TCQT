package com.tencent.biz;

import android.content.Intent;
import com.tencent.qphone.base.remote.FromServiceMsg;
import mqq.app.MSFServlet;
import mqq.app.Packet;

public class ProtoServlet extends MSFServlet {

    public void onReceive(Intent intent, FromServiceMsg fromServiceMsg) {
        throw new RuntimeException("Stub!");
    }

    public void onSend(Intent intent, Packet packet) {
        throw new RuntimeException("Stub!");
    }
}
