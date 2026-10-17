package com.gangchat.client.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.filled.KeyboardDoubleArrowRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gangchat.client.foundation.DateTime
import com.gangchat.client.ui.Button
import com.gangchat.client.ui.ButtonIcon
import com.gangchat.client.ui.ButtonTone
import com.gangchat.client.ui.DialogFrame
import com.gangchat.client.ui.Input
import com.gangchat.client.ui.PressableSurface
import com.gangchat.client.ui.ResponsiveDialogAction
import com.gangchat.client.ui.ResponsiveDialogActionBar
import com.gangchat.client.ui.UiColors
import com.gangchat.client.ui.UiRadii
import com.gangchat.client.ui.UiTypography
import com.gangchat.client.ui.copyWith
import com.gangchat.client.ui.showFloatingErrorNotice
import com.gangchat.client.widgets.EdgeInsets
import com.gangchat.client.widgets.FocusNode
import com.gangchat.client.widgets.Icon
import com.gangchat.client.widgets.MediaQuery
import com.gangchat.client.widgets.Navigator
import com.gangchat.client.widgets.PointerDownEvent
import com.gangchat.client.widgets.StatefulWidget
import com.gangchat.client.widgets.TapRegion
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.TextEditingController
import com.gangchat.client.widgets.ViewState
import com.gangchat.client.widgets.decoration
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private val _notificationCalendarDaySize = 36.dp
private const val _notificationCalendarYearsPerPage = 12

private enum class _NotificationCalendarEntryMode { calendar, input }

// Test tags of the calendar (Dart's `ValueKey`s).
const val notificationCalendarConfirmButtonTag = "notification-calendar-confirm-button"
const val notificationCalendarInputModeToggleTag = "notification-calendar-input-mode-toggle"
const val notificationCalendarPreviousMonthTag = "notification-calendar-previous-month"
const val notificationCalendarNextMonthTag = "notification-calendar-next-month"
const val notificationCalendarPreviousYearTag = "notification-calendar-previous-year"
const val notificationCalendarNextYearTag = "notification-calendar-next-year"
const val notificationCalendarPreviousYearPageTag = "notification-calendar-previous-year-page"
const val notificationCalendarNextYearPageTag = "notification-calendar-next-year-page"
const val notificationCalendarYearPickerToggleTag = "notification-calendar-year-picker-toggle"
const val notificationCalendarYearPickerCloseTag = "notification-calendar-year-picker-close"
const val notificationCalendarMonthLabelTag = "notification-calendar-month-label"
const val notificationCalendarInlineDateInputTag = "notification-calendar-inline-date-input"
const val notificationCalendarWeekdayHeaderTag = "notification-calendar-weekday-header"
const val notificationCalendarMonthGridTag = "notification-calendar-month-grid"
const val notificationCalendarYearPickerTag = "notification-calendar-year-picker"

fun notificationCalendarWeekTag(week: Int) = "notification-calendar-week-$week"

fun notificationDateDayTag(year: Int, month: Int, day: Int) = "notification-date-day-$year-$month-$day"

fun notificationCalendarYearTag(year: Int) = "notification-calendar-year-$year"

/**
 * The single-date picker behind the date range fields: a month grid with
 * year paging, and a manual `YYYY-MM-DD` entry mode. Pops the chosen date.
 */
@Composable
internal fun _NotificationCalendarDialog(title: String, initialDate: DateTime, firstDate: DateTime, lastDate: DateTime) =
    StatefulWidget(_NotificationCalendarDialogWidget(title, initialDate, firstDate, lastDate)) { _NotificationCalendarDialogState() }

internal class _NotificationCalendarDialogWidget(
    val title: String,
    val initialDate: DateTime,
    val firstDate: DateTime,
    val lastDate: DateTime,
)

private class _NotificationCalendarDialogState : ViewState<_NotificationCalendarDialogWidget>() {
    private lateinit var _selectedDate: DateTime
    private lateinit var _visibleMonth: DateTime
    private lateinit var _dateInputController: TextEditingController
    private lateinit var _dateInputFocusNode: FocusNode
    private var _yearPageStart = 0
    private var _showingYearPicker = false
    private var _entryMode = _NotificationCalendarEntryMode.calendar
    private val _inlineInputTapRegionGroup = Any()
    private var _lastRejectedDateInput: String? = null
    private var _scope: CoroutineScope? = null

    private val _firstMonth: DateTime get() = DateTime(widget.firstDate.year, widget.firstDate.month)

    private val _lastMonth: DateTime get() = DateTime(widget.lastDate.year, widget.lastDate.month)

    private val _handleDateInputFocusChanged: () -> Unit = {
        if (!_dateInputFocusNode.hasFocus && _entryMode == _NotificationCalendarEntryMode.input) {
            _postFrame {
                if (mounted && !_dateInputFocusNode.hasFocus && _entryMode == _NotificationCalendarEntryMode.input) {
                    _commitDateInput()
                }
            }
        }
    }

    override fun initState() {
        _selectedDate = widget.initialDate
        _visibleMonth = DateTime(_selectedDate.year, _selectedDate.month)
        _dateInputController = TextEditingController(text = _notificationCalendarInputDate(_selectedDate))
        _dateInputFocusNode = FocusNode()
        _dateInputFocusNode.addListener(_handleDateInputFocusChanged)
        _yearPageStart = _yearPageStartFor(_visibleMonth.year)
    }

    override fun dispose() {
        _dateInputFocusNode.removeListener(_handleDateInputFocusChanged)
        _dateInputController.dispose()
        _dateInputFocusNode.dispose()
    }

    /** Flutter's `addPostFrameCallback`. */
    private fun _postFrame(callback: () -> Unit) {
        val scope = _scope ?: return
        scope.launch {
            withFrameNanos { }
            callback()
        }
    }

    private fun _yearPageStartFor(year: Int): Int {
        val firstYear = widget.firstDate.year
        val lastPossibleStart = widget.lastDate.year - _notificationCalendarYearsPerPage + 1
        if (lastPossibleStart <= firstYear) return firstYear
        val preferredStart = year - (_notificationCalendarYearsPerPage / 2)
        if (preferredStart < firstYear) return firstYear
        if (preferredStart > lastPossibleStart) return lastPossibleStart
        return preferredStart
    }

    private fun _canMoveMonth(monthDelta: Int): Boolean {
        val target = DateTime(_visibleMonth.year, _visibleMonth.month + monthDelta)
        return !_monthIsBefore(target, _firstMonth) && !_monthIsAfter(target, _lastMonth)
    }

    private fun _moveMonth(monthDelta: Int) {
        if (!_canMoveMonth(monthDelta)) return
        setState {
            _visibleMonth = DateTime(_visibleMonth.year, _visibleMonth.month + monthDelta)
            _yearPageStart = _yearPageStartFor(_visibleMonth.year)
        }
    }

    private val _canMovePreviousYearPage: Boolean get() = _yearPageStart > widget.firstDate.year

    private val _canMoveNextYearPage: Boolean get() = _yearPageStart + _notificationCalendarYearsPerPage <= widget.lastDate.year

    private fun _moveYearPage(direction: Int) {
        if (direction < 0 && !_canMovePreviousYearPage) return
        if (direction > 0 && !_canMoveNextYearPage) return
        setState {
            val target = _yearPageStart + direction * _notificationCalendarYearsPerPage
            val lastPossibleStart = widget.lastDate.year - _notificationCalendarYearsPerPage + 1
            _yearPageStart =
                target.coerceIn(
                    widget.firstDate.year,
                    if (lastPossibleStart < widget.firstDate.year) widget.firstDate.year else lastPossibleStart,
                )
        }
    }

    private fun _toggleYearPicker() {
        setState {
            _showingYearPicker = !_showingYearPicker
            _yearPageStart = _yearPageStartFor(_visibleMonth.year)
        }
    }

    private fun _toggleEntryMode() {
        if (_entryMode == _NotificationCalendarEntryMode.input) {
            _commitDateInput()
            return
        }
        setState {
            _entryMode = _NotificationCalendarEntryMode.input
            _showingYearPicker = false
            _lastRejectedDateInput = null
            _dateInputController.text = _notificationCalendarInputDate(_selectedDate)
        }
        _focusDateInput(selectAll = true)
    }

    private fun _selectYear(year: Int) {
        setState {
            _visibleMonth = DateTime(year, _visibleMonth.month)
            _yearPageStart = _yearPageStartFor(year)
            _showingYearPicker = false
        }
    }

    private fun _isSelectable(date: DateTime): Boolean = !date.isBefore(widget.firstDate) && !date.isAfter(widget.lastDate)

    private fun _commitDateInput(): Boolean {
        if (_entryMode != _NotificationCalendarEntryMode.input) return true
        val value = _dateInputController.text.trim()
        val date = _notificationCalendarParseInputDate(value)
        if (date == null) {
            _showDateInputError("请输入如 2026-07-10 的有效日期")
            return false
        }
        if (!_isSelectable(date)) {
            _showDateInputError(
                "日期需介于 ${_notificationCalendarInputDate(widget.firstDate)} 和 ${_notificationCalendarInputDate(widget.lastDate)} 之间",
            )
            return false
        }
        _selectDate(date)
        setState { _entryMode = _NotificationCalendarEntryMode.calendar }
        _dateInputFocusNode.unfocus()
        return true
    }

    private fun _showDateInputError(message: String) {
        val value = _dateInputController.text.trim()
        if (_lastRejectedDateInput != value) {
            _lastRejectedDateInput = value
            showFloatingErrorNotice(context, message)
        }
        _focusDateInput()
    }

    private fun _focusDateInput(selectAll: Boolean = false) {
        _postFrame {
            if (!mounted || _entryMode != _NotificationCalendarEntryMode.input) return@_postFrame
            _dateInputFocusNode.requestFocus()
            if (selectAll) _dateInputController.selection = TextRange(0, _dateInputController.text.length)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun _handleDateInputTapOutside(event: PointerDownEvent) {
        _commitDateInput()
    }

    private fun _selectDate(date: DateTime) {
        setState {
            _selectedDate = date
            _visibleMonth = DateTime(date.year, date.month)
            _yearPageStart = _yearPageStartFor(date.year)
            _dateInputController.text = _notificationCalendarInputDate(date)
            _lastRejectedDateInput = null
        }
    }

    private fun _confirm() {
        if (!_commitDateInput()) return
        Navigator.of(context).pop(_selectedDate)
    }

    @Suppress("UNUSED_PARAMETER")
    private fun _handleDateInputChanged(value: String) {
        _lastRejectedDateInput = null
    }

    @Composable
    override fun build() {
        _scope = rememberCoroutineScope()
        // The keyboard-hiding rule is Android's (Dart checks the platform).
        val hideCalendarForAndroidInput =
            _entryMode == _NotificationCalendarEntryMode.input || MediaQuery.viewInsetsOf(context).bottom > 0.dp
        DialogFrame(
            title = widget.title,
            icon = Icons.Outlined.CalendarMonth,
            maxWidth = 420.dp,
            actionBar = {
                ResponsiveDialogActionBar(
                    actions =
                        listOf(
                            ResponsiveDialogAction(label = "取消", onPressed = { Navigator.of(context).pop() }),
                            ResponsiveDialogAction(
                                buttonKey = notificationCalendarConfirmButtonTag,
                                label = "确定",
                                icon = Icons.Filled.Check,
                                tone = ButtonTone.primary,
                                onPressed = ::_confirm,
                            ),
                        ),
                )
            },
            child = {
                Column(Modifier.fillMaxWidth()) {
                    _toolbar()
                    if (!hideCalendarForAndroidInput) {
                        Spacer(Modifier.height(14.dp))
                        if (_showingYearPicker) {
                            _yearPicker()
                        } else {
                            _weekdayHeader()
                            Spacer(Modifier.height(6.dp))
                            _monthGrid()
                        }
                    }
                }
            },
        )
    }

    @Composable
    private fun _toolbar() {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { _navigation() }
            Spacer(Modifier.width(6.dp))
            TapRegion(groupId = _inlineInputTapRegionGroup) {
                ButtonIcon(
                    modifier = Modifier.testTag(notificationCalendarInputModeToggleTag),
                    tooltip = "切换到手动输入",
                    icon = { Icon(Icons.Outlined.EditCalendar) },
                    onPressed = ::_toggleEntryMode,
                    selected = _entryMode == _NotificationCalendarEntryMode.input,
                    size = 32.dp,
                )
            }
        }
    }

    @Composable
    private fun _navigation() {
        if (_showingYearPicker) _yearNavigation() else _monthNavigation()
    }

    @Composable
    private fun _calendarIconButton(tag: String, tooltip: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onPressed: (() -> Unit)?) {
        ButtonIcon(
            modifier = Modifier.testTag(tag),
            tooltip = tooltip,
            icon = { Icon(icon) },
            onPressed = onPressed,
            size = 32.dp,
        )
    }

    @Composable
    private fun _monthNavigation() {
        if (HomeAdaptiveLayout.usesCompactLayout(context)) {
            val month = _visibleMonth.month.toString().padStart(2, '0')
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                _calendarIconButton(
                    notificationCalendarPreviousMonthTag,
                    "上个月",
                    Icons.Filled.ChevronLeft,
                    if (_canMoveMonth(-1)) ({ _moveMonth(-1) }) else null,
                )
                Spacer(Modifier.width(4.dp))
                Box(Modifier.weight(1f)) {
                    if (_entryMode == _NotificationCalendarEntryMode.input) {
                        _inlineInput()
                    } else {
                        _navigationLabel(
                            tag = notificationCalendarYearPickerToggleTag,
                            label = "${_visibleMonth.year}/$month",
                            tooltip = "展开年份选择",
                            expanded = false,
                            onPressed = ::_toggleYearPicker,
                        )
                    }
                }
                Spacer(Modifier.width(4.dp))
                _calendarIconButton(
                    notificationCalendarNextMonthTag,
                    "下个月",
                    Icons.Filled.ChevronRight,
                    if (_canMoveMonth(1)) ({ _moveMonth(1) }) else null,
                )
            }
            return
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            _calendarIconButton(
                notificationCalendarPreviousYearTag,
                "上一年",
                Icons.Filled.KeyboardDoubleArrowLeft,
                if (_canMoveMonth(-12)) ({ _moveMonth(-12) }) else null,
            )
            Spacer(Modifier.width(6.dp))
            _calendarIconButton(
                notificationCalendarPreviousMonthTag,
                "上个月",
                Icons.Filled.ChevronLeft,
                if (_canMoveMonth(-1)) ({ _moveMonth(-1) }) else null,
            )
            Box(Modifier.weight(1f)) {
                if (_entryMode == _NotificationCalendarEntryMode.input) {
                    _inlineInput()
                } else {
                    _navigationLabel(
                        tag = notificationCalendarYearPickerToggleTag,
                        label = "${_visibleMonth.year} 年 ${_visibleMonth.month} 月",
                        tooltip = "展开年份选择",
                        expanded = false,
                        onPressed = ::_toggleYearPicker,
                    )
                }
            }
            _calendarIconButton(
                notificationCalendarNextMonthTag,
                "下个月",
                Icons.Filled.ChevronRight,
                if (_canMoveMonth(1)) ({ _moveMonth(1) }) else null,
            )
            Spacer(Modifier.width(6.dp))
            _calendarIconButton(
                notificationCalendarNextYearTag,
                "下一年",
                Icons.Filled.KeyboardDoubleArrowRight,
                if (_canMoveMonth(12)) ({ _moveMonth(12) }) else null,
            )
        }
    }

    @Composable
    private fun _yearNavigation() {
        val lastYear = (_yearPageStart + _notificationCalendarYearsPerPage - 1).coerceIn(widget.firstDate.year, widget.lastDate.year)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            _calendarIconButton(
                notificationCalendarPreviousYearPageTag,
                "上一组年份",
                Icons.Filled.KeyboardDoubleArrowLeft,
                if (_canMovePreviousYearPage) ({ _moveYearPage(-1) }) else null,
            )
            Box(Modifier.weight(1f)) {
                if (_entryMode == _NotificationCalendarEntryMode.input) {
                    _inlineInput()
                } else {
                    _navigationLabel(
                        tag = notificationCalendarYearPickerCloseTag,
                        label = "${_yearPageStart}年 - ${lastYear}年",
                        tooltip = "返回月份选择",
                        expanded = true,
                        onPressed = ::_toggleYearPicker,
                    )
                }
            }
            _calendarIconButton(
                notificationCalendarNextYearPageTag,
                "下一组年份",
                Icons.Filled.KeyboardDoubleArrowRight,
                if (_canMoveNextYearPage) ({ _moveYearPage(1) }) else null,
            )
        }
    }

    @Composable
    private fun _navigationLabel(tag: String, label: String, tooltip: String, expanded: Boolean, onPressed: () -> Unit) {
        PressableSurface(
            modifier = Modifier.fillMaxWidth().testTag(tag),
            height = 32.dp,
            onPressed = onPressed,
            tooltip = tooltip,
            padding = EdgeInsets.symmetric(horizontal = 8.dp),
            backgroundColor = Color.Transparent,
            pressedBackgroundColor = UiColors.surfaceRaised,
            borderColor = Color.Transparent,
            selectedBorderColor = Color.Transparent,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    modifier = Modifier.testTag(notificationCalendarMonthLabelTag),
                    style = UiTypography.body.copyWith(fontWeight = FontWeight.W600),
                )
                Spacer(Modifier.width(3.dp))
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    color = UiColors.textMuted,
                    size = 18.dp,
                )
            }
        }
    }

    @Composable
    private fun _inlineInput() {
        Input(
            modifier = Modifier.testTag(notificationCalendarInlineDateInputTag),
            controller = _dateInputController,
            focusNode = _dateInputFocusNode,
            hintText = "例如 2026-07-10",
            // PORT: Compose has no `TextInputType.datetime`; the phone keypad
            // carries the digits and the '-' and '/' separators.
            keyboardType = KeyboardType.Phone,
            textInputAction = ImeAction.Done,
            minLines = 1,
            maxLines = 1,
            onChanged = ::_handleDateInputChanged,
            onSubmitted = { _ -> _commitDateInput() },
            onTapOutside = ::_handleDateInputTapOutside,
            tapRegionGroupId = _inlineInputTapRegionGroup,
            height = 32.dp,
            textAlign = TextAlign.Center,
            style = UiTypography.body.copyWith(fontWeight = FontWeight.W600),
            hintStyle = UiTypography.label.copyWith(color = UiColors.textMuted),
        )
    }

    @Composable
    private fun _weekdayHeader() {
        Row(Modifier.fillMaxWidth().testTag(notificationCalendarWeekdayHeaderTag), verticalAlignment = Alignment.CenterVertically) {
            for (label in listOf("一", "二", "三", "四", "五", "六", "日")) {
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = UiTypography.label.copyWith(color = UiColors.textMuted),
                )
            }
        }
    }

    @Composable
    private fun _monthGrid() {
        val firstOfMonth = DateTime(_visibleMonth.year, _visibleMonth.month)
        // Dart's `DateTime.monday` is 1.
        val leadingEmptyDays = firstOfMonth.weekday - 1
        val daysInMonth = DateTime(_visibleMonth.year, _visibleMonth.month + 1, 0).day
        val weekCount = (leadingEmptyDays + daysInMonth + 6) / 7
        Column(Modifier.fillMaxWidth().testTag(notificationCalendarMonthGridTag), horizontalAlignment = Alignment.CenterHorizontally) {
            for (week in 0 until weekCount) {
                Row(
                    Modifier
                        .testTag(notificationCalendarWeekTag(week))
                        .fillMaxWidth()
                        .padding(bottom = if (week == weekCount - 1) 0.dp else 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for (weekday in 0 until 7) {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            _dayForCell(cellIndex = week * 7 + weekday, leadingEmptyDays = leadingEmptyDays, daysInMonth = daysInMonth)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun _dayForCell(cellIndex: Int, leadingEmptyDays: Int, daysInMonth: Int) {
        val day = cellIndex - leadingEmptyDays + 1
        if (day < 1 || day > daysInMonth) {
            Spacer(Modifier.size(_notificationCalendarDaySize))
            return
        }
        val date = DateTime(_visibleMonth.year, _visibleMonth.month, day)
        Box(Modifier.size(_notificationCalendarDaySize), propagateMinConstraints = true) {
            _NotificationCalendarDay(
                modifier = Modifier.testTag(notificationDateDayTag(date.year, date.month, date.day)),
                date = date,
                selected = _sameDate(date, _selectedDate),
                today = _sameDate(date, DateTime.now()),
                enabled = _isSelectable(date),
                onPressed = { _selectDate(date) },
            )
        }
    }

    @Composable
    private fun _yearPicker() {
        val lastYear = _yearPageStart + _notificationCalendarYearsPerPage - 1
        val visibleLastYear = if (lastYear > widget.lastDate.year) widget.lastDate.year else lastYear
        val yearCount = visibleLastYear - _yearPageStart + 1
        val rowCount = (yearCount + 2) / 3
        Column(Modifier.fillMaxWidth().testTag(notificationCalendarYearPickerTag)) {
            for (row in 0 until rowCount) {
                Row(Modifier.fillMaxWidth().padding(bottom = if (row == rowCount - 1) 0.dp else 8.dp)) {
                    for (column in 0 until 3) {
                        Box(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                            _yearForCell(_yearPageStart + row * 3 + column)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun _yearForCell(year: Int) {
        if (year < widget.firstDate.year || year > widget.lastDate.year) {
            Spacer(Modifier.height(36.dp))
            return
        }
        Button(
            modifier = Modifier.testTag(notificationCalendarYearTag(year)),
            onPressed = { _selectYear(year) },
            selected = year == _visibleMonth.year,
            // A width-less Dart Button fills its bounded cell.
            width = Dp.Infinity,
            height = 36.dp,
            padding = EdgeInsets.zero,
        ) {
            Text("${year}年")
        }
    }
}

@Composable
private fun _NotificationCalendarDay(
    date: DateTime,
    selected: Boolean,
    today: Boolean,
    enabled: Boolean,
    onPressed: () -> Unit,
    modifier: Modifier = Modifier,
) = StatefulWidget(_NotificationCalendarDayWidget(date, selected, today, enabled, onPressed, modifier)) { _NotificationCalendarDayState() }

private class _NotificationCalendarDayWidget(
    val date: DateTime,
    val selected: Boolean,
    val today: Boolean,
    val enabled: Boolean,
    val onPressed: () -> Unit,
    val modifier: Modifier,
)

private class _NotificationCalendarDayState : ViewState<_NotificationCalendarDayWidget>() {
    private var _hovered = false

    private fun _setHovered(hovered: Boolean) {
        if (_hovered == hovered) return
        setState { _hovered = hovered }
    }

    @Composable
    override fun build() {
        val foreground =
            when {
                !widget.enabled -> UiColors.textMuted.copy(alpha = 0.45f)
                widget.selected -> UiColors.accent
                else -> UiColors.textSecondary
            }
        val borderColor =
            when {
                widget.selected -> UiColors.selectedBorder
                widget.today -> UiColors.accentBorder
                else -> Color.Transparent
            }
        val background by animateColorAsState(
            when {
                widget.selected -> UiColors.selected
                _hovered -> UiColors.surfaceRaised
                else -> Color.Transparent
            },
            tween(80),
            label = "calendarDayBackground",
        )
        val border by animateColorAsState(borderColor, tween(80), label = "calendarDayBorder")
        val label = "${widget.date.year}年${widget.date.month}月${widget.date.day}日"
        Box(
            widget.modifier
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = label
                    if (widget.selected) selected = true
                    if (!widget.enabled) {
                        disabled()
                    } else {
                        onClick {
                            widget.onPressed()
                            true
                        }
                    }
                }
                .pointerInput(widget.enabled) {
                    if (!widget.enabled) return@pointerInput
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.none { it.type == PointerType.Mouse }) continue
                            when (event.type) {
                                PointerEventType.Enter -> _setHovered(true)
                                PointerEventType.Exit -> _setHovered(false)
                                else -> Unit
                            }
                        }
                    }
                }
                .pointerInput(widget.enabled) {
                    // HitTestBehavior.opaque: the whole cell takes the tap.
                    detectTapGestures(onTap = { if (widget.enabled) widget.onPressed() })
                }
                .fillMaxSize()
                .decoration(background, RoundedCornerShape(UiRadii.md), border),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${widget.date.day}",
                style =
                    UiTypography.body.copyWith(
                        color = foreground,
                        fontWeight = if (widget.selected) FontWeight.W700 else FontWeight.W500,
                    ),
            )
        }
    }
}

private val _inputDatePattern = Regex("^(\\d{4})[-/](\\d{1,2})[-/](\\d{1,2})$")

internal fun _notificationCalendarParseInputDate(value: String): DateTime? {
    val match = _inputDatePattern.find(value) ?: return null
    val year = match.groupValues[1].toIntOrNull()
    val month = match.groupValues[2].toIntOrNull()
    val day = match.groupValues[3].toIntOrNull()
    if (year == null || month == null || day == null) return null
    val date = DateTime(year, month, day)
    return if (date.year == year && date.month == month && date.day == day) date else null
}

internal fun _notificationCalendarInputDate(date: DateTime): String =
    "${date.year.toString().padStart(4, '0')}-${date.month.toString().padStart(2, '0')}-${date.day.toString().padStart(2, '0')}"

private fun _monthIsBefore(left: DateTime, right: DateTime): Boolean =
    left.year < right.year || (left.year == right.year && left.month < right.month)

private fun _monthIsAfter(left: DateTime, right: DateTime): Boolean =
    left.year > right.year || (left.year == right.year && left.month > right.month)

private fun _sameDate(left: DateTime, right: DateTime): Boolean =
    left.year == right.year && left.month == right.month && left.day == right.day
