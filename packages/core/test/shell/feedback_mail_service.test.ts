import { expect, test } from 'vitest';
import { FeedbackMailDraft, FeedbackMailService } from '../../src/shell/feedback_mail_service';
import { ExternalUriLauncher } from '../../src/shell/external_uri_launcher';
import { installFakePlatform } from '../support/fake_platform';

test('feedback mail draft builds a mailto URI with sender metadata', () => {
  const uri = new URL(
    new FeedbackMailDraft({
      from: 'user@example.test',
      to: 'gang-chat@outlook.com',
      subject: 'Subject',
      body: 'Body',
    }).toMailtoUri(),
  );

  expect(uri.protocol).toBe('mailto:');
  expect(uri.pathname).toBe('gang-chat@outlook.com');
  expect(uri.searchParams.get('from')).toBe('user@example.test');
  expect(uri.searchParams.get('subject')).toBe('Subject');
  expect(uri.searchParams.get('body')).toBe('Body');
});

test('mailto query is encoded like Dart Uri (spaces as +, reserved escaped)', () => {
  const value = new FeedbackMailDraft({ from: 'a@b.c', to: 'x@y.z', subject: '反馈 (1)!', body: 'a&b=c\nd' }).toMailtoUri();
  expect(value).toBe(
    'mailto:x@y.z?subject=%E5%8F%8D%E9%A6%88+%281%29%21&body=a%26b%3Dc%0Ad&from=a%40b.c',
  );
  const parsed = new URL(value);
  expect(parsed.searchParams.get('subject')).toBe('反馈 (1)!');
  expect(parsed.searchParams.get('body')).toBe('a&b=c\nd');
});

test('openDraft and ExternalUriLauncher.open go through platform().openExternal', async () => {
  const fake = installFakePlatform({ os: 'windows' });
  await new FeedbackMailService().openDraft(new FeedbackMailDraft({ from: 'a@b.c', to: 'x@y.z', subject: 's', body: 'b' }));
  await new ExternalUriLauncher().open('https://example.test/download');
  expect(fake.openedUrls).toEqual(['mailto:x@y.z?subject=s&body=b&from=a%40b.c', 'https://example.test/download']);

  fake.openExternalResult = false;
  await expect(new FeedbackMailService().openMailto('mailto:x@y.z')).rejects.toThrow('当前平台不支持打开邮件客户端');
  await expect(new ExternalUriLauncher().open('https://example.test')).rejects.toThrow('当前平台不支持打开链接');
});
