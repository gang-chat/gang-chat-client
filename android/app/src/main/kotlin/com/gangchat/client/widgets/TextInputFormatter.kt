package com.gangchat.client.widgets

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import java.text.BreakIterator

/**
 * Flutter's `TextInputFormatter`: rewrites a user edit before the text field
 * commits it. Fields run their formatters in order whenever an edit changes
 * the text (or commits the IME composing region), like Flutter's
 * `EditableText`.
 */
fun interface TextInputFormatter {
    fun formatEditUpdate(oldValue: TextFieldValue, newValue: TextFieldValue): TextFieldValue
}

/** Runs [formatters] over a field edit with Flutter's `EditableText` rules. */
fun applyTextInputFormatters(
    formatters: List<TextInputFormatter>?,
    oldValue: TextFieldValue,
    newValue: TextFieldValue,
): TextFieldValue {
    if (formatters.isNullOrEmpty()) return newValue
    val textChanged = oldValue.text != newValue.text
    val textCommitted = oldValue.composition != null && newValue.composition == null
    if (!textChanged && !textCommitted) return newValue
    return formatters.fold(newValue) { value, formatter -> formatter.formatEditUpdate(oldValue, value) }
}

/**
 * Flutter's `FilteringTextInputFormatter`: keeps ([allow] = true) or removes
 * ([allow] = false) the parts of the text matching [filterPattern], keeping
 * the selection on the same characters.
 */
class FilteringTextInputFormatter(
    val filterPattern: Regex,
    val allow: Boolean,
    val replacementString: String = "",
) : TextInputFormatter {
    override fun formatEditUpdate(oldValue: TextFieldValue, newValue: TextFieldValue): TextFieldValue {
        val text = newValue.text
        val kept = StringBuilder()
        // Maps every original offset (0..length) to its offset in the result.
        val offsetMap = IntArray(text.length + 1)
        var cursor = 0
        fun copyOriginal(until: Int) {
            while (cursor < until) {
                offsetMap[cursor] = kept.length
                kept.append(text[cursor])
                cursor += 1
            }
        }
        fun replaceUntil(until: Int) {
            val start = kept.length
            kept.append(replacementString)
            while (cursor < until) {
                offsetMap[cursor] = start
                cursor += 1
            }
        }
        val matches = filterPattern.findAll(text).filter { it.range.last >= it.range.first }.toList()
        if (allow) {
            for (match in matches) {
                if (match.range.first > cursor) replaceUntil(match.range.first)
                copyOriginal(match.range.last + 1)
            }
            if (cursor < text.length) replaceUntil(text.length)
        } else {
            for (match in matches) {
                copyOriginal(match.range.first)
                replaceUntil(match.range.last + 1)
            }
            copyOriginal(text.length)
        }
        offsetMap[text.length] = kept.length
        val result = kept.toString()
        if (result == text) return newValue
        fun map(offset: Int) = offsetMap[offset.coerceIn(0, text.length)]
        return TextFieldValue(
            text = result,
            selection = TextRange(map(newValue.selection.start), map(newValue.selection.end)),
            composition = null,
        )
    }

    companion object {
        /** Flutter's `FilteringTextInputFormatter.allow(pattern)`. */
        fun allow(filterPattern: Regex, replacementString: String = "") =
            FilteringTextInputFormatter(filterPattern, allow = true, replacementString = replacementString)

        /** Flutter's `FilteringTextInputFormatter.deny(pattern)`. */
        fun deny(filterPattern: Regex, replacementString: String = "") =
            FilteringTextInputFormatter(filterPattern, allow = false, replacementString = replacementString)

        /** Flutter's `FilteringTextInputFormatter.singleLineFormatter`. */
        val singleLineFormatter = deny(Regex("\n"))

        /** Flutter's `FilteringTextInputFormatter.digitsOnly`. */
        val digitsOnly = allow(Regex("[0-9]"))
    }
}

/**
 * Flutter's `LengthLimitingTextInputFormatter`: limits the text to
 * [maxLength] user-perceived characters (grapheme clusters). A null or
 * negative [maxLength] disables the limit.
 */
class LengthLimitingTextInputFormatter(val maxLength: Int?) : TextInputFormatter {
    override fun formatEditUpdate(oldValue: TextFieldValue, newValue: TextFieldValue): TextFieldValue {
        val limit = maxLength ?: return newValue
        if (limit < 0) return newValue
        if (characterCount(newValue.text) <= limit) return newValue
        // Keep the old value when it is already full and the edit only adds.
        if (characterCount(oldValue.text) == limit && oldValue.selection.collapsed) return oldValue
        val truncated = truncate(newValue.text, limit)
        return TextFieldValue(
            text = truncated,
            selection = TextRange(
                newValue.selection.start.coerceAtMost(truncated.length),
                newValue.selection.end.coerceAtMost(truncated.length),
            ),
            composition = null,
        )
    }

    companion object {
        fun characterCount(text: String): Int {
            if (text.isEmpty()) return 0
            val iterator = BreakIterator.getCharacterInstance()
            iterator.setText(text)
            var count = 0
            while (iterator.next() != BreakIterator.DONE) count += 1
            return count
        }

        fun truncate(text: String, maxLength: Int): String {
            val iterator = BreakIterator.getCharacterInstance()
            iterator.setText(text)
            var count = 0
            var end = 0
            while (count < maxLength) {
                val next = iterator.next()
                if (next == BreakIterator.DONE) break
                end = next
                count += 1
            }
            return text.substring(0, end)
        }
    }
}
