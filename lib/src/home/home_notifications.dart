import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';

import '../app/room_notifications.dart';
import '../app/error_display.dart';
import '../protocol/models.dart';
import '../ui/ui.dart';
import 'adaptive_layout.dart';
import 'compact_activity_layout.dart';
import 'hover_card_anchor.dart';
import 'room_profile_card.dart';

part 'home_notification_calendar.dart';

typedef RoomInviteReviewCallback =
    Future<void> Function(RoomInvite invite, bool accept);
typedef RoomApplicationWithdrawCallback =
    Future<void> Function(RoomApplication application);
typedef RoomEventOpenCallback =
    void Function(RoomEventNotification notification);
typedef RoomNotificationCopyCallback =
    Future<void> Function(RoomNotificationItem item);
typedef RoomNotificationDeleteCallback =
    Future<void> Function(RoomNotificationItem item);
typedef RoomNotificationsDeleteCallback =
    Future<void> Function(List<RoomNotificationItem> items);

class HomeNotificationsPane extends StatefulWidget {
  const HomeNotificationsPane({
    super.key,
    required this.invites,
    required this.applications,
    required this.roomNotifications,
    required this.loading,
    required this.error,
    required this.busyInviteId,
    required this.busyApplicationId,
    required this.onClose,
    required this.onRefresh,
    required this.onReviewInvite,
    required this.onWithdrawApplication,
    required this.currentUser,
    required this.onOpenRoom,
    required this.onOpenRoomEvent,
    this.onCopyNotification,
    this.onDeleteNotification,
    this.onDeleteNotifications,
    this.onResolveRoomProfile,
    this.onResolveRoomUserProfile,
  });

  final List<RoomInvite> invites;
  final List<RoomApplication> applications;
  final List<RoomEventNotification> roomNotifications;
  final CurrentUser currentUser;
  final bool loading;
  final String? error;
  final String? busyInviteId;
  final String? busyApplicationId;
  final VoidCallback onClose;
  final VoidCallback onRefresh;
  final RoomInviteReviewCallback onReviewInvite;
  final RoomApplicationWithdrawCallback onWithdrawApplication;
  final ValueChanged<PublicRoom> onOpenRoom;
  final RoomEventOpenCallback onOpenRoomEvent;
  final RoomNotificationCopyCallback? onCopyNotification;
  final RoomNotificationDeleteCallback? onDeleteNotification;
  final RoomNotificationsDeleteCallback? onDeleteNotifications;
  final RoomProfileResolver? onResolveRoomProfile;
  final Future<UserSummary> Function(String roomId, UserSummary user)?
  onResolveRoomUserProfile;

  @override
  State<HomeNotificationsPane> createState() => _HomeNotificationsPaneState();
}

class _HomeNotificationsPaneState extends State<HomeNotificationsPane> {
  final TextEditingController _searchController = TextEditingController();
  RoomNotificationFilter _filter = RoomNotificationFilter.all;
  late RoomNotificationDateRange _defaultDateRange;
  late RoomNotificationDateRange _dateRange;
  bool _selectionMode = false;
  Set<String> _selectedNotificationIds = <String>{};
  String _query = '';
  String _lastSearchText = '';

  @override
  void initState() {
    super.initState();
    _defaultDateRange = _defaultDateRangeFor(widget.currentUser);
    _dateRange = _defaultDateRange;
    _searchController.addListener(_handleSearchChanged);
  }

  @override
  void didUpdateWidget(HomeNotificationsPane oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.currentUser.createdAt == widget.currentUser.createdAt) return;
    final wasUsingDefault = _dateRange == _defaultDateRange;
    _defaultDateRange = _defaultDateRangeFor(widget.currentUser);
    if (wasUsingDefault ||
        _dateRange.startDate.isBefore(_defaultDateRange.startDate) ||
        _dateRange.endDate.isAfter(_defaultDateRange.endDate)) {
      _dateRange = _defaultDateRange;
    }
  }

  @override
  void dispose() {
    _searchController.removeListener(_handleSearchChanged);
    _searchController.dispose();
    super.dispose();
  }

  void _handleSearchChanged() {
    final text = _searchController.text;
    if (text == _lastSearchText) return;
    _lastSearchText = text;
    setState(() {
      _query = text;
      if (_selectionMode) _selectedNotificationIds = <String>{};
    });
  }

  RoomNotificationDateRange _defaultDateRangeFor(CurrentUser user) {
    return RoomNotificationDateRange.defaultFor(
      accountCreatedAt: user.createdAt,
      today: DateTime.now(),
    );
  }

  void _refreshDefaultDateRangeForToday() {
    final nextDefault = _defaultDateRangeFor(widget.currentUser);
    if (nextDefault == _defaultDateRange) return;
    final wasUsingDefault = _dateRange == _defaultDateRange;
    _defaultDateRange = nextDefault;
    if (wasUsingDefault) _dateRange = nextDefault;
  }

  Future<void> _showDateRangeFilter() async {
    final result = await showDialog<RoomNotificationDateRange>(
      context: context,
      builder: (context) => _NotificationDateRangeDialog(
        initialRange: _dateRange,
        defaultRange: _defaultDateRange,
      ),
    );
    if (!mounted || result == null || result == _dateRange) return;
    setState(() {
      _dateRange = result;
      if (_selectionMode) _selectedNotificationIds = <String>{};
    });
  }

  void _toggleSelectionMode() {
    setState(() {
      _selectionMode = !_selectionMode;
      if (!_selectionMode) _selectedNotificationIds = <String>{};
    });
  }

  void _toggleNotificationSelection(RoomNotificationItem item) {
    setState(() {
      final ids = {..._selectedNotificationIds};
      if (!ids.add(item.id)) ids.remove(item.id);
      _selectedNotificationIds = ids;
    });
  }

  void _toggleVisibleNotificationSelection(
    List<RoomNotificationItem> visibleItems,
  ) {
    final visibleIds = visibleItems.map((item) => item.id).toSet();
    if (visibleIds.isEmpty) return;
    setState(() {
      final ids = {..._selectedNotificationIds};
      if (visibleIds.every(ids.contains)) {
        ids.removeAll(visibleIds);
      } else {
        ids.addAll(visibleIds);
      }
      _selectedNotificationIds = ids;
    });
  }

  void _clearSelectedNotifications() {
    if (_selectedNotificationIds.isEmpty) return;
    setState(() => _selectedNotificationIds = <String>{});
  }

  @override
  Widget build(BuildContext context) {
    _refreshDefaultDateRangeForToday();
    final visibleItems = roomNotificationsForView(
      invites: widget.invites,
      applications: widget.applications,
      roomEvents: widget.roomNotifications,
      query: _query,
      filter: _filter,
      dateRange: _dateRange,
    );
    final hasActiveDateFilter = _dateRange != _defaultDateRange;
    final rawNotificationCount =
        widget.invites.length +
        widget.applications.length +
        widget.roomNotifications.length;
    return SettingsScaffold(
      icon: Icons.notifications_none,
      title: '通知',
      onBack: widget.onClose,
      headerAction: ButtonIcon(
        key: const ValueKey('home-notifications-refresh-button'),
        tooltip: '刷新通知',
        icon: const Icon(Icons.refresh),
        onPressed: widget.loading ? null : widget.onRefresh,
        loading: widget.loading && rawNotificationCount > 0,
        size: 38,
      ),
      pinned: Column(
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: Input(
                  controller: _searchController,
                  hintText: '搜索通知',
                  prefixIcon: Icons.search,
                  showClearButton: true,
                ),
              ),
              const SizedBox(width: 10),
              ButtonIcon(
                key: const ValueKey('home-notifications-date-filter-button'),
                tooltip: '筛选通知日期',
                icon: const Icon(Icons.calendar_month_outlined),
                selected: hasActiveDateFilter,
                onPressed: _showDateRangeFilter,
                size: Input.defaultHeight,
              ),
              const SizedBox(width: 8),
              ButtonIcon(
                key: const ValueKey('home-notifications-select-button'),
                tooltip: _selectionMode ? '退出批量管理' : '批量管理',
                icon: const Icon(Icons.checklist_outlined),
                selected: _selectionMode,
                onPressed: _toggleSelectionMode,
                size: Input.defaultHeight,
              ),
            ],
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              if (_selectionMode) ...[
                UiCheckbox(
                  key: const ValueKey('home-notifications-select-all'),
                  value:
                      visibleItems.isNotEmpty &&
                      visibleItems.every(
                        (item) => _selectedNotificationIds.contains(item.id),
                      ),
                  onChanged: visibleItems.isEmpty
                      ? null
                      : (_) =>
                            _toggleVisibleNotificationSelection(visibleItems),
                  tooltip:
                      visibleItems.isNotEmpty &&
                          visibleItems.every(
                            (item) =>
                                _selectedNotificationIds.contains(item.id),
                          )
                      ? '取消全选当前通知'
                      : '全选当前通知',
                  semanticLabel: '全选当前通知',
                ),
                const SizedBox(width: 8),
              ],
              Expanded(
                child: SegmentedControl<RoomNotificationFilter>(
                  expanded: true,
                  value: _filter,
                  segments: const [
                    Segment(
                      value: RoomNotificationFilter.all,
                      label: '全部',
                      icon: Icons.inbox_outlined,
                    ),
                    Segment(
                      value: RoomNotificationFilter.invites,
                      label: '邀请',
                      icon: Icons.mail_outline,
                    ),
                    Segment(
                      value: RoomNotificationFilter.applications,
                      label: '申请',
                      icon: Icons.assignment_turned_in_outlined,
                    ),
                    Segment(
                      value: RoomNotificationFilter.roomNotifications,
                      label: '房间',
                      icon: Icons.meeting_room_outlined,
                    ),
                  ],
                  onChanged: (value) => setState(() {
                    _filter = value;
                    if (_selectionMode) {
                      _selectedNotificationIds = <String>{};
                    }
                  }),
                ),
              ),
            ],
          ),
        ],
      ),
      body: _NotificationsBody(
        items: visibleItems,
        loading: widget.loading,
        error: widget.error,
        rawNotificationCount: rawNotificationCount,
        query: _query,
        filter: _filter,
        hasActiveDateFilter: hasActiveDateFilter,
        busyInviteId: widget.busyInviteId,
        busyApplicationId: widget.busyApplicationId,
        onRefresh: widget.onRefresh,
        onReviewInvite: widget.onReviewInvite,
        onWithdrawApplication: widget.onWithdrawApplication,
        currentUser: widget.currentUser,
        onOpenRoom: widget.onOpenRoom,
        onOpenRoomEvent: widget.onOpenRoomEvent,
        onCopyNotification: widget.onCopyNotification,
        onDeleteNotification: widget.onDeleteNotification,
        onDeleteNotifications: widget.onDeleteNotifications,
        selectionMode: _selectionMode,
        selectedNotificationIds: _selectedNotificationIds,
        onToggleNotificationSelection: _toggleNotificationSelection,
        onClearSelectedNotifications: _clearSelectedNotifications,
        onResolveRoomProfile: widget.onResolveRoomProfile,
        onResolveRoomUserProfile: widget.onResolveRoomUserProfile,
      ),
    );
  }
}

class _NotificationDateRangeDialog extends StatefulWidget {
  const _NotificationDateRangeDialog({
    required this.initialRange,
    required this.defaultRange,
    this.title = '筛选通知日期',
    this.description = '选择包含首尾日期的通知时间区间。',
  });

  final RoomNotificationDateRange initialRange;
  final RoomNotificationDateRange defaultRange;
  final String title;
  final String description;

  @override
  State<_NotificationDateRangeDialog> createState() =>
      _NotificationDateRangeDialogState();
}

class _NotificationDateRangeDialogState
    extends State<_NotificationDateRangeDialog> {
  late DateTime _startDate;
  late DateTime _endDate;

  @override
  void initState() {
    super.initState();
    _startDate = widget.initialRange.startDate;
    _endDate = widget.initialRange.endDate;
  }

  Future<void> _pickDate({required bool start}) async {
    final selected = start ? _startDate : _endDate;
    final picked = await showDialog<DateTime>(
      context: context,
      builder: (context) => _NotificationCalendarDialog(
        title: start ? '选择开始日期' : '选择结束日期',
        initialDate: selected,
        firstDate: widget.defaultRange.startDate,
        lastDate: widget.defaultRange.endDate,
      ),
    );
    if (!mounted || picked == null) return;
    setState(() {
      if (start) {
        _startDate = picked;
        if (_endDate.isBefore(picked)) _endDate = picked;
      } else {
        _endDate = picked;
        if (_startDate.isAfter(picked)) _startDate = picked;
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return DialogFrame(
      title: widget.title,
      icon: Icons.calendar_month_outlined,
      maxWidth: 420,
      actionBar: ResponsiveDialogActionBar(
        leadingActionCount: 1,
        actions: [
          ResponsiveDialogAction(
            buttonKey: const ValueKey('notification-date-reset-button'),
            label: '重置',
            icon: Icons.restart_alt,
            onPressed: () => Navigator.of(context).pop(widget.defaultRange),
          ),
          ResponsiveDialogAction(
            label: '取消',
            onPressed: () => Navigator.of(context).pop(),
          ),
          ResponsiveDialogAction(
            buttonKey: const ValueKey('notification-date-confirm-button'),
            label: '应用',
            icon: Icons.check,
            tone: ButtonTone.primary,
            onPressed: () => Navigator.of(context).pop(
              RoomNotificationDateRange(
                startDate: _startDate,
                endDate: _endDate,
              ),
            ),
          ),
        ],
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(
            widget.description,
            style: UiTypography.label.copyWith(color: UiColors.textMuted),
          ),
          const SizedBox(height: 16),
          _NotificationDateField(
            key: const ValueKey('notification-start-date-field'),
            label: '开始日期',
            date: _startDate,
            onPressed: () => _pickDate(start: true),
          ),
          const SizedBox(height: 14),
          _NotificationDateField(
            key: const ValueKey('notification-end-date-field'),
            label: '结束日期',
            date: _endDate,
            onPressed: () => _pickDate(start: false),
          ),
        ],
      ),
    );
  }
}

Future<RoomNotificationDateRange?> showDateRangeFilterDialog(
  BuildContext context, {
  required RoomNotificationDateRange initialRange,
  required RoomNotificationDateRange defaultRange,
  String title = '筛选日期',
  String description = '选择包含首尾日期的时间区间。',
}) {
  return showDialog<RoomNotificationDateRange>(
    context: context,
    builder: (context) => _NotificationDateRangeDialog(
      initialRange: initialRange,
      defaultRange: defaultRange,
      title: title,
      description: description,
    ),
  );
}

class _NotificationDateField extends StatelessWidget {
  const _NotificationDateField({
    super.key,
    required this.label,
    required this.date,
    required this.onPressed,
  });

  final String label;
  final DateTime date;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(label, style: UiTypography.label),
        const SizedBox(height: 8),
        PressableSurface(
          height: 40,
          onPressed: onPressed,
          padding: const EdgeInsets.symmetric(horizontal: 12),
          backgroundColor: UiColors.background,
          child: Row(
            children: [
              const Icon(
                Icons.calendar_today_outlined,
                size: 16,
                color: UiColors.textMuted,
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Text(
                  roomNotificationDateLabel(date),
                  style: UiTypography.body,
                ),
              ),
              const Icon(
                Icons.chevron_right,
                size: 18,
                color: UiColors.textMuted,
              ),
            ],
          ),
        ),
      ],
    );
  }
}

class _NotificationsBody extends StatelessWidget {
  const _NotificationsBody({
    required this.items,
    required this.loading,
    required this.error,
    required this.rawNotificationCount,
    required this.query,
    required this.filter,
    required this.hasActiveDateFilter,
    required this.busyInviteId,
    required this.busyApplicationId,
    required this.onRefresh,
    required this.onReviewInvite,
    required this.onWithdrawApplication,
    required this.currentUser,
    required this.onOpenRoom,
    required this.onOpenRoomEvent,
    this.onCopyNotification,
    this.onDeleteNotification,
    this.onDeleteNotifications,
    required this.selectionMode,
    required this.selectedNotificationIds,
    required this.onToggleNotificationSelection,
    required this.onClearSelectedNotifications,
    required this.onResolveRoomProfile,
    required this.onResolveRoomUserProfile,
  });

  final List<RoomNotificationItem> items;
  final CurrentUser currentUser;
  final bool loading;
  final String? error;
  final int rawNotificationCount;
  final String query;
  final RoomNotificationFilter filter;
  final bool hasActiveDateFilter;
  final String? busyInviteId;
  final String? busyApplicationId;
  final VoidCallback onRefresh;
  final RoomInviteReviewCallback onReviewInvite;
  final RoomApplicationWithdrawCallback onWithdrawApplication;
  final ValueChanged<PublicRoom> onOpenRoom;
  final RoomEventOpenCallback onOpenRoomEvent;
  final RoomNotificationCopyCallback? onCopyNotification;
  final RoomNotificationDeleteCallback? onDeleteNotification;
  final RoomNotificationsDeleteCallback? onDeleteNotifications;
  final bool selectionMode;
  final Set<String> selectedNotificationIds;
  final ValueChanged<RoomNotificationItem> onToggleNotificationSelection;
  final VoidCallback onClearSelectedNotifications;
  final RoomProfileResolver? onResolveRoomProfile;
  final Future<UserSummary> Function(String roomId, UserSummary user)?
  onResolveRoomUserProfile;

  @override
  Widget build(BuildContext context) {
    Widget withErrorNotice(Widget child) {
      final message = error?.trim();
      if (message == null || message.isEmpty) return child;
      return FloatingNoticeEmitter(
        notices: [
          FloatingNotice(
            message: message,
            tone: FloatingNoticeTone.error,
            duration: null,
          ),
        ],
        child: child,
      );
    }

    if (loading && rawNotificationCount == 0) {
      return const Center(
        child: SizedBox.square(
          dimension: 24,
          child: CircularProgressIndicator(
            strokeWidth: 2.2,
            color: UiColors.accent,
          ),
        ),
      );
    }

    if (error != null && rawNotificationCount == 0) {
      return withErrorNotice(
        _NotificationEmptyState(
          icon: Icons.error_outline,
          title: '通知加载失败',
          subtitle: '请稍后重试',
          actionLabel: '重试',
          onAction: onRefresh,
          danger: true,
        ),
      );
    }

    if (items.isEmpty) {
      return withErrorNotice(
        _NotificationEmptyState(
          icon: _emptyIcon(filter),
          title: _emptyTitle(
            filter: filter,
            query: query,
            hasActiveDateFilter: hasActiveDateFilter,
          ),
        ),
      );
    }

    final listHorizontalPadding = HomeAdaptiveLayout.usesCompactLayout(context)
        ? CompactActivityLayout.compactNotificationListHorizontalPadding
        : 22.0;
    return withErrorNotice(
      _SynchronizedNotificationLayout(
        itemKeys: {for (final item in items) item.id},
        child: ListView.separated(
          padding: EdgeInsets.fromLTRB(
            listHorizontalPadding,
            0,
            listHorizontalPadding,
            22,
          ),
          itemCount: items.length,
          separatorBuilder: (_, _) => const SizedBox(height: 10),
          itemBuilder: (context, index) {
            final item = items[index];
            final selectedItems = [
              for (final candidate in items)
                if (selectedNotificationIds.contains(candidate.id)) candidate,
            ];
            final card = _NotificationContextMenuRegion(
              key: ValueKey('notification-context-row-${item.id}'),
              item: item,
              selectionMode: selectionMode,
              selectedItems: selectedItems,
              onToggleSelection: selectionMode
                  ? () => onToggleNotificationSelection(item)
                  : null,
              onCopyNotification: onCopyNotification,
              onDeleteNotification: onDeleteNotification,
              onDeleteNotifications: onDeleteNotifications,
              onSelectionDeleted: onClearSelectedNotifications,
              childBuilder: (contextMenuActive) => switch (item.type) {
                RoomNotificationItemType.invite => _RoomInviteNotificationRow(
                  invite: item.invite!,
                  query: query,
                  busy: busyInviteId == item.invite!.id,
                  busyInviteId: busyInviteId,
                  contextMenuActive: contextMenuActive,
                  onReviewInvite: onReviewInvite,
                  currentUser: currentUser,
                  onOpenRoom: onOpenRoom,
                  onResolveRoomProfile: onResolveRoomProfile,
                  onResolveRoomUserProfile: onResolveRoomUserProfile,
                ),
                RoomNotificationItemType.applicationRequested =>
                  _RoomApplicationRequestNotificationRow(
                    application: item.application!,
                    query: query,
                    busy: busyApplicationId == item.application!.id,
                    busyApplicationId: busyApplicationId,
                    contextMenuActive: contextMenuActive,
                    onWithdrawApplication: onWithdrawApplication,
                    currentUser: currentUser,
                    onOpenRoom: onOpenRoom,
                    onResolveRoomProfile: onResolveRoomProfile,
                    onResolveRoomUserProfile: onResolveRoomUserProfile,
                  ),
                RoomNotificationItemType.applicationReviewed =>
                  _RoomApplicationReviewNotificationRow(
                    application: item.application!,
                    query: query,
                    contextMenuActive: contextMenuActive,
                    currentUser: currentUser,
                    onOpenRoom: onOpenRoom,
                    onResolveRoomProfile: onResolveRoomProfile,
                    onResolveRoomUserProfile: onResolveRoomUserProfile,
                  ),
                RoomNotificationItemType.roomEvent => _RoomEventNotificationRow(
                  notification: item.roomEvent!,
                  query: query,
                  contextMenuActive: contextMenuActive,
                  currentUser: currentUser,
                  onOpenRoom: onOpenRoom,
                  onOpenRoomEvent: onOpenRoomEvent,
                  onResolveRoomProfile: onResolveRoomProfile,
                  onResolveRoomUserProfile: onResolveRoomUserProfile,
                ),
              },
            );
            if (!selectionMode) return card;
            final selected = selectedNotificationIds.contains(item.id);
            return Row(
              crossAxisAlignment: CrossAxisAlignment.center,
              children: [
                UiCheckbox(
                  key: ValueKey('notification-selectbox-${item.id}'),
                  value: selected,
                  onChanged: (_) => onToggleNotificationSelection(item),
                  tooltip: selected ? '取消选择通知' : '选择通知',
                  semanticLabel: '选择通知',
                ),
                const SizedBox(width: 8),
                Expanded(child: card),
              ],
            );
          },
        ),
      ),
    );
  }
}

class _NotificationContextMenuRegion extends StatefulWidget {
  const _NotificationContextMenuRegion({
    super.key,
    required this.item,
    required this.childBuilder,
    required this.selectionMode,
    required this.selectedItems,
    this.onToggleSelection,
    this.onCopyNotification,
    this.onDeleteNotification,
    this.onDeleteNotifications,
    this.onSelectionDeleted,
  });

  final RoomNotificationItem item;
  final Widget Function(bool contextMenuActive) childBuilder;
  final bool selectionMode;
  final List<RoomNotificationItem> selectedItems;
  final VoidCallback? onToggleSelection;
  final RoomNotificationCopyCallback? onCopyNotification;
  final RoomNotificationDeleteCallback? onDeleteNotification;
  final RoomNotificationsDeleteCallback? onDeleteNotifications;
  final VoidCallback? onSelectionDeleted;

  @override
  State<_NotificationContextMenuRegion> createState() =>
      _NotificationContextMenuRegionState();
}

class _NotificationContextMenuRegionState
    extends State<_NotificationContextMenuRegion> {
  bool _contextMenuActive = false;

  @override
  Widget build(BuildContext context) {
    final itemSelected = widget.selectedItems.any(
      (selected) => selected.id == widget.item.id,
    );
    return UiContextMenuTriggerRegion(
      onTap: widget.selectionMode ? widget.onToggleSelection : null,
      onTriggered: _showContextMenu,
      child: widget.childBuilder(_contextMenuActive || itemSelected),
    );
  }

  void _showContextMenu(Offset position) {
    final contextItems = widget.selectionMode
        ? widget.selectedItems
        : [widget.item];
    final canDelete =
        widget.onDeleteNotifications != null ||
        widget.onDeleteNotification != null;
    if (contextItems.isEmpty ||
        (widget.onCopyNotification == null && !canDelete)) {
      return;
    }
    setState(() => _contextMenuActive = true);
    unawaited(
      showUiContextMenu(
        context,
        position: position,
        sections: [
          if (contextItems.length == 1)
            UiContextMenuSection([
              UiContextMenuItem(
                label: '复制',
                shortcut: 'Ctrl+C',
                onPressed: widget.onCopyNotification == null
                    ? null
                    : () => unawaited(_copyNotification(contextItems.single)),
              ),
            ]),
          if (canDelete)
            UiContextMenuSection([
              UiContextMenuItem(
                label: '删除',
                onPressed: () =>
                    unawaited(_confirmDeleteNotifications(contextItems)),
              ),
            ]),
        ],
      ).whenComplete(() {
        if (mounted) setState(() => _contextMenuActive = false);
      }),
    );
  }

  Future<void> _copyNotification(RoomNotificationItem item) async {
    try {
      await widget.onCopyNotification!(item);
      if (!mounted) return;
      showFloatingSuccessNotice(context, '已复制');
    } catch (error) {
      if (!mounted) return;
      showFloatingErrorNotice(
        context,
        userFacingErrorMessage(error, fallback: '复制通知失败'),
      );
    }
  }

  Future<void> _confirmDeleteNotifications(
    List<RoomNotificationItem> items,
  ) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (_) => _NotificationDeleteConfirmDialog(itemCount: items.length),
    );
    if (confirmed != true || !mounted) return;
    try {
      final deleteNotifications = widget.onDeleteNotifications;
      if (deleteNotifications != null) {
        await deleteNotifications(items);
      } else {
        for (final item in items) {
          await widget.onDeleteNotification!(item);
        }
      }
      if (!mounted) return;
      widget.onSelectionDeleted?.call();
      showFloatingSuccessNotice(
        context,
        items.length == 1 ? '已删除' : '已删除 ${items.length} 条通知',
      );
    } catch (error) {
      if (!mounted) return;
      showFloatingErrorNotice(
        context,
        userFacingErrorMessage(error, fallback: '删除通知失败'),
      );
    }
  }
}

/// Keeps interactive notification controls from also toggling batch selection.
///
/// Some shared controls use [Listener] rather than a gesture recognizer.  This
/// wrapper claims the primary tap before the row-level selection detector while
/// allowing the control itself to continue receiving pointer events.
class _NotificationInteractiveTarget extends StatelessWidget {
  const _NotificationInteractiveTarget({required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      behavior: HitTestBehavior.translucent,
      excludeFromSemantics: true,
      onTap: () {},
      child: child,
    );
  }
}

class _NotificationDeleteConfirmDialog extends StatelessWidget {
  const _NotificationDeleteConfirmDialog({required this.itemCount});

  final int itemCount;

  @override
  Widget build(BuildContext context) {
    return DialogFrame(
      title: itemCount == 1 ? '删除通知' : '删除选中的通知',
      icon: Icons.warning_amber_outlined,
      actions: [
        Button(
          onPressed: () => Navigator.of(context).pop(false),
          child: const Text('取消'),
        ),
        Button(
          tone: ButtonTone.danger,
          onPressed: () => Navigator.of(context).pop(true),
          child: const Text('删除'),
        ),
      ],
      child: Text(
        itemCount == 1 ? '删除后将无法恢复' : '确定删除所有选中的$itemCount条通知？',
        style: UiTypography.body,
      ),
    );
  }
}

class _RoomInviteNotificationRow extends StatelessWidget {
  const _RoomInviteNotificationRow({
    required this.invite,
    required this.query,
    required this.contextMenuActive,
    required this.busy,
    required this.busyInviteId,
    required this.onReviewInvite,
    required this.currentUser,
    required this.onOpenRoom,
    required this.onResolveRoomProfile,
    required this.onResolveRoomUserProfile,
  });

  final RoomInvite invite;
  final String query;
  final bool contextMenuActive;
  final bool busy;
  final String? busyInviteId;
  final RoomInviteReviewCallback onReviewInvite;
  final CurrentUser currentUser;
  final ValueChanged<PublicRoom> onOpenRoom;
  final RoomProfileResolver? onResolveRoomProfile;
  final Future<UserSummary> Function(String roomId, UserSummary user)?
  onResolveRoomUserProfile;

  @override
  Widget build(BuildContext context) {
    final room = invite.room;
    final inviter = invite.inviter;
    final inviterName = roomNotificationUserLabel(
      inviter,
      userExists: invite.inviterExists,
    );
    final inviterAvatarLabel = roomNotificationUserAvatarLabel(
      inviter,
      userExists: invite.inviterExists,
    );
    final role = roomInviteRoleLabel(inviter);
    final time = roomInviteTimestampLabel(invite.createdAt);
    final invalid = isInvalidPendingRoomInvite(invite);
    final inviterAvatar = _notificationUserAvatar(
      key: ValueKey('notification-inviter-avatar-${invite.id}'),
      user: inviter,
      userExists: invite.inviterExists,
      label: inviterAvatarLabel,
      imageUrl: AppConfigScope.of(context).resolveAssetUrl(
        roomNotificationUserAvatarUrl(
          inviter,
          userExists: invite.inviterExists,
        ),
      ),
      defaultAvatarKey: roomNotificationUserAvatarKey(
        inviter,
        userExists: invite.inviterExists,
      ),
    );
    final inviterAvatarTarget = _NotificationInteractiveTarget(
      child: UserHoverCard(
        user: inviter.copyWith(isDeleted: !invite.inviterExists),
        currentUser: currentUser,
        onResolveProfile: onResolveRoomUserProfile == null
            ? null
            : (user) => onResolveRoomUserProfile!(room.id, user),
        onResolveRoomProfile: onResolveRoomProfile,
        onEnterCommonRoom: onOpenRoom,
        child: inviterAvatar,
      ),
    );
    final inviteAction = invalid
        ? _InvalidInviteLabel(invite: invite, query: query)
        : isPendingRoomInvite(invite)
        ? _InviteDecisionActions(
            invite: invite,
            busy: busy,
            enabled: canReviewNotificationInvite(
              invite: invite,
              busyInviteId: busyInviteId,
            ),
            onReviewInvite: onReviewInvite,
          )
        : _ProcessedInviteLabel(invite: invite, query: query);
    return _NotificationNewMarker(
      show: isActionablePendingRoomInvite(invite),
      child: AnimatedContainer(
        key: ValueKey('notification-row-surface-invite:${invite.id}'),
        duration: const Duration(milliseconds: 90),
        curve: Curves.easeOutCubic,
        decoration: BoxDecoration(
          color: contextMenuActive ? UiColors.selected : UiColors.surface,
          borderRadius: BorderRadius.circular(UiRadii.lg),
          border: Border.all(
            color: contextMenuActive
                ? UiColors.selectedBorder
                : isPendingRoomInvite(invite) && !invalid
                ? UiColors.accentBorder
                : UiColors.border,
          ),
        ),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 11),
          child: _ResponsiveNotificationContent(
            layoutKey: 'invite:${invite.id}',
            layoutSignature: invite,
            compact: _CompactNotificationRow(
              time: time,
              timeKey: ValueKey('notification-time-${invite.id}'),
              body: _AndroidNotificationBody(
                key: ValueKey('notification-mobile-body-${invite.id}'),
                children: [
                  inviterAvatarTarget,
                  HighlightedText(
                    text: inviterName,
                    query: query,
                    key: ValueKey('notification-inviter-name-${invite.id}'),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: UiTypography.label.copyWith(
                      color: UiColors.text,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  _InviteRoleBadge(
                    key: ValueKey('notification-inviter-role-${invite.id}'),
                    label: role,
                    query: query,
                  ),
                  Text(
                    '邀请您加入',
                    key: ValueKey('notification-invite-action-${invite.id}'),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: UiTypography.label.copyWith(
                      color: UiColors.textSecondary,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  _InlineRoomTarget(
                    room: room,
                    inviteId: invite.id,
                    query: query,
                    roomExists: invite.roomExists,
                    currentUser: currentUser,
                    onResolveRoomProfile: onResolveRoomProfile,
                    onResolveRoomUserProfile: onResolveRoomUserProfile,
                    onOpenRoom: onOpenRoom,
                  ),
                ],
              ),
              trailing: SizedBox(
                width: 78,
                child: Align(
                  alignment: Alignment.centerRight,
                  child: inviteAction,
                ),
              ),
            ),
            wide: Row(
              children: [
                SizedBox(
                  width: 116,
                  child: HighlightedText(
                    text: time,
                    query: '',
                    key: ValueKey('notification-time-${invite.id}'),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: UiTypography.label.copyWith(
                      color: UiColors.textMuted,
                    ),
                  ),
                ),
                const SizedBox(width: 10),
                inviterAvatarTarget,
                const SizedBox(width: 8),
                Expanded(
                  child: Row(
                    children: [
                      Flexible(
                        flex: 3,
                        child: HighlightedText(
                          text: inviterName,
                          query: query,
                          key: ValueKey(
                            'notification-inviter-name-${invite.id}',
                          ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: UiTypography.label.copyWith(
                            color: UiColors.text,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ),
                      const SizedBox(width: 7),
                      Flexible(
                        flex: 2,
                        child: _InviteRoleBadge(
                          key: ValueKey(
                            'notification-inviter-role-${invite.id}',
                          ),
                          label: role,
                          query: query,
                        ),
                      ),
                      const SizedBox(width: 8),
                      Flexible(
                        flex: 2,
                        child: Text(
                          '邀请您加入',
                          key: ValueKey(
                            'notification-invite-action-${invite.id}',
                          ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: UiTypography.label.copyWith(
                            color: UiColors.textSecondary,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ),
                      const SizedBox(width: 8),
                      Flexible(
                        flex: 4,
                        child: _InlineRoomTarget(
                          room: room,
                          inviteId: invite.id,
                          query: query,
                          roomExists: invite.roomExists,
                          currentUser: currentUser,
                          onResolveRoomProfile: onResolveRoomProfile,
                          onResolveRoomUserProfile: onResolveRoomUserProfile,
                          onOpenRoom: onOpenRoom,
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 12),
                SizedBox(
                  width: 88,
                  child: Align(
                    alignment: Alignment.centerRight,
                    child: inviteAction,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _RoomApplicationRequestNotificationRow extends StatelessWidget {
  const _RoomApplicationRequestNotificationRow({
    required this.application,
    required this.query,
    required this.contextMenuActive,
    required this.busy,
    required this.busyApplicationId,
    required this.onWithdrawApplication,
    required this.currentUser,
    required this.onOpenRoom,
    required this.onResolveRoomProfile,
    required this.onResolveRoomUserProfile,
  });

  final RoomApplication application;
  final String query;
  final bool contextMenuActive;
  final bool busy;
  final String? busyApplicationId;
  final RoomApplicationWithdrawCallback onWithdrawApplication;
  final CurrentUser currentUser;
  final ValueChanged<PublicRoom> onOpenRoom;
  final RoomProfileResolver? onResolveRoomProfile;
  final Future<UserSummary> Function(String roomId, UserSummary user)?
  onResolveRoomUserProfile;

  @override
  Widget build(BuildContext context) {
    final room = application.room;
    final time = roomInviteTimestampLabel(application.createdAt);
    final applicationAction = isPendingRoomApplication(application)
        ? _ApplicationWithdrawAction(
            application: application,
            busy: busy,
            enabled: canWithdrawNotificationApplication(
              application: application,
              busyApplicationId: busyApplicationId,
            ),
            onWithdrawApplication: onWithdrawApplication,
          )
        : _ProcessedApplicationLabel(application: application, query: query);
    return _NotificationNewMarker(
      show: isPendingRoomApplication(application),
      child: AnimatedContainer(
        key: ValueKey(
          'notification-row-surface-application-requested:${application.id}',
        ),
        duration: const Duration(milliseconds: 90),
        curve: Curves.easeOutCubic,
        decoration: BoxDecoration(
          color: contextMenuActive ? UiColors.selected : UiColors.surface,
          borderRadius: BorderRadius.circular(UiRadii.lg),
          border: Border.all(
            color: contextMenuActive
                ? UiColors.selectedBorder
                : isPendingRoomApplication(application)
                ? UiColors.accentBorder
                : UiColors.border,
          ),
        ),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 11),
          child: _ResponsiveNotificationContent(
            layoutKey: 'application-requested:${application.id}',
            layoutSignature: application,
            compact: _CompactNotificationRow(
              time: time,
              timeKey: ValueKey(
                'notification-application-time-${application.id}',
              ),
              body: _AndroidNotificationBody(
                key: ValueKey(
                  'notification-mobile-body-application-${application.id}',
                ),
                children: [
                  Text(
                    '您已申请加入',
                    key: ValueKey(
                      'notification-application-request-action-${application.id}',
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: UiTypography.label.copyWith(
                      color: UiColors.textSecondary,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  _InlineRoomTarget(
                    room: room,
                    inviteId: 'application-${application.id}',
                    query: query,
                    roomExists: true,
                    currentUser: currentUser,
                    onResolveRoomProfile: onResolveRoomProfile,
                    onResolveRoomUserProfile: onResolveRoomUserProfile,
                    onOpenRoom: onOpenRoom,
                  ),
                ],
              ),
              trailing: SizedBox(
                width: 78,
                child: Align(
                  alignment: Alignment.centerRight,
                  child: applicationAction,
                ),
              ),
            ),
            wide: Row(
              children: [
                SizedBox(
                  width: 116,
                  child: HighlightedText(
                    text: time,
                    query: '',
                    key: ValueKey(
                      'notification-application-time-${application.id}',
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: UiTypography.label.copyWith(
                      color: UiColors.textMuted,
                    ),
                  ),
                ),
                const SizedBox(width: 10),
                Text(
                  '您已申请加入',
                  key: ValueKey(
                    'notification-application-request-action-${application.id}',
                  ),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: UiTypography.label.copyWith(
                    color: UiColors.textSecondary,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: _InlineRoomTarget(
                    room: room,
                    inviteId: 'application-${application.id}',
                    query: query,
                    roomExists: true,
                    currentUser: currentUser,
                    onResolveRoomProfile: onResolveRoomProfile,
                    onResolveRoomUserProfile: onResolveRoomUserProfile,
                    onOpenRoom: onOpenRoom,
                  ),
                ),
                const SizedBox(width: 12),
                SizedBox(
                  width: 88,
                  child: Align(
                    alignment: Alignment.centerRight,
                    child: applicationAction,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _RoomApplicationReviewNotificationRow extends StatelessWidget {
  const _RoomApplicationReviewNotificationRow({
    required this.application,
    required this.query,
    required this.contextMenuActive,
    required this.currentUser,
    required this.onOpenRoom,
    required this.onResolveRoomProfile,
    required this.onResolveRoomUserProfile,
  });

  final RoomApplication application;
  final String query;
  final bool contextMenuActive;
  final CurrentUser currentUser;
  final ValueChanged<PublicRoom> onOpenRoom;
  final RoomProfileResolver? onResolveRoomProfile;
  final Future<UserSummary> Function(String roomId, UserSummary user)?
  onResolveRoomUserProfile;

  @override
  Widget build(BuildContext context) {
    final room = application.room;
    final reviewer = application.reviewer!;
    final reviewerName = roomNotificationUserLabel(
      reviewer,
      userExists: application.reviewerExists,
    );
    final reviewerAvatarLabel = roomNotificationUserAvatarLabel(
      reviewer,
      userExists: application.reviewerExists,
    );
    final role = roomInviteRoleLabel(reviewer);
    final time = roomInviteTimestampLabel(
      application.reviewedAt ?? application.updatedAt,
    );
    final reviewerAvatar = _notificationUserAvatar(
      key: ValueKey(
        'notification-application-reviewer-avatar-${application.id}',
      ),
      user: reviewer,
      userExists: application.reviewerExists,
      label: reviewerAvatarLabel,
      imageUrl: AppConfigScope.of(context).resolveAssetUrl(
        roomNotificationUserAvatarUrl(
          reviewer,
          userExists: application.reviewerExists,
        ),
      ),
      defaultAvatarKey: roomNotificationUserAvatarKey(
        reviewer,
        userExists: application.reviewerExists,
      ),
    );
    final reviewerAvatarTarget = _NotificationInteractiveTarget(
      child: UserHoverCard(
        user: reviewer.copyWith(isDeleted: !application.reviewerExists),
        currentUser: currentUser,
        onResolveProfile: onResolveRoomUserProfile == null
            ? null
            : (user) => onResolveRoomUserProfile!(room.id, user),
        onResolveRoomProfile: onResolveRoomProfile,
        onEnterCommonRoom: onOpenRoom,
        child: reviewerAvatar,
      ),
    );
    return AnimatedContainer(
      key: ValueKey(
        'notification-row-surface-application-reviewed:${application.id}',
      ),
      duration: const Duration(milliseconds: 90),
      curve: Curves.easeOutCubic,
      decoration: BoxDecoration(
        color: contextMenuActive ? UiColors.selected : UiColors.surface,
        borderRadius: BorderRadius.circular(UiRadii.lg),
        border: Border.all(
          color: contextMenuActive ? UiColors.selectedBorder : UiColors.border,
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 11),
        child: _ResponsiveNotificationContent(
          layoutKey: 'application-reviewed:${application.id}',
          layoutSignature: application,
          compact: _CompactNotificationRow(
            time: time,
            timeKey: ValueKey(
              'notification-application-review-time-${application.id}',
            ),
            body: _AndroidNotificationBody(
              key: ValueKey(
                'notification-mobile-body-reviewed-${application.id}',
              ),
              children: [
                reviewerAvatarTarget,
                HighlightedText(
                  text: reviewerName,
                  query: query,
                  key: ValueKey(
                    'notification-application-reviewer-name-${application.id}',
                  ),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: UiTypography.label.copyWith(
                    color: UiColors.text,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                _InviteRoleBadge(
                  key: ValueKey(
                    'notification-application-reviewer-role-${application.id}',
                  ),
                  label: role,
                  query: query,
                ),
                HighlightedText(
                  text: roomApplicationReviewActionLabel(application),
                  query: query,
                  key: ValueKey(
                    'notification-application-review-action-${application.id}',
                  ),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: UiTypography.label.copyWith(
                    color: UiColors.textSecondary,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                _InlineRoomTarget(
                  room: room,
                  inviteId: 'application-reviewed-${application.id}',
                  query: query,
                  roomExists: true,
                  currentUser: currentUser,
                  onResolveRoomProfile: onResolveRoomProfile,
                  onResolveRoomUserProfile: onResolveRoomUserProfile,
                  onOpenRoom: onOpenRoom,
                ),
              ],
            ),
          ),
          wide: Row(
            children: [
              SizedBox(
                width: 116,
                child: HighlightedText(
                  text: time,
                  query: '',
                  key: ValueKey(
                    'notification-application-review-time-${application.id}',
                  ),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: UiTypography.label.copyWith(color: UiColors.textMuted),
                ),
              ),
              const SizedBox(width: 10),
              reviewerAvatarTarget,
              const SizedBox(width: 8),
              Expanded(
                child: Row(
                  children: [
                    Flexible(
                      flex: 3,
                      child: HighlightedText(
                        text: reviewerName,
                        query: query,
                        key: ValueKey(
                          'notification-application-reviewer-name-${application.id}',
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: UiTypography.label.copyWith(
                          color: UiColors.text,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ),
                    const SizedBox(width: 7),
                    Flexible(
                      flex: 2,
                      child: _InviteRoleBadge(
                        key: ValueKey(
                          'notification-application-reviewer-role-${application.id}',
                        ),
                        label: role,
                        query: query,
                      ),
                    ),
                    const SizedBox(width: 8),
                    Flexible(
                      flex: 2,
                      child: HighlightedText(
                        text: roomApplicationReviewActionLabel(application),
                        query: query,
                        key: ValueKey(
                          'notification-application-review-action-${application.id}',
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: UiTypography.label.copyWith(
                          color: UiColors.textSecondary,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Flexible(
                      flex: 4,
                      child: _InlineRoomTarget(
                        room: room,
                        inviteId: 'application-reviewed-${application.id}',
                        query: query,
                        roomExists: true,
                        currentUser: currentUser,
                        onResolveRoomProfile: onResolveRoomProfile,
                        onResolveRoomUserProfile: onResolveRoomUserProfile,
                        onOpenRoom: onOpenRoom,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 12),
              const SizedBox(width: 88),
            ],
          ),
        ),
      ),
    );
  }
}

class _RoomEventNotificationRow extends StatelessWidget {
  const _RoomEventNotificationRow({
    required this.notification,
    required this.query,
    required this.contextMenuActive,
    required this.currentUser,
    required this.onOpenRoom,
    required this.onOpenRoomEvent,
    required this.onResolveRoomProfile,
    required this.onResolveRoomUserProfile,
  });

  final RoomEventNotification notification;
  final String query;
  final bool contextMenuActive;
  final CurrentUser currentUser;
  final ValueChanged<PublicRoom> onOpenRoom;
  final RoomEventOpenCallback onOpenRoomEvent;
  final RoomProfileResolver? onResolveRoomProfile;
  final Future<UserSummary> Function(String roomId, UserSummary user)?
  onResolveRoomUserProfile;

  @override
  Widget build(BuildContext context) {
    final time = roomInviteTimestampLabel(notification.createdAt);
    return _NotificationNewMarker(
      show: notification.isUnread,
      markerKey: ValueKey('notification-room-event-new-${notification.id}'),
      child: AnimatedContainer(
        key: ValueKey('notification-row-surface-room-event:${notification.id}'),
        duration: const Duration(milliseconds: 90),
        curve: Curves.easeOutCubic,
        decoration: BoxDecoration(
          color: contextMenuActive ? UiColors.selected : UiColors.surface,
          borderRadius: BorderRadius.circular(UiRadii.lg),
          border: Border.all(
            color: contextMenuActive
                ? UiColors.selectedBorder
                : UiColors.border,
          ),
        ),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 11),
          child: _ResponsiveNotificationContent(
            layoutKey: 'room-event:${notification.id}',
            layoutSignature: notification,
            compact: _CompactNotificationRow(
              time: time,
              timeKey: ValueKey(
                'notification-room-event-time-${notification.id}',
              ),
              body: _buildAndroidContent(),
              trailing: _RoomEventJumpAction(
                notification: notification,
                onOpenRoomEvent: onOpenRoomEvent,
              ),
            ),
            wide: Row(
              children: [
                SizedBox(
                  width: 116,
                  child: HighlightedText(
                    text: time,
                    query: '',
                    key: ValueKey(
                      'notification-room-event-time-${notification.id}',
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: UiTypography.label.copyWith(
                      color: UiColors.textMuted,
                    ),
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(child: _buildContent()),
                const SizedBox(width: 12),
                SizedBox(
                  width: 88,
                  child: Align(
                    alignment: Alignment.centerRight,
                    child: _RoomEventJumpAction(
                      notification: notification,
                      onOpenRoomEvent: onOpenRoomEvent,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildContent() {
    final actor = notification.actor;
    final roleLabel = roomNotificationRoleLabel(notification.toRole);
    switch (notification.type) {
      case kRoomEventNotificationMemberRemoved:
        return Row(
          children: [
            _NotificationText(text: '您被', query: query),
            if (actor != null) ...[
              const SizedBox(width: 8),
              Flexible(
                flex: 4,
                child: _InlineUserTarget(
                  user: actor,
                  roomId: notification.room.id,
                  targetId: notification.id,
                  query: query,
                  userExists: notification.actorExists,
                  currentUser: currentUser,
                  onResolveRoomProfile: onResolveRoomProfile,
                  onResolveRoomUserProfile: onResolveRoomUserProfile,
                  onOpenRoom: onOpenRoom,
                ),
              ),
              const SizedBox(width: 8),
            ],
            _NotificationText(text: '踢出了', query: query),
            const SizedBox(width: 8),
            Flexible(flex: 4, child: _roomTarget()),
          ],
        );
      case kRoomEventNotificationRolePromoted:
      case kRoomEventNotificationRoleDemoted:
        return Row(
          children: [
            _NotificationText(text: '您在', query: query),
            const SizedBox(width: 8),
            Flexible(flex: 4, child: _roomTarget()),
            const SizedBox(width: 8),
            if (actor != null) ...[
              _NotificationText(text: '中被', query: query),
              const SizedBox(width: 8),
              Flexible(
                flex: 4,
                child: _InlineUserTarget(
                  user: actor,
                  roomId: notification.room.id,
                  targetId: notification.id,
                  query: query,
                  userExists: notification.actorExists,
                  currentUser: currentUser,
                  onResolveRoomProfile: onResolveRoomProfile,
                  onResolveRoomUserProfile: onResolveRoomUserProfile,
                  onOpenRoom: onOpenRoom,
                ),
              ),
              const SizedBox(width: 8),
            ] else ...[
              _NotificationText(text: '中', query: query),
              const SizedBox(width: 8),
            ],
            _NotificationText(
              text: roomEventNotificationRoleActionLabel(notification),
              query: query,
            ),
            const SizedBox(width: 7),
            Flexible(
              child: _InviteRoleBadge(
                key: ValueKey(
                  'notification-room-event-target-role-${notification.id}',
                ),
                label: roleLabel,
                query: query,
              ),
            ),
          ],
        );
      case kRoomEventNotificationCreatorTransferDemoted:
        return Row(
          children: [
            _NotificationText(text: '您在', query: query),
            const SizedBox(width: 8),
            Flexible(flex: 4, child: _roomTarget()),
            const SizedBox(width: 8),
            _NotificationText(text: '中降职为', query: query),
            const SizedBox(width: 7),
            Flexible(
              child: _InviteRoleBadge(
                key: ValueKey(
                  'notification-room-event-target-role-${notification.id}',
                ),
                label: roleLabel,
                query: query,
              ),
            ),
          ],
        );
      case kRoomEventNotificationMentioned:
        return Row(
          children: [
            _NotificationText(text: '您在', query: query),
            const SizedBox(width: 8),
            Flexible(flex: 4, child: _roomTarget()),
            const SizedBox(width: 8),
            if (actor != null) ...[
              _NotificationText(text: '中被', query: query),
              const SizedBox(width: 8),
              Flexible(
                flex: 4,
                child: _InlineUserTarget(
                  user: actor,
                  roomId: notification.room.id,
                  targetId: notification.id,
                  query: query,
                  userExists: notification.actorExists,
                  currentUser: currentUser,
                  onResolveRoomProfile: onResolveRoomProfile,
                  onResolveRoomUserProfile: onResolveRoomUserProfile,
                  onOpenRoom: onOpenRoom,
                ),
              ),
              const SizedBox(width: 8),
              _NotificationText(text: '提及', query: query),
              ..._messagePreviewInfoButton(),
            ] else ...[
              _NotificationText(text: '中被提及', query: query),
              ..._messagePreviewInfoButton(),
            ],
          ],
        );
      default:
        return Row(
          children: [
            _NotificationText(text: '您在', query: query),
            const SizedBox(width: 8),
            Flexible(flex: 4, child: _roomTarget()),
            const SizedBox(width: 8),
            _NotificationText(text: '收到了房间通知', query: query),
          ],
        );
    }
  }

  Widget _buildAndroidContent() {
    final actor = notification.actor;
    final roleLabel = roomNotificationRoleLabel(notification.toRole);
    final bodyKey = ValueKey(
      'notification-mobile-body-room-event-${notification.id}',
    );
    switch (notification.type) {
      case kRoomEventNotificationMemberRemoved:
        return _AndroidNotificationBody(
          key: bodyKey,
          children: [
            _NotificationText(text: '您被', query: query),
            if (actor != null)
              _InlineUserTarget(
                user: actor,
                roomId: notification.room.id,
                targetId: notification.id,
                query: query,
                userExists: notification.actorExists,
                currentUser: currentUser,
                onResolveRoomProfile: onResolveRoomProfile,
                onResolveRoomUserProfile: onResolveRoomUserProfile,
                onOpenRoom: onOpenRoom,
              ),
            _NotificationText(text: '踢出了', query: query),
            _roomTarget(),
          ],
        );
      case kRoomEventNotificationRolePromoted:
      case kRoomEventNotificationRoleDemoted:
        return _AndroidNotificationBody(
          key: bodyKey,
          children: [
            _NotificationText(text: '您在', query: query),
            _roomTarget(),
            _NotificationText(text: actor == null ? '中' : '中被', query: query),
            if (actor != null)
              _InlineUserTarget(
                user: actor,
                roomId: notification.room.id,
                targetId: notification.id,
                query: query,
                userExists: notification.actorExists,
                currentUser: currentUser,
                onResolveRoomProfile: onResolveRoomProfile,
                onResolveRoomUserProfile: onResolveRoomUserProfile,
                onOpenRoom: onOpenRoom,
              ),
            _NotificationText(
              text: roomEventNotificationRoleActionLabel(notification),
              query: query,
            ),
            _InviteRoleBadge(
              key: ValueKey(
                'notification-room-event-target-role-${notification.id}',
              ),
              label: roleLabel,
              query: query,
            ),
          ],
        );
      case kRoomEventNotificationCreatorTransferDemoted:
        return _AndroidNotificationBody(
          key: bodyKey,
          children: [
            _NotificationText(text: '您在', query: query),
            _roomTarget(),
            _NotificationText(text: '中降职为', query: query),
            _InviteRoleBadge(
              key: ValueKey(
                'notification-room-event-target-role-${notification.id}',
              ),
              label: roleLabel,
              query: query,
            ),
          ],
        );
      case kRoomEventNotificationMentioned:
        final preview = notification.messagePreview?.trim();
        return _AndroidNotificationBody(
          key: bodyKey,
          children: [
            _NotificationText(text: '您在', query: query),
            _roomTarget(),
            _NotificationText(
              text: actor == null ? '中被提及' : '中被',
              query: query,
            ),
            if (actor != null) ...[
              _InlineUserTarget(
                user: actor,
                roomId: notification.room.id,
                targetId: notification.id,
                query: query,
                userExists: notification.actorExists,
                currentUser: currentUser,
                onResolveRoomProfile: onResolveRoomProfile,
                onResolveRoomUserProfile: onResolveRoomUserProfile,
                onOpenRoom: onOpenRoom,
              ),
              _NotificationText(text: '提及', query: query),
            ],
            if (preview != null && preview.isNotEmpty)
              _RoomEventInfoButton(message: preview),
          ],
        );
      default:
        return _AndroidNotificationBody(
          key: bodyKey,
          children: [
            _NotificationText(text: '您在', query: query),
            _roomTarget(),
            _NotificationText(text: '收到了房间通知', query: query),
          ],
        );
    }
  }

  Widget _roomTarget() {
    return _InlineRoomTarget(
      room: notification.room,
      inviteId: 'room-event-${notification.id}',
      query: query,
      roomExists: notification.roomExists,
      currentUser: currentUser,
      onResolveRoomProfile: onResolveRoomProfile,
      onResolveRoomUserProfile: onResolveRoomUserProfile,
      onOpenRoom: onOpenRoom,
    );
  }

  List<Widget> _messagePreviewInfoButton() {
    final preview = notification.messagePreview?.trim();
    if (preview == null || preview.isEmpty) return const [];
    return [const SizedBox(width: 4), _RoomEventInfoButton(message: preview)];
  }
}

class _RoomEventJumpAction extends StatelessWidget {
  const _RoomEventJumpAction({
    required this.notification,
    required this.onOpenRoomEvent,
  });

  final RoomEventNotification notification;
  final RoomEventOpenCallback onOpenRoomEvent;

  @override
  Widget build(BuildContext context) {
    final enabled = notification.roomExists && notification.room.joined;
    return _NotificationInteractiveTarget(
      child: ButtonIcon(
        tooltip: notification.messageId == null ? '进入房间' : '跳转到消息',
        icon: const Icon(Icons.location_on_rounded),
        tone: ButtonTone.primary,
        onPressed: enabled ? () => onOpenRoomEvent(notification) : null,
        size: 34,
      ),
    );
  }
}

class _RoomEventInfoButton extends StatelessWidget {
  const _RoomEventInfoButton({required this.message});

  final String message;

  @override
  Widget build(BuildContext context) {
    return _NotificationInteractiveTarget(
      child: HoverCardAnchor(
        resetKey: message,
        cardWidth: hoverInfoCardWidth(context, message),
        gap: 8,
        child: MouseRegion(
          cursor: SystemMouseCursors.click,
          child: SizedBox.square(
            dimension: 16,
            child: Icon(Icons.info_outline, size: 14, color: UiColors.accent),
          ),
        ),
        cardBuilder: (context) => _RoomEventInfoCard(message: message),
      ),
    );
  }
}

class _RoomEventInfoCard extends StatelessWidget {
  const _RoomEventInfoCard({required this.message});

  final String message;

  @override
  Widget build(BuildContext context) {
    final hoverScope = HoverCardTapRegionScope.maybeOf(context);
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      child: ReadOnlySelectableText(
        value: message,
        maxLines: 8,
        style: UiTypography.body.copyWith(
          color: UiColors.text,
          fontSize: 12,
          height: 1.38,
        ),
        contextMenuTapRegionGroupId: hoverScope?.tapRegionGroup,
        onContextMenuOpenChanged: hoverScope?.onOverlayActivityChanged,
      ),
    );
  }
}

double _notificationAvatarSize(BuildContext context) {
  return _NotificationLayoutMode.usesCompactLayout(context)
      ? CompactActivityLayout.avatarSize
      : 34;
}

Widget _notificationUserAvatar({
  required Key key,
  required UserSummary user,
  required bool userExists,
  required String label,
  required String? imageUrl,
  required String defaultAvatarKey,
}) {
  return Builder(
    builder: (context) {
      final size = _notificationAvatarSize(context);
      final role = user.roomRole?.trim().toLowerCase();
      final platform = Theme.of(context).platform;
      final useBundledSuperuserBrand =
          (platform == TargetPlatform.android ||
              platform == TargetPlatform.windows) &&
          userExists &&
          imageUrl == null &&
          (user.isSuperuser || role == 'superuser');
      if (useBundledSuperuserBrand) {
        return SizedBox.square(
          key: key,
          dimension: size,
          child: ClipOval(
            child: Image.asset(
              'assets/branding/auth_brand_icon.png',
              width: size,
              height: size,
              fit: BoxFit.cover,
              filterQuality: FilterQuality.medium,
            ),
          ),
        );
      }
      return Avatar(
        key: key,
        label: label,
        imageUrl: imageUrl,
        defaultAvatarKey: defaultAvatarKey,
        size: size,
        showFallbackText: userExists,
      );
    },
  );
}

class _NotificationLayoutMode extends InheritedWidget {
  const _NotificationLayoutMode({required this.compact, required super.child});

  final bool compact;

  static bool usesCompactLayout(BuildContext context) {
    final mode = context
        .dependOnInheritedWidgetOfExactType<_NotificationLayoutMode>();
    return mode?.compact ?? HomeAdaptiveLayout.usesCompactLayout(context);
  }

  @override
  bool updateShouldNotify(_NotificationLayoutMode oldWidget) {
    return compact != oldWidget.compact;
  }
}

class _NotificationLayoutMeasurement {
  const _NotificationLayoutMeasurement({
    required this.requiredWidth,
    required this.availableWidth,
  });

  final double requiredWidth;
  final double availableWidth;

  bool get requiresCompactLayout => requiredWidth > availableWidth;
}

class _SynchronizedNotificationLayout extends StatefulWidget {
  const _SynchronizedNotificationLayout({
    required this.itemKeys,
    required this.child,
  });

  final Set<String> itemKeys;
  final Widget child;

  @override
  State<_SynchronizedNotificationLayout> createState() =>
      _SynchronizedNotificationLayoutState();
}

class _SynchronizedNotificationLayoutState
    extends State<_SynchronizedNotificationLayout> {
  final Map<String, _NotificationLayoutMeasurement> _measurements = {};
  bool _compact = false;

  @override
  void didUpdateWidget(_SynchronizedNotificationLayout oldWidget) {
    super.didUpdateWidget(oldWidget);
    _measurements.removeWhere(
      (itemKey, _) => !widget.itemKeys.contains(itemKey),
    );
    _compact = _requiresCompactLayout();
  }

  bool _requiresCompactLayout() {
    return _measurements.entries.any(
      (entry) =>
          widget.itemKeys.contains(entry.key) &&
          entry.value.requiresCompactLayout,
    );
  }

  void _reportMeasurement(
    String itemKey,
    double requiredWidth,
    double availableWidth,
  ) {
    if (!mounted ||
        !widget.itemKeys.contains(itemKey) ||
        !requiredWidth.isFinite ||
        !availableWidth.isFinite) {
      return;
    }
    final previous = _measurements[itemKey];
    if (previous?.requiredWidth == requiredWidth &&
        previous?.availableWidth == availableWidth) {
      return;
    }
    _measurements[itemKey] = _NotificationLayoutMeasurement(
      requiredWidth: requiredWidth,
      availableWidth: availableWidth,
    );
    final nextCompact = _requiresCompactLayout();
    if (nextCompact == _compact) return;
    setState(() => _compact = nextCompact);
  }

  @override
  Widget build(BuildContext context) {
    return _NotificationLayoutGroup(
      compact: _compact,
      onMeasured: _reportMeasurement,
      child: widget.child,
    );
  }
}

typedef _NotificationLayoutMeasurementCallback =
    void Function(String itemKey, double requiredWidth, double availableWidth);

class _NotificationLayoutGroup extends InheritedWidget {
  const _NotificationLayoutGroup({
    required this.compact,
    required this.onMeasured,
    required super.child,
  });

  final bool compact;
  final _NotificationLayoutMeasurementCallback onMeasured;

  static _NotificationLayoutGroup? maybeOf(BuildContext context) {
    return context
        .dependOnInheritedWidgetOfExactType<_NotificationLayoutGroup>();
  }

  @override
  bool updateShouldNotify(_NotificationLayoutGroup oldWidget) {
    return compact != oldWidget.compact;
  }
}

class _NotificationMeasureLayout extends MultiChildRenderObjectWidget {
  _NotificationMeasureLayout({
    required Widget wide,
    required Widget compact,
    required ValueChanged<double> onMeasured,
  }) : _onMeasured = onMeasured,
       super(
         children: [
           _NotificationLayoutMode(compact: false, child: wide),
           _NotificationLayoutMode(compact: true, child: compact),
         ],
       );

  final ValueChanged<double> _onMeasured;

  @override
  RenderObject createRenderObject(BuildContext context) {
    return _RenderNotificationMeasureLayout(onMeasured: _onMeasured);
  }

  @override
  void updateRenderObject(
    BuildContext context,
    _RenderNotificationMeasureLayout renderObject,
  ) {
    renderObject.onMeasured = _onMeasured;
  }
}

class _NotificationMeasureParentData
    extends ContainerBoxParentData<RenderBox> {}

class _RenderNotificationMeasureLayout extends RenderBox
    with
        ContainerRenderObjectMixin<RenderBox, _NotificationMeasureParentData>,
        RenderBoxContainerDefaultsMixin<
          RenderBox,
          _NotificationMeasureParentData
        > {
  _RenderNotificationMeasureLayout({required ValueChanged<double> onMeasured})
    : _onMeasured = onMeasured;

  ValueChanged<double> _onMeasured;
  double? _reportedWidth;
  RenderBox? _activeChild;

  ValueChanged<double> get onMeasured => _onMeasured;

  set onMeasured(ValueChanged<double> value) {
    if (identical(_onMeasured, value)) return;
    _onMeasured = value;
    _reportedWidth = null;
  }

  RenderBox get _wideChild => firstChild!;
  RenderBox get _compactChild => childAfter(_wideChild)!;

  @override
  void setupParentData(RenderBox child) {
    if (child.parentData is! _NotificationMeasureParentData) {
      child.parentData = _NotificationMeasureParentData();
    }
  }

  double _requiredWideWidth(BoxConstraints childConstraints) {
    final availableHeight = childConstraints.maxHeight.isFinite
        ? childConstraints.maxHeight
        : double.infinity;
    return _wideChild.getMaxIntrinsicWidth(availableHeight).ceilToDouble();
  }

  RenderBox _childFor(BoxConstraints childConstraints, double requiredWidth) {
    if (childConstraints.maxWidth.isFinite &&
        requiredWidth > childConstraints.maxWidth) {
      return _compactChild;
    }
    return _wideChild;
  }

  void _reportRequiredWidth(double width) {
    if (_reportedWidth == width) return;
    _reportedWidth = width;
    WidgetsBinding.instance.addPostFrameCallback((_) => _onMeasured(width));
  }

  @override
  void performLayout() {
    final requiredWidth = _requiredWideWidth(constraints);
    final child = _childFor(constraints, requiredWidth);
    child.layout(constraints, parentUsesSize: true);
    final parentData = child.parentData! as _NotificationMeasureParentData;
    parentData.offset = Offset.zero;
    _activeChild = child;
    size = constraints.constrain(child.size);
    _reportRequiredWidth(requiredWidth);
  }

  @override
  Size computeDryLayout(BoxConstraints constraints) {
    final requiredWidth = _requiredWideWidth(constraints);
    final child = _childFor(constraints, requiredWidth);
    return constraints.constrain(child.getDryLayout(constraints));
  }

  @override
  void paint(PaintingContext context, Offset offset) {
    final child = _activeChild;
    if (child == null) return;
    final parentData = child.parentData! as _NotificationMeasureParentData;
    context.paintChild(child, offset + parentData.offset);
  }

  @override
  bool hitTestChildren(BoxHitTestResult result, {required Offset position}) {
    final child = _activeChild;
    if (child == null) return false;
    final parentData = child.parentData! as _NotificationMeasureParentData;
    return child.hitTest(result, position: position - parentData.offset);
  }

  @override
  void visitChildrenForSemantics(RenderObjectVisitor visitor) {
    final child = _activeChild;
    if (child != null) visitor(child);
  }
}

class _ResponsiveNotificationContent extends StatefulWidget {
  const _ResponsiveNotificationContent({
    required this.layoutKey,
    required this.layoutSignature,
    required this.compact,
    required this.wide,
  });

  final String layoutKey;
  final Object layoutSignature;
  final Widget compact;
  final Widget wide;

  @override
  State<_ResponsiveNotificationContent> createState() =>
      _ResponsiveNotificationContentState();
}

class _ResponsiveNotificationContentState
    extends State<_ResponsiveNotificationContent> {
  double? _requiredWideWidth;
  double? _lastReportedRequiredWidth;
  double? _lastReportedAvailableWidth;

  @override
  void didUpdateWidget(_ResponsiveNotificationContent oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.layoutKey != widget.layoutKey ||
        oldWidget.layoutSignature != widget.layoutSignature) {
      _requiredWideWidth = null;
      _lastReportedRequiredWidth = null;
      _lastReportedAvailableWidth = null;
    }
  }

  @override
  Widget build(BuildContext context) {
    if (HomeAdaptiveLayout.usesCompactLayout(context)) {
      return _NotificationLayoutMode(compact: true, child: widget.compact);
    }
    return LayoutBuilder(
      builder: (context, constraints) {
        final group = _NotificationLayoutGroup.maybeOf(context);
        final requiredWidth = _requiredWideWidth;
        if (requiredWidth == null) {
          return _NotificationMeasureLayout(
            wide: widget.wide,
            compact: widget.compact,
            onMeasured: (width) => _handleMeasured(
              requiredWidth: width,
              availableWidth: constraints.maxWidth,
              group: group,
            ),
          );
        }
        _reportMeasurement(
          group: group,
          requiredWidth: requiredWidth,
          availableWidth: constraints.maxWidth,
        );
        final useCompactLayout =
            group?.compact == true ||
            (constraints.maxWidth.isFinite &&
                requiredWidth > constraints.maxWidth);
        return _NotificationLayoutMode(
          compact: useCompactLayout,
          child: useCompactLayout ? widget.compact : widget.wide,
        );
      },
    );
  }

  void _handleMeasured({
    required double requiredWidth,
    required double availableWidth,
    required _NotificationLayoutGroup? group,
  }) {
    if (!mounted) return;
    if (_requiredWideWidth != requiredWidth) {
      setState(() => _requiredWideWidth = requiredWidth);
    }
    _reportMeasurement(
      group: group,
      requiredWidth: requiredWidth,
      availableWidth: availableWidth,
    );
  }

  void _reportMeasurement({
    required _NotificationLayoutGroup? group,
    required double requiredWidth,
    required double availableWidth,
  }) {
    if (group == null ||
        !requiredWidth.isFinite ||
        !availableWidth.isFinite ||
        (_lastReportedRequiredWidth == requiredWidth &&
            _lastReportedAvailableWidth == availableWidth)) {
      return;
    }
    _lastReportedRequiredWidth = requiredWidth;
    _lastReportedAvailableWidth = availableWidth;
    final layoutKey = widget.layoutKey;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      group.onMeasured(layoutKey, requiredWidth, availableWidth);
    });
  }
}

class _CompactNotificationRow extends StatelessWidget {
  const _CompactNotificationRow({
    required this.time,
    required this.timeKey,
    required this.body,
    this.trailing,
  });

  final String time;
  final Key timeKey;
  final Widget body;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        SizedBox(
          width: CompactActivityLayout.compactTimestampColumnWidth,
          child: CompactActivityTimestamp(
            key: timeKey,
            timestamp: time,
            style: UiTypography.label.copyWith(
              color: UiColors.textMuted,
              height: 1.28,
            ),
          ),
        ),
        const SizedBox(width: CompactActivityLayout.compactTimestampContentGap),
        Expanded(child: body),
        if (trailing != null) ...[const SizedBox(width: 8), trailing!],
      ],
    );
  }
}

class _AndroidNotificationBody extends StatelessWidget {
  const _AndroidNotificationBody({super.key, required this.children});

  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    return Wrap(
      spacing: 6,
      runSpacing: 6,
      crossAxisAlignment: WrapCrossAlignment.center,
      children: children,
    );
  }
}

class _NotificationText extends StatelessWidget {
  const _NotificationText({required this.text, required this.query});

  final String text;
  final String query;

  @override
  Widget build(BuildContext context) {
    return HighlightedText(
      text: text,
      query: query,
      maxLines: 1,
      overflow: TextOverflow.ellipsis,
      style: UiTypography.label.copyWith(
        color: UiColors.textSecondary,
        fontWeight: FontWeight.w600,
      ),
    );
  }
}

class _NotificationNewMarker extends StatelessWidget {
  const _NotificationNewMarker({
    required this.show,
    required this.child,
    this.markerKey,
  });

  final bool show;
  final Widget child;
  final Key? markerKey;

  @override
  Widget build(BuildContext context) {
    return Stack(
      clipBehavior: Clip.none,
      children: [
        child,
        if (show)
          Positioned(
            top: 8,
            right: 8,
            child: BadgeDot(key: markerKey, size: 8),
          ),
      ],
    );
  }
}

class _InviteRoleBadge extends StatelessWidget {
  const _InviteRoleBadge({super.key, required this.label, required this.query});

  final String label;
  final String query;

  @override
  Widget build(BuildContext context) {
    return RoleBadge(
      label: label,
      query: query,
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 3),
      borderRadius: UiRadii.sm,
      fontSize: 11,
    );
  }
}

class _InlineUserTarget extends StatelessWidget {
  const _InlineUserTarget({
    required this.user,
    required this.roomId,
    required this.targetId,
    required this.query,
    required this.userExists,
    required this.currentUser,
    required this.onOpenRoom,
    required this.onResolveRoomProfile,
    required this.onResolveRoomUserProfile,
  });

  final UserSummary user;
  final String roomId;
  final String targetId;
  final String query;
  final bool userExists;
  final CurrentUser currentUser;
  final ValueChanged<PublicRoom> onOpenRoom;
  final RoomProfileResolver? onResolveRoomProfile;
  final Future<UserSummary> Function(String roomId, UserSummary user)?
  onResolveRoomUserProfile;

  @override
  Widget build(BuildContext context) {
    final name = roomNotificationUserLabel(user, userExists: userExists);
    final avatarLabel = roomNotificationUserAvatarLabel(
      user,
      userExists: userExists,
    );
    final avatar = _notificationUserAvatar(
      key: ValueKey('notification-room-event-actor-avatar-$targetId'),
      user: user,
      userExists: userExists,
      label: avatarLabel,
      imageUrl: AppConfigScope.of(context).resolveAssetUrl(
        roomNotificationUserAvatarUrl(user, userExists: userExists),
      ),
      defaultAvatarKey: roomNotificationUserAvatarKey(
        user,
        userExists: userExists,
      ),
    );
    final avatarTarget = _NotificationInteractiveTarget(
      child: UserHoverCard(
        user: user.copyWith(isDeleted: !userExists),
        currentUser: currentUser,
        onResolveProfile: onResolveRoomUserProfile == null
            ? null
            : (target) => onResolveRoomUserProfile!(roomId, target),
        onResolveRoomProfile: onResolveRoomProfile,
        onEnterCommonRoom: onOpenRoom,
        child: avatar,
      ),
    );
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        avatarTarget,
        const SizedBox(width: 8),
        Flexible(
          flex: 3,
          child: HighlightedText(
            text: name,
            query: query,
            key: ValueKey('notification-room-event-actor-name-$targetId'),
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: UiTypography.label.copyWith(
              color: UiColors.text,
              fontWeight: FontWeight.w600,
            ),
          ),
        ),
        const SizedBox(width: 7),
        if (_NotificationLayoutMode.usesCompactLayout(context))
          _InviteRoleBadge(
            key: ValueKey('notification-room-event-actor-role-$targetId'),
            label: roomInviteRoleLabel(user),
            query: query,
          )
        else
          Flexible(
            flex: 2,
            child: _InviteRoleBadge(
              key: ValueKey('notification-room-event-actor-role-$targetId'),
              label: roomInviteRoleLabel(user),
              query: query,
            ),
          ),
      ],
    );
  }
}

class _InlineRoomTarget extends StatelessWidget {
  const _InlineRoomTarget({
    required this.room,
    required this.inviteId,
    required this.query,
    required this.roomExists,
    required this.currentUser,
    required this.onOpenRoom,
    required this.onResolveRoomProfile,
    required this.onResolveRoomUserProfile,
  });

  final PublicRoom room;
  final String inviteId;
  final String query;
  final bool roomExists;
  final CurrentUser currentUser;
  final ValueChanged<PublicRoom> onOpenRoom;
  final RoomProfileResolver? onResolveRoomProfile;
  final Future<UserSummary> Function(String roomId, UserSummary user)?
  onResolveRoomUserProfile;

  @override
  Widget build(BuildContext context) {
    final config = AppConfigScope.of(context);
    final roomLabel = roomNotificationRoomLabel(room, roomExists: roomExists);
    final roomAvatarLabel = roomNotificationRoomAvatarLabel(
      room,
      roomExists: roomExists,
    );
    final avatar = Avatar(
      key: ValueKey('notification-room-avatar-$inviteId'),
      label: roomAvatarLabel,
      imageUrl: config.resolveAssetUrl(
        roomNotificationRoomAvatarUrl(room, roomExists: roomExists),
      ),
      defaultAvatarKey: roomNotificationRoomAvatarKey(
        room,
        roomExists: roomExists,
      ),
      size: _notificationAvatarSize(context),
      showFallbackText: roomExists,
    );
    final avatarTarget = _NotificationInteractiveTarget(
      child: roomNotificationRoomCardEnabled(roomExists: roomExists)
          ? RoomHoverCard(
              room: room.copyWith(isDeleted: !roomExists),
              currentUser: currentUser,
              onResolveRoom: onResolveRoomProfile,
              onResolveUserProfile: onResolveRoomUserProfile == null
                  ? null
                  : (user) => onResolveRoomUserProfile!(room.id, user),
              onEnterRoom: room.joined ? onOpenRoom : null,
              child: avatar,
            )
          : avatar,
    );
    final roomName = HighlightedText(
      text: roomLabel,
      query: query,
      key: ValueKey('notification-room-name-$inviteId'),
      maxLines: 1,
      overflow: TextOverflow.ellipsis,
      style: UiTypography.label.copyWith(
        color: UiColors.text,
        fontWeight: FontWeight.w600,
      ),
    );
    if (!_NotificationLayoutMode.usesCompactLayout(context)) {
      return Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          avatarTarget,
          const SizedBox(width: 5),
          Flexible(child: roomName),
        ],
      );
    }
    return LayoutBuilder(
      builder: (context, constraints) {
        if (constraints.maxWidth < 39) {
          return SizedBox(width: constraints.maxWidth, child: roomName);
        }
        return Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            avatarTarget,
            const SizedBox(width: 5),
            Flexible(child: roomName),
          ],
        );
      },
    );
  }
}

class _InviteDecisionActions extends StatelessWidget {
  const _InviteDecisionActions({
    required this.invite,
    required this.busy,
    required this.enabled,
    required this.onReviewInvite,
  });

  final RoomInvite invite;
  final bool busy;
  final bool enabled;
  final RoomInviteReviewCallback onReviewInvite;

  @override
  Widget build(BuildContext context) {
    return _NotificationInteractiveTarget(
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          ButtonIcon(
            tooltip: '拒绝邀请',
            icon: const Icon(Icons.close),
            tone: ButtonTone.danger,
            onPressed: enabled
                ? () => unawaited(onReviewInvite(invite, false))
                : null,
            size: 34,
          ),
          const SizedBox(width: 8),
          ButtonIcon(
            tooltip: '接受邀请',
            icon: const Icon(Icons.check),
            tone: ButtonTone.primary,
            onPressed: enabled
                ? () => unawaited(onReviewInvite(invite, true))
                : null,
            loading: busy,
            size: 34,
          ),
        ],
      ),
    );
  }
}

class _ApplicationWithdrawAction extends StatelessWidget {
  const _ApplicationWithdrawAction({
    required this.application,
    required this.busy,
    required this.enabled,
    required this.onWithdrawApplication,
  });

  final RoomApplication application;
  final bool busy;
  final bool enabled;
  final RoomApplicationWithdrawCallback onWithdrawApplication;

  @override
  Widget build(BuildContext context) {
    return _NotificationInteractiveTarget(
      child: Button(
        tooltip: '撤回申请',
        tone: ButtonTone.danger,
        onPressed: enabled
            ? () => unawaited(onWithdrawApplication(application))
            : null,
        loading: busy,
        height: 34,
        padding: const EdgeInsets.symmetric(horizontal: 10),
        child: const Text('撤回'),
      ),
    );
  }
}

class _InvalidInviteLabel extends StatelessWidget {
  const _InvalidInviteLabel({required this.invite, required this.query});

  final RoomInvite invite;
  final String query;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: UiColors.surfacePressed,
        borderRadius: BorderRadius.circular(UiRadii.md),
        border: Border.all(color: UiColors.border),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 6),
        child: HighlightedText(
          text: roomInviteDecisionLabel(invite),
          query: query,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: UiTypography.label.copyWith(
            color: UiColors.textMuted,
            fontWeight: FontWeight.w600,
          ),
        ),
      ),
    );
  }
}

class _ProcessedInviteLabel extends StatelessWidget {
  const _ProcessedInviteLabel({required this.invite, required this.query});

  final RoomInvite invite;
  final String query;

  @override
  Widget build(BuildContext context) {
    final accepted = isAcceptedRoomInvite(invite);
    return DecoratedBox(
      decoration: BoxDecoration(
        color: accepted ? UiColors.selected : const Color(0xFF2E1F22),
        borderRadius: BorderRadius.circular(UiRadii.md),
        border: Border.all(
          color: accepted ? UiColors.accentBorder : UiColors.dangerBorder,
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 6),
        child: HighlightedText(
          text: roomInviteDecisionLabel(invite),
          query: query,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: UiTypography.label.copyWith(
            color: accepted ? UiColors.accent : UiColors.danger,
            fontWeight: FontWeight.w600,
          ),
        ),
      ),
    );
  }
}

class _ProcessedApplicationLabel extends StatelessWidget {
  const _ProcessedApplicationLabel({
    required this.application,
    required this.query,
  });

  final RoomApplication application;
  final String query;

  @override
  Widget build(BuildContext context) {
    final approved = isApprovedRoomApplication(application);
    final rejected = isRejectedRoomApplication(application);
    final label = roomApplicationStatusLabel(application);
    return DecoratedBox(
      decoration: BoxDecoration(
        color: approved
            ? UiColors.selected
            : rejected
            ? const Color(0xFF2E1F22)
            : UiColors.surfacePressed,
        borderRadius: BorderRadius.circular(UiRadii.md),
        border: Border.all(
          color: approved
              ? UiColors.accentBorder
              : rejected
              ? UiColors.dangerBorder
              : UiColors.border,
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 6),
        child: HighlightedText(
          text: label,
          query: query,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: UiTypography.label.copyWith(
            color: approved
                ? UiColors.accent
                : rejected
                ? UiColors.danger
                : UiColors.textMuted,
            fontWeight: FontWeight.w600,
          ),
        ),
      ),
    );
  }
}

class _NotificationEmptyState extends StatelessWidget {
  const _NotificationEmptyState({
    required this.icon,
    required this.title,
    this.subtitle,
    this.actionLabel,
    this.onAction,
    this.danger = false,
  });

  final IconData icon;
  final String title;
  final String? subtitle;
  final String? actionLabel;
  final VoidCallback? onAction;
  final bool danger;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(22),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              icon,
              color: danger ? UiColors.danger : UiColors.textMuted,
              size: 30,
            ),
            const SizedBox(height: 8),
            Text(
              title,
              textAlign: TextAlign.center,
              style: UiTypography.body.copyWith(
                color: danger ? UiColors.danger : UiColors.textMuted,
                fontWeight: FontWeight.w600,
              ),
            ),
            if (subtitle != null) ...[
              const SizedBox(height: 6),
              Text(
                subtitle!,
                textAlign: TextAlign.center,
                maxLines: 3,
                overflow: TextOverflow.ellipsis,
                style: UiTypography.label.copyWith(color: UiColors.textMuted),
              ),
            ],
            if (actionLabel != null && onAction != null) ...[
              const SizedBox(height: 14),
              Button(
                icon: const Icon(Icons.refresh),
                onPressed: onAction,
                child: Text(actionLabel!),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

IconData _emptyIcon(RoomNotificationFilter filter) {
  return switch (filter) {
    RoomNotificationFilter.all => Icons.notifications_none,
    RoomNotificationFilter.invites => Icons.mail_outline,
    RoomNotificationFilter.applications => Icons.assignment_turned_in_outlined,
    RoomNotificationFilter.roomNotifications => Icons.meeting_room_outlined,
  };
}

String _emptyTitle({
  required RoomNotificationFilter filter,
  required String query,
  required bool hasActiveDateFilter,
}) {
  if (query.trim().isNotEmpty || hasActiveDateFilter) return '没有匹配通知';
  return switch (filter) {
    RoomNotificationFilter.all => '暂无通知',
    RoomNotificationFilter.invites => '暂无邀请',
    RoomNotificationFilter.applications => '暂无申请',
    RoomNotificationFilter.roomNotifications => '暂无房间',
  };
}
