import { describe, expect, test } from 'vitest';

import {
  _tryParseDateTime,
  appVersionLabel,
  appVersionNumberLabel,
  boundEmailForFeedback,
  compareAppVersions,
  feedbackMailBody,
  feedbackMailSubject,
  gangChatClientVersion,
  installTimeLabel,
  officialDateTimeLabel,
  officialVersionTimeLabel,
  updateCheckSucceededText,
} from '../../src/app/settings_about';

describe('settings_about', () => {
  test('debug builds use fixed app version', () => {
    expect(gangChatClientVersion).toBe('1.0.0');
  });

  test('appVersionLabel normalizes display text', () => {
    expect(appVersionLabel('1.2.3')).toBe('v1.2.3');
    expect(appVersionLabel('v2.0.0')).toBe('v2.0.0');
  });

  test('appVersionNumberLabel strips display prefix', () => {
    expect(appVersionNumberLabel('1.2.3')).toBe('1.2.3');
    expect(appVersionNumberLabel('v2.0.0')).toBe('2.0.0');
  });

  test('compareAppVersions compares semantic version parts', () => {
    expect(compareAppVersions('1.2.3', '1.2.3')).toBe(0);
    expect(compareAppVersions('1.2.4', '1.2.3')).toBeGreaterThan(0);
    expect(compareAppVersions('1.2.3', '1.3.0')).toBeLessThan(0);
    expect(compareAppVersions('v1.2.3+4', '1.2.3')).toBe(0);
    expect(compareAppVersions('0.10.0', '0.9.0')).toBeGreaterThan(0);
  });

  test('updateCheckSucceededText describes current and newer versions', () => {
    expect(updateCheckSucceededText({ currentVersion: '1.0.0', latestVersion: '1.0.0' })).toBe('当前已是最新版本');
    expect(updateCheckSucceededText({ currentVersion: '1.0.0', latestVersion: '1.1.0' })).toBe('发现新版本 v1.1.0');
  });

  test('feedback helpers use bound email and version', () => {
    expect(boundEmailForFeedback(' user@example.test ')).toBe('user@example.test');
    expect(boundEmailForFeedback('')).toBeNull();
    expect(feedbackMailSubject('1.0.0')).toContain('v1.0.0');
    expect(feedbackMailBody({ senderEmail: 'user@example.test', currentVersion: '1.0.0' })).toContain(
      '发件人（绑定邮箱）：user@example.test',
    );
  });

  test('official release time converts UTC across the Beijing date boundary', () => {
    expect(officialDateTimeLabel(new Date(Date.UTC(2026, 6, 12, 20, 34)))).toBe('2026/07/13 04:34 UTC+08:00');
    expect(officialVersionTimeLabel('2026-07-12T20:34:27.126Z')).toBe('2026/07/13 04:34 UTC+08:00');
  });

  test('install time stays local and supports legacy date files', () => {
    expect(installTimeLabel('2026/07/01')).toBe('2026/07/01');
    expect(installTimeLabel('2026-07-01T12:34:56')).toBe('2026/07/01 12:34');
  });

  test('_tryParseDateTime follows Dart DateTime.tryParse', () => {
    expect(_tryParseDateTime('2026-07-08T09:00:00.123Z')).toEqual(new Date(Date.UTC(2026, 6, 8, 9, 0, 0, 123)));
    expect(_tryParseDateTime('20260708T090000Z')).toEqual(new Date(Date.UTC(2026, 6, 8, 9)));
    expect(_tryParseDateTime('2026-07-08 17:30:00+0830')).toEqual(new Date(Date.UTC(2026, 6, 8, 9)));
    expect(_tryParseDateTime('2026-07-08')).toEqual(new Date(2026, 6, 8));
    expect(_tryParseDateTime('Wed, 08 Jul 2026 09:00:00 GMT')).toBeNull();
    expect(_tryParseDateTime('')).toBeNull();
  });
});
