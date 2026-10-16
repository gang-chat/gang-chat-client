package com.gangchat.client.app

import java.util.Objects

class AudioDeviceInfo(
    val deviceId: String,
    val label: String,
    val kind: String,
    val groupId: String = "",
) {
    override fun equals(other: Any?): Boolean {
        return other is AudioDeviceInfo &&
            other.deviceId == deviceId &&
            other.label == label &&
            other.kind == kind &&
            other.groupId == groupId
    }

    override fun hashCode(): Int = Objects.hash(deviceId, label, kind, groupId)

    override fun toString(): String {
        return "AudioDeviceInfo(deviceId: $deviceId, label: $label, kind: $kind)"
    }
}

fun audioDeviceInfoKind(device: AudioDeviceInfo): String = device.kind

fun audioDeviceInfoId(device: AudioDeviceInfo): String = device.deviceId

fun audioDeviceInfoLabel(device: AudioDeviceInfo): String = device.label

fun audioDeviceInfoGroupId(device: AudioDeviceInfo): String = device.groupId
