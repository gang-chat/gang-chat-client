package com.gangchat.client.widgets

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import com.gangchat.client.foundation.ChangeNotifier
import com.gangchat.client.foundation.ValueListenable

/**
 * Flutter's `TextEditingController`: the source of truth for a text field's
 * text, selection and IME composing range. Bind it with
 * `BasicTextField(value = controller.value, onValueChange = controller::updateFromField)`;
 * programmatic edits (`controller.text = ...`) update the field and notify
 * listeners synchronously, like Flutter.
 */
open class TextEditingController(text: String = "") : ChangeNotifier(), ValueListenable<TextFieldValue> {
    private var current by mutableStateOf(TextFieldValue(text, TextRange(text.length)))

    override var value: TextFieldValue
        get() = current
        set(newValue) {
            if (newValue == current) return
            current = newValue
            notifyListeners()
        }

    /** Setting text moves the cursor to the end and clears composing, like Flutter. */
    var text: String
        get() = current.text
        set(newText) {
            value = TextFieldValue(newText, TextRange(newText.length))
        }

    var selection: TextRange
        get() = current.selection
        set(newSelection) {
            value = current.copy(selection = newSelection)
        }

    val composing: TextRange? get() = current.composition

    fun clear() {
        value = TextFieldValue("")
    }

    /**
     * Flutter's `buildTextSpan` override point: the styled text a field shows
     * for [text] (spans over the field's [style]). Null, the default, shows
     * the text plain.
     */
    open fun buildTextSpan(text: String, style: TextStyle): AnnotatedString? = null

    /** `onValueChange` handler for `BasicTextField`. */
    fun updateFromField(newValue: TextFieldValue) {
        value = newValue
    }
}

/**
 * Flutter's `FocusNode`. Attach with [Modifier.focusNode]; [hasFocus] updates
 * and listeners fire when focus changes. [requestFocus] before attachment is
 * deferred until the node is attached.
 */
class FocusNode(val debugLabel: String? = null) : ChangeNotifier() {
    val requester = FocusRequester()
    internal var focusManager: FocusManager? = null
    internal var attached = false
    private var pendingRequest = false

    var hasFocus: Boolean = false
        private set

    /** Flutter's `hasPrimaryFocus` (no nested focus scopes in the port). */
    val hasPrimaryFocus: Boolean get() = hasFocus

    fun requestFocus() {
        if (!attached) {
            pendingRequest = true
            return
        }
        try {
            requester.requestFocus()
        } catch (error: IllegalStateException) {
            pendingRequest = true
        }
    }

    fun unfocus() {
        pendingRequest = false
        if (hasFocus) focusManager?.clearFocus(force = true)
    }

    internal fun onAttached() {
        attached = true
        if (pendingRequest) {
            pendingRequest = false
            runCatching { requester.requestFocus() }
        }
    }

    internal fun onFocusChanged(focused: Boolean) {
        if (hasFocus == focused) return
        hasFocus = focused
        notifyListeners()
    }
}

/** Connects a [FocusNode] to the focusable it modifies. */
fun Modifier.focusNode(node: FocusNode): Modifier =
    composed {
        node.focusManager = LocalFocusManager.current
        androidx.compose.runtime.DisposableEffect(node) {
            node.onAttached()
            onDispose { node.attached = false }
        }
        this.focusRequester(node.requester).onFocusChanged { node.onFocusChanged(it.isFocused || it.hasFocus) }
    }

/** Flutter's `FocusScope.of(context).unfocus()`. */
object FocusScope {
    fun unfocus(context: BuildContext) {
        context[LocalFocusManager].clearFocus(force = true)
    }
}
