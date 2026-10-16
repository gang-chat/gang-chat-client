export interface LivePresenceSpeechPlayer {
  speak(announcement: LivePresenceAnnouncement, opts: { volume: number }): Promise<void>;

  dispose(): Promise<void>;
}

export enum LivePresenceAnnouncementAction {
  joined = 'joined',
  left = 'left',
  removed = 'removed',
}

export function shouldSpeakLivePresenceAnnouncement(opts: {
  enabled: boolean;
  participantIdentity: string | null | undefined;
  currentUserIdentity: string;
}): boolean {
  const { enabled, participantIdentity, currentUserIdentity } = opts;
  const identity = participantIdentity?.trim();
  return enabled && identity != null && identity.length > 0 && identity !== currentUserIdentity;
}

export class LivePresenceAnnouncement {
  constructor(opts: { roleLabel: string; roomDisplayName: string; action: LivePresenceAnnouncementAction }) {
    this.roleLabel = opts.roleLabel;
    this.roomDisplayName = opts.roomDisplayName;
    this.action = opts.action;
  }

  readonly roleLabel: string;
  readonly roomDisplayName: string;
  readonly action: LivePresenceAnnouncementAction;

  get actionLabel(): string {
    switch (this.action) {
      case LivePresenceAnnouncementAction.joined:
        return '进入了语音频道';
      case LivePresenceAnnouncementAction.left:
        return '离开了语音频道';
      case LivePresenceAnnouncementAction.removed:
        return '被踢出了语音频道';
    }
  }

  /// Separate segments let the platform TTS adapter insert real pauses instead
  /// of relying on punctuation, whose timing varies between installed voices.
  get segments(): string[] {
    return [this.roleLabel, this.roomDisplayName, this.actionLabel];
  }
}
