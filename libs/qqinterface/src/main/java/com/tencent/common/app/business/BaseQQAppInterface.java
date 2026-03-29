package com.tencent.common.app.business;

import com.tencent.common.app.AppInterface;

import mqq.app.MobileQQ;
import mqq.app.api.IRuntimeService;

public abstract class BaseQQAppInterface extends AppInterface {
    public BaseQQAppInterface(MobileQQ mobileQQ, String str) {
        super(mobileQQ, str);
    }

    public <T extends IRuntimeService> T getRuntimeService(Class<T> cls) {
        throw new RuntimeException("Stub!");
    }
}
