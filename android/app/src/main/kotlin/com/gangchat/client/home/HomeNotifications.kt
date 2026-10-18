package com.gangchat.client.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AssignmentTurnedIn
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.gangchat.client.R
import com.gangchat.client.app.RoomNotificationDateRange
import com.gangchat.client.app.RoomNotificationFilter
import com.gangchat.client.app.RoomNotificationItem
import com.gangchat.client.app.RoomNotificationItemType
import com.gangchat.client.app.canReviewNotificationInvite
import com.gangchat.client.app.canWithdrawNotificationApplication
import com.gangchat.client.app.isAcceptedRoomInvite
import com.gangchat.client.app.isActionablePendingRoomInvite
import com.gangchat.client.app.isApprovedRoomApplication
import com.gangchat.client.app.isInvalidPendingRoomInvite
import com.gangchat.client.app.isPendingRoomApplication
import com.gangchat.client.app.isPendingRoomInvite
import com.gangchat.client.app.isRejectedRoomApplication
import com.gangchat.client.app.kRoomEventNotificationCreatorTransferDemoted
import com.gangchat.client.app.kRoomEventNotificationMemberRemoved
import com.gangchat.client.app.kRoomEventNotificationMentioned
import com.gangchat.client.app.kRoomEventNotificationRoleDemoted
import com.gangchat.client.app.kRoomEventNotificationRolePromoted
import com.gangchat.client.app.roomApplicationReviewActionLabel
import com.gangchat.client.app.roomApplicationStatusLabel
import com.gangchat.client.app.roomEventNotificationRoleActionLabel
import com.gangchat.client.app.roomInviteDecisionLabel
import com.gangchat.client.app.roomInviteRoleLabel
import com.gangchat.client.app.roomInviteTimestampLabel
import com.gangchat.client.app.roomNotificationDateLabel
import com.gangchat.client.app.roomNotificationRoleLabel
import com.gangchat.client.app.roomNotificationRoomAvatarKey
import com.gangchat.client.app.roomNotificationRoomAvatarLabel
import com.gangchat.client.app.roomNotificationRoomAvatarUrl
import com.gangchat.client.app.roomNotificationRoomCardEnabled
import com.gangchat.client.app.roomNotificationRoomLabel
import com.gangchat.client.app.roomNotificationUserAvatarKey
import com.gangchat.client.app.roomNotificationUserAvatarLabel
import com.gangchat.client.app.roomNotificationUserAvatarUrl
import com.gangchat.client.app.roomNotificationUserLabel
import com.gangchat.client.app.roomNotificationsForView
import com.gangchat.client.app.userFacingErrorMessage
import com.gangchat.client.foundation.DateTime
import com.gangchat.client.foundation.unawaited
import com.gangchat.client.protocol.CurrentUser
import com.gangchat.client.protocol.PublicRoom
import com.gangchat.client.protocol.RoomApplication
import com.gangchat.client.protocol.RoomEventNotification
import com.gangchat.client.protocol.RoomInvite
import com.gangchat.client.protocol.UserSummary
import com.gangchat.client.ui.Avatar
import com.gangchat.client.ui.BadgeDot
import com.gangchat.client.ui.Button
import com.gangchat.client.ui.ButtonIcon
import com.gangchat.client.ui.ButtonTone
import com.gangchat.client.ui.DialogFrame
import com.gangchat.client.ui.FloatingNotice
import com.gangchat.client.ui.FloatingNoticeEmitter
import com.gangchat.client.ui.FloatingNoticeTone
import com.gangchat.client.ui.HighlightedText
import com.gangchat.client.ui.Input
import com.gangchat.client.ui.LocalAppConfig
import com.gangchat.client.ui.PressableSurface
import com.gangchat.client.ui.ReadOnlySelectableText
import com.gangchat.client.ui.ResponsiveDialogAction
import com.gangchat.client.ui.ResponsiveDialogActionBar
import com.gangchat.client.ui.RoleBadge
import com.gangchat.client.ui.Segment
import com.gangchat.client.ui.SegmentedControl
import com.gangchat.client.ui.SettingsScaffold
import com.gangchat.client.ui.UiCheckbox
import com.gangchat.client.ui.UiColors
import com.gangchat.client.ui.UiContextMenuItem
import com.gangchat.client.ui.UiContextMenuSection
import com.gangchat.client.ui.UiContextMenuTriggerRegion
import com.gangchat.client.ui.UiRadii
import com.gangchat.client.ui.UiTypography
import com.gangchat.client.ui.copyWith
import com.gangchat.client.ui.showFloatingErrorNotice
import com.gangchat.client.ui.showFloatingSuccessNotice
import com.gangchat.client.ui.showUiContextMenu
import com.gangchat.client.widgets.BuildContext
import com.gangchat.client.widgets.Curves
import com.gangchat.client.widgets.EdgeInsets
import com.gangchat.client.widgets.Icon
import com.gangchat.client.widgets.Navigator
import com.gangchat.client.widgets.StatefulWidget
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.TextEditingController
import com.gangchat.client.widgets.ViewState
import com.gangchat.client.widgets.decoration
import com.gangchat.client.widgets.padding
import com.gangchat.client.widgets.rememberBuildContext
import com.gangchat.client.widgets.showDialog
import kotlin.math.ceil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

typealias RoomInviteReviewCallback = suspend (invite: RoomInvite, accept: Boolean) -> Unit
typealias RoomApplicationWithdrawCallback = suspend (application: RoomApplication) -> Unit
typealias RoomEventOpenCallback = (notification: RoomEventNotification) -> Unit
typealias RoomNotificationCopyCallback = suspend (item: RoomNotificationItem) -> Unit
typealias RoomNotificationDeleteCallback = suspend (item: RoomNotificationItem) -> Unit
typealias RoomNotificationsDeleteCallback = suspend (items: List<RoomNotificationItem>) -> Unit

/** Dart's inline `Future<UserSummary> Function(String roomId, UserSummary user)` resolver type. */
typealias RoomUserProfileResolver = suspend (roomId: String, user: UserSummary) -> UserSummary

// Test tags of the pane's controls (Dart's `ValueKey`s).
const val homeNotificationsRefreshButtonTag = "home-notifications-refresh-button"
const val homeNotificationsDateFilterButtonTag = "home-notifications-date-filter-button"
const val homeNotificationsSelectButtonTag = "home-notifications-select-button"
const val homeNotificationsSelectAllTag = "home-notifications-select-all"

/**
 * The notification centre: room invites, the user's own join applications
 * (and their reviews) and room events, with keyword, type and date filters,
 * batch selection and a copy/delete context menu.
 *
 * The home shell owns the data and the network calls; this pane keeps only
 * the view state (query, filter, date range, selection).
 */
@Composable
fun HomeNotificationsPane(
    invites: List<RoomInvite>,
    applications: List<RoomApplication>,
    roomNotifications: List<RoomEventNotification>,
    loading: Boolean,
    error: String?,
    busyInviteId: String?,
    busyApplicationId: String?,
    onClose: () -> Unit,
    onRefresh: () -> Unit,
    onReviewInvite: RoomInviteReviewCallback,
    onWithdrawApplication: RoomApplicationWithdrawCallback,
    currentUser: CurrentUser,
    onOpenRoom: (PublicRoom) -> Unit,
    onOpenRoomEvent: RoomEventOpenCallback,
    modifier: Modifier = Modifier,
    onCopyNotification: RoomNotificationCopyCallback? = null,
    onDeleteNotification: RoomNotificationDeleteCallback? = null,
    onDeleteNotifications: RoomNotificationsDeleteCallback? = null,
    onResolveRoomProfile: RoomProfileResolver? = null,
    onResolveRoomUserProfile: RoomUserProfileResolver? = null,
) = StatefulWidget(
    HomeNotificationsPaneWidget(
        invites, applications, roomNotifications, currentUser, loading, error, busyInviteId, busyApplicationId, onClose,
        onRefresh, onReviewInvite, onWithdrawApplication, onOpenRoom, onOpenRoomEvent, onCopyNotification,
        onDeleteNotification, onDeleteNotifications, onResolveRoomProfile, onResolveRoomUserProfile, modifier,
    ),
) { _HomeNotificationsPaneState() }

class HomeNotificationsPaneWidget(
    val invites: List<RoomInvite>,
    val applications: List<RoomApplication>,
    val roomNotifications: List<RoomEventNotification>,
    val currentUser: CurrentUser,
    val loading: Boolean,
    val error: String?,
    val busyInviteId: String?,
    val busyApplicationId: String?,
    val onClose: () -> Unit,
    val onRefresh: () -> Unit,
    val onReviewInvite: RoomInviteReviewCallback,
    val onWithdrawApplication: RoomApplicationWithdrawCallback,
    val onOpenRoom: (PublicRoom) -> Unit,
    val onOpenRoomEvent: RoomEventOpenCallback,
    val onCopyNotification: RoomNotificationCopyCallback?,
    val onDeleteNotification: RoomNotificationDeleteCallback?,
    val onDeleteNotifications: RoomNotificationsDeleteCallback?,
    val onResolveRoomProfile: RoomProfileResolver?,
    val onResolveRoomUserProfile: RoomUserProfileResolver?,
    val modifier: Modifier,
)

private class _HomeNotificationsPaneState : ViewState<HomeNotificationsPaneWidget>() {
    private val _searchController = TextEditingController()
    private var _filter = RoomNotificationFilter.all
    private lateinit var _defaultDateRange: RoomNotificationDateRange
    private lateinit var _dateRange: RoomNotificationDateRange
    private var _selectionMode = false
    private var _selectedNotificationIds: Set<String> = emptySet()
    private var _query = ""
    private var _lastSearchText = ""

    private val _handleSearchChanged: () -> Unit = {
        val text = _searchController.text
        if (text != _lastSearchText) {
            _lastSearchText = text
            setState {
                _query = text
                if (_selectionMode) _selectedNotificationIds = emptySet()
            }
        }
    }

    override fun initState() {
        _defaultDateRange = _defaultDateRangeFor(widget.currentUser)
        _dateRange = _defaultDateRange
        _searchController.addListener(_handleSearchChanged)
    }

    override fun didUpdateWidget(oldWidget: HomeNotificationsPaneWidget) {
        if (oldWidget.currentUser.createdAt == widget.currentUser.createdAt) return
        val wasUsingDefault = _dateRange == _defaultDateRange
        _defaultDateRange = _defaultDateRangeFor(widget.currentUser)
        if (wasUsingDefault ||
            _dateRange.startDate.isBefore(_defaultDateRange.startDate) ||
            _dateRange.endDate.isAfter(_defaultDateRange.endDate)
        ) {
            _dateRange = _defaultDateRange
        }
    }

    override fun dispose() {
        _searchController.removeListener(_handleSearchChanged)
        _searchController.dispose()
    }

    private fun _defaultDateRangeFor(user: CurrentUser): RoomNotificationDateRange =
        RoomNotificationDateRange.defaultFor(accountCreatedAt = user.createdAt, today = DateTime.now())

    private fun _refreshDefaultDateRangeForToday() {
        val nextDefault = _defaultDateRangeFor(widget.currentUser)
        if (nextDefault == _defaultDateRange) return
        val wasUsingDefault = _dateRange == _defaultDateRange
        _defaultDateRange = nextDefault
        if (wasUsingDefault) _dateRange = nextDefault
    }

    private suspend fun _showDateRangeFilter() {
        val initialRange = _dateRange
        val defaultRange = _defaultDateRange
        val result =
            showDialog<RoomNotificationDateRange>(context) { _ ->
                _NotificationDateRangeDialog(initialRange = initialRange, defaultRange = defaultRange)
            }
        if (!mounted || result == null || result == _dateRange) return
        setState {
            _dateRange = result
            if (_selectionMode) _selectedNotificationIds = emptySet()
        }
    }

    private fun _toggleSelectionMode() {
        setState {
            _selectionMode = !_selectionMode
            if (!_selectionMode) _selectedNotificationIds = emptySet()
        }
    }

    private fun _toggleNotificationSelection(item: RoomNotificationItem) {
        setState {
            val ids = LinkedHashSet(_selectedNotificationIds)
            if (!ids.add(item.id)) ids.remove(item.id)
            _selectedNotificationIds = ids
        }
    }

    private fun _toggleVisibleNotificationSelection(visibleItems: List<RoomNotificationItem>) {
        val visibleIds = visibleItems.map { it.id }.toSet()
        if (visibleIds.isEmpty()) return
        setState {
            val ids = LinkedHashSet(_selectedNotificationIds)
            if (visibleIds.all(ids::contains)) ids.removeAll(visibleIds) else ids.addAll(visibleIds)
            _selectedNotificationIds = ids
        }
    }

    private fun _clearSelectedNotifications() {
        if (_selectedNotificationIds.isEmpty()) return
        setState { _selectedNotificationIds = emptySet() }
    }

    @Composable
    override fun build() {
        _refreshDefaultDateRangeForToday()
        val visibleItems =
            roomNotificationsForView(
                invites = widget.invites,
                applications = widget.applications,
                roomEvents = widget.roomNotifications,
                query = _query,
                filter = _filter,
                dateRange = _dateRange,
            )
        val hasActiveDateFilter = _dateRange != _defaultDateRange
        val rawNotificationCount = widget.invites.size + widget.applications.size + widget.roomNotifications.size
        val allVisibleSelected = visibleItems.isNotEmpty() && visibleItems.all { _selectedNotificationIds.contains(it.id) }
        SettingsScaffold(
            modifier = widget.modifier,
            icon = Icons.Filled.NotificationsNone,
            title = "通知",
            onBack = widget.onClose,
            headerAction = {
                ButtonIcon(
                    modifier = Modifier.testTag(homeNotificationsRefreshButtonTag),
                    tooltip = "刷新通知",
                    icon = { Icon(Icons.Filled.Refresh) },
                    onPressed = if (widget.loading) null else widget.onRefresh,
                    loading = widget.loading && rawNotificationCount > 0,
                    size = 38.dp,
                )
            },
            pinned = {
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Input(
                            modifier = Modifier.weight(1f),
                            controller = _searchController,
                            hintText = "搜索通知",
                            prefixIcon = Icons.Filled.Search,
                            showClearButton = true,
                        )
                        Spacer(Modifier.width(10.dp))
                        ButtonIcon(
                            modifier = Modifier.testTag(homeNotificationsDateFilterButtonTag),
                            tooltip = "筛选通知日期",
                            icon = { Icon(Icons.Outlined.CalendarMonth) },
                            selected = hasActiveDateFilter,
                            onPressed = { unawaited { _showDateRangeFilter() } },
                            size = Input.defaultHeight,
                        )
                        Spacer(Modifier.width(8.dp))
                        ButtonIcon(
                            modifier = Modifier.testTag(homeNotificationsSelectButtonTag),
                            tooltip = if (_selectionMode) "退出批量管理" else "批量管理",
                            icon = { Icon(Icons.Outlined.Checklist) },
                            selected = _selectionMode,
                            onPressed = ::_toggleSelectionMode,
                            size = Input.defaultHeight,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (_selectionMode) {
                            UiCheckbox(
                                modifier = Modifier.testTag(homeNotificationsSelectAllTag),
                                value = allVisibleSelected,
                                onChanged = if (visibleItems.isEmpty()) null else { _ -> _toggleVisibleNotificationSelection(visibleItems) },
                                tooltip = if (allVisibleSelected) "取消全选当前通知" else "全选当前通知",
                                semanticLabel = "全选当前通知",
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        SegmentedControl(
                            modifier = Modifier.weight(1f),
                            expanded = true,
                            value = _filter,
                            segments =
                                listOf(
                                    Segment(value = RoomNotificationFilter.all, label = "全部", icon = Icons.Outlined.Inbox),
                                    Segment(value = RoomNotificationFilter.invites, label = "邀请", icon = Icons.Filled.MailOutline),
                                    Segment(
                                        value = RoomNotificationFilter.applications,
                                        label = "申请",
                                        icon = Icons.Outlined.AssignmentTurnedIn,
                                    ),
                                    Segment(
                                        value = RoomNotificationFilter.roomNotifications,
                                        label = "房间",
                                        icon = Icons.Outlined.MeetingRoom,
                                    ),
                                ),
                            onChanged = { value ->
                                setState {
                                    _filter = value
                                    if (_selectionMode) _selectedNotificationIds = emptySet()
                                }
                            },
                        )
                    }
                }
            },
            body = {
                _NotificationsBody(
                    items = visibleItems,
                    loading = widget.loading,
                    error = widget.error,
                    rawNotificationCount = rawNotificationCount,
                    query = _query,
                    filter = _filter,
                    hasActiveDateFilter = hasActiveDateFilter,
                    busyInviteId = widget.busyInviteId,
                    busyApplicationId = widget.busyApplicationId,
                    onRefresh = widget.onRefresh,
                    onReviewInvite = widget.onReviewInvite,
                    onWithdrawApplication = widget.onWithdrawApplication,
                    currentUser = widget.currentUser,
                    onOpenRoom = widget.onOpenRoom,
                    onOpenRoomEvent = widget.onOpenRoomEvent,
                    onCopyNotification = widget.onCopyNotification,
                    onDeleteNotification = widget.onDeleteNotification,
                    onDeleteNotifications = widget.onDeleteNotifications,
                    selectionMode = _selectionMode,
                    selectedNotificationIds = _selectedNotificationIds,
                    onToggleNotificationSelection = ::_toggleNotificationSelection,
                    onClearSelectedNotifications = ::_clearSelectedNotifications,
                    onResolveRoomProfile = widget.onResolveRoomProfile,
                    onResolveRoomUserProfile = widget.onResolveRoomUserProfile,
                )
            },
        )
    }
}

// Test tags of the date range dialog (Dart's `ValueKey`s).
const val notificationDateResetButtonTag = "notification-date-reset-button"
const val notificationDateConfirmButtonTag = "notification-date-confirm-button"
const val notificationStartDateFieldTag = "notification-start-date-field"
const val notificationEndDateFieldTag = "notification-end-date-field"

@Composable
private fun _NotificationDateRangeDialog(
    initialRange: RoomNotificationDateRange,
    defaultRange: RoomNotificationDateRange,
    title: String = "筛选通知日期",
    description: String = "选择包含首尾日期的通知时间区间。",
) = StatefulWidget(_NotificationDateRangeDialogWidget(initialRange, defaultRange, title, description)) {
    _NotificationDateRangeDialogState()
}

private class _NotificationDateRangeDialogWidget(
    val initialRange: RoomNotificationDateRange,
    val defaultRange: RoomNotificationDateRange,
    val title: String,
    val description: String,
)

private class _NotificationDateRangeDialogState : ViewState<_NotificationDateRangeDialogWidget>() {
    private lateinit var _startDate: DateTime
    private lateinit var _endDate: DateTime

    override fun initState() {
        _startDate = widget.initialRange.startDate
        _endDate = widget.initialRange.endDate
    }

    private suspend fun _pickDate(start: Boolean) {
        val selected = if (start) _startDate else _endDate
        val firstDate = widget.defaultRange.startDate
        val lastDate = widget.defaultRange.endDate
        val picked =
            showDialog<DateTime>(context) { _ ->
                _NotificationCalendarDialog(
                    title = if (start) "选择开始日期" else "选择结束日期",
                    initialDate = selected,
                    firstDate = firstDate,
                    lastDate = lastDate,
                )
            }
        if (!mounted || picked == null) return
        setState {
            if (start) {
                _startDate = picked
                if (_endDate.isBefore(picked)) _endDate = picked
            } else {
                _endDate = picked
                if (_startDate.isAfter(picked)) _startDate = picked
            }
        }
    }

    @Composable
    override fun build() {
        DialogFrame(
            title = widget.title,
            icon = Icons.Outlined.CalendarMonth,
            maxWidth = 420.dp,
            actionBar = {
                ResponsiveDialogActionBar(
                    leadingActionCount = 1,
                    actions =
                        listOf(
                            ResponsiveDialogAction(
                                buttonKey = notificationDateResetButtonTag,
                                label = "重置",
                                icon = Icons.Filled.RestartAlt,
                                onPressed = { Navigator.of(context).pop(widget.defaultRange) },
                            ),
                            ResponsiveDialogAction(label = "取消", onPressed = { Navigator.of(context).pop() }),
                            ResponsiveDialogAction(
                                buttonKey = notificationDateConfirmButtonTag,
                                label = "应用",
                                icon = Icons.Filled.Check,
                                tone = ButtonTone.primary,
                                onPressed = {
                                    Navigator.of(context).pop(RoomNotificationDateRange(startDate = _startDate, endDate = _endDate))
                                },
                            ),
                        ),
                )
            },
            child = {
                Column(Modifier.fillMaxWidth()) {
                    Text(widget.description, style = UiTypography.label.copyWith(color = UiColors.textMuted))
                    Spacer(Modifier.height(16.dp))
                    _NotificationDateField(
                        modifier = Modifier.testTag(notificationStartDateFieldTag),
                        label = "开始日期",
                        date = _startDate,
                        onPressed = { unawaited { _pickDate(start = true) } },
                    )
                    Spacer(Modifier.height(14.dp))
                    _NotificationDateField(
                        modifier = Modifier.testTag(notificationEndDateFieldTag),
                        label = "结束日期",
                        date = _endDate,
                        onPressed = { unawaited { _pickDate(start = false) } },
                    )
                }
            },
        )
    }
}

/**
 * The date range dialog for other date filters (the room message history).
 * Suspends until it closes; null when cancelled.
 */
suspend fun showDateRangeFilterDialog(
    context: BuildContext,
    initialRange: RoomNotificationDateRange,
    defaultRange: RoomNotificationDateRange,
    title: String = "筛选日期",
    description: String = "选择包含首尾日期的时间区间。",
): RoomNotificationDateRange? =
    showDialog(context) { _ ->
        _NotificationDateRangeDialog(initialRange = initialRange, defaultRange = defaultRange, title = title, description = description)
    }

@Composable
private fun _NotificationDateField(label: String, date: DateTime, onPressed: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Text(label, style = UiTypography.label)
        Spacer(Modifier.height(8.dp))
        PressableSurface(
            modifier = Modifier.fillMaxWidth(),
            height = 40.dp,
            onPressed = onPressed,
            padding = EdgeInsets.symmetric(horizontal = 12.dp),
            backgroundColor = UiColors.background,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CalendarToday, size = 16.dp, color = UiColors.textMuted)
                Spacer(Modifier.width(10.dp))
                Text(roomNotificationDateLabel(date), modifier = Modifier.weight(1f), style = UiTypography.body)
                Icon(Icons.Filled.ChevronRight, size = 18.dp, color = UiColors.textMuted)
            }
        }
    }
}

/** Test tag of a notification row's context-menu region (Dart's `ValueKey('notification-context-row-<id>')`). */
fun notificationContextRowTag(itemId: String) = "notification-context-row-$itemId"

/** Test tag of a notification's batch checkbox (Dart's `ValueKey('notification-selectbox-<id>')`). */
fun notificationSelectBoxTag(itemId: String) = "notification-selectbox-$itemId"

@Composable
private fun _NotificationsBody(
    items: List<RoomNotificationItem>,
    loading: Boolean,
    error: String?,
    rawNotificationCount: Int,
    query: String,
    filter: RoomNotificationFilter,
    hasActiveDateFilter: Boolean,
    busyInviteId: String?,
    busyApplicationId: String?,
    onRefresh: () -> Unit,
    onReviewInvite: RoomInviteReviewCallback,
    onWithdrawApplication: RoomApplicationWithdrawCallback,
    currentUser: CurrentUser,
    onOpenRoom: (PublicRoom) -> Unit,
    onOpenRoomEvent: RoomEventOpenCallback,
    onCopyNotification: RoomNotificationCopyCallback?,
    onDeleteNotification: RoomNotificationDeleteCallback?,
    onDeleteNotifications: RoomNotificationsDeleteCallback?,
    selectionMode: Boolean,
    selectedNotificationIds: Set<String>,
    onToggleNotificationSelection: (RoomNotificationItem) -> Unit,
    onClearSelectedNotifications: () -> Unit,
    onResolveRoomProfile: RoomProfileResolver?,
    onResolveRoomUserProfile: RoomUserProfileResolver?,
) {
    val context = rememberBuildContext()

    @Composable
    fun withErrorNotice(child: @Composable () -> Unit) {
        val message = error?.trim()
        if (message == null || message.isEmpty()) {
            child()
            return
        }
        FloatingNoticeEmitter(
            notices = listOf(FloatingNotice(message = message, tone = FloatingNoticeTone.error, duration = null)),
            child = child,
        )
    }

    if (loading && rawNotificationCount == 0) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = UiColors.accent,
                strokeWidth = 2.2.dp,
                trackColor = Color.Transparent,
                strokeCap = StrokeCap.Square,
            )
        }
        return
    }

    if (error != null && rawNotificationCount == 0) {
        withErrorNotice {
            _NotificationEmptyState(
                icon = Icons.Filled.ErrorOutline,
                title = "通知加载失败",
                subtitle = "请稍后重试",
                actionLabel = "重试",
                onAction = onRefresh,
                danger = true,
            )
        }
        return
    }

    if (items.isEmpty()) {
        withErrorNotice {
            _NotificationEmptyState(
                icon = _emptyIcon(filter),
                title = _emptyTitle(filter = filter, query = query, hasActiveDateFilter = hasActiveDateFilter),
            )
        }
        return
    }

    val listHorizontalPadding =
        if (HomeAdaptiveLayout.usesCompactLayout(context)) CompactActivityLayout.compactNotificationListHorizontalPadding else 22.dp
    val selectedItems = items.filter { selectedNotificationIds.contains(it.id) }
    withErrorNotice {
        _SynchronizedNotificationLayout(itemKeys = items.map { it.id }.toSet()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = listHorizontalPadding, end = listHorizontalPadding, bottom = 22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items, key = { it.id }) { item ->
                    val card: @Composable (Modifier) -> Unit = { cardModifier ->
                        _NotificationContextMenuRegion(
                            modifier = cardModifier.testTag(notificationContextRowTag(item.id)),
                            item = item,
                            selectionMode = selectionMode,
                            selectedItems = selectedItems,
                            onToggleSelection = if (selectionMode) ({ onToggleNotificationSelection(item) }) else null,
                            onCopyNotification = onCopyNotification,
                            onDeleteNotification = onDeleteNotification,
                            onDeleteNotifications = onDeleteNotifications,
                            onSelectionDeleted = onClearSelectedNotifications,
                            childBuilder = { contextMenuActive ->
                                when (item.type) {
                                    RoomNotificationItemType.invite ->
                                        _RoomInviteNotificationRow(
                                            invite = item.invite!!,
                                            query = query,
                                            busy = busyInviteId == item.invite!!.id,
                                            busyInviteId = busyInviteId,
                                            contextMenuActive = contextMenuActive,
                                            onReviewInvite = onReviewInvite,
                                            currentUser = currentUser,
                                            onOpenRoom = onOpenRoom,
                                            onResolveRoomProfile = onResolveRoomProfile,
                                            onResolveRoomUserProfile = onResolveRoomUserProfile,
                                        )
                                    RoomNotificationItemType.applicationRequested ->
                                        _RoomApplicationRequestNotificationRow(
                                            application = item.application!!,
                                            query = query,
                                            busy = busyApplicationId == item.application!!.id,
                                            busyApplicationId = busyApplicationId,
                                            contextMenuActive = contextMenuActive,
                                            onWithdrawApplication = onWithdrawApplication,
                                            currentUser = currentUser,
                                            onOpenRoom = onOpenRoom,
                                            onResolveRoomProfile = onResolveRoomProfile,
                                            onResolveRoomUserProfile = onResolveRoomUserProfile,
                                        )
                                    RoomNotificationItemType.applicationReviewed ->
                                        _RoomApplicationReviewNotificationRow(
                                            application = item.application!!,
                                            query = query,
                                            contextMenuActive = contextMenuActive,
                                            currentUser = currentUser,
                                            onOpenRoom = onOpenRoom,
                                            onResolveRoomProfile = onResolveRoomProfile,
                                            onResolveRoomUserProfile = onResolveRoomUserProfile,
                                        )
                                    RoomNotificationItemType.roomEvent ->
                                        _RoomEventNotificationRow(
                                            notification = item.roomEvent!!,
                                            query = query,
                                            contextMenuActive = contextMenuActive,
                                            currentUser = currentUser,
                                            onOpenRoom = onOpenRoom,
                                            onOpenRoomEvent = onOpenRoomEvent,
                                            onResolveRoomProfile = onResolveRoomProfile,
                                            onResolveRoomUserProfile = onResolveRoomUserProfile,
                                        )
                                }
                            },
                        )
                    }
                    if (!selectionMode) {
                        // ListView children span the list's width.
                        card(Modifier.fillMaxWidth())
                    } else {
                        val selected = selectedNotificationIds.contains(item.id)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            UiCheckbox(
                                modifier = Modifier.testTag(notificationSelectBoxTag(item.id)),
                                value = selected,
                                onChanged = { _ -> onToggleNotificationSelection(item) },
                                tooltip = if (selected) "取消选择通知" else "选择通知",
                                semanticLabel = "选择通知",
                            )
                            Spacer(Modifier.width(8.dp))
                            card(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun _NotificationContextMenuRegion(
    item: RoomNotificationItem,
    childBuilder: @Composable (contextMenuActive: Boolean) -> Unit,
    selectionMode: Boolean,
    selectedItems: List<RoomNotificationItem>,
    modifier: Modifier = Modifier,
    onToggleSelection: (() -> Unit)? = null,
    onCopyNotification: RoomNotificationCopyCallback? = null,
    onDeleteNotification: RoomNotificationDeleteCallback? = null,
    onDeleteNotifications: RoomNotificationsDeleteCallback? = null,
    onSelectionDeleted: (() -> Unit)? = null,
) = StatefulWidget(
    _NotificationContextMenuRegionWidget(
        item, childBuilder, selectionMode, selectedItems, onToggleSelection, onCopyNotification, onDeleteNotification,
        onDeleteNotifications, onSelectionDeleted, modifier,
    ),
) { _NotificationContextMenuRegionState() }

private class _NotificationContextMenuRegionWidget(
    val item: RoomNotificationItem,
    val childBuilder: @Composable (contextMenuActive: Boolean) -> Unit,
    val selectionMode: Boolean,
    val selectedItems: List<RoomNotificationItem>,
    val onToggleSelection: (() -> Unit)?,
    val onCopyNotification: RoomNotificationCopyCallback?,
    val onDeleteNotification: RoomNotificationDeleteCallback?,
    val onDeleteNotifications: RoomNotificationsDeleteCallback?,
    val onSelectionDeleted: (() -> Unit)?,
    val modifier: Modifier,
)

private class _NotificationContextMenuRegionState : ViewState<_NotificationContextMenuRegionWidget>() {
    private var _contextMenuActive = false

    @Composable
    override fun build() {
        val itemSelected = widget.selectedItems.any { selected -> selected.id == widget.item.id }
        UiContextMenuTriggerRegion(
            modifier = widget.modifier,
            onTap = if (widget.selectionMode) widget.onToggleSelection else null,
            onTriggered = ::_showContextMenu,
        ) {
            widget.childBuilder(_contextMenuActive || itemSelected)
        }
    }

    private fun _showContextMenu(position: DpOffset) {
        val contextItems = if (widget.selectionMode) widget.selectedItems else listOf(widget.item)
        val canDelete = widget.onDeleteNotifications != null || widget.onDeleteNotification != null
        if (contextItems.isEmpty() || (widget.onCopyNotification == null && !canDelete)) return
        setState { _contextMenuActive = true }
        val copy = widget.onCopyNotification
        unawaited {
            try {
                showUiContextMenu(
                    context,
                    position = position,
                    sections =
                        buildList {
                            if (contextItems.size == 1) {
                                add(
                                    UiContextMenuSection(
                                        listOf(
                                            UiContextMenuItem(
                                                label = "复制",
                                                shortcut = "Ctrl+C",
                                                onPressed =
                                                    if (copy == null) null else ({ unawaited { _copyNotification(contextItems.single()) } }),
                                            ),
                                        ),
                                    ),
                                )
                            }
                            if (canDelete) {
                                add(
                                    UiContextMenuSection(
                                        listOf(
                                            UiContextMenuItem(
                                                label = "删除",
                                                onPressed = { unawaited { _confirmDeleteNotifications(contextItems) } },
                                            ),
                                        ),
                                    ),
                                )
                            }
                        },
                )
            } finally {
                if (mounted) setState { _contextMenuActive = false }
            }
        }
    }

    private suspend fun _copyNotification(item: RoomNotificationItem) {
        try {
            widget.onCopyNotification!!(item)
            if (!mounted) return
            showFloatingSuccessNotice(context, "已复制")
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (!mounted) return
            showFloatingErrorNotice(context, userFacingErrorMessage(error, fallback = "复制通知失败"))
        }
    }

    private suspend fun _confirmDeleteNotifications(items: List<RoomNotificationItem>) {
        val confirmed = showDialog<Boolean>(context) { _ -> _NotificationDeleteConfirmDialog(itemCount = items.size) }
        if (confirmed != true || !mounted) return
        try {
            val deleteNotifications = widget.onDeleteNotifications
            if (deleteNotifications != null) {
                deleteNotifications(items)
            } else {
                for (item in items) widget.onDeleteNotification!!(item)
            }
            if (!mounted) return
            widget.onSelectionDeleted?.invoke()
            showFloatingSuccessNotice(context, if (items.size == 1) "已删除" else "已删除 ${items.size} 条通知")
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (!mounted) return
            showFloatingErrorNotice(context, userFacingErrorMessage(error, fallback = "删除通知失败"))
        }
    }
}

/**
 * Keeps interactive notification controls from also toggling batch selection.
 *
 * Some shared controls use raw pointer listeners rather than a gesture
 * detector. This wrapper claims the primary tap before the row-level
 * selection detector while letting the control itself keep receiving
 * pointer events.
 */
@Composable
private fun _NotificationInteractiveTarget(modifier: Modifier = Modifier, child: @Composable () -> Unit) {
    Box(modifier.pointerInput(Unit) { detectTapGestures(onTap = {}) }, propagateMinConstraints = true) { child() }
}

@Composable
private fun _NotificationDeleteConfirmDialog(itemCount: Int) {
    val context = rememberBuildContext()
    DialogFrame(
        title = if (itemCount == 1) "删除通知" else "删除选中的通知",
        icon = Icons.Outlined.WarningAmber,
        adaptiveActions =
            listOf(
                ResponsiveDialogAction(label = "取消", onPressed = { Navigator.of(context).pop(false) }),
                ResponsiveDialogAction(label = "删除", tone = ButtonTone.danger, onPressed = { Navigator.of(context).pop(true) }),
            ),
        child = {
            Text(if (itemCount == 1) "删除后将无法恢复" else "确定删除所有选中的${itemCount}条通知？", style = UiTypography.body)
        },
    )
}

/** Test tag of a notification row's surface (Dart's `ValueKey('notification-row-surface-<kind>:<id>')`). */
fun notificationRowSurfaceTag(layoutKey: String) = "notification-row-surface-$layoutKey"

/**
 * The row surface (Dart's `AnimatedContainer`): selected colours while the
 * context menu is open or the row is batch-selected. Like Flutter's
 * container, the child is inset by the 1 dp border.
 */
@Composable
private fun _NotificationRowSurface(
    tag: String,
    contextMenuActive: Boolean,
    restingBorder: Color,
    child: @Composable () -> Unit,
) {
    val color by animateColorAsState(
        if (contextMenuActive) UiColors.selected else UiColors.surface,
        tween(90, easing = Curves.easeOutCubic),
        label = "notificationRowColor",
    )
    val border by animateColorAsState(
        if (contextMenuActive) UiColors.selectedBorder else restingBorder,
        tween(90, easing = Curves.easeOutCubic),
        label = "notificationRowBorder",
    )
    Box(
        Modifier
            .testTag(tag)
            .fillMaxWidth()
            .decoration(color, RoundedCornerShape(UiRadii.lg), border)
            .padding(1.dp)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        propagateMinConstraints = true,
    ) {
        child()
    }
}

private val _labelTextStyle get() = UiTypography.label.copyWith(color = UiColors.text, fontWeight = FontWeight.W600)
private val _labelSecondaryStyle get() = UiTypography.label.copyWith(color = UiColors.textSecondary, fontWeight = FontWeight.W600)
private val _labelMutedStyle get() = UiTypography.label.copyWith(color = UiColors.textMuted)

@Composable
private fun _NotificationTime(time: String, tag: String) {
    HighlightedText(
        text = time,
        query = "",
        modifier = Modifier.width(116.dp).testTag(tag),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = _labelMutedStyle,
    )
}

@Composable
private fun _RoomInviteNotificationRow(
    invite: RoomInvite,
    query: String,
    contextMenuActive: Boolean,
    busy: Boolean,
    busyInviteId: String?,
    onReviewInvite: RoomInviteReviewCallback,
    currentUser: CurrentUser,
    onOpenRoom: (PublicRoom) -> Unit,
    onResolveRoomProfile: RoomProfileResolver?,
    onResolveRoomUserProfile: RoomUserProfileResolver?,
) {
    val config = LocalAppConfig.current
    val room = invite.room
    val inviter = invite.inviter
    val inviterName = roomNotificationUserLabel(inviter, userExists = invite.inviterExists)
    val inviterAvatarLabel = roomNotificationUserAvatarLabel(inviter, userExists = invite.inviterExists)
    val role = roomInviteRoleLabel(inviter)
    val time = roomInviteTimestampLabel(invite.createdAt)
    val invalid = isInvalidPendingRoomInvite(invite)
    val inviterAvatarTarget: @Composable () -> Unit = {
        _NotificationInteractiveTarget {
            UserHoverCard(
                user = inviter.copyWith(isDeleted = !invite.inviterExists),
                currentUser = currentUser,
                onResolveProfile = _roomScopedResolver(onResolveRoomUserProfile, room.id),
                onResolveRoomProfile = onResolveRoomProfile,
                onEnterCommonRoom = onOpenRoom,
            ) {
                _notificationUserAvatar(
                    tag = "notification-inviter-avatar-${invite.id}",
                    user = inviter,
                    userExists = invite.inviterExists,
                    label = inviterAvatarLabel,
                    imageUrl = config.resolveAssetUrl(roomNotificationUserAvatarUrl(inviter, userExists = invite.inviterExists)),
                    defaultAvatarKey = roomNotificationUserAvatarKey(inviter, userExists = invite.inviterExists),
                )
            }
        }
    }
    val inviteAction: @Composable () -> Unit = {
        when {
            invalid -> _InvalidInviteLabel(invite = invite, query = query)
            isPendingRoomInvite(invite) ->
                _InviteDecisionActions(
                    invite = invite,
                    busy = busy,
                    enabled = canReviewNotificationInvite(invite = invite, busyInviteId = busyInviteId),
                    onReviewInvite = onReviewInvite,
                )
            else -> _ProcessedInviteLabel(invite = invite, query = query)
        }
    }
    val roomTarget: @Composable (Modifier) -> Unit = { targetModifier ->
        _InlineRoomTarget(
            modifier = targetModifier,
            room = room,
            inviteId = invite.id,
            query = query,
            roomExists = invite.roomExists,
            currentUser = currentUser,
            onResolveRoomProfile = onResolveRoomProfile,
            onResolveRoomUserProfile = onResolveRoomUserProfile,
            onOpenRoom = onOpenRoom,
        )
    }
    _NotificationNewMarker(show = isActionablePendingRoomInvite(invite)) {
        _NotificationRowSurface(
            tag = notificationRowSurfaceTag("invite:${invite.id}"),
            contextMenuActive = contextMenuActive,
            restingBorder = if (isPendingRoomInvite(invite) && !invalid) UiColors.accentBorder else UiColors.border,
        ) {
            _ResponsiveNotificationContent(
                layoutKey = "invite:${invite.id}",
                layoutSignature = invite,
                compact = {
                    _CompactNotificationRow(
                        time = time,
                        timeTag = "notification-time-${invite.id}",
                        body = {
                            _AndroidNotificationBody(tag = "notification-mobile-body-${invite.id}") {
                                inviterAvatarTarget()
                                HighlightedText(
                                    text = inviterName,
                                    query = query,
                                    modifier = Modifier.testTag("notification-inviter-name-${invite.id}"),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = _labelTextStyle,
                                )
                                _InviteRoleBadge(
                                    modifier = Modifier.testTag("notification-inviter-role-${invite.id}"),
                                    label = role,
                                    query = query,
                                )
                                Text(
                                    "邀请您加入",
                                    modifier = Modifier.testTag("notification-invite-action-${invite.id}"),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = _labelSecondaryStyle,
                                )
                                roomTarget(Modifier)
                            }
                        },
                        trailing = { Box(Modifier.width(78.dp), contentAlignment = Alignment.CenterEnd) { inviteAction() } },
                    )
                },
                wide = {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        _NotificationTime(time, "notification-time-${invite.id}")
                        Spacer(Modifier.width(10.dp))
                        inviterAvatarTarget()
                        Spacer(Modifier.width(8.dp))
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            HighlightedText(
                                text = inviterName,
                                query = query,
                                modifier = Modifier.weight(3f, fill = false).testTag("notification-inviter-name-${invite.id}"),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = _labelTextStyle,
                            )
                            Spacer(Modifier.width(7.dp))
                            _InviteRoleBadge(
                                modifier = Modifier.weight(2f, fill = false).testTag("notification-inviter-role-${invite.id}"),
                                label = role,
                                query = query,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "邀请您加入",
                                modifier = Modifier.weight(2f, fill = false).testTag("notification-invite-action-${invite.id}"),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = _labelSecondaryStyle,
                            )
                            Spacer(Modifier.width(8.dp))
                            roomTarget(Modifier.weight(4f, fill = false))
                        }
                        Spacer(Modifier.width(12.dp))
                        Box(Modifier.width(88.dp), contentAlignment = Alignment.CenterEnd) { inviteAction() }
                    }
                },
            )
        }
    }
}

@Composable
private fun _RoomApplicationRequestNotificationRow(
    application: RoomApplication,
    query: String,
    contextMenuActive: Boolean,
    busy: Boolean,
    busyApplicationId: String?,
    onWithdrawApplication: RoomApplicationWithdrawCallback,
    currentUser: CurrentUser,
    onOpenRoom: (PublicRoom) -> Unit,
    onResolveRoomProfile: RoomProfileResolver?,
    onResolveRoomUserProfile: RoomUserProfileResolver?,
) {
    val room = application.room
    val time = roomInviteTimestampLabel(application.createdAt)
    val applicationAction: @Composable (Dp) -> Unit = { width ->
        if (isPendingRoomApplication(application)) {
            _ApplicationWithdrawAction(
                application = application,
                busy = busy,
                enabled = canWithdrawNotificationApplication(application = application, busyApplicationId = busyApplicationId),
                onWithdrawApplication = onWithdrawApplication,
                width = width,
            )
        } else {
            _ProcessedApplicationLabel(application = application, query = query)
        }
    }
    val roomTarget: @Composable (Modifier) -> Unit = { targetModifier ->
        _InlineRoomTarget(
            modifier = targetModifier,
            room = room,
            inviteId = "application-${application.id}",
            query = query,
            roomExists = true,
            currentUser = currentUser,
            onResolveRoomProfile = onResolveRoomProfile,
            onResolveRoomUserProfile = onResolveRoomUserProfile,
            onOpenRoom = onOpenRoom,
        )
    }
    _NotificationNewMarker(show = isPendingRoomApplication(application)) {
        _NotificationRowSurface(
            tag = notificationRowSurfaceTag("application-requested:${application.id}"),
            contextMenuActive = contextMenuActive,
            restingBorder = if (isPendingRoomApplication(application)) UiColors.accentBorder else UiColors.border,
        ) {
            _ResponsiveNotificationContent(
                layoutKey = "application-requested:${application.id}",
                layoutSignature = application,
                compact = {
                    _CompactNotificationRow(
                        time = time,
                        timeTag = "notification-application-time-${application.id}",
                        body = {
                            _AndroidNotificationBody(tag = "notification-mobile-body-application-${application.id}") {
                                Text(
                                    "您已申请加入",
                                    modifier = Modifier.testTag("notification-application-request-action-${application.id}"),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = _labelSecondaryStyle,
                                )
                                roomTarget(Modifier)
                            }
                        },
                        trailing = { Box(Modifier.width(78.dp), contentAlignment = Alignment.CenterEnd) { applicationAction(78.dp) } },
                    )
                },
                wide = {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        _NotificationTime(time, "notification-application-time-${application.id}")
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "您已申请加入",
                            modifier = Modifier.testTag("notification-application-request-action-${application.id}"),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = _labelSecondaryStyle,
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.weight(1f)) { roomTarget(Modifier) }
                        Spacer(Modifier.width(12.dp))
                        Box(Modifier.width(88.dp), contentAlignment = Alignment.CenterEnd) { applicationAction(88.dp) }
                    }
                },
            )
        }
    }
}

@Composable
private fun _RoomApplicationReviewNotificationRow(
    application: RoomApplication,
    query: String,
    contextMenuActive: Boolean,
    currentUser: CurrentUser,
    onOpenRoom: (PublicRoom) -> Unit,
    onResolveRoomProfile: RoomProfileResolver?,
    onResolveRoomUserProfile: RoomUserProfileResolver?,
) {
    val config = LocalAppConfig.current
    val room = application.room
    val reviewer = application.reviewer!!
    val reviewerName = roomNotificationUserLabel(reviewer, userExists = application.reviewerExists)
    val reviewerAvatarLabel = roomNotificationUserAvatarLabel(reviewer, userExists = application.reviewerExists)
    val role = roomInviteRoleLabel(reviewer)
    val time = roomInviteTimestampLabel(application.reviewedAt ?: application.updatedAt)
    val reviewerAvatarTarget: @Composable () -> Unit = {
        _NotificationInteractiveTarget {
            UserHoverCard(
                user = reviewer.copyWith(isDeleted = !application.reviewerExists),
                currentUser = currentUser,
                onResolveProfile = _roomScopedResolver(onResolveRoomUserProfile, room.id),
                onResolveRoomProfile = onResolveRoomProfile,
                onEnterCommonRoom = onOpenRoom,
            ) {
                _notificationUserAvatar(
                    tag = "notification-application-reviewer-avatar-${application.id}",
                    user = reviewer,
                    userExists = application.reviewerExists,
                    label = reviewerAvatarLabel,
                    imageUrl = config.resolveAssetUrl(roomNotificationUserAvatarUrl(reviewer, userExists = application.reviewerExists)),
                    defaultAvatarKey = roomNotificationUserAvatarKey(reviewer, userExists = application.reviewerExists),
                )
            }
        }
    }
    val roomTarget: @Composable (Modifier) -> Unit = { targetModifier ->
        _InlineRoomTarget(
            modifier = targetModifier,
            room = room,
            inviteId = "application-reviewed-${application.id}",
            query = query,
            roomExists = true,
            currentUser = currentUser,
            onResolveRoomProfile = onResolveRoomProfile,
            onResolveRoomUserProfile = onResolveRoomUserProfile,
            onOpenRoom = onOpenRoom,
        )
    }
    _NotificationRowSurface(
        tag = notificationRowSurfaceTag("application-reviewed:${application.id}"),
        contextMenuActive = contextMenuActive,
        restingBorder = UiColors.border,
    ) {
        _ResponsiveNotificationContent(
            layoutKey = "application-reviewed:${application.id}",
            layoutSignature = application,
            compact = {
                _CompactNotificationRow(
                    time = time,
                    timeTag = "notification-application-review-time-${application.id}",
                    body = {
                        _AndroidNotificationBody(tag = "notification-mobile-body-reviewed-${application.id}") {
                            reviewerAvatarTarget()
                            HighlightedText(
                                text = reviewerName,
                                query = query,
                                modifier = Modifier.testTag("notification-application-reviewer-name-${application.id}"),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = _labelTextStyle,
                            )
                            _InviteRoleBadge(
                                modifier = Modifier.testTag("notification-application-reviewer-role-${application.id}"),
                                label = role,
                                query = query,
                            )
                            HighlightedText(
                                text = roomApplicationReviewActionLabel(application),
                                query = query,
                                modifier = Modifier.testTag("notification-application-review-action-${application.id}"),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = _labelSecondaryStyle,
                            )
                            roomTarget(Modifier)
                        }
                    },
                )
            },
            wide = {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    _NotificationTime(time, "notification-application-review-time-${application.id}")
                    Spacer(Modifier.width(10.dp))
                    reviewerAvatarTarget()
                    Spacer(Modifier.width(8.dp))
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        HighlightedText(
                            text = reviewerName,
                            query = query,
                            modifier = Modifier.weight(3f, fill = false).testTag("notification-application-reviewer-name-${application.id}"),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = _labelTextStyle,
                        )
                        Spacer(Modifier.width(7.dp))
                        _InviteRoleBadge(
                            modifier = Modifier.weight(2f, fill = false).testTag("notification-application-reviewer-role-${application.id}"),
                            label = role,
                            query = query,
                        )
                        Spacer(Modifier.width(8.dp))
                        HighlightedText(
                            text = roomApplicationReviewActionLabel(application),
                            query = query,
                            modifier = Modifier.weight(2f, fill = false).testTag("notification-application-review-action-${application.id}"),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = _labelSecondaryStyle,
                        )
                        Spacer(Modifier.width(8.dp))
                        roomTarget(Modifier.weight(4f, fill = false))
                    }
                    Spacer(Modifier.width(12.dp))
                    Spacer(Modifier.width(88.dp))
                }
            },
        )
    }
}

@Composable
private fun _RoomEventNotificationRow(
    notification: RoomEventNotification,
    query: String,
    contextMenuActive: Boolean,
    currentUser: CurrentUser,
    onOpenRoom: (PublicRoom) -> Unit,
    onOpenRoomEvent: RoomEventOpenCallback,
    onResolveRoomProfile: RoomProfileResolver?,
    onResolveRoomUserProfile: RoomUserProfileResolver?,
) {
    val time = roomInviteTimestampLabel(notification.createdAt)
    _NotificationNewMarker(show = notification.isUnread, markerTag = "notification-room-event-new-${notification.id}") {
        _NotificationRowSurface(
            tag = notificationRowSurfaceTag("room-event:${notification.id}"),
            contextMenuActive = contextMenuActive,
            restingBorder = UiColors.border,
        ) {
            _ResponsiveNotificationContent(
                layoutKey = "room-event:${notification.id}",
                layoutSignature = notification,
                compact = {
                    _CompactNotificationRow(
                        time = time,
                        timeTag = "notification-room-event-time-${notification.id}",
                        body = {
                            _RoomEventAndroidContent(
                                notification, query, currentUser, onOpenRoom, onResolveRoomProfile, onResolveRoomUserProfile,
                            )
                        },
                        trailing = { _RoomEventJumpAction(notification = notification, onOpenRoomEvent = onOpenRoomEvent) },
                    )
                },
                wide = {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        _NotificationTime(time, "notification-room-event-time-${notification.id}")
                        Spacer(Modifier.width(10.dp))
                        Box(Modifier.weight(1f)) {
                            _RoomEventContent(notification, query, currentUser, onOpenRoom, onResolveRoomProfile, onResolveRoomUserProfile)
                        }
                        Spacer(Modifier.width(12.dp))
                        Box(Modifier.width(88.dp), contentAlignment = Alignment.CenterEnd) {
                            _RoomEventJumpAction(notification = notification, onOpenRoomEvent = onOpenRoomEvent)
                        }
                    }
                },
            )
        }
    }
}

/** Dart's `_RoomEventNotificationRow._buildContent` (the wide row). */
@Composable
private fun _RoomEventContent(
    notification: RoomEventNotification,
    query: String,
    currentUser: CurrentUser,
    onOpenRoom: (PublicRoom) -> Unit,
    onResolveRoomProfile: RoomProfileResolver?,
    onResolveRoomUserProfile: RoomUserProfileResolver?,
) {
    val actor = notification.actor
    val roleLabel = roomNotificationRoleLabel(notification.toRole)

    @Composable
    fun roomTarget(modifier: Modifier) =
        _RoomEventRoomTarget(notification, query, currentUser, onOpenRoom, onResolveRoomProfile, onResolveRoomUserProfile, modifier)

    @Composable
    fun actorTarget(modifier: Modifier) =
        _InlineUserTarget(
            modifier = modifier,
            user = actor!!,
            roomId = notification.room.id,
            targetId = notification.id,
            query = query,
            userExists = notification.actorExists,
            currentUser = currentUser,
            onResolveRoomProfile = onResolveRoomProfile,
            onResolveRoomUserProfile = onResolveRoomUserProfile,
            onOpenRoom = onOpenRoom,
        )

    @Composable
    fun targetRoleBadge(modifier: Modifier) =
        _InviteRoleBadge(
            modifier = modifier.testTag("notification-room-event-target-role-${notification.id}"),
            label = roleLabel,
            query = query,
        )

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        when (notification.type) {
            kRoomEventNotificationMemberRemoved -> {
                _NotificationText(text = "您被", query = query)
                if (actor != null) {
                    Spacer(Modifier.width(8.dp))
                    actorTarget(Modifier.weight(4f, fill = false))
                    Spacer(Modifier.width(8.dp))
                }
                _NotificationText(text = "踢出了", query = query)
                Spacer(Modifier.width(8.dp))
                roomTarget(Modifier.weight(4f, fill = false))
            }
            kRoomEventNotificationRolePromoted, kRoomEventNotificationRoleDemoted -> {
                _NotificationText(text = "您在", query = query)
                Spacer(Modifier.width(8.dp))
                roomTarget(Modifier.weight(4f, fill = false))
                Spacer(Modifier.width(8.dp))
                if (actor != null) {
                    _NotificationText(text = "中被", query = query)
                    Spacer(Modifier.width(8.dp))
                    actorTarget(Modifier.weight(4f, fill = false))
                    Spacer(Modifier.width(8.dp))
                } else {
                    _NotificationText(text = "中", query = query)
                    Spacer(Modifier.width(8.dp))
                }
                _NotificationText(text = roomEventNotificationRoleActionLabel(notification), query = query)
                Spacer(Modifier.width(7.dp))
                targetRoleBadge(Modifier.weight(1f, fill = false))
            }
            kRoomEventNotificationCreatorTransferDemoted -> {
                _NotificationText(text = "您在", query = query)
                Spacer(Modifier.width(8.dp))
                roomTarget(Modifier.weight(4f, fill = false))
                Spacer(Modifier.width(8.dp))
                _NotificationText(text = "中降职为", query = query)
                Spacer(Modifier.width(7.dp))
                targetRoleBadge(Modifier.weight(1f, fill = false))
            }
            kRoomEventNotificationMentioned -> {
                val preview = notification.messagePreview?.trim()
                _NotificationText(text = "您在", query = query)
                Spacer(Modifier.width(8.dp))
                roomTarget(Modifier.weight(4f, fill = false))
                Spacer(Modifier.width(8.dp))
                if (actor != null) {
                    _NotificationText(text = "中被", query = query)
                    Spacer(Modifier.width(8.dp))
                    actorTarget(Modifier.weight(4f, fill = false))
                    Spacer(Modifier.width(8.dp))
                    _NotificationText(text = "提及", query = query)
                } else {
                    _NotificationText(text = "中被提及", query = query)
                }
                if (preview != null && preview.isNotEmpty()) {
                    Spacer(Modifier.width(4.dp))
                    _RoomEventInfoButton(message = preview)
                }
            }
            else -> {
                _NotificationText(text = "您在", query = query)
                Spacer(Modifier.width(8.dp))
                roomTarget(Modifier.weight(4f, fill = false))
                Spacer(Modifier.width(8.dp))
                _NotificationText(text = "收到了房间通知", query = query)
            }
        }
    }
}

/** Dart's `_RoomEventNotificationRow._buildAndroidContent` (the compact row). */
@Composable
private fun _RoomEventAndroidContent(
    notification: RoomEventNotification,
    query: String,
    currentUser: CurrentUser,
    onOpenRoom: (PublicRoom) -> Unit,
    onResolveRoomProfile: RoomProfileResolver?,
    onResolveRoomUserProfile: RoomUserProfileResolver?,
) {
    val actor = notification.actor
    val roleLabel = roomNotificationRoleLabel(notification.toRole)
    val bodyTag = "notification-mobile-body-room-event-${notification.id}"

    @Composable
    fun roomTarget() =
        _RoomEventRoomTarget(notification, query, currentUser, onOpenRoom, onResolveRoomProfile, onResolveRoomUserProfile, Modifier)

    @Composable
    fun actorTarget() =
        _InlineUserTarget(
            user = actor!!,
            roomId = notification.room.id,
            targetId = notification.id,
            query = query,
            userExists = notification.actorExists,
            currentUser = currentUser,
            onResolveRoomProfile = onResolveRoomProfile,
            onResolveRoomUserProfile = onResolveRoomUserProfile,
            onOpenRoom = onOpenRoom,
        )

    @Composable
    fun targetRoleBadge() =
        _InviteRoleBadge(
            modifier = Modifier.testTag("notification-room-event-target-role-${notification.id}"),
            label = roleLabel,
            query = query,
        )

    _AndroidNotificationBody(tag = bodyTag) {
        when (notification.type) {
            kRoomEventNotificationMemberRemoved -> {
                _NotificationText(text = "您被", query = query)
                if (actor != null) actorTarget()
                _NotificationText(text = "踢出了", query = query)
                roomTarget()
            }
            kRoomEventNotificationRolePromoted, kRoomEventNotificationRoleDemoted -> {
                _NotificationText(text = "您在", query = query)
                roomTarget()
                _NotificationText(text = if (actor == null) "中" else "中被", query = query)
                if (actor != null) actorTarget()
                _NotificationText(text = roomEventNotificationRoleActionLabel(notification), query = query)
                targetRoleBadge()
            }
            kRoomEventNotificationCreatorTransferDemoted -> {
                _NotificationText(text = "您在", query = query)
                roomTarget()
                _NotificationText(text = "中降职为", query = query)
                targetRoleBadge()
            }
            kRoomEventNotificationMentioned -> {
                val preview = notification.messagePreview?.trim()
                _NotificationText(text = "您在", query = query)
                roomTarget()
                _NotificationText(text = if (actor == null) "中被提及" else "中被", query = query)
                if (actor != null) {
                    actorTarget()
                    _NotificationText(text = "提及", query = query)
                }
                if (preview != null && preview.isNotEmpty()) _RoomEventInfoButton(message = preview)
            }
            else -> {
                _NotificationText(text = "您在", query = query)
                roomTarget()
                _NotificationText(text = "收到了房间通知", query = query)
            }
        }
    }
}

@Composable
private fun _RoomEventRoomTarget(
    notification: RoomEventNotification,
    query: String,
    currentUser: CurrentUser,
    onOpenRoom: (PublicRoom) -> Unit,
    onResolveRoomProfile: RoomProfileResolver?,
    onResolveRoomUserProfile: RoomUserProfileResolver?,
    modifier: Modifier,
) {
    _InlineRoomTarget(
        modifier = modifier,
        room = notification.room,
        inviteId = "room-event-${notification.id}",
        query = query,
        roomExists = notification.roomExists,
        currentUser = currentUser,
        onResolveRoomProfile = onResolveRoomProfile,
        onResolveRoomUserProfile = onResolveRoomUserProfile,
        onOpenRoom = onOpenRoom,
    )
}

@Composable
private fun _RoomEventJumpAction(notification: RoomEventNotification, onOpenRoomEvent: RoomEventOpenCallback) {
    val enabled = notification.roomExists && notification.room.joined
    _NotificationInteractiveTarget {
        ButtonIcon(
            tooltip = if (notification.messageId == null) "进入房间" else "跳转到消息",
            icon = { Icon(Icons.Rounded.LocationOn) },
            tone = ButtonTone.primary,
            onPressed = if (enabled) ({ onOpenRoomEvent(notification) }) else null,
            size = 34.dp,
        )
    }
}

@Composable
private fun _RoomEventInfoButton(message: String) {
    val context = rememberBuildContext()
    _NotificationInteractiveTarget {
        HoverCardAnchor(
            resetKey = message,
            cardWidth = hoverInfoCardWidth(context, message),
            gap = 8.dp,
            cardBuilder = { _ -> _RoomEventInfoCard(message = message) },
        ) {
            Box(Modifier.size(16.dp), propagateMinConstraints = true) {
                Icon(Icons.Outlined.Info, size = 14.dp, color = UiColors.accent)
            }
        }
    }
}

@Composable
private fun _RoomEventInfoCard(message: String) {
    val hoverScope = HoverCardTapRegionScope.current
    Box(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
        ReadOnlySelectableText(
            value = message,
            maxLines = 8,
            style = UiTypography.body.copyWith(color = UiColors.text, fontSize = 12.0, height = 1.38),
            contextMenuTapRegionGroupId = hoverScope?.tapRegionGroup,
            onContextMenuOpenChanged = hoverScope?.onOverlayActivityChanged,
        )
    }
}

@Composable
private fun _notificationAvatarSize(): Dp = if (_NotificationLayoutMode.usesCompactLayout()) CompactActivityLayout.avatarSize else 34.dp

@Composable
private fun _notificationUserAvatar(
    tag: String,
    user: UserSummary,
    userExists: Boolean,
    label: String,
    imageUrl: String?,
    defaultAvatarKey: String,
) {
    val size = _notificationAvatarSize()
    val role = user.roomRole?.trim()?.lowercase()
    // Android (and Windows) show the bundled brand mark for a superuser
    // without a custom avatar.
    val useBundledSuperuserBrand = userExists && imageUrl == null && (user.isSuperuser || role == "superuser")
    if (useBundledSuperuserBrand) {
        Box(Modifier.testTag(tag).size(size).clip(CircleShape), propagateMinConstraints = true) {
            _NotificationBrandImage()
        }
        return
    }
    Avatar(
        modifier = Modifier.testTag(tag),
        label = label,
        imageUrl = imageUrl,
        defaultAvatarKey = defaultAvatarKey,
        size = size,
        showFallbackText = userExists,
    )
}

/** Test tag of the bundled brand image used as a superuser's avatar. */
const val notificationBrandAvatarImageTag = "notification-brand-avatar-image"

/**
 * `Image.asset('assets/branding/auth_brand_icon.png', fit: BoxFit.cover)`.
 * PORT: the login page's vector mark, which stays sharp at every density.
 */
@Composable
private fun _NotificationBrandImage() {
    Image(
        painterResource(R.drawable.auth_brand_icon),
        contentDescription = null,
        modifier = Modifier.testTag(notificationBrandAvatarImageTag),
        contentScale = ContentScale.Crop,
    )
}

/**
 * Dart's `_NotificationLayoutMode` inherited widget: whether the enclosing
 * notification content uses its compact (wrapping) layout.
 */
private val _LocalNotificationLayoutMode = staticCompositionLocalOf<Boolean?> { null }

private object _NotificationLayoutMode {
    @Composable
    fun usesCompactLayout(): Boolean {
        val context = rememberBuildContext()
        return _LocalNotificationLayoutMode.current ?: HomeAdaptiveLayout.usesCompactLayout(context)
    }
}

/** Binds a room-scoped user resolver to [roomId] (Dart's `(user) => onResolveRoomUserProfile!(roomId, user)`). */
private fun _roomScopedResolver(resolve: RoomUserProfileResolver?, roomId: String): UserProfileResolver? =
    if (resolve == null) null else { user -> resolve(roomId, user) }

private class _NotificationLayoutMeasurement(val requiredWidth: Float, val availableWidth: Float) {
    val requiresCompactLayout: Boolean get() = requiredWidth > availableWidth
}

/** Dart's `_NotificationLayoutGroup` inherited widget. */
private class _NotificationLayoutGroup(
    val compact: Boolean,
    val onMeasured: (itemKey: String, requiredWidth: Float, availableWidth: Float) -> Unit,
)

private val _LocalNotificationLayoutGroup = staticCompositionLocalOf<_NotificationLayoutGroup?> { null }

/**
 * Keeps every wide-layout notification row on the same layout: once one
 * visible row's wide content no longer fits, all rows switch to the compact
 * one.
 */
@Composable
private fun _SynchronizedNotificationLayout(itemKeys: Set<String>, child: @Composable () -> Unit) =
    StatefulWidget(_SynchronizedNotificationLayoutWidget(itemKeys, child)) { _SynchronizedNotificationLayoutState() }

private class _SynchronizedNotificationLayoutWidget(val itemKeys: Set<String>, val child: @Composable () -> Unit)

private class _SynchronizedNotificationLayoutState : ViewState<_SynchronizedNotificationLayoutWidget>() {
    private val _measurements = LinkedHashMap<String, _NotificationLayoutMeasurement>()
    private var _compact = false

    override fun didUpdateWidget(oldWidget: _SynchronizedNotificationLayoutWidget) {
        _measurements.keys.retainAll(widget.itemKeys)
        _compact = _requiresCompactLayout()
    }

    private fun _requiresCompactLayout(): Boolean =
        _measurements.entries.any { (key, value) -> widget.itemKeys.contains(key) && value.requiresCompactLayout }

    private fun _reportMeasurement(itemKey: String, requiredWidth: Float, availableWidth: Float) {
        if (!mounted || !widget.itemKeys.contains(itemKey) || !requiredWidth.isFinite() || !availableWidth.isFinite()) return
        val previous = _measurements[itemKey]
        if (previous != null && previous.requiredWidth == requiredWidth && previous.availableWidth == availableWidth) return
        _measurements[itemKey] = _NotificationLayoutMeasurement(requiredWidth = requiredWidth, availableWidth = availableWidth)
        val nextCompact = _requiresCompactLayout()
        if (nextCompact == _compact) return
        setState { _compact = nextCompact }
    }

    @Composable
    override fun build() {
        CompositionLocalProvider(
            _LocalNotificationLayoutGroup provides _NotificationLayoutGroup(compact = _compact, onMeasured = ::_reportMeasurement),
            content = widget.child,
        )
    }
}

private enum class _NotificationMeasureSlot { wide, compact }

/**
 * Dart's `_NotificationMeasureLayout` render object: measures the wide
 * child's max intrinsic width, lays out (and shows) the wide child when it
 * fits and the compact one otherwise, and reports the required width after
 * the frame.
 */
@Composable
private fun _NotificationMeasureLayout(
    wide: @Composable () -> Unit,
    compact: @Composable () -> Unit,
    onMeasured: (Float) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val currentOnMeasured by rememberUpdatedState(onMeasured)
    val reported = remember { floatArrayOf(Float.NaN) }
    SubcomposeLayout { constraints ->
        val wideMeasurables =
            subcompose(_NotificationMeasureSlot.wide) {
                CompositionLocalProvider(_LocalNotificationLayoutMode provides false, content = wide)
            }
        val availableHeight = if (constraints.hasBoundedHeight) constraints.maxHeight else Constraints.Infinity
        val requiredPx = wideMeasurables.maxOfOrNull { it.maxIntrinsicWidth(availableHeight) } ?: 0
        val requiredWidth = ceil(requiredPx / density)
        val useCompact = constraints.hasBoundedWidth && requiredWidth > constraints.maxWidth / density
        val measurables =
            if (!useCompact) {
                wideMeasurables
            } else {
                subcompose(_NotificationMeasureSlot.compact) {
                    CompositionLocalProvider(_LocalNotificationLayoutMode provides true, content = compact)
                }
            }
        val placeables = measurables.map { it.measure(constraints) }
        val width = (placeables.maxOfOrNull { it.width } ?: 0).coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = (placeables.maxOfOrNull { it.height } ?: 0).coerceIn(constraints.minHeight, constraints.maxHeight)
        if (reported[0] != requiredWidth) {
            reported[0] = requiredWidth
            // Flutter's post-frame callback.
            scope.launch { currentOnMeasured(requiredWidth) }
        }
        layout(width, height) { for (placeable in placeables) placeable.place(0, 0) }
    }
}

@Composable
private fun _ResponsiveNotificationContent(
    layoutKey: String,
    layoutSignature: Any,
    compact: @Composable () -> Unit,
    wide: @Composable () -> Unit,
) = StatefulWidget(_ResponsiveNotificationContentWidget(layoutKey, layoutSignature, compact, wide)) {
    _ResponsiveNotificationContentState()
}

private class _ResponsiveNotificationContentWidget(
    val layoutKey: String,
    val layoutSignature: Any,
    val compact: @Composable () -> Unit,
    val wide: @Composable () -> Unit,
)

private class _ResponsiveNotificationContentState : ViewState<_ResponsiveNotificationContentWidget>() {
    private var _requiredWideWidth: Float? = null
    private var _lastReportedRequiredWidth: Float? = null
    private var _lastReportedAvailableWidth: Float? = null
    private var _scope: CoroutineScope? = null

    override fun didUpdateWidget(oldWidget: _ResponsiveNotificationContentWidget) {
        if (oldWidget.layoutKey != widget.layoutKey || oldWidget.layoutSignature != widget.layoutSignature) {
            _requiredWideWidth = null
            _lastReportedRequiredWidth = null
            _lastReportedAvailableWidth = null
        }
    }

    @Composable
    override fun build() {
        if (HomeAdaptiveLayout.usesCompactLayout(context)) {
            CompositionLocalProvider(_LocalNotificationLayoutMode provides true, content = widget.compact)
            return
        }
        _scope = rememberCoroutineScope()
        val group = _LocalNotificationLayoutGroup.current
        BoxWithConstraints(Modifier.fillMaxWidth(), propagateMinConstraints = true) {
            val availableWidth = if (constraints.hasBoundedWidth) maxWidth.value else Float.POSITIVE_INFINITY
            val requiredWidth = _requiredWideWidth
            if (requiredWidth == null) {
                _NotificationMeasureLayout(
                    wide = widget.wide,
                    compact = widget.compact,
                    onMeasured = { width -> _handleMeasured(requiredWidth = width, availableWidth = availableWidth, group = group) },
                )
                return@BoxWithConstraints
            }
            _reportMeasurement(group = group, requiredWidth = requiredWidth, availableWidth = availableWidth)
            val useCompactLayout = group?.compact == true || (availableWidth.isFinite() && requiredWidth > availableWidth)
            CompositionLocalProvider(
                _LocalNotificationLayoutMode provides useCompactLayout,
                content = if (useCompactLayout) widget.compact else widget.wide,
            )
        }
    }

    private fun _handleMeasured(requiredWidth: Float, availableWidth: Float, group: _NotificationLayoutGroup?) {
        if (!mounted) return
        if (_requiredWideWidth != requiredWidth) setState { _requiredWideWidth = requiredWidth }
        _reportMeasurement(group = group, requiredWidth = requiredWidth, availableWidth = availableWidth)
    }

    private fun _reportMeasurement(group: _NotificationLayoutGroup?, requiredWidth: Float, availableWidth: Float) {
        if (group == null ||
            !requiredWidth.isFinite() ||
            !availableWidth.isFinite() ||
            (_lastReportedRequiredWidth == requiredWidth && _lastReportedAvailableWidth == availableWidth)
        ) {
            return
        }
        _lastReportedRequiredWidth = requiredWidth
        _lastReportedAvailableWidth = availableWidth
        val layoutKey = widget.layoutKey
        // Flutter's post-frame callback.
        _scope?.launch {
            if (!mounted) return@launch
            group.onMeasured(layoutKey, requiredWidth, availableWidth)
        }
    }
}

@Composable
private fun _CompactNotificationRow(
    time: String,
    timeTag: String,
    body: @Composable () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CompactActivityTimestamp(
            modifier = Modifier.width(CompactActivityLayout.compactTimestampColumnWidth).testTag(timeTag),
            timestamp = time,
            style = UiTypography.label.copyWith(color = UiColors.textMuted, height = 1.28),
        )
        Spacer(Modifier.width(CompactActivityLayout.compactTimestampContentGap))
        Box(Modifier.weight(1f)) { body() }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

/** Dart's `_AndroidNotificationBody`: a `Wrap(spacing: 6, runSpacing: 6)` of the sentence parts. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun _AndroidNotificationBody(tag: String, content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier.testTag(tag),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

@Composable
private fun _NotificationText(text: String, query: String, modifier: Modifier = Modifier) {
    HighlightedText(
        text = text,
        query = query,
        modifier = modifier,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = _labelSecondaryStyle,
    )
}

@Composable
private fun _NotificationNewMarker(show: Boolean, markerTag: String? = null, child: @Composable () -> Unit) {
    // Stack(clipBehavior: Clip.none): sized by the child, the dot on top.
    Box(propagateMinConstraints = true) {
        child()
        if (show) {
            Box(Modifier.matchParentSize()) {
                BadgeDot(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 8.dp, end = 8.dp)
                            .then(if (markerTag != null) Modifier.testTag(markerTag) else Modifier),
                    size = 8.dp,
                )
            }
        }
    }
}

@Composable
private fun _InviteRoleBadge(label: String, query: String, modifier: Modifier = Modifier) {
    RoleBadge(
        modifier = modifier,
        label = label,
        query = query,
        padding = EdgeInsets.symmetric(horizontal = 6.dp, vertical = 3.dp),
        borderRadius = UiRadii.sm,
        fontSize = 11.0,
    )
}

@Composable
private fun _InlineUserTarget(
    user: UserSummary,
    roomId: String,
    targetId: String,
    query: String,
    userExists: Boolean,
    currentUser: CurrentUser,
    onOpenRoom: (PublicRoom) -> Unit,
    onResolveRoomProfile: RoomProfileResolver?,
    onResolveRoomUserProfile: RoomUserProfileResolver?,
    modifier: Modifier = Modifier,
) {
    val config = LocalAppConfig.current
    val name = roomNotificationUserLabel(user, userExists = userExists)
    val avatarLabel = roomNotificationUserAvatarLabel(user, userExists = userExists)
    val compact = _NotificationLayoutMode.usesCompactLayout()
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        _NotificationInteractiveTarget {
            UserHoverCard(
                user = user.copyWith(isDeleted = !userExists),
                currentUser = currentUser,
                onResolveProfile = _roomScopedResolver(onResolveRoomUserProfile, roomId),
                onResolveRoomProfile = onResolveRoomProfile,
                onEnterCommonRoom = onOpenRoom,
            ) {
                _notificationUserAvatar(
                    tag = "notification-room-event-actor-avatar-$targetId",
                    user = user,
                    userExists = userExists,
                    label = avatarLabel,
                    imageUrl = config.resolveAssetUrl(roomNotificationUserAvatarUrl(user, userExists = userExists)),
                    defaultAvatarKey = roomNotificationUserAvatarKey(user, userExists = userExists),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        HighlightedText(
            text = name,
            query = query,
            modifier = Modifier.weight(3f, fill = false).testTag("notification-room-event-actor-name-$targetId"),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = _labelTextStyle,
        )
        Spacer(Modifier.width(7.dp))
        _InviteRoleBadge(
            modifier =
                (if (compact) Modifier else Modifier.weight(2f, fill = false))
                    .testTag("notification-room-event-actor-role-$targetId"),
            label = roomInviteRoleLabel(user),
            query = query,
        )
    }
}

@Composable
private fun _InlineRoomTarget(
    room: PublicRoom,
    inviteId: String,
    query: String,
    roomExists: Boolean,
    currentUser: CurrentUser,
    onOpenRoom: (PublicRoom) -> Unit,
    onResolveRoomProfile: RoomProfileResolver?,
    onResolveRoomUserProfile: RoomUserProfileResolver?,
    modifier: Modifier = Modifier,
) {
    val config = LocalAppConfig.current
    val roomLabel = roomNotificationRoomLabel(room, roomExists = roomExists)
    val roomAvatarLabel = roomNotificationRoomAvatarLabel(room, roomExists = roomExists)
    val avatarSize = _notificationAvatarSize()
    val avatar: @Composable () -> Unit = {
        Avatar(
            modifier = Modifier.testTag("notification-room-avatar-$inviteId"),
            label = roomAvatarLabel,
            imageUrl = config.resolveAssetUrl(roomNotificationRoomAvatarUrl(room, roomExists = roomExists)),
            defaultAvatarKey = roomNotificationRoomAvatarKey(room, roomExists = roomExists),
            size = avatarSize,
            showFallbackText = roomExists,
        )
    }
    val avatarTarget: @Composable () -> Unit = {
        _NotificationInteractiveTarget {
            if (roomNotificationRoomCardEnabled(roomExists = roomExists)) {
                RoomHoverCard(
                    room = room.copyWith(isDeleted = !roomExists),
                    currentUser = currentUser,
                    onResolveRoom = onResolveRoomProfile,
                    onResolveUserProfile = _roomScopedResolver(onResolveRoomUserProfile, room.id),
                    onEnterRoom = if (room.joined) onOpenRoom else null,
                    child = avatar,
                )
            } else {
                avatar()
            }
        }
    }
    val roomName: @Composable (Modifier) -> Unit = { nameModifier ->
        HighlightedText(
            text = roomLabel,
            query = query,
            modifier = nameModifier.testTag("notification-room-name-$inviteId"),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = _labelTextStyle,
        )
    }
    if (!_NotificationLayoutMode.usesCompactLayout()) {
        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            avatarTarget()
            Spacer(Modifier.width(5.dp))
            roomName(Modifier.weight(1f, fill = false))
        }
        return
    }
    BoxWithConstraints(modifier) {
        if (maxWidth < 39.dp) {
            roomName(Modifier.width(maxWidth))
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                avatarTarget()
                Spacer(Modifier.width(5.dp))
                roomName(Modifier.weight(1f, fill = false))
            }
        }
    }
}

@Composable
private fun _InviteDecisionActions(
    invite: RoomInvite,
    busy: Boolean,
    enabled: Boolean,
    onReviewInvite: RoomInviteReviewCallback,
) {
    _NotificationInteractiveTarget {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ButtonIcon(
                tooltip = "拒绝邀请",
                icon = { Icon(Icons.Filled.Close) },
                tone = ButtonTone.danger,
                onPressed = if (enabled) ({ unawaited { onReviewInvite(invite, false) } }) else null,
                size = 34.dp,
            )
            Spacer(Modifier.width(8.dp))
            ButtonIcon(
                tooltip = "接受邀请",
                icon = { Icon(Icons.Filled.Check) },
                tone = ButtonTone.primary,
                onPressed = if (enabled) ({ unawaited { onReviewInvite(invite, true) } }) else null,
                loading = busy,
                size = 34.dp,
            )
        }
    }
}

/**
 * [width] is the trailing column the Dart button filled (a width-less
 * `Button` fills a bounded parent).
 */
@Composable
private fun _ApplicationWithdrawAction(
    application: RoomApplication,
    busy: Boolean,
    enabled: Boolean,
    onWithdrawApplication: RoomApplicationWithdrawCallback,
    width: Dp,
) {
    _NotificationInteractiveTarget {
        Button(
            tooltip = "撤回申请",
            tone = ButtonTone.danger,
            onPressed = if (enabled) ({ unawaited { onWithdrawApplication(application) } }) else null,
            loading = busy,
            width = width,
            height = 34.dp,
            padding = EdgeInsets.symmetric(horizontal = 10.dp),
        ) {
            Text("撤回")
        }
    }
}

@Composable
private fun _NotificationStatusLabel(text: String, query: String, background: Color, border: Color, foreground: Color) {
    Box(
        Modifier
            .decoration(background, RoundedCornerShape(UiRadii.md), border)
            .padding(horizontal = 9.dp, vertical = 6.dp),
    ) {
        HighlightedText(
            text = text,
            query = query,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = UiTypography.label.copyWith(color = foreground, fontWeight = FontWeight.W600),
        )
    }
}

@Composable
private fun _InvalidInviteLabel(invite: RoomInvite, query: String) {
    _NotificationStatusLabel(
        text = roomInviteDecisionLabel(invite),
        query = query,
        background = UiColors.surfacePressed,
        border = UiColors.border,
        foreground = UiColors.textMuted,
    )
}

@Composable
private fun _ProcessedInviteLabel(invite: RoomInvite, query: String) {
    val accepted = isAcceptedRoomInvite(invite)
    _NotificationStatusLabel(
        text = roomInviteDecisionLabel(invite),
        query = query,
        background = if (accepted) UiColors.selected else Color(0xFF2E1F22),
        border = if (accepted) UiColors.accentBorder else UiColors.dangerBorder,
        foreground = if (accepted) UiColors.accent else UiColors.danger,
    )
}

@Composable
private fun _ProcessedApplicationLabel(application: RoomApplication, query: String) {
    val approved = isApprovedRoomApplication(application)
    val rejected = isRejectedRoomApplication(application)
    _NotificationStatusLabel(
        text = roomApplicationStatusLabel(application),
        query = query,
        background =
            when {
                approved -> UiColors.selected
                rejected -> Color(0xFF2E1F22)
                else -> UiColors.surfacePressed
            },
        border =
            when {
                approved -> UiColors.accentBorder
                rejected -> UiColors.dangerBorder
                else -> UiColors.border
            },
        foreground =
            when {
                approved -> UiColors.accent
                rejected -> UiColors.danger
                else -> UiColors.textMuted
            },
    )
}

@Composable
private fun _NotificationEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    danger: Boolean = false,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, color = if (danger) UiColors.danger else UiColors.textMuted, size = 30.dp)
            Spacer(Modifier.height(8.dp))
            Text(
                title,
                textAlign = TextAlign.Center,
                style = UiTypography.body.copyWith(color = if (danger) UiColors.danger else UiColors.textMuted, fontWeight = FontWeight.W600),
            )
            if (subtitle != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    subtitle,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    style = UiTypography.label.copyWith(color = UiColors.textMuted),
                )
            }
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(14.dp))
                // A width-less Dart Button fills the column's bounded width.
                Button(icon = { Icon(Icons.Filled.Refresh) }, onPressed = onAction, width = Dp.Infinity) {
                    Text(actionLabel)
                }
            }
        }
    }
}

private fun _emptyIcon(filter: RoomNotificationFilter): ImageVector =
    when (filter) {
        RoomNotificationFilter.all -> Icons.Filled.NotificationsNone
        RoomNotificationFilter.invites -> Icons.Filled.MailOutline
        RoomNotificationFilter.applications -> Icons.Outlined.AssignmentTurnedIn
        RoomNotificationFilter.roomNotifications -> Icons.Outlined.MeetingRoom
    }

private fun _emptyTitle(filter: RoomNotificationFilter, query: String, hasActiveDateFilter: Boolean): String {
    if (query.trim().isNotEmpty() || hasActiveDateFilter) return "没有匹配通知"
    return when (filter) {
        RoomNotificationFilter.all -> "暂无通知"
        RoomNotificationFilter.invites -> "暂无邀请"
        RoomNotificationFilter.applications -> "暂无申请"
        RoomNotificationFilter.roomNotifications -> "暂无房间"
    }
}
