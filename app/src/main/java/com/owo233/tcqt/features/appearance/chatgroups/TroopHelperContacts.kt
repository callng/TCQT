package com.owo233.tcqt.features.appearance.chatgroups

import android.os.Handler
import android.os.Looper
import com.owo233.tcqt.host.service.NTServiceFetcher
import com.tencent.qqnt.kernel.nativeinterface.AnchorPointContactInfo
import com.tencent.qqnt.kernel.nativeinterface.IKernelRecentContactListener
import com.tencent.qqnt.kernel.nativeinterface.IOperateCallback
import com.tencent.qqnt.kernel.nativeinterface.NotificationCommonInfo
import com.tencent.qqnt.kernel.nativeinterface.RecentContactExtra
import com.tencent.qqnt.kernel.nativeinterface.RecentContactFetchParam
import com.tencent.qqnt.kernel.nativeinterface.RecentContactInfo
import com.tencent.qqnt.kernel.nativeinterface.RecentContactListChangedInfo

/** TroopHelperChatsRepo requests listType 2; the type-7 row has no nested contacts. */
internal fun loadTroopHelperContacts(onResult: (Int, String, List<RecentContactInfo>) -> Unit) {
    val service = NTServiceFetcher.kernelService.wrapperSession.recentContactService
    val main = Handler(Looper.getMainLooper())
    val contacts = LinkedHashMap<Long, RecentContactInfo>()
    var listenerId = 0L
    var loading = true
    lateinit var fetch: (AnchorPointContactInfo) -> Unit
    val listener = object : IKernelRecentContactListener {
        override fun onRecentContactListChangedVer2(infos: ArrayList<RecentContactListChangedInfo>, reason: Int) {
            main.post {
                if (!loading) return@post
                val batch = infos.firstOrNull { it.listType == 2 } ?: return@post
                batch.changedList.forEach { contacts[it.contactId] = it }
                if (batch.moreDataAtTheBottom) {
                    val last = contacts.getValue(batch.sortedContactList.last())
                    fetch(AnchorPointContactInfo().apply {
                        pos = batch.firstContactPos.toInt() + batch.sortedContactList.size - 1
                        contactId = last.contactId
                        sortField = last.sortField
                    })
                } else {
                    loading = false
                    service.removeKernelRecentContactListener(listenerId)
                    onResult(0, "", contacts.values.toList())
                }
            }
        }

        override fun onDeletedContactsNotify(contacts: ArrayList<RecentContactInfo>) = Unit
        override fun onMsgUnreadCountUpdate(counts: HashMap<String, Int>) = Unit
        override fun onRecentContactListChanged(sortedContacts: ArrayList<Long>, changedContacts: ArrayList<RecentContactInfo>, extra: RecentContactExtra) = Unit
        override fun onRecentContactNotification(contacts: ArrayList<RecentContactInfo>, info: NotificationCommonInfo, reason: Int) = Unit
    }
    val callback = IOperateCallback { code, message ->
        if (code != 0) main.post {
            if (loading) {
                loading = false
                service.removeKernelRecentContactListener(listenerId)
                onResult(code, message, emptyList())
            }
        }
    }
    fetch = { service.fetchAndSubscribeABatchOfRecentContact(RecentContactFetchParam(2, true, 200, it), callback) }
    listenerId = service.addKernelRecentContactListener(listener)
    fetch(AnchorPointContactInfo())
}
