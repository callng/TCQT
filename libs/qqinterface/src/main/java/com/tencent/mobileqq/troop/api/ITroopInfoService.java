package com.tencent.mobileqq.troop.api;

import com.tencent.mobileqq.data.troop.TroopInfo;
import mqq.app.api.IRuntimeService;

// 群NT下沉，新逻辑建议使用对应业务的Repo，如ITroopListRepo、ITroopInfoRepo
public interface ITroopInfoService extends IRuntimeService {
    TroopInfo getTroopInfo(String groupId);
    TroopInfo findTroopInfo(String groupId);
    TroopInfo findTroopInfo(String groupId, boolean z);
    TroopInfo findTroopInfo(String groupId, boolean z, boolean z2);
    TroopInfo findTroopInfoInUI(String groupId);
}
