package com.tencent.mobileqq.troop.api;

import com.tencent.mobileqq.data.troop.TroopMemberInfo;
import mqq.app.api.IRuntimeService;

public interface ITroopMemberInfoService extends IRuntimeService {
    TroopMemberInfo getTroopMemberFromCacheOrFetchAsync(String groupId, String senderUin, String str3, a aVar);
    void getTroopMemberInfoAsync(String groupId, String senderUin, String str3, a aVar);

    interface a {
        void a(TroopMemberInfo troopMemberInfo);
    }
}