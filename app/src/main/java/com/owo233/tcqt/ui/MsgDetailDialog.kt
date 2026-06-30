package com.owo233.tcqt.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.ext.copyToClipboard
import java.util.Locale

class MsgDetailDialog(
    context: Context,
    private val title: String,
    private val jsonString: String
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
        val currentSearchTextColor: Color
    )

    private val darkSyntaxColors = SyntaxColors(
        keyColor = Color(0xFFFC618D),       // Pink
        stringColor = Color(0xFFFCE566),    // Light Yellow
        numberColor = Color(0xFF78E2E2),    // Light Blue/Cyan
        booleanColor = Color(0xFFB48EAD),   // Purple
        nullColor = Color(0xFFB48EAD),      // Purple
        searchHighlightColor = Color(0x66FFCC00), // Semi-transparent yellow
        searchTextColor = Color.White,
        currentSearchHighlightColor = Color(0xFFFF9800), // Bright Orange
        currentSearchTextColor = Color.Black
    )

    private val lightSyntaxColors = SyntaxColors(
        keyColor = Color(0xFFD33682),       // Pink
        stringColor = Color(0xFF859900),    // Olive Green
        numberColor = Color(0xFF2AA198),    // Cyan/Teal
        booleanColor = Color(0xFF268BD2),   // Blue
        nullColor = Color(0xFFB58900),      // Yellow-Brown
        searchHighlightColor = Color(0x7FFFCC00), // Semi-transparent yellow
        searchTextColor = Color.Black,
        currentSearchHighlightColor = Color(0xFFE65100), // Dark Orange
        currentSearchTextColor = Color.White
    )

    @Composable
    override fun DialogContent() {
        val isDark = HookEnv.isNightMode()
        val syntaxColors = if (isDark) darkSyntaxColors else lightSyntaxColors
        val lines = remember(jsonString) { jsonString.split("\n") }

        var searchQuery by remember { mutableStateOf("") }

        val matches = remember(searchQuery, lines) {
            if (searchQuery.isBlank()) {
                emptyList()
            } else {
                val list = mutableListOf<SearchMatch>()
                lines.forEachIndexed { lineIdx, line ->
                    val lowerLine = line.lowercase(Locale.getDefault())
                    val lowerQuery = searchQuery.lowercase(Locale.getDefault())
                    var startIdx = lowerLine.indexOf(lowerQuery)
                    while (startIdx != -1) {
                        list.add(SearchMatch(lineIdx, startIdx, startIdx + lowerQuery.length))
                        startIdx = lowerLine.indexOf(lowerQuery, startIdx + 1)
                    }
                }
                list
            }
        }

        var currentMatchIndex by remember(matches) {
            mutableStateOf(if (matches.isNotEmpty()) 0 else -1)
        }

        val lazyListState = rememberLazyListState()

        LaunchedEffect(currentMatchIndex) {
            if (currentMatchIndex in matches.indices) {
                val match = matches[currentMatchIndex]
                val targetIndex = maxOf(0, match.lineIndex - 3)
                lazyListState.animateScrollToItem(targetIndex)
            }
        }

        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(250))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = ::dismissWithAnimation
                    ),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .fillMaxHeight(0.85f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {} // Consume click
                        ),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // Title bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = title,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "共 ${lines.size} 行",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row {
                                IconButton(onClick = {
                                    context.copyToClipboard(jsonString)
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "复制全部",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(onClick = ::dismissWithAnimation) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "关闭",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
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
                            placeholder = { Text("搜索属性或内容...") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "搜索"
                                )
                            },
                            trailingIcon = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(end = 4.dp)
                                ) {
                                    if (searchQuery.isNotEmpty()) {
                                        Text(
                                            text = if (matches.isNotEmpty()) {
                                                "${currentMatchIndex + 1}/${matches.size}"
                                            } else {
                                                "无匹配"
                                            },
                                            fontSize = 12.sp,
                                            color = if (matches.isNotEmpty()) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.error
                                            },
                                            modifier = Modifier.padding(end = 8.dp)
                                        )

                                        IconButton(
                                            onClick = {
                                                if (matches.isNotEmpty()) {
                                                    currentMatchIndex = (currentMatchIndex - 1 + matches.size) % matches.size
                                                }
                                            },
                                            enabled = matches.isNotEmpty(),
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowUp,
                                                contentDescription = "上一个"
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                if (matches.isNotEmpty()) {
                                                    currentMatchIndex = (currentMatchIndex + 1) % matches.size
                                                }
                                            },
                                            enabled = matches.isNotEmpty(),
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowDown,
                                                contentDescription = "下一个"
                                            )
                                        }

                                        IconButton(
                                            onClick = { searchQuery = "" },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Clear,
                                                contentDescription = "清除搜索"
                                            )
                                        }
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // JSON Viewport
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = ButtonDefaults.outlinedButtonBorder(enabled = true)
                        ) {
                            SelectionContainer {
                                LazyColumn(
                                    state = lazyListState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    contentPadding = PaddingValues(vertical = 4.dp)
                                ) {
                                    itemsIndexed(lines) { lineIndex, line ->
                                        val formattedText = remember(line, searchQuery, currentMatchIndex, syntaxColors, matches) {
                                            formatJsonLine(
                                                line = line,
                                                searchQuery = searchQuery,
                                                lineIndex = lineIndex,
                                                matches = matches,
                                                currentMatchIndex = currentMatchIndex,
                                                syntaxColors = syntaxColors
                                            )
                                        }
                                        Text(
                                            text = formattedText,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Bottom Actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = ::dismissWithAnimation,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("确定")
                            }
                        }
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
        syntaxColors: SyntaxColors
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
                    withStyle(style = SpanStyle(color = syntaxColors.keyColor, fontWeight = FontWeight.Bold)) {
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
                    val actualValue = if (suffix.isNotEmpty()) trimmedValue.dropLast(1) else trimmedValue
                    
                    when {
                        actualValue.startsWith("\"") -> {
                            withStyle(style = SpanStyle(color = syntaxColors.stringColor)) {
                                append(actualValue)
                            }
                        }
                        actualValue == "true" || actualValue == "false" -> {
                            withStyle(style = SpanStyle(color = syntaxColors.booleanColor, fontWeight = FontWeight.Bold)) {
                                append(actualValue)
                            }
                        }
                        actualValue == "null" -> {
                            withStyle(style = SpanStyle(color = syntaxColors.nullColor, fontWeight = FontWeight.Bold)) {
                                append(actualValue)
                            }
                        }
                        actualValue.firstOrNull()?.isDigit() == true || actualValue.startsWith("-") -> {
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
                        actualValue.firstOrNull()?.isDigit() == true || actualValue.startsWith("-") -> {
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

                    val isCurrent = matches.indices.firstOrNull { idx ->
                        val m = matches[idx]
                        m.lineIndex == lineIndex && m.startChar == startIdx && m.endChar == endIdx
                    }?.let { it == currentMatchIndex } ?: false

                    addStyle(
                        style = SpanStyle(
                            background = if (isCurrent) syntaxColors.currentSearchHighlightColor else syntaxColors.searchHighlightColor,
                            color = if (isCurrent) syntaxColors.currentSearchTextColor else syntaxColors.searchTextColor
                        ),
                        start = startIdx,
                        end = endIdx
                    )

                    startIdx = lowerLine.indexOf(lowerQuery, startIdx + 1)
                }
            }
        }
    }
}
