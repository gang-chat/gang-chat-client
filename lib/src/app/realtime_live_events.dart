const liveSnapshotRealtimeEventTypes = <String>{
  'live_participant_joined',
  'live_participant_left',
  'live_participant_updated',
  'live_participant_moderated',
  'live_participant_reconnecting',
  'live_participant_reconnected',
  'live_participants_reconciled',
  'live_room_reconnecting',
  'live_room_finished',
};

/// Whether a realtime event carries the server's authoritative live snapshot.
///
/// Reconnect lifecycle snapshots use the same payload as ordinary participant
/// updates. Keeping their routing in one allow-list prevents room-card counts
/// and the open voice roster from consuming different generations of state.
bool isLiveSnapshotRealtimeEventType(String type) {
  return liveSnapshotRealtimeEventTypes.contains(type);
}
