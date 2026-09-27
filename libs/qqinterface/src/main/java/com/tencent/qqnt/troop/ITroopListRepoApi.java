package com.tencent.qqnt.troop;

import com.tencent.mobileqq.data.troop.TroopInfo;
import com.tencent.mobileqq.qroute.QRouteApi;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.List;
import java.util.Map;

/**
 * 群列表仓库
 * 这些同步方法都读**本地缓存**，不发网络请求，因此可以安全地在脚本里直接调用。
 */
public interface ITroopListRepoApi extends QRouteApi {

    /** 已加入的群（缓存）。 */
    @Nullable
    List<TroopInfo> getJoinedTroopInfoFromCache();

    /** 已加入的群，已排序（缓存）。 */
    @Nullable
    List<TroopInfo> getSortedJoinedTroopInfoFromCache();

    /** 全部群（缓存）。 */
    @Nullable
    List<TroopInfo> getTroopListFromCache();

    /** 置顶群（缓存）。 */
    @Nullable
    List<TroopInfo> getTopTroopListFromCache();

    /** 群号 → 消息屏蔽状态（缓存）。 */
    @Nullable
    Map<String, Integer> getTroopUinListWithMsgMask();

    /** 单个群信息（缓存）。 */
    @Nullable
    TroopInfo getTroopInfoFromCache(@Nullable String str);

    /** 群列表缓存是否已初始化完成。 */
    boolean isTroopListCacheAllInited();

    /** 由群号取群 Uin。 */
    @Nullable
    String getTroopUinByTroopCode(@Nullable String str);

    /** 是否已退出该群。 */
    boolean isExit(@Nullable String str, @Nullable String str2, boolean z);

    /** 预加载群列表。 */
    void preloadTroopList();

    /** 触发一次群列表拉取（走网络）。 */
    void fetchTroopList(boolean z);

    void deleteTroopInCache(@NotNull String str);

    void saveTroopInCache(@NotNull TroopInfo troopInfo);
}
