import { asString, type JsonMap } from '../lib/json';
import type { RoomMember, UserSummary } from '../protocol/models';
import * as room_display from './room_display';
import * as member_filter from './room_members_filter';

export const defaultMessageMentionOptionLimit = 8;

export class MessageMentionQuery {
  constructor(opts: { start: number; end: number; query: string }) {
    this.start = opts.start;
    this.end = opts.end;
    this.query = opts.query;
  }

  readonly start: number;
  readonly end: number;
  readonly query: string;
}

export class MessageMentionRange {
  constructor(opts: { start: number; end: number }) {
    this.start = opts.start;
    this.end = opts.end;
  }

  readonly start: number;
  readonly end: number;
}

export enum MessageMentionKind {
  user = 'user',
  everyone = 'everyone',
  admins = 'admins',
  bot = 'bot',
}

export class MessageMentionOption {
  constructor(opts: {
    member?: RoomMember | null;
    label: string;
    roleLabel: string;
    kind?: MessageMentionKind;
  }) {
    this.member = opts.member ?? null;
    this.label = opts.label;
    this.roleLabel = opts.roleLabel;
    this.kind = opts.kind ?? MessageMentionKind.user;
  }

  readonly member: RoomMember | null;
  readonly label: string;
  readonly roleLabel: string;
  readonly kind: MessageMentionKind;

  get isUser(): boolean {
    return this.kind === MessageMentionKind.user && this.member != null;
  }
}

export function activeMessageMentionQuery(opts: { text: string; cursorOffset: number }): MessageMentionQuery | null {
  const { text, cursorOffset } = opts;
  if (cursorOffset < 0 || cursorOffset > text.length) return null;
  let atIndex = -1;
  for (let index = cursorOffset - 1; index >= 0; index--) {
    const codeUnit = text.charCodeAt(index);
    if (text[index] === '@') {
      atIndex = index;
      break;
    }
    if (_isMentionTerminator(codeUnit)) break;
  }
  if (atIndex < 0) return null;
  const query = text.substring(atIndex + 1, cursorOffset);
  if (query.includes('@') || query.includes('\n') || query.includes('\r')) {
    return null;
  }
  if (_looksLikeEmailAddress(text, { atIndex, end: cursorOffset })) {
    return null;
  }
  return new MessageMentionQuery({ start: atIndex, end: cursorOffset, query });
}

export function messageMentionRanges(text: string, opts: { labels?: Iterable<string> } = {}): MessageMentionRange[] {
  const ranges = _messageMentionRangesForKnownLabels(text, opts.labels ?? []);
  let index = 0;
  while (index < text.length) {
    const atIndex = text.indexOf('@', index);
    if (atIndex < 0) break;
    let end = atIndex + 1;
    while (end < text.length && !_isMentionTerminator(text.charCodeAt(end))) {
      if (text[end] === '@') break;
      end++;
    }
    if (_looksLikeEmailAddress(text, { atIndex, end })) {
      index = end <= atIndex ? atIndex + 1 : end;
      continue;
    }
    const range = new MessageMentionRange({ start: atIndex, end });
    if (end > atIndex + 1 && !_overlapsAnyMentionRange(range, ranges)) {
      ranges.push(range);
    }
    index = end <= atIndex ? atIndex + 1 : end;
  }
  ranges.sort((a, b) => a.start - b.start);
  return ranges;
}

export function messageMentionOptions(opts: {
  members: Iterable<RoomMember>;
  query: string;
  ownerUserId: string | null;
  excludedUserId?: string | null;
  limit?: number;
  includeGangBot?: boolean;
}): MessageMentionOption[] {
  const { members, query, ownerUserId, limit = defaultMessageMentionOptionLimit, includeGangBot = false } = opts;
  const excludedUserId = opts.excludedUserId ?? null;
  const normalizedQuery = query.trim().toLowerCase();
  const specialOptions = _specialMentionOptions(normalizedQuery, { includeGangBot });
  if (specialOptions.length >= limit) {
    return specialOptions.slice(0, Math.max(0, limit));
  }
  const ranked: _RankedMentionOption[] = [];
  for (const member of members) {
    if (excludedUserId != null && member.user.id === excludedUserId) {
      continue;
    }
    const label = member_filter.roomMemberDisplayName(member).trim();
    if (label.length === 0) continue;
    const rank = _mentionSearchRank(member, label, normalizedQuery);
    if (rank == null) continue;
    ranked.push(
      new _RankedMentionOption({
        option: new MessageMentionOption({
          member,
          label,
          roleLabel: room_display.roomRoleLabel(member.user, { ownerUserId }),
        }),
        rank,
        roleRank: member_filter.roomMemberRoleRank(member, { ownerUserId }),
      }),
    );
  }
  ranked.sort((a, b) => {
    const rankCompare = a.rank - b.rank;
    if (rankCompare !== 0) return rankCompare;
    const roleCompare = a.roleRank - b.roleRank;
    if (roleCompare !== 0) return roleCompare;
    return _compareStrings(a.option.label.toLowerCase(), b.option.label.toLowerCase());
  });
  const remaining = Math.min(Math.max(limit - specialOptions.length, 0), limit);
  return [...specialOptions, ...ranked.slice(0, remaining).map((entry) => entry.option)];
}

export function messageMentionInsertText(option: MessageMentionOption): string {
  return `@${option.label} `;
}

export function messageMentionDescriptors(opts: {
  text: string;
  members: Iterable<RoomMember>;
  confirmedLabels?: Iterable<string>;
}): JsonMap[] {
  const { text, members } = opts;
  const out: JsonMap[] = [];
  const seen = new Set<string>();
  const labels = [...(opts.confirmedLabels ?? [])].map((label) => label.trim()).filter((label) => label.length > 0);
  if (labels.length > 0) {
    for (const label of labels) {
      _addMentionDescriptorForLabel({ out, seen, label, members });
    }
    return out;
  }
  for (const range of messageMentionRanges(text)) {
    _addMentionDescriptorForLabel({
      out,
      seen,
      label: text.substring(range.start + 1, range.end),
      members,
    });
  }
  return out;
}

function _addMentionDescriptorForLabel(opts: {
  out: JsonMap[];
  seen: Set<string>;
  label: string;
  members: Iterable<RoomMember>;
}): void {
  const { out, seen, label, members } = opts;
  const add = (key: string): boolean => {
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  };
  const kind = messageMentionKindForLabel(label);
  switch (kind) {
    case MessageMentionKind.everyone:
      if (add('kind:all')) out.push({ type: 'all', label });
      break;
    case MessageMentionKind.admins:
      if (add('kind:admins')) {
        out.push({ type: 'admins', label });
      }
      break;
    case MessageMentionKind.bot:
      if (add('kind:bot')) out.push({ type: 'bot', label });
      break;
    case MessageMentionKind.user: {
      const member = resolveMessageMentionMember({ label, members });
      if (member != null && add(`user:${member.user.id}`)) {
        out.push({ type: 'user', user_id: member.user.id, label });
      }
      break;
    }
  }
}

export function messageMentionsUser(opts: {
  text: string;
  mentions?: readonly JsonMap[];
  user: UserSummary;
  ownerUserId: string | null;
}): boolean {
  const { text, mentions = [], user, ownerUserId } = opts;
  for (const mention of mentions) {
    const type = (asString(mention['type']) ?? '').trim().toLowerCase();
    switch (type) {
      case 'all':
        return true;
      case 'admins':
        if (_userIsAdminLike(user, { ownerUserId })) return true;
        break;
      case 'user': {
        const userId = asString(mention['user_id']);
        const uid = asString(mention['uid']);
        if (userId != null && userId === user.id) return true;
        if (uid != null && uid === user.uid) return true;
        break;
      }
    }
  }
  return messageMentionRanges(text).some((range) => {
    const label = text.substring(range.start + 1, range.end);
    return messageMentionLabelTargetsUser({ label, user, ownerUserId });
  });
}

export function messageMentionLabelTargetsUser(opts: {
  label: string;
  user: UserSummary;
  ownerUserId: string | null;
}): boolean {
  const { label, user, ownerUserId } = opts;
  const kind = messageMentionKindForLabel(label);
  switch (kind) {
    case MessageMentionKind.everyone:
      return true;
    case MessageMentionKind.admins:
      return _userIsAdminLike(user, { ownerUserId });
    case MessageMentionKind.bot:
      return false;
    case MessageMentionKind.user: {
      const normalized = _normalizeMentionLabel(label);
      return _mentionLabelsForUser(user)
        .map(_normalizeMentionLabel)
        .some((candidate) => candidate === normalized);
    }
  }
}

export function messageMentionKindForLabel(label: string): MessageMentionKind {
  const normalized = _normalizeMentionLabel(label);
  if (normalized === _normalizeMentionLabel(kMentionEveryoneLabel)) {
    return MessageMentionKind.everyone;
  }
  if (normalized === _normalizeMentionLabel(kMentionAdminsLabel)) {
    return MessageMentionKind.admins;
  }
  if (normalized === _normalizeMentionLabel(kMentionGangBotLabel)) {
    return MessageMentionKind.bot;
  }
  return MessageMentionKind.user;
}

export function resolveMessageMentionMember(opts: {
  label: string;
  members: Iterable<RoomMember>;
}): RoomMember | null {
  const normalized = _normalizeMentionLabel(opts.label);
  for (const member of opts.members) {
    const labels = _mentionLabelsForMember(member).map(_normalizeMentionLabel);
    if (labels.some((candidate) => candidate === normalized)) return member;
  }
  return null;
}

export function isMessageMentionRangeForUser(opts: {
  text: string;
  range: MessageMentionRange;
  mentions?: readonly JsonMap[];
  user: UserSummary;
  ownerUserId: string | null;
}): boolean {
  const { text, range, mentions = [], user, ownerUserId } = opts;
  if (range.start < 0 || range.end > text.length || range.start >= range.end) {
    return false;
  }
  const label = text.substring(range.start + 1, range.end);
  for (const mention of mentions) {
    const mentionLabel = asString(mention['label']);
    if (mentionLabel == null || _normalizeMentionLabel(mentionLabel) !== _normalizeMentionLabel(label)) {
      continue;
    }
    const type = (asString(mention['type']) ?? '').trim().toLowerCase();
    switch (type) {
      case 'all':
        return true;
      case 'admins':
        if (_userIsAdminLike(user, { ownerUserId })) return true;
        break;
      case 'user': {
        const userId = asString(mention['user_id']);
        const uid = asString(mention['uid']);
        if (userId != null && userId === user.id) return true;
        if (uid != null && uid === user.uid) return true;
        break;
      }
    }
  }
  return messageMentionLabelTargetsUser({ label, user, ownerUserId });
}

export const kMentionEveryoneLabel = '所有人';
export const kMentionAdminsLabel = '管理员';

/** gang-bot, the room AI. Mentioning it sends `{type: "bot"}`. */
export const kMentionGangBotLabel = 'gang-bot';

function _specialMentionOptions(
  normalizedQuery: string,
  opts: { includeGangBot?: boolean } = {},
): MessageMentionOption[] {
  const { includeGangBot = false } = opts;
  const options: MessageMentionOption[] = [];
  const addIfMatches = (args: { label: string; kind: MessageMentionKind; roleLabel?: string }): void => {
    const { label, kind, roleLabel = '' } = args;
    if (!_specialMentionMatches(label, normalizedQuery)) return;
    options.push(new MessageMentionOption({ label, roleLabel, kind }));
  };

  if (includeGangBot) {
    addIfMatches({
      label: kMentionGangBotLabel,
      kind: MessageMentionKind.bot,
      roleLabel: 'AI',
    });
  }
  addIfMatches({ label: kMentionEveryoneLabel, kind: MessageMentionKind.everyone });
  addIfMatches({ label: kMentionAdminsLabel, kind: MessageMentionKind.admins });
  return options;
}

function _specialMentionMatches(label: string, normalizedQuery: string): boolean {
  if (normalizedQuery.length === 0) return true;
  const normalizedLabel = _normalizeMentionLabel(label);
  if (normalizedLabel.includes(normalizedQuery)) return true;
  if (label === kMentionEveryoneLabel) {
    return 'all'.includes(normalizedQuery) || 'everyone'.includes(normalizedQuery);
  }
  if (label === kMentionAdminsLabel) {
    return 'admin'.includes(normalizedQuery) || 'admins'.includes(normalizedQuery);
  }
  if (label === kMentionGangBotLabel) {
    return ['bot', 'ai', 'gangbot', '机器人'].some((alias) => alias.includes(normalizedQuery));
  }
  return false;
}

function _messageMentionRangesForKnownLabels(text: string, labels: Iterable<string>): MessageMentionRange[] {
  const knownLabels = [...new Set([...labels].map((label) => label.trim()).filter((label) => label.length > 0))].sort(
    (a, b) => b.length - a.length,
  );
  if (knownLabels.length === 0) return [];

  const ranges: MessageMentionRange[] = [];
  let index = 0;
  while (index < text.length) {
    const atIndex = text.indexOf('@', index);
    if (atIndex < 0) break;
    let matched: MessageMentionRange | null = null;
    for (const label of knownLabels) {
      const end = atIndex + 1 + label.length;
      if (end > text.length) continue;
      if (text.substring(atIndex + 1, end) !== label) continue;
      if (!_isKnownMentionBoundary(text, end)) continue;
      matched = new MessageMentionRange({ start: atIndex, end });
      break;
    }
    if (matched != null) {
      ranges.push(matched);
      index = matched.end;
    } else {
      index = atIndex + 1;
    }
  }
  return ranges;
}

function _overlapsAnyMentionRange(range: MessageMentionRange, ranges: MessageMentionRange[]): boolean {
  for (const existing of ranges) {
    if (range.start < existing.end && range.end > existing.start) return true;
  }
  return false;
}

function _isKnownMentionBoundary(text: string, end: number): boolean {
  if (end >= text.length) return true;
  return _isMentionTerminator(text.charCodeAt(end));
}

function _mentionSearchRank(member: RoomMember, label: string, normalizedQuery: string): number | null {
  if (normalizedQuery.length === 0) return 100;
  const candidates = [
    ...new Set<string>([
      label,
      member.roomDisplayName ?? '',
      member.remarkName ?? '',
      member.user.roomDisplayName ?? '',
      member.user.displayName,
      member.user.username,
      member.user.uid ?? '',
      member.user.id,
    ]),
  ]
    .filter((value) => value.trim().length > 0)
    .map((value) => value.toLowerCase());
  let bestRank = 1 << 30;
  for (const candidate of candidates) {
    if (candidate === normalizedQuery) {
      bestRank = bestRank < 0 ? bestRank : 0;
    } else if (candidate.startsWith(normalizedQuery)) {
      bestRank = bestRank < 1 ? bestRank : 1;
    } else if (candidate.includes(normalizedQuery)) {
      bestRank = bestRank < 2 ? bestRank : 2;
    }
  }
  return bestRank === 1 << 30 ? null : bestRank;
}

function _mentionLabelsForMember(member: RoomMember): string[] {
  const labels: string[] = [];
  const label = member_filter.roomMemberDisplayName(member).trim();
  if (label.length > 0) labels.push(label);
  const values = [
    member.roomDisplayName,
    member.remarkName,
    member.user.roomDisplayName,
    member.user.displayName,
    member.user.username,
    member.user.uid,
    member.user.id,
  ];
  for (const value of values) {
    const text = value?.trim();
    if (text != null && text.length > 0) labels.push(text);
  }
  return labels;
}

function _mentionLabelsForUser(user: UserSummary): string[] {
  const labels: string[] = [];
  const values = [user.roomDisplayName, user.displayName, user.username, user.uid, user.id];
  for (const value of values) {
    const text = value?.trim();
    if (text != null && text.length > 0) labels.push(text);
  }
  return labels;
}

function _normalizeMentionLabel(value: string): string {
  return value.trim().toLowerCase();
}

function _userIsAdminLike(user: UserSummary, opts: { ownerUserId: string | null }): boolean {
  const { ownerUserId } = opts;
  if (user.isSuperuser || (ownerUserId != null && user.id === ownerUserId)) {
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

function _isMentionTerminator(codeUnit: number): boolean {
  return codeUnit <= 0x20 || codeUnit === 0x3000;
}

function _looksLikeEmailAddress(text: string, opts: { atIndex: number; end: number }): boolean {
  const { atIndex, end } = opts;
  if (atIndex <= 0 || end <= atIndex + 1 || end > text.length) return false;
  const domain = text.substring(atIndex + 1, end);
  if (!domain.includes('.')) return false;
  if (domain.startsWith('.') || domain.endsWith('.')) return false;
  if (!_everyCodeUnit(domain, _isEmailDomainCodeUnit)) return false;

  let localStart = atIndex - 1;
  while (localStart >= 0 && _isEmailLocalCodeUnit(text.charCodeAt(localStart))) {
    localStart--;
  }
  localStart++;
  if (localStart >= atIndex) return false;
  const local = text.substring(localStart, atIndex);
  return (
    local.length > 0 &&
    !local.startsWith('.') &&
    !local.endsWith('.') &&
    _everyCodeUnit(local, _isEmailLocalCodeUnit)
  );
}

function _everyCodeUnit(value: string, test: (codeUnit: number) => boolean): boolean {
  for (let index = 0; index < value.length; index++) {
    if (!test(value.charCodeAt(index))) return false;
  }
  return true;
}

function _isEmailLocalCodeUnit(codeUnit: number): boolean {
  return (
    _isAsciiLetterOrDigit(codeUnit) ||
    codeUnit === 0x2e ||
    codeUnit === 0x5f ||
    codeUnit === 0x25 ||
    codeUnit === 0x2b ||
    codeUnit === 0x2d
  );
}

function _isEmailDomainCodeUnit(codeUnit: number): boolean {
  return _isAsciiLetterOrDigit(codeUnit) || codeUnit === 0x2e || codeUnit === 0x2d;
}

function _isAsciiLetterOrDigit(codeUnit: number): boolean {
  return (
    (codeUnit >= 0x30 && codeUnit <= 0x39) || (codeUnit >= 0x41 && codeUnit <= 0x5a) || (codeUnit >= 0x61 && codeUnit <= 0x7a)
  );
}

/** Dart `String.compareTo`: UTF-16 code unit order. */
function _compareStrings(a: string, b: string): number {
  if (a === b) return 0;
  return a < b ? -1 : 1;
}

class _RankedMentionOption {
  constructor(opts: { option: MessageMentionOption; rank: number; roleRank: number }) {
    this.option = opts.option;
    this.rank = opts.rank;
    this.roleRank = opts.roleRank;
  }

  readonly option: MessageMentionOption;
  readonly rank: number;
  readonly roleRank: number;
}
