package com.owo233.tcqt.ui.troopmanagement

import androidx.compose.ui.text.input.KeyboardType

internal const val MUTE_MIN_SECONDS = 1
internal const val MUTE_MAX_SECONDS = 2592000
private const val SECONDS_PER_DAY = 86400
private const val SECONDS_PER_HOUR = 3600
private const val SECONDS_PER_MINUTE = 60

internal data class MuteDurationParts(
    val days: Int,
    val hours: Int,
    val minutes: Int,
    val seconds: Int
) {
    val totalSeconds: Int
        get() = days * SECONDS_PER_DAY + hours * SECONDS_PER_HOUR + minutes * SECONDS_PER_MINUTE + seconds

    fun toDisplayString(): String = buildList {
        if (days > 0) add("${days}天")
        if (hours > 0) add("${hours}小时")
        if (minutes > 0) add("${minutes}分钟")
        if (seconds > 0) add("${seconds}秒")
    }.takeIf { it.isNotEmpty() }?.joinToString(" ") ?: "0秒"
}

internal fun Int.toMuteDurationParts(): MuteDurationParts {
    val total = coerceIn(MUTE_MIN_SECONDS, MUTE_MAX_SECONDS)
    val days = total / SECONDS_PER_DAY
    val hours = (total % SECONDS_PER_DAY) / SECONDS_PER_HOUR
    val minutes = (total % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    val seconds = total % SECONDS_PER_MINUTE
    return MuteDurationParts(days, hours, minutes, seconds)
}

internal sealed interface MenuState {
    data object Main : MenuState

    data class MuteDuration(
        val initialValue: Int,
        val onConfirm: (Int) -> Unit
    ) : MenuState

    data class Input(
        val title: String,
        val label: String,
        val hint: String,
        val initialValue: String,
        val keyboardType: KeyboardType,
        val onConfirm: (String) -> Unit
    ) : MenuState

    data class Confirm(
        val title: String,
        val message: String,
        val onConfirm: () -> Unit
    ) : MenuState
}

internal data class TroopMemberRoles(
    val currentUserIsOwner: Boolean,
    val currentUserIsAdmin: Boolean,
    val isTargetOwner: Boolean,
    val isTargetAdmin: Boolean
)

internal data class TroopManagementPermissions(
    val canRecall: Boolean,
    val canSetAdmin: Boolean,
    val canCancelAdmin: Boolean,
    val canMute: Boolean,
    val canSetTitle: Boolean,
    val canEditCard: Boolean,
    val canKick: Boolean,
    val canMuteAll: Boolean
)

internal fun TroopMemberRoles.permissions(isQQ: Boolean): TroopManagementPermissions {
    val canManageTarget =
        currentUserIsOwner || (currentUserIsAdmin && !isTargetOwner && !isTargetAdmin)

    return TroopManagementPermissions(
        canRecall = canManageTarget,
        canSetAdmin = currentUserIsOwner && !isTargetOwner && !isTargetAdmin,
        canCancelAdmin = currentUserIsOwner && !isTargetOwner && isTargetAdmin,
        canMute = canManageTarget,
        canSetTitle = currentUserIsOwner && isQQ,
        canEditCard = currentUserIsOwner || currentUserIsAdmin,
        canKick = canManageTarget,
        canMuteAll = currentUserIsOwner || currentUserIsAdmin
    )
}
