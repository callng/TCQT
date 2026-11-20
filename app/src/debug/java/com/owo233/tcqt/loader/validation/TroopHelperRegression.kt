package com.owo233.tcqt.loader.validation

import android.app.Application
import com.owo233.tcqt.features.appearance.chatgroups.loadTroopHelperContacts
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Only requests native list data; never marks a conversation read. */
internal object TroopHelperRegression {
    fun run(app: Application) {
        val output = File(app.filesDir, ".tcqt/validation/troop-helper.json")
        output.parentFile!!.mkdirs()
        loadTroopHelperContacts { code, message, contacts ->
            output.writeText(JSONObject().put("code", code).put("message", message)
                .put("contacts", contacts.size)
                .put("types", JSONArray(contacts.map { it.chatType }.distinct()))
                .put("unread", contacts.count { it.unreadCnt > 0 || it.unreadFlag == 1L })
                .toString(2))
        }
    }
}
