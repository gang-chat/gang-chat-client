package com.gangchat.client.home

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gangchat.client.widgets.BoxFit
import com.gangchat.client.widgets.FittedBox
import com.gangchat.client.widgets.Text

object CompactActivityLayout {
    val avatarSize: Dp = 18.dp
    val timestampColumnWidth: Dp = 82.dp
    val compactTimestampColumnWidth: Dp = 68.dp
    val compactTimestampContentGap: Dp = 4.dp
    val compactNotificationListHorizontalPadding: Dp = 16.dp

    fun splitTimestamp(timestamp: String): String {
        val splitAt = timestamp.indexOf(' ')
        if (splitAt < 0) return timestamp
        return "${timestamp.substring(0, splitAt)}\n${timestamp.substring(splitAt + 1)}"
    }
}

@Composable
fun CompactActivityTimestamp(timestamp: String, style: TextStyle, modifier: Modifier = Modifier) {
    val lines = CompactActivityLayout.splitTimestamp(timestamp).split("\n")
    FittedBox(modifier = modifier, fit = BoxFit.scaleDown, alignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            for (line in lines) Text(line, maxLines = 1, softWrap = false, style = style)
        }
    }
}
