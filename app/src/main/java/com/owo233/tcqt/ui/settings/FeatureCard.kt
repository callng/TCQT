package com.owo233.tcqt.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.owo233.tcqt.core.action.ActionUiType
import com.owo233.tcqt.ui.component.ActionButton
import com.owo233.tcqt.ui.component.TCQTPanel
import com.owo233.tcqt.ui.component.textMatchRanges
import com.owo233.tcqt.ui.settings.model.FeatureErrorUiState
import com.owo233.tcqt.ui.settings.model.FeatureItemUiState
import com.owo233.tcqt.ui.settings.model.FeatureOptionGroup
import com.owo233.tcqt.ui.settings.model.FeatureSliderUiState
import com.owo233.tcqt.ui.settings.model.TextAreaUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField as OutlinedTextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.Report
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun FeatureCard(
    item: FeatureItemUiState,
    searchQuery: String,
    onOpenDetails: () -> Unit,
    onFeatureEnabledChange: (Boolean) -> Unit,
    onFeatureClick: () -> Unit,
) {
    val hasDetails = item.optionGroups.isNotEmpty() || item.sliders.isNotEmpty() ||
        item.textAreas.isNotEmpty() || item.error != null
    val isEntry = item.uiType == ActionUiType.ENTRY
    val opensPage = hasDetails || isEntry
    val onClick = when {
        hasDetails -> onOpenDetails
        isEntry -> onFeatureClick
        item.initReady -> { { onFeatureEnabledChange(!item.enabled) } }
        else -> null
    }
    val rowEnabled = item.initReady || item.error != null
    val colors = MiuixTheme.colorScheme
    val titleColor = if (rowEnabled) colors.onBackground else colors.disabledOnSecondaryVariant
    val summaryColor = if (rowEnabled) colors.onSurfaceVariantSummary else colors.disabledOnSecondaryVariant
    BasicComponent(
        enabled = rowEnabled,
        insideMargin = SettingsRowPadding,
        onClick = onClick,
        endActions = {
            if (opensPage) {
                if (!isEntry && item.initReady) {
                    Text(
                        text = if (item.enabled) "已开启" else "未开启",
                        style = MiuixTheme.textStyles.body2,
                        color = summaryColor,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                Icon(
                    MiuixIcons.Basic.ArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(width = 10.dp, height = 16.dp),
                    tint = if (rowEnabled) colors.onSurfaceVariantActions else colors.disabledOnSecondaryVariant,
                )
            } else {
                Switch(
                    checked = item.initReady && item.enabled,
                    enabled = item.initReady,
                    onCheckedChange = onFeatureEnabledChange,
                )
            }
        },
    ) {
        Text(
            text = rememberHighlightedText(item.feature.label, searchQuery),
            style = MiuixTheme.textStyles.headline1,
            color = titleColor,
        )
        if (item.feature.desc.isNotBlank()) {
            Text(
                text = rememberHighlightedText(item.feature.desc, searchQuery),
                style = MiuixTheme.textStyles.body2,
                color = summaryColor,
            )
        }
        if (!item.initReady || item.error != null || item.hasPending) {
            Text(
                text = when {
                    !item.initReady -> "当前宿主不支持"
                    item.error != null -> "运行异常 · 点击查看"
                    else -> "未保存"
                },
                style = MiuixTheme.textStyles.footnote2,
                color = if (item.error != null) MiuixTheme.colorScheme.error
                    else summaryColor,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
internal fun FeatureTextArea(
    area: TextAreaUiState,
    repositionKey: Any?,
    onValueChange: (String) -> Unit,
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(isFocused, repositionKey) {
        if (isFocused) {
            delay(32.milliseconds)
            bringIntoViewRequester.bringIntoView()
            delay(288.milliseconds)
            bringIntoViewRequester.bringIntoView()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = area.label,
            style = MiuixTheme.textStyles.headline1,
            color = MiuixTheme.colorScheme.onSurface,
        )
        OutlinedTextField(
            value = area.value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().bringIntoViewRequester(bringIntoViewRequester)
                .onFocusChanged { isFocused = it.isFocused },
            label = area.placeholder,
            useLabelAsPlaceholder = true,
            minLines = 3,
            cornerRadius = 14.dp,
        )
    }
}

@Composable
internal fun FeatureErrorPanel(error: FeatureErrorUiState, onClear: () -> Unit) {
    val occurredAt =
        remember(error.occurredAt) {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(Date(error.occurredAt))
        }

    TCQTPanel(
        cornerRadius = 16.dp,
        color = MiuixTheme.colorScheme.errorContainer.copy(alpha = 0.58f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = MiuixIcons.Report,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "异常日志",
                        style = MiuixTheme.textStyles.headline2,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.error,
                    )
                    Text(
                        text = "$occurredAt · ${error.processName} · ${error.stage}",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onErrorContainer.copy(alpha = 0.72f),
                    )
                }
                ActionButton(onClick = onClear) {
                    Text("清除")
                }
            }
            Text(
                text = error.summary,
                style = MiuixTheme.textStyles.body2,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onErrorContainer,
            )
            SelectionContainer {
                TCQTPanel(
                    cornerRadius = 10.dp,
                    color = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = error.details,
                        modifier = Modifier.padding(12.dp),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
internal fun rememberHighlightedText(text: String, keyword: String): AnnotatedString {
    val colorScheme = MiuixTheme.colorScheme
    return remember(text, keyword, colorScheme) {
        if (keyword.isBlank() || text.isBlank()) return@remember AnnotatedString(text)
        buildAnnotatedString {
            var cursor = 0
            textMatchRanges(text, keyword).forEach { range ->
                append(text.substring(cursor, range.first))
                pushStyle(SpanStyle(color = colorScheme.primary, fontWeight = FontWeight.Bold))
                append(text.substring(range.first, range.last + 1))
                pop()
                cursor = range.last + 1
            }
            append(text.substring(cursor))
        }
    }
}

@Composable
internal fun OptionGroup(
    group: FeatureOptionGroup,
    currentValue: Int,
    onValueChange: (Int) -> Unit,
) {
    if (!group.isMulti) {
        top.yukonga.miuix.kmp.preference.OverlayDropdownPreference(
            title = group.title,
            items = group.options.map { it.label },
            selectedIndex = group.options.indexOfFirst { it.value == currentValue }.coerceAtLeast(0),
            insideMargin = SettingsRowPadding,
            onSelectedIndexChange = { index -> onValueChange(group.options[index].value) },
        )
        return
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (group.title.isNotBlank()) {
            Text(
                text = group.title,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
        }
        group.options.forEachIndexed { index, option ->
            val mask = group.resolveMask(option, index)
            CheckboxPreference(
                title = option.label,
                checked = (currentValue and mask) != 0,
                onCheckedChange = { checked ->
                    onValueChange(if (checked) currentValue or mask else currentValue and mask.inv())
                },
                insideMargin = PaddingValues(vertical = 12.dp),
            )
        }
    }
}

@Composable
internal fun FeatureSlider(slider: FeatureSliderUiState, onValueChange: (Int) -> Unit) {
    val points =
        remember(slider.min, slider.max, slider.step) {
            if (slider.step > 1)
                ((slider.min..slider.max step slider.step).toList() + slider.max)
                    .distinct()
                    .map(Int::toFloat)
            else null
        }
    top.yukonga.miuix.kmp.preference.SliderPreference(
        title = slider.label,
        valueText = "${slider.value}${slider.suffix}",
        value = slider.value.toFloat().coerceIn(slider.min.toFloat(), slider.max.toFloat()),
        onValueChange = { onValueChange(it.roundToInt()) },
        valueRange = slider.min.toFloat()..slider.max.toFloat(),
        showKeyPoints = slider.step > 1,
        keyPoints = points,
        insideMargin = SettingsRowPadding,
    )
}
