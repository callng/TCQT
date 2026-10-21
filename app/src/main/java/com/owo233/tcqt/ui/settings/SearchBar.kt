package com.owo233.tcqt.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SearchBarDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Clear
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SearchBar(query: String, onQueryChange: (String) -> Unit, onBackClick: () -> Unit) {
    var editingValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(query, selection = TextRange(query.length)))
    }
    // External replacements (history/clear) move to the end. Edits from the IME retain
    // their selection and composition, including insertion in the middle of a query.
    val value = if (editingValue.text == query) editingValue
        else TextFieldValue(query, selection = TextRange(query.length))
    SideEffect {
        if (editingValue.text != query) editingValue = value
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBackClick) { Icon(MiuixIcons.Back, contentDescription = "返回") }
        Spacer(Modifier.width(4.dp))
        // Miuix 0.9.4 InputField only exposes String, so it cannot accept a selection.
        // Keep its expanded appearance and focus-on-entry behavior with an editable value.
        BasicTextField(
            value = value,
            onValueChange = {
                editingValue = it
                if (it.text != query) onQueryChange(it.text)
            },
            modifier = Modifier.weight(1f).focusRequester(focusRequester)
                .semantics { contentDescription = "搜索功能名称或描述" },
            singleLine = true,
            textStyle = MiuixTheme.textStyles.main.copy(
                fontWeight = FontWeight.Medium,
                color = LocalContentColor.current,
            ),
            cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier.fillMaxWidth().background(
                        MiuixTheme.colorScheme.surfaceContainerHigh, CircleShape,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Basic.Search,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurfaceContainerHigh,
                        modifier = Modifier.padding(
                            start = SearchBarDefaults.LeadingIconStartPadding,
                            end = SearchBarDefaults.LeadingIconEndPadding,
                        ),
                    )
                    Box(
                        modifier = Modifier.weight(1f).heightIn(min = SearchBarDefaults.InputFieldMinHeight),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        innerTextField()
                    }
                    if (query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }) {
                        Icon(MiuixIcons.Clear, contentDescription = "清除搜索")
                    }
                }
            },
        )
    }
}
