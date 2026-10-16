// Part of home_notifications (Dart `part of 'home_notifications.dart'`).

import { useRef, useState, type ReactNode, type RefObject } from 'react';

import { HostPlatform } from '@gang-chat/core/config/host_platform';

import { EdgeInsets } from '../lib/edge_insets';
import { Column, Expanded, Row, SizedBox, Text } from '../lib/layout';
import { SystemMouseCursors } from '../lib/mouse_cursors';
import { useTapOutside } from '../lib/tap_region';
import { FocusNode, TextEditingController, TextInputAction, TextInputType, TextSelection } from '../lib/text_editing';
import { useViewState, ViewState } from '../lib/view_state';
import { Button, ButtonIcon, ButtonTone } from '../ui/button';
import { DialogFrame, ResponsiveDialogAction, ResponsiveDialogActionBar, showFloatingErrorNotice } from '../ui/feedback';
import { Icon, Icons } from '../ui/icon';
import { Input } from '../ui/input';
import { useRoute } from '../ui/overlay';
import { PressableSurface } from '../ui/surface';
import { UiColors, UiRadii, UiTypography, withAlpha } from '../ui/tokens';
import { HomeAdaptiveLayout } from './adaptive_layout';

export const _notificationCalendarDaySize = 36;
export const _notificationCalendarYearsPerPage = 12;

export enum _NotificationCalendarEntryMode {
  calendar = 'calendar',
  input = 'input',
}

export interface _NotificationCalendarDialogProps {
  title: string;
  initialDate: Date;
  firstDate: Date;
  lastDate: Date;
}

interface _NotificationCalendarDialogStateProps extends _NotificationCalendarDialogProps {
  /** Navigator.pop for the dialog route. */
  close: (result?: Date) => void;
  /** `HomeAdaptiveLayout.usesCompactLayout(context)`. */
  compact: boolean;
}

/** Dart `~/`. */
function _intDiv(a: number, b: number): number {
  return Math.trunc(a / b);
}

class _NotificationCalendarDialogState extends ViewState<_NotificationCalendarDialogStateProps> {
  // Dart initialized these `late` fields in initState; the first build needs
  // them, so they are set from the initial props here.
  constructor(initial: _NotificationCalendarDialogStateProps) {
    super();
    this._selectedDate = initial.initialDate;
    this._visibleMonth = _localDate(initial.initialDate.getFullYear(), initial.initialDate.getMonth() + 1, 1);
    this._dateInputController = new TextEditingController({ text: _notificationCalendarInputDate(this._selectedDate) });
    this._dateInputFocusNode = new FocusNode();
    this.props = initial;
    this._yearPageStart = this._yearPageStartFor(this._visibleMonth.getFullYear());
  }

  _selectedDate: Date;
  _visibleMonth: Date;
  readonly _dateInputController: TextEditingController;
  readonly _dateInputFocusNode: FocusNode;
  _yearPageStart: number;
  _showingYearPicker = false;
  _entryMode: _NotificationCalendarEntryMode = _NotificationCalendarEntryMode.calendar;
  /**
   * `TapRegion(groupId: _inlineInputTapRegionGroup)`: the inline field and
   * the entry-mode toggle are one tap region, so pressing the toggle is not a
   * tap outside the field.
   */
  readonly _inlineInputRegion: RefObject<HTMLDivElement | null> = { current: null };
  readonly _entryModeToggleRegion: RefObject<HTMLDivElement | null> = { current: null };
  _lastRejectedDateInput: string | null = null;

  get _firstMonth(): Date {
    return _localDate(this.widget.firstDate.getFullYear(), this.widget.firstDate.getMonth() + 1, 1);
  }

  get _lastMonth(): Date {
    return _localDate(this.widget.lastDate.getFullYear(), this.widget.lastDate.getMonth() + 1, 1);
  }

  override initState(): void {
    this._dateInputFocusNode.addListener(this._handleDateInputFocusChanged);
  }

  override dispose(): void {
    this._dateInputFocusNode.removeListener(this._handleDateInputFocusChanged);
    this._dateInputController.dispose();
    this._dateInputFocusNode.dispose();
  }

  _yearPageStartFor(year: number): number {
    const firstYear = this.widget.firstDate.getFullYear();
    const lastPossibleStart = this.widget.lastDate.getFullYear() - _notificationCalendarYearsPerPage + 1;
    if (lastPossibleStart <= firstYear) return firstYear;
    const preferredStart = year - _intDiv(_notificationCalendarYearsPerPage, 2);
    if (preferredStart < firstYear) return firstYear;
    if (preferredStart > lastPossibleStart) return lastPossibleStart;
    return preferredStart;
  }

  _canMoveMonth(monthDelta: number): boolean {
    const target = _localDate(this._visibleMonth.getFullYear(), this._visibleMonth.getMonth() + 1 + monthDelta, 1);
    return !_monthIsBefore(target, this._firstMonth) && !_monthIsAfter(target, this._lastMonth);
  }

  _moveMonth(monthDelta: number): void {
    if (!this._canMoveMonth(monthDelta)) return;
    this.setState(() => {
      this._visibleMonth = _localDate(this._visibleMonth.getFullYear(), this._visibleMonth.getMonth() + 1 + monthDelta, 1);
      this._yearPageStart = this._yearPageStartFor(this._visibleMonth.getFullYear());
    });
  }

  get _canMovePreviousYearPage(): boolean {
    return this._yearPageStart > this.widget.firstDate.getFullYear();
  }

  get _canMoveNextYearPage(): boolean {
    return this._yearPageStart + _notificationCalendarYearsPerPage <= this.widget.lastDate.getFullYear();
  }

  _moveYearPage(direction: number): void {
    if (direction < 0 && !this._canMovePreviousYearPage) return;
    if (direction > 0 && !this._canMoveNextYearPage) return;
    this.setState(() => {
      const target = this._yearPageStart + direction * _notificationCalendarYearsPerPage;
      const firstYear = this.widget.firstDate.getFullYear();
      const lastPossibleStart = this.widget.lastDate.getFullYear() - _notificationCalendarYearsPerPage + 1;
      const upper = lastPossibleStart < firstYear ? firstYear : lastPossibleStart;
      this._yearPageStart = Math.min(Math.max(target, firstYear), upper);
    });
  }

  _toggleYearPicker = (): void => {
    this.setState(() => {
      this._showingYearPicker = !this._showingYearPicker;
      this._yearPageStart = this._yearPageStartFor(this._visibleMonth.getFullYear());
    });
  };

  _toggleEntryMode = (): void => {
    if (this._entryMode === _NotificationCalendarEntryMode.input) {
      this._commitDateInput();
      return;
    }
    this.setState(() => {
      this._entryMode = _NotificationCalendarEntryMode.input;
      this._showingYearPicker = false;
      this._lastRejectedDateInput = null;
      this._dateInputController.text = _notificationCalendarInputDate(this._selectedDate);
    });
    this._focusDateInput({ selectAll: true });
  };

  _selectYear(year: number): void {
    this.setState(() => {
      this._visibleMonth = _localDate(year, this._visibleMonth.getMonth() + 1, 1);
      this._yearPageStart = this._yearPageStartFor(year);
      this._showingYearPicker = false;
    });
  }

  _isSelectable(date: Date): boolean {
    return !(date.getTime() < this.widget.firstDate.getTime()) && !(date.getTime() > this.widget.lastDate.getTime());
  }

  _commitDateInput = (): boolean => {
    if (this._entryMode !== _NotificationCalendarEntryMode.input) return true;
    const value = this._dateInputController.text.trim();
    const date = _notificationCalendarParseInputDate(value);
    if (date == null) {
      this._showDateInputError('请输入如 2026-07-10 的有效日期');
      return false;
    }
    if (!this._isSelectable(date)) {
      this._showDateInputError(
        `日期需介于 ${_notificationCalendarInputDate(this.widget.firstDate)} 和 ${_notificationCalendarInputDate(this.widget.lastDate)} 之间`,
      );
      return false;
    }
    this._selectDate(date);
    this.setState(() => {
      this._entryMode = _NotificationCalendarEntryMode.calendar;
    });
    this._dateInputFocusNode.unfocus();
    return true;
  };

  _showDateInputError(message: string): void {
    const value = this._dateInputController.text.trim();
    if (this._lastRejectedDateInput !== value) {
      this._lastRejectedDateInput = value;
      showFloatingErrorNotice(message);
    }
    this._focusDateInput();
  }

  _focusDateInput(opts: { selectAll?: boolean } = {}): void {
    const { selectAll = false } = opts;
    // addPostFrameCallback: the inline field mounts in the frame being built.
    setTimeout(() => {
      if (!this.mounted || this._entryMode !== _NotificationCalendarEntryMode.input) {
        return;
      }
      this._dateInputFocusNode.requestFocus();
      if (selectAll) {
        this._dateInputController.selection = new TextSelection({
          baseOffset: 0,
          extentOffset: this._dateInputController.text.length,
        });
      }
    }, 0);
  }

  _handleDateInputFocusChanged = (): void => {
    if (this._dateInputFocusNode.hasFocus || this._entryMode !== _NotificationCalendarEntryMode.input) {
      return;
    }
    setTimeout(() => {
      if (!this.mounted || this._dateInputFocusNode.hasFocus || this._entryMode !== _NotificationCalendarEntryMode.input) {
        return;
      }
      this._commitDateInput();
    }, 0);
  };

  _handleDateInputTapOutside = (_event: PointerEvent): void => {
    this._commitDateInput();
  };

  _selectDate(date: Date): void {
    this.setState(() => {
      this._selectedDate = date;
      this._visibleMonth = _localDate(date.getFullYear(), date.getMonth() + 1, 1);
      this._yearPageStart = this._yearPageStartFor(date.getFullYear());
      this._dateInputController.text = _notificationCalendarInputDate(date);
      this._lastRejectedDateInput = null;
    });
  }

  _confirm = (): void => {
    if (!this._commitDateInput()) {
      return;
    }
    this.widget.close(this._selectedDate);
  };

  _handleDateInputChanged = (_value: string): void => {
    this._lastRejectedDateInput = null;
  };

  build(): ReactNode {
    const isAndroid = HostPlatform.isAndroid;
    // PORT: `MediaQuery.viewInsetsOf(context).bottom > 0` (the soft keyboard)
    // has no desktop equivalent; only the input-mode branch applies.
    const hideCalendarForAndroidInput = isAndroid && this._entryMode === _NotificationCalendarEntryMode.input;
    return (
      <DialogFrame
        title={this.widget.title}
        icon={Icons.calendar_month_outlined}
        maxWidth={420}
        actionBar={
          <ResponsiveDialogActionBar
            actions={[
              new ResponsiveDialogAction({ label: '取消', onPressed: () => this.widget.close() }),
              new ResponsiveDialogAction({
                buttonKey: 'notification-calendar-confirm-button',
                label: '确定',
                icon: Icons.check,
                tone: ButtonTone.primary,
                onPressed: this._confirm,
              }),
            ]}
          />
        }
      >
        <Column mainAxisSize="min" crossAxisAlignment="stretch">
          {this._toolbar()}
          {!hideCalendarForAndroidInput ? (
            <>
              <SizedBox height={14} />
              {this._showingYearPicker ? (
                this._yearPicker()
              ) : (
                <>
                  {this._weekdayHeader()}
                  <SizedBox height={6} />
                  {this._monthGrid()}
                </>
              )}
            </>
          ) : null}
        </Column>
      </DialogFrame>
    );
  }

  _toolbar(): ReactNode {
    return (
      <Row>
        <Expanded>{this._navigation()}</Expanded>
        <SizedBox width={6} />
        <div
          ref={this._entryModeToggleRegion}
          // Same tap-region group as the inline field: pressing the toggle
          // keeps the field focused (Flutter skipped the outside unfocus).
          onMouseDown={(event) => event.preventDefault()}
          style={{ display: 'flex', flexShrink: 0 }}
        >
          <ButtonIcon
            data-testid="notification-calendar-input-mode-toggle"
            tooltip="切换到手动输入"
            icon={<Icon icon={Icons.edit_calendar_outlined} />}
            onPressed={this._toggleEntryMode}
            selected={this._entryMode === _NotificationCalendarEntryMode.input}
            size={32}
          />
        </div>
      </Row>
    );
  }

  _navigation(): ReactNode {
    if (this._showingYearPicker) return this._yearNavigation();
    return this._monthNavigation();
  }

  _monthNavigation(): ReactNode {
    const visibleYear = this._visibleMonth.getFullYear();
    const visibleMonth = this._visibleMonth.getMonth() + 1;
    if (this.widget.compact) {
      const month = String(visibleMonth).padStart(2, '0');
      return (
        <Row>
          <ButtonIcon
            data-testid="notification-calendar-previous-month"
            tooltip="上个月"
            icon={<Icon icon={Icons.chevron_left} />}
            onPressed={this._canMoveMonth(-1) ? () => this._moveMonth(-1) : null}
            size={32}
          />
          <SizedBox width={4} />
          <Expanded>
            {this._entryMode === _NotificationCalendarEntryMode.input
              ? this._inlineInput()
              : this._navigationLabel({
                  testId: 'notification-calendar-year-picker-toggle',
                  label: `${visibleYear}/${month}`,
                  tooltip: '展开年份选择',
                  expanded: false,
                  onPressed: this._toggleYearPicker,
                })}
          </Expanded>
          <SizedBox width={4} />
          <ButtonIcon
            data-testid="notification-calendar-next-month"
            tooltip="下个月"
            icon={<Icon icon={Icons.chevron_right} />}
            onPressed={this._canMoveMonth(1) ? () => this._moveMonth(1) : null}
            size={32}
          />
        </Row>
      );
    }
    return (
      <Row>
        <ButtonIcon
          data-testid="notification-calendar-previous-year"
          tooltip="上一年"
          icon={<Icon icon={Icons.keyboard_double_arrow_left} />}
          onPressed={this._canMoveMonth(-12) ? () => this._moveMonth(-12) : null}
          size={32}
        />
        <SizedBox width={6} />
        <ButtonIcon
          data-testid="notification-calendar-previous-month"
          tooltip="上个月"
          icon={<Icon icon={Icons.chevron_left} />}
          onPressed={this._canMoveMonth(-1) ? () => this._moveMonth(-1) : null}
          size={32}
        />
        <Expanded>
          {this._entryMode === _NotificationCalendarEntryMode.input
            ? this._inlineInput()
            : this._navigationLabel({
                testId: 'notification-calendar-year-picker-toggle',
                label: `${visibleYear} 年 ${visibleMonth} 月`,
                tooltip: '展开年份选择',
                expanded: false,
                onPressed: this._toggleYearPicker,
              })}
        </Expanded>
        <ButtonIcon
          data-testid="notification-calendar-next-month"
          tooltip="下个月"
          icon={<Icon icon={Icons.chevron_right} />}
          onPressed={this._canMoveMonth(1) ? () => this._moveMonth(1) : null}
          size={32}
        />
        <SizedBox width={6} />
        <ButtonIcon
          data-testid="notification-calendar-next-year"
          tooltip="下一年"
          icon={<Icon icon={Icons.keyboard_double_arrow_right} />}
          onPressed={this._canMoveMonth(12) ? () => this._moveMonth(12) : null}
          size={32}
        />
      </Row>
    );
  }

  _yearNavigation(): ReactNode {
    const lastYear = Math.min(
      Math.max(this._yearPageStart + _notificationCalendarYearsPerPage - 1, this.widget.firstDate.getFullYear()),
      this.widget.lastDate.getFullYear(),
    );
    return (
      <Row>
        <ButtonIcon
          data-testid="notification-calendar-previous-year-page"
          tooltip="上一组年份"
          icon={<Icon icon={Icons.keyboard_double_arrow_left} />}
          onPressed={this._canMovePreviousYearPage ? () => this._moveYearPage(-1) : null}
          size={32}
        />
        <Expanded>
          {this._entryMode === _NotificationCalendarEntryMode.input
            ? this._inlineInput()
            : this._navigationLabel({
                testId: 'notification-calendar-year-picker-close',
                label: `${this._yearPageStart}年 - ${lastYear}年`,
                tooltip: '返回月份选择',
                expanded: true,
                onPressed: this._toggleYearPicker,
              })}
        </Expanded>
        <ButtonIcon
          data-testid="notification-calendar-next-year-page"
          tooltip="下一组年份"
          icon={<Icon icon={Icons.keyboard_double_arrow_right} />}
          onPressed={this._canMoveNextYearPage ? () => this._moveYearPage(1) : null}
          size={32}
        />
      </Row>
    );
  }

  _navigationLabel(opts: {
    testId: string;
    label: string;
    tooltip: string;
    expanded: boolean;
    onPressed: () => void;
  }): ReactNode {
    const { testId, label, tooltip, expanded, onPressed } = opts;
    return (
      <PressableSurface
        data-testid={testId}
        height={32}
        onPressed={onPressed}
        tooltip={tooltip}
        padding={EdgeInsets.symmetric({ horizontal: 8 })}
        backgroundColor={UiColors.transparent}
        pressedBackgroundColor={UiColors.surfaceRaised}
        borderColor={UiColors.transparent}
        selectedBorderColor={UiColors.transparent}
      >
        <Row mainAxisAlignment="center">
          <Text data-testid="notification-calendar-month-label" softWrap={false} style={{ ...UiTypography.body, fontWeight: 600 }}>
            {label}
          </Text>
          <SizedBox width={3} />
          <Icon icon={expanded ? Icons.keyboard_arrow_up : Icons.keyboard_arrow_down} color={UiColors.textMuted} size={18} />
        </Row>
      </PressableSurface>
    );
  }

  _inlineInput(): ReactNode {
    return (
      <div ref={this._inlineInputRegion} data-testid="notification-calendar-inline-date-input" style={{ minWidth: 0 }}>
        <Input
          controller={this._dateInputController}
          focusNode={this._dateInputFocusNode}
          hintText="例如 2026-07-10"
          keyboardType={TextInputType.datetime}
          textInputAction={TextInputAction.done}
          minLines={1}
          maxLines={1}
          onChanged={this._handleDateInputChanged}
          onSubmitted={() => this._commitDateInput()}
          // The tap-outside handling lives in [_NotificationCalendarDialog],
          // which treats the toggle as part of this field's tap region.
          height={32}
          textAlign="center"
          style={{ ...UiTypography.body, fontWeight: 600 }}
          hintStyle={{ ...UiTypography.label, color: UiColors.textMuted }}
        />
      </div>
    );
  }

  _weekdayHeader(): ReactNode {
    return (
      <Row data-testid="notification-calendar-weekday-header">
        {['一', '二', '三', '四', '五', '六', '日'].map((label) => (
          <_NotificationCalendarWeekday key={label} label={label} />
        ))}
      </Row>
    );
  }

  _monthGrid(): ReactNode {
    const year = this._visibleMonth.getFullYear();
    const month = this._visibleMonth.getMonth() + 1;
    const firstOfMonth = _localDate(year, month, 1);
    // DateTime.weekday - DateTime.monday (Monday = 0 … Sunday = 6).
    const leadingEmptyDays = (firstOfMonth.getDay() + 6) % 7;
    const daysInMonth = _localDate(year, month + 1, 0).getDate();
    const weekCount = _intDiv(leadingEmptyDays + daysInMonth + 6, 7);
    const weeks: ReactNode[] = [];
    for (let week = 0; week < weekCount; week += 1) {
      const cells: ReactNode[] = [];
      for (let weekday = 0; weekday < 7; weekday += 1) {
        cells.push(
          <Expanded key={weekday} style={{ alignItems: 'center', justifyContent: 'center' }}>
            {this._dayForCell({ cellIndex: week * 7 + weekday, leadingEmptyDays, daysInMonth })}
          </Expanded>,
        );
      }
      weeks.push(
        <div
          key={week}
          data-testid={`notification-calendar-week-${week}`}
          style={{ paddingBottom: week === weekCount - 1 ? 0 : 4 }}
        >
          <Row>{cells}</Row>
        </div>,
      );
    }
    return (
      <Column data-testid="notification-calendar-month-grid" mainAxisSize="min" crossAxisAlignment="stretch">
        {weeks}
      </Column>
    );
  }

  _dayForCell(opts: { cellIndex: number; leadingEmptyDays: number; daysInMonth: number }): ReactNode {
    const { cellIndex, leadingEmptyDays, daysInMonth } = opts;
    const day = cellIndex - leadingEmptyDays + 1;
    if (day < 1 || day > daysInMonth) {
      return <SizedBox width={_notificationCalendarDaySize} height={_notificationCalendarDaySize} />;
    }
    const date = _localDate(this._visibleMonth.getFullYear(), this._visibleMonth.getMonth() + 1, day);
    return (
      <SizedBox width={_notificationCalendarDaySize} height={_notificationCalendarDaySize}>
        <_NotificationCalendarDay
          testId={`notification-date-day-${date.getFullYear()}-${date.getMonth() + 1}-${date.getDate()}`}
          date={date}
          selected={_sameDate(date, this._selectedDate)}
          today={_sameDate(date, new Date())}
          enabled={this._isSelectable(date)}
          onPressed={() => this._selectDate(date)}
        />
      </SizedBox>
    );
  }

  _yearPicker(): ReactNode {
    const lastYear = this._yearPageStart + _notificationCalendarYearsPerPage - 1;
    const visibleLastYear = lastYear > this.widget.lastDate.getFullYear() ? this.widget.lastDate.getFullYear() : lastYear;
    const yearCount = visibleLastYear - this._yearPageStart + 1;
    const rowCount = _intDiv(yearCount + 2, 3);
    const rows: ReactNode[] = [];
    for (let row = 0; row < rowCount; row += 1) {
      const columns: ReactNode[] = [];
      for (let column = 0; column < 3; column += 1) {
        columns.push(
          <Expanded key={column} style={{ paddingLeft: 4, paddingRight: 4, boxSizing: 'border-box' }}>
            {this._yearForCell(this._yearPageStart + row * 3 + column)}
          </Expanded>,
        );
      }
      rows.push(
        <div key={row} style={{ paddingBottom: row === rowCount - 1 ? 0 : 8 }}>
          <Row>{columns}</Row>
        </div>,
      );
    }
    return (
      <Column data-testid="notification-calendar-year-picker" mainAxisSize="min" crossAxisAlignment="stretch">
        {rows}
      </Column>
    );
  }

  _yearForCell(year: number): ReactNode {
    if (year < this.widget.firstDate.getFullYear() || year > this.widget.lastDate.getFullYear()) {
      return <SizedBox height={36} />;
    }
    return (
      <Button
        data-testid={`notification-calendar-year-${year}`}
        onPressed={() => this._selectYear(year)}
        selected={year === this._visibleMonth.getFullYear()}
        height={36}
        padding={EdgeInsets.zero}
      >
        {`${year}年`}
      </Button>
    );
  }
}

/** The calendar picker shown by the date-range filter's start / end fields. */
export function _NotificationCalendarDialog(props: _NotificationCalendarDialogProps) {
  const route = useRoute<Date>();
  const compact = HomeAdaptiveLayout.usesCompactLayout();
  const stateProps: _NotificationCalendarDialogStateProps = { ...props, close: route.close, compact };
  const state = useViewState(() => new _NotificationCalendarDialogState(stateProps), stateProps);
  useTapOutside(
    [state._inlineInputRegion, state._entryModeToggleRegion],
    state._handleDateInputTapOutside,
    state._entryMode === _NotificationCalendarEntryMode.input,
  );
  return <>{state.build()}</>;
}

function _NotificationCalendarWeekday({ label }: { label: string }) {
  return (
    <Expanded>
      <Text textAlign="center" style={{ ...UiTypography.label, color: UiColors.textMuted }}>
        {label}
      </Text>
    </Expanded>
  );
}

function _NotificationCalendarDay({
  testId,
  date,
  selected,
  today,
  enabled,
  onPressed,
}: {
  testId: string;
  date: Date;
  selected: boolean;
  today: boolean;
  enabled: boolean;
  onPressed: () => void;
}) {
  const [hovered, setHoveredState] = useState(false);
  const hoveredRef = useRef(false);
  const setHovered = (next: boolean) => {
    if (hoveredRef.current === next) return;
    hoveredRef.current = next;
    setHoveredState(next);
  };
  const foreground = !enabled ? withAlpha(UiColors.textMuted, 0.45) : selected ? UiColors.accent : UiColors.textSecondary;
  const borderColor = selected ? UiColors.selectedBorder : today ? UiColors.accentBorder : UiColors.transparent;
  return (
    <div
      role="button"
      data-testid={testId}
      aria-disabled={!enabled}
      aria-selected={selected}
      aria-label={`${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日`}
      onMouseEnter={enabled ? () => setHovered(true) : undefined}
      onMouseLeave={enabled ? () => setHovered(false) : undefined}
      onClick={
        enabled
          ? (event) => {
              if (event.button !== 0) return;
              onPressed();
            }
          : undefined
      }
      style={{
        width: '100%',
        height: '100%',
        boxSizing: 'border-box',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        cursor: enabled ? SystemMouseCursors.click : SystemMouseCursors.basic,
        backgroundColor: selected ? UiColors.selected : hovered ? UiColors.surfaceRaised : UiColors.transparent,
        borderRadius: UiRadii.md,
        border: `1px solid ${borderColor}`,
        transition: 'background-color 80ms linear, border-color 80ms linear',
      }}
    >
      <Text softWrap={false} style={{ ...UiTypography.body, color: foreground, fontWeight: selected ? 700 : 500 }}>
        {`${date.getDate()}`}
      </Text>
    </div>
  );
}

/**
 * Local calendar date `DateTime(year, month, day)` (1-based [month]; out of
 * range months / days roll over like Dart). Years below 100 stay literal,
 * unlike the two-digit-year rule of the `Date` constructor.
 */
export function _localDate(year: number, month: number, day: number): Date {
  const date = new Date(2000, 0, 1);
  date.setFullYear(year, month - 1, day);
  date.setHours(0, 0, 0, 0);
  return date;
}

export function _notificationCalendarParseInputDate(value: string): Date | null {
  const match = /^(\d{4})[-/](\d{1,2})[-/](\d{1,2})$/.exec(value);
  if (match == null) return null;
  const year = Number.parseInt(match[1]!, 10);
  const month = Number.parseInt(match[2]!, 10);
  const day = Number.parseInt(match[3]!, 10);
  if (Number.isNaN(year) || Number.isNaN(month) || Number.isNaN(day)) return null;
  const date = _localDate(year, month, day);
  return date.getFullYear() === year && date.getMonth() + 1 === month && date.getDate() === day ? date : null;
}

export function _notificationCalendarInputDate(date: Date): string {
  return `${String(date.getFullYear()).padStart(4, '0')}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

export function _monthIsBefore(left: Date, right: Date): boolean {
  return (
    left.getFullYear() < right.getFullYear() ||
    (left.getFullYear() === right.getFullYear() && left.getMonth() < right.getMonth())
  );
}

export function _monthIsAfter(left: Date, right: Date): boolean {
  return (
    left.getFullYear() > right.getFullYear() ||
    (left.getFullYear() === right.getFullYear() && left.getMonth() > right.getMonth())
  );
}

export function _sameDate(left: Date, right: Date): boolean {
  return (
    left.getFullYear() === right.getFullYear() && left.getMonth() === right.getMonth() && left.getDate() === right.getDate()
  );
}
