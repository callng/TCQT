package com.tencent.mobileqq.msf.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import com.tencent.mobileqq.msf.core.MsfCore;

public class MsfService extends Service {

    @Override
    public IBinder onBind(Intent intent) {
        throw new RuntimeException("Stub !");
    }

    public static MsfCore getCore() {
        throw new RuntimeException("Stub !");
    }
}
