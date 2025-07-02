package com.tencent.mobileqq.vas.api;

import com.tencent.mobileqq.vip.IVipStatusManager;

import mqq.app.api.IRuntimeService;

public interface IVasSingedApi extends IRuntimeService {

    IVipStatusManager getVipStatus();
}