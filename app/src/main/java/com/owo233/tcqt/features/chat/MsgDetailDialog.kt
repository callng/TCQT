package com.owo233.tcqt.features.chat

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.owo233.tcqt.core.env.copyToClipboard
import com.owo233.tcqt.ui.component.CompatibleComposeDialog
import com.owo233.tcqt.ui.component.HostDialog
import com.owo233.tcqt.ui.component.TCQTPanel
import com.owo233.tcqt.ui.component.textMatchRanges
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Clear
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.ExpandLess
import top.yukonga.miuix.kmp.icon.extended.ExpandMore
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale
import top.yukonga.miuix.kmp.basic.TextField as OutlinedTextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults as OutlinedTextFieldDefaults

class MsgDetailDialog(
    context: Context,
    private val title: String,
    private val jsonString: String,
) : CompatibleComposeDialog(context) {

    data class SearchMatch(val lineIndex: Int, val startChar: Int, val endChar: Int)

    data class SyntaxColors(
        val keyColor: Color,
        val stringColor: Color,
        val numberColor: Color,
        val booleanColor: Color,
        val nullColor: Color,
        val searchHighlightColor: Color,
        val searchTextColor: Color,
        val currentSearchHighlightColor: Color,
        val currentSearchTextColor: Color,
    )

    @Composable
    override fun DialogContent() {
        val colors = MiuixTheme.colorScheme
        val syntaxColors =
            SyntaxColors(
                keyColor = colors.primary,
                stringColor = colors.onSurfaceSecondary,
                numberColor = colors.onSurface,
                booleanColor = colors.primary,
                nullColor = colors.onSurfaceSecondary,
                searchHighlightColor = colors.secondaryContainer,
                searchTextColor = colors.onSecondaryContainer,
                currentSearchHighlightColor = colors.primary,
                currentSearchTextColor = colors.onPrimary,
            )
        val lines = remember(jsonString) { jsonString.split("\n") }

        var searchQuery by remember { mutableStateOf("") }

        val matches =
            remember(searchQuery, lines) {
                if (searchQuery.isBlank()) {
                    emptyList()
                } else {
                    val list = mutableListOf<SearchMatch>()
                    lines.forEachIndexed { lineIdx, line ->
                        textMatchRanges(line, searchQuery).forEach { range ->
                            list.add(SearchMatch(lineIdx, range.first, range.last + 1))
                        }
                    }
                    list
                }
            }

        var currentMatchIndex by
            remember(matches) {
                mutableIntStateOf(if (matches.isNotEmpty()) 0 else -1)
            }

        val lazyListState = rememberLazyListState()

        LaunchedEffect(matches, currentMatchIndex) {
            if (currentMatchIndex in matches.indices) {
                val match = matches[currentMatchIndex]
                val targetIndex = maxOf(0, match.lineIndex - 3)
                lazyListState.animateScrollToItem(targetIndex)
            }
        }

        HostDialog(
            show = isVisible,
            onDismissRequest = { dismissWithAnimation() },
            onDismissFinished = ::dismissImmediately,
        ) {
            Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(24.dp)) {
                // Title bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = title,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "共 ${lines.size} 行",
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                        )
                    }
                    Row {
                        IconButton(
                            onClick = {
                                context.copyToClipboard(jsonString)
                            }
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Copy,
                                contentDescription = "复制全部",
                                tint = MiuixTheme.colorScheme.primary,
                            )
                        }
                        IconButton(onClick = ::dismissWithAnimation) {
                            Icon(
                                imageVector = MiuixIcons.Close,
                                contentDescription = "关闭",
                                tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "搜索属性或内容...",
                    useLabelAsPlaceholder = true,
                    leadingIcon = {
                        Icon(
                            imageVector = MiuixIcons.Search,
                            contentDescription = "搜索",
                        )
                    },
                    trailingIcon = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 4.dp),
                        ) {
                            if (searchQuery.isNotEmpty()) {
                                Text(
                                    text =
                                        if (matches.isNotEmpty()) {
                                            "${currentMatchIndex + 1}/${matches.size}"
                                        } else {
                                            "无匹配"
                                        },
                                    fontSize = 12.sp,
                                    color =
                                        if (matches.isNotEmpty()) {
                                            MiuixTheme.colorScheme.primary
                                        } else {
                                            MiuixTheme.colorScheme.error
                                        },
                                    modifier = Modifier.padding(end = 8.dp),
                                )

                                IconButton(
                                    onClick = {
                                        if (matches.isNotEmpty()) {
                                            currentMatchIndex =
                                                (currentMatchIndex - 1 + matches.size) %
                                                    matches.size
                                        }
                                    },
                                    enabled = matches.isNotEmpty(),
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        imageVector = MiuixIcons.ExpandLess,
                                        contentDescription = "上一个",
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        if (matches.isNotEmpty()) {
                                            currentMatchIndex =
                                                (currentMatchIndex + 1) % matches.size
                                        }
                                    },
                                    enabled = matches.isNotEmpty(),
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        imageVector = MiuixIcons.ExpandMore,
                                        contentDescription = "下一个",
                                    )
                                }

                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        imageVector = MiuixIcons.Clear,
                                        contentDescription = "清除搜索",
                                    )
                                }
                            }
                        }
                    },
                    singleLine = true,
                    cornerRadius = 12.dp,
                    colors =
                        OutlinedTextFieldDefaults.textFieldColors(
                            backgroundColor =
                                MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            borderColor = MiuixTheme.colorScheme.primary,
                        ),
                )

                Spacer(modifier = Modifier.height(16.dp))

                // JSON Viewport
                TCQTPanel(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    cornerRadius = 12.dp,
                    color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ) {
                    SelectionContainer {
                        LazyColumn(
                            state = lazyListState,
                            modifier =
                                Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                            contentPadding = PaddingValues(vertical = 4.dp),
                        ) {
                            itemsIndexed(lines) { lineIndex, line ->
                                val formattedText =
                                    remember(
                                        line,
                                        searchQuery,
                                        currentMatchIndex,
                                        syntaxColors,
                                        matches,
                                    ) {
                                        formatJsonLine(
                                            line = line,
                                            searchQuery = searchQuery,
                                            lineIndex = lineIndex,
                                            matches = matches,
                                            currentMatchIndex = currentMatchIndex,
                                            syntaxColors = syntaxColors,
                                        )
                                    }
                                Text(
                                    text = formattedText,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(
                        onClick = ::dismissWithAnimation,
                        cornerRadius = 10.dp,
                    ) {
                        Text("确定")
                    }
                }
            }
        }
    }

    private fun formatJsonLine(
        line: String,
        searchQuery: String,
        lineIndex: Int,
        matches: List<SearchMatch>,
        currentMatchIndex: Int,
        syntaxColors: SyntaxColors,
    ): AnnotatedString {
        return buildAnnotatedString {
            val colonIndex = line.indexOf(':')
            if (colonIndex != -1) {
                val keyPart = line.substring(0, colonIndex + 1)
                val valuePart = line.substring(colonIndex + 1)

                // Format Key
                val keyQuoteStart = keyPart.indexOf('"')
                val keyQuoteEnd = keyPart.lastIndexOf('"')
                if (keyQuoteStart != -1 && keyQuoteEnd != -1 && keyQuoteStart < keyQuoteEnd) {
                    append(keyPart.substring(0, keyQuoteStart))
                    withStyle(
                        style =
                            SpanStyle(color = syntaxColors.keyColor, fontWeight = FontWeight.Bold)
                    ) {
                        append(keyPart.substring(keyQuoteStart, keyQuoteEnd + 1))
                    }
                    append(keyPart.substring(keyQuoteEnd + 1))
                } else {
                    append(keyPart)
                }

                // Format Value
                val trimmedValue = valuePart.trim()
                if (trimmedValue.isNotEmpty()) {
                    val valueStartOffset = valuePart.indexOf(trimmedValue)
                    append(valuePart.substring(0, valueStartOffset)) // Whitespace before value

                    val suffix = if (trimmedValue.endsWith(",")) "," else ""
                    val actualValue =
                        if (suffix.isNotEmpty()) trimmedValue.dropLast(1) else trimmedValue

                    when {
                        actualValue.startsWith("\"") -> {
                            withStyle(style = SpanStyle(color = syntaxColors.stringColor)) {
                                append(actualValue)
                            }
                        }
                        actualValue == "true" || actualValue == "false" -> {
                            withStyle(
                                style =
                                    SpanStyle(
                                        color = syntaxColors.booleanColor,
                                        fontWeight = FontWeight.Bold,
                                    )
                            ) {
                                append(actualValue)
                            }
                        }
                        actualValue == "null" -> {
                            withStyle(
                                style =
                                    SpanStyle(
                                        color = syntaxColors.nullColor,
                                        fontWeight = FontWeight.Bold,
                                    )
                            ) {
                                append(actualValue)
                            }
                        }
                        actualValue.firstOrNull()?.isDigit() == true ||
                            actualValue.startsWith("-") -> {
                            withStyle(style = SpanStyle(color = syntaxColors.numberColor)) {
                                append(actualValue)
                            }
                        }
                        else -> {
                            append(actualValue)
                        }
                    }
                    if (suffix.isNotEmpty()) {
                        append(suffix)
                    }
                } else {
                    append(valuePart)
                }
            } else {
                val trimmed = line.trim()
                if (trimmed.isNotEmpty()) {
                    val startOffset = line.indexOf(trimmed)
                    append(line.substring(0, startOffset))

                    val suffix = if (trimmed.endsWith(",")) "," else ""
                    val actualValue = if (suffix.isNotEmpty()) trimmed.dropLast(1) else trimmed

                    when {
                        actualValue.startsWith("\"") -> {
                            withStyle(style = SpanStyle(color = syntaxColors.stringColor)) {
                                append(actualValue)
                            }
                        }
                        actualValue.firstOrNull()?.isDigit() == true ||
                            actualValue.startsWith("-") -> {
                            withStyle(style = SpanStyle(color = syntaxColors.numberColor)) {
                                append(actualValue)
                            }
                        }
                        else -> {
                            append(actualValue)
                        }
                    }
                    if (suffix.isNotEmpty()) {
                        append(suffix)
                    }
                } else {
                    append(line)
                }
            }

            // Overlay search highlights
            if (searchQuery.isNotEmpty()) {
                val lowerLine = line.lowercase(Locale.getDefault())
                val lowerQuery = searchQuery.lowercase(Locale.getDefault())
                var startIdx = lowerLine.indexOf(lowerQuery)
                while (startIdx != -1) {
                    val endIdx = startIdx + lowerQuery.length

                    val isCurrent =
                        matches.indices
                            .firstOrNull { idx ->
                                val m = matches[idx]
                                m.lineIndex == lineIndex &&
                                    m.startChar == startIdx &&
                                    m.endChar == endIdx
                            }
                            ?.let { it == currentMatchIndex } ?: false

                    addStyle(
                        style =
                            SpanStyle(
                                background =
                                    if (isCurrent) syntaxColors.currentSearchHighlightColor
                                    else syntaxColors.searchHighlightColor,
                                color =
                                    if (isCurrent) syntaxColors.currentSearchTextColor
                                    else syntaxColors.searchTextColor,
                            ),
                        start = startIdx,
                        end = endIdx,
                    )

                    startIdx = lowerLine.indexOf(lowerQuery, startIdx + 1)
                }
            }
        }
    }
}
