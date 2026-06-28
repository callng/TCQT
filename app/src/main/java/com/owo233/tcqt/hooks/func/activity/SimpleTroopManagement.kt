package com.owo233.tcqt.hooks.func.activity

import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.activity.SettingTheme
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.copyToClipboard
import com.owo233.tcqt.hooks.base.Toasts
import com.owo233.tcqt.internals.QQInterfaces
import com.owo233.tcqt.loader.api.Chain
import com.owo233.tcqt.ui.CommonContextWrapper.Companion.toCompatibleContext
import com.owo233.tcqt.utils.api.GroupService
import com.owo233.tcqt.utils.dexkit.DexKitTask
import com.owo233.tcqt.utils.hook.hookReplace
import com.owo233.tcqt.utils.hook.invokeOriginal
import com.owo233.tcqt.utils.log.Log
import com.owo233.tcqt.utils.reflect.getObjectByType
import com.tencent.mobileqq.aio.msg.AIOMsgItem
import com.tencent.mobileqq.aio.msglist.holder.component.avatar.AIOAvatarContentComponent
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import com.tencent.qqnt.kernelpublic.nativeinterface.Contact
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.base.BaseMatcher
import android.graphics.Color as AndroidColor

@RegisterAction
class SimpleTroopManagement : IAction, DexKitTask {

    override val key: String get() = "simple_troop_management"
    override val name: String get() = "简易群管菜单"
    override val desc: String get() = "点击群聊对于群成员头像开启群管菜单，快速进行群成员相关操作。"
    override val uiTab: String get() = "界面"

    override fun onRun(app: Application, process: ActionProcess) {
        requireClass("onClick").getMethod(
            "onClick",
            View::class.java
        ).hookReplace { param ->
            val view = param.args[0] as View
            val component = param.thisObject.getObjectByType<AIOAvatarContentComponent>()
            val msgItem = component.getObjectByType<AIOMsgItem>()
            val msgRecord = msgItem.msgRecord

            if (msgRecord.chatType != 2) return@hookReplace param.invokeOriginal()

            val groupId = msgRecord.peerUin.toString()
            if (!GroupService.getGroupInfo(groupId).isOwnerOrAdmin) {
                return@hookReplace param.invokeOriginal()
            }

            val activity = view.context as? Activity ?: return@hookReplace param.invokeOriginal()

            showManagementSheet(
                activity,
                msgRecord,
                param
            )

            return@hookReplace null
        }
    }

    private fun showManagementSheet(
        activity: Activity,
        msgRecord: MsgRecord,
        param: Chain,
    ) {
        val troopUin = msgRecord.peerUin.toString()
        val memberUin = msgRecord.senderUin.toString()
        val memberUid = msgRecord.senderUid.toString()
        val nick = msgRecord.sendMemberName.ifEmpty { msgRecord.sendNickName }

        fun dismissAndRun(dismiss: () -> Unit, action: () -> Unit) {
            dismiss()
            runAction(action)
        }

        TCQTBottomDialog(activity) { dismiss ->
            TroopManagementContent(
                groupId = troopUin,
                memberUin = memberUin,
                memberNick = nick,
                memberUid = memberUid,
                onEnterProfile = {
                    dismissAndRun(dismiss) {
                        param.invokeOriginal()
                    }
                },
                onNoPermission = {
                    dismissAndRun(dismiss) {
                        param.invokeOriginal()
                    }
                },
                onRecall = {
                    dismissAndRun(dismiss) {
                        val contact = Contact(msgRecord.chatType, msgRecord.peerUid, msgRecord.guildId)
                        QQInterfaces.msgService.recallMsg(contact, arrayListOf(msgRecord.msgId)) { errCode, errMsg ->
                            val sucMsg = "已撤回该消息"
                            val failMsg = "撤回消息失败"
                            if (errCode != 0) {
                                Toasts.error("$failMsg, $errMsg ($errCode)")
                            } else {
                                Toasts.success(sucMsg)
                            }
                        }
                    }
                },
                onSetAdmin = {
                    dismissAndRun(dismiss) {
                        GroupService.modifyMemberRole(troopUin, memberUin, true)
                    }
                },
                onCancelAdmin = {
                    dismissAndRun(dismiss) {
                        GroupService.modifyMemberRole(troopUin, memberUin, false)
                    }
                },
                onSetMute = { duration ->
                    GroupService.setMemberShutUp(troopUin, memberUin, duration)
                },
                onCancelMute = {
                    dismissAndRun(dismiss) {
                        GroupService.setMemberShutUp(troopUin, memberUin, 0)
                    }
                },
                onSetTitle = { title ->
                    GroupService.setMemberTitle(troopUin, memberUin, title)
                },
                onSetCard = { card ->
                    GroupService.modifyMemberCardName(troopUin, memberUin, card)
                    msgRecord.sendMemberName = card
                },
                onKick = {
                    GroupService.kickMember(troopUin, memberUin, false)
                },
                onKickBlock = {
                    GroupService.kickMember(troopUin, memberUin, true)
                },
                onMuteAll = {
                    dismissAndRun(dismiss) {
                        GroupService.setGroupShutUp(troopUin, true)
                    }
                },
                onUnmuteAll = {
                    dismissAndRun(dismiss) {
                        GroupService.setGroupShutUp(troopUin, false)
                    }
                },
                getCurrentCard = { nick },
                onDismiss = dismiss
            )
        }.show()
    }

    private fun runAction(action: () -> Unit) {
        runCatching { action() }.onFailure { Log.e("SimpleTroopManagement runAction failed", it) }
    }

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        "onClick" to FindClass().apply {
            searchPackages("com.tencent.mobileqq.aio.msglist.holder.component.avatar")
            matcher {
                addInterface(View.OnClickListener::class.java.name)
                methods {
                    add { name("onClick") }
                }
            }
        }
    )
}

class TCQTDialogLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    init {
        savedStateRegistryController.performRestore(null)
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    fun handleLifecycleEvent(event: Lifecycle.Event) {
        if (lifecycleRegistry.currentState == Lifecycle.State.DESTROYED) {
            return
        }
        lifecycleRegistry.handleLifecycleEvent(event)
    }
}

@Suppress("DEPRECATION")
abstract class CompatibleComposeDialog(
    context: Context
) : Dialog(context.toCompatibleContext(), android.R.style.Theme_Material_Light_NoActionBar) {

    private val dialogLifecycleOwner = TCQTDialogLifecycleOwner()
    protected var isVisible by mutableStateOf(false)
    private var composeView: ComposeView? = null
    private var isDismissing = false

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setCanceledOnTouchOutside(true)
        configureWindow()
    }

    protected open fun configureWindow() {
        window?.apply {
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
            
            statusBarColor = AndroidColor.TRANSPARENT
            navigationBarColor = AndroidColor.TRANSPARENT

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                isNavigationBarContrastEnforced = false
                isStatusBarContrastEnforced = false
            }

            WindowCompat.setDecorFitsSystemWindows(this, false)

            setBackgroundDrawable(AndroidColor.TRANSPARENT.toDrawable())
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setGravity(Gravity.CENTER)
            
            // Clear default window dimming to draw and fade the dim background ourselves in Compose
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            
            // Remove default window animations to prevent conflicts with Compose transitions
            setWindowAnimations(0)
            
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dialogLifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(dialogLifecycleOwner)
            setViewTreeSavedStateRegistryOwner(dialogLifecycleOwner)
        }

        setContentView(composeView!!)

        window?.decorView?.let { decorView ->
            decorView.setViewTreeLifecycleOwner(dialogLifecycleOwner)
            decorView.setViewTreeSavedStateRegistryOwner(dialogLifecycleOwner)
        }

        composeView?.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides dialogLifecycleOwner) {
                SettingTheme(darkTheme = HookEnv.isNightMode(), dynamicColor = false) {
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        isVisible = true
                    }
                    DialogContent()
                }
            }
        }
    }

    @Composable
    protected abstract fun DialogContent()

    protected fun dismissWithAnimation() {
        dismiss()
    }

    override fun onStart() {
        super.onStart()
        dialogLifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        dialogLifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onStop() {
        dialogLifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        dialogLifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        dialogLifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onStop()
    }

    override fun dismiss() {
        if (!isDismissing) {
            isDismissing = true
            isVisible = false
            window?.decorView?.postDelayed({
                super.dismiss()
            }, 300)
        } else {
            composeView = null
            super.dismiss()
        }
    }
}

class TCQTBottomDialog(
    context: Context,
    private val content: @Composable (onDismiss: () -> Unit) -> Unit
) : CompatibleComposeDialog(context) {

    override fun configureWindow() {
        super.configureWindow()
        window?.setGravity(Gravity.BOTTOM)
    }

    @Composable
    override fun DialogContent() {
        TCQTBottomDialogWrapper(
            visible = isVisible,
            onDismiss = ::dismissWithAnimation,
            content = { content(::dismissWithAnimation) }
        )
    }
}

@Composable
private fun TCQTBottomDialogWrapper(
    visible: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300)),
        exit = fadeOut(tween(250))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(tween(300)) { it } + fadeIn(tween(200)),
                exit = slideOutVertically(tween(280)) { it } + fadeOut(tween(150))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {}
                ) {
                    content()
                }
            }
        }
    }
}

sealed interface MenuState {
    object MainMenu : MenuState
    data class InputDialog(
        val title: String,
        val label: String,
        val hint: String,
        val initialValue: String,
        val keyboardType: KeyboardType,
        val onConfirm: (String) -> Unit
    ) : MenuState
    data class ConfirmDialog(
        val title: String,
        val message: String,
        val onConfirm: () -> Unit
    ) : MenuState
}

@Composable
internal fun TroopManagementContent(
    groupId: String,
    memberUin: String,
    memberNick: String,
    memberUid: String,
    onEnterProfile: () -> Unit,
    onNoPermission: () -> Unit,
    onRecall: () -> Unit,
    onSetAdmin: () -> Unit,
    onCancelAdmin: () -> Unit,
    onSetMute: (Int) -> Unit,
    onCancelMute: () -> Unit,
    onSetTitle: (String) -> Unit,
    onSetCard: (String) -> Unit,
    onKick: () -> Unit,
    onKickBlock: () -> Unit,
    onMuteAll: () -> Unit,
    onUnmuteAll: () -> Unit,
    getCurrentCard: () -> String,
    onDismiss: () -> Unit
) {
    var menuState by remember { mutableStateOf<MenuState>(MenuState.MainMenu) }

    var roles by remember { mutableStateOf<TroopMemberRoles?>(null) }
    var uniqueTitle by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(groupId, memberUin) {
        isLoading = true
        try {
            val currentUin = QQInterfaces.currentUin
            val adminList = GroupService.getGroupAdminList(groupId)

            if (adminList.isNotEmpty()) {
                val owner = adminList.first()
                val admins = adminList.drop(1)

                val currentUserIsOwner = currentUin == owner
                val currentUserIsAdmin = admins.contains(currentUin)

                if (!currentUserIsOwner && !currentUserIsAdmin) {
                    onNoPermission()
                    return@LaunchedEffect
                }

                val isTargetOwner = memberUin == owner
                val isTargetAdmin = admins.contains(memberUin)

                roles = TroopMemberRoles(
                    currentUserIsOwner = currentUserIsOwner,
                    currentUserIsAdmin = currentUserIsAdmin,
                    isTargetOwner = isTargetOwner,
                    isTargetAdmin = isTargetAdmin
                )

                if (currentUserIsOwner) {
                    uniqueTitle = GroupService.getMemberTitle(groupId, memberUin)
                }
            }
        } catch (_: Exception) {
            // 静默异常
        } finally {
            isLoading = false
        }
    }

    AnimatedContent(
        targetState = menuState,
        transitionSpec = {
            if (targetState is MenuState.MainMenu) {
                (slideInHorizontally { -it } + fadeIn(tween(220)))
                    .togetherWith(slideOutHorizontally { it } + fadeOut(tween(180)))
            } else {
                (slideInHorizontally { it } + fadeIn(tween(220)))
                    .togetherWith(slideOutHorizontally { -it } + fadeOut(tween(180)))
            }
        },
        label = "menu_state_transition"
    ) { state ->
        when (state) {
            is MenuState.MainMenu -> {
                MainMenuView(
                    memberUin = memberUin,
                    memberNick = memberNick,
                    memberUid = memberUid,
                    roles = roles,
                    isLoading = isLoading,
                    onEnterProfile = onEnterProfile,
                    onRecall = onRecall,
                    onSetAdmin = onSetAdmin,
                    onCancelAdmin = onCancelAdmin,
                    onSetMute = {
                        menuState = MenuState.InputDialog(
                            title = "设置禁言时长",
                            label = "禁言时长（秒）",
                            hint = "请输入禁言秒数",
                            initialValue = "60",
                            keyboardType = KeyboardType.Number,
                            onConfirm = { input -> onSetMute(input.toIntOrNull() ?: 0) }
                        )
                    },
                    onCancelMute = onCancelMute,
                    onSetTitle = {
                        menuState = MenuState.InputDialog(
                            title = "设置群头衔",
                            label = "专属头衔",
                            hint = "请输入专属头衔",
                            initialValue = uniqueTitle,
                            keyboardType = KeyboardType.Text,
                            onConfirm = onSetTitle
                        )
                    },
                    onSetCard = {
                        menuState = MenuState.InputDialog(
                            title = "修改群名片",
                            label = "群名片",
                            hint = "请输入新名片",
                            initialValue = getCurrentCard(),
                            keyboardType = KeyboardType.Text,
                            onConfirm = onSetCard
                        )
                    },
                    onKick = {
                        menuState = MenuState.ConfirmDialog(
                            title = "确认移出群聊",
                            message = "确定要将此群员移出本群吗？此操作无法撤回。",
                            onConfirm = onKick
                        )
                    },
                    onKickBlock = {
                        menuState = MenuState.ConfirmDialog(
                            title = "确认拉黑并移出",
                            message = "确定要将此群员移出本群并拉黑吗？拉黑后该群员将无法再次申请入群。",
                            onConfirm = onKickBlock
                        )
                    },
                    onMuteAll = onMuteAll,
                    onUnmuteAll = onUnmuteAll
                )
            }
            is MenuState.InputDialog -> {
                InputMenuView(
                    title = state.title,
                    label = state.label,
                    hint = state.hint,
                    initialValue = state.initialValue,
                    keyboardType = state.keyboardType,
                    onBack = { menuState = MenuState.MainMenu },
                    onConfirm = { input ->
                        state.onConfirm(input)
                        onDismiss()
                    }
                )
            }
            is MenuState.ConfirmDialog -> {
                ConfirmMenuView(
                    title = state.title,
                    message = state.message,
                    onBack = { menuState = MenuState.MainMenu },
                    onConfirm = {
                        state.onConfirm()
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
private fun MainMenuView(
    memberUin: String,
    memberNick: String,
    memberUid: String,
    roles: TroopMemberRoles?,
    isLoading: Boolean,
    onEnterProfile: () -> Unit,
    onRecall: () -> Unit,
    onSetAdmin: () -> Unit,
    onCancelAdmin: () -> Unit,
    onSetMute: () -> Unit,
    onCancelMute: () -> Unit,
    onSetTitle: () -> Unit,
    onSetCard: () -> Unit,
    onKick: () -> Unit,
    onKickBlock: () -> Unit,
    onMuteAll: () -> Unit,
    onUnmuteAll: () -> Unit
) {
    val context = LocalContext.current
    val isDark = HookEnv.isNightMode()

    val customPrimary = MaterialTheme.colorScheme.primary
    val customError = MaterialTheme.colorScheme.error
    val customGreen = if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .navigationBarsPadding()
    ) {
        Text(
            text = "群管菜单",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(16.dp))

        InfoRow(label = "Uin", value = memberUin) {
            context.copyToClipboard(memberUin, false)
            Toasts.success("已复制Uin")
        }
        Spacer(modifier = Modifier.height(8.dp))
        InfoRow(label = "Uid", value = memberUid) {
            context.copyToClipboard(memberUid, false)
            Toasts.success("已复制Uid")
        }
        Spacer(modifier = Modifier.height(8.dp))
        InfoRow(label = "Name", value = memberNick) {
            context.copyToClipboard(memberNick, false)
            Toasts.success("已复制Name")
        }
        Spacer(modifier = Modifier.height(20.dp))

        AnimatedContent(
            targetState = isLoading,
            transitionSpec = {
                fadeIn(tween(200)) togetherWith fadeOut(tween(200))
            },
            label = "buttons_loading_transition"
        ) { loading ->
            if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp,
                            modifier = Modifier.width(36.dp).height(36.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "正在获取群管列表...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (roles != null) {
                val managementButtons = remember(
                    roles,
                    customPrimary,
                    customError,
                    customGreen
                ) {
                    buildList {
                        add(ManagementButtonData("打开资料页", customPrimary, onEnterProfile))

                        val canRecall = roles.currentUserIsOwner || (roles.currentUserIsAdmin && !roles.isTargetOwner && !roles.isTargetAdmin)
                        if (canRecall) {
                            add(ManagementButtonData("撤回群消息", customPrimary, onRecall))
                        }

                        if (roles.currentUserIsOwner && !roles.isTargetOwner) {
                            val showSetAdmin = !roles.isTargetAdmin
                            val showCancelAdmin = roles.isTargetAdmin

                            if (showSetAdmin) add(ManagementButtonData("设置管理员", customGreen, onSetAdmin))
                            if (showCancelAdmin) add(ManagementButtonData("取消管理员", customGreen, onCancelAdmin))
                        }

                        val canMute = (roles.currentUserIsOwner && !roles.isTargetOwner) || (roles.currentUserIsAdmin && !roles.isTargetOwner && !roles.isTargetAdmin)
                        if (canMute) {
                            add(ManagementButtonData("设置禁言", customPrimary, onSetMute))
                            add(ManagementButtonData("解除禁言", customPrimary, onCancelMute))
                        }

                        if (roles.currentUserIsOwner && HookEnv.isQQ()) { // TIM 看不到自定义的头衔
                            add(ManagementButtonData("设置头衔", customPrimary, onSetTitle))
                        }

                        val canEditCard = roles.currentUserIsOwner || roles.currentUserIsAdmin
                        if (canEditCard) {
                            add(ManagementButtonData("修改名片", customPrimary, onSetCard))
                        }

                        val canKick = (roles.currentUserIsOwner && !roles.isTargetOwner) || (roles.currentUserIsAdmin && !roles.isTargetOwner && !roles.isTargetAdmin)
                        if (canKick) {
                            add(ManagementButtonData("踢出本群", customError, onKick))
                            add(ManagementButtonData("踢出并拉黑", customError, onKickBlock))
                        }

                        val canShutUp = roles.currentUserIsOwner || roles.currentUserIsAdmin
                        if (canShutUp) {
                            add(ManagementButtonData("全员禁言", customError, onMuteAll))
                            add(ManagementButtonData("全员解禁", customGreen, onUnmuteAll))
                        }
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val chunks = managementButtons.chunked(2)
                    chunks.forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowItems.forEach { button ->
                                ManagementButton(
                                    text = button.text,
                                    color = button.color,
                                    onClick = button.onClick,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowItems.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                ) {
                    ManagementButton(
                        text = "打开资料页",
                        color = customPrimary,
                        onClick = onEnterProfile,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "获取群管列表失败~",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }
    }
}

@Composable
private fun InputMenuView(
    title: String,
    label: String,
    hint: String,
    initialValue: String,
    keyboardType: KeyboardType,
    onBack: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = {
                    keyboardController?.hide()
                    onBack()
                },
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Text("返回", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = text,
            onValueChange = {
                text = if (keyboardType == KeyboardType.Number) {
                    it.filter { c -> c.isDigit() }
                } else {
                    it
                }
            },
            label = { Text(label) },
            placeholder = { Text(hint) },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                keyboardController?.hide()
                onConfirm(text)
            },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("确定", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ConfirmMenuView(
    title: String,
    message: String,
    onBack: () -> Unit,
    onConfirm: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("取消", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.weight(1f)
            ) {
                Text("确定", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

internal data class ManagementButtonData(
    val text: String,
    val color: Color,
    val onClick: () -> Unit
)

@Composable
private fun InfoRow(label: String, value: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$label: ",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "📋",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ManagementButton(
    text: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

private data class TroopMemberRoles(
    val currentUserIsOwner: Boolean,
    val currentUserIsAdmin: Boolean,
    val isTargetOwner: Boolean,
    val isTargetAdmin: Boolean
)
