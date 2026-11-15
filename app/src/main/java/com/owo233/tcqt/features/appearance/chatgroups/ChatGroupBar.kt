package com.owo233.tcqt.features.appearance.chatgroups

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.owo233.tcqt.ui.component.CompatibleComposeView
import kotlinx.coroutines.isActive
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.roundToInt

internal fun createChatGroupBar(
    context: Context,
    session: ChatGroupSession,
    access: RecentChatAccess,
): FrameLayout {
    lateinit var bar: ChatGroupBarView
    bar = ChatGroupBarView(context) {
        val account = session.editingAccount()
        key(account) {
            ChatGroupTabs(
                tabs = session.tabs,
                selected = session.selected,
                empty = session.empty,
                onSelect = session::select,
                onManage = { ChatGroupDialog(context, session, access.catalog(session.source)).show() },
                onReorder = { session.saveOrder(it, account) },
                onDraggingChanged = { bar.reordering = it },
            )
        }
    }
    return bar
}

private class ChatGroupBarView(context: Context, content: @Composable () -> Unit) :
    CompatibleComposeView(context, followHostTheme = true, content = content) {
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var directionResolved = false
    private var protectGesture = false
    var reordering = false
        set(value) {
            field = value
            parent?.requestDisallowInterceptTouchEvent(value || protectGesture)
        }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                directionResolved = false
                protectGesture = true
                parent.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> if (!directionResolved && !reordering) {
                val dx = abs(event.x - downX)
                val dy = abs(event.y - downY)
                if (maxOf(dx, dy) > touchSlop) {
                    directionResolved = true
                    protectGesture = dx >= dy
                    parent.requestDisallowInterceptTouchEvent(protectGesture)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                protectGesture = false
                parent.requestDisallowInterceptTouchEvent(false)
            }
        }
        return super.dispatchTouchEvent(event)
    }

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        // Keep edge drags in this strip even when Compose cannot scroll farther.
        super.requestDisallowInterceptTouchEvent(disallowIntercept || protectGesture || reordering)
    }
}

@Composable
internal fun ChatGroupTabs(
    tabs: List<ChatGroupTab>,
    selected: String,
    empty: Boolean,
    onSelect: (String) -> Unit,
    onManage: () -> Unit,
    onReorder: (List<String>) -> Unit,
    onDraggingChanged: (Boolean) -> Unit = {},
) {
    val density = LocalDensity.current
    val fontScale = density.fontScale.coerceAtLeast(1f)
    val height = (48 * fontScale).dp
    val haptics = LocalHapticFeedback.current
    val list = rememberLazyListState()
    var displayed by remember(tabs) { mutableStateOf(tabs) }
    var dragged by remember(tabs) { mutableStateOf<ChatGroupTab?>(null) }
    var pointerX by remember { mutableFloatStateOf(0f) }
    var grabOffset by remember { mutableFloatStateOf(0f) }
    val saveOrder by rememberUpdatedState(onReorder)
    val notifyDragging by rememberUpdatedState(onDraggingChanged)
    DisposableEffect(Unit) { onDispose { notifyDragging(false) } }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BoxWithConstraints(Modifier.weight(1f).height(height).clipToBounds()) {
                val spacing = 6.dp
                val tabWidth = maxOf((64 * fontScale).dp, (maxWidth - spacing * (tabs.size - 1)) / tabs.size)
                val widthPx = with(density) { tabWidth.toPx() }
                val viewportPx = with(density) { maxWidth.toPx() }
                val stridePx = with(density) { (tabWidth + spacing).toPx() }
                val edgePx = with(density) { 32.dp.toPx() }
                val speedPx = with(density) { 520.dp.toPx() }

                fun moveDraggedTab() {
                    val id = dragged?.id ?: return
                    val from = displayed.indexOfFirst { it.id == id }
                    val scroll = list.firstVisibleItemIndex * stridePx + list.firstVisibleItemScrollOffset
                    val to = ((pointerX - grabOffset + scroll) / stridePx).roundToInt()
                        .coerceIn(0, displayed.lastIndex)
                    if (from == to) return
                    // Preserve the viewport instead of letting LazyRow follow its first item's key.
                    list.requestScrollToItem(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset)
                    displayed = displayed.toMutableList().apply { add(to, removeAt(from)) }
                }

                LaunchedEffect(selected) {
                    val index = displayed.indexOfFirst { it.id == selected }
                    if (index >= 0) list.animateScrollToItem(index, -((viewportPx - widthPx) / 2).roundToInt())
                }
                LaunchedEffect(dragged?.id, tabWidth) {
                    if (dragged == null) return@LaunchedEffect
                    var previous = withFrameNanos { it }
                    while (isActive) {
                        val now = withFrameNanos { it }
                        val seconds = (now - previous) / 1_000_000_000f
                        previous = now
                        val speed = when {
                            pointerX < edgePx -> -((edgePx - pointerX) / edgePx).coerceAtMost(1f)
                            pointerX > viewportPx - edgePx -> ((pointerX - viewportPx + edgePx) / edgePx).coerceAtMost(1f)
                            else -> 0f
                        }
                        if (speed != 0f) {
                            list.scrollBy(speed * speedPx * seconds)
                            moveDraggedTab()
                        }
                    }
                }

                LazyRow(
                    state = list,
                    modifier = Modifier.fillMaxSize()
                        .semantics { collectionInfo = CollectionInfo(1, displayed.size) }
                        .pointerInput(tabs, tabWidth) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { point ->
                                    val item = list.layoutInfo.visibleItemsInfo.firstOrNull {
                                        point.x >= it.offset && point.x < it.offset + it.size
                                    }
                                    if (item != null) {
                                        dragged = displayed.first { it.id == item.key }
                                        pointerX = point.x
                                        grabOffset = point.x - item.offset
                                        notifyDragging(true)
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                },
                                onDrag = { _, amount ->
                                    pointerX += amount.x
                                    moveDraggedTab()
                                },
                                onDragEnd = {
                                    if (dragged != null) {
                                        dragged = null
                                        notifyDragging(false)
                                        if (displayed != tabs) saveOrder(displayed.map { it.id })
                                    }
                                },
                                onDragCancel = {
                                    dragged = null
                                    displayed = tabs
                                    notifyDragging(false)
                                },
                            )
                        },
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                    overscrollEffect = null,
                ) {
                    itemsIndexed(displayed, key = { _, tab -> tab.id }) { index, tab ->
                        val isSelected = tab.id == selected
                        ChatGroupChip(
                            text = tab.title,
                            isSelected = isSelected,
                            onClick = { onSelect(tab.id) },
                            modifier = Modifier.width(tabWidth).height(height)
                                .animateItem(placementSpec = if (dragged?.id == tab.id) null else tween(140))
                                .graphicsLayer { alpha = if (dragged?.id == tab.id) 0f else 1f }
                                .semantics {
                                    role = Role.Tab
                                    this.selected = isSelected
                                    collectionItemInfo = CollectionItemInfo(0, 1, index, 1)
                                    customActions = buildList {
                                        if (index > 0) add(CustomAccessibilityAction("向前移动") {
                                            displayed = displayed.toMutableList().apply { add(index - 1, removeAt(index)) }
                                            saveOrder(displayed.map { it.id })
                                            true
                                        })
                                        if (index < displayed.lastIndex) add(CustomAccessibilityAction("向后移动") {
                                            displayed = displayed.toMutableList().apply { add(index + 1, removeAt(index)) }
                                            saveOrder(displayed.map { it.id })
                                            true
                                        })
                                    }
                                },
                        )
                    }
                }
                dragged?.let { tab ->
                    ChatGroupChip(
                        text = tab.title,
                        isSelected = tab.id == selected,
                        lifted = true,
                        onClick = {},
                        modifier = Modifier.offset { IntOffset((pointerX - grabOffset).roundToInt(), 0) }
                            .width(tabWidth).height(height).clearAndSetSemantics {},
                    )
                }
            }
            ChatGroupChip("管理", onClick = onManage, modifier = Modifier.height(height))
        }
        if (empty) {
            Text(
                "此分组暂无会话",
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(24.dp),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
            )
        }
    }
}

@Composable
private fun ChatGroupChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    lifted: Boolean = false,
) {
    val colors = MiuixTheme.colorScheme
    val corner = TabRowDefaults.TabRowCornerRadius
    Button(
        onClick = onClick,
        modifier = modifier.squircleBorder(
            width = { if (isSelected) 0.dp else 1.dp },
            color = { colors.outline },
            cornerRadius = corner,
        ),
        cornerRadius = corner,
        colors = ButtonDefaults.buttonColors(
            color = if (isSelected || lifted) colors.surfaceContainer else Color.Transparent,
            contentColor = if (isSelected) colors.onBackground else colors.onSurfaceVariantSummary,
        ),
        insideMargin = PaddingValues(horizontal = 12.dp),
        indication = null,
    ) {
        Text(text, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = MiuixTheme.textStyles.body1.fontSize, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
