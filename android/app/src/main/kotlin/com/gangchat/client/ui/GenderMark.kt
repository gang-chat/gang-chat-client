package com.gangchat.client.ui

import androidx.compose.ui.graphics.Color

data class GenderMarkData(val symbol: String, val color: Color)

fun genderMark(value: String?): GenderMarkData? =
    when (_nonEmpty(value)?.lowercase()) {
        "male", "m", "man" -> GenderMarkData(symbol = "♂", color = Color(0xFF5AA7FF))
        "female", "f", "woman" -> GenderMarkData(symbol = "♀", color = Color(0xFFFF6F8F))
        else -> null
    }

private fun _nonEmpty(value: String?): String? {
    val trimmed = value?.trim()
    if (trimmed.isNullOrEmpty()) return null
    return trimmed
}
