part of 'home_notifications.dart';

const double _notificationCalendarDaySize = 36;
const int _notificationCalendarYearsPerPage = 12;

enum _NotificationCalendarEntryMode { calendar, input }

class _NotificationCalendarDialog extends StatefulWidget {
  const _NotificationCalendarDialog({
    required this.title,
    required this.initialDate,
    required this.firstDate,
    required this.lastDate,
  });

  final String title;
  final DateTime initialDate;
  final DateTime firstDate;
  final DateTime lastDate;

  @override
  State<_NotificationCalendarDialog> createState() =>
      _NotificationCalendarDialogState();
}

class _NotificationCalendarDialogState
    extends State<_NotificationCalendarDialog> {
  late DateTime _selectedDate;
  late DateTime _visibleMonth;
  late final TextEditingController _dateInputController;
  late final FocusNode _dateInputFocusNode;
  late int _yearPageStart;
  bool _showingYearPicker = false;
  _NotificationCalendarEntryMode _entryMode =
      _NotificationCalendarEntryMode.calendar;
  final Object _inlineInputTapRegionGroup = Object();
  String? _lastRejectedDateInput;

  DateTime get _firstMonth =>
      DateTime(widget.firstDate.year, widget.firstDate.month);

  DateTime get _lastMonth =>
      DateTime(widget.lastDate.year, widget.lastDate.month);

  @override
  void initState() {
    super.initState();
    _selectedDate = widget.initialDate;
    _visibleMonth = DateTime(_selectedDate.year, _selectedDate.month);
    _dateInputController = TextEditingController(
      text: _notificationCalendarInputDate(_selectedDate),
    );
    _dateInputFocusNode = FocusNode();
    _dateInputFocusNode.addListener(_handleDateInputFocusChanged);
    _yearPageStart = _yearPageStartFor(_visibleMonth.year);
  }

  @override
  void dispose() {
    _dateInputFocusNode.removeListener(_handleDateInputFocusChanged);
    _dateInputController.dispose();
    _dateInputFocusNode.dispose();
    super.dispose();
  }

  int _yearPageStartFor(int year) {
    final firstYear = widget.firstDate.year;
    final lastPossibleStart =
        widget.lastDate.year - _notificationCalendarYearsPerPage + 1;
    if (lastPossibleStart <= firstYear) return firstYear;
    final preferredStart = year - (_notificationCalendarYearsPerPage ~/ 2);
    if (preferredStart < firstYear) return firstYear;
    if (preferredStart > lastPossibleStart) return lastPossibleStart;
    return preferredStart;
  }

  bool _canMoveMonth(int monthDelta) {
    final target = DateTime(
      _visibleMonth.year,
      _visibleMonth.month + monthDelta,
    );
    return !_monthIsBefore(target, _firstMonth) &&
        !_monthIsAfter(target, _lastMonth);
  }

  void _moveMonth(int monthDelta) {
    if (!_canMoveMonth(monthDelta)) return;
    setState(() {
      _visibleMonth = DateTime(
        _visibleMonth.year,
        _visibleMonth.month + monthDelta,
      );
      _yearPageStart = _yearPageStartFor(_visibleMonth.year);
    });
  }

  bool get _canMovePreviousYearPage => _yearPageStart > widget.firstDate.year;

  bool get _canMoveNextYearPage =>
      _yearPageStart + _notificationCalendarYearsPerPage <=
      widget.lastDate.year;

  void _moveYearPage(int direction) {
    if (direction < 0 && !_canMovePreviousYearPage) return;
    if (direction > 0 && !_canMoveNextYearPage) return;
    setState(() {
      final target =
          _yearPageStart + direction * _notificationCalendarYearsPerPage;
      final lastPossibleStart =
          widget.lastDate.year - _notificationCalendarYearsPerPage + 1;
      _yearPageStart = target.clamp(
        widget.firstDate.year,
        lastPossibleStart < widget.firstDate.year
            ? widget.firstDate.year
            : lastPossibleStart,
      );
    });
  }

  void _toggleYearPicker() {
    setState(() {
      _showingYearPicker = !_showingYearPicker;
      _yearPageStart = _yearPageStartFor(_visibleMonth.year);
    });
  }

  void _toggleEntryMode() {
    if (_entryMode == _NotificationCalendarEntryMode.input) {
      _commitDateInput();
      return;
    }
    setState(() {
      _entryMode = _NotificationCalendarEntryMode.input;
      _showingYearPicker = false;
      _lastRejectedDateInput = null;
      _dateInputController.text = _notificationCalendarInputDate(_selectedDate);
    });
    _focusDateInput(selectAll: true);
  }

  void _selectYear(int year) {
    setState(() {
      _visibleMonth = DateTime(year, _visibleMonth.month);
      _yearPageStart = _yearPageStartFor(year);
      _showingYearPicker = false;
    });
  }

  bool _isSelectable(DateTime date) {
    return !date.isBefore(widget.firstDate) && !date.isAfter(widget.lastDate);
  }

  bool _commitDateInput() {
    if (_entryMode != _NotificationCalendarEntryMode.input) return true;
    final value = _dateInputController.text.trim();
    final date = _notificationCalendarParseInputDate(value);
    if (date == null) {
      _showDateInputError('请输入如 2026-07-10 的有效日期');
      return false;
    }
    if (!_isSelectable(date)) {
      _showDateInputError(
        '日期需介于 ${_notificationCalendarInputDate(widget.firstDate)} 和 ${_notificationCalendarInputDate(widget.lastDate)} 之间',
      );
      return false;
    }
    _selectDate(date);
    setState(() => _entryMode = _NotificationCalendarEntryMode.calendar);
    _dateInputFocusNode.unfocus();
    return true;
  }

  void _showDateInputError(String message) {
    final value = _dateInputController.text.trim();
    if (_lastRejectedDateInput != value) {
      _lastRejectedDateInput = value;
      showFloatingErrorNotice(context, message);
    }
    _focusDateInput();
  }

  void _focusDateInput({bool selectAll = false}) {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted || _entryMode != _NotificationCalendarEntryMode.input) {
        return;
      }
      _dateInputFocusNode.requestFocus();
      if (selectAll) {
        _dateInputController.selection = TextSelection(
          baseOffset: 0,
          extentOffset: _dateInputController.text.length,
        );
      }
    });
  }

  void _handleDateInputFocusChanged() {
    if (_dateInputFocusNode.hasFocus ||
        _entryMode != _NotificationCalendarEntryMode.input) {
      return;
    }
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted ||
          _dateInputFocusNode.hasFocus ||
          _entryMode != _NotificationCalendarEntryMode.input) {
        return;
      }
      _commitDateInput();
    });
  }

  void _handleDateInputTapOutside(PointerDownEvent _) {
    _commitDateInput();
  }

  void _selectDate(DateTime date) {
    setState(() {
      _selectedDate = date;
      _visibleMonth = DateTime(date.year, date.month);
      _yearPageStart = _yearPageStartFor(date.year);
      _dateInputController.text = _notificationCalendarInputDate(date);
      _lastRejectedDateInput = null;
    });
  }

  void _confirm() {
    if (!_commitDateInput()) {
      return;
    }
    Navigator.of(context).pop(_selectedDate);
  }

  void _handleDateInputChanged(String _) {
    _lastRejectedDateInput = null;
  }

  @override
  Widget build(BuildContext context) {
    final isAndroid = Theme.of(context).platform == TargetPlatform.android;
    final hideCalendarForAndroidInput =
        isAndroid &&
        (_entryMode == _NotificationCalendarEntryMode.input ||
            MediaQuery.viewInsetsOf(context).bottom > 0);
    return DialogFrame(
      title: widget.title,
      icon: Icons.calendar_month_outlined,
      maxWidth: 420,
      actionBar: ResponsiveDialogActionBar(
        actions: [
          ResponsiveDialogAction(
            label: '取消',
            onPressed: () => Navigator.of(context).pop(),
          ),
          ResponsiveDialogAction(
            buttonKey: const ValueKey('notification-calendar-confirm-button'),
            label: '确定',
            icon: Icons.check,
            tone: ButtonTone.primary,
            onPressed: _confirm,
          ),
        ],
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          _toolbar(),
          if (!hideCalendarForAndroidInput) ...[
            const SizedBox(height: 14),
            if (_showingYearPicker) ...[
              _yearPicker(),
            ] else ...[
              _weekdayHeader(),
              const SizedBox(height: 6),
              _monthGrid(),
            ],
          ],
        ],
      ),
    );
  }

  Widget _toolbar() {
    return Row(
      children: [
        Expanded(child: _navigation()),
        const SizedBox(width: 6),
        TapRegion(
          groupId: _inlineInputTapRegionGroup,
          child: ButtonIcon(
            key: const ValueKey('notification-calendar-input-mode-toggle'),
            tooltip: '切换到手动输入',
            icon: const Icon(Icons.edit_calendar_outlined),
            onPressed: _toggleEntryMode,
            selected: _entryMode == _NotificationCalendarEntryMode.input,
            size: 32,
          ),
        ),
      ],
    );
  }

  Widget _navigation() {
    if (_showingYearPicker) return _yearNavigation();
    return _monthNavigation();
  }

  Widget _monthNavigation() {
    if (HomeAdaptiveLayout.usesCompactLayout(context)) {
      final month = _visibleMonth.month.toString().padLeft(2, '0');
      return Row(
        children: [
          ButtonIcon(
            key: const ValueKey('notification-calendar-previous-month'),
            tooltip: '上个月',
            icon: const Icon(Icons.chevron_left),
            onPressed: _canMoveMonth(-1) ? () => _moveMonth(-1) : null,
            size: 32,
          ),
          const SizedBox(width: 4),
          Expanded(
            child: _entryMode == _NotificationCalendarEntryMode.input
                ? _inlineInput()
                : _navigationLabel(
                    key: const ValueKey(
                      'notification-calendar-year-picker-toggle',
                    ),
                    label: '${_visibleMonth.year}/$month',
                    tooltip: '展开年份选择',
                    expanded: false,
                    onPressed: _toggleYearPicker,
                  ),
          ),
          const SizedBox(width: 4),
          ButtonIcon(
            key: const ValueKey('notification-calendar-next-month'),
            tooltip: '下个月',
            icon: const Icon(Icons.chevron_right),
            onPressed: _canMoveMonth(1) ? () => _moveMonth(1) : null,
            size: 32,
          ),
        ],
      );
    }
    return Row(
      children: [
        ButtonIcon(
          key: const ValueKey('notification-calendar-previous-year'),
          tooltip: '上一年',
          icon: const Icon(Icons.keyboard_double_arrow_left),
          onPressed: _canMoveMonth(-12) ? () => _moveMonth(-12) : null,
          size: 32,
        ),
        const SizedBox(width: 6),
        ButtonIcon(
          key: const ValueKey('notification-calendar-previous-month'),
          tooltip: '上个月',
          icon: const Icon(Icons.chevron_left),
          onPressed: _canMoveMonth(-1) ? () => _moveMonth(-1) : null,
          size: 32,
        ),
        Expanded(
          child: _entryMode == _NotificationCalendarEntryMode.input
              ? _inlineInput()
              : _navigationLabel(
                  key: const ValueKey(
                    'notification-calendar-year-picker-toggle',
                  ),
                  label: '${_visibleMonth.year} 年 ${_visibleMonth.month} 月',
                  tooltip: '展开年份选择',
                  expanded: false,
                  onPressed: _toggleYearPicker,
                ),
        ),
        ButtonIcon(
          key: const ValueKey('notification-calendar-next-month'),
          tooltip: '下个月',
          icon: const Icon(Icons.chevron_right),
          onPressed: _canMoveMonth(1) ? () => _moveMonth(1) : null,
          size: 32,
        ),
        const SizedBox(width: 6),
        ButtonIcon(
          key: const ValueKey('notification-calendar-next-year'),
          tooltip: '下一年',
          icon: const Icon(Icons.keyboard_double_arrow_right),
          onPressed: _canMoveMonth(12) ? () => _moveMonth(12) : null,
          size: 32,
        ),
      ],
    );
  }

  Widget _yearNavigation() {
    final lastYear = (_yearPageStart + _notificationCalendarYearsPerPage - 1)
        .clamp(widget.firstDate.year, widget.lastDate.year);
    return Row(
      children: [
        ButtonIcon(
          key: const ValueKey('notification-calendar-previous-year-page'),
          tooltip: '上一组年份',
          icon: const Icon(Icons.keyboard_double_arrow_left),
          onPressed: _canMovePreviousYearPage ? () => _moveYearPage(-1) : null,
          size: 32,
        ),
        Expanded(
          child: _entryMode == _NotificationCalendarEntryMode.input
              ? _inlineInput()
              : _navigationLabel(
                  key: const ValueKey(
                    'notification-calendar-year-picker-close',
                  ),
                  label: '$_yearPageStart年 - $lastYear年',
                  tooltip: '返回月份选择',
                  expanded: true,
                  onPressed: _toggleYearPicker,
                ),
        ),
        ButtonIcon(
          key: const ValueKey('notification-calendar-next-year-page'),
          tooltip: '下一组年份',
          icon: const Icon(Icons.keyboard_double_arrow_right),
          onPressed: _canMoveNextYearPage ? () => _moveYearPage(1) : null,
          size: 32,
        ),
      ],
    );
  }

  Widget _navigationLabel({
    required Key key,
    required String label,
    required String tooltip,
    required bool expanded,
    required VoidCallback onPressed,
  }) {
    return PressableSurface(
      key: key,
      height: 32,
      onPressed: onPressed,
      tooltip: tooltip,
      padding: const EdgeInsets.symmetric(horizontal: 8),
      backgroundColor: Colors.transparent,
      pressedBackgroundColor: UiColors.surfaceRaised,
      borderColor: Colors.transparent,
      selectedBorderColor: Colors.transparent,
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Text(
            label,
            key: const ValueKey('notification-calendar-month-label'),
            style: UiTypography.body.copyWith(fontWeight: FontWeight.w600),
          ),
          const SizedBox(width: 3),
          Icon(
            expanded ? Icons.keyboard_arrow_up : Icons.keyboard_arrow_down,
            color: UiColors.textMuted,
            size: 18,
          ),
        ],
      ),
    );
  }

  Widget _inlineInput() {
    return Input(
      key: const ValueKey('notification-calendar-inline-date-input'),
      controller: _dateInputController,
      focusNode: _dateInputFocusNode,
      hintText: '例如 2026-07-10',
      keyboardType: TextInputType.datetime,
      textInputAction: TextInputAction.done,
      minLines: 1,
      maxLines: 1,
      onChanged: _handleDateInputChanged,
      onSubmitted: (_) => _commitDateInput(),
      onTapOutside: _handleDateInputTapOutside,
      tapRegionGroupId: _inlineInputTapRegionGroup,
      height: 32,
      textAlign: TextAlign.center,
      style: UiTypography.body.copyWith(fontWeight: FontWeight.w600),
      hintStyle: UiTypography.label.copyWith(color: UiColors.textMuted),
    );
  }

  Widget _weekdayHeader() {
    return const Row(
      key: ValueKey('notification-calendar-weekday-header'),
      children: [
        _NotificationCalendarWeekday('一'),
        _NotificationCalendarWeekday('二'),
        _NotificationCalendarWeekday('三'),
        _NotificationCalendarWeekday('四'),
        _NotificationCalendarWeekday('五'),
        _NotificationCalendarWeekday('六'),
        _NotificationCalendarWeekday('日'),
      ],
    );
  }

  Widget _monthGrid() {
    final firstOfMonth = DateTime(_visibleMonth.year, _visibleMonth.month);
    final leadingEmptyDays = firstOfMonth.weekday - DateTime.monday;
    final daysInMonth = DateTime(
      _visibleMonth.year,
      _visibleMonth.month + 1,
      0,
    ).day;
    final weekCount = (leadingEmptyDays + daysInMonth + 6) ~/ 7;
    return Column(
      key: const ValueKey('notification-calendar-month-grid'),
      mainAxisSize: MainAxisSize.min,
      children: [
        for (var week = 0; week < weekCount; week++)
          Padding(
            key: ValueKey('notification-calendar-week-$week'),
            padding: EdgeInsets.only(bottom: week == weekCount - 1 ? 0 : 4),
            child: Row(
              children: [
                for (var weekday = 0; weekday < 7; weekday++)
                  Expanded(
                    child: Center(
                      child: _dayForCell(
                        cellIndex: week * 7 + weekday,
                        leadingEmptyDays: leadingEmptyDays,
                        daysInMonth: daysInMonth,
                      ),
                    ),
                  ),
              ],
            ),
          ),
      ],
    );
  }

  Widget _dayForCell({
    required int cellIndex,
    required int leadingEmptyDays,
    required int daysInMonth,
  }) {
    final day = cellIndex - leadingEmptyDays + 1;
    if (day < 1 || day > daysInMonth) {
      return const SizedBox.square(dimension: _notificationCalendarDaySize);
    }
    final date = DateTime(_visibleMonth.year, _visibleMonth.month, day);
    return SizedBox.square(
      dimension: _notificationCalendarDaySize,
      child: _NotificationCalendarDay(
        key: ValueKey(
          'notification-date-day-${date.year}-${date.month}-${date.day}',
        ),
        date: date,
        selected: _sameDate(date, _selectedDate),
        today: _sameDate(date, DateTime.now()),
        enabled: _isSelectable(date),
        onPressed: () => _selectDate(date),
      ),
    );
  }

  Widget _yearPicker() {
    final lastYear = _yearPageStart + _notificationCalendarYearsPerPage - 1;
    final visibleLastYear = lastYear > widget.lastDate.year
        ? widget.lastDate.year
        : lastYear;
    final yearCount = visibleLastYear - _yearPageStart + 1;
    final rowCount = (yearCount + 2) ~/ 3;
    return Column(
      key: const ValueKey('notification-calendar-year-picker'),
      mainAxisSize: MainAxisSize.min,
      children: [
        for (var row = 0; row < rowCount; row++)
          Padding(
            padding: EdgeInsets.only(bottom: row == rowCount - 1 ? 0 : 8),
            child: Row(
              children: [
                for (var column = 0; column < 3; column++)
                  Expanded(
                    child: Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 4),
                      child: _yearForCell(_yearPageStart + row * 3 + column),
                    ),
                  ),
              ],
            ),
          ),
      ],
    );
  }

  Widget _yearForCell(int year) {
    if (year < widget.firstDate.year || year > widget.lastDate.year) {
      return const SizedBox(height: 36);
    }
    return Button(
      key: ValueKey('notification-calendar-year-$year'),
      onPressed: () => _selectYear(year),
      selected: year == _visibleMonth.year,
      height: 36,
      padding: EdgeInsets.zero,
      child: Text('$year年'),
    );
  }
}

class _NotificationCalendarWeekday extends StatelessWidget {
  const _NotificationCalendarWeekday(this.label);

  final String label;

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: Text(
        label,
        textAlign: TextAlign.center,
        style: UiTypography.label.copyWith(color: UiColors.textMuted),
      ),
    );
  }
}

class _NotificationCalendarDay extends StatefulWidget {
  const _NotificationCalendarDay({
    super.key,
    required this.date,
    required this.selected,
    required this.today,
    required this.enabled,
    required this.onPressed,
  });

  final DateTime date;
  final bool selected;
  final bool today;
  final bool enabled;
  final VoidCallback onPressed;

  @override
  State<_NotificationCalendarDay> createState() =>
      _NotificationCalendarDayState();
}

class _NotificationCalendarDayState extends State<_NotificationCalendarDay> {
  bool _hovered = false;

  @override
  Widget build(BuildContext context) {
    final foreground = !widget.enabled
        ? UiColors.textMuted.withValues(alpha: 0.45)
        : widget.selected
        ? UiColors.accent
        : UiColors.textSecondary;
    final borderColor = widget.selected
        ? UiColors.selectedBorder
        : widget.today
        ? UiColors.accentBorder
        : Colors.transparent;
    return Semantics(
      button: true,
      enabled: widget.enabled,
      selected: widget.selected,
      label: '${widget.date.year}年${widget.date.month}月${widget.date.day}日',
      child: MouseRegion(
        cursor: widget.enabled
            ? SystemMouseCursors.click
            : SystemMouseCursors.basic,
        onEnter: widget.enabled ? (_) => _setHovered(true) : null,
        onExit: widget.enabled ? (_) => _setHovered(false) : null,
        child: GestureDetector(
          behavior: HitTestBehavior.opaque,
          onTap: widget.enabled ? widget.onPressed : null,
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 80),
            alignment: Alignment.center,
            decoration: BoxDecoration(
              color: widget.selected
                  ? UiColors.selected
                  : _hovered
                  ? UiColors.surfaceRaised
                  : Colors.transparent,
              borderRadius: BorderRadius.circular(UiRadii.md),
              border: Border.all(color: borderColor),
            ),
            child: Text(
              '${widget.date.day}',
              style: UiTypography.body.copyWith(
                color: foreground,
                fontWeight: widget.selected ? FontWeight.w700 : FontWeight.w500,
              ),
            ),
          ),
        ),
      ),
    );
  }

  void _setHovered(bool hovered) {
    if (_hovered == hovered) return;
    setState(() => _hovered = hovered);
  }
}

DateTime? _notificationCalendarParseInputDate(String value) {
  final match = RegExp(
    r'^(\d{4})[-/](\d{1,2})[-/](\d{1,2})$',
  ).firstMatch(value);
  if (match == null) return null;
  final year = int.tryParse(match.group(1)!);
  final month = int.tryParse(match.group(2)!);
  final day = int.tryParse(match.group(3)!);
  if (year == null || month == null || day == null) return null;
  final date = DateTime(year, month, day);
  return date.year == year && date.month == month && date.day == day
      ? date
      : null;
}

String _notificationCalendarInputDate(DateTime date) {
  return '${date.year.toString().padLeft(4, '0')}-${date.month.toString().padLeft(2, '0')}-${date.day.toString().padLeft(2, '0')}';
}

bool _monthIsBefore(DateTime left, DateTime right) {
  return left.year < right.year ||
      (left.year == right.year && left.month < right.month);
}

bool _monthIsAfter(DateTime left, DateTime right) {
  return left.year > right.year ||
      (left.year == right.year && left.month > right.month);
}

bool _sameDate(DateTime left, DateTime right) {
  return left.year == right.year &&
      left.month == right.month &&
      left.day == right.day;
}
