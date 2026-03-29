package com.tencent.mobileqq.qfix;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;

public class ApplicationDelegate extends Application {

    @Override
    public Intent registerReceiver(BroadcastReceiver broadcastReceiver, IntentFilter intentFilter) {
        throw new RuntimeException("Stub!");
    }
}
