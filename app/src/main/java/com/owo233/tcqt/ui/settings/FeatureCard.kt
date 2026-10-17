package com.owo233.tcqt.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField as OutlinedTextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Report
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun FeatureCard(
    item: FeatureItemUiState,
    searchQuery: String,
    onToggleExpanded: () -> Unit,
    onFeatureEnabledChange: (Boolean) -> Unit,
    onOptionValueChange: (String, Int) -> Unit,
    onSliderValueChange: (String, Int) -> Unit,
    onTextValueChange: (String, String) -> Unit,
    onClearError: () -> Unit,
    onFeatureClick: () -> Unit,
    forceExpanded: Boolean = false,
) {
    var searchExpanded by
        remember(item.key, searchQuery, forceExpanded) {
            mutableStateOf(forceExpanded)
        }
    val initReady = item.initReady
    val effectivelyExpanded =
        initReady && ((if (forceExpanded) searchExpanded else item.expanded) || item.error != null)
    val toggleDetails = {
        if (forceExpanded) {
            searchExpanded = !searchExpanded
        } else {
            onToggleExpanded()
        }
    }
    val feature = item.feature
    val hasDetails =
        initReady &&
            (item.error != null ||
                item.optionGroups.isNotEmpty() ||
                item.sliders.isNotEmpty() ||
                item.textAreas.isNotEmpty())
    val query = searchQuery.trim()
    val featureTitleColor =
        if (item.error != null) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onSurface
    val titleColor = BasicComponentDefaults.titleColor(color = featureTitleColor)
    val bottomAction: (@Composable () -> Unit)? =
        if (
            !initReady ||
                item.hasPending ||
                item.error != null ||
                (effectivelyExpanded && hasDetails)
        ) {
            {
                if (!initReady) {
                    ForcedDisabledHint()
                }
                if (initReady) {
                    FeaturePreferenceDetails(
                        item = item,
                        expanded = effectivelyExpanded,
                        onOptionValueChange = onOptionValueChange,
                        onSliderValueChange = onSliderValueChange,
                        onTextValueChange = onTextValueChange,
                        onClearError = onClearError,
                    )
                }
            }
        } else {
            null
        }

    Column(Modifier.fillMaxWidth()) {
        if (query.isNotBlank()) {
            BasicComponent(
                enabled = initReady,
                endActions = {
                    when (item.uiType) {
                        ActionUiType.ENTRY -> {
                            Icon(
                                imageVector = MiuixIcons.ChevronForward,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                                modifier = Modifier.size(20.dp),
                            )
                        }

                        else -> {
                            if (initReady && hasDetails && item.error == null) {
                                Icon(
                                    imageVector = MiuixIcons.ChevronForward,
                                    contentDescription =
                                        if (effectivelyExpanded) "收起详细设置" else "展开详细设置",
                                    tint = MiuixTheme.colorScheme.primary,
                                    modifier =
                                        Modifier.padding(end = 12.dp).size(20.dp).graphicsLayer {
                                            rotationZ = if (effectivelyExpanded) -90f else 90f
                                        },
                                )
                            }
                            Switch(
                                checked = if (initReady) item.enabled else false,
                                onCheckedChange = if (initReady) onFeatureEnabledChange else null,
                                enabled = initReady,
                            )
                        }
                    }
                },
                bottomAction = bottomAction,
                insideMargin = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                onClick =
                    when {
                        !initReady -> null
                        item.uiType == ActionUiType.ENTRY -> onFeatureClick
                        hasDetails && item.error == null -> toggleDetails
                        hasDetails -> null
                        else -> {
                            { onFeatureEnabledChange(!item.enabled) }
                        }
                    },
            ) {
                Text(
                    text = rememberHighlightedText(feature.label, query),
                    fontSize = MiuixTheme.textStyles.headline1.fontSize,
                    fontWeight = FontWeight.Medium,
                    color = featureTitleColor,
                )
                if (feature.desc.isNotBlank()) {
                    Text(
                        text = rememberHighlightedText(feature.desc, query),
                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    )
                }
            }
        } else if (item.uiType == ActionUiType.ENTRY) {
            ArrowPreference(
                title = feature.label,
                summary = feature.desc.takeIf(String::isNotBlank),
                titleColor = titleColor,
                bottomAction = bottomAction,
                insideMargin = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                onClick = if (initReady) onFeatureClick else null,
                enabled = initReady,
            )
        } else if (hasDetails) {
            BasicComponent(
                title = feature.label,
                summary = feature.desc.takeIf(String::isNotBlank),
                titleColor = titleColor,
                endActions = {
                    if (item.error == null) {
                        Icon(
                            imageVector = MiuixIcons.ChevronForward,
                            contentDescription = if (effectivelyExpanded) "收起详细设置" else "展开详细设置",
                            tint = MiuixTheme.colorScheme.primary,
                            modifier =
                                Modifier.padding(end = 12.dp).size(20.dp).graphicsLayer {
                                    rotationZ = if (effectivelyExpanded) -90f else 90f
                                },
                        )
                    }
                    Switch(
                        checked = item.enabled,
                        onCheckedChange = onFeatureEnabledChange,
                    )
                },
                bottomAction = bottomAction,
                insideMargin = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                onClick = if (item.error == null) toggleDetails else null,
                onClickLabel = if (effectivelyExpanded) "收起详细设置" else "展开详细设置",
            )
        } else {
            SwitchPreference(
                checked = if (initReady) item.enabled else false,
                onCheckedChange = onFeatureEnabledChange,
                title = feature.label,
                summary = feature.desc.takeIf(String::isNotBlank),
                titleColor = titleColor,
                bottomAction = bottomAction,
                insideMargin = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                enabled = initReady,
            )
        }
    }
}

@Composable
internal fun ForcedDisabledHint() {
    Text(
        text = "当前宿主环境不满足运行条件，此功能已被强制禁用。",
        style = MiuixTheme.textStyles.footnote2,
        color = MiuixTheme.colorScheme.error,
        modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
internal fun FeaturePreferenceDetails(
    item: FeatureItemUiState,
    expanded: Boolean,
    onOptionValueChange: (String, Int) -> Unit,
    onSliderValueChange: (String, Int) -> Unit,
    onTextValueChange: (String, String) -> Unit,
    onClearError: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (item.error != null) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StatusPill(
                    text = "运行异常",
                    containerColor = MiuixTheme.colorScheme.errorContainer,
                    contentColor = MiuixTheme.colorScheme.onErrorContainer,
                )
            }
        }

        AnimatedVisibility(
            visible =
                expanded &&
                    (item.error != null ||
                        item.optionGroups.isNotEmpty() ||
                        item.sliders.isNotEmpty() ||
                        item.textAreas.isNotEmpty())
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HorizontalDivider(
                    color =
                        if (item.error != null) {
                            MiuixTheme.colorScheme.error.copy(alpha = 0.24f)
                        } else {
                            MiuixTheme.colorScheme.dividerLine.copy(alpha = 0.45f)
                        }
                )
                item.error?.let { error ->
                    FeatureErrorPanel(error = error, onClear = onClearError)
                }
                item.optionGroups.forEach { group ->
                    OptionGroup(
                        group = group,
                        currentValue = item.optionValues[group.key] ?: group.fallbackValue,
                        onValueChange = { value -> onOptionValueChange(group.key, value) },
                    )
                }
                item.sliders.forEach { slider ->
                    FeatureSlider(
                        slider = slider,
                        onValueChange = { value -> onSliderValueChange(slider.key, value) },
                    )
                }
                item.textAreas.forEach { area ->
                    FeatureTextArea(
                        area = area,
                        repositionKey = item.hasPending,
                        onValueChange = { value -> onTextValueChange(area.key, value) },
                    )
                }
            }
        }

        if (item.hasPending) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StatusPill(
                    text = "未保存",
                    containerColor = MiuixTheme.colorScheme.secondaryContainer,
                    contentColor = MiuixTheme.colorScheme.onSecondaryContainer,
                )
            }
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

    OutlinedTextField(
        value = area.value,
        onValueChange = onValueChange,
        modifier =
            Modifier.fillMaxWidth().bringIntoViewRequester(bringIntoViewRequester).onFocusChanged {
                focusState ->
                isFocused = focusState.isFocused
            },
        label = area.label,
        useLabelAsPlaceholder = area.value.isBlank(),
        minLines = 3,
        cornerRadius = 14.dp,
    )
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
internal fun StatusPill(text: String, containerColor: Color, contentColor: Color) {
    TCQTPanel(
        cornerRadius = 6.dp,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MiuixTheme.textStyles.footnote2,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun OptionGroup(
    group: FeatureOptionGroup,
    currentValue: Int,
    onValueChange: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        if (group.title.isNotBlank()) {
            Text(
                group.title,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        group.options.forEachIndexed { index, option ->
            val mask = group.resolveMask(option, index)
            val selected =
                if (group.isMulti) (currentValue and mask) != 0 else currentValue == option.value
            if (group.isMulti) {
                CheckboxPreference(
                    title = option.label,
                    checked = selected,
                    onCheckedChange = { checked ->
                        onValueChange(
                            if (checked) currentValue or mask else currentValue and mask.inv()
                        )
                    },
                    insideMargin = PaddingValues(vertical = 12.dp),
                )
            } else {
                RadioButtonPreference(
                    title = option.label,
                    selected = selected,
                    onClick = { onValueChange(option.value) },
                    insideMargin = PaddingValues(vertical = 12.dp),
                )
            }
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
        insideMargin = PaddingValues(vertical = 8.dp),
    )
}
