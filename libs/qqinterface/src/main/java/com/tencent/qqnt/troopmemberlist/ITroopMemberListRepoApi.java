package com.tencent.qqnt.troopmemberlist;

import androidx.lifecycle.LifecycleOwner;

import com.tencent.mobileqq.data.troop.TroopMemberInfo;
import com.tencent.mobileqq.qroute.QRouteApi;

import org.jetbrains.annotations.Nullable;

import java.util.List;

import kotlin.jvm.functions.Function2;

/**
 * 群成员仓库
 * 只列**同步读缓存**的方法 —— 脚本回调跑在推送线程上，不适合在里面等异步回调。
 */
public interface ITroopMemberListRepoApi extends QRouteApi {

    /** 从缓存取单个成员，不命中不发请求。 */
    @Nullable
    TroopMemberInfo getTroopMemberFromCache(
            @Nullable String str,
            @Nullable String str2,
            @Nullable LifecycleOwner lifecycleOwner,
            @Nullable String str3
    );

    /** 同步取成员信息。 */
    @Nullable
    TroopMemberInfo getTroopMemberInfoSync(
            @Nullable String str,
            @Nullable String str2,
            @Nullable LifecycleOwner lifecycleOwner,
            @Nullable String str3
    );

    /** 成员信息 DB 是否已初始化。 */
    boolean isTroopMemberInfoDBInited(@Nullable String str);

    /** 成员列表是否已过期。 */
    boolean isTroopMemberListExpired(@Nullable String str);

    /** 由成员 UID 取 QQ 号（异步回调）。 */
    void fetchTroopMemberUin(@Nullable String str, @Nullable Function2<?, ?, ?> function2);

    /** 由成员 UID 取 QQ 号（批量，异步回调）。 */
    void fetchTroopMemberUin(@Nullable List<String> list, @Nullable Function2<?, ?, ?> function2);

    /** 由成员 QQ 号取 UID（异步回调）。 */
    void fetchTroopMemberUid(@Nullable String str, @Nullable Function2<?, ?, ?> function2);
}
