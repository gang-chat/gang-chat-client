package com.gangchat.client.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.gangchat.client.app.ComposerAttachmentStatus
import com.gangchat.client.app.ComposerAttachmentView
import com.gangchat.client.app.FileMessageProgressHandler
import com.gangchat.client.app.FileMessageStatePatch
import com.gangchat.client.app.MediaCacheRequest
import com.gangchat.client.app.MessageMentionOption
import com.gangchat.client.app.MessageMentionQuery
import com.gangchat.client.app.StickerPanelSource
import com.gangchat.client.app.VoiceRecorderState
import com.gangchat.client.app.activeMessageMentionQuery
import com.gangchat.client.app.canSendComposedMessage
import com.gangchat.client.app.clipboardFilesReadFailureMessage
import com.gangchat.client.app.clipboardImageUploadFilename
import com.gangchat.client.app.composerMessageSendPlan
import com.gangchat.client.app.fileAttachmentTitle
import com.gangchat.client.app.fileEmptyMessage
import com.gangchat.client.app.isImageMimeType
import com.gangchat.client.app.kSystemMessageType
import com.gangchat.client.app.messageClipboardText
import com.gangchat.client.app.messageClipboardTextMatches
import com.gangchat.client.app.messageComponentClipboardText
import com.gangchat.client.app.messageComponentFromClipboardText
import com.gangchat.client.app.messageMentionDescriptors
import com.gangchat.client.app.messageMentionInsertText
import com.gangchat.client.app.messageMentionOptions
import com.gangchat.client.app.messageQuoteSnapshot
import com.gangchat.client.app.normalizedFilePaths
import com.gangchat.client.app.roomDraftPreviewText
import com.gangchat.client.app.sendComposerMessagePartsSequentially
import com.gangchat.client.app.shouldLoadStickerPanel
import com.gangchat.client.app.stickerMessageDraft
import com.gangchat.client.app.stickerPanelCachedPersonalApplied
import com.gangchat.client.app.stickerPanelLoadFailed
import com.gangchat.client.app.stickerPanelLoadFinished
import com.gangchat.client.app.stickerPanelLoadStarted
import com.gangchat.client.app.stickerPanelLoadSucceeded
import com.gangchat.client.app.stickerPanelSourceChanged
import com.gangchat.client.app.stickerPreviewFilename
import com.gangchat.client.app.userFacingErrorMessage
import com.gangchat.client.app.userPrimaryName
import com.gangchat.client.app.voiceMessageFilename
import com.gangchat.client.app.voiceRecordingCancelled
import com.gangchat.client.app.voiceRecordingReachedLimit
import com.gangchat.client.app.voiceRecordingStarted
import com.gangchat.client.app.voiceRecordingStopped
import com.gangchat.client.app.voiceRecordingTicked
import com.gangchat.client.app.voiceSendFailed
import com.gangchat.client.app.voiceSendStarted
import com.gangchat.client.app.voiceSendSucceeded
import com.gangchat.client.foundation.DateTime
import com.gangchat.client.foundation.Uri
import com.gangchat.client.foundation.delayed
import com.gangchat.client.foundation.future
import com.gangchat.client.foundation.http.HttpClient
import com.gangchat.client.foundation.http.get
import com.gangchat.client.foundation.unawaited
import com.gangchat.client.foundation.waitAll
import com.gangchat.client.plugins.PathProvider
import com.gangchat.client.protocol.ApiException
import com.gangchat.client.protocol.Message
import com.gangchat.client.protocol.MessageAttachment
import com.gangchat.client.protocol.MessageQuote
import com.gangchat.client.protocol.MusicBoxSearchResult
import com.gangchat.client.protocol.PersonalMusicPlaylistApi
import com.gangchat.client.protocol.RoomDetail
import com.gangchat.client.protocol.RoomMusicPlaylistApi
import com.gangchat.client.protocol.SharedMusicPlaylist
import com.gangchat.client.protocol.SharedMusicPlaylistCloneApi
import com.gangchat.client.protocol.SharedMusicTrack
import com.gangchat.client.protocol.Sticker
import com.gangchat.client.protocol.StickerPack
import com.gangchat.client.protocol.UploadCancelledException
import com.gangchat.client.protocol.UploadedAsset
import com.gangchat.client.protocol.UserSummary
import com.gangchat.client.protocol.MusicTrackPreviewApi
import com.gangchat.client.shell.SelectedFile
import com.gangchat.client.ui.AppConfigScope
import com.gangchat.client.ui.ButtonTone
import com.gangchat.client.ui.ComposerSuggestionAction
import com.gangchat.client.ui.ComposerSuggestionNavigation
import com.gangchat.client.ui.DialogFrame
import com.gangchat.client.ui.FloatingNoticeTone
import com.gangchat.client.ui.ResponsiveDialogAction
import com.gangchat.client.ui.UiTypography
import com.gangchat.client.ui.showFloatingErrorNotice
import com.gangchat.client.ui.showFloatingNotice
import com.gangchat.client.widgets.BuildContext
import com.gangchat.client.widgets.Navigator
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.showDialog
import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Port of lib/src/home/home_shell_messages.dart.
//
// PORT: the desktop file drop (`_handleDroppedFiles`, `_composerContainsDropPoint`)
// and the web paste hook (`_handleWebPaste`, `_composerHasFocus`) are not
// ported.

private fun _latestServerMessage(messages: List<Message>): Message? {
    for (message in messages.asReversed()) {
        if (!message.pending) return message
    }
    return null
}

internal fun _latestServerMessageId(messages: List<Message>): String? = _latestServerMessage(messages)?.id

internal fun _HomeShellState._clearRoomUnreadCount(roomId: String) {
    _servers = _roomsController.patchRoomUnreadCleared(rooms = _servers, roomId = roomId).rooms
}

internal suspend fun _HomeShellState._markRoomReadFromMessages(roomId: String, messages: List<Message>) {
    if (_selectedServerId != roomId) return
    val lastReadMessage = _latestServerMessage(messages) ?: return
    val lastReadMessageId = lastReadMessage.id

    if (_services.roomReads.isSynced(roomId = roomId, messageId = lastReadMessageId)) {
        if (mounted) _setHomeState { _clearRoomUnreadCount(roomId) }
        return
    }
    if (mounted) _setHomeState { _clearRoomUnreadCount(roomId) }

    _services.roomReads.markRead(
        roomId = roomId,
        lastReadMessageId = lastReadMessageId,
        messageCreatedAt = lastReadMessage.createdAt,
    )
}

internal fun _HomeShellState._clearSelectedRoomNewMessagePrompt() {
    if (_selectedRoomNewMessageCount == 0) return
    val roomId = _selectedServerId
    val messages = _messages
    _setHomeState { _selectedRoomNewMessageCount = 0 }
    if (roomId != null) {
        unawaited { _markRoomReadFromMessages(roomId, messages) }
    }
}

internal fun _HomeShellState._handleComposerDraftChanged() {
    if (_updatingComposerFromDraft) return
    if (_tryCompleteSingleComposerMentionAfterSpace()) return
    _composerController.pruneInvalidConfirmedMentions(notify = false)
    _saveComposerDraftValue(_composerController.text)
    _updateComposerMentionState()
}

internal fun _HomeShellState._storeSelectedComposerDraft() {
    _setHomeState { _saveDraftInState() }
}

internal fun _HomeShellState._restoreComposerDraftForRoom(roomId: String) {
    _stagedAttachments.clear()
    _stagedAttachments.addAll(_stagedAttachmentDrafts[roomId] ?: emptyList())
    _setComposerText(_messageDrafts[roomId] ?: "", saveDraft = false)
    _clearComposerMentionState()
}

internal val _HomeShellState._selectedComposerQuotes: List<MessageQuote>
    get() {
        val roomId = _selectedServerId ?: return emptyList()
        return _messageQuoteDrafts[roomId] ?: emptyList()
    }

internal val _HomeShellState._selectedComposerComponent: Message?
    get() {
        val roomId = _selectedServerId ?: return null
        return _messageComponentDrafts[roomId]
    }

internal fun _HomeShellState._removeSelectedComposerComponent() {
    val roomId = _selectedServerId
    if (roomId == null || !_messageComponentDrafts.containsKey(roomId)) return
    _setHomeState {
        _messageComponentDrafts = LinkedHashMap(_messageComponentDrafts).apply { remove(roomId) }
        _saveDraftInState()
    }
}

private fun _HomeShellState._quoteChatMessage(message: Message) {
    if (!_canQuoteChatMessage(message)) return
    val current = _messageQuoteDrafts[message.roomId] ?: emptyList()
    if (current.any { quote -> quote.messageId == message.id }) {
        _composerPanelController.closePanel()
        _composerPanelController.requestInputFocus()
        return
    }
    _setHomeState {
        val next = LinkedHashMap(_messageQuoteDrafts)
        next[message.roomId] = current + messageQuoteSnapshot(message)
        _messageQuoteDrafts = next
        _saveDraftInState()
        _sendError = null
    }
    _composerPanelController.closePanel()
    _composerPanelController.requestInputFocus()
}

internal fun _HomeShellState._removeSelectedComposerQuote(messageId: String) {
    val roomId = _selectedServerId
    if (roomId == null || !_messageQuoteDrafts.containsKey(roomId)) return
    _setHomeState {
        val remaining = (_messageQuoteDrafts[roomId] ?: emptyList()).filter { quote -> quote.messageId != messageId }
        val next = LinkedHashMap(_messageQuoteDrafts)
        if (remaining.isEmpty()) {
            next.remove(roomId)
        } else {
            next[roomId] = remaining
        }
        _messageQuoteDrafts = next
        _saveDraftInState()
    }
}

internal fun _HomeShellState._setComposerText(text: String, saveDraft: Boolean) {
    val nextValue = TextFieldValue(text = text, selection = TextRange(text.length))
    if (_composerController.value == nextValue) {
        if (saveDraft) _saveComposerDraftValue(text)
        return
    }

    _composerController.clearConfirmedMentions()
    val previousUpdating = _updatingComposerFromDraft
    _updatingComposerFromDraft = true
    _composerController.value = nextValue
    _updatingComposerFromDraft = previousUpdating
    if (saveDraft) _saveComposerDraftValue(text)
}

private fun _HomeShellState._saveComposerDraftValue(text: String) {
    _setHomeState { _saveDraftInState(text = text) }
}

private fun _HomeShellState._buildComposerMentionOptions(query: MessageMentionQuery?): List<MessageMentionOption> {
    val roomId = _selectedServerId
    if (query == null || roomId == null || _composerMentionMembersRoomId != roomId) {
        return emptyList()
    }
    return messageMentionOptions(
        members = _composerMentionMembers,
        query = query.query,
        ownerUserId = _selectedRoom?.createdBy?.id,
        excludedUserId = _currentUser.id,
        limit = 50,
        includeGangBot = _selectedRoom?.gangBotActive ?: false,
    )
}

private fun _HomeShellState._setComposerMentionQuery(query: MessageMentionQuery?) {
    _composerMentionQuery = query
    _composerMentionOptions = _buildComposerMentionOptions(query)
    _composerMentionSelectedIndex = 0
}

private fun _HomeShellState._refreshComposerMentionOptions() {
    _composerMentionOptions = _buildComposerMentionOptions(_composerMentionQuery)
    _composerMentionSelectedIndex = _clampComposerMentionSelectedIndex(_composerMentionSelectedIndex)
}

private fun _HomeShellState._clampComposerMentionSelectedIndex(index: Int): Int {
    if (_composerMentionOptions.isEmpty()) return 0
    return index.coerceIn(0, _composerMentionOptions.size - 1)
}

internal fun _HomeShellState._highlightComposerMentionSelection(index: Int) {
    if (_composerMentionOptions.isEmpty()) return
    val nextIndex = _clampComposerMentionSelectedIndex(index)
    if (_composerMentionSelectedIndex == nextIndex) return
    _setHomeState { _composerMentionSelectedIndex = nextIndex }
}

internal fun _HomeShellState._navigateComposerMentionSelection(navigation: ComposerSuggestionNavigation): Boolean {
    if (_composerMentionQuery == null || _composerMentionOptions.isEmpty()) {
        return false
    }
    val current = _clampComposerMentionSelectedIndex(_composerMentionSelectedIndex)
    val delta = if (navigation == ComposerSuggestionNavigation.previous) -1 else 1
    val count = _composerMentionOptions.size
    val nextIndex = (current + delta + count).mod(count)
    _setHomeState { _composerMentionSelectedIndex = nextIndex }
    _composerPanelController.requestInputFocus()
    return true
}

internal fun _HomeShellState._confirmComposerMentionSelection(action: ComposerSuggestionAction): Boolean {
    if (action != ComposerSuggestionAction.confirm) return false
    return _completeComposerMentionSelection(includeTerminatingSpace = true, synthesizeTerminatingSpace = true)
}

private fun _HomeShellState._clearComposerMentionState() {
    if (_composerMentionQuery == null && _composerMentionOptions.isEmpty() && !_loadingComposerMentionMembers) {
        return
    }
    _setHomeState {
        _setComposerMentionQuery(null)
        _loadingComposerMentionMembers = false
        _loadingComposerMentionMembersRoomId = null
    }
}

private fun _HomeShellState._updateComposerMentionState() {
    val roomId = _selectedServerId
    if (roomId == null) {
        if (_composerMentionQuery != null) {
            _setHomeState { _setComposerMentionQuery(null) }
        }
        return
    }
    val selection = _composerController.selection
    val query =
        if (!selection.collapsed) {
            null
        } else {
            activeMessageMentionQuery(text = _composerController.text, cursorOffset = selection.end)
        }
    if (query == null || _composerController.hasConfirmedMention(start = query.start, end = query.end)) {
        if (_composerMentionQuery != null) {
            _setHomeState { _setComposerMentionQuery(null) }
        }
        return
    }

    val previous = _composerMentionQuery
    if (previous == null || previous.start != query.start || previous.end != query.end || previous.query != query.query) {
        _setHomeState { _setComposerMentionQuery(query) }
    }
    unawaited { _ensureComposerMentionMembers(roomId) }
}

internal suspend fun _HomeShellState._ensureComposerMentionMembers(roomId: String) {
    if (_composerMentionMembersRoomId == roomId ||
        (_loadingComposerMentionMembers && _loadingComposerMentionMembersRoomId == roomId)
    ) {
        return
    }
    val serial = ++_composerMentionMembersSerial
    _setHomeState {
        _loadingComposerMentionMembers = true
        _loadingComposerMentionMembersRoomId = roomId
    }
    try {
        val members = _roomsController.loadAllRoomMembers(roomId)
        if (!mounted || serial != _composerMentionMembersSerial || _selectedServerId != roomId) {
            return
        }
        _setHomeState {
            _composerMentionMembers = members.toList()
            _composerMentionMembersRoomId = roomId
            _loadingComposerMentionMembers = false
            _loadingComposerMentionMembersRoomId = null
            _refreshComposerMentionOptions()
        }
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        if (!mounted || serial != _composerMentionMembersSerial || _selectedServerId != roomId) {
            return
        }
        _setHomeState {
            _composerMentionMembers = emptyList()
            _composerMentionMembersRoomId = roomId
            _loadingComposerMentionMembers = false
            _loadingComposerMentionMembersRoomId = null
            _refreshComposerMentionOptions()
        }
    }
}

internal fun _HomeShellState._selectComposerMention(option: MessageMentionOption) {
    val value = _composerController.value
    val selection = value.selection
    val query =
        if (selection.collapsed) {
            activeMessageMentionQuery(text = value.text, cursorOffset = selection.end)
        } else {
            _composerMentionQuery
        } ?: return

    val replacement = messageMentionInsertText(option)
    val nextText = value.text.replaceRange(query.start, query.end, replacement)
    val nextOffset = query.start + replacement.length
    val nextValue = value.copy(text = nextText, selection = TextRange(nextOffset), composition = null)
    _composerController.value = nextValue
    _composerController.addConfirmedMention(start = query.start, label = option.label)
    _saveComposerDraftValue(nextText)
    if (_composerMentionQuery != null) {
        _setHomeState { _setComposerMentionQuery(null) }
    }
    _composerPanelController.requestInputFocus()
}

private fun _HomeShellState._tryCompleteSingleComposerMentionAfterSpace(): Boolean {
    val value = _composerController.value
    val selection = value.selection
    if (!selection.collapsed) return false
    val query = _composerMentionQuery ?: return false
    val cursor = selection.end
    if (cursor != query.end + 1) return false
    if (query.start < 0 || query.end >= value.text.length) return false
    if (value.text[query.end].code != 0x20) return false
    return _completeComposerMentionSelection(includeTerminatingSpace = true)
}

private fun _HomeShellState._completeComposerMentionSelection(
    includeTerminatingSpace: Boolean,
    synthesizeTerminatingSpace: Boolean = false,
): Boolean {
    val query = _composerMentionQuery ?: return false
    val value = _composerController.value
    val selection = value.selection
    if (!selection.collapsed) return false
    var text = value.text
    if (synthesizeTerminatingSpace) {
        if (!includeTerminatingSpace || selection.end != query.end) {
            return false
        }
        text = text.replaceRange(query.end, query.end, " ")
    }
    val replaceEnd = if (includeTerminatingSpace) query.end + 1 else query.end
    if (query.start < 0 || query.end > text.length || replaceEnd > text.length) {
        return false
    }
    if (includeTerminatingSpace && text[query.end].code != 0x20) {
        return false
    }
    if (text.substring(query.start, query.end) != "@${query.query}") {
        return false
    }
    if (_composerController.hasConfirmedMention(start = query.start, end = query.end)) {
        _setHomeState { _setComposerMentionQuery(null) }
        return false
    }

    val options = _composerMentionOptions
    if (options.isEmpty()) return false
    val option = options[_clampComposerMentionSelectedIndex(_composerMentionSelectedIndex)]
    val replacement = messageMentionInsertText(option)
    val nextText = text.replaceRange(query.start, replaceEnd, replacement)
    val nextOffset = query.start + replacement.length
    val nextValue = value.copy(text = nextText, selection = TextRange(nextOffset), composition = null)

    val previousUpdating = _updatingComposerFromDraft
    _updatingComposerFromDraft = true
    _composerController.value = nextValue
    _updatingComposerFromDraft = previousUpdating
    _composerController.addConfirmedMention(start = query.start, label = option.label)
    _saveComposerDraftValue(nextText)
    _setHomeState { _setComposerMentionQuery(null) }
    _composerPanelController.requestInputFocus()
    return true
}

internal fun _HomeShellState._mentionUserFromAvatar(user: UserSummary) {
    val label = _mentionLabelForUser(user)
    if (label.isEmpty()) return
    val value = _composerController.value
    val needsLeadingSpace = value.text.isNotEmpty() && !_endsWithMentionBoundary(value.text)
    val prefix = if (needsLeadingSpace) " " else ""
    val insertion = "$prefix@$label "
    val insertAt = value.selection.max
    val nextText = value.text.replaceRange(insertAt, insertAt, insertion)
    val mentionStart = insertAt + prefix.length
    val nextOffset = insertAt + insertion.length
    _composerController.value = value.copy(text = nextText, selection = TextRange(nextOffset), composition = null)
    _composerController.addConfirmedMention(start = mentionStart, label = label)
    _saveComposerDraftValue(nextText)
    _composerPanelController.requestInputFocus()
}

private fun _mentionLabelForUser(user: UserSummary): String {
    val roomName = user.roomDisplayName?.trim()
    if (roomName != null && roomName.isNotEmpty()) return roomName
    val displayName = user.displayName.trim()
    if (displayName.isNotEmpty()) return displayName
    return user.username.trim()
}

private fun _endsWithMentionBoundary(text: String): Boolean {
    if (text.isEmpty()) return true
    val codeUnit = text[text.length - 1].code
    return codeUnit <= 0x20 || codeUnit == 0x3000
}

internal fun _HomeShellState._saveDraftInState(text: String? = null) {
    val roomId = _selectedServerId ?: return
    val draftText = text ?: _composerController.text
    val hasAttachments = _stagedAttachments.isNotEmpty()
    val hasQuote = (_messageQuoteDrafts[roomId] ?: emptyList()).isNotEmpty()
    val hasComponent = _messageComponentDrafts.containsKey(roomId)
    val hasVisibleDraft = draftText.trim().isNotEmpty() || hasAttachments || hasQuote || hasComponent
    val current = _messageDrafts[roomId]
    val currentAttachments = _stagedAttachmentDrafts[roomId] ?: emptyList()
    val sameText = current == draftText
    val sameAttachments = _sameStagedAttachments(currentAttachments, _stagedAttachments)
    if (!hasVisibleDraft && current == null && currentAttachments.isEmpty()) {
        return
    }
    if (hasVisibleDraft && sameText && sameAttachments) return

    val nextText = LinkedHashMap(_messageDrafts)
    val nextAttachments = LinkedHashMap(_stagedAttachmentDrafts)
    if (hasVisibleDraft) {
        nextText[roomId] = draftText
        nextAttachments[roomId] = _stagedAttachments.toList()
    } else {
        nextText.remove(roomId)
        nextAttachments.remove(roomId)
    }
    _messageDrafts = nextText
    _stagedAttachmentDrafts = nextAttachments
}

private fun _sameStagedAttachments(a: List<_StagedAttachment>, b: List<_StagedAttachment>): Boolean {
    if (a.size != b.size) return false
    for (index in a.indices) {
        if (a[index] !== b[index]) return false
    }
    return true
}

internal fun _HomeShellState._discardRoomDraftInState(roomId: String, cancelUploads: Boolean = true) {
    val attachments = _stagedAttachmentDrafts[roomId] ?: emptyList()
    if (cancelUploads) {
        for (entry in attachments) {
            entry.uploadController.cancel()
        }
    }
    if (_messageDrafts.containsKey(roomId)) {
        _messageDrafts = LinkedHashMap(_messageDrafts).apply { remove(roomId) }
    }
    if (_stagedAttachmentDrafts.containsKey(roomId)) {
        _stagedAttachmentDrafts = LinkedHashMap(_stagedAttachmentDrafts).apply { remove(roomId) }
    }
    if (_messageQuoteDrafts.containsKey(roomId)) {
        _messageQuoteDrafts = LinkedHashMap(_messageQuoteDrafts).apply { remove(roomId) }
    }
    if (_messageComponentDrafts.containsKey(roomId)) {
        _messageComponentDrafts = LinkedHashMap(_messageComponentDrafts).apply { remove(roomId) }
    }
}

internal fun _HomeShellState._cancelAllDraftAttachments() {
    val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<_StagedAttachment, Boolean>())
    for (entry in _stagedAttachments) {
        if (seen.add(entry)) entry.uploadController.cancel()
    }
    for (attachments in _stagedAttachmentDrafts.values) {
        for (entry in attachments) {
            if (seen.add(entry)) entry.uploadController.cancel()
        }
    }
}

private fun _HomeShellState._isStagedAttachmentActive(entry: _StagedAttachment): Boolean {
    if (_stagedAttachments.any { it === entry }) return true
    for (attachments in _stagedAttachmentDrafts.values) {
        if (attachments.any { it === entry }) return true
    }
    return false
}

internal val _HomeShellState._sidebarRoomDrafts: Map<String, String>
    get() {
        val roomIds = LinkedHashSet<String>()
        roomIds.addAll(_messageDrafts.keys)
        roomIds.addAll(_stagedAttachmentDrafts.keys)
        roomIds.addAll(_messageQuoteDrafts.keys)
        roomIds.addAll(_messageComponentDrafts.keys)
        val previews = LinkedHashMap<String, String>()
        for (roomId in roomIds) {
            val preview = _roomDraftPreviewFor(roomId)
            if (preview != null) previews[roomId] = preview
        }
        return previews
    }

private fun _HomeShellState._roomDraftPreviewFor(roomId: String): String? {
    val attachments = _stagedAttachmentDrafts[roomId] ?: emptyList()
    val attachment = attachments.firstOrNull()
    val component = _messageComponentDrafts[roomId]
    if (component != null) {
        return if (component.playlistAttachment != null) {
            "[歌单] ${component.playlistAttachment!!.playlist!!.name}"
        } else {
            "[歌曲] ${component.musicTrackAttachment!!.track!!.title}"
        }
    }
    return roomDraftPreviewText(
        text = _messageDrafts[roomId],
        attachmentFilename = attachment?.file?.name,
        attachmentMimeType = attachment?.file?.mimeType,
        hasQuote = (_messageQuoteDrafts[roomId] ?: emptyList()).isNotEmpty(),
    )
}

internal val _HomeShellState._chatMessageActions: ChatMessageActions
    get() =
        ChatMessageActions(
            onCopy = { context, message -> _copyChatMessage(context, message) },
            onQuote = { message -> _quoteChatMessage(message) },
            onOpenQuote = { context, quote -> _openQuotedMessage(context, quote) },
            onDeleteForMe = { context, message -> _deleteMessageForMe(context, message) },
            onRecall = { context, message -> _recallChatMessage(context, message) },
            canRecall = { message -> _canRecallChatMessage(message) },
            canQuote = { message -> _canQuoteChatMessage(message) },
            onReeditRecalledText = { message -> _reeditRecalledTextMessage(message) },
            canReeditRecalledText = { message -> _canReeditRecalledTextMessage(message) },
            canInspectRecalledText = { message -> _canInspectRecalledTextMessage(message) },
            onViewSharedPlaylist = { context, message, playlist -> _viewSharedPlaylistMessage(context, message, playlist) },
            onEditOwnedSharedPlaylist = { context, message, playlist -> _editOwnedSharedPlaylistMessage(context, message, playlist) },
            sharedTrackPreviewController = _sharedMessageTrackPreviewController,
            loadSharedTrackPlaylists = { roomId -> _loadSharedTrackPlaylists(roomId) },
            onAddSharedTrackToPlaylist = { track, target -> _addSharedTrackToPlaylist(track, target) },
        )

private suspend fun _HomeShellState._loadSharedTrackPlaylists(roomId: String): List<MusicTrackPlaylistTarget> {
    val api = _services.api
    val targets = mutableListOf<MusicTrackPlaylistTarget>()
    if (api is PersonalMusicPlaylistApi) {
        val page = api.listPersonalMusicPlaylists(pageSize = 50)
        targets.addAll(page.playlists.map { MusicTrackPlaylistTarget.personal(it) })
    }
    if (api is RoomMusicPlaylistApi) {
        val page = api.listRoomMusicPlaylists(roomId = roomId, pageSize = 50)
        targets.addAll(page.playlists.map { playlist -> MusicTrackPlaylistTarget.room(playlist = playlist, roomId = roomId) })
    }
    return targets
}

private suspend fun _HomeShellState._addSharedTrackToPlaylist(track: SharedMusicTrack, target: MusicTrackPlaylistTarget) {
    val api = _services.api
    val searchResult =
        MusicBoxSearchResult(
            trackId = track.trackId,
            name = track.title,
            artists = track.artists,
            source = track.source,
        )
    if (target.roomScoped) {
        val roomId = target.roomId
        if (api !is RoomMusicPlaylistApi || roomId == null || roomId.isEmpty()) {
            throw IllegalStateException("当前版本不支持添加到房间歌单")
        }
        api.addRoomMusicPlaylistItem(
            roomId = roomId,
            playlistId = target.playlist.id,
            track = searchResult,
            durationMs = if (track.durationMs > 0) track.durationMs else null,
        )
        return
    }
    if (api !is PersonalMusicPlaylistApi) {
        throw IllegalStateException("当前版本不支持添加到个人歌单")
    }
    api.addPersonalMusicPlaylistItem(
        playlistId = target.playlist.id,
        track = searchResult,
        durationMs = if (track.durationMs > 0) track.durationMs else null,
    )
}

private suspend fun _HomeShellState._viewSharedPlaylistMessage(
    context: BuildContext,
    message: Message,
    playlist: SharedMusicPlaylist,
) {
    val api = _services.api
    if (api !is PersonalMusicPlaylistApi) {
        if (context.mounted) {
            showFloatingErrorNotice(context, "当前版本暂不支持查看歌单")
        }
        return
    }
    val personalApi: PersonalMusicPlaylistApi = api
    val tracks =
        playlist.items.map { item ->
            MusicTrackCardData(
                id = item.id,
                source = item.source,
                trackId = item.trackId,
                title = item.title,
                artists = item.artists,
                durationMs = item.durationMs,
            )
        }
    showDialog<Unit>(context) { _ ->
        MusicPlaylistSnapshotDialog(
            title = playlist.name,
            tracks = tracks,
            previewApi = api as? MusicTrackPreviewApi,
            previewPlatformFactory = widget.musicTrackPreviewPlatformFactory,
            loadPersonalPlaylists = { personalApi.listPersonalMusicPlaylists(pageSize = 50) },
            onAddToPlaylist = { track, target ->
                personalApi.addPersonalMusicPlaylistItem(
                    playlistId = target.id,
                    track =
                        MusicBoxSearchResult(
                            trackId = track.trackId,
                            name = track.title,
                            artists = track.artists,
                            source = track.source,
                        ),
                    durationMs = if (track.durationMs > 0) track.durationMs else null,
                )
            },
            onClone =
                if (api is SharedMusicPlaylistCloneApi) {
                    { api.cloneSharedMusicPlaylistToPersonal(roomId = message.roomId, messageId = message.id) }
                } else {
                    null
                },
            contentKey = "shared-music-playlist-view-${playlist.id}",
            trackListKey = "shared-music-playlist-tracks-${playlist.id}",
            cloneButtonKey = "shared-music-playlist-clone-${playlist.id}",
            doneButtonKey = "shared-music-playlist-done-${playlist.id}",
            cloneErrorMessage = { error ->
                if (error !is ApiException) {
                    "克隆歌单失败：$error"
                } else {
                    when (error.code) {
                        "playlist_limit_reached" -> "克隆失败：我的歌单已达 50 个上限"
                        "not_found" -> "克隆失败：该歌单消息已不可用"
                        else -> "克隆歌单失败：$error"
                    }
                }
            },
        )
    }
}

private suspend fun _HomeShellState._editOwnedSharedPlaylistMessage(
    context: BuildContext,
    message: Message,
    playlist: SharedMusicPlaylist,
) {
    if (playlist.creator.id != _currentUser.id) {
        _viewSharedPlaylistMessage(context, message, playlist)
        return
    }
    _openSettingsSection(
        com.gangchat.client.app.SettingsSection.playlists,
        openContent = true,
        initialMusicPlaylistId = playlist.id,
    )
}

private fun _canQuoteChatMessage(message: Message): Boolean = !message.pending && !message.failed && !message.isRemoved

private suspend fun _HomeShellState._openQuotedMessage(context: BuildContext, quote: MessageQuote) {
    val roomId = _selectedServerId
    if (roomId == null || _locallyDeletedMessageKeys.contains(quote.messageId)) {
        if (context.mounted) {
            showFloatingErrorNotice(context, "原消息已被删除或撤回")
        }
        return
    }

    var target: Message? = _messageById(_messages, quote.messageId)
    if (target == null) {
        try {
            val loaded = _messagesController.loadMessagesUntil(roomId = roomId, messageId = quote.messageId)
            if (!mounted || _selectedServerId != roomId) return
            target = _messageById(loaded, quote.messageId)
            if (target != null) {
                _setHomeState { _messages = _mergeMessages(_messages, loaded) }
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (context.mounted) {
                showFloatingErrorNotice(context, userFacingErrorMessage(error, fallback = "无法跳转到原消息"))
            }
            return
        }
    }

    if (target == null || target.isRemoved || _isMessageDeletedForMe(target)) {
        if (context.mounted) {
            showFloatingErrorNotice(context, "原消息已被删除或撤回")
        }
        return
    }
    _setHomeState {
        _settingsOpen = false
        _contentMode = _ContentMode.chat
        _focusedMessageId = quote.messageId
    }
}

private fun _messageById(messages: List<Message>, messageId: String): Message? {
    for (message in messages) {
        if (message.id == messageId) return message
    }
    return null
}

private fun _mergeMessages(current: List<Message>, loaded: List<Message>): List<Message> {
    val byId = LinkedHashMap<String, Message>()
    for (message in loaded) {
        byId[message.id] = message
    }
    for (message in current) {
        byId[message.id] = message
    }
    return byId.values.sortedWith { a, b ->
        val byTime = a.createdAt.compareTo(b.createdAt)
        if (byTime != 0) byTime else a.id.compareTo(b.id)
    }
}

internal fun _HomeShellState._visibleMessagesForMe(messages: List<Message>): List<Message> {
    if (_locallyDeletedMessageKeys.isEmpty()) return messages
    return messages.filter { message -> !_isMessageDeletedForMe(message) }
}

internal fun _HomeShellState._visibleNewMessageCount(visibleMessages: List<Message>): Int {
    val count = _selectedRoomNewMessageCount
    if (count <= 0) return 0
    val firstUnreadIndex = _messages.size - count
    if (firstUnreadIndex <= 0) return visibleMessages.size
    var visibleUnread = 0
    for (i in firstUnreadIndex until _messages.size) {
        if (!_isMessageDeletedForMe(_messages[i])) visibleUnread++
    }
    return visibleUnread
}

private fun _HomeShellState._isMessageDeletedForMe(message: Message): Boolean =
    _locallyDeletedMessageKeys.contains(message.id) || _locallyDeletedMessageKeys.contains(message.clientMessageId)

private suspend fun _HomeShellState._copyChatMessage(context: BuildContext, message: Message) {
    try {
        _copyChatMessageContents(context, message)
        if (!context.mounted) return
        _showChatMessageActionNotice(context, "已复制", tone = FloatingNoticeTone.success)
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        if (!context.mounted) return
        _showChatMessageActionNotice(
            context,
            userFacingErrorMessage(error, fallback = "复制消息失败"),
            tone = FloatingNoticeTone.error,
        )
    }
}

private suspend fun _HomeShellState._copyChatMessageContents(context: BuildContext, message: Message) {
    val isMusicComponent = message.playlistAttachment != null || message.musicTrackAttachment != null
    if (isMusicComponent) {
        val text = messageComponentClipboardText(message)
        if (text.isEmpty()) {
            throw Exception("这条消息没有可复制的内容")
        }
        // Pending messages only carry a local client id and cannot be resolved
        // to the immutable server snapshot used by structured paste.
        _copiedMessageComponent = if (message.pending) null else message
        try {
            _clipboardService.writeText(text)
        } catch (error: Exception) {
            if (_copiedMessageComponent === message) {
                _copiedMessageComponent = null
            }
            throw error
        }
        return
    }
    _copiedMessageComponent = null
    val attachments = _copyableMessageAttachments(message)
    if (attachments.isNotEmpty()) {
        _copyMessageAttachments(context, message, attachments)
        return
    }
    val text = messageClipboardText(message)
    if (text.isEmpty()) {
        throw Exception("这条消息没有可复制的内容")
    }
    _clipboardService.writeText(text)
}

private fun _copyableMessageAttachments(message: Message): List<MessageAttachment> =
    message.attachments.filter { attachment ->
        attachment.asset != null && (attachment.type == "file" || attachment.type == "sticker" || attachment.type == "audio")
    }

private suspend fun _HomeShellState._copyMessageAttachments(
    context: BuildContext,
    message: Message,
    attachments: List<MessageAttachment>,
) {
    val config = AppConfigScope.of(context)
    if (attachments.size == 1) {
        val attachment = attachments.single()
        val asset = attachment.asset
        val resolvedUrl = config.resolveAssetUrl(asset?.url)
        if (resolvedUrl != null && resolvedUrl.isNotEmpty() && isImageMimeType(asset?.mimeType)) {
            try {
                _imagePreviewActions.onCopyToClipboard(resolvedUrl)
                return
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                // Fall through to file clipboard so platforms without image clipboard
                // support can still paste the sticker/image as a file.
            }
        }
    }

    val paths = mutableListOf<String>()
    val fallbackLines = mutableListOf<String>()
    val body = message.body.trimEnd()
    if (body.isNotEmpty()) fallbackLines.add(body)
    for (attachment in attachments) {
        val asset = attachment.asset
        val resolvedUrl = config.resolveAssetUrl(asset?.url)
        if (resolvedUrl == null || resolvedUrl.isEmpty() || asset == null) {
            continue
        }
        fallbackLines.add(resolvedUrl)
        val path =
            _downloadAttachmentToClipboardFile(
                url = resolvedUrl,
                title = _attachmentClipboardFilename(attachment),
                asset = asset,
            )
        if (path != null) paths.add(path)
    }

    if (paths.isNotEmpty() && _clipboardService.writeFilePaths(paths)) {
        return
    }
    if (fallbackLines.isNotEmpty()) {
        _clipboardService.writeText(fallbackLines.joinToString("\n"))
        return
    }
    throw Exception("这条消息没有可复制的内容")
}

private suspend fun _HomeShellState._downloadAttachmentToClipboardFile(
    url: String,
    title: String,
    asset: UploadedAsset,
): String? {
    val baseDirectory = PathProvider.getTemporaryDirectory()
    val directory = File(baseDirectory, "gang-chat-clipboard")
    withContext(Dispatchers.IO) { directory.mkdirs() }
    val filename = _safeClipboardFilename(title)
    val path = _uniqueDestinationPath(directory = directory.path, filename = filename)
    val request =
        MediaCacheRequest.tryFromUrl(
            url = url,
            filename = filename,
            mimeType = asset.mimeType,
            expectedBytes = asset.sizeBytes,
            namespace = "clipboard",
        )
    if (request != null) {
        val cached = _mediaCacheController.getOrDownload(request = request)
        withContext(Dispatchers.IO) { cached.copyTo(File(path), overwrite = true) }
        return path
    }

    val uri = Uri.tryParse(url) ?: return null
    val client = HttpClient()
    try {
        val response = client.get(uri)
        if (response.statusCode < 200 || response.statusCode >= 300) {
            return null
        }
        val bytes = response.bodyBytes
        withContext(Dispatchers.IO) { File(path).writeBytes(bytes) }
        return path
    } finally {
        client.close()
    }
}

private fun _attachmentClipboardFilename(attachment: MessageAttachment): String {
    if (attachment.type == "sticker") {
        return stickerPreviewFilename(attachment)
    }
    return fileAttachmentTitle(attachment)
}

private fun _safeClipboardFilename(value: String): String {
    val basename = com.gangchat.client.app.basename(value)
    val safe =
        basename
            .replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1F]+"), "_")
            .replace(Regex("_+"), "_")
            .trim()
    return if (safe.isEmpty() || safe == "_") "file" else safe
}

private suspend fun _HomeShellState._deleteMessageForMe(context: BuildContext, message: Message) {
    val confirmed =
        showDialog<Boolean>(context) { dialogContext ->
            DialogFrame(
                title = "删除消息",
                icon = Icons.Filled.DeleteOutline,
                adaptiveActions =
                    listOf(
                        ResponsiveDialogAction(label = "取消", onPressed = { Navigator.of(dialogContext).pop(false) }),
                        ResponsiveDialogAction(
                            label = "删除",
                            tone = ButtonTone.danger,
                            onPressed = { Navigator.of(dialogContext).pop(true) },
                        ),
                    ),
                child = { Text("这只会从你的聊天记录中删除，其他人仍然可以看到这条消息。", style = UiTypography.body) },
            )
        }
    if (confirmed != true || !mounted) return
    _setHomeState {
        _locallyDeletedMessageKeys.add(message.id)
        _locallyDeletedMessageKeys.add(message.clientMessageId)
    }
}

private suspend fun _HomeShellState._recallChatMessage(context: BuildContext, message: Message) {
    if (!_canRecallChatMessage(message)) return
    val recallingOther = message.sender.id != _currentUser.id
    if (recallingOther) {
        val confirmed =
            showDialog<Boolean>(context) { dialogContext ->
                DialogFrame(
                    title = "撤回消息",
                    icon = Icons.AutoMirrored.Rounded.Undo,
                    adaptiveActions =
                        listOf(
                            ResponsiveDialogAction(label = "取消", onPressed = { Navigator.of(dialogContext).pop(false) }),
                            ResponsiveDialogAction(
                                label = "撤回",
                                tone = ButtonTone.danger,
                                onPressed = { Navigator.of(dialogContext).pop(true) },
                            ),
                        ),
                    child = {
                        Text("确认撤回 ${userPrimaryName(message.sender)} 的这条消息？", style = UiTypography.body)
                    },
                )
            }
        if (confirmed != true) return
    }

    try {
        val result = _messagesController.recallMessage(roomId = message.roomId, messageId = message.id)
        if (!mounted) return
        val recalled = result.message
        if (recalled != null) {
            _setHomeState { _messages = _messagesController.replaceByMessageId(_messages, recalled) }
            unawaited { _loadServers() }
            if (context.mounted) {
                _showChatMessageActionNotice(context, "已撤回", tone = FloatingNoticeTone.success)
            }
            return
        }
        if (result.isPending && context.mounted) {
            _showChatMessageActionNotice(context, "已提交撤回申请", tone = FloatingNoticeTone.success)
        }
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        if (!context.mounted) return
        _showChatMessageActionNotice(
            context,
            userFacingErrorMessage(error, fallback = "撤回消息失败"),
            tone = FloatingNoticeTone.error,
        )
    }
}

private fun _HomeShellState._canRecallChatMessage(message: Message): Boolean {
    if (message.pending || message.failed || message.isRemoved || message.type == kSystemMessageType) {
        return false
    }
    if (message.sender.id == _currentUser.id) return true
    val room = _selectedRoom
    if (room == null || (!room.isAdmin && !_currentUser.isSuperuser)) {
        return false
    }
    return _currentUserRoomRank(room) > _messageSenderRoomRank(message.sender)
}

private fun _HomeShellState._reeditRecalledTextMessage(message: Message) {
    if (!_canReeditRecalledTextMessage(message)) return
    _setComposerText(message.body, saveDraft = true)
    _composerPanelController.closePanel()
    if (_contentMode != _ContentMode.chat) {
        _setHomeState { _contentMode = _ContentMode.chat }
    }
}

private fun _HomeShellState._canReeditRecalledTextMessage(message: Message): Boolean {
    if (!message.isRecalled || message.isForceDeleted || message.type != "text" || message.body.isEmpty()) {
        return false
    }
    if (message.sender.id != _currentUser.id) return false
    val actor = message.recalledBy ?: message.sender
    return actor.id == _currentUser.id
}

private fun _HomeShellState._canInspectRecalledTextMessage(message: Message): Boolean {
    if (!message.isRecalled || message.isForceDeleted || message.type != "text" || message.body.isEmpty()) {
        return false
    }
    val actor = message.recalledBy ?: message.sender
    if (message.sender.id == _currentUser.id) {
        return actor.id != _currentUser.id
    }
    val room = _selectedRoom ?: return false
    return _currentUserRoomRank(room) > _messageSenderRoomRank(message.sender)
}

private fun _HomeShellState._currentUserRoomRank(room: RoomDetail): Int {
    if (_currentUser.isSuperuser) return 4
    return _roomRoleRank(room.myMembership.role)
}

private fun _messageSenderRoomRank(sender: UserSummary): Int {
    if (sender.isSuperuser) return 4
    return _roomRoleRank(sender.roomRole)
}

private fun _roomRoleRank(role: String?): Int =
    when ((role ?: "").lowercase()) {
        "owner", "creator" -> 3
        "admin", "administrator" -> 2
        "member" -> 1
        else -> 0
    }

private fun _showChatMessageActionNotice(
    context: BuildContext,
    message: String,
    tone: FloatingNoticeTone = FloatingNoticeTone.info,
) {
    showFloatingNotice(context, message, tone = tone)
}

internal suspend fun _HomeShellState._sendText(value: String) {
    val body = value.trimEnd()
    val component = _selectedComposerComponent
    if (component != null) {
        val roomId = _selectedServerId
        val plan = composerMessageSendPlan(body = body, component = component)
        sendComposerMessagePartsSequentially(
            parts = plan,
            send = { part, hasFollowingPart ->
                val sent =
                    _sendComposed(
                        body = part.body,
                        type = part.type,
                        attachments = part.attachments,
                        clearComposer = !hasFollowingPart,
                        clearComponent = hasFollowingPart,
                        includeQuotes = !hasFollowingPart,
                        includeMentions = !hasFollowingPart,
                    )
                sent && mounted && _selectedServerId == roomId
            },
        )
        return
    }
    // When files are staged, the message goes out as a file message carrying
    // them as attachments (the body rides along). Otherwise it's plain text.
    if (_stagedAttachments.isNotEmpty()) {
        _sendStagedAttachments(body)
        return
    }
    _sendComposed(body = body, type = "text", attachments = emptyList(), clearComposer = true)
}

internal suspend fun _HomeShellState._sendSticker(sticker: Sticker) {
    val draft = stickerMessageDraft(sticker)
    _sendComposed(body = draft.body, type = draft.type, attachments = draft.attachments, clearComposer = false)
}

private suspend fun _HomeShellState._sendComposed(
    body: String,
    type: String,
    attachments: List<MessageAttachment>,
    clearComposer: Boolean,
    clearComponent: Boolean = false,
    includeQuotes: Boolean = true,
    includeMentions: Boolean = true,
): Boolean {
    val room = _selectedRoom
    if (room == null || _sending) return false
    if (!canSendComposedMessage(body = body, type = type, attachments = attachments)) {
        return false
    }

    val quotes = if (includeQuotes) _messageQuoteDrafts[room.id] ?: emptyList() else emptyList()
    var clientMessageId: String? = null
    _setHomeState {
        _sending = true
        _sendError = null
    }

    try {
        val confirmedMentionLabels = _composerController.confirmedMentionLabels()
        val sent =
            _messagesController.sendComposedMessage(
                roomId = room.id,
                sender = _currentUser.toSummary().copyWith(roomDisplayName = _selectedRoom?.personalProfile?.displayName),
                body = body,
                type = type,
                attachments = attachments,
                mentions =
                    if (includeMentions && _composerMentionMembersRoomId == room.id && confirmedMentionLabels.isNotEmpty()) {
                        messageMentionDescriptors(
                            text = body,
                            members = _composerMentionMembers,
                            confirmedLabels = confirmedMentionLabels,
                        )
                    } else {
                        emptyList()
                    },
                quotes = quotes,
                onPending = { pending ->
                    clientMessageId = pending.clientMessageId
                    if (mounted) {
                        _setHomeState {
                            _messages = _messagesController.patchPendingMessage(messages = _messages, pending = pending)
                            if (quotes.isNotEmpty()) {
                                _messageQuoteDrafts = LinkedHashMap(_messageQuoteDrafts).apply { remove(room.id) }
                            }
                        }
                        if (clearComposer || clearComponent) {
                            _setHomeState {
                                _messageComponentDrafts = LinkedHashMap(_messageComponentDrafts).apply { remove(room.id) }
                            }
                        }
                        if (clearComposer) {
                            _setComposerText("", saveDraft = true)
                        } else if (clearComponent || quotes.isNotEmpty()) {
                            _saveComposerDraftValue(_composerController.text)
                        }
                    }
                },
            )
        if (!mounted || _selectedServerId != room.id) return true
        _setHomeState { _messages = _messagesController.patchSentMessage(messages = _messages, sent = sent) }
        unawaited { _loadServers() }
        return true
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        if (mounted) {
            _setHomeState {
                _sendError = userFacingErrorMessage(error)
                val failedId = clientMessageId
                if (failedId != null) {
                    _messages = _messagesController.patchFailedMessage(messages = _messages, clientMessageId = failedId)
                }
            }
        }
        return false
    } finally {
        if (mounted) _setHomeState { _sending = false }
    }
}

/**
 * Load the personal + room sticker packs that back the composer panel.
 * Personal packs are read from the on-disk cache first for an instant
 * render, then refreshed from the server alongside the room's packs.
 */
internal suspend fun _HomeShellState._loadStickerPacks(forceReload: Boolean = false) {
    val roomId = _selectedServerId ?: return
    if (!shouldLoadStickerPanel(state = _stickerPanelState, forceReload = forceReload)) {
        return
    }

    _setHomeState { _stickerPanelState = stickerPanelLoadStarted(_stickerPanelState) }

    try {
        val cachedPersonal =
            if (forceReload) null else _stickerPacksController.readCachedPersonalPacks(userId = _currentUser.id)
        if (!mounted || _selectedServerId != roomId) return
        if (cachedPersonal != null) {
            _setHomeState {
                _stickerPanelState = stickerPanelCachedPersonalApplied(state = _stickerPanelState, packs = cachedPersonal)
            }
        }
        val shouldFetchPersonal = forceReload || cachedPersonal == null
        val stickers = _stickerPacksController
        val userId = _currentUser.id
        val packs: List<List<StickerPack>> =
            waitAll(
                listOf(
                    if (shouldFetchPersonal) {
                        future { stickers.loadPersonalPacks(userId = userId, forceReload = true) }
                    } else {
                        com.gangchat.client.foundation.completedFuture(cachedPersonal!!)
                    },
                    future { stickers.loadRoomPacks(roomId) },
                ),
            )
        if (!mounted || _selectedServerId != roomId) return
        _setHomeState {
            _stickerPanelState =
                stickerPanelLoadSucceeded(
                    state = _stickerPanelState,
                    personalPacks = packs[0],
                    roomPacks = packs[1],
                )
        }
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        if (!mounted || _selectedServerId != roomId) return
        _setHomeState { _stickerPanelState = stickerPanelLoadFailed(state = _stickerPanelState, failure = error) }
    } finally {
        if (mounted && _selectedServerId == roomId) {
            _setHomeState { _stickerPanelState = stickerPanelLoadFinished(_stickerPanelState) }
        }
    }
}

internal fun _HomeShellState._changeStickerSource(source: StickerPanelSource) {
    _setHomeState { _stickerPanelState = stickerPanelSourceChanged(_stickerPanelState, source) }
}

// --- Voice messages ---------------------------------------------------

/**
 * Begin a click-to-record voice clip. Starts the recorder, then drives a 1s
 * ticker that updates the displayed duration and stops automatically at the
 * max length.
 */
internal suspend fun _HomeShellState._startVoiceRecording() {
    if (!_voiceState.isIdle || _selectedRoom == null) return
    try {
        _voiceRecorder.start()
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        if (!mounted) return
        _setHomeState { _voiceState = VoiceRecorderState().copyWith(error = userFacingErrorMessage(error)) }
        return
    }
    if (!mounted) {
        val recorder = _voiceRecorder
        unawaited { recorder.cancel() }
        return
    }
    _voiceStartedAt = DateTime.now()
    _setHomeState { _voiceState = voiceRecordingStarted() }
    _voiceTicker?.cancel()
    // Dart's `Timer.periodic(1s)`, on the composition clock like the shell's
    // other timers.
    _voiceTicker =
        _scope?.launch {
            while (isActive) {
                delay(1.seconds)
                _onVoiceTick()
            }
        }
}

private fun _HomeShellState._onVoiceTick() {
    if (!mounted || !_voiceState.isRecording) return
    val startedAt = _voiceStartedAt ?: return
    val elapsed = DateTime.now().difference(startedAt)
    _setHomeState { _voiceState = voiceRecordingTicked(_voiceState, elapsed) }
    if (voiceRecordingReachedLimit(elapsed)) {
        unawaited { _stopVoiceRecording() }
    }
}

/** Stop recording and move to the review state where the user sends or discards the clip. */
private suspend fun _HomeShellState._stopVoiceRecording() {
    if (!_voiceState.isRecording) return
    _voiceTicker?.cancel()
    _voiceTicker = null
    val startedAt = _voiceStartedAt
    val elapsed = if (startedAt == null) _voiceState.elapsed else DateTime.now().difference(startedAt)
    _voiceStartedAt = null
    val path: String?
    try {
        path = _voiceRecorder.stop()
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        if (!mounted) return
        _setHomeState { _voiceState = VoiceRecorderState().copyWith(error = userFacingErrorMessage(error)) }
        return
    }
    if (!mounted) return
    _setHomeState { _voiceState = voiceRecordingStopped(state = _voiceState, path = path, elapsed = elapsed) }
}

/** Discard the current recording or review clip and reset to idle. */
internal suspend fun _HomeShellState._cancelVoiceRecording() {
    if (_voiceState.isIdle) return
    _voiceTicker?.cancel()
    _voiceTicker = null
    _voiceStartedAt = null
    val wasRecording = _voiceState.isRecording
    val path = _voiceState.recordingPath
    _setHomeState { _voiceState = voiceRecordingCancelled() }
    try {
        if (wasRecording) {
            _voiceRecorder.cancel()
        } else {
            // Reviewed-but-discarded clip: drop the temp file.
            _voiceRecorder.discardClip(path)
        }
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        // Cleanup failures are non-fatal; the state is already reset.
    }
}

/**
 * Upload and send the reviewed voice clip as a playable audio message,
 * reusing the shared file transfer pipeline (pending bubble + transfer).
 *
 * Once the clip's bytes are read and the pending bubble is created we reset
 * the recorder and retract the voice panel, so the in-flight clip lives in
 * the message list (with its own pending spinner) instead of trapping the
 * composer on the send screen. Delivery success/failure is then reflected on
 * the bubble, mirroring how text and file messages behave.
 */
private suspend fun _HomeShellState._sendVoiceMessage() {
    val room = _selectedRoom
    val path = _voiceState.recordingPath
    if (room == null || path == null || !_voiceState.canSend) return

    val duration = _voiceState.elapsed
    val quotes = _messageQuoteDrafts[room.id] ?: emptyList()

    _setHomeState { _voiceState = voiceSendStarted(_voiceState) }

    val bytes: ByteArray
    try {
        bytes = _voiceRecorder.readClip(path)
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        if (!mounted) return
        // Read failed before any bubble existed — keep the review panel open so
        // the user can retry sending the same clip.
        _setHomeState { _voiceState = voiceSendFailed(state = _voiceState, failure = error) }
        return
    }

    // Bytes are buffered in memory now; the clip file is no longer needed.
    val recorder = _voiceRecorder
    unawaited { recorder.discardClip(path) }

    val filename = voiceMessageFilename(DateTime.now(), extension = _voiceRecorder.clipExtension)
    var clientMessageId: String? = null
    try {
        val sent =
            _messagesController.sendVoiceMessage(
                roomId = room.id,
                sender = _currentUser.toSummary().copyWith(roomDisplayName = _selectedRoom?.personalProfile?.displayName),
                filename = filename,
                sizeBytes = bytes.size.toLong(),
                mimeType = _voiceRecorder.clipMimeType,
                duration = duration,
                quotes = quotes,
                readBytes = { bytes },
                onPending = { pending ->
                    clientMessageId = pending.clientMessageId
                    if (mounted) {
                        _setHomeState {
                            _applyFileMessageStatePatch(
                                _messagesController.patchPendingFileMessage(
                                    messages = _messages,
                                    fileTransfers = _fileTransfers,
                                    pending = pending,
                                ),
                            )
                            // The clip now lives as a pending bubble; retract the recorder.
                            _voiceState = voiceSendSucceeded()
                            if (quotes.isNotEmpty()) {
                                _messageQuoteDrafts = LinkedHashMap(_messageQuoteDrafts).apply { remove(room.id) }
                                _saveDraftInState()
                            }
                        }
                        _composerPanelController.closePanel()
                    }
                },
                onProgress =
                    FileMessageProgressHandler { pending, sentBytes, totalBytes ->
                        if (!mounted) return@FileMessageProgressHandler
                        val patch =
                            _messagesController.patchFileTransferProgress(
                                messages = _messages,
                                fileTransfers = _fileTransfers,
                                pending = pending,
                                sentBytes = sentBytes,
                                totalBytes = totalBytes,
                            ) ?: return@FileMessageProgressHandler
                        _setHomeState { _applyFileMessageStatePatch(patch) }
                    },
                onUploaded = { pending, attachment ->
                    if (mounted) {
                        _setHomeState {
                            _applyFileMessageStatePatch(
                                _messagesController.patchUploadedFileMessage(
                                    messages = _messages,
                                    fileTransfers = _fileTransfers,
                                    pending = pending,
                                    attachment = attachment,
                                ),
                            )
                        }
                    }
                },
            )
        if (!mounted || _selectedServerId != room.id) return
        val activeClientMessageId = clientMessageId
        if (activeClientMessageId != null) {
            _setHomeState {
                _applyFileMessageStatePatch(
                    _messagesController.patchSentFileMessage(
                        messages = _messages,
                        fileTransfers = _fileTransfers,
                        clientMessageId = activeClientMessageId,
                        sent = sent,
                    ),
                )
            }
        }
        unawaited { _loadServers() }
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        if (!mounted) return
        val activeClientMessageId = clientMessageId
        if (activeClientMessageId != null) {
            // The pending bubble owns the failure now — surface "发送失败" on it.
            _setHomeState {
                _applyFileMessageStatePatch(
                    _messagesController.patchFailedFileMessage(
                        messages = _messages,
                        fileTransfers = _fileTransfers,
                        clientMessageId = activeClientMessageId,
                        failure = error,
                    ),
                )
            }
        } else {
            // Failed before the bubble existed — fall back to the review panel.
            _setHomeState { _voiceState = voiceSendFailed(state = _voiceState, failure = error) }
        }
    }
}

/**
 * Stop the in-progress recording and immediately send it. Mirrors the "发送"
 * control shown while recording.
 */
internal suspend fun _HomeShellState._finishAndSendVoice() {
    if (_voiceState.isRecording) {
        _stopVoiceRecording()
        if (!mounted) return
    }
    if (_voiceState.canSend) {
        _sendVoiceMessage()
    }
}

/** Apply a [FileMessageStatePatch] to the local message + transfer maps. */
private fun _HomeShellState._applyFileMessageStatePatch(patch: FileMessageStatePatch) {
    _messages = patch.messages
    _fileTransfers = patch.fileTransfers
}

// --- Composer attachments ---------------------------------------------
//
// Files picked here upload immediately (eagerly), so by send time the assets
// are usually already in hand and the message goes out in one round trip. The
// message itself still rides the shared composed-send path as a `file` message
// carrying the assets as attachments — a workaround until the backend offers a
// single multipart "send message with files" call. Eager upload can leave an
// orphan asset behind if the user removes a chip or never sends; that cleanup
// is the backend's responsibility (asset TTL / unreferenced sweep).

/** View models for the chips shown above the composer input. */
internal val _HomeShellState._stagedAttachmentViews: List<ComposerAttachmentView>
    get() =
        _stagedAttachments.map { entry ->
            ComposerAttachmentView(
                id = entry.id,
                filename = entry.file.name,
                status = entry.status,
                sizeBytes = entry.sizeBytes,
                mimeType = entry.file.mimeType,
                progress = entry.progress,
                errorMessage = entry.errorMessage,
            )
        }

/**
 * Open the system file picker (multi-select), stage the chosen files on the
 * composer, and start uploading each one right away. Does not send anything;
 * the finished assets go out with the next message.
 */
internal suspend fun _HomeShellState._pickAttachments() {
    if (_selectedRoom == null || _pickingAttachments) return
    val files: List<SelectedFile>
    _setHomeState {
        _pickingAttachments = true
        _sendError = null
    }
    try {
        files = _fileSelectionService.openFiles()
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        if (!mounted) return
        _setHomeState { _sendError = userFacingErrorMessage(error) }
        return
    } finally {
        if (mounted) _setHomeState { _pickingAttachments = false }
    }
    if (!mounted || files.isEmpty()) return

    _stageAttachmentFiles(files)
}

/**
 * Whether the clipboard holds a component or an image to stage, so the
 * composer's paste can be routed here instead of the default text paste.
 */
internal suspend fun _HomeShellState._canPasteAttachments(): Boolean {
    if (_selectedRoom == null) return false
    return try {
        if (_clipboardMessageComponent() != null) return true
        val paths = _clipboardService.readFilePaths()
        if (normalizedFilePaths(paths).isNotEmpty()) return true
        val image = _clipboardService.readImageFile()
        image != null
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        false
    }
}

internal suspend fun _HomeShellState._pasteAttachments(): Boolean {
    if (_selectedRoom == null) return false
    try {
        val clipboardComponent = _clipboardMessageComponent()
        if (clipboardComponent != null) {
            if (!mounted) return false
            return _stageClipboardComponent(clipboardComponent)
        }
        val paths = _clipboardService.readFilePaths()
        if (!mounted) return false
        val normalized = normalizedFilePaths(paths)
        if (normalized.isNotEmpty()) {
            _stageAttachmentPaths(normalized)
            return true
        }
        val image = _clipboardService.readImageFile()
        if (!mounted || image == null) return false
        val filename =
            clipboardImageUploadFilename(
                timestamp = DateTime.now(),
                sequence = ++_clipboardImagePasteSerial,
                mimeType = image.mimeType,
            )
        _stageAttachmentFiles(listOf(SelectedFile.fromBytes(name = filename, mimeType = image.mimeType, bytes = image.bytes)))
        return true
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        if (!mounted) return false
        _setHomeState { _sendError = clipboardFilesReadFailureMessage(error) }
        return false
    }
}

/**
 * Puts a pasted song or playlist into the composer. Returns false when no
 * room is selected; otherwise true, even when it only explains why the
 * component can't be added yet.
 */
private fun _HomeShellState._stageClipboardComponent(component: Message): Boolean {
    if (_composerController.text.trim().isNotEmpty()) {
        _setHomeState { _sendError = "请先清空输入内容，再粘贴歌单或歌曲组件" }
        return true
    }
    if (_stagedAttachments.isNotEmpty()) {
        _setHomeState { _sendError = "请先移除已选文件，再粘贴歌单或歌曲" }
        return true
    }
    val roomId = _selectedServerId ?: return false
    _setHomeState {
        val next = LinkedHashMap(_messageComponentDrafts)
        next[roomId] = component
        _messageComponentDrafts = next
        _sendError = null
        _saveDraftInState()
    }
    _composerPanelController.closePanel()
    _composerPanelController.requestInputFocus()
    return true
}

private suspend fun _HomeShellState._clipboardMessageComponent(): Message? =
    _messageComponentFromClipboardText(_clipboardService.readText())

private fun _HomeShellState._messageComponentFromClipboardText(clipboardText: String?): Message? {
    val embedded = messageComponentFromClipboardText(clipboardText)
    if (embedded != null) {
        _copiedMessageComponent = embedded
        return embedded
    }
    val copied = _copiedMessageComponent
    if (copied != null && !copied.isRemoved && messageClipboardTextMatches(copied, clipboardText)) {
        return copied
    }
    // Compatibility for text copied with an older Gang Chat build: if the
    // immutable source message is still loaded, recover the component from
    // its complete human-readable clipboard representation.
    for (message in _messages.asReversed()) {
        if (message.pending || message.isRemoved) continue
        if (message.playlistAttachment == null && message.musicTrackAttachment == null) {
            continue
        }
        if (messageClipboardTextMatches(message, clipboardText)) {
            _copiedMessageComponent = message
            return message
        }
    }
    return null
}

private fun _HomeShellState._stageAttachmentPaths(paths: Iterable<String>) {
    val normalized = normalizedFilePaths(paths)
    if (normalized.isEmpty()) return
    _stageAttachmentFiles(_fileSelectionService.filesFromPaths(normalized))
}

private fun _HomeShellState._stageAttachmentFiles(files: List<SelectedFile>) {
    if (_selectedRoom == null || files.isEmpty()) return
    val fresh = mutableListOf<_StagedAttachment>()
    _setHomeState {
        val roomId = _selectedServerId
        if (roomId != null && _messageComponentDrafts.containsKey(roomId)) {
            _messageComponentDrafts = LinkedHashMap(_messageComponentDrafts).apply { remove(roomId) }
        }
        for (file in files) {
            val entry = _StagedAttachment(id = _messagesController.mintClientId("att"), file = file)
            _stagedAttachments.add(entry)
            fresh.add(entry)
        }
        _saveDraftInState()
        _sendError = null
    }

    for (entry in fresh) {
        unawaited { _uploadStagedAttachment(entry) }
    }
}

/**
 * Upload (or re-upload) a single staged file, streaming progress into its
 * chip. Safe to call again after a failure to retry.
 */
private suspend fun _HomeShellState._uploadStagedAttachment(entry: _StagedAttachment) {
    if (!_isStagedAttachmentActive(entry)) return
    _setHomeState {
        entry.status = ComposerAttachmentStatus.uploading
        entry.progress = null
        entry.error = null
        entry.errorMessage = null
    }

    try {
        val bytes = entry.file.readAsBytes()
        // Drop out if the chip was removed (or the room switched) while reading.
        if (!_isStagedAttachmentActive(entry)) return
        if (bytes.isEmpty()) throw IllegalStateException(fileEmptyMessage())
        val asset =
            _messagesController.uploadFileAsset(
                bytes = bytes,
                filename = entry.file.name,
                controller = entry.uploadController,
                onProgress = { sentBytes, totalBytes ->
                    if (mounted && _isStagedAttachmentActive(entry)) {
                        _setHomeState {
                            entry.sizeBytes = totalBytes
                            entry.progress = if (totalBytes > 0) sentBytes.toDouble() / totalBytes else null
                        }
                    }
                },
            )
        if (!_isStagedAttachmentActive(entry)) return
        _setHomeState {
            entry.asset = asset
            entry.sizeBytes = asset.sizeBytes
            entry.progress = 1.0
            entry.status = ComposerAttachmentStatus.uploaded
        }
    } catch (error: UploadCancelledException) {
        // Cancellation means the chip was removed; nothing left to update.
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        if (!mounted || !_isStagedAttachmentActive(entry)) return
        _setHomeState {
            entry.error = error
            entry.errorMessage = _stagedAttachmentErrorMessage(error)
            entry.status = ComposerAttachmentStatus.failed
        }
    }
}

/** Retry a staged upload that previously failed. */
internal fun _HomeShellState._retryAttachment(id: String) {
    val entry = _stagedAttachmentById(id)
    if (entry == null || entry.isUploaded) return
    unawaited { _uploadStagedAttachment(entry) }
}

/** Drop a single staged file, cancelling its upload if still in flight. */
internal fun _HomeShellState._removeAttachment(id: String) {
    val index = _stagedAttachments.indexOfFirst { entry -> entry.id == id }
    if (index < 0) return
    val entry = _stagedAttachments[index]
    entry.uploadController.cancel()
    _setHomeState {
        _stagedAttachments.removeAt(index)
        _saveDraftInState()
    }
}

private fun _HomeShellState._stagedAttachmentById(id: String): _StagedAttachment? {
    for (entry in _stagedAttachments) {
        if (entry.id == id) return entry
    }
    return null
}

/**
 * Send the staged files as one composed message alongside any typed [body].
 * Uploads were kicked off at pick time, so this waits for any still in
 * flight, refuses to send while uploads are failed, then collects the
 * finished assets.
 */
private suspend fun _HomeShellState._sendStagedAttachments(body: String) {
    val room = _selectedRoom
    if (room == null || _stagedAttachments.isEmpty() || _sending) return

    // Wait for any uploads still in flight before deciding what to send.
    val pending = _stagedAttachments.filter { entry -> entry.status == ComposerAttachmentStatus.uploading }
    if (pending.isNotEmpty()) {
        _setHomeState {
            _sending = true
            _sendError = null
        }
        waitAll(pending.map { entry -> future { _awaitUpload(entry) } })
        if (!mounted || _selectedServerId != room.id) {
            if (mounted) _setHomeState { _sending = false }
            return
        }
        _setHomeState { _sending = false }
    }

    // Refuse to send a partial batch; surface the failures for retry/removal.
    if (_stagedAttachments.any { entry -> entry.status == ComposerAttachmentStatus.failed }) {
        _setHomeState { _sendError = "部分文件上传失败，请重试或移除后再发送" }
        return
    }

    val attachments =
        _stagedAttachments.mapNotNull { entry ->
            val asset = entry.asset ?: return@mapNotNull null
            _messagesController.fileAttachment(name = entry.file.name, asset = asset)
        }
    if (attachments.isEmpty()) return

    // Hand off to the shared composed send (which owns the pending/sent/failed
    // bubble and the _sending flag).
    _setHomeState { _stagedAttachments.clear() }
    _sendComposed(body = body, type = "file", attachments = attachments, clearComposer = true)
}

/** Block until [entry]'s in-flight upload settles (success or failure). */
private suspend fun _HomeShellState._awaitUpload(entry: _StagedAttachment) {
    while (mounted && _stagedAttachments.any { it === entry } && entry.status == ComposerAttachmentStatus.uploading) {
        delayed(50.milliseconds)
    }
}

private fun _stagedAttachmentErrorMessage(error: Exception): String {
    if (error is IllegalStateException) return error.message ?: ""
    return userFacingErrorMessage(error, fallback = "上传失败，请重试")
}
