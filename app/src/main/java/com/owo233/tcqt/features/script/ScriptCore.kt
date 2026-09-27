package com.owo233.tcqt.features.script

import android.content.ContentResolver
import android.net.Uri
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.InfraTask
import com.owo233.tcqt.api.Requires
import com.owo233.tcqt.core.action.ActionPriority
import com.owo233.tcqt.core.action.ActionProcess
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.log.Log
import com.owo233.tcqt.core.proto.GlobalJson
import com.owo233.tcqt.core.script.ScriptGateway
import com.owo233.tcqt.core.script.ScriptMeta
import com.owo233.tcqt.core.sync.ModuleScope
import com.owo233.tcqt.features.internal.pipeline.OnAIOSendMsgBefore
import com.owo233.tcqt.features.internal.pipeline.OnAIOViewUpdate
import com.owo233.tcqt.features.internal.pipeline.OnMenuBuilder
import com.owo233.tcqt.features.script.bean.ScriptAtUinConverter
import com.owo233.tcqt.features.script.bean.ScriptInfo
import com.owo233.tcqt.host.service.api.GroupService
import com.tencent.qqnt.kernel.nativeinterface.MsgElement
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.util.zip.ZipInputStream

/**
 * 脚本引擎核心：脚本目录装载、生命周期管理与宿主事件管线接入。
 *
 * **必须是 [InfraTask]，不能声明为 `ActionUiType.ENTRY`**：`ActionSpec.canRun()` 对
 * ENTRY 恒为 false，`onRun` 因此不会被调用，`install()` 也就永不执行 —— 网关没有注入，
 * 设置入口只会提示「脚本引擎未就绪」。管线与注册表同理必须始终存在，否则关掉再打开
 * 会丢掉事件接线；每个脚本是否随宿主启动由它自己的自动装载位决定。
 *
 * 用户可见的入口是同一个包下的 [ScriptEngine]。
 */
@RegisterAction
object ScriptCore : InfraTask(
    key = "script_core",
    priority = ActionPriority.DEFERRED,
    processes = setOf(ActionProcess.MAIN),
    requires = Requires(ntOnly = true),
),
    ScriptGateway,
    OnAIOSendMsgBefore,
    OnAIOViewUpdate,
    OnMenuBuilder {

    /** 脚本菜单装配顺序：排在模块内置菜单项之后。 */
    override val decoratorOrder: Int = 500

    override fun install() {
        ScriptGateway.instance = this
        ScriptAtUinConverter.converter = { uid -> GroupService.getUinFromUid(uid) }

        ScriptRegistry.clear()
        synchronized(installed) { installed.clear() }
        loadAutoLoadIds()
        refresh()
        startAutoLoad()
    }

    // ── 目录装载 ─────────────────────────────────────────────────────────

    /** 重新扫描脚本目录；运行中的脚本保持运行。 */
    fun refresh(): List<ScriptInfo> {
        val existing = synchronized(installed) { installed.associateBy { it.id }.toMutableMap() }

        val found = scriptsDir.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.mapNotNull { ScriptInfo.fromDir(it) }
            ?.sortedBy { it.name }
            .orEmpty()

        synchronized(installed) {
            installed.clear()
            found.forEach { info ->
                val old = existing[info.id]
                if (old != null) {
                    old.updateFromDisk()
                    installed.add(old)
                } else {
                    installed.add(info)
                }
            }
        }
        return installed.toList()
    }

    fun scripts(): List<ScriptInfo> = synchronized(installed) { installed.toList() }

    fun find(id: String): ScriptInfo? =
        synchronized(installed) { installed.firstOrNull { it.id == id } }

    // ── 生命周期 ─────────────────────────────────────────────────────────

    fun start(info: ScriptInfo): Boolean = runCatching {
        runtimeOf(info).start()
        ScriptRegistry.attach(runtimeOf(info))
        true
    }.onFailure {
        Log.e("脚本启动失败 [${info.id}]", it)
        info.isRunning = false
    }.getOrDefault(false)

    fun stop(info: ScriptInfo) {
        runCatching { runtimeOf(info).stop() }
            .onFailure { Log.e("脚本停止失败 [${info.id}]", it) }
        ScriptRegistry.detach(runtimeOf(info))
    }

    fun reload(info: ScriptInfo): Boolean {
        stop(info)
        return start(info)
    }

    fun delete(info: ScriptInfo) {
        stop(info)
        synchronized(installed) { installed.remove(info) }
        runCatching { File(info.dirPath).deleteRecursively() }
            .onFailure { Log.e("脚本删除失败 [${info.id}]", it) }
        setAutoLoad(info, false)
        refresh()
    }

    // ── ScriptGateway：供设置界面与宿主入口调用 ──────────────────────────

    override val scriptDir: String get() = scriptsDir.absolutePath

    override fun list(): List<ScriptMeta> = scripts().map { it.toMeta() }

    override fun start(id: String): Boolean = find(id)?.let { start(it) } ?: false

    override fun stop(id: String) {
        find(id)?.let { stop(it) }
    }

    override fun reload(id: String): Boolean = find(id)?.let { reload(it) } ?: false

    override fun delete(id: String) {
        find(id)?.let { delete(it) }
    }

    override fun setAutoLoad(id: String, enabled: Boolean) {
        find(id)?.let { setAutoLoad(it, enabled) }
    }

    override fun create(id: String, name: String, version: String, author: String): Boolean =
        createScript(id, name, version, author)

    override fun importZip(resolver: ContentResolver, uri: Uri): String? = installFromZip(resolver, uri)

    // ── 自动装载 ─────────────────────────────────────────────────────────

    fun isAutoLoad(info: ScriptInfo): Boolean = info.id in autoLoadIds

    fun setAutoLoad(info: ScriptInfo, enabled: Boolean) {
        if (enabled) autoLoadIds.add(info.id) else autoLoadIds.remove(info.id)
        saveAutoLoadIds()
    }

    private fun startAutoLoad() {
        ModuleScope.launchIO("ScriptAutoLoad") {
            scripts()
                .filter { isAutoLoad(it) && !it.isRunning }
                .forEach { start(it) }
        }
    }

    private fun loadAutoLoadIds() {
        autoLoadIds.clear()
        runCatching {
            if (!autoLoadFile.exists()) return
            GlobalJson.parseToJsonElement(autoLoadFile.readText()).jsonObject
                .filterValues { (it as? JsonPrimitive)?.booleanOrNull == true }
                .keys
                .forEach(autoLoadIds::add)
        }.onFailure { Log.e("脚本自动装载列表读取失败", it) }
    }

    private fun saveAutoLoadIds() {
        runCatching {
            autoLoadFile.parentFile?.mkdirs()
            val obj = JsonObject(autoLoadIds.associateWith { JsonPrimitive(true) })
            autoLoadFile.writeText(GlobalJson.encodeToString(JsonObject.serializer(), obj))
        }.onFailure { Log.e("脚本自动装载列表写入失败", it) }
    }

    // ── 新建与导入 ───────────────────────────────────────────────────────

    private fun createScript(id: String, name: String, version: String, author: String): Boolean {
        if (scripts().any { it.id == id }) return false

        val folderName = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { id }
        val target = File(scriptsDir, folderName)
        if (target.exists()) return false
        if (!target.mkdirs()) return false

        return runCatching {
            File(target, ScriptInfo.PROP_FILE).writeText(
                buildString {
                    appendLine("id=$id")
                    appendLine("pluginName=$name")
                    appendLine("versionCode=$version")
                    appendLine("author=$author")
                }
            )
            File(target, ScriptInfo.DESC_FILE).writeText("在这里填写脚本说明。")
            File(target, ScriptInfo.ENTRY_FILE).writeText(DEFAULT_SCRIPT)
            File(target, ScriptInfo.CONFIG_DIR).mkdirs()
            refresh()
            true
        }.onFailure {
            Log.e("脚本创建失败 [$id]", it)
            target.deleteRecursively()
        }.getOrDefault(false)
    }

    private fun installFromZip(resolver: ContentResolver, uri: Uri): String? {
        val tempDir = File(scriptsDir, ".import_${System.currentTimeMillis()}")
        return try {
            tempDir.mkdirs()
            resolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "无法读取所选文件" }
                unzip(input, tempDir)
            }

            // 压缩包内只有一层目录时，把它当作脚本根
            val children = tempDir.listFiles().orEmpty()
            val root = if (children.size == 1 && children[0].isDirectory) children[0] else tempDir

            val info = ScriptInfo.fromDir(root) ?: return "无效的脚本包（缺少 info.prop）"

            val existing = find(info.id)
            val target = if (existing != null) File(existing.dirPath) else File(scriptsDir, root.name)
            if (existing != null) stop(existing)

            val configBackup = existing?.let { File(it.dirPath, ScriptInfo.CONFIG_DIR) }
                ?.takeIf { it.exists() }
                ?.let { backup ->
                    File(tempDir.parentFile, ".config_${info.id}").also { dst ->
                        dst.deleteRecursively()
                        backup.copyRecursively(dst)
                    }
                }

            target.deleteRecursively()
            root.copyRecursively(target)
            configBackup?.takeIf { it.exists() }?.copyRecursively(
                File(target, ScriptInfo.CONFIG_DIR),
                overwrite = true
            )
            configBackup?.deleteRecursively()

            refresh()
            val installed = find(info.id)
            if (installed != null && (existing?.isRunning == true || isAutoLoad(installed))) {
                start(installed)
            }
            null
        } catch (t: Throwable) {
            Log.e("脚本导入失败", t)
            "导入失败: ${t.message}"
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun unzip(input: java.io.InputStream, targetDir: File) {
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name.replace('\\', '/')
                if (name.contains("..")) {
                    entry = zip.nextEntry
                    continue
                }

                val outFile = File(targetDir, name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { zip.copyTo(it) }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    // ── 管线：发送前改写文本 ─────────────────────────────────────────────

    override fun onSend(elements: ArrayList<MsgElement>) {
        if (!ScriptRegistry.hasRunning) return
        elements.forEach { element ->
            ScriptEvents.rewriteText(element)?.let { newText ->
                element.textElement?.content = newText
            }
        }
    }

    // ── 管线：收到的消息 -> onMsg ────────────────────────────────────────

    override fun onGetViewNt(
        view: android.view.ViewGroup,
        msgRecord: MsgRecord,
        param: com.owo233.tcqt.core.hook.MethodHookParam,
    ) {
        ScriptEvents.onReceiveMessage(msgRecord)
    }

    // ── 管线：长按菜单 ───────────────────────────────────────────────────

    override val targetComponentTypes: Array<String>
        get() = MSG_MENU_COMPONENTS

    override fun onGetMenuNt(
        msg: Any,
        componentType: String,
        param: com.owo233.tcqt.core.hook.MethodHookParam,
    ) {
        if (!ScriptRegistry.hasRunning) return
        val menuList = param.result as? List<*> ?: return

        val record = runCatching {
            AIO_MSG_ITEM.getMethod("getMsgRecord").invoke(msg)
        }.getOrNull() as? MsgRecord ?: return

        param.result = ScriptEvents.onBuildMenu(record, msg, menuList)
    }

    // ── 内部 ─────────────────────────────────────────────────────────────

    /** 脚本目录：`<模块数据目录>/script`。 */
    private val scriptsDir: File
        get() = File(HookEnv.moduleDataPath, "script").apply { mkdirs() }

    private val autoLoadFile: File
        get() = File(scriptsDir, "auto_load.json")

    private val installed = LinkedHashSet<ScriptInfo>()

    private val autoLoadIds = linkedSetOf<String>()

    private val runtimeCache = mutableMapOf<String, ScriptRuntime>()

    private fun runtimeOf(info: ScriptInfo): ScriptRuntime = synchronized(runtimeCache) {
        runtimeCache.getOrPut(info.id) { ScriptRuntime(info) }
    }

    private fun ScriptInfo.toMeta(): ScriptMeta = ScriptMeta(
        id = id,
        name = name,
        version = version,
        author = author,
        desc = desc,
        dirPath = dirPath,
        running = isRunning,
        autoLoad = isAutoLoad(this),
    )

    private val AIO_MSG_ITEM: Class<*> by lazy {
        HookEnv.hostClassLoader.loadClass("com.tencent.mobileqq.aio.msg.AIOMsgItem")
    }

    private const val DEFAULT_SCRIPT = """log("脚本开始运行");
toast("Hello TCQT");
addItem("测试菜单", "onTestClick");

void onTestClick(int chatType, String peerUin, String peerName) {
    qqToast(2, "点击了菜单");
}

void onMsg(Object msgData) {
    log("收到消息: " + msgData.msg);
}

void unLoadPlugin() {
    log("脚本停止运行");
}
"""

    /** 脚本菜单挂载点：覆盖常见消息类型，与模块内置菜单项保持一致。 */
    private val MSG_MENU_COMPONENTS = arrayOf(
        "com.tencent.mobileqq.aio.msglist.holder.component.text.AIOTextContentComponent",
        "com.tencent.mobileqq.aio.msglist.holder.component.text.AIOUnsuportContentComponent",
        "com.tencent.mobileqq.aio.msglist.holder.component.ptt.AIOPttContentComponent",
        "com.tencent.mobileqq.aio.msglist.holder.component.flashpic.AIOFlashPicContentComponent",
        "com.tencent.mobileqq.aio.msglist.holder.component.video.AIOVideoContentComponent",
        "com.tencent.mobileqq.aio.msglist.holder.component.pic.AIOPicContentComponent",
        "com.tencent.mobileqq.aio.msglist.holder.component.multipci.AIOMultiPicContentComponent",
        "com.tencent.mobileqq.aio.msglist.holder.component.reply.AIOReplyComponent",
        "com.tencent.mobileqq.aio.msglist.holder.component.mix.AIOMixContentComponent",
        "com.tencent.mobileqq.aio.msglist.holder.component.marketface.AIOMarketFaceComponent",
        "com.tencent.mobileqq.aio.msglist.holder.component.markdown.AIORichContentComponent",
        "com.tencent.mobileqq.aio.msglist.holder.component.longmsg.AIOLongMsgContentComponent",
        "com.tencent.mobileqq.aio.msglist.holder.component.file.AIOFileContentComponent",
        "com.tencent.mobileqq.aio.msglist.holder.component.file.AIOOnlineFileContentComponent",
        "com.tencent.mobileqq.aio.msglist.holder.component.ark.AIOArkContentComponent",
        "com.tencent.mobileqq.aio.msglist.holder.component.anisticker.AIOAniStickerContentComponent",
        "com.tencent.mobileqq.aio.msglist.holder.component.chain.ChainAniStickerContentComponent",
        "com.tencent.mobileqq.aio.msglist.holder.component.template.AIOTemplateMsgComponent",
        "com.tencent.mobileqq.aio.shop.AIOShopArkContentComponent",
    )
}
