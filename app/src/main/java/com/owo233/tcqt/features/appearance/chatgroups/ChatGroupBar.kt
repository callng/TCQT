package com.owo233.tcqt.features.appearance.chatgroups

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.owo233.tcqt.core.env.Toasts
import com.owo233.tcqt.ui.component.CompatibleComposeView
import kotlinx.coroutines.isActive
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Messages
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowListPopup
import kotlin.math.abs
import kotlin.math.roundToInt

internal fun createChatGroupBar(
    context: Context,
    session: ChatGroupSession,
    access: RecentChatAccess,
): ChatGroupBarView {
    lateinit var bar: ChatGroupBarView
    bar = ChatGroupBarView(context) {
        val account = session.editingAccount()
        val hostColors = bar.hostColors
        val colors = MiuixTheme.colorScheme
        MiuixTheme(colors = if (hostColors == null) colors else colors.copy(
            surfaceContainer = hostColors.surface,
            primary = hostColors.foreground,
            onPrimary = hostColors.surface.copy(alpha = 1f),
            onSurfaceSecondary = hostColors.foreground,
            onSurfaceContainer = hostColors.foreground,
            onBackground = hostColors.foreground,
        )) {
            key(account) {
                ChatGroupTabs(
                    tabs = session.tabs,
                    selected = session.selected,
                    onSelect = session::select,
                    onEdit = { id ->
                        ChatGroupActivity.open(context, session, id)
                    },
                    onDelete = { id ->
                        ChatGroupDeleteDialog(context, session, id).show()
                    },
                    onMarkRead = { id ->
                        access.markRead(session.source, id, session.groups) { error ->
                            if (error != null) Toasts.error(error)
                        }
                    },
                    onReorder = { session.saveOrder(it, account) },
                    onDraggingChanged = { bar.reordering = it },
                )
            }
        }
    }
    return bar
}

internal data class ChatGroupHostColors(val surface: Color, val foreground: Color)

internal class ChatGroupBarView(context: Context, content: @Composable () -> Unit) :
    CompatibleComposeView(context, followHostTheme = true, content = content) {
    var hostColors by mutableStateOf<ChatGroupHostColors?>(null)
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
    onSelect: (String) -> Unit,
    onEdit: (String?) -> Unit,
    onDelete: (String) -> Unit,
    onMarkRead: (String) -> Unit,
    onReorder: (List<String>) -> Unit,
    onDraggingChanged: (Boolean) -> Unit = {},
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val titleStyle = MiuixTheme.textStyles.body1.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
    val counterStyle = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium)
    val textHeight = with(density) { textMeasurer.measure("聊天", titleStyle).size.height.toDp() }
    val pillHeight = maxOf(28.dp, textHeight + 8.dp)
    val height = maxOf(44.dp, pillHeight + 16.dp)
    val haptics = LocalHapticFeedback.current
    val list = rememberLazyListState()
    val ids = tabs.map { it.id }
    var displayedIds by remember(ids) { mutableStateOf(ids) }
    val displayed = displayedIds.map { id -> tabs.first { it.id == id } }
    var reordering by remember { mutableStateOf(false) }
    var dragged by remember(ids) { mutableStateOf<String?>(null) }
    var draggedWidth by remember { mutableFloatStateOf(0f) }
    var pointerX by remember { mutableFloatStateOf(0f) }
    var grabOffset by remember { mutableFloatStateOf(0f) }
    val saveOrder by rememberUpdatedState(onReorder)
    val notifyDragging by rememberUpdatedState(onDraggingChanged)
    DisposableEffect(reordering) {
        notifyDragging(reordering)
        onDispose { notifyDragging(false) }
    }

    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Box(Modifier.fillMaxWidth().height(height - 8.dp).align(Alignment.Center)
            .squircleBackground(MiuixTheme.colorScheme.surfaceContainer, (height - 8.dp) / 2, extension = 1f))
        Row(
            Modifier.fillMaxWidth().height(height).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BoxWithConstraints(Modifier.weight(1f).height(height).clipToBounds()) {
                val viewportPx = with(density) { maxWidth.toPx() }
                val edgePx = with(density) { 32.dp.toPx() }
                val speedPx = with(density) { 520.dp.toPx() }
                // Telegram FilterTabsView.onMeasure adds an equal share of spare width to each tab.
                val (fittedTabs, widths) = remember(tabs, maxWidth, titleStyle, density) {
                    fun tabWidth(tab: ChatGroupTab): Float {
                        val title = textMeasurer.measure(tab.title, titleStyle).size.width.toFloat()
                        val counter = if (tab.unreadCount > 0) {
                            maxOf(with(density) { 18.dp.toPx() },
                                textMeasurer.measure(tab.unreadCount.toString(), counterStyle).size.width + with(density) { 10.dp.toPx() }) +
                                with(density) { 6.dp.toPx() }
                        } else 0f
                        return title + counter + with(density) { 24.dp.toPx() }
                    }
                    val fullTitles = tabs.map { if (it.id == "all") it.copy(title = "所有") else it }
                    val fitted = if (fullTitles.sumOf { tabWidth(it).toDouble() } <= viewportPx) fullTitles else tabs
                    val natural = fitted.map(::tabWidth)
                    val extra = ((viewportPx - natural.sum()) / fitted.size).coerceAtLeast(0f)
                    fitted.associateBy { it.id } to fitted.mapIndexed { index, tab ->
                        tab.id to with(density) { (natural[index] + extra).toDp() }
                    }.toMap()
                }

                fun moveDraggedTab() {
                    val id = dragged ?: return
                    val from = displayedIds.indexOf(id)
                    val center = pointerX - grabOffset + draggedWidth / 2
                    val target = list.layoutInfo.visibleItemsInfo.firstOrNull {
                        it.key != id && if (it.index < from) center < it.offset + it.size / 2
                        else center > it.offset + it.size / 2
                    } ?: return
                    val to = target.index
                    if (from == to) return
                    // Preserve the viewport instead of letting LazyRow follow its first item's key.
                    list.requestScrollToItem(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset)
                    displayedIds = displayedIds.toMutableList().apply { add(to, removeAt(from)) }
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }

                LaunchedEffect(selected) {
                    val index = displayed.indexOfFirst { it.id == selected }
                    if (index >= 0) list.animateScrollToItem(index)
                }
                LaunchedEffect(dragged) {
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
                        .pointerInput(ids, reordering) {
                            if (!reordering) return@pointerInput
                            detectDragGesturesAfterLongPress(
                                onDragStart = { point ->
                                    val item = list.layoutInfo.visibleItemsInfo.firstOrNull {
                                        point.x >= it.offset && point.x < it.offset + it.size
                                    }
                                    if (item != null) {
                                        dragged = item.key as String
                                        draggedWidth = item.size.toFloat()
                                        pointerX = point.x
                                        grabOffset = point.x - item.offset
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
                                        if (displayedIds != ids) saveOrder(displayedIds)
                                    }
                                },
                                onDragCancel = {
                                    dragged = null
                                    displayedIds = ids
                                },
                            )
                        },
                    userScrollEnabled = dragged == null,
                    overscrollEffect = null,
                ) {
                    itemsIndexed(displayed, key = { _, tab -> tab.id }) { index, tab ->
                        val isSelected = tab.id == selected
                        ChatGroupChip(
                            tab = fittedTabs.getValue(tab.id),
                            titleStyle = titleStyle,
                            counterStyle = counterStyle,
                            pillHeight = pillHeight,
                            isSelected = isSelected,
                            reordering = reordering,
                            onClick = { if (!reordering) onSelect(tab.id) },
                            onReorder = { reordering = true },
                            onEdit = { onEdit(tab.id) },
                            onDelete = { onDelete(tab.id) },
                            onMarkRead = { onMarkRead(tab.id) },
                            modifier = Modifier.width(widths.getValue(tab.id)).height(height)
                                .animateItem(placementSpec = if (dragged == tab.id) null else tween(140))
                                .graphicsLayer { alpha = if (dragged == tab.id) 0f else 1f }
                                .semantics {
                                    role = Role.Tab
                                    this.selected = isSelected
                                    collectionItemInfo = CollectionItemInfo(0, 1, index, 1)
                                    customActions = buildList {
                                        if (index > 0) add(CustomAccessibilityAction("向前移动") {
                                            displayedIds = displayedIds.toMutableList().apply { add(index - 1, removeAt(index)) }
                                            saveOrder(displayedIds)
                                            true
                                        })
                                        if (index < displayed.lastIndex) add(CustomAccessibilityAction("向后移动") {
                                            displayedIds = displayedIds.toMutableList().apply { add(index + 1, removeAt(index)) }
                                            saveOrder(displayedIds)
                                            true
                                        })
                                    }
                                },
                        )
                    }
                }
                dragged?.let { id ->
                    ChatGroupChip(
                        tab = fittedTabs.getValue(id),
                        titleStyle = titleStyle,
                        counterStyle = counterStyle,
                        pillHeight = pillHeight,
                        isSelected = id == selected,
                        lifted = true,
                        reordering = true,
                        onClick = {},
                        modifier = Modifier.offset { IntOffset((pointerX - grabOffset).roundToInt(), 0) }
                            .width(with(density) { draggedWidth.toDp() }).height(height).clearAndSetSemantics {},
                    )
                }
            }
            if (reordering) {
                Box(Modifier.size(height).squircleClip(height / 2, extension = 1f)
                    .clickable(role = Role.Button, onClickLabel = "完成排序") { reordering = false },
                    contentAlignment = Alignment.Center) {
                    Icon(MiuixIcons.Basic.Check, contentDescription = "完成排序",
                        tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

@Composable
private fun ChatGroupChip(
    tab: ChatGroupTab,
    titleStyle: TextStyle,
    counterStyle: TextStyle,
    pillHeight: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    lifted: Boolean = false,
    reordering: Boolean = false,
    onReorder: () -> Unit = {},
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    onMarkRead: () -> Unit = {},
) {
    val colors = MiuixTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    var menu by remember { mutableStateOf(false) }
    var action by remember { mutableStateOf<(() -> Unit)?>(null) }
    val background by animateColorAsState(
        if (isSelected || lifted || menu) colors.primary.copy(alpha = 0.12f) else colors.surfaceContainer.copy(alpha = 0f),
        tween(160), label = "folder selection",
    )
    Box(modifier.combinedClickable(interactionSource = interactionSource, indication = null, onClick = onClick,
        onLongClickLabel = "文件夹菜单", onLongClick = if (reordering) null else ({ menu = true }))) {
        Row(
            Modifier.fillMaxWidth().height(pillHeight).align(Alignment.Center)
                .squircleSurface(background, pillHeight / 2, extension = 1f)
                .indication(interactionSource, LocalIndication.current)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        ) {
            Text(tab.title, style = titleStyle,
                color = if (isSelected || menu) colors.primary else colors.onSurfaceSecondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (tab.unreadCount > 0) {
                Box(Modifier.heightIn(min = 18.dp).widthIn(min = 18.dp)
                    .squircleBackground(if (isSelected || menu) colors.primary else colors.onSurfaceSecondary.copy(alpha = 0.14f),
                        9.dp, extension = 1f).padding(horizontal = 5.dp),
                    contentAlignment = Alignment.Center) {
                    Text(tab.unreadCount.toString(), style = counterStyle,
                        color = if (isSelected || menu) colors.onPrimary else colors.onSurfaceSecondary)
                }
            }
        }
        WindowListPopup(show = menu, onDismissRequest = { menu = false }, onDismissFinished = {
            action?.invoke()
            action = null
        }) {
            ListPopupColumn {
                FolderMenuItem("重新排序", MiuixIcons.Sort) { action = onReorder; menu = false }
                FolderMenuItem("编辑文件夹", MiuixIcons.Edit) { action = onEdit; menu = false }
                if (tab.unreadCount > 0) {
                    FolderMenuItem("全部标记为已读", MiuixIcons.Messages) { action = onMarkRead; menu = false }
                }
                if (tab.id.startsWith("custom:")) {
                    FolderMenuItem("删除文件夹", MiuixIcons.Delete, destructive = true) { action = onDelete; menu = false }
                }
            }
        }
    }
}

@Composable
private fun FolderMenuItem(text: String, icon: ImageVector, destructive: Boolean = false, onClick: () -> Unit) {
    val color = if (destructive) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onSurfaceContainer
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick)
        .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Text(text, color = color, fontSize = 16.sp)
    }
}
