package com.gangchat.client.app

typealias AudioDeviceKindOf<T> = (device: T) -> String
typealias AudioDeviceIdOf<T> = (device: T) -> String
typealias AudioDeviceLabelOf<T> = (device: T) -> String
typealias AudioDeviceGroupIdOf<T> = (device: T) -> String

fun audioDeviceKey(kind: String, deviceId: String): String {
    return "$kind:$deviceId"
}

fun <T> audioDeviceKeyOf(
    device: T,
    kindOf: AudioDeviceKindOf<T>,
    deviceIdOf: AudioDeviceIdOf<T>,
): String {
    return audioDeviceKey(kind = kindOf(device), deviceId = deviceIdOf(device))
}

fun canStartAudioDeviceSelection(busyDeviceId: String?): Boolean {
    return busyDeviceId == null
}

fun <T> audioDeviceBusy(
    device: T,
    busyDeviceId: String?,
    kindOf: AudioDeviceKindOf<T>,
    deviceIdOf: AudioDeviceIdOf<T>,
): Boolean {
    return busyDeviceId ==
        audioDeviceKeyOf(device, kindOf = kindOf, deviceIdOf = deviceIdOf)
}

@Suppress("UNUSED_PARAMETER")
fun audioDevicePreferenceSaveFailureMessage(error: Any): String {
    return "无法保存音频设备偏好"
}

fun <T> isSameAudioDevice(
    left: T?,
    right: T,
    kindOf: AudioDeviceKindOf<T>,
    deviceIdOf: AudioDeviceIdOf<T>,
): Boolean {
    return left != null &&
        deviceIdOf(left) == deviceIdOf(right) &&
        kindOf(left) == kindOf(right)
}

fun <T> audioDevicesByKind(
    devices: List<T>,
    kind: String,
    kindOf: AudioDeviceKindOf<T>,
): List<T> {
    return devices.filter { device -> kindOf(device) == kind }
}

fun <T> selectedAudioDeviceFrom(
    devices: List<T>,
    candidates: Iterable<T?>,
    kindOf: AudioDeviceKindOf<T>,
    deviceIdOf: AudioDeviceIdOf<T>,
): T? {
    if (devices.isEmpty()) return null
    for (candidate in candidates) {
        if (candidate == null) continue
        for (device in devices) {
            if (isSameAudioDevice(
                    candidate,
                    device,
                    kindOf = kindOf,
                    deviceIdOf = deviceIdOf,
                )
            ) {
                return device
            }
        }
    }
    return devices.first()
}

fun <T> audioDeviceLabels(
    devices: List<T>,
    fallbackLabel: String,
    labelOf: AudioDeviceLabelOf<T>,
    deviceIdOf: AudioDeviceIdOf<T>,
): List<String> {
    val baseLabels =
        devices.mapIndexed { index, device ->
            audioDeviceLabel(
                label = labelOf(device),
                deviceId = deviceIdOf(device),
                index = index,
                fallbackLabel = fallbackLabel,
            )
        }
    val totals = LinkedHashMap<String, Int>()
    for (label in baseLabels) {
        totals[label] = (totals[label] ?: 0) + 1
    }
    val seen = LinkedHashMap<String, Int>()
    return baseLabels.map { label ->
        if (totals[label] == 1) label else _labelWithDuplicateSuffix(label, seen)
    }
}

fun audioDeviceLabel(
    label: String,
    deviceId: String,
    index: Int,
    fallbackLabel: String,
): String {
    val trimmed = label.trim()
    if (trimmed.isNotEmpty()) return trimmed
    if (deviceId == "default") return "系统默认"
    if (deviceId == "communications") return "通信设备"
    return "$fallbackLabel ${index + 1}"
}

fun audioInputTestTooltip(testing: Boolean): String {
    return if (testing) "停止输入测试" else "测试输入音量"
}

fun audioOutputTestTooltip(testing: Boolean): String {
    return if (testing) "停止输出测试" else "测试输出音量"
}

private fun _labelWithDuplicateSuffix(label: String, seen: MutableMap<String, Int>): String {
    val count = (seen[label] ?: 0) + 1
    seen[label] = count
    if (count == 1) return label
    return "$label #$count"
}
