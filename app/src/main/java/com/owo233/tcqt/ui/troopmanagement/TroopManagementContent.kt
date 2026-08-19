package com.owo233.tcqt.ui.troopmanagement

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import com.owo233.tcqt.internals.QQInterfaces
import com.owo233.tcqt.utils.api.GroupService

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
    var menuState by remember { mutableStateOf<MenuState>(MenuState.Main) }

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
            if (targetState is MenuState.Main) {
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
            is MenuState.Main -> {
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
                        menuState = MenuState.MuteDuration(
                            initialValue = MUTE_MAX_SECONDS - 1,
                            onConfirm = onSetMute
                        )
                    },
                    onCancelMute = onCancelMute,
                    onSetTitle = {
                        menuState = MenuState.Input(
                            title = "设置群头衔",
                            label = "专属头衔",
                            hint = "请输入专属头衔",
                            initialValue = uniqueTitle,
                            keyboardType = KeyboardType.Text,
                            onConfirm = onSetTitle
                        )
                    },
                    onSetCard = {
                        menuState = MenuState.Input(
                            title = "修改群名片",
                            label = "群名片",
                            hint = "请输入新名片",
                            initialValue = getCurrentCard(),
                            keyboardType = KeyboardType.Text,
                            onConfirm = onSetCard
                        )
                    },
                    onKick = {
                        menuState = MenuState.Confirm(
                            title = "确认移出群聊",
                            message = "确定要将此群员移出本群吗？此操作无法撤回。",
                            onConfirm = onKick
                        )
                    },
                    onKickBlock = {
                        menuState = MenuState.Confirm(
                            title = "确认拉黑并移出",
                            message = "确定要将此群员移出本群并拉黑吗？拉黑后该群员将无法再次申请入群。",
                            onConfirm = onKickBlock
                        )
                    },
                    onMuteAll = onMuteAll,
                    onUnmuteAll = onUnmuteAll
                )
            }
            is MenuState.MuteDuration -> {
                MuteDurationMenuView(
                    initialValue = state.initialValue,
                    onBack = { menuState = MenuState.Main },
                    onConfirm = { seconds ->
                        state.onConfirm(seconds)
                        onDismiss()
                    }
                )
            }
            is MenuState.Input -> {
                InputMenuView(
                    title = state.title,
                    label = state.label,
                    hint = state.hint,
                    initialValue = state.initialValue,
                    keyboardType = state.keyboardType,
                    onBack = { menuState = MenuState.Main },
                    onConfirm = { input ->
                        state.onConfirm(input)
                        onDismiss()
                    }
                )
            }
            is MenuState.Confirm -> {
                ConfirmMenuView(
                    title = state.title,
                    message = state.message,
                    onBack = { menuState = MenuState.Main },
                    onConfirm = {
                        state.onConfirm()
                        onDismiss()
                    }
                )
            }
        }
    }
}
