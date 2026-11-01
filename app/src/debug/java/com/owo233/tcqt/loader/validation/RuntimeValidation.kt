package com.owo233.tcqt.loader.validation

import android.app.Application
import android.app.Activity
import android.os.Bundle
import java.lang.ref.WeakReference
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.owo233.tcqt.core.config.SettingsStore
import com.owo233.tcqt.core.config.ConfigRestorePlan
import com.owo233.tcqt.core.config.TCQTSetting
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.TCQTBuild
import com.owo233.tcqt.core.hook.HookEngineManager
import com.owo233.tcqt.core.action.ActionRegistry
import com.owo233.tcqt.core.action.ActionProcess
import com.owo233.tcqt.core.action.StartupScheduler
import com.owo233.tcqt.core.action.FirstFrameGate
import com.owo233.tcqt.generated.GeneratedActionList
import java.io.File
import org.json.JSONObject

/** Explicit, debug-only host regression entry; reports contain no account or message data. */
internal object RuntimeValidation {

    private var installed = false
    private var settingsActivity: WeakReference<Activity>? = null

    fun install(app: Application) {
        if (installed || !HookEnv.isMainProcess()) return
        installed = true
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                if (activity.javaClass.name == "com.owo233.tcqt.ui.settings.SettingActivity") settingsActivity = WeakReference(activity)
            }
            override fun onActivityDestroyed(activity: Activity) {
                if (settingsActivity?.get() === activity) settingsActivity = null
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        })
        app.registerReceiver(object : BroadcastReceiver() {
            // The debug command deliberately combines bounded storage checks and real UI calls on
            // the receiver's main thread. Production releases do not register this test receiver.
            @android.annotation.SuppressLint("ThreadConstraint")
            override fun onReceive(context: Context, intent: Intent) {
                val step = intent.getStringExtra("step") ?: "01"
                require(step.matches(Regex("[0-9]{2}")))
                val report = JSONObject()
                    .put("step", step)
                    .put("version", TCQTBuild.VER_NAME)
                    .put("engine", HookEngineManager.engine.frameworkName)
                    .put("mode", System.getProperty("tcqt.injection.mode"))
                try {
                    when (step) {
                        "01" -> {
                            val store = SettingsStore(File(app.filesDir, ".tcqt/validation/settings"), "test")
                            store.put("enabled", true)
                            store.put("count", 17)
                            val second = SettingsStore(File(app.filesDir, ".tcqt/validation/settings"), "test")
                            check(second["enabled"] == true && second["count"] == 17)
                            second.remove("enabled")
                            check(!store.contains("enabled"))
                            val start = System.nanoTime()
                            repeat(100) { check(store["count"] == 17) }
                            report.put("read100Ms", (System.nanoTime() - start) / 1_000_000.0)
                            // Reads the actual host configuration without changing it.
                            report.put("hostSettingKeys", TCQTSetting.getAllKeys().size)
                        }
                        "02" -> {
                            val store = SettingsStore(File(app.filesDir, ".tcqt/validation/restore"), "test")
                            store.replace(mapOf("enabled" to false, "mask" to 3, "local" to "preserved"))
                            val schema = mapOf("enabled" to "BOOLEAN", "mask" to "INT_MULTI")
                            val valid = """{"marker":"TCQT_CONFIG_BACKUP","version":1,"settings":[{"key":"enabled","type":"BOOLEAN","value":"true"}]}"""
                            listOf("{}", valid.replace("true", "bad"), valid.replace("enabled", "unknown")).forEach {
                                check(runCatching { ConfigRestorePlan.decode(it, schema).applyTo(store) }.isFailure)
                                check(store["enabled"] == false && store["mask"] == 3)
                            }
                            ConfigRestorePlan.decode(valid, schema).applyTo(store)
                            check(store.all() == mapOf("enabled" to true, "local" to "preserved"))
                            report.put("hostSettingKeys", TCQTSetting.getAllKeys().size)
                        }
                        "03" -> report.put("hookContractChecks", HookRegression.run(HookEngineManager.engine))
                        "04" -> {
                            report.put("hookContractChecks", HookRegression.run(HookEngineManager.engine))
                            report.put("lifecycleChecks", HookRegression.lifecycle(HookEngineManager.engine))
                        }
                        "12" -> {
                            report.put("hookContractChecks", HookRegression.run(HookEngineManager.engine))
                            report.put("lifecycleChecks", HookRegression.lifecycle(HookEngineManager.engine))
                            report.put("jniTypeChecks", ArtTypesRegression.run(HookEngineManager.engine))
                            report.put("hostSettingKeys", TCQTSetting.getAllKeys().size)
                        }
                        "13" -> {
                            val names = GeneratedActionList.ACTIONS.map { it.name }
                            check(names == names.distinct().sorted())
                            check(GeneratedActionList.DESCRIPTORS.map { it.actionClass.name } == names)
                            check(TCQTSetting.settingMap.size > 100)
                            report.put("registrations", names.size)
                            report.put("settingDefinitions", TCQTSetting.settingMap.size)
                            report.put("hookContractChecks", HookRegression.run(HookEngineManager.engine))
                            report.put("settingsChecks", SettingsRegression.run(app))
                            report.put("hostSettingKeys", TCQTSetting.getAllKeys().size)
                        }
                        "14" -> {
                            val wire = com.owo233.tcqt.core.proto.ProtoUtils.encodeToByteArray(
                                com.owo233.tcqt.core.proto.protobufOf(1 to 17))
                            val decoded = com.owo233.tcqt.core.proto.ProtoUtils.decodeFromByteArray(wire)
                            check((decoded[1] as com.owo233.tcqt.core.proto.ProtoNumber).value.toInt() == 17)
                            check(com.owo233.tcqt.core.dexkit.DexKitHostCode(true, "loaded").matches("loaded"))
                            check(!com.owo233.tcqt.core.dexkit.DexKitHostCode(true).matches(null))
                            report.put("hookContractChecks", HookRegression.run(HookEngineManager.engine))
                            report.put("lifecycleChecks", HookRegression.lifecycle(HookEngineManager.engine))
                            report.put("jniTypeChecks", ArtTypesRegression.run(HookEngineManager.engine))
                            report.put("settingsChecks", SettingsRegression.run(app))
                            report.put("hostSettingKeys", TCQTSetting.getAllKeys().size)
                        }
                        "06" -> {
                            val before = ActionRegistry.instantiatedCount
                            val msf = ActionRegistry.actionClassesFor(ActionProcess.MSF)
                            check(ActionRegistry.instantiatedCount == before)
                            check(msf.size < GeneratedActionList.ACTIONS.size)
                            check(msf.all { ActionRegistry.metadataFor(it)!!.matches(ActionProcess.MSF) })
                            report.put("registeredClasses", GeneratedActionList.ACTIONS.size)
                                .put("msfClasses", msf.size)
                                .put("startupPlanMs", ActionRegistry.lastPlanCostMs)
                                .put("startupPlanInstances", ActionRegistry.lastPlanInstanceCount)
                                .put("instancesBeforeCatalog", before)
                            val definitions = TCQTSetting.settingMap
                            check(definitions.size > 100)
                            check(definitions["fake_pic_size.type"] != null)
                            report.put("settingDefinitions", definitions.size)
                                .put("hostSettingKeys", TCQTSetting.getAllKeys().size)
                        }
                        "07" -> {
                            val frame = requireNotNull(StartupScheduler.lastFirstFrame)
                            report.put("drawSignal", frame.reason.name)
                                .put("signalToDeferredMs", (StartupScheduler.deferredStartedNanos - frame.observedNanos) / 1_000_000.0)
                            check(frame.reason == FirstFrameGate.Reason.FRAME_COMMIT || frame.reason == FirstFrameGate.Reason.DRAW_TRAVERSAL)
                            check(StartupScheduler.deferredStartedNanos >= frame.observedNanos)
                            report.put("hookContractChecks", HookRegression.run(HookEngineManager.engine))
                        }
                        "11" -> {
                            report.put("layoutChecks", GlassRegression.synthetic(app))
                            val action = intent.getStringExtra("uiAction")
                            if (action == null) report.put("hookContractChecks", HookRegression.run(HookEngineManager.engine))
                            else if (android.os.Build.VERSION.SDK_INT >= 33) report.put("bar", GlassRegression.action(app, action))
                            else error("Glass layout validation requires API 33")
                            report.put("hostSettingKeys", TCQTSetting.getAllKeys().size)
                        }
                        "10" -> {
                            check(com.owo233.tcqt.core.env.HostServicesProvider.services != null)
                            report.put("nightMode", HookEnv.isNightMode())
                            val alternate = com.owo233.tcqt.core.env.HostServicesProvider.requireServices().alternateVersionCode()
                            check(HookEnv.versionCode >= alternate)
                            report.put("hostVersionCode", HookEnv.versionCode)
                            report.put("alternateVersionCode", alternate)
                            com.owo233.tcqt.core.env.Toasts.info("TCQT 边界验证")
                            report.put("hookContractChecks", HookRegression.run(HookEngineManager.engine))
                            report.put("settingsChecks", SettingsRegression.run(app))
                            report.put("hostSettingKeys", TCQTSetting.getAllKeys().size)
                        }
                        "08" -> {
                            report.put("settingsChecks", SettingsRegression.run(app))
                            report.put("hostSettingKeys", TCQTSetting.getAllKeys().size)
                            val uiAction = intent.getStringExtra("uiAction")
                            if (uiAction == "open") {
                                val activity = requireNotNull(com.owo233.tcqt.core.env.HostBridge.topActivity())
                                activity.startActivity(Intent(activity, com.owo233.tcqt.ui.settings.SettingActivity::class.java))
                            } else if (uiAction != null) {
                                val activity = requireNotNull(settingsActivity?.get())
                                check(activity.javaClass.name == "com.owo233.tcqt.ui.settings.SettingActivity")
                                val model = activity.javaClass.getDeclaredMethod("getSettingsModel").apply { isAccessible = true }
                                    .invoke(activity) as com.owo233.tcqt.ui.settings.SettingViewModel
                                when (uiAction) {
                                    "search" -> { model.enterSearch(); model.updateSearchQuery("复读") }
                                    "empty" -> model.clearSearchQuery()
                                    "details" -> { model.openFeatureDetails("repeat_message"); model.setTextValue("repeat_message.icon", SettingsRegression.icon()) }
                                    "revert" -> { model.reloadPersistedSettings(); model.navigateToRoot(); model.exitSearch() }
                                    "appearance" -> model.openAppearance()
                                    "themeDark", "themeMonet", "themeReset" -> ThemeRegression.apply(activity, uiAction)
                                    else -> error("Unknown UI action")
                                }
                                report.put("pendingChanges", model.pendingChangeCount)
                                report.put("searchActive", model.isSearchActive)
                            }
                        }
                        "16" -> report.put("stickySearch", StickySearchRegression.run(intent))
                        else -> error("Unimplemented validation step: $step")
                    }
                    report.put("passed", true)
                } catch (error: Throwable) {
                    report.put("passed", false).put("error", error.stackTraceToString())
                }
                val output = File(app.filesDir, ".tcqt/validation/$step.json")
                output.parentFile!!.mkdirs()
                output.writeText(report.toString(2))
                android.util.Log.i("TCQT-Validation", report.toString())
            }
        }, IntentFilter("com.owo233.tcqt.DEBUG_VALIDATE"), Context.RECEIVER_EXPORTED)
    }
}
