package com.tencent.qqnt.kernel.nativeinterface;

public interface IKernelRecentContactService {
    long addKernelRecentContactListener(IKernelRecentContactListener listener);
    void removeKernelRecentContactListener(long listenerId);
    void fetchAndSubscribeABatchOfRecentContact(RecentContactFetchParam param, IOperateCallback callback);
}
