package com.tencent.relation.common.api;

import com.tencent.mobileqq.qroute.QRouteApi;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface IRelationNTUinAndUidApi extends QRouteApi {
    void fetchUid2UinMap(List<String> list, boolean z, boolean z2, a aVar);

    void fetchUidFromUin(List<String> list, boolean z, boolean z2, b bVar);

    void fetchUin2UidMap(List<String> list, boolean z, boolean z2, c cVar);

    String getFriendUidFromUin(String str);

    String getFriendUinFromUid(String str);

    String getUidFromUin(String str);

    void getUidFromUin(String str, boolean z, boolean z2, d dVar);

    String getUinFromUid(String str);

    void getUinFromUid(String str, boolean z, boolean z2, e eVar);

    boolean isValidUin(String str);

    void saveUidByUin(String str, String str2);

    public interface a {
        void a(HashMap<String, String> hashMap);
    }

    public interface b {
        void a(ArrayList<String> arrayList);
    }

    public interface c {
        void a(Map<String, String> map);
    }

    public interface d {
        void a(String str);
    }

    public interface e {
        void a(String str);
    }
}
