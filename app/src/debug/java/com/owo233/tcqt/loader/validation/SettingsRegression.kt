package com.owo233.tcqt.loader.validation

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.owo233.tcqt.core.config.PngIconCodec
import com.owo233.tcqt.core.config.SettingsStore
import com.owo233.tcqt.ui.settings.SettingViewModel
import com.owo233.tcqt.ui.settings.SettingsEditSession
import java.io.ByteArrayOutputStream
import java.io.File

internal object SettingsRegression {
    fun run(app: Application): Int {
        val store = SettingsStore(File(app.filesDir, ".tcqt/validation/edit"), "test")
        store.replace(mapOf("enabled" to false, "count" to 1))
        val session = SettingsEditSession()
        session.reload(store.all())
        session.update("enabled", true)
        session.update("count", 2)
        check(runCatching { session.save { error("simulated disk failure") } }.isFailure)
        check(session.changeCount == 2 && store["enabled"] == false)
        session.save { edits -> store.update { it.putAll(edits) } }
        check(session.changeCount == 0 && store["enabled"] == true && store["count"] == 2)

        val encoded = icon()
        requireNotNull(PngIconCodec.decodeOrNull(encoded)).also { check(it.width == 32); it.recycle() }

        val holder = ViewModelStore()
        try {
            val model = ViewModelProvider(holder, ViewModelProvider.NewInstanceFactory())[SettingViewModel::class.java]
            model.enterSearch()
            model.updateSearchQuery("复读")
            check(model.currentFeatures.value.isNotEmpty())
            model.openFeatureDetails("repeat_message")
            model.setTextValue("repeat_message.icon", encoded)
            check(model.hasPendingChanges && model.pendingChangeCount == 1)
            check(model.navigateUp())
            check(model.searchQuery == "复读" && model.hasPendingChanges)
            model.clearSearchQuery()
            check(model.currentFeatures.value.isEmpty())
            model.exitSearch()
            model.reloadPersistedSettings()
            check(!model.hasPendingChanges)
        } finally {
            holder.clear()
        }
        return 6
    }

    fun icon(): String {
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        val bytes = ByteArrayOutputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            output.toByteArray()
        }
        bitmap.recycle()
        return bytes.inputStream().use(PngIconCodec::importPng)
    }
}
