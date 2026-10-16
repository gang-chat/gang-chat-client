import type { PasswordResetController } from '@gang-chat/core/app/password_reset_controller';
import { AuthException, type PasswordResetChallenge } from '@gang-chat/core/auth/auth_client';
import { PeriodicTimer, Timer } from '@gang-chat/core/lib/async';

import { Column, SizedBox, Text } from '../lib/layout';
import { SystemMouseCursors } from '../lib/mouse_cursors';
import { CircularProgressIndicator } from '../lib/progress_indicator';
import {
  FilteringTextInputFormatter,
  LengthLimitingTextInputFormatter,
  TextEditingController,
  TextInputAction,
  TextInputType,
} from '../lib/text_editing';
import { useViewState, ViewState } from '../lib/view_state';
import { ButtonTone } from '../ui/button';
import { DialogFrame, ResponsiveDialogAction, showFloatingErrorNotice, showFloatingSuccessNotice } from '../ui/feedback';
import { Icon, Icons } from '../ui/icon';
import { Input } from '../ui/input';
import { showDialog } from '../ui/overlay';
import { UiTooltip } from '../ui/tooltip';
import { UiColors, UiTypography } from '../ui/tokens';

/** PORT: the `BuildContext` argument is dropped; dialogs open in the app's OverlayHost. */
export async function showPasswordResetFlow(opts: { login: string; controller: PasswordResetController }): Promise<boolean> {
  const { controller } = opts;
  const normalizedLogin = opts.login.trim();
  if (normalizedLogin.length === 0) {
    showFloatingErrorNotice('请先输入用户名或邮箱');
    return false;
  }

  const resetToken = await verifyPasswordResetEmail({ login: normalizedLogin, controller });
  if (resetToken == null) return false;

  const completed = await showDialog<boolean>(
    (close) => <_ResetPasswordDialog resetToken={resetToken} controller={controller} close={close} />,
    { barrierDismissible: false },
  );
  if (completed === true) {
    showFloatingSuccessNotice('密码已重置，请使用新密码登录');
    return true;
  }
  return false;
}

export async function verifyPasswordResetEmail(opts: { login: string; controller: PasswordResetController }): Promise<string | null> {
  const { controller } = opts;
  const normalizedLogin = opts.login.trim();
  if (normalizedLogin.length === 0) {
    showFloatingErrorNotice('请先输入用户名或邮箱');
    return null;
  }
  let challenge: PasswordResetChallenge | null;
  let maskedEmail: string;
  let retryAfterSeconds: number;
  try {
    const inspection = await controller.inspect(normalizedLogin);
    challenge = inspection.reusableChallenge;
    if (challenge == null && inspection.canSend) {
      challenge = await controller.start(normalizedLogin);
    }
    maskedEmail = challenge?.maskedEmail ?? inspection.maskedEmail;
    retryAfterSeconds = challenge?.retryAfterSeconds ?? inspection.retryAfterSeconds;
  } catch (error) {
    showFloatingErrorNotice(_passwordResetErrorMessage(error));
    return null;
  }
  const token = await showDialog<string>(
    (close) => (
      <_PasswordResetVerificationDialog
        login={normalizedLogin}
        challenge={challenge}
        maskedEmail={maskedEmail}
        retryAfterSeconds={retryAfterSeconds}
        controller={controller}
        close={close}
      />
    ),
    { barrierDismissible: false },
  );
  return token ?? null;
}

function _passwordResetErrorMessage(error: unknown): string {
  if (error instanceof AuthException) return error.message;
  return '暂时无法完成密码重置，请稍后重试';
}

// ---------------------------------------------------------------------------

interface _PasswordResetVerificationDialogProps {
  login: string;
  challenge: PasswordResetChallenge | null;
  maskedEmail: string;
  retryAfterSeconds: number;
  controller: PasswordResetController;
  /** `Navigator.of(context).pop(result)`. */
  close: (result?: string) => void;
}

const _codeInputFormatters = [FilteringTextInputFormatter.digitsOnly, new LengthLimitingTextInputFormatter(6)];

class _PasswordResetVerificationDialogState extends ViewState<_PasswordResetVerificationDialogProps> {
  readonly _code = new TextEditingController();
  _timer: PeriodicTimer | null = null;
  _challenge: PasswordResetChallenge | null = null;
  _maskedEmail = '';
  _resendAvailableAt = new Date();
  _remainingSeconds = 0;
  _resending = false;
  _verifying = false;

  override initState(): void {
    const challenge = this.widget.challenge;
    this._challenge = challenge;
    this._maskedEmail = challenge?.maskedEmail ?? this.widget.maskedEmail;
    this._applyCooldown(challenge?.retryAfterSeconds ?? this.widget.retryAfterSeconds);
    // PORT: Flutter's initState ran before the first build; ViewState's runs
    // in a layout effect after the first commit, so rebuild (before paint)
    // with the resolved fields.
    this.setState();
  }

  _applyChallenge(challenge: PasswordResetChallenge): void {
    this._challenge = challenge;
    this._maskedEmail = challenge.maskedEmail;
    this._applyCooldown(challenge.retryAfterSeconds);
  }

  _applyCooldown(retryAfterSeconds: number): void {
    const seconds = retryAfterSeconds < 0 ? 0 : retryAfterSeconds;
    this._resendAvailableAt = new Date(Date.now() + seconds * 1000);
    this._remainingSeconds = seconds;
    this._startTimer();
  }

  _startTimer(): void {
    this._timer?.cancel();
    if (this._remainingSeconds <= 0) return;
    this._timer = Timer.periodic(1000, () => {
      const remaining = this._resendAvailableAt.getTime() - Date.now();
      const seconds = remaining <= 0 ? 0 : Math.ceil(remaining / 1000);
      if (!this.mounted) return;
      if (seconds === 0) this._timer?.cancel();
      if (this._remainingSeconds !== seconds) {
        this.setState(() => {
          this._remainingSeconds = seconds;
        });
      }
    });
  }

  _resend = async (): Promise<void> => {
    if (this._resending || this._verifying || this._remainingSeconds > 0) return;
    this.setState(() => {
      this._resending = true;
    });
    try {
      const currentChallenge = this._challenge;
      const challenge =
        currentChallenge == null
          ? await this.widget.controller.start(this.widget.login)
          : await this.widget.controller.resend(currentChallenge.id);
      if (!this.mounted) return;
      this.setState(() => {
        this._resending = false;
        this._applyChallenge(challenge);
      });
    } catch (error) {
      if (!this.mounted) return;
      this.setState(() => {
        this._resending = false;
      });
      showFloatingErrorNotice(_passwordResetErrorMessage(error));
    }
  };

  _verify = async (): Promise<void> => {
    if (this._verifying || this._resending) return;
    const challenge = this._challenge;
    if (challenge == null) {
      showFloatingErrorNotice('请先发送验证码');
      return;
    }
    const code = this._code.text.trim();
    if (!/^\d{6}$/.test(code)) {
      showFloatingErrorNotice('请输入 6 位数字验证码');
      return;
    }
    this.setState(() => {
      this._verifying = true;
    });
    try {
      const token = await this.widget.controller.verify({ challengeId: challenge.id, code });
      if (!this.mounted) return;
      this.widget.close(token);
    } catch (error) {
      if (!this.mounted) return;
      this.setState(() => {
        this._verifying = false;
      });
      showFloatingErrorNotice(_passwordResetErrorMessage(error));
    }
  };

  override dispose(): void {
    this._timer?.cancel();
    this._code.dispose();
  }
}

function _PasswordResetVerificationDialog(props: _PasswordResetVerificationDialogProps) {
  const state = useViewState(() => new _PasswordResetVerificationDialogState(), props);
  const maskedEmail = state._maskedEmail;
  const remainingSeconds = state._remainingSeconds;
  const challenge = state._challenge;
  return (
    <DialogFrame
      title="邮箱验证"
      icon={Icons.mark_email_read_outlined}
      maxWidth={420}
      adaptiveActions={[
        new ResponsiveDialogAction({ label: '取消', onPressed: state._verifying ? null : () => props.close() }),
        new ResponsiveDialogAction({
          label: '验证',
          buttonKey: 'password-reset-verify-button',
          onPressed: state._verifying || state._resending || challenge == null ? null : () => void state._verify(),
          loading: state._verifying,
          tone: ButtonTone.primary,
        }),
      ]}
    >
      <Column mainAxisSize="min" crossAxisAlignment="stretch">
        <Text data-testid="password-reset-email-message" style={{ ...UiTypography.body, color: UiColors.textSecondary }}>
          已发送验证码到该账号绑定的邮箱 {maskedEmail}
        </Text>
        <SizedBox height={14} />
        <Input
          controller={state._code}
          hintText="请输入验证码"
          prefixIcon={Icons.password_outlined}
          suffix={
            <_InlineAction
              actionKey="password-reset-resend-button"
              label={remainingSeconds > 0 ? `重新发送(${remainingSeconds})` : '发送验证码'}
              enabled={!state._resending && !state._verifying && remainingSeconds <= 0}
              loading={state._resending}
              onPressed={() => void state._resend()}
            />
          }
          keyboardType={TextInputType.number}
          textInputAction={TextInputAction.done}
          inputFormatters={_codeInputFormatters}
          maxLines={1}
          onSubmitted={() => void state._verify()}
        />
      </Column>
    </DialogFrame>
  );
}

// ---------------------------------------------------------------------------

interface _ResetPasswordDialogProps {
  resetToken: string;
  controller: PasswordResetController;
  close: (result?: boolean) => void;
}

class _ResetPasswordDialogState extends ViewState<_ResetPasswordDialogProps> {
  readonly _password = new TextEditingController();
  readonly _confirmation = new TextEditingController();
  _obscurePassword = true;
  _obscureConfirmation = true;
  _submitting = false;

  _submit = async (): Promise<void> => {
    if (this._submitting) return;
    const password = this._password.text;
    if (password.length === 0 || this._confirmation.text.length === 0) {
      showFloatingErrorNotice('请完整填写新密码和确认密码');
      return;
    }
    if (password.length < 8) {
      showFloatingErrorNotice('新密码至少需要 8 个字符');
      return;
    }
    if (password !== this._confirmation.text) {
      showFloatingErrorNotice('两次输入的新密码不一致');
      return;
    }
    this.setState(() => {
      this._submitting = true;
    });
    try {
      await this.widget.controller.complete({ resetToken: this.widget.resetToken, newPassword: password });
      if (!this.mounted) return;
      this.widget.close(true);
    } catch (error) {
      if (!this.mounted) return;
      this.setState(() => {
        this._submitting = false;
      });
      showFloatingErrorNotice(_passwordResetErrorMessage(error));
    }
  };

  override dispose(): void {
    this._password.dispose();
    this._confirmation.dispose();
  }
}

function _ResetPasswordDialog(props: _ResetPasswordDialogProps) {
  const state = useViewState(() => new _ResetPasswordDialogState(), props);
  return (
    <DialogFrame
      title="重置密码"
      icon={Icons.lock_reset_outlined}
      maxWidth={420}
      adaptiveActions={[
        new ResponsiveDialogAction({ label: '取消', onPressed: state._submitting ? null : () => props.close() }),
        new ResponsiveDialogAction({
          label: '重置密码',
          buttonKey: 'password-reset-submit-button',
          onPressed: state._submitting ? null : () => void state._submit(),
          loading: state._submitting,
          tone: ButtonTone.primary,
        }),
      ]}
    >
      <Column mainAxisSize="min" crossAxisAlignment="stretch">
        <Input
          controller={state._password}
          hintText="新密码"
          prefixIcon={Icons.lock_outline}
          obscureText={state._obscurePassword}
          suffix={
            <_PasswordVisibilityAction
              obscure={state._obscurePassword}
              onPressed={() =>
                state.setState(() => {
                  state._obscurePassword = !state._obscurePassword;
                })
              }
            />
          }
          maxLines={1}
        />
        <SizedBox height={10} />
        <Input
          controller={state._confirmation}
          hintText="确认密码"
          prefixIcon={Icons.lock_outline}
          obscureText={state._obscureConfirmation}
          suffix={
            <_PasswordVisibilityAction
              obscure={state._obscureConfirmation}
              onPressed={() =>
                state.setState(() => {
                  state._obscureConfirmation = !state._obscureConfirmation;
                })
              }
            />
          }
          maxLines={1}
          textInputAction={TextInputAction.done}
          onSubmitted={() => void state._submit()}
        />
      </Column>
    </DialogFrame>
  );
}

// ---------------------------------------------------------------------------

function _InlineAction({
  actionKey,
  label,
  enabled,
  loading,
  onPressed,
}: {
  actionKey: string;
  label: string;
  enabled: boolean;
  loading: boolean;
  onPressed: () => void;
}) {
  return (
    <div
      role="button"
      aria-disabled={!enabled}
      data-testid={actionKey}
      // A tap on an input's suffix keeps the field's focus (TextFieldTapRegion).
      onMouseDown={(event) => event.preventDefault()}
      onClick={(event) => {
        if (event.button !== 0 || !enabled) return;
        onPressed();
      }}
      style={{
        cursor: enabled ? SystemMouseCursors.click : SystemMouseCursors.basic,
        paddingLeft: 4,
        paddingRight: 4,
        paddingTop: 6,
        paddingBottom: 6,
        flexShrink: 0,
      }}
    >
      {loading ? (
        <CircularProgressIndicator size={14} strokeWidth={1.6} />
      ) : (
        <Text style={{ ...UiTypography.label, color: enabled ? UiColors.accent : UiColors.textMuted, whiteSpace: 'nowrap' }}>{label}</Text>
      )}
    </div>
  );
}

function _PasswordVisibilityAction({ obscure, onPressed }: { obscure: boolean; onPressed: () => void }) {
  return (
    <UiTooltip message={obscure ? '显示密码' : '隐藏密码'}>
      <div
        role="button"
        aria-label={obscure ? '显示密码' : '隐藏密码'}
        onMouseDown={(event) => event.preventDefault()}
        onClick={(event) => {
          if (event.button !== 0) return;
          onPressed();
        }}
        style={{ width: 24, height: 24, flexShrink: 0, display: 'flex', alignItems: 'center', justifyContent: 'center' }}
      >
        <Icon icon={obscure ? Icons.visibility_off_outlined : Icons.visibility_outlined} size={17} color={UiColors.textSecondary} />
      </div>
    </UiTooltip>
  );
}
