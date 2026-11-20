package com.owo233.tcqt.loader.validation

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.owo233.tcqt.core.config.ModuleThemeMode
import com.owo233.tcqt.core.env.TCQTBuild
import com.owo233.tcqt.ui.component.AdaptiveContent
import com.owo233.tcqt.ui.component.horizontalSafePadding
import com.owo233.tcqt.ui.settings.FeatureSummaryCard
import com.owo233.tcqt.ui.settings.TopBar
import com.owo233.tcqt.ui.settings.aboutPage
import com.owo233.tcqt.ui.settings.rememberAboutGradient
import com.owo233.tcqt.ui.theme.SettingTheme
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

/** Renders production About content with synthetic host data; no host settings are opened. */
class SettingsAboutPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = if (intent.getBooleanExtra("landscape", false)) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        val dark = intent.getBooleanExtra("dark", false)
        val summary = intent.getBooleanExtra("summary", false)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
        setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, intent.getFloatExtra("fontScale", 1f))) {
                SettingTheme(
                    themeMode = if (dark) ModuleThemeMode.Dark else ModuleThemeMode.Light,
                    monetEnabled = intent.getBooleanExtra("monet", false),
                ) {
                    val scrollBehavior = MiuixScrollBehavior()
                    val listState = rememberLazyListState(initialFirstVisibleItemIndex = intent.getIntExtra("item", 0))
                    val aboutGradient = if (summary) null else rememberAboutGradient()
                    Scaffold(
                        modifier = aboutGradient?.background ?: Modifier,
                        containerColor = if (summary) MiuixTheme.colorScheme.surface else Color.Transparent,
                        topBar = {
                            TopBar(
                                title = if (summary) "设置" else "关于 ${TCQTBuild.APP_NAME}",
                                showBack = true,
                                isSearchActive = false,
                                searchQuery = "",
                                onBack = ::finish,
                                onSearchRequested = null,
                                onSearchClosed = {},
                                onSearchQueryChange = {},
                                isErrorOverviewActive = false,
                                isExportingBugReport = false,
                                onExportBugReportClick = {},
                                scrollBehavior = scrollBehavior,
                                compact = !summary,
                            )
                        },
                    ) { padding ->
                        AdaptiveContent { sidePadding ->
                            val aboutViewport = if (summary) Modifier else Modifier
                                .padding(top = padding.calculateTopPadding()).clipToBounds()
                            LazyColumn(
                                modifier = Modifier.fillMaxSize().then(aboutViewport).horizontalSafePadding()
                                    .nestedScroll(scrollBehavior.nestedScrollConnection).overScrollVertical(),
                                state = listState,
                                contentPadding = PaddingValues(
                                    start = sidePadding,
                                    end = sidePadding,
                                    top = if (summary) padding.calculateTopPadding() + 8.dp else 8.dp,
                                    bottom = padding.calculateBottomPadding() + 24.dp,
                                ),
                            ) {
                                if (summary) item {
                                    FeatureSummaryCard(enabledCount = 24, disabledCount = 63, errorCount = 0, onErrorClick = {})
                                } else aboutPage(
                                    hostVersion = "QQ 9.3.75 (42040) GuanWang",
                                    frameworkName = "Zygisk",
                                    titleModifier = aboutGradient?.title ?: Modifier,
                                    onSourceClick = {},
                                    onUpdatesClick = {},
                                    onUpdatesLongClick = {},
                                    onIssueClick = {},
                                    onGroupClick = {},
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
