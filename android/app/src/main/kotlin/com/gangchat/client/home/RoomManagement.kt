package com.gangchat.client.home

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gangchat.client.protocol.RoomDetail
import com.gangchat.client.ui.UiRadii

// Shared by the room settings and members dialogs (Dart's `room_management.dart`
// library and its parts).
internal val _dialogMaxWidth: Dp = 680.dp
internal val _dialogMaxHeight: Dp = 700.dp
internal val _panelRadius: Dp = UiRadii.lg
internal val _rowRadius: Dp = UiRadii.md

enum class RoomManagementResultKind { created, updated, left, deleted }

/**
 * What a room settings dialog ended with. Dart's named constructors
 * (`RoomManagementResult.created(room)`, `.updated(room)`, `.left()`,
 * `.deleted()`) are companion functions.
 */
class RoomManagementResult private constructor(val kind: RoomManagementResultKind, val room: RoomDetail? = null) {
    companion object {
        fun created(room: RoomDetail): RoomManagementResult = RoomManagementResult(RoomManagementResultKind.created, room)

        fun updated(room: RoomDetail): RoomManagementResult = RoomManagementResult(RoomManagementResultKind.updated, room)

        fun left(): RoomManagementResult = RoomManagementResult(RoomManagementResultKind.left)

        fun deleted(): RoomManagementResult = RoomManagementResult(RoomManagementResultKind.deleted)
    }
}
