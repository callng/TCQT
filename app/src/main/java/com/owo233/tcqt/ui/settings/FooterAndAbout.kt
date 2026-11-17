package com.owo233.tcqt.ui.settings

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.owo233.tcqt.core.env.TCQTBuild
import com.owo233.tcqt.ui.component.AdaptiveContent
import com.owo233.tcqt.ui.component.PreferenceSegment
import com.owo233.tcqt.ui.component.horizontalSafePadding
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal fun LazyListScope.aboutPage(
    hostVersion: String,
    frameworkName: String,
    titleModifier: Modifier,
    onSourceClick: () -> Unit,
    onUpdatesClick: () -> Unit,
    onUpdatesLongClick: () -> Unit,
    onIssueClick: () -> Unit,
    onGroupClick: () -> Unit,
) {
    item(key = "about_header") {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 96.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = TCQTBuild.APP_NAME,
                modifier = titleModifier,
                style = MiuixTheme.textStyles.title1,
                color = MiuixTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "${TCQTBuild.VER_NAME} (${TCQTBuild.VER_CODE})",
                modifier = Modifier.padding(top = 8.dp),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
    settingsGroupTitle("关于")
    item(key = "about_source") {
        PreferenceSegment(first = true, last = false) {
            ArrowPreference(
                title = "查看源代码",
                insideMargin = SettingsRowPadding,
                onClick = onSourceClick,
            )
        }
    }
    item(key = "about_updates") {
        PreferenceSegment(first = false, last = false) {
            ArrowPreference(
                title = "获取更新",
                insideMargin = SettingsRowPadding,
                modifier = Modifier.combinedClickable(
                    onClick = onUpdatesClick,
                    onLongClick = onUpdatesLongClick,
                    onLongClickLabel = "打开 CI 频道",
                ),
            )
        }
    }
    item(key = "about_feedback") {
        PreferenceSegment(first = false, last = false) {
            ArrowPreference(
                title = "反馈问题",
                insideMargin = SettingsRowPadding,
                modifier = Modifier.combinedClickable(
                    onClick = onIssueClick,
                    onLongClick = onGroupClick,
                    onLongClickLabel = "打开 Telegram 群组",
                ),
            )
        }
    }
    item(key = "about_group") {
        PreferenceSegment(first = false, last = true) {
            ArrowPreference(
                title = "TG 群组",
                insideMargin = SettingsRowPadding,
                onClick = onGroupClick,
            )
        }
    }
    settingsGroupTitle("构建与运行")
    item(key = "about_build_time") {
        PreferenceSegment(first = true, last = false) {
            BasicComponent(
                title = "构建时间",
                summary = TCQTBuild.BUILD_TIME,
                insideMargin = SettingsRowPadding,
            )
        }
    }
    item(key = "about_build_type") {
        PreferenceSegment(first = false, last = false) {
            BasicComponent(
                title = "构建类型",
                summary = if (TCQTBuild.DEBUG) "Debug" else "Release",
                insideMargin = SettingsRowPadding,
            )
        }
    }
    item(key = "about_host") {
        PreferenceSegment(first = false, last = false) {
            BasicComponent(
                title = "宿主版本",
                summary = hostVersion,
                insideMargin = SettingsRowPadding,
            )
        }
    }
    item(key = "about_framework") {
        PreferenceSegment(first = false, last = true) {
            BasicComponent(
                title = "注入方式",
                summary = frameworkName,
                insideMargin = SettingsRowPadding,
            )
        }
    }
}

/** A primary action measured within the page width instead of an unbounded FAB. */
@Composable
internal fun SaveChangesBar(pendingCount: Int, onClick: () -> Unit) {
    val hapticFeedback = LocalHapticFeedback.current
    AdaptiveContent { sidePadding ->
        Button(
            modifier = Modifier.fillMaxWidth().horizontalSafePadding()
                .padding(horizontal = sidePadding, vertical = 12.dp),
            colors = ButtonDefaults.buttonColorsPrimary(),
            minHeight = 52.dp,
            cornerRadius = 16.dp,
            onClick = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                onClick()
            },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = MiuixIcons.Ok,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MiuixTheme.colorScheme.onPrimary,
                )
                Text(
                    text = "保存更改（$pendingCount）",
                    style = MiuixTheme.textStyles.button,
                    color = MiuixTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}
