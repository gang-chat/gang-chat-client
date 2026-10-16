import '../protocol/models.dart';
import 'room_display.dart' as room_display;
import 'room_members_filter.dart' as member_filter;

const int defaultMessageMentionOptionLimit = 8;

class MessageMentionQuery {
  const MessageMentionQuery({
    required this.start,
    required this.end,
    required this.query,
  });

  final int start;
  final int end;
  final String query;
}

class MessageMentionRange {
  const MessageMentionRange({required this.start, required this.end});

  final int start;
  final int end;
}

enum MessageMentionKind { user, everyone, admins, bot }

class MessageMentionOption {
  const MessageMentionOption({
    this.member,
    required this.label,
    required this.roleLabel,
    this.kind = MessageMentionKind.user,
  });

  final RoomMember? member;
  final String label;
  final String roleLabel;
  final MessageMentionKind kind;

  bool get isUser => kind == MessageMentionKind.user && member != null;
}

MessageMentionQuery? activeMessageMentionQuery({
  required String text,
  required int cursorOffset,
}) {
  if (cursorOffset < 0 || cursorOffset > text.length) return null;
  var atIndex = -1;
  for (var index = cursorOffset - 1; index >= 0; index--) {
    final codeUnit = text.codeUnitAt(index);
    if (text[index] == '@') {
      atIndex = index;
      break;
    }
    if (_isMentionTerminator(codeUnit)) break;
  }
  if (atIndex < 0) return null;
  final query = text.substring(atIndex + 1, cursorOffset);
  if (query.contains('@') || query.contains('\n') || query.contains('\r')) {
    return null;
  }
  if (_looksLikeEmailAddress(text, atIndex: atIndex, end: cursorOffset)) {
    return null;
  }
  return MessageMentionQuery(start: atIndex, end: cursorOffset, query: query);
}

List<MessageMentionRange> messageMentionRanges(
  String text, {
  Iterable<String> labels = const [],
}) {
  final ranges = _messageMentionRangesForKnownLabels(text, labels);
  var index = 0;
  while (index < text.length) {
    final atIndex = text.indexOf('@', index);
    if (atIndex < 0) break;
    var end = atIndex + 1;
    while (end < text.length && !_isMentionTerminator(text.codeUnitAt(end))) {
      if (text[end] == '@') break;
      end++;
    }
    if (_looksLikeEmailAddress(text, atIndex: atIndex, end: end)) {
      index = end <= atIndex ? atIndex + 1 : end;
      continue;
    }
    final range = MessageMentionRange(start: atIndex, end: end);
    if (end > atIndex + 1 && !_overlapsAnyMentionRange(range, ranges)) {
      ranges.add(range);
    }
    index = end <= atIndex ? atIndex + 1 : end;
  }
  ranges.sort((a, b) => a.start.compareTo(b.start));
  return ranges;
}

List<MessageMentionOption> messageMentionOptions({
  required Iterable<RoomMember> members,
  required String query,
  required String? ownerUserId,
  String? excludedUserId,
  int limit = defaultMessageMentionOptionLimit,
  bool includeGangBot = false,
}) {
  final normalizedQuery = query.trim().toLowerCase();
  final specialOptions = _specialMentionOptions(
    normalizedQuery,
    includeGangBot: includeGangBot,
  );
  if (specialOptions.length >= limit) {
    return specialOptions.take(limit).toList();
  }
  final ranked = <_RankedMentionOption>[];
  for (final member in members) {
    if (excludedUserId != null && member.user.id == excludedUserId) {
      continue;
    }
    final label = member_filter.roomMemberDisplayName(member).trim();
    if (label.isEmpty) continue;
    final rank = _mentionSearchRank(member, label, normalizedQuery);
    if (rank == null) continue;
    ranked.add(
      _RankedMentionOption(
        option: MessageMentionOption(
          member: member,
          label: label,
          roleLabel: room_display.roomRoleLabel(
            member.user,
            ownerUserId: ownerUserId,
          ),
        ),
        rank: rank,
        roleRank: member_filter.roomMemberRoleRank(
          member,
          ownerUserId: ownerUserId,
        ),
      ),
    );
  }
  ranked.sort((a, b) {
    final rankCompare = a.rank.compareTo(b.rank);
    if (rankCompare != 0) return rankCompare;
    final roleCompare = a.roleRank.compareTo(b.roleRank);
    if (roleCompare != 0) return roleCompare;
    return a.option.label.toLowerCase().compareTo(b.option.label.toLowerCase());
  });
  return [
    ...specialOptions,
    ...ranked
        .take((limit - specialOptions.length).clamp(0, limit).toInt())
        .map((entry) => entry.option),
  ];
}

String messageMentionInsertText(MessageMentionOption option) =>
    '@${option.label} ';

List<Map<String, Object?>> messageMentionDescriptors({
  required String text,
  required Iterable<RoomMember> members,
  Iterable<String> confirmedLabels = const [],
}) {
  final out = <Map<String, Object?>>[];
  final seen = <String>{};
  final labels = confirmedLabels
      .map((label) => label.trim())
      .where((label) => label.isNotEmpty)
      .toList(growable: false);
  if (labels.isNotEmpty) {
    for (final label in labels) {
      _addMentionDescriptorForLabel(
        out: out,
        seen: seen,
        label: label,
        members: members,
      );
    }
    return out;
  }
  for (final range in messageMentionRanges(text)) {
    _addMentionDescriptorForLabel(
      out: out,
      seen: seen,
      label: text.substring(range.start + 1, range.end),
      members: members,
    );
  }
  return out;
}

void _addMentionDescriptorForLabel({
  required List<Map<String, Object?>> out,
  required Set<String> seen,
  required String label,
  required Iterable<RoomMember> members,
}) {
  final kind = messageMentionKindForLabel(label);
  switch (kind) {
    case MessageMentionKind.everyone:
      if (seen.add('kind:all')) out.add({'type': 'all', 'label': label});
      break;
    case MessageMentionKind.admins:
      if (seen.add('kind:admins')) {
        out.add({'type': 'admins', 'label': label});
      }
      break;
    case MessageMentionKind.bot:
      if (seen.add('kind:bot')) out.add({'type': 'bot', 'label': label});
      break;
    case MessageMentionKind.user:
      final member = resolveMessageMentionMember(
        label: label,
        members: members,
      );
      if (member != null && seen.add('user:${member.user.id}')) {
        out.add({'type': 'user', 'user_id': member.user.id, 'label': label});
      }
      break;
  }
}

bool messageMentionsUser({
  required String text,
  List<Map<String, Object?>> mentions = const [],
  required UserSummary user,
  required String? ownerUserId,
}) {
  for (final mention in mentions) {
    final type = (mention['type'] as String? ?? '').trim().toLowerCase();
    switch (type) {
      case 'all':
        return true;
      case 'admins':
        if (_userIsAdminLike(user, ownerUserId: ownerUserId)) return true;
        break;
      case 'user':
        final userId = mention['user_id'] as String?;
        final uid = mention['uid'] as String?;
        if (userId != null && userId == user.id) return true;
        if (uid != null && uid == user.uid) return true;
        break;
    }
  }
  return messageMentionRanges(text).any((range) {
    final label = text.substring(range.start + 1, range.end);
    return messageMentionLabelTargetsUser(
      label: label,
      user: user,
      ownerUserId: ownerUserId,
    );
  });
}

bool messageMentionLabelTargetsUser({
  required String label,
  required UserSummary user,
  required String? ownerUserId,
}) {
  final kind = messageMentionKindForLabel(label);
  switch (kind) {
    case MessageMentionKind.everyone:
      return true;
    case MessageMentionKind.admins:
      return _userIsAdminLike(user, ownerUserId: ownerUserId);
    case MessageMentionKind.bot:
      return false;
    case MessageMentionKind.user:
      final normalized = _normalizeMentionLabel(label);
      return _mentionLabelsForUser(
        user,
      ).map(_normalizeMentionLabel).any((candidate) => candidate == normalized);
  }
}

MessageMentionKind messageMentionKindForLabel(String label) {
  final normalized = _normalizeMentionLabel(label);
  if (normalized == _normalizeMentionLabel(kMentionEveryoneLabel)) {
    return MessageMentionKind.everyone;
  }
  if (normalized == _normalizeMentionLabel(kMentionAdminsLabel)) {
    return MessageMentionKind.admins;
  }
  if (normalized == _normalizeMentionLabel(kMentionGangBotLabel)) {
    return MessageMentionKind.bot;
  }
  return MessageMentionKind.user;
}

RoomMember? resolveMessageMentionMember({
  required String label,
  required Iterable<RoomMember> members,
}) {
  final normalized = _normalizeMentionLabel(label);
  for (final member in members) {
    final labels = _mentionLabelsForMember(member).map(_normalizeMentionLabel);
    if (labels.any((candidate) => candidate == normalized)) return member;
  }
  return null;
}

bool isMessageMentionRangeForUser({
  required String text,
  required MessageMentionRange range,
  List<Map<String, Object?>> mentions = const [],
  required UserSummary user,
  required String? ownerUserId,
}) {
  if (range.start < 0 || range.end > text.length || range.start >= range.end) {
    return false;
  }
  final label = text.substring(range.start + 1, range.end);
  for (final mention in mentions) {
    final mentionLabel = mention['label'] as String?;
    if (mentionLabel == null ||
        _normalizeMentionLabel(mentionLabel) != _normalizeMentionLabel(label)) {
      continue;
    }
    final type = (mention['type'] as String? ?? '').trim().toLowerCase();
    switch (type) {
      case 'all':
        return true;
      case 'admins':
        if (_userIsAdminLike(user, ownerUserId: ownerUserId)) return true;
        break;
      case 'user':
        final userId = mention['user_id'] as String?;
        final uid = mention['uid'] as String?;
        if (userId != null && userId == user.id) return true;
        if (uid != null && uid == user.uid) return true;
        break;
    }
  }
  return messageMentionLabelTargetsUser(
    label: label,
    user: user,
    ownerUserId: ownerUserId,
  );
}

const kMentionEveryoneLabel = '所有人';
const kMentionAdminsLabel = '管理员';

/// gang-bot, the room AI. Mentioning it sends `{type: "bot"}`.
const kMentionGangBotLabel = 'gang-bot';

List<MessageMentionOption> _specialMentionOptions(
  String normalizedQuery, {
  bool includeGangBot = false,
}) {
  final options = <MessageMentionOption>[];
  void addIfMatches({
    required String label,
    required MessageMentionKind kind,
    String roleLabel = '',
  }) {
    if (!_specialMentionMatches(label, normalizedQuery)) return;
    options.add(
      MessageMentionOption(label: label, roleLabel: roleLabel, kind: kind),
    );
  }

  if (includeGangBot) {
    addIfMatches(
      label: kMentionGangBotLabel,
      kind: MessageMentionKind.bot,
      roleLabel: 'AI',
    );
  }
  addIfMatches(label: kMentionEveryoneLabel, kind: MessageMentionKind.everyone);
  addIfMatches(label: kMentionAdminsLabel, kind: MessageMentionKind.admins);
  return options;
}

bool _specialMentionMatches(String label, String normalizedQuery) {
  if (normalizedQuery.isEmpty) return true;
  final normalizedLabel = _normalizeMentionLabel(label);
  if (normalizedLabel.contains(normalizedQuery)) return true;
  if (label == kMentionEveryoneLabel) {
    return 'all'.contains(normalizedQuery) ||
        'everyone'.contains(normalizedQuery);
  }
  if (label == kMentionAdminsLabel) {
    return 'admin'.contains(normalizedQuery) ||
        'admins'.contains(normalizedQuery);
  }
  if (label == kMentionGangBotLabel) {
    return const [
      'bot',
      'ai',
      'gangbot',
      '机器人',
    ].any((alias) => alias.contains(normalizedQuery));
  }
  return false;
}

List<MessageMentionRange> _messageMentionRangesForKnownLabels(
  String text,
  Iterable<String> labels,
) {
  final knownLabels =
      labels
          .map((label) => label.trim())
          .where((label) => label.isNotEmpty)
          .toSet()
          .toList()
        ..sort((a, b) => b.length.compareTo(a.length));
  if (knownLabels.isEmpty) return <MessageMentionRange>[];

  final ranges = <MessageMentionRange>[];
  var index = 0;
  while (index < text.length) {
    final atIndex = text.indexOf('@', index);
    if (atIndex < 0) break;
    MessageMentionRange? matched;
    for (final label in knownLabels) {
      final end = atIndex + 1 + label.length;
      if (end > text.length) continue;
      if (text.substring(atIndex + 1, end) != label) continue;
      if (!_isKnownMentionBoundary(text, end)) continue;
      matched = MessageMentionRange(start: atIndex, end: end);
      break;
    }
    if (matched != null) {
      ranges.add(matched);
      index = matched.end;
    } else {
      index = atIndex + 1;
    }
  }
  return ranges;
}

bool _overlapsAnyMentionRange(
  MessageMentionRange range,
  List<MessageMentionRange> ranges,
) {
  for (final existing in ranges) {
    if (range.start < existing.end && range.end > existing.start) return true;
  }
  return false;
}

bool _isKnownMentionBoundary(String text, int end) {
  if (end >= text.length) return true;
  return _isMentionTerminator(text.codeUnitAt(end));
}

int? _mentionSearchRank(
  RoomMember member,
  String label,
  String normalizedQuery,
) {
  if (normalizedQuery.isEmpty) return 100;
  final candidates =
      <String>{
            label,
            member.roomDisplayName ?? '',
            member.remarkName ?? '',
            member.user.roomDisplayName ?? '',
            member.user.displayName,
            member.user.username,
            member.user.uid ?? '',
            member.user.id,
          }
          .where((value) => value.trim().isNotEmpty)
          .map((value) => value.toLowerCase())
          .toList();
  var bestRank = 1 << 30;
  for (final candidate in candidates) {
    if (candidate == normalizedQuery) {
      bestRank = bestRank < 0 ? bestRank : 0;
    } else if (candidate.startsWith(normalizedQuery)) {
      bestRank = bestRank < 1 ? bestRank : 1;
    } else if (candidate.contains(normalizedQuery)) {
      bestRank = bestRank < 2 ? bestRank : 2;
    }
  }
  return bestRank == 1 << 30 ? null : bestRank;
}

Iterable<String> _mentionLabelsForMember(RoomMember member) sync* {
  final label = member_filter.roomMemberDisplayName(member).trim();
  if (label.isNotEmpty) yield label;
  final values = [
    member.roomDisplayName,
    member.remarkName,
    member.user.roomDisplayName,
    member.user.displayName,
    member.user.username,
    member.user.uid,
    member.user.id,
  ];
  for (final value in values) {
    final text = value?.trim();
    if (text != null && text.isNotEmpty) yield text;
  }
}

Iterable<String> _mentionLabelsForUser(UserSummary user) sync* {
  final values = [
    user.roomDisplayName,
    user.displayName,
    user.username,
    user.uid,
    user.id,
  ];
  for (final value in values) {
    final text = value?.trim();
    if (text != null && text.isNotEmpty) yield text;
  }
}

String _normalizeMentionLabel(String value) {
  return value.trim().toLowerCase();
}

bool _userIsAdminLike(UserSummary user, {required String? ownerUserId}) {
  if (user.isSuperuser || (ownerUserId != null && user.id == ownerUserId)) {
    return true;
  }
  switch ((user.roomRole ?? '').toLowerCase()) {
    case 'owner':
    case 'creator':
    case 'admin':
    case 'administrator':
      return true;
    default:
      return false;
  }
}

bool _isMentionTerminator(int codeUnit) =>
    codeUnit <= 0x20 || codeUnit == 0x3000;

bool _looksLikeEmailAddress(
  String text, {
  required int atIndex,
  required int end,
}) {
  if (atIndex <= 0 || end <= atIndex + 1 || end > text.length) return false;
  final domain = text.substring(atIndex + 1, end);
  if (!domain.contains('.')) return false;
  if (domain.startsWith('.') || domain.endsWith('.')) return false;
  if (!domain.codeUnits.every(_isEmailDomainCodeUnit)) return false;

  var localStart = atIndex - 1;
  while (localStart >= 0 &&
      _isEmailLocalCodeUnit(text.codeUnitAt(localStart))) {
    localStart--;
  }
  localStart++;
  if (localStart >= atIndex) return false;
  final local = text.substring(localStart, atIndex);
  return local.isNotEmpty &&
      !local.startsWith('.') &&
      !local.endsWith('.') &&
      local.codeUnits.every(_isEmailLocalCodeUnit);
}

bool _isEmailLocalCodeUnit(int codeUnit) {
  return _isAsciiLetterOrDigit(codeUnit) ||
      codeUnit == 0x2e ||
      codeUnit == 0x5f ||
      codeUnit == 0x25 ||
      codeUnit == 0x2b ||
      codeUnit == 0x2d;
}

bool _isEmailDomainCodeUnit(int codeUnit) {
  return _isAsciiLetterOrDigit(codeUnit) ||
      codeUnit == 0x2e ||
      codeUnit == 0x2d;
}

bool _isAsciiLetterOrDigit(int codeUnit) {
  return (codeUnit >= 0x30 && codeUnit <= 0x39) ||
      (codeUnit >= 0x41 && codeUnit <= 0x5a) ||
      (codeUnit >= 0x61 && codeUnit <= 0x7a);
}

class _RankedMentionOption {
  const _RankedMentionOption({
    required this.option,
    required this.rank,
    required this.roleRank,
  });

  final MessageMentionOption option;
  final int rank;
  final int roleRank;
}
