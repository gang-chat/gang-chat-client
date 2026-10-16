import { asString, type JsonMap } from '../lib/json';
import type { JoinRequest, RoomDetail, UserSummary } from '../protocol/models';
import { userPrimaryName } from './room_display';

export enum JoinRequestListBodyState {
  empty = 'empty',
  results = 'results',
}

export class JoinRequestCandidate {
  constructor(opts: { request: JoinRequest; busy: boolean }) {
    this.request = opts.request;
    this.busy = opts.busy;
  }

  readonly request: JoinRequest;
  readonly busy: boolean;

  get canReview(): boolean {
    return !this.busy;
  }

  get userForInfo(): UserSummary {
    return this.request.user.copyWith({ roomRole: 'pending' });
  }
}

export class JoinRequestReviewActionState {
  constructor(opts: { requests: JoinRequest[]; busyRequestIds: Set<string>; changed: boolean }) {
    this.requests = opts.requests;
    this.busyRequestIds = opts.busyRequestIds;
    this.changed = opts.changed;
  }

  readonly requests: JoinRequest[];
  readonly busyRequestIds: Set<string>;
  readonly changed: boolean;

  get hasPendingRequests(): boolean {
    return this.requests.length > 0;
  }
}

export function joinRequestCandidates(opts: {
  requests: Iterable<JoinRequest>;
  busyRequestIds?: Iterable<string>;
}): JoinRequestCandidate[] {
  const busySet = new Set(opts.busyRequestIds ?? []);
  return [...opts.requests].map(
    (request) =>
      new JoinRequestCandidate({
        request,
        busy: busySet.has(request.id),
      }),
  );
}

export function joinRequestListBodyState(candidates: Iterable<JoinRequestCandidate>): JoinRequestListBodyState {
  for (const _ of candidates) return JoinRequestListBodyState.results;
  return JoinRequestListBodyState.empty;
}

export function joinRequestReasonText(request: JoinRequest): string | null {
  const reason = request.reason.trim();
  if (reason.length === 0) return null;
  return reason;
}

export function joinRequestDetailReasonText(request: JoinRequest): string {
  return joinRequestReasonText(request) ?? '未填写';
}

export function joinRequestFromInvitation(request: JoinRequest): boolean {
  return request.source === 'invitation' || request.inviters.length > 0;
}

export function joinRequestSourceText(request: JoinRequest): string {
  if (!joinRequestFromInvitation(request)) return '公开房间搜索';
  const names = request.inviters.map(userPrimaryName).filter((name) => name.trim().length > 0);
  if (names.length === 0) return '邀请';
  return `${names.join('、')} 的邀请`;
}

export function canStartJoinRequestReview(opts: { requestId: string; busyRequestIds?: Iterable<string> }): boolean {
  for (const id of opts.busyRequestIds ?? []) {
    if (id === opts.requestId) return false;
  }
  return true;
}

export function shouldRefreshJoinRequestBadgeForEvent(opts: {
  data: JsonMap;
  selectedRoomId: string | null;
  canReviewJoinRequests: boolean;
}): boolean {
  const roomId = asString(opts.data['room_id']);
  if (roomId == null) return false;
  return shouldRefreshJoinRequestBadgeForRoom({
    roomId,
    selectedRoomId: opts.selectedRoomId,
    canReviewJoinRequests: opts.canReviewJoinRequests,
  });
}

export function shouldRefreshJoinRequestBadgeForRoom(opts: {
  roomId: string;
  selectedRoomId: string | null;
  canReviewJoinRequests: boolean;
}): boolean {
  return opts.roomId === opts.selectedRoomId && opts.canReviewJoinRequests;
}

export function joinRequestBadgeRefreshTarget(opts: {
  room: RoomDetail | null;
  canReviewJoinRequests: boolean;
}): RoomDetail | null {
  const { room } = opts;
  if (room == null) return null;
  if (!opts.canReviewJoinRequests) return null;
  return room;
}

export function canApplyJoinRequestBadgeRefresh(opts: { targetRoomId: string; selectedRoomId: string | null }): boolean {
  return opts.targetRoomId === opts.selectedRoomId;
}

export function joinRequestReviewStarted(opts: {
  requests: Iterable<JoinRequest>;
  requestId: string;
  busyRequestIds?: Iterable<string>;
}): JoinRequestReviewActionState {
  return new JoinRequestReviewActionState({
    requests: [...opts.requests],
    busyRequestIds: new Set([...(opts.busyRequestIds ?? []), opts.requestId]),
    changed: false,
  });
}

export function joinRequestReviewSucceeded(opts: {
  requests: Iterable<JoinRequest>;
  requestId: string;
  busyRequestIds?: Iterable<string>;
}): JoinRequestReviewActionState {
  const { requestId } = opts;
  return new JoinRequestReviewActionState({
    requests: [...opts.requests].filter((request) => request.id !== requestId),
    busyRequestIds: new Set([...(opts.busyRequestIds ?? [])].filter((id) => id !== requestId)),
    changed: true,
  });
}

export function joinRequestReviewFailed(opts: {
  requests: Iterable<JoinRequest>;
  requestId: string;
  busyRequestIds?: Iterable<string>;
}): JoinRequestReviewActionState {
  const { requestId } = opts;
  return new JoinRequestReviewActionState({
    requests: [...opts.requests],
    busyRequestIds: new Set([...(opts.busyRequestIds ?? [])].filter((id) => id !== requestId)),
    changed: false,
  });
}
