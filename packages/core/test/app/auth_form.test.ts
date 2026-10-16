import { describe, expect, test } from 'vitest';

import {
  authRequestFromForm,
  authSubmitFailed,
  authSubmitInvalid,
  authSubmitStarted,
  registerEmailValidationError,
} from '../../src/app/auth_form';
import { AuthException } from '../../src/auth/auth_client';

class HandshakeException extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'HandshakeException';
  }
}

describe('auth_form', () => {
  test('authRequestFromForm validates required login credentials', () => {
    const result = authRequestFromForm({ registering: false, login: ' ', password: '' });

    expect(result.request).toBeNull();
    expect(result.error).toBe('请输入账号和密码后继续');
  });

  test('authRequestFromForm localizes validation errors', () => {
    const english = authRequestFromForm({ registering: false, login: ' ', password: '', language: 'en' });
    const traditional = authRequestFromForm({
      registering: true,
      login: 'a@example.test',
      password: 'secret',
      language: 'zh-Hant',
    });

    expect(english.error).toBe('Enter your account and password to continue');
    expect(traditional.error).toBe('使用者名稱不能為空');
  });

  test('authRequestFromForm builds trimmed login request', () => {
    const result = authRequestFromForm({ registering: false, login: ' logan ', password: 'secret' });

    expect(result.error).toBeNull();
    expect(result.request?.registering).toBe(false);
    expect(result.request?.login).toBe('logan');
    expect(result.request?.password).toBe('secret');
  });

  test('authRequestFromForm validates register fields', () => {
    expect(
      authRequestFromForm({
        registering: true,
        username: 'logan.test',
        login: 'a@example.test',
        password: 'secret',
        confirmPassword: 'secret',
      }).error,
    ).toBe('登录用户名需为 3-32 位，只能包含英文字母、数字、下划线或连字符');
    expect(
      authRequestFromForm({
        registering: true,
        username: 'logan',
        login: 'invalid-email',
        password: 'secret',
        confirmPassword: 'secret',
      }).error,
    ).toBe('请输入有效的邮箱地址');
    expect(authRequestFromForm({ registering: true, login: 'a@example.test', password: 'secret' }).error).toBe(
      '登录用户名不能为空',
    );
    expect(
      authRequestFromForm({
        registering: true,
        username: 'logan',
        login: 'a@example.test',
        password: 'secret',
        confirmPassword: 'different',
      }).error,
    ).toBe('两次输入的密码不一致');
  });

  test('registerEmailValidationError validates normalized email format', () => {
    expect(registerEmailValidationError(' ')).toBe('邮箱不能为空');
    expect(registerEmailValidationError('logan.example.test')).toBe('请输入有效的邮箱地址');
    expect(registerEmailValidationError(' logan@example.test ')).toBeNull();
  });

  test('authRequestFromForm builds register request', () => {
    const result = authRequestFromForm({
      registering: true,
      username: ' logan ',
      login: ' a@example.test ',
      password: 'secret',
      confirmPassword: 'secret',
      emailVerificationToken: 'verification-token',
    });

    expect(result.error).toBeNull();
    expect(result.request?.registering).toBe(true);
    expect(result.request?.username).toBe('logan');
    expect(result.request?.login).toBe('a@example.test');
    expect(result.request?.emailVerificationToken).toBe('verification-token');
  });

  test('authRequestFromForm requires email verification before register', () => {
    const result = authRequestFromForm({
      registering: true,
      username: 'logan',
      login: 'a@example.test',
      password: 'secret',
      confirmPassword: 'secret',
    });

    expect(result.request).toBeNull();
    expect(result.error).toBe('请先验证邮箱');
  });

  test('auth submit state covers started invalid and generic failure', () => {
    const started = authSubmitStarted();

    expect(started.busy).toBe(true);
    expect(started.error).toBeNull();

    const invalid = authSubmitInvalid('Missing password');

    expect(invalid.busy).toBe(false);
    expect(invalid.error).toBe('Missing password');

    const failed = authSubmitFailed(new Error('offline'));

    expect(failed.busy).toBe(false);
    expect(failed.error).toBe('无法连接服务器');
  });

  test('auth submit failure gives a concise TLS recovery message', () => {
    const failed = authSubmitFailed(new HandshakeException('Handshake error in client'));

    expect(failed.error).toBe('无法建立安全连接，请检查网络、代理或系统时间后重试');
  });

  test('auth submit failure localizes auth exception codes', () => {
    const failed = authSubmitFailed(
      new AuthException('Invalid credentials', { statusCode: 401, code: 'invalid_credentials' }),
    );
    const english = authSubmitFailed(new AuthException('invalid credentials', { statusCode: 401, code: 'unauthorized' }), {
      language: 'en',
    });
    const traditional = authSubmitFailed(
      new AuthException('too many failed login attempts', { statusCode: 429, code: 'rate_limited' }),
      { language: 'zh-Hant' },
    );
    const requestFailed = authSubmitFailed(new AuthException('请求失败 (502)', { statusCode: 502, code: 'request_failed' }));

    expect(failed.busy).toBe(false);
    expect(failed.error).toBe('账号或密码不正确');
    expect(english.error).toBe('Incorrect account or password');
    expect(traditional.error).toBe('登入嘗試次數過多，請稍後再試');
    expect(requestFailed.error).toBe('请求失败 (502)');
  });
});
