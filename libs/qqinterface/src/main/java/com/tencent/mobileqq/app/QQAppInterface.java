package com.tencent.mobileqq.app;

import com.tencent.common.app.BaseApplicationImpl;
import com.tencent.common.app.business.BaseQQAppInterface;
import com.tencent.qphone.base.util.BaseApplication;

public class QQAppInterface extends BaseQQAppInterface {

    public QQAppInterface(BaseApplicationImpl baseApplication, String str) {
        super(baseApplication, str);
    }

    @Override
    public BaseApplication getApp() {
        throw new RuntimeException("Stub !");
    }

    @Override
    public int getAppid() {
        throw new RuntimeException("Stub !");
    }

    @Override
    public String getCurrentAccountUin() {
        throw new RuntimeException("Stub !");
    }
}
