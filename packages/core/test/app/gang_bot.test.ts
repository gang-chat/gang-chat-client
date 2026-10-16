// Port of the `gang-bot mentions` group of test/unit/app/gang_bot_test.dart.
// The model/API groups of that file belong to the protocol port.
import { describe, expect, test } from 'vitest';

import {
  kMentionGangBotLabel,
  MessageMentionKind,
  messageMentionDescriptors,
  messageMentionKindForLabel,
  messageMentionLabelTargetsUser,
  messageMentionOptions,
} from '../../src/app/message_mentions';
import { UserSummary } from '../../src/protocol/models';

describe('gang-bot mentions', () => {
  test('offers gang-bot first only when the room has it on', () => {
    const withBot = messageMentionOptions({
      members: [],
      query: '',
      ownerUserId: null,
      includeGangBot: true,
    });
    expect(withBot[0]!.label).toBe(kMentionGangBotLabel);
    expect(withBot[0]!.kind).toBe(MessageMentionKind.bot);
    expect(withBot[0]!.roleLabel).toBe('AI');

    const withoutBot = messageMentionOptions({
      members: [],
      query: '',
      ownerUserId: null,
    });
    expect(withoutBot.map((option) => option.label)).not.toContain(kMentionGangBotLabel);
  });

  test('matches the bot by name and common aliases', () => {
    for (const query of ['gang', 'bot', 'ai', '机器']) {
      const options = messageMentionOptions({
        members: [],
        query,
        ownerUserId: null,
        includeGangBot: true,
      });
      expect(
        options.map((option) => option.kind),
        query,
      ).toContain(MessageMentionKind.bot);
    }
    const unrelated = messageMentionOptions({
      members: [],
      query: 'zzz',
      ownerUserId: null,
      includeGangBot: true,
    });
    expect(unrelated).toEqual([]);
  });

  test('sends a bot mention, never a user one', () => {
    const descriptors = messageMentionDescriptors({
      text: '@gang-bot 你怎么看',
      members: [],
      confirmedLabels: ['gang-bot'],
    });
    expect(descriptors).toEqual([{ type: 'bot', label: 'gang-bot' }]);
    expect(messageMentionKindForLabel('Gang-Bot')).toBe(MessageMentionKind.bot);
    const someone = new UserSummary({
      id: 'u1',
      username: 'gang-bot-fan',
      displayName: 'gang-bot',
      avatarUrl: null,
      defaultAvatarKey: 'blue-3',
    });
    expect(
      messageMentionLabelTargetsUser({
        label: 'gang-bot',
        user: someone,
        ownerUserId: null,
      }),
    ).toBe(false);
  });
});
