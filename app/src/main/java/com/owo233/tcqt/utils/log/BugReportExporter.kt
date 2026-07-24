package com.owo233.tcqt.utils.log

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.owo233.tcqt.ActionManager
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.data.TCQTBuild
import com.owo233.tcqt.ext.resolveDeviceName
import com.owo233.tcqt.utils.PlatformTools
import java.io.File
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal object BugReportExporter {

    private const val REPORT_PREFIX = "tcqt_report_bug_"
    private const val REPORT_ENTRY_NAME = "tcqt_report.txt"
    private const val MIME_TYPE_ZIP = "application/zip"

    data class ExportedReport(
        val file: File,
        val shareUri: Uri,
        val recordCount: Int,
    )

    fun export(): ExportedReport {
        val records = ActionErrorStore.readAllRecords()
        require(records.isNotEmpty()) { "没有可导出的异常日志" }
        val hostContext = HookEnv.hostAppContext

        val directory = checkNotNull(ActionErrorStore.reportDirectory()) {
            "无法访问异常日志目录"
        }
        val output = createUniqueReportFile(directory)
        val temporary = File(directory, ".${output.name}.tmp")

        try {
            writeZip(temporary, buildReportText(hostContext, records))
            if (!temporary.renameTo(output)) {
                temporary.copyTo(output, overwrite = false)
                temporary.delete()
            }
        } catch (throwable: Throwable) {
            temporary.delete()
            output.delete()
            throw throwable
        }

        val shareUri = prepareShareUri(hostContext, output)
        return ExportedReport(
            file = output,
            shareUri = shareUri,
            recordCount = records.size,
        )
    }

    fun createShareIntent(report: ExportedReport): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = MIME_TYPE_ZIP
            putExtra(Intent.EXTRA_STREAM, report.shareUri)
            putExtra(Intent.EXTRA_SUBJECT, "TCQT 异常报告")
            clipData = ClipData.newRawUri("TCQT 异常报告", report.shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun grantSharePermissions(context: Context, intent: Intent, uri: Uri) {
        @Suppress("DEPRECATION")
        val targets = context.packageManager.queryIntentActivities(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY,
        )
        targets.forEach { target ->
            runCatching {
                context.grantUriPermission(
                    target.activityInfo.packageName,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
    }

    private fun createUniqueReportFile(directory: File): File {
        val timestamp = SimpleDateFormat("yyyyMMddHHmm", Locale.ROOT).format(Date())
        val baseName = "$REPORT_PREFIX$timestamp"
        var candidate = File(directory, "$baseName.zip")
        var suffix = 2
        while (candidate.exists()) {
            candidate = File(directory, "${baseName}_$suffix.zip")
            suffix++
        }
        return candidate
    }

    private fun writeZip(file: File, reportText: String) {
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            zip.setLevel(Deflater.BEST_COMPRESSION)
            zip.putNextEntry(ZipEntry(REPORT_ENTRY_NAME))
            zip.write(reportText.toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()
        }
    }

    private fun buildReportText(
        context: Context,
        records: List<ActionErrorStore.Record>,
    ): String {
        val generatedAt = SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss Z",
            Locale.ROOT,
        ).format(Date())
        val timezone = TimeZone.getDefault()
        val featureCount = records.distinctBy { it.actionKey }.size

        return buildString {
            // UTF-8 BOM keeps the Chinese report readable in basic Windows editors.
            append('\uFEFF')
            appendLine("TCQT 异常报告")
            appendLine("==============================")
            appendLine("生成时间: $generatedAt")
            appendLine("时区: ${timezone.id}")
            appendLine("异常功能数: $featureCount")
            appendLine("异常记录数: ${records.size}")
            appendLine()
            appendLine("[宿主]")
            appendLine("应用: ${HookEnv.appName}")
            appendLine("包名: ${HookEnv.hostAppPackageName}")
            appendLine("版本: ${HookEnv.versionName} (${HookEnv.versionCode})")
            appendLine("渠道: ${PlatformTools.getHostChannel()}")
            appendLine("当前进程: ${HookEnv.processName}")
            appendLine()
            appendLine("[模块]")
            appendLine("名称: ${TCQTBuild.APP_NAME}")
            appendLine("版本: ${TCQTBuild.VER_NAME} (${TCQTBuild.VER_CODE})")
            appendLine("构建类型: ${if (TCQTBuild.DEBUG) "debug" else "release"}")
            appendLine("构建时间: ${TCQTBuild.BUILD_TIME}")
            appendLine()
            appendLine("[设备与系统]")
            appendLine("设备名称: ${resolveDeviceName()}")
            appendLine("制造商: ${Build.MANUFACTURER}")
            appendLine("品牌: ${Build.BRAND}")
            appendLine("型号: ${Build.MODEL}")
            appendLine("设备代号: ${Build.DEVICE}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("安全补丁: ${Build.VERSION.SECURITY_PATCH.ifBlank { "unknown" }}")
            appendLine("系统指纹: ${Build.FINGERPRINT}")
            appendLine("内核: ${System.getProperty("os.version").orEmpty().ifBlank { "unknown" }}")
            appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("语言: ${Locale.getDefault().toLanguageTag()}")
            appendLine("宿主 targetSdk: ${context.applicationInfo.targetSdkVersion}")
            appendLine()
            appendLine("说明: 本报告不主动收集账号、Android ID 或聊天内容；异常堆栈仍可能包含运行路径及上下文信息。")

            records.forEachIndexed { index, record ->
                val featureName = ActionManager.getActionByKey(record.actionKey)?.name
                    ?.takeIf(String::isNotBlank)
                    ?: "未知功能"
                appendLine()
                appendLine()
                appendLine("异常记录 ${index + 1}/${records.size}")
                appendLine("------------------------------")
                appendLine("功能: $featureName")
                appendLine("功能键: ${record.actionKey}")
                appendLine("发生时间: ${formatRecordTime(record.occurredAt)}")
                appendLine("进程: ${record.processName}")
                appendLine("阶段: ${record.stage}")
                appendLine("摘要: ${record.summary}")
                appendLine("模块版本码: ${record.moduleVersionCode}")
                appendLine()
                appendLine(record.details.ifBlank { "(无堆栈详情)" })
            }
        }
    }

    private fun formatRecordTime(timestamp: Long): String {
        return SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss.SSS Z",
            Locale.ROOT,
        ).format(Date(timestamp))
    }

    private fun prepareShareUri(context: Context, report: File): Uri {
        findHostFileProviderUri(context, report)?.let { return it }

        val cacheCopy = copyForSharing(context.cacheDir, report)
        findHostFileProviderUri(context, cacheCopy)?.let { return it }

        context.externalCacheDir?.let { externalCache ->
            val externalCopy = copyForSharing(externalCache, report)
            findHostFileProviderUri(context, externalCopy)?.let { return it }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            exportToDownloads(context, report)?.let { return it }
        }

        throw IllegalStateException("宿主未提供可用的文件分享通道")
    }

    private fun copyForSharing(root: File, report: File): File {
        val directory = File(root, "tcqt_bug_reports")
        check(directory.exists() || directory.mkdirs()) { "无法创建分享缓存目录" }
        return File(directory, report.name).also { destination ->
            report.copyTo(destination, overwrite = true)
        }
    }

    @Suppress("DEPRECATION")
    private fun findHostFileProviderUri(context: Context, file: File): Uri? {
        val providers = runCatching {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_PROVIDERS or PackageManager.GET_META_DATA,
            ).providers.orEmpty()
        }.getOrDefault(emptyArray())

        return providers
            .asSequence()
            .filter { it.grantUriPermissions }
            .flatMap { provider ->
                provider.authority.orEmpty()
                    .split(';')
                    .asSequence()
                    .map(String::trim)
                    .filter(String::isNotEmpty)
            }
            .firstNotNullOfOrNull { authority ->
                runCatching {
                    FileProvider.getUriForFile(context, authority, file)
                }.getOrNull()
            }
    }

    private fun exportToDownloads(context: Context, report: File): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, report.name)
            put(MediaStore.MediaColumns.MIME_TYPE, MIME_TYPE_ZIP)
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                "${Environment.DIRECTORY_DOWNLOADS}/TCQT",
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return null

        return try {
            resolver.openOutputStream(uri, "w")?.use { output ->
                report.inputStream().buffered().use { input -> input.copyTo(output) }
            } ?: error("无法写入系统下载目录")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } catch (_: Throwable) {
            resolver.delete(uri, null, null)
            null
        }
    }
}
