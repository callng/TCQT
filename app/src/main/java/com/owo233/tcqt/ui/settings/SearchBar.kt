package com.owo233.tcqt.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Clear

@Composable
internal fun SearchBar(query: String, onQueryChange: (String) -> Unit, onBackClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBackClick) { Icon(MiuixIcons.Back, contentDescription = "返回") }
        Spacer(Modifier.width(4.dp))
        InputField(
            query = query,
            onQueryChange = onQueryChange,
            onSearch = {},
            expanded = true,
            onExpandedChange = { if (!it) onBackClick() },
            modifier = Modifier.weight(1f),
            label = "搜索功能名称或描述",
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }) {
                    Icon(MiuixIcons.Clear, contentDescription = "清除搜索")
                }
            },
        )
    }
}
