import { describe, expect, test } from 'vitest';

import { userFacingErrorMessage } from '../../src/app/error_display';

describe('userFacingErrorMessage', () => {
  test('preserves an existing Chinese error', () => {
    expect(userFacingErrorMessage('保存失败')).toBe('保存失败');
  });

  test('maps common platform errors without exposing English details', () => {
    expect(userFacingErrorMessage(new Error('SocketException: connection failed'))).toBe('网络连接失败，请检查网络后重试');
    expect(userFacingErrorMessage(new Error('unsupported platform'))).toBe('当前平台不支持此操作');
  });

  test('uses the caller fallback for unknown English errors', () => {
    expect(userFacingErrorMessage(new Error('decoder exploded'), { fallback: '读取图片失败' })).toBe('读取图片失败');
  });
});
