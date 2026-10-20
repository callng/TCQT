package com.owo233.tcqt.features.menu.troop

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.owo233.tcqt.core.env.Toasts
import com.owo233.tcqt.core.log.Log
import com.owo233.tcqt.host.service.api.AdminPermission
import com.owo233.tcqt.host.service.api.AdminPermissionProtocol
import com.owo233.tcqt.host.service.api.AdminPermissionSnapshot
import com.owo233.tcqt.host.service.api.TroopAdminPermissionService
import com.owo233.tcqt.ui.component.ActionButton
import com.owo233.tcqt.ui.component.DialogForm
import com.owo233.tcqt.ui.component.SecondaryButton
import com.owo233.tcqt.ui.component.TCQTPanel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun TroopAdminPermissionsMenu(
    groupId: String, memberUin: String, memberNick: String, onBack: () -> Unit
) {

    var snapshot by remember(groupId, memberUin) { mutableStateOf<AdminPermissionSnapshot?>(null) }
    var busy by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedLimit by remember { mutableIntStateOf(0) }
    var hasLoaded by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    var limitExpanded by remember { mutableStateOf(false) }
    var awaitingConfirmation by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val canEdit = !busy && !awaitingConfirmation

    fun report(failure: Throwable) {
        if (failure is CancellationException && failure !is TimeoutCancellationException) throw failure
        val reason = when (failure) {
            is TimeoutCancellationException -> "请求超时，请重新读取后再试"
            is LinkageError -> "当前 QQ 版本不支持此权限接口"
            else -> failure.message ?: "请求失败，请重试"
        }
        val message = if (awaitingConfirmation) "修改已提交但未确认，请重新读取；$reason" else reason
        error = message
        Toasts.error(message)
        Log.e("TroopAdminPermissionsMenu", failure)
    }

    suspend fun load(updateInput: Boolean) {
        val loaded = TroopAdminPermissionService.load(groupId.toLong(), memberUin.toLong())
        snapshot = loaded
        awaitingConfirmation = false
        if (updateInput) selectedLimit = loaded.maxKickPerDay.takeIf {
            it in AdminPermissionProtocol.kickLimits
        } ?: 0
        hasLoaded = true
    }

    fun refresh() {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try { load(updateInput = !hasLoaded) }
            catch (failure: Exception) { report(failure) }
            catch (failure: LinkageError) { report(failure) }
            finally { busy = false }
        }
    }

    fun change(permission: AdminPermission, enabled: Boolean, limit: Int) {
        if (busy || awaitingConfirmation) return
        busy = true
        error = null
        scope.launch {
            try {
                TroopAdminPermissionService.change(
                    groupId.toLong(), memberUin.toLong(), permission, enabled, limit
                )
                // Keep the form mounted while confirming the server result, including
                // its scroll position and any expanded controls.
                awaitingConfirmation = true
                load(updateInput = false)
                val actual = snapshot!!
                check(actual.enabled[permission] == enabled &&
                    (permission != AdminPermission.KICK || !enabled || actual.maxKickPerDay == limit)) {
                    "服务器返回的配置与提交值不同，请按当前显示值确认"
                }
                if (permission == AdminPermission.KICK && enabled) selectedLimit = actual.maxKickPerDay
                Toasts.success("已保存")
            } catch (failure: Exception) {
                report(failure)
            } catch (failure: LinkageError) {
                report(failure)
            } finally { busy = false }
        }
    }

    LaunchedEffect(groupId, memberUin) {
        try { load(updateInput = true) }
        catch (failure: Exception) { report(failure) }
        catch (failure: LinkageError) { report(failure) }
        finally { busy = false }
    }
    BackHandler { if (!busy) onBack() }

    DialogForm(
        title = "管理员权限",
        actions = {
            SecondaryButton(onClick = onBack, enabled = !busy, modifier = Modifier.weight(1f)) {
                Text("返回")
            }
            ActionButton(onClick = ::refresh, enabled = !busy, modifier = Modifier.weight(1f)) {
                Text("重新读取")
            }
        }
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                memberNick.ifBlank { memberUin },
                modifier = Modifier.weight(1f),
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
            Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                if (busy) CircularProgressIndicator(size = 28.dp, strokeWidth = 3.dp)
            }
        }
        if (snapshot == null) error?.let { Text(it, color = MiuixTheme.colorScheme.error) }
        snapshot?.let { current ->
            TCQTPanel {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    AdminPermission.entries.take(3).forEach { permission ->
                        PermissionSwitch(permission.title, current.enabled.getValue(permission), canEdit) {
                            change(permission, it, if (permission == AdminPermission.KICK) selectedLimit else current.maxKickPerDay)
                        }
                    }
                    val canKick = current.enabled.getValue(AdminPermission.KICK)
                    SecondaryButton(
                        onClick = { limitExpanded = !limitExpanded },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("每日可移除人数")
                            Text(
                                if (current.maxKickPerDay == 0) "不限制" else "${current.maxKickPerDay} 人 / 天",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary
                            )
                        }
                        Text(if (limitExpanded) "收起" else "展开")
                    }
                    if (limitExpanded) {
                        if (current.maxKickPerDay !in AdminPermissionProtocol.kickLimits) {
                            Text(
                                "当前服务器值：${current.maxKickPerDay} 人，请选择档位后保存",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary
                            )
                        }
                        ManagementWheelColumn(
                            label = "人 / 天",
                            value = AdminPermissionProtocol.kickLimits.indexOf(selectedLimit),
                            range = AdminPermissionProtocol.kickLimits.indices,
                            enabled = canEdit && canKick,
                            onValueChange = { selectedLimit = AdminPermissionProtocol.kickLimits[it] },
                            valueLabel = { index ->
                                val count = AdminPermissionProtocol.kickLimits[index]
                                if (count == 0) "不限制" else count.toString()
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        ActionButton(
                            onClick = { change(AdminPermission.KICK, true, selectedLimit) },
                            enabled = canEdit && canKick && selectedLimit != current.maxKickPerDay,
                            primary = true,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("保存人数") }
                    }
                }
            }
            SecondaryButton(onClick = { more = !more }, modifier = Modifier.fillMaxWidth()) {
                Text(if (more) "收起更多权限" else "更多权限")
            }
            if (more) TCQTPanel {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        "这些权限是否可修改由服务器决定",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                    AdminPermission.entries.drop(3).forEach { permission ->
                        PermissionSwitch(
                            permission.title, current.enabled.getValue(permission), canEdit,
                            description = permission.description
                        ) {
                            change(permission, it, current.maxKickPerDay)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionSwitch(
    title: String,
    checked: Boolean,
    enabled: Boolean,
    description: String? = null,
    onChange: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title)
            description?.let {
                Text(
                    it,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                )
            }
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = onChange)
    }
}
