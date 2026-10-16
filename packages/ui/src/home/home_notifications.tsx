import {
  createContext,
  useContext,
  useEffect,
  useLayoutEffect,
  useMemo,
  useReducer,
  useRef,
  useState,
  type CSSProperties,
  type ReactNode,
  type RefObject,
} from 'react';

import { userFacingErrorMessage } from '@gang-chat/core/app/error_display';
import {
  RoomNotificationDateRange,
  RoomNotificationFilter,
  RoomNotificationItemType,
  canReviewNotificationInvite,
  canWithdrawNotificationApplication,
  isAcceptedRoomInvite,
  isActionablePendingRoomInvite,
  isApprovedRoomApplication,
  isInvalidPendingRoomInvite,
  isPendingRoomApplication,
  isPendingRoomInvite,
  isRejectedRoomApplication,
  kRoomEventNotificationCreatorTransferDemoted,
  kRoomEventNotificationMemberRemoved,
  kRoomEventNotificationMentioned,
  kRoomEventNotificationRoleDemoted,
  kRoomEventNotificationRolePromoted,
  roomApplicationReviewActionLabel,
  roomApplicationStatusLabel,
  roomEventNotificationRoleActionLabel,
  roomInviteDecisionLabel,
  roomInviteRoleLabel,
  roomInviteTimestampLabel,
  roomNotificationDateLabel,
  roomNotificationRoleLabel,
  roomNotificationRoomAvatarKey,
  roomNotificationRoomAvatarLabel,
  roomNotificationRoomAvatarUrl,
  roomNotificationRoomCardEnabled,
  roomNotificationRoomLabel,
  roomNotificationsForView,
  roomNotificationUserAvatarKey,
  roomNotificationUserAvatarLabel,
  roomNotificationUserAvatarUrl,
  roomNotificationUserLabel,
  type RoomNotificationItem,
} from '@gang-chat/core/app/room_notifications';
import { HostPlatform } from '@gang-chat/core/config/host_platform';
import { unawaited } from '@gang-chat/core/lib/async';
import type {
  CurrentUser,
  PublicRoom,
  RoomApplication,
  RoomEventNotification,
  RoomInvite,
  UserSummary,
} from '@gang-chat/core/protocol/models';

import { EdgeInsets } from '../lib/edge_insets';
import { Column, Expanded, Flexible, Row, SizedBox, Stack, Positioned, Text } from '../lib/layout';
import { SystemMouseCursors } from '../lib/mouse_cursors';
import { CircularProgressIndicator } from '../lib/progress_indicator';
import { TextEditingController } from '../lib/text_editing';
import { useMountedRef } from '../lib/use_mounted';
import { useViewState, ViewState } from '../lib/view_state';
import { useAppConfig } from '../ui/app_config_scope';
import { Avatar } from '../ui/avatar';
import { BadgeDot } from '../ui/badge_dot';
import { Button, ButtonIcon, ButtonTone } from '../ui/button';
import { UiCheckbox } from '../ui/checkbox';
import { UiContextMenuItem, UiContextMenuSection, UiContextMenuTriggerRegion, showUiContextMenu } from '../ui/context_menu';
import {
  DialogFrame,
  FloatingNotice,
  FloatingNoticeEmitter,
  FloatingNoticeTone,
  ResponsiveDialogAction,
  ResponsiveDialogActionBar,
  showFloatingErrorNotice,
  showFloatingSuccessNotice,
} from '../ui/feedback';
import { HighlightedText } from '../ui/highlighted_text';
import { Icon, Icons, type IconData } from '../ui/icon';
import { Input } from '../ui/input';
import { showDialog, useRoute } from '../ui/overlay';
import type { WindowPoint } from '../ui/platform_gestures';
import { ReadOnlySelectableText } from '../ui/read_only_text_box';
import { RoleBadge } from '../ui/role_badge';
import { Segment, SegmentedControl } from '../ui/segmented_control';
import { SettingsScaffold } from '../ui/settings_scaffold';
import { PressableSurface } from '../ui/surface';
import { UiColors, UiCurves, UiRadii, UiTypography, argb } from '../ui/tokens';
import { HomeAdaptiveLayout } from './adaptive_layout';
import { CompactActivityLayout, CompactActivityTimestamp } from './compact_activity_layout';
import { _NotificationCalendarDialog } from './home_notification_calendar';
import { HoverCardAnchor, HoverCardTapRegionScope, hoverInfoCardWidth } from './hover_card_anchor';
import { RoomHoverCard, UserHoverCard, type RoomProfileResolver } from './room_profile_card';

export type RoomInviteReviewCallback = (invite: RoomInvite, accept: boolean) => Promise<void>;
export type RoomApplicationWithdrawCallback = (application: RoomApplication) => Promise<void>;
export type RoomEventOpenCallback = (notification: RoomEventNotification) => void;
export type RoomNotificationCopyCallback = (item: RoomNotificationItem) => Promise<void>;
export type RoomNotificationDeleteCallback = (item: RoomNotificationItem) => Promise<void>;
export type RoomNotificationsDeleteCallback = (items: RoomNotificationItem[]) => Promise<void>;
/** `Future<UserSummary> Function(String roomId, UserSummary user)`. */
export type RoomNotificationUserProfileResolver = (roomId: string, user: UserSummary) => Promise<UserSummary>;

// ---------------------------------------------------------------------------
// HomeNotificationsPane

export interface HomeNotificationsPaneProps {
  invites: readonly RoomInvite[];
  applications: readonly RoomApplication[];
  roomNotifications: readonly RoomEventNotification[];
  currentUser: CurrentUser;
  loading: boolean;
  error: string | null;
  busyInviteId: string | null;
  busyApplicationId: string | null;
  onClose: () => void;
  onRefresh: () => void;
  onReviewInvite: RoomInviteReviewCallback;
  onWithdrawApplication: RoomApplicationWithdrawCallback;
  onOpenRoom: (room: PublicRoom) => void;
  onOpenRoomEvent: RoomEventOpenCallback;
  onCopyNotification?: RoomNotificationCopyCallback | null;
  onDeleteNotification?: RoomNotificationDeleteCallback | null;
  onDeleteNotifications?: RoomNotificationsDeleteCallback | null;
  onResolveRoomProfile?: RoomProfileResolver | null;
  onResolveRoomUserProfile?: RoomNotificationUserProfileResolver | null;
}

function _sameCreatedAt(a: Date | null, b: Date | null): boolean {
  return (a?.getTime() ?? null) === (b?.getTime() ?? null);
}

class _HomeNotificationsPaneState extends ViewState<HomeNotificationsPaneProps> {
  // Dart initialized the `late` date ranges in initState; the first build
  // needs them, so they are set from the initial props here.
  constructor(initial: HomeNotificationsPaneProps) {
    super();
    this._defaultDateRange = this._defaultDateRangeFor(initial.currentUser);
    this._dateRange = this._defaultDateRange;
  }

  readonly _searchController = new TextEditingController();
  _filter: RoomNotificationFilter = RoomNotificationFilter.all;
  _defaultDateRange: RoomNotificationDateRange;
  _dateRange: RoomNotificationDateRange;
  _selectionMode = false;
  _selectedNotificationIds: Set<string> = new Set<string>();
  _query = '';
  _lastSearchText = '';

  override initState(): void {
    this._searchController.addListener(this._handleSearchChanged);
  }

  override didUpdateProps(oldWidget: HomeNotificationsPaneProps): void {
    if (_sameCreatedAt(oldWidget.currentUser.createdAt, this.widget.currentUser.createdAt)) return;
    // PORT: didUpdateProps runs after the render that received the new
    // props, so the new range re-renders (Flutter updated before build).
    this.setState(() => {
      const wasUsingDefault = this._dateRange.equals(this._defaultDateRange);
      this._defaultDateRange = this._defaultDateRangeFor(this.widget.currentUser);
      if (
        wasUsingDefault ||
        this._dateRange.startDate.getTime() < this._defaultDateRange.startDate.getTime() ||
        this._dateRange.endDate.getTime() > this._defaultDateRange.endDate.getTime()
      ) {
        this._dateRange = this._defaultDateRange;
      }
    });
  }

  override dispose(): void {
    this._searchController.removeListener(this._handleSearchChanged);
    this._searchController.dispose();
  }

  _handleSearchChanged = (): void => {
    const text = this._searchController.text;
    if (text === this._lastSearchText) return;
    this._lastSearchText = text;
    this.setState(() => {
      this._query = text;
      if (this._selectionMode) this._selectedNotificationIds = new Set<string>();
    });
  };

  _defaultDateRangeFor(user: CurrentUser): RoomNotificationDateRange {
    return RoomNotificationDateRange.defaultFor({ accountCreatedAt: user.createdAt, today: new Date() });
  }

  _refreshDefaultDateRangeForToday(): void {
    const nextDefault = this._defaultDateRangeFor(this.widget.currentUser);
    if (nextDefault.equals(this._defaultDateRange)) return;
    const wasUsingDefault = this._dateRange.equals(this._defaultDateRange);
    this._defaultDateRange = nextDefault;
    if (wasUsingDefault) this._dateRange = nextDefault;
  }

  _showDateRangeFilter = async (): Promise<void> => {
    const initialRange = this._dateRange;
    const defaultRange = this._defaultDateRange;
    const result = await showDialog<RoomNotificationDateRange>(() => (
      <_NotificationDateRangeDialog initialRange={initialRange} defaultRange={defaultRange} />
    ));
    if (!this.mounted || result == null || result.equals(this._dateRange)) return;
    this.setState(() => {
      this._dateRange = result;
      if (this._selectionMode) this._selectedNotificationIds = new Set<string>();
    });
  };

  _toggleSelectionMode = (): void => {
    this.setState(() => {
      this._selectionMode = !this._selectionMode;
      if (!this._selectionMode) this._selectedNotificationIds = new Set<string>();
    });
  };

  _toggleNotificationSelection = (item: RoomNotificationItem): void => {
    this.setState(() => {
      const ids = new Set(this._selectedNotificationIds);
      if (ids.has(item.id)) {
        ids.delete(item.id);
      } else {
        ids.add(item.id);
      }
      this._selectedNotificationIds = ids;
    });
  };

  _toggleVisibleNotificationSelection(visibleItems: readonly RoomNotificationItem[]): void {
    const visibleIds = new Set(visibleItems.map((item) => item.id));
    if (visibleIds.size === 0) return;
    this.setState(() => {
      const ids = new Set(this._selectedNotificationIds);
      if ([...visibleIds].every((id) => ids.has(id))) {
        for (const id of visibleIds) ids.delete(id);
      } else {
        for (const id of visibleIds) ids.add(id);
      }
      this._selectedNotificationIds = ids;
    });
  }

  _clearSelectedNotifications = (): void => {
    if (this._selectedNotificationIds.size === 0) return;
    this.setState(() => {
      this._selectedNotificationIds = new Set<string>();
    });
  };

  build(): ReactNode {
    this._refreshDefaultDateRangeForToday();
    const widget = this.widget;
    const visibleItems = roomNotificationsForView({
      invites: widget.invites,
      applications: widget.applications,
      roomEvents: widget.roomNotifications,
      query: this._query,
      filter: this._filter,
      dateRange: this._dateRange,
    });
    const hasActiveDateFilter = !this._dateRange.equals(this._defaultDateRange);
    const rawNotificationCount = widget.invites.length + widget.applications.length + widget.roomNotifications.length;
    const allVisibleSelected =
      visibleItems.length > 0 && visibleItems.every((item) => this._selectedNotificationIds.has(item.id));
    return (
      <SettingsScaffold
        icon={Icons.notifications_none}
        title="通知"
        onBack={widget.onClose}
        headerAction={
          <ButtonIcon
            data-testid="home-notifications-refresh-button"
            tooltip="刷新通知"
            icon={<Icon icon={Icons.refresh} />}
            onPressed={widget.loading ? null : widget.onRefresh}
            loading={widget.loading && rawNotificationCount > 0}
            size={38}
          />
        }
        pinned={
          <Column crossAxisAlignment="stretch">
            <Row crossAxisAlignment="start">
              <Expanded>
                <Input controller={this._searchController} hintText="搜索通知" prefixIcon={Icons.search} showClearButton />
              </Expanded>
              <SizedBox width={10} />
              <ButtonIcon
                data-testid="home-notifications-date-filter-button"
                tooltip="筛选通知日期"
                icon={<Icon icon={Icons.calendar_month_outlined} />}
                selected={hasActiveDateFilter}
                onPressed={() => void this._showDateRangeFilter()}
                size={Input.defaultHeight}
              />
              <SizedBox width={8} />
              <ButtonIcon
                data-testid="home-notifications-select-button"
                tooltip={this._selectionMode ? '退出批量管理' : '批量管理'}
                icon={<Icon icon={Icons.checklist_outlined} />}
                selected={this._selectionMode}
                onPressed={this._toggleSelectionMode}
                size={Input.defaultHeight}
              />
            </Row>
            <SizedBox height={12} />
            <Row>
              {this._selectionMode ? (
                <>
                  <div data-testid="home-notifications-select-all" style={{ display: 'contents' }}>
                    <UiCheckbox
                      value={allVisibleSelected}
                      onChanged={
                        visibleItems.length === 0 ? null : () => this._toggleVisibleNotificationSelection(visibleItems)
                      }
                      tooltip={allVisibleSelected ? '取消全选当前通知' : '全选当前通知'}
                      semanticLabel="全选当前通知"
                    />
                  </div>
                  <SizedBox width={8} />
                </>
              ) : null}
              <Expanded>
                <SegmentedControl<RoomNotificationFilter>
                  expanded
                  value={this._filter}
                  segments={[
                    new Segment({ value: RoomNotificationFilter.all, label: '全部', icon: Icons.inbox_outlined }),
                    new Segment({ value: RoomNotificationFilter.invites, label: '邀请', icon: Icons.mail_outline }),
                    new Segment({
                      value: RoomNotificationFilter.applications,
                      label: '申请',
                      icon: Icons.assignment_turned_in_outlined,
                    }),
                    new Segment({
                      value: RoomNotificationFilter.roomNotifications,
                      label: '房间',
                      icon: Icons.meeting_room_outlined,
                    }),
                  ]}
                  onChanged={(value) =>
                    this.setState(() => {
                      this._filter = value;
                      if (this._selectionMode) {
                        this._selectedNotificationIds = new Set<string>();
                      }
                    })
                  }
                />
              </Expanded>
            </Row>
          </Column>
        }
        body={
          <_NotificationsBody
            items={visibleItems}
            loading={widget.loading}
            error={widget.error}
            rawNotificationCount={rawNotificationCount}
            query={this._query}
            filter={this._filter}
            hasActiveDateFilter={hasActiveDateFilter}
            busyInviteId={widget.busyInviteId}
            busyApplicationId={widget.busyApplicationId}
            onRefresh={widget.onRefresh}
            onReviewInvite={widget.onReviewInvite}
            onWithdrawApplication={widget.onWithdrawApplication}
            currentUser={widget.currentUser}
            onOpenRoom={widget.onOpenRoom}
            onOpenRoomEvent={widget.onOpenRoomEvent}
            onCopyNotification={widget.onCopyNotification ?? null}
            onDeleteNotification={widget.onDeleteNotification ?? null}
            onDeleteNotifications={widget.onDeleteNotifications ?? null}
            selectionMode={this._selectionMode}
            selectedNotificationIds={this._selectedNotificationIds}
            onToggleNotificationSelection={this._toggleNotificationSelection}
            onClearSelectedNotifications={this._clearSelectedNotifications}
            onResolveRoomProfile={widget.onResolveRoomProfile ?? null}
            onResolveRoomUserProfile={widget.onResolveRoomUserProfile ?? null}
          />
        }
      />
    );
  }
}

export function HomeNotificationsPane(props: HomeNotificationsPaneProps) {
  const state = useViewState(() => new _HomeNotificationsPaneState(props), props);
  return <>{state.build()}</>;
}

// ---------------------------------------------------------------------------
// Date range dialog

interface _NotificationDateRangeDialogProps {
  initialRange: RoomNotificationDateRange;
  defaultRange: RoomNotificationDateRange;
  title?: string;
  description?: string;
}

function _NotificationDateRangeDialog({
  initialRange,
  defaultRange,
  title = '筛选通知日期',
  description = '选择包含首尾日期的通知时间区间。',
}: _NotificationDateRangeDialogProps) {
  const route = useRoute<RoomNotificationDateRange>();
  const mounted = useMountedRef();
  const [range, setRange] = useState<{ startDate: Date; endDate: Date }>(() => ({
    startDate: initialRange.startDate,
    endDate: initialRange.endDate,
  }));

  const pickDate = async (opts: { start: boolean }): Promise<void> => {
    const { start } = opts;
    const selected = start ? range.startDate : range.endDate;
    const picked = await showDialog<Date>(() => (
      <_NotificationCalendarDialog
        title={start ? '选择开始日期' : '选择结束日期'}
        initialDate={selected}
        firstDate={defaultRange.startDate}
        lastDate={defaultRange.endDate}
      />
    ));
    if (!mounted.current || picked == null) return;
    setRange((current) => {
      let { startDate, endDate } = current;
      if (start) {
        startDate = picked;
        if (endDate.getTime() < picked.getTime()) endDate = picked;
      } else {
        endDate = picked;
        if (startDate.getTime() > picked.getTime()) startDate = picked;
      }
      return { startDate, endDate };
    });
  };

  return (
    <DialogFrame
      title={title}
      icon={Icons.calendar_month_outlined}
      maxWidth={420}
      actionBar={
        <ResponsiveDialogActionBar
          leadingActionCount={1}
          actions={[
            new ResponsiveDialogAction({
              buttonKey: 'notification-date-reset-button',
              label: '重置',
              icon: Icons.restart_alt,
              onPressed: () => route.close(defaultRange),
            }),
            new ResponsiveDialogAction({ label: '取消', onPressed: () => route.close() }),
            new ResponsiveDialogAction({
              buttonKey: 'notification-date-confirm-button',
              label: '应用',
              icon: Icons.check,
              tone: ButtonTone.primary,
              onPressed: () =>
                route.close(new RoomNotificationDateRange({ startDate: range.startDate, endDate: range.endDate })),
            }),
          ]}
        />
      }
    >
      <Column mainAxisSize="min" crossAxisAlignment="stretch">
        <Text style={{ ...UiTypography.label, color: UiColors.textMuted }}>{description}</Text>
        <SizedBox height={16} />
        <_NotificationDateField
          testId="notification-start-date-field"
          label="开始日期"
          date={range.startDate}
          onPressed={() => void pickDate({ start: true })}
        />
        <SizedBox height={14} />
        <_NotificationDateField
          testId="notification-end-date-field"
          label="结束日期"
          date={range.endDate}
          onPressed={() => void pickDate({ start: false })}
        />
      </Column>
    </DialogFrame>
  );
}

/**
 * Port of `showDateRangeFilterDialog(context, …)`; the `BuildContext`
 * argument is dropped. Resolves with null when the dialog is dismissed.
 */
export function showDateRangeFilterDialog(opts: {
  initialRange: RoomNotificationDateRange;
  defaultRange: RoomNotificationDateRange;
  title?: string;
  description?: string;
}): Promise<RoomNotificationDateRange | null> {
  const { initialRange, defaultRange, title = '筛选日期', description = '选择包含首尾日期的时间区间。' } = opts;
  return showDialog<RoomNotificationDateRange>(() => (
    <_NotificationDateRangeDialog
      initialRange={initialRange}
      defaultRange={defaultRange}
      title={title}
      description={description}
    />
  )).then((value) => value ?? null);
}

function _NotificationDateField({
  testId,
  label,
  date,
  onPressed,
}: {
  testId: string;
  label: string;
  date: Date;
  onPressed: () => void;
}) {
  return (
    <Column data-testid={testId} crossAxisAlignment="stretch">
      <Text style={UiTypography.label}>{label}</Text>
      <SizedBox height={8} />
      <PressableSurface
        height={40}
        onPressed={onPressed}
        padding={EdgeInsets.symmetric({ horizontal: 12 })}
        backgroundColor={UiColors.background}
      >
        <Row>
          <Icon icon={Icons.calendar_today_outlined} size={16} color={UiColors.textMuted} />
          <SizedBox width={10} />
          <Expanded>
            <Text style={UiTypography.body}>{roomNotificationDateLabel(date)}</Text>
          </Expanded>
          <Icon icon={Icons.chevron_right} size={18} color={UiColors.textMuted} />
        </Row>
      </PressableSurface>
    </Column>
  );
}

// ---------------------------------------------------------------------------
// Body

interface _NotificationsBodyProps {
  items: readonly RoomNotificationItem[];
  currentUser: CurrentUser;
  loading: boolean;
  error: string | null;
  rawNotificationCount: number;
  query: string;
  filter: RoomNotificationFilter;
  hasActiveDateFilter: boolean;
  busyInviteId: string | null;
  busyApplicationId: string | null;
  onRefresh: () => void;
  onReviewInvite: RoomInviteReviewCallback;
  onWithdrawApplication: RoomApplicationWithdrawCallback;
  onOpenRoom: (room: PublicRoom) => void;
  onOpenRoomEvent: RoomEventOpenCallback;
  onCopyNotification: RoomNotificationCopyCallback | null;
  onDeleteNotification: RoomNotificationDeleteCallback | null;
  onDeleteNotifications: RoomNotificationsDeleteCallback | null;
  selectionMode: boolean;
  selectedNotificationIds: ReadonlySet<string>;
  onToggleNotificationSelection: (item: RoomNotificationItem) => void;
  onClearSelectedNotifications: () => void;
  onResolveRoomProfile: RoomProfileResolver | null;
  onResolveRoomUserProfile: RoomNotificationUserProfileResolver | null;
}

/** The scaffold body is an Expanded; Center / ListView fill it. */
const _bodyFillStyle: CSSProperties = { flex: '1 1 0', minHeight: 0, display: 'flex', flexDirection: 'column' };

function _NotificationsBody(props: _NotificationsBodyProps) {
  const {
    items,
    loading,
    error,
    rawNotificationCount,
    query,
    filter,
    hasActiveDateFilter,
    selectionMode,
    selectedNotificationIds,
  } = props;
  const compactHome = HomeAdaptiveLayout.usesCompactLayout();

  const withErrorNotice = (child: ReactNode): ReactNode => {
    const message = error?.trim() ?? null;
    if (message == null || message.length === 0) return child;
    return (
      <FloatingNoticeEmitter notices={[new FloatingNotice({ message, tone: FloatingNoticeTone.error, duration: null })]}>
        {child}
      </FloatingNoticeEmitter>
    );
  };

  if (loading && rawNotificationCount === 0) {
    return (
      <div style={{ ..._bodyFillStyle, alignItems: 'center', justifyContent: 'center' }}>
        <SizedBox width={24} height={24}>
          <CircularProgressIndicator size={24} strokeWidth={2.2} color={UiColors.accent} />
        </SizedBox>
      </div>
    );
  }

  if (error != null && rawNotificationCount === 0) {
    return withErrorNotice(
      <_NotificationEmptyState
        icon={Icons.error_outline}
        title="通知加载失败"
        subtitle="请稍后重试"
        actionLabel="重试"
        onAction={props.onRefresh}
        danger
      />,
    );
  }

  if (items.length === 0) {
    return withErrorNotice(
      <_NotificationEmptyState icon={_emptyIcon(filter)} title={_emptyTitle({ filter, query, hasActiveDateFilter })} />,
    );
  }

  const listHorizontalPadding = compactHome ? CompactActivityLayout.compactNotificationListHorizontalPadding : 22;
  const selectedItems = items.filter((candidate) => selectedNotificationIds.has(candidate.id));
  return withErrorNotice(
    <_SynchronizedNotificationLayout itemKeys={new Set(items.map((item) => item.id))}>
      <div
        style={{
          ..._bodyFillStyle,
          overflowY: 'auto',
          padding: `0 ${listHorizontalPadding}px 22px`,
          boxSizing: 'border-box',
          gap: 10,
        }}
      >
        {items.map((item) => {
          const card = (
            <_NotificationContextMenuRegion
              item={item}
              selectionMode={selectionMode}
              selectedItems={selectedItems}
              onToggleSelection={selectionMode ? () => props.onToggleNotificationSelection(item) : null}
              onCopyNotification={props.onCopyNotification}
              onDeleteNotification={props.onDeleteNotification}
              onDeleteNotifications={props.onDeleteNotifications}
              onSelectionDeleted={props.onClearSelectedNotifications}
              childBuilder={(contextMenuActive) => _buildNotificationRow(props, item, contextMenuActive)}
            />
          );
          if (!selectionMode) {
            return (
              <div key={item.id} data-testid={`notification-context-row-${item.id}`} style={{ flexShrink: 0 }}>
                {card}
              </div>
            );
          }
          const selected = selectedNotificationIds.has(item.id);
          return (
            <Row key={item.id} data-testid={`notification-context-row-${item.id}`} style={{ flexShrink: 0 }}>
              <div data-testid={`notification-selectbox-${item.id}`} style={{ display: 'contents' }}>
                <UiCheckbox
                  value={selected}
                  onChanged={() => props.onToggleNotificationSelection(item)}
                  tooltip={selected ? '取消选择通知' : '选择通知'}
                  semanticLabel="选择通知"
                />
              </div>
              <SizedBox width={8} />
              <Expanded>{card}</Expanded>
            </Row>
          );
        })}
      </div>
    </_SynchronizedNotificationLayout>,
  );
}

function _buildNotificationRow(props: _NotificationsBodyProps, item: RoomNotificationItem, contextMenuActive: boolean): ReactNode {
  switch (item.type) {
    case RoomNotificationItemType.invite:
      return (
        <_RoomInviteNotificationRow
          invite={item.invite!}
          query={props.query}
          busy={props.busyInviteId === item.invite!.id}
          busyInviteId={props.busyInviteId}
          contextMenuActive={contextMenuActive}
          onReviewInvite={props.onReviewInvite}
          currentUser={props.currentUser}
          onOpenRoom={props.onOpenRoom}
          onResolveRoomProfile={props.onResolveRoomProfile}
          onResolveRoomUserProfile={props.onResolveRoomUserProfile}
        />
      );
    case RoomNotificationItemType.applicationRequested:
      return (
        <_RoomApplicationRequestNotificationRow
          application={item.application!}
          query={props.query}
          busy={props.busyApplicationId === item.application!.id}
          busyApplicationId={props.busyApplicationId}
          contextMenuActive={contextMenuActive}
          onWithdrawApplication={props.onWithdrawApplication}
          currentUser={props.currentUser}
          onOpenRoom={props.onOpenRoom}
          onResolveRoomProfile={props.onResolveRoomProfile}
          onResolveRoomUserProfile={props.onResolveRoomUserProfile}
        />
      );
    case RoomNotificationItemType.applicationReviewed:
      return (
        <_RoomApplicationReviewNotificationRow
          application={item.application!}
          query={props.query}
          contextMenuActive={contextMenuActive}
          currentUser={props.currentUser}
          onOpenRoom={props.onOpenRoom}
          onResolveRoomProfile={props.onResolveRoomProfile}
          onResolveRoomUserProfile={props.onResolveRoomUserProfile}
        />
      );
    case RoomNotificationItemType.roomEvent:
      return (
        <_RoomEventNotificationRow
          notification={item.roomEvent!}
          query={props.query}
          contextMenuActive={contextMenuActive}
          currentUser={props.currentUser}
          onOpenRoom={props.onOpenRoom}
          onOpenRoomEvent={props.onOpenRoomEvent}
          onResolveRoomProfile={props.onResolveRoomProfile}
          onResolveRoomUserProfile={props.onResolveRoomUserProfile}
        />
      );
  }
}

// ---------------------------------------------------------------------------
// Context menu

interface _NotificationContextMenuRegionProps {
  item: RoomNotificationItem;
  childBuilder: (contextMenuActive: boolean) => ReactNode;
  selectionMode: boolean;
  selectedItems: readonly RoomNotificationItem[];
  onToggleSelection: (() => void) | null;
  onCopyNotification: RoomNotificationCopyCallback | null;
  onDeleteNotification: RoomNotificationDeleteCallback | null;
  onDeleteNotifications: RoomNotificationsDeleteCallback | null;
  onSelectionDeleted: (() => void) | null;
}

class _NotificationContextMenuRegionState extends ViewState<_NotificationContextMenuRegionProps> {
  _contextMenuActive = false;

  build(): ReactNode {
    const widget = this.widget;
    const itemSelected = widget.selectedItems.some((selected) => selected.id === widget.item.id);
    return (
      <UiContextMenuTriggerRegion
        onTap={widget.selectionMode ? widget.onToggleSelection : null}
        onTriggered={this._showContextMenu}
      >
        {widget.childBuilder(this._contextMenuActive || itemSelected)}
      </UiContextMenuTriggerRegion>
    );
  }

  _showContextMenu = (position: WindowPoint): void => {
    const widget = this.widget;
    const contextItems: readonly RoomNotificationItem[] = widget.selectionMode ? widget.selectedItems : [widget.item];
    const canDelete = widget.onDeleteNotifications != null || widget.onDeleteNotification != null;
    if (contextItems.length === 0 || (widget.onCopyNotification == null && !canDelete)) {
      return;
    }
    this.setState(() => {
      this._contextMenuActive = true;
    });
    const sections: UiContextMenuSection[] = [];
    if (contextItems.length === 1) {
      sections.push(
        new UiContextMenuSection([
          new UiContextMenuItem({
            label: '复制',
            shortcut: 'Ctrl+C',
            onPressed:
              widget.onCopyNotification == null ? null : () => unawaited(this._copyNotification(contextItems[0]!)),
          }),
        ]),
      );
    }
    if (canDelete) {
      sections.push(
        new UiContextMenuSection([
          new UiContextMenuItem({
            label: '删除',
            onPressed: () => unawaited(this._confirmDeleteNotifications([...contextItems])),
          }),
        ]),
      );
    }
    unawaited(
      showUiContextMenu({ position, sections }).finally(() => {
        if (this.mounted) {
          this.setState(() => {
            this._contextMenuActive = false;
          });
        }
      }),
    );
  };

  async _copyNotification(item: RoomNotificationItem): Promise<void> {
    try {
      await this.widget.onCopyNotification!(item);
      if (!this.mounted) return;
      showFloatingSuccessNotice('已复制');
    } catch (error) {
      if (!this.mounted) return;
      showFloatingErrorNotice(userFacingErrorMessage(error, { fallback: '复制通知失败' }));
    }
  }

  async _confirmDeleteNotifications(items: RoomNotificationItem[]): Promise<void> {
    const confirmed = await showDialog<boolean>(() => <_NotificationDeleteConfirmDialog itemCount={items.length} />);
    if (confirmed !== true || !this.mounted) return;
    try {
      const deleteNotifications = this.widget.onDeleteNotifications;
      if (deleteNotifications != null) {
        await deleteNotifications(items);
      } else {
        for (const item of items) {
          await this.widget.onDeleteNotification!(item);
        }
      }
      if (!this.mounted) return;
      this.widget.onSelectionDeleted?.();
      showFloatingSuccessNotice(items.length === 1 ? '已删除' : `已删除 ${items.length} 条通知`);
    } catch (error) {
      if (!this.mounted) return;
      showFloatingErrorNotice(userFacingErrorMessage(error, { fallback: '删除通知失败' }));
    }
  }
}

function _NotificationContextMenuRegion(props: _NotificationContextMenuRegionProps) {
  const state = useViewState(() => new _NotificationContextMenuRegionState(), props);
  return <>{state.build()}</>;
}

/**
 * Keeps interactive notification controls from also toggling batch selection.
 *
 * The row-level selection tap is a click handler on the context-menu region;
 * this wrapper claims the click before it bubbles there while the control
 * itself still receives every pointer event.
 */
function _NotificationInteractiveTarget({ children }: { children?: ReactNode }) {
  return (
    <div style={{ display: 'contents' }} onClick={(event) => event.stopPropagation()}>
      {children}
    </div>
  );
}

function _NotificationDeleteConfirmDialog({ itemCount }: { itemCount: number }) {
  const route = useRoute<boolean>();
  return (
    <DialogFrame
      title={itemCount === 1 ? '删除通知' : '删除选中的通知'}
      icon={Icons.warning_amber_outlined}
      adaptiveActions={[
        new ResponsiveDialogAction({ label: '取消', onPressed: () => route.close(false) }),
        new ResponsiveDialogAction({ label: '删除', tone: ButtonTone.danger, onPressed: () => route.close(true) }),
      ]}
    >
      <Text style={UiTypography.body}>{itemCount === 1 ? '删除后将无法恢复' : `确定删除所有选中的${itemCount}条通知？`}</Text>
    </DialogFrame>
  );
}

// ---------------------------------------------------------------------------
// Rows

/** AnimatedContainer(duration: 90ms, curve: easeOutCubic) around each row. */
function _rowSurfaceStyle(opts: { contextMenuActive: boolean; borderColor: string }): CSSProperties {
  const transition = `90ms ${UiCurves.easeOutCubic}`;
  return {
    boxSizing: 'border-box',
    backgroundColor: opts.contextMenuActive ? UiColors.selected : UiColors.surface,
    borderRadius: UiRadii.lg,
    border: `1px solid ${opts.borderColor}`,
    padding: '11px 12px',
    transition: `background-color ${transition}, border-color ${transition}`,
  };
}

const _nameStyle: CSSProperties = { ...UiTypography.label, color: UiColors.text, fontWeight: 600 };
const _actionTextStyle: CSSProperties = { ...UiTypography.label, color: UiColors.textSecondary, fontWeight: 600 };
const _timeStyle: CSSProperties = { ...UiTypography.label, color: UiColors.textMuted };

/** `SizedBox(width: w, child: Align(alignment: Alignment.centerRight, child: …))`. */
function _TrailingSlot({ width, children }: { width: number; children?: ReactNode }) {
  return (
    <SizedBox
      width={width}
      style={{ display: 'flex', flexDirection: 'row', alignItems: 'center', justifyContent: 'flex-end', minWidth: 0 }}
    >
      {children}
    </SizedBox>
  );
}

/** The 116px timestamp column of the wide layouts. */
function _WideTime({ testId, time }: { testId: string; time: string }) {
  return (
    <SizedBox data-testid={testId} width={116}>
      <HighlightedText text={time} query="" maxLines={1} overflow="ellipsis" style={_timeStyle} />
    </SizedBox>
  );
}

interface _RoomInviteNotificationRowProps {
  invite: RoomInvite;
  query: string;
  contextMenuActive: boolean;
  busy: boolean;
  busyInviteId: string | null;
  onReviewInvite: RoomInviteReviewCallback;
  currentUser: CurrentUser;
  onOpenRoom: (room: PublicRoom) => void;
  onResolveRoomProfile: RoomProfileResolver | null;
  onResolveRoomUserProfile: RoomNotificationUserProfileResolver | null;
}

function _RoomInviteNotificationRow({
  invite,
  query,
  contextMenuActive,
  busy,
  busyInviteId,
  onReviewInvite,
  currentUser,
  onOpenRoom,
  onResolveRoomProfile,
  onResolveRoomUserProfile,
}: _RoomInviteNotificationRowProps) {
  const config = useAppConfig();
  const room = invite.room;
  const inviter = invite.inviter;
  const userExists = invite.inviterExists;
  const inviterName = roomNotificationUserLabel(inviter, { userExists });
  const inviterAvatarLabel = roomNotificationUserAvatarLabel(inviter, { userExists });
  const role = roomInviteRoleLabel(inviter);
  const time = roomInviteTimestampLabel(invite.createdAt);
  const invalid = isInvalidPendingRoomInvite(invite);
  const inviterAvatar = (
    <_NotificationUserAvatar
      testId={`notification-inviter-avatar-${invite.id}`}
      user={inviter}
      userExists={userExists}
      label={inviterAvatarLabel}
      imageUrl={config.resolveAssetUrl(roomNotificationUserAvatarUrl(inviter, { userExists }))}
      defaultAvatarKey={roomNotificationUserAvatarKey(inviter, { userExists })}
    />
  );
  const inviterAvatarTarget = (
    <_NotificationInteractiveTarget>
      <UserHoverCard
        user={inviter.copyWith({ isDeleted: !userExists })}
        currentUser={currentUser}
        onResolveProfile={onResolveRoomUserProfile == null ? null : (user) => onResolveRoomUserProfile(room.id, user)}
        onResolveRoomProfile={onResolveRoomProfile}
        onEnterCommonRoom={onOpenRoom}
      >
        {inviterAvatar}
      </UserHoverCard>
    </_NotificationInteractiveTarget>
  );
  const inviteAction = invalid ? (
    <_InvalidInviteLabel invite={invite} query={query} />
  ) : isPendingRoomInvite(invite) ? (
    <_InviteDecisionActions
      invite={invite}
      busy={busy}
      enabled={canReviewNotificationInvite({ invite, busyInviteId })}
      onReviewInvite={onReviewInvite}
    />
  ) : (
    <_ProcessedInviteLabel invite={invite} query={query} />
  );
  const roomTarget = (
    <_InlineRoomTarget
      room={room}
      inviteId={invite.id}
      query={query}
      roomExists={invite.roomExists}
      currentUser={currentUser}
      onResolveRoomProfile={onResolveRoomProfile}
      onResolveRoomUserProfile={onResolveRoomUserProfile}
      onOpenRoom={onOpenRoom}
    />
  );
  return (
    <_NotificationNewMarker show={isActionablePendingRoomInvite(invite)}>
      <div
        data-testid={`notification-row-surface-invite:${invite.id}`}
        style={_rowSurfaceStyle({
          contextMenuActive,
          borderColor: contextMenuActive
            ? UiColors.selectedBorder
            : isPendingRoomInvite(invite) && !invalid
              ? UiColors.accentBorder
              : UiColors.border,
        })}
      >
        <_ResponsiveNotificationContent
          layoutKey={`invite:${invite.id}`}
          layoutSignature={invite}
          compact={
            <_CompactNotificationRow
              time={time}
              timeKey={`notification-time-${invite.id}`}
              body={
                <_AndroidNotificationBody testId={`notification-mobile-body-${invite.id}`}>
                  {inviterAvatarTarget}
                  <HighlightedText text={inviterName} query={query} maxLines={1} overflow="ellipsis" style={_nameStyle} />
                  <_InviteRoleBadge label={role} query={query} />
                  <Text maxLines={1} style={_actionTextStyle}>
                    邀请您加入
                  </Text>
                  {roomTarget}
                </_AndroidNotificationBody>
              }
              trailing={<_TrailingSlot width={78}>{inviteAction}</_TrailingSlot>}
            />
          }
          wide={
            <_NRow>
              <_WideTime testId={`notification-time-${invite.id}`} time={time} />
              <SizedBox width={10} />
              {inviterAvatarTarget}
              <SizedBox width={8} />
              <_NFlex expanded>
                <_NRow>
                  <_NFlex flex={3}>
                    <HighlightedText text={inviterName} query={query} maxLines={1} overflow="ellipsis" style={_nameStyle} />
                  </_NFlex>
                  <SizedBox width={7} />
                  <_NFlex flex={2}>
                    <_InviteRoleBadge label={role} query={query} />
                  </_NFlex>
                  <SizedBox width={8} />
                  <_NFlex flex={2}>
                    <Text maxLines={1} style={_actionTextStyle}>
                      邀请您加入
                    </Text>
                  </_NFlex>
                  <SizedBox width={8} />
                  <_NFlex flex={4}>{roomTarget}</_NFlex>
                </_NRow>
              </_NFlex>
              <SizedBox width={12} />
              <_TrailingSlot width={88}>{inviteAction}</_TrailingSlot>
            </_NRow>
          }
        />
      </div>
    </_NotificationNewMarker>
  );
}

interface _RoomApplicationRequestNotificationRowProps {
  application: RoomApplication;
  query: string;
  contextMenuActive: boolean;
  busy: boolean;
  busyApplicationId: string | null;
  onWithdrawApplication: RoomApplicationWithdrawCallback;
  currentUser: CurrentUser;
  onOpenRoom: (room: PublicRoom) => void;
  onResolveRoomProfile: RoomProfileResolver | null;
  onResolveRoomUserProfile: RoomNotificationUserProfileResolver | null;
}

function _RoomApplicationRequestNotificationRow({
  application,
  query,
  contextMenuActive,
  busy,
  busyApplicationId,
  onWithdrawApplication,
  currentUser,
  onOpenRoom,
  onResolveRoomProfile,
  onResolveRoomUserProfile,
}: _RoomApplicationRequestNotificationRowProps) {
  const room = application.room;
  const time = roomInviteTimestampLabel(application.createdAt);
  const applicationAction = isPendingRoomApplication(application) ? (
    <_ApplicationWithdrawAction
      application={application}
      busy={busy}
      enabled={canWithdrawNotificationApplication({ application, busyApplicationId })}
      onWithdrawApplication={onWithdrawApplication}
    />
  ) : (
    <_ProcessedApplicationLabel application={application} query={query} />
  );
  const roomTarget = (
    <_InlineRoomTarget
      room={room}
      inviteId={`application-${application.id}`}
      query={query}
      roomExists
      currentUser={currentUser}
      onResolveRoomProfile={onResolveRoomProfile}
      onResolveRoomUserProfile={onResolveRoomUserProfile}
      onOpenRoom={onOpenRoom}
    />
  );
  return (
    <_NotificationNewMarker show={isPendingRoomApplication(application)}>
      <div
        data-testid={`notification-row-surface-application-requested:${application.id}`}
        style={_rowSurfaceStyle({
          contextMenuActive,
          borderColor: contextMenuActive
            ? UiColors.selectedBorder
            : isPendingRoomApplication(application)
              ? UiColors.accentBorder
              : UiColors.border,
        })}
      >
        <_ResponsiveNotificationContent
          layoutKey={`application-requested:${application.id}`}
          layoutSignature={application}
          compact={
            <_CompactNotificationRow
              time={time}
              timeKey={`notification-application-time-${application.id}`}
              body={
                <_AndroidNotificationBody testId={`notification-mobile-body-application-${application.id}`}>
                  <_NotificationLabel testId={`notification-application-request-action-${application.id}`}>
                    您已申请加入
                  </_NotificationLabel>
                  {roomTarget}
                </_AndroidNotificationBody>
              }
              trailing={<_TrailingSlot width={78}>{applicationAction}</_TrailingSlot>}
            />
          }
          wide={
            <_NRow>
              <_WideTime testId={`notification-application-time-${application.id}`} time={time} />
              <SizedBox width={10} />
              <_NotificationLabel testId={`notification-application-request-action-${application.id}`}>
                您已申请加入
              </_NotificationLabel>
              <SizedBox width={8} />
              <_NFlex expanded>{roomTarget}</_NFlex>
              <SizedBox width={12} />
              <_TrailingSlot width={88}>{applicationAction}</_TrailingSlot>
            </_NRow>
          }
        />
      </div>
    </_NotificationNewMarker>
  );
}

interface _RoomApplicationReviewNotificationRowProps {
  application: RoomApplication;
  query: string;
  contextMenuActive: boolean;
  currentUser: CurrentUser;
  onOpenRoom: (room: PublicRoom) => void;
  onResolveRoomProfile: RoomProfileResolver | null;
  onResolveRoomUserProfile: RoomNotificationUserProfileResolver | null;
}

function _RoomApplicationReviewNotificationRow({
  application,
  query,
  contextMenuActive,
  currentUser,
  onOpenRoom,
  onResolveRoomProfile,
  onResolveRoomUserProfile,
}: _RoomApplicationReviewNotificationRowProps) {
  const config = useAppConfig();
  const room = application.room;
  const reviewer = application.reviewer!;
  const userExists = application.reviewerExists;
  const reviewerName = roomNotificationUserLabel(reviewer, { userExists });
  const reviewerAvatarLabel = roomNotificationUserAvatarLabel(reviewer, { userExists });
  const role = roomInviteRoleLabel(reviewer);
  const time = roomInviteTimestampLabel(application.reviewedAt ?? application.updatedAt);
  const reviewerAvatar = (
    <_NotificationUserAvatar
      testId={`notification-application-reviewer-avatar-${application.id}`}
      user={reviewer}
      userExists={userExists}
      label={reviewerAvatarLabel}
      imageUrl={config.resolveAssetUrl(roomNotificationUserAvatarUrl(reviewer, { userExists }))}
      defaultAvatarKey={roomNotificationUserAvatarKey(reviewer, { userExists })}
    />
  );
  const reviewerAvatarTarget = (
    <_NotificationInteractiveTarget>
      <UserHoverCard
        user={reviewer.copyWith({ isDeleted: !userExists })}
        currentUser={currentUser}
        onResolveProfile={onResolveRoomUserProfile == null ? null : (user) => onResolveRoomUserProfile(room.id, user)}
        onResolveRoomProfile={onResolveRoomProfile}
        onEnterCommonRoom={onOpenRoom}
      >
        {reviewerAvatar}
      </UserHoverCard>
    </_NotificationInteractiveTarget>
  );
  const reviewAction = roomApplicationReviewActionLabel(application);
  const roomTarget = (
    <_InlineRoomTarget
      room={room}
      inviteId={`application-reviewed-${application.id}`}
      query={query}
      roomExists
      currentUser={currentUser}
      onResolveRoomProfile={onResolveRoomProfile}
      onResolveRoomUserProfile={onResolveRoomUserProfile}
      onOpenRoom={onOpenRoom}
    />
  );
  return (
    <div
      data-testid={`notification-row-surface-application-reviewed:${application.id}`}
      style={_rowSurfaceStyle({
        contextMenuActive,
        borderColor: contextMenuActive ? UiColors.selectedBorder : UiColors.border,
      })}
    >
      <_ResponsiveNotificationContent
        layoutKey={`application-reviewed:${application.id}`}
        layoutSignature={application}
        compact={
          <_CompactNotificationRow
            time={time}
            timeKey={`notification-application-review-time-${application.id}`}
            body={
              <_AndroidNotificationBody testId={`notification-mobile-body-reviewed-${application.id}`}>
                {reviewerAvatarTarget}
                <HighlightedText text={reviewerName} query={query} maxLines={1} overflow="ellipsis" style={_nameStyle} />
                <_InviteRoleBadge label={role} query={query} />
                <HighlightedText text={reviewAction} query={query} maxLines={1} overflow="ellipsis" style={_actionTextStyle} />
                {roomTarget}
              </_AndroidNotificationBody>
            }
          />
        }
        wide={
          <_NRow>
            <_WideTime testId={`notification-application-review-time-${application.id}`} time={time} />
            <SizedBox width={10} />
            {reviewerAvatarTarget}
            <SizedBox width={8} />
            <_NFlex expanded>
              <_NRow>
                <_NFlex flex={3}>
                  <HighlightedText text={reviewerName} query={query} maxLines={1} overflow="ellipsis" style={_nameStyle} />
                </_NFlex>
                <SizedBox width={7} />
                <_NFlex flex={2}>
                  <_InviteRoleBadge label={role} query={query} />
                </_NFlex>
                <SizedBox width={8} />
                <_NFlex flex={2}>
                  <HighlightedText
                    text={reviewAction}
                    query={query}
                    maxLines={1}
                    overflow="ellipsis"
                    style={_actionTextStyle}
                  />
                </_NFlex>
                <SizedBox width={8} />
                <_NFlex flex={4}>{roomTarget}</_NFlex>
              </_NRow>
            </_NFlex>
            <SizedBox width={12} />
            <SizedBox width={88} />
          </_NRow>
        }
      />
    </div>
  );
}

interface _RoomEventNotificationRowProps {
  notification: RoomEventNotification;
  query: string;
  contextMenuActive: boolean;
  currentUser: CurrentUser;
  onOpenRoom: (room: PublicRoom) => void;
  onOpenRoomEvent: RoomEventOpenCallback;
  onResolveRoomProfile: RoomProfileResolver | null;
  onResolveRoomUserProfile: RoomNotificationUserProfileResolver | null;
}

function _RoomEventNotificationRow(props: _RoomEventNotificationRowProps) {
  const { notification, contextMenuActive, onOpenRoomEvent } = props;
  const time = roomInviteTimestampLabel(notification.createdAt);
  return (
    <_NotificationNewMarker show={notification.isUnread} markerKey={`notification-room-event-new-${notification.id}`}>
      <div
        data-testid={`notification-row-surface-room-event:${notification.id}`}
        style={_rowSurfaceStyle({
          contextMenuActive,
          borderColor: contextMenuActive ? UiColors.selectedBorder : UiColors.border,
        })}
      >
        <_ResponsiveNotificationContent
          layoutKey={`room-event:${notification.id}`}
          layoutSignature={notification}
          compact={
            <_CompactNotificationRow
              time={time}
              timeKey={`notification-room-event-time-${notification.id}`}
              body={_buildRoomEventAndroidContent(props)}
              trailing={<_RoomEventJumpAction notification={notification} onOpenRoomEvent={onOpenRoomEvent} />}
            />
          }
          wide={
            <_NRow>
              <_WideTime testId={`notification-room-event-time-${notification.id}`} time={time} />
              <SizedBox width={10} />
              <_NFlex expanded>{_buildRoomEventContent(props)}</_NFlex>
              <SizedBox width={12} />
              <_TrailingSlot width={88}>
                <_RoomEventJumpAction notification={notification} onOpenRoomEvent={onOpenRoomEvent} />
              </_TrailingSlot>
            </_NRow>
          }
        />
      </div>
    </_NotificationNewMarker>
  );
}

function _roomEventActorTarget(props: _RoomEventNotificationRowProps, actor: UserSummary): ReactNode {
  const { notification } = props;
  return (
    <_InlineUserTarget
      user={actor}
      roomId={notification.room.id}
      targetId={notification.id}
      query={props.query}
      userExists={notification.actorExists}
      currentUser={props.currentUser}
      onResolveRoomProfile={props.onResolveRoomProfile}
      onResolveRoomUserProfile={props.onResolveRoomUserProfile}
      onOpenRoom={props.onOpenRoom}
    />
  );
}

function _roomEventRoomTarget(props: _RoomEventNotificationRowProps): ReactNode {
  const { notification } = props;
  return (
    <_InlineRoomTarget
      room={notification.room}
      inviteId={`room-event-${notification.id}`}
      query={props.query}
      roomExists={notification.roomExists}
      currentUser={props.currentUser}
      onResolveRoomProfile={props.onResolveRoomProfile}
      onResolveRoomUserProfile={props.onResolveRoomUserProfile}
      onOpenRoom={props.onOpenRoom}
    />
  );
}

function _roomEventTargetRoleBadge(notification: RoomEventNotification, query: string): ReactNode {
  return (
    <_InviteRoleBadge
      testId={`notification-room-event-target-role-${notification.id}`}
      label={roomNotificationRoleLabel(notification.toRole)}
      query={query}
    />
  );
}

function _messagePreviewInfoButton(notification: RoomEventNotification): ReactNode {
  const preview = notification.messagePreview?.trim() ?? null;
  if (preview == null || preview.length === 0) return null;
  return (
    <>
      <SizedBox width={4} />
      <_RoomEventInfoButton message={preview} />
    </>
  );
}

/** Port of `_RoomEventNotificationRow._buildContent` (wide layout). */
function _buildRoomEventContent(props: _RoomEventNotificationRowProps): ReactNode {
  const { notification, query } = props;
  const actor = notification.actor;
  const text = (value: string) => <_NotificationText text={value} query={query} />;
  switch (notification.type) {
    case kRoomEventNotificationMemberRemoved:
      return (
        <_NRow>
          {text('您被')}
          {actor != null ? (
            <>
              <SizedBox width={8} />
              <_NFlex flex={4}>{_roomEventActorTarget(props, actor)}</_NFlex>
              <SizedBox width={8} />
            </>
          ) : null}
          {text('踢出了')}
          <SizedBox width={8} />
          <_NFlex flex={4}>{_roomEventRoomTarget(props)}</_NFlex>
        </_NRow>
      );
    case kRoomEventNotificationRolePromoted:
    case kRoomEventNotificationRoleDemoted:
      return (
        <_NRow>
          {text('您在')}
          <SizedBox width={8} />
          <_NFlex flex={4}>{_roomEventRoomTarget(props)}</_NFlex>
          <SizedBox width={8} />
          {actor != null ? (
            <>
              {text('中被')}
              <SizedBox width={8} />
              <_NFlex flex={4}>{_roomEventActorTarget(props, actor)}</_NFlex>
              <SizedBox width={8} />
            </>
          ) : (
            <>
              {text('中')}
              <SizedBox width={8} />
            </>
          )}
          {text(roomEventNotificationRoleActionLabel(notification))}
          <SizedBox width={7} />
          <_NFlex>{_roomEventTargetRoleBadge(notification, query)}</_NFlex>
        </_NRow>
      );
    case kRoomEventNotificationCreatorTransferDemoted:
      return (
        <_NRow>
          {text('您在')}
          <SizedBox width={8} />
          <_NFlex flex={4}>{_roomEventRoomTarget(props)}</_NFlex>
          <SizedBox width={8} />
          {text('中降职为')}
          <SizedBox width={7} />
          <_NFlex>{_roomEventTargetRoleBadge(notification, query)}</_NFlex>
        </_NRow>
      );
    case kRoomEventNotificationMentioned:
      return (
        <_NRow>
          {text('您在')}
          <SizedBox width={8} />
          <_NFlex flex={4}>{_roomEventRoomTarget(props)}</_NFlex>
          <SizedBox width={8} />
          {actor != null ? (
            <>
              {text('中被')}
              <SizedBox width={8} />
              <_NFlex flex={4}>{_roomEventActorTarget(props, actor)}</_NFlex>
              <SizedBox width={8} />
              {text('提及')}
              {_messagePreviewInfoButton(notification)}
            </>
          ) : (
            <>
              {text('中被提及')}
              {_messagePreviewInfoButton(notification)}
            </>
          )}
        </_NRow>
      );
    default:
      return (
        <_NRow>
          {text('您在')}
          <SizedBox width={8} />
          <_NFlex flex={4}>{_roomEventRoomTarget(props)}</_NFlex>
          <SizedBox width={8} />
          {text('收到了房间通知')}
        </_NRow>
      );
  }
}

/** Port of `_RoomEventNotificationRow._buildAndroidContent` (compact layout). */
function _buildRoomEventAndroidContent(props: _RoomEventNotificationRowProps): ReactNode {
  const { notification, query } = props;
  const actor = notification.actor;
  const bodyKey = `notification-mobile-body-room-event-${notification.id}`;
  const text = (value: string) => <_NotificationText text={value} query={query} />;
  switch (notification.type) {
    case kRoomEventNotificationMemberRemoved:
      return (
        <_AndroidNotificationBody testId={bodyKey}>
          {text('您被')}
          {actor != null ? _roomEventActorTarget(props, actor) : null}
          {text('踢出了')}
          {_roomEventRoomTarget(props)}
        </_AndroidNotificationBody>
      );
    case kRoomEventNotificationRolePromoted:
    case kRoomEventNotificationRoleDemoted:
      return (
        <_AndroidNotificationBody testId={bodyKey}>
          {text('您在')}
          {_roomEventRoomTarget(props)}
          {text(actor == null ? '中' : '中被')}
          {actor != null ? _roomEventActorTarget(props, actor) : null}
          {text(roomEventNotificationRoleActionLabel(notification))}
          {_roomEventTargetRoleBadge(notification, query)}
        </_AndroidNotificationBody>
      );
    case kRoomEventNotificationCreatorTransferDemoted:
      return (
        <_AndroidNotificationBody testId={bodyKey}>
          {text('您在')}
          {_roomEventRoomTarget(props)}
          {text('中降职为')}
          {_roomEventTargetRoleBadge(notification, query)}
        </_AndroidNotificationBody>
      );
    case kRoomEventNotificationMentioned: {
      const preview = notification.messagePreview?.trim() ?? null;
      return (
        <_AndroidNotificationBody testId={bodyKey}>
          {text('您在')}
          {_roomEventRoomTarget(props)}
          {text(actor == null ? '中被提及' : '中被')}
          {actor != null ? (
            <>
              {_roomEventActorTarget(props, actor)}
              {text('提及')}
            </>
          ) : null}
          {preview != null && preview.length > 0 ? <_RoomEventInfoButton message={preview} /> : null}
        </_AndroidNotificationBody>
      );
    }
    default:
      return (
        <_AndroidNotificationBody testId={bodyKey}>
          {text('您在')}
          {_roomEventRoomTarget(props)}
          {text('收到了房间通知')}
        </_AndroidNotificationBody>
      );
  }
}

function _RoomEventJumpAction({
  notification,
  onOpenRoomEvent,
}: {
  notification: RoomEventNotification;
  onOpenRoomEvent: RoomEventOpenCallback;
}) {
  const enabled = notification.roomExists && notification.room.joined;
  return (
    <_NotificationInteractiveTarget>
      <ButtonIcon
        tooltip={notification.messageId == null ? '进入房间' : '跳转到消息'}
        icon={<Icon icon={Icons.location_on_rounded} />}
        tone={ButtonTone.primary}
        onPressed={enabled ? () => onOpenRoomEvent(notification) : null}
        size={34}
      />
    </_NotificationInteractiveTarget>
  );
}

function _RoomEventInfoButton({ message }: { message: string }) {
  return (
    <_NotificationInteractiveTarget>
      <HoverCardAnchor
        resetKey={message}
        cardWidth={hoverInfoCardWidth(message)}
        gap={8}
        cardBuilder={() => <_RoomEventInfoCard message={message} />}
      >
        <SizedBox
          width={16}
          height={16}
          style={{ cursor: SystemMouseCursors.click, display: 'flex', alignItems: 'center', justifyContent: 'center' }}
        >
          <Icon icon={Icons.info_outline} size={14} color={UiColors.accent} />
        </SizedBox>
      </HoverCardAnchor>
    </_NotificationInteractiveTarget>
  );
}

function _RoomEventInfoCard({ message }: { message: string }) {
  const hoverScope = HoverCardTapRegionScope.maybeOf();
  return (
    <div style={{ padding: '10px 12px', boxSizing: 'border-box' }}>
      <ReadOnlySelectableText
        value={message}
        maxLines={8}
        style={{ ...UiTypography.body, color: UiColors.text, fontSize: 12, lineHeight: 1.38 }}
        contextMenuTapRegionGroupId={hoverScope?.tapRegionGroup}
        onContextMenuOpenChanged={hoverScope?.onOverlayActivityChanged ?? null}
      />
    </div>
  );
}

// ---------------------------------------------------------------------------
// Avatars

/** Port of `_notificationAvatarSize(context)` (a hook). */
function _useNotificationAvatarSize(): number {
  return _NotificationLayoutMode.usesCompactLayout() ? CompactActivityLayout.avatarSize : 34;
}

let _authBrandIconUrlCache: string | null = null;

/** `assets/branding/auth_brand_icon.png`, bundled by Vite. */
function _authBrandIconUrl(): string {
  _authBrandIconUrlCache ??= new URL('../../../../assets/branding/auth_brand_icon.png', import.meta.url).href;
  return _authBrandIconUrlCache;
}

function _NotificationUserAvatar({
  testId,
  user,
  userExists,
  label,
  imageUrl,
  defaultAvatarKey,
}: {
  testId: string;
  user: UserSummary;
  userExists: boolean;
  label: string;
  imageUrl: string | null;
  defaultAvatarKey: string;
}) {
  const size = _useNotificationAvatarSize();
  const role = user.roomRole?.trim().toLowerCase() ?? null;
  const useBundledSuperuserBrand =
    (HostPlatform.isAndroid || HostPlatform.isWindows) &&
    userExists &&
    imageUrl == null &&
    (user.isSuperuser || role === 'superuser');
  if (useBundledSuperuserBrand) {
    return (
      <div
        data-testid={testId}
        style={{ width: size, height: size, flexShrink: 0, borderRadius: '50%', overflow: 'hidden' }}
      >
        <img
          src={_authBrandIconUrl()}
          alt=""
          width={size}
          height={size}
          draggable={false}
          style={{ display: 'block', width: size, height: size, objectFit: 'cover' }}
        />
      </div>
    );
  }
  return (
    <div data-testid={testId} style={{ display: 'contents' }}>
      <Avatar
        label={label}
        imageUrl={imageUrl}
        defaultAvatarKey={defaultAvatarKey}
        size={size}
        showFallbackText={userExists}
      />
    </div>
  );
}

// ---------------------------------------------------------------------------
// Synchronized wide / compact layout

const _NotificationLayoutModeContext = createContext<boolean | null>(null);

function _NotificationLayoutModeImpl({ compact, children }: { compact: boolean; children?: ReactNode }) {
  return <_NotificationLayoutModeContext.Provider value={compact}>{children}</_NotificationLayoutModeContext.Provider>;
}

/** Port of `_NotificationLayoutMode.usesCompactLayout(context)` (a hook). */
function _notificationUsesCompactLayout(): boolean {
  const mode = useContext(_NotificationLayoutModeContext);
  const homeCompact = HomeAdaptiveLayout.usesCompactLayout();
  return mode ?? homeCompact;
}

const _NotificationLayoutMode = Object.assign(_NotificationLayoutModeImpl, {
  usesCompactLayout: _notificationUsesCompactLayout,
});

class _NotificationLayoutMeasurement {
  constructor(opts: { requiredWidth: number; availableWidth: number }) {
    this.requiredWidth = opts.requiredWidth;
    this.availableWidth = opts.availableWidth;
  }

  readonly requiredWidth: number;
  readonly availableWidth: number;

  get requiresCompactLayout(): boolean {
    return this.requiredWidth > this.availableWidth;
  }
}

type _NotificationLayoutMeasurementCallback = (itemKey: string, requiredWidth: number, availableWidth: number) => void;

interface _NotificationLayoutGroupData {
  readonly compact: boolean;
  readonly onMeasured: _NotificationLayoutMeasurementCallback;
}

const _NotificationLayoutGroupContext = createContext<_NotificationLayoutGroupData | null>(null);

/**
 * Switches every row to the compact layout as soon as one of them no longer
 * fits its wide layout, so the list never mixes the two.
 */
function _SynchronizedNotificationLayout({ itemKeys, children }: { itemKeys: ReadonlySet<string>; children?: ReactNode }) {
  const measurements = useRef(new Map<string, _NotificationLayoutMeasurement>()).current;
  const itemKeysRef = useRef(itemKeys);
  itemKeysRef.current = itemKeys;
  const mounted = useMountedRef();
  const [, force] = useReducer((x: number) => x + 1, 0);

  const requiresCompactLayout = (): boolean => {
    for (const [itemKey, measurement] of measurements) {
      if (itemKeysRef.current.has(itemKey) && measurement.requiresCompactLayout) return true;
    }
    return false;
  };

  // didUpdateWidget: forget rows that left the list.
  for (const itemKey of Array.from(measurements.keys())) {
    if (!itemKeys.has(itemKey)) measurements.delete(itemKey);
  }
  const compact = requiresCompactLayout();
  const compactRef = useRef(compact);
  compactRef.current = compact;

  const onMeasured = useRef<_NotificationLayoutMeasurementCallback>((itemKey, requiredWidth, availableWidth) => {
    if (
      !mounted.current ||
      !itemKeysRef.current.has(itemKey) ||
      !Number.isFinite(requiredWidth) ||
      !Number.isFinite(availableWidth)
    ) {
      return;
    }
    const previous = measurements.get(itemKey);
    if (previous?.requiredWidth === requiredWidth && previous?.availableWidth === availableWidth) {
      return;
    }
    measurements.set(itemKey, new _NotificationLayoutMeasurement({ requiredWidth, availableWidth }));
    const nextCompact = requiresCompactLayout();
    if (nextCompact === compactRef.current) return;
    force();
  }).current;

  const value = useMemo<_NotificationLayoutGroupData>(() => ({ compact, onMeasured }), [compact, onMeasured]);
  return <_NotificationLayoutGroupContext.Provider value={value}>{children}</_NotificationLayoutGroupContext.Provider>;
}

/**
 * True while a row lays its wide layout out at its natural size to measure
 * it (`_NotificationMeasureLayout`'s intrinsic-width pass).
 */
const _NotificationMeasureContext = createContext(false);

/**
 * A `Row` of a wide notification layout. The `data-nrow` marker lets the
 * measuring pass compute Flutter's max intrinsic width for it.
 */
function _NRow({ mainAxisSize, children }: { mainAxisSize?: 'min' | 'max'; children?: ReactNode }) {
  return (
    <Row data-nrow="" mainAxisSize={mainAxisSize}>
      {children}
    </Row>
  );
}

/**
 * `Flexible(flex:)` / `Expanded(flex:)` inside an [_NRow]. While measuring it
 * keeps its child's natural width so the intrinsic pass can read it.
 */
function _NFlex({ flex = 1, expanded = false, children }: { flex?: number; expanded?: boolean; children?: ReactNode }) {
  const measuring = useContext(_NotificationMeasureContext);
  if (measuring) {
    return (
      <div data-nflex={flex} style={{ flex: '0 0 auto', minWidth: 0 }}>
        {children}
      </div>
    );
  }
  return expanded ? (
    <Expanded data-nflex={flex} flex={flex}>
      {children}
    </Expanded>
  ) : (
    <Flexible data-nflex={flex} flex={flex}>
      {children}
    </Flexible>
  );
}

/** Width of [element]'s box; `display: contents` wrappers report their children. */
function _boxWidth(element: Element): number {
  if (element instanceof HTMLElement && getComputedStyle(element).display === 'contents') {
    let left = Infinity;
    let right = -Infinity;
    const visit = (parent: Element) => {
      for (const child of Array.from(parent.children)) {
        if (child instanceof HTMLElement && getComputedStyle(child).display === 'contents') {
          visit(child);
          continue;
        }
        const rect = child.getBoundingClientRect();
        if (rect.width === 0 && rect.height === 0) continue;
        left = Math.min(left, rect.left);
        right = Math.max(right, rect.right);
      }
    };
    visit(element);
    return right > left ? right - left : 0;
  }
  return element.getBoundingClientRect().width;
}

/**
 * Flutter's `RenderFlex` max intrinsic width for a measured wide layout:
 * inflexible children add their natural width, flexible children need
 * `maxFlexFraction * totalFlex` so each one gets at least its natural width
 * from its proportional share.
 */
function _flutterMaxIntrinsicWidth(element: Element): number {
  if (element.hasAttribute('data-nrow')) {
    let totalFlex = 0;
    let inflexibleSpace = 0;
    let maxFlexFractionSoFar = 0;
    for (const child of Array.from(element.children)) {
      const flex = Number(child.getAttribute('data-nflex') ?? '0') || 0;
      const childWidth = _flutterMaxIntrinsicWidth(child);
      if (flex > 0) {
        totalFlex += flex;
        maxFlexFractionSoFar = Math.max(maxFlexFractionSoFar, childWidth / flex);
      } else {
        inflexibleSpace += childWidth;
      }
    }
    return maxFlexFractionSoFar * totalFlex + inflexibleSpace;
  }
  if (element.hasAttribute('data-nflex') && element.children.length === 1 && element.children[0]!.hasAttribute('data-nrow')) {
    return _flutterMaxIntrinsicWidth(element.children[0]!);
  }
  return _boxWidth(element);
}

/** Fractional content width of [ref] (LayoutBuilder's `constraints.maxWidth`). */
function _useAvailableWidth(ref: RefObject<HTMLElement | null>): number | null {
  const [width, setWidth] = useState<number | null>(null);
  useLayoutEffect(() => {
    const node = ref.current;
    if (node == null) return;
    const update = () => {
      const next = node.getBoundingClientRect().width;
      setWidth((previous) => (previous != null && Math.abs(previous - next) < 0.01 ? previous : next));
    };
    update();
    if (typeof ResizeObserver === 'undefined') return;
    const observer = new ResizeObserver(update);
    observer.observe(node);
    return () => observer.disconnect();
  }, [ref]);
  return width;
}

function _ResponsiveNotificationContent({
  layoutKey,
  layoutSignature,
  compact,
  wide,
}: {
  layoutKey: string;
  layoutSignature: unknown;
  compact: ReactNode;
  wide: ReactNode;
}) {
  const homeCompact = HomeAdaptiveLayout.usesCompactLayout();
  const group = useContext(_NotificationLayoutGroupContext);
  const outerRef = useRef<HTMLDivElement | null>(null);
  const measureRef = useRef<HTMLDivElement | null>(null);
  const availableWidth = _useAvailableWidth(outerRef);
  const [measured, setMeasured] = useState<{ layoutKey: string; layoutSignature: unknown; width: number } | null>(null);
  const lastReported = useRef<{ layoutKey: string; layoutSignature: unknown; requiredWidth: number; availableWidth: number } | null>(
    null,
  );
  // didUpdateWidget: a new key or signature drops the cached measurement.
  const requiredWideWidth =
    measured != null && measured.layoutKey === layoutKey && measured.layoutSignature === layoutSignature
      ? measured.width
      : null;
  const measuring = !homeCompact && requiredWideWidth == null;

  useLayoutEffect(() => {
    if (!measuring) return;
    const root = measureRef.current?.firstElementChild ?? null;
    if (root == null) return;
    const width = Math.ceil(_flutterMaxIntrinsicWidth(root));
    setMeasured({ layoutKey, layoutSignature, width });
  });

  // _reportMeasurement: the group hears about the row after the frame.
  useEffect(() => {
    if (homeCompact || requiredWideWidth == null || availableWidth == null || group == null) return;
    if (!Number.isFinite(requiredWideWidth) || !Number.isFinite(availableWidth)) return;
    const last = lastReported.current;
    if (
      last != null &&
      last.layoutKey === layoutKey &&
      last.layoutSignature === layoutSignature &&
      last.requiredWidth === requiredWideWidth &&
      last.availableWidth === availableWidth
    ) {
      return;
    }
    lastReported.current = { layoutKey, layoutSignature, requiredWidth: requiredWideWidth, availableWidth };
    group.onMeasured(layoutKey, requiredWideWidth, availableWidth);
  });

  if (homeCompact) {
    return <_NotificationLayoutMode compact>{compact}</_NotificationLayoutMode>;
  }

  const useCompactLayout =
    !measuring &&
    (group?.compact === true ||
      (availableWidth != null && Number.isFinite(availableWidth) && requiredWideWidth! > availableWidth));
  return (
    <div ref={outerRef} style={{ width: '100%', minWidth: 0 }}>
      <div ref={measureRef} style={measuring ? { width: 'max-content' } : { width: '100%', minWidth: 0 }}>
        <_NotificationMeasureContext.Provider value={measuring}>
          <_NotificationLayoutMode compact={useCompactLayout}>{useCompactLayout ? compact : wide}</_NotificationLayoutMode>
        </_NotificationMeasureContext.Provider>
      </div>
    </div>
  );
}

function _CompactNotificationRow({
  time,
  timeKey,
  body,
  trailing,
}: {
  time: string;
  timeKey: string;
  body: ReactNode;
  trailing?: ReactNode;
}) {
  return (
    <Row crossAxisAlignment="center">
      <SizedBox
        data-testid={timeKey}
        width={CompactActivityLayout.compactTimestampColumnWidth}
        style={{ display: 'flex', flexDirection: 'column' }}
      >
        <CompactActivityTimestamp timestamp={time} style={{ ...UiTypography.label, color: UiColors.textMuted, lineHeight: 1.28 }} />
      </SizedBox>
      <SizedBox width={CompactActivityLayout.compactTimestampContentGap} />
      <Expanded>{body}</Expanded>
      {trailing != null ? (
        <>
          <SizedBox width={8} />
          {trailing}
        </>
      ) : null}
    </Row>
  );
}

/** Width of the enclosing compact body (the Wrap's `constraints.maxWidth`). */
const _AndroidNotificationBodyWidthContext = createContext<number | null>(null);

function _AndroidNotificationBody({ testId, children }: { testId: string; children?: ReactNode }) {
  const ref = useRef<HTMLDivElement | null>(null);
  const width = _useAvailableWidth(ref);
  return (
    <div
      ref={ref}
      data-testid={testId}
      style={{
        display: 'flex',
        flexDirection: 'row',
        flexWrap: 'wrap',
        columnGap: 6,
        rowGap: 6,
        alignItems: 'center',
        minWidth: 0,
      }}
    >
      <_AndroidNotificationBodyWidthContext.Provider value={width}>{children}</_AndroidNotificationBodyWidthContext.Provider>
    </div>
  );
}

function _NotificationText({ text, query }: { text: string; query: string }) {
  // An inflexible Text in a Flutter Row keeps its natural width; inside the
  // compact Wrap it may shrink to the run width and ellipsize.
  const compact = _NotificationLayoutMode.usesCompactLayout();
  return (
    <HighlightedText
      text={text}
      query={query}
      maxLines={1}
      overflow="ellipsis"
      style={{ ..._actionTextStyle, flexShrink: compact ? 1 : 0 }}
    />
  );
}

/** A non-highlighted action label (`Text('您已申请加入', …)`). */
function _NotificationLabel({ testId, children }: { testId: string; children: string }) {
  const compact = _NotificationLayoutMode.usesCompactLayout();
  return (
    <Text data-testid={testId} maxLines={1} style={{ ..._actionTextStyle, flexShrink: compact ? 1 : 0 }}>
      {children}
    </Text>
  );
}

function _NotificationNewMarker({ show, children, markerKey }: { show: boolean; children?: ReactNode; markerKey?: string }) {
  return (
    <Stack>
      {children}
      {show ? (
        <Positioned data-testid={markerKey} top={8} right={8}>
          <BadgeDot size={8} />
        </Positioned>
      ) : null}
    </Stack>
  );
}

function _InviteRoleBadge({ testId, label, query }: { testId?: string; label: string; query: string }) {
  return (
    <div data-testid={testId} style={{ display: 'contents' }}>
      <RoleBadge
        label={label}
        query={query}
        padding={EdgeInsets.symmetric({ horizontal: 6, vertical: 3 })}
        borderRadius={UiRadii.sm}
        fontSize={11}
      />
    </div>
  );
}

interface _InlineUserTargetProps {
  user: UserSummary;
  roomId: string;
  targetId: string;
  query: string;
  userExists: boolean;
  currentUser: CurrentUser;
  onOpenRoom: (room: PublicRoom) => void;
  onResolveRoomProfile: RoomProfileResolver | null;
  onResolveRoomUserProfile: RoomNotificationUserProfileResolver | null;
}

function _InlineUserTarget({
  user,
  roomId,
  targetId,
  query,
  userExists,
  currentUser,
  onOpenRoom,
  onResolveRoomProfile,
  onResolveRoomUserProfile,
}: _InlineUserTargetProps) {
  const config = useAppConfig();
  const compact = _NotificationLayoutMode.usesCompactLayout();
  const name = roomNotificationUserLabel(user, { userExists });
  const avatarLabel = roomNotificationUserAvatarLabel(user, { userExists });
  const avatar = (
    <_NotificationUserAvatar
      testId={`notification-room-event-actor-avatar-${targetId}`}
      user={user}
      userExists={userExists}
      label={avatarLabel}
      imageUrl={config.resolveAssetUrl(roomNotificationUserAvatarUrl(user, { userExists }))}
      defaultAvatarKey={roomNotificationUserAvatarKey(user, { userExists })}
    />
  );
  const avatarTarget = (
    <_NotificationInteractiveTarget>
      <UserHoverCard
        user={user.copyWith({ isDeleted: !userExists })}
        currentUser={currentUser}
        onResolveProfile={onResolveRoomUserProfile == null ? null : (target) => onResolveRoomUserProfile(roomId, target)}
        onResolveRoomProfile={onResolveRoomProfile}
        onEnterCommonRoom={onOpenRoom}
      >
        {avatar}
      </UserHoverCard>
    </_NotificationInteractiveTarget>
  );
  const roleBadge = (
    <_InviteRoleBadge testId={`notification-room-event-actor-role-${targetId}`} label={roomInviteRoleLabel(user)} query={query} />
  );
  return (
    <_NRow mainAxisSize="min">
      {avatarTarget}
      <SizedBox width={8} />
      <_NFlex flex={3}>
        <HighlightedText text={name} query={query} maxLines={1} overflow="ellipsis" style={_nameStyle} />
      </_NFlex>
      <SizedBox width={7} />
      {compact ? <div style={{ flexShrink: 0, minWidth: 0 }}>{roleBadge}</div> : <_NFlex flex={2}>{roleBadge}</_NFlex>}
    </_NRow>
  );
}

interface _InlineRoomTargetProps {
  room: PublicRoom;
  inviteId: string;
  query: string;
  roomExists: boolean;
  currentUser: CurrentUser;
  onOpenRoom: (room: PublicRoom) => void;
  onResolveRoomProfile: RoomProfileResolver | null;
  onResolveRoomUserProfile: RoomNotificationUserProfileResolver | null;
}

function _InlineRoomTarget({
  room,
  inviteId,
  query,
  roomExists,
  currentUser,
  onOpenRoom,
  onResolveRoomProfile,
  onResolveRoomUserProfile,
}: _InlineRoomTargetProps) {
  const config = useAppConfig();
  const compact = _NotificationLayoutMode.usesCompactLayout();
  const avatarSize = _useNotificationAvatarSize();
  const bodyWidth = useContext(_AndroidNotificationBodyWidthContext);
  const roomLabel = roomNotificationRoomLabel(room, { roomExists });
  const roomAvatarLabel = roomNotificationRoomAvatarLabel(room, { roomExists });
  const avatar = (
    <div data-testid={`notification-room-avatar-${inviteId}`} style={{ display: 'contents' }}>
      <Avatar
        label={roomAvatarLabel}
        imageUrl={config.resolveAssetUrl(roomNotificationRoomAvatarUrl(room, { roomExists }))}
        defaultAvatarKey={roomNotificationRoomAvatarKey(room, { roomExists })}
        size={avatarSize}
        showFallbackText={roomExists}
      />
    </div>
  );
  const avatarTarget = (
    <_NotificationInteractiveTarget>
      {roomNotificationRoomCardEnabled({ roomExists }) ? (
        <RoomHoverCard
          room={room.copyWith({ isDeleted: !roomExists })}
          currentUser={currentUser}
          onResolveRoom={onResolveRoomProfile}
          onResolveUserProfile={onResolveRoomUserProfile == null ? null : (user) => onResolveRoomUserProfile(room.id, user)}
          onEnterRoom={room.joined ? onOpenRoom : null}
        >
          {avatar}
        </RoomHoverCard>
      ) : (
        avatar
      )}
    </_NotificationInteractiveTarget>
  );
  const roomName = (
    <HighlightedText text={roomLabel} query={query} maxLines={1} overflow="ellipsis" style={_nameStyle} />
  );
  // LayoutBuilder in the compact Wrap: too narrow for the avatar.
  if (compact && bodyWidth != null && bodyWidth < 39) {
    return <SizedBox width={bodyWidth}>{roomName}</SizedBox>;
  }
  return (
    <_NRow mainAxisSize="min">
      {avatarTarget}
      <SizedBox width={5} />
      <_NFlex>{roomName}</_NFlex>
    </_NRow>
  );
}

// ---------------------------------------------------------------------------
// Actions and status labels

function _InviteDecisionActions({
  invite,
  busy,
  enabled,
  onReviewInvite,
}: {
  invite: RoomInvite;
  busy: boolean;
  enabled: boolean;
  onReviewInvite: RoomInviteReviewCallback;
}) {
  return (
    <_NotificationInteractiveTarget>
      <Row mainAxisSize="min">
        <ButtonIcon
          tooltip="拒绝邀请"
          icon={<Icon icon={Icons.close} />}
          tone={ButtonTone.danger}
          onPressed={enabled ? () => unawaited(onReviewInvite(invite, false)) : null}
          size={34}
        />
        <SizedBox width={8} />
        <ButtonIcon
          tooltip="接受邀请"
          icon={<Icon icon={Icons.check} />}
          tone={ButtonTone.primary}
          onPressed={enabled ? () => unawaited(onReviewInvite(invite, true)) : null}
          loading={busy}
          size={34}
        />
      </Row>
    </_NotificationInteractiveTarget>
  );
}

function _ApplicationWithdrawAction({
  application,
  busy,
  enabled,
  onWithdrawApplication,
}: {
  application: RoomApplication;
  busy: boolean;
  enabled: boolean;
  onWithdrawApplication: RoomApplicationWithdrawCallback;
}) {
  return (
    <_NotificationInteractiveTarget>
      {/* The Flutter Button fills the bounded slot Align hands it. */}
      <Button
        tooltip="撤回申请"
        tone={ButtonTone.danger}
        onPressed={enabled ? () => unawaited(onWithdrawApplication(application)) : null}
        loading={busy}
        height={34}
        padding={EdgeInsets.symmetric({ horizontal: 10 })}
        width={Infinity}
      >
        撤回
      </Button>
    </_NotificationInteractiveTarget>
  );
}

const _rejectedLabelSurface = argb(0xff2e1f22);

function _StatusLabel({
  background,
  border,
  foreground,
  text,
  query,
}: {
  background: string;
  border: string;
  foreground: string;
  text: string;
  query: string;
}) {
  return (
    <div
      style={{
        boxSizing: 'border-box',
        minWidth: 0,
        maxWidth: '100%',
        backgroundColor: background,
        borderRadius: UiRadii.md,
        border: `1px solid ${border}`,
        padding: '6px 9px',
      }}
    >
      <HighlightedText
        text={text}
        query={query}
        maxLines={1}
        overflow="ellipsis"
        style={{ ...UiTypography.label, color: foreground, fontWeight: 600 }}
      />
    </div>
  );
}

function _InvalidInviteLabel({ invite, query }: { invite: RoomInvite; query: string }) {
  return (
    <_StatusLabel
      background={UiColors.surfacePressed}
      border={UiColors.border}
      foreground={UiColors.textMuted}
      text={roomInviteDecisionLabel(invite)}
      query={query}
    />
  );
}

function _ProcessedInviteLabel({ invite, query }: { invite: RoomInvite; query: string }) {
  const accepted = isAcceptedRoomInvite(invite);
  return (
    <_StatusLabel
      background={accepted ? UiColors.selected : _rejectedLabelSurface}
      border={accepted ? UiColors.accentBorder : UiColors.dangerBorder}
      foreground={accepted ? UiColors.accent : UiColors.danger}
      text={roomInviteDecisionLabel(invite)}
      query={query}
    />
  );
}

function _ProcessedApplicationLabel({ application, query }: { application: RoomApplication; query: string }) {
  const approved = isApprovedRoomApplication(application);
  const rejected = isRejectedRoomApplication(application);
  const label = roomApplicationStatusLabel(application);
  return (
    <_StatusLabel
      background={approved ? UiColors.selected : rejected ? _rejectedLabelSurface : UiColors.surfacePressed}
      border={approved ? UiColors.accentBorder : rejected ? UiColors.dangerBorder : UiColors.border}
      foreground={approved ? UiColors.accent : rejected ? UiColors.danger : UiColors.textMuted}
      text={label}
      query={query}
    />
  );
}

// ---------------------------------------------------------------------------
// Empty state

function _NotificationEmptyState({
  icon,
  title,
  subtitle = null,
  actionLabel = null,
  onAction = null,
  danger = false,
}: {
  icon: IconData;
  title: string;
  subtitle?: string | null;
  actionLabel?: string | null;
  onAction?: (() => void) | null;
  danger?: boolean;
}) {
  return (
    <div style={{ ..._bodyFillStyle, alignItems: 'center', justifyContent: 'center' }}>
      {/* Padding(22) > Column(mainAxisSize.min): the Column takes the padded
          width (its Button fills a bounded parent, like Flutter's). */}
      <div style={{ padding: 22, boxSizing: 'border-box', width: '100%' }}>
        <Column mainAxisSize="min">
          <Icon icon={icon} color={danger ? UiColors.danger : UiColors.textMuted} size={30} />
          <SizedBox height={8} />
          <Text
            textAlign="center"
            style={{ ...UiTypography.body, color: danger ? UiColors.danger : UiColors.textMuted, fontWeight: 600 }}
          >
            {title}
          </Text>
          {subtitle != null ? (
            <>
              <SizedBox height={6} />
              <Text textAlign="center" maxLines={3} style={{ ...UiTypography.label, color: UiColors.textMuted }}>
                {subtitle}
              </Text>
            </>
          ) : null}
          {actionLabel != null && onAction != null ? (
            <>
              <SizedBox height={14} />
              <Button icon={<Icon icon={Icons.refresh} />} onPressed={onAction}>
                {actionLabel}
              </Button>
            </>
          ) : null}
        </Column>
      </div>
    </div>
  );
}

function _emptyIcon(filter: RoomNotificationFilter): IconData {
  switch (filter) {
    case RoomNotificationFilter.all:
      return Icons.notifications_none;
    case RoomNotificationFilter.invites:
      return Icons.mail_outline;
    case RoomNotificationFilter.applications:
      return Icons.assignment_turned_in_outlined;
    case RoomNotificationFilter.roomNotifications:
      return Icons.meeting_room_outlined;
  }
}

function _emptyTitle(opts: { filter: RoomNotificationFilter; query: string; hasActiveDateFilter: boolean }): string {
  const { filter, query, hasActiveDateFilter } = opts;
  if (query.trim().length > 0 || hasActiveDateFilter) return '没有匹配通知';
  switch (filter) {
    case RoomNotificationFilter.all:
      return '暂无通知';
    case RoomNotificationFilter.invites:
      return '暂无邀请';
    case RoomNotificationFilter.applications:
      return '暂无申请';
    case RoomNotificationFilter.roomNotifications:
      return '暂无房间';
  }
}
