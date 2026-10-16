package com.gangchat.client.app

enum class CloseBehavior { askEveryTime, minimizeToTray, exitProgram }

val defaultCloseBehavior: CloseBehavior = CloseBehavior.askEveryTime

/** Dart `extension CloseBehaviorStorage on CloseBehavior`. */
val CloseBehavior.storageValue: String
    get() {
        return when (this) {
            CloseBehavior.askEveryTime -> "ask_every_time"
            CloseBehavior.minimizeToTray -> "minimize_to_tray"
            CloseBehavior.exitProgram -> "exit_program"
        }
    }

fun closeBehaviorFromStorageValue(value: String?): CloseBehavior {
    return when (value) {
        "minimize_to_tray" -> CloseBehavior.minimizeToTray
        "exit_program" -> CloseBehavior.exitProgram
        else -> defaultCloseBehavior
    }
}

fun closeBehaviorLabel(behavior: CloseBehavior): String {
    return when (behavior) {
        CloseBehavior.askEveryTime -> "每次询问"
        CloseBehavior.minimizeToTray -> "最小化到托盘"
        CloseBehavior.exitProgram -> "直接退出程序"
    }
}

fun closeBehaviorDescription(behavior: CloseBehavior): String {
    return when (behavior) {
        CloseBehavior.askEveryTime -> "关闭窗口时先询问本次要后台运行还是退出账号。"
        CloseBehavior.minimizeToTray -> "关闭窗口时保留当前状态并在后台运行。"
        CloseBehavior.exitProgram -> "关闭窗口时离开语音、退出账号并结束程序。"
    }
}

open class CloseBehaviorStore {
    open suspend fun read(): CloseBehavior {
        throw NotImplementedError("CloseBehaviorStore.read must be implemented.")
    }

    open suspend fun write(behavior: CloseBehavior) {
        throw NotImplementedError("CloseBehaviorStore.write must be implemented.")
    }
}
