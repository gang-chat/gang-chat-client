import type { EmailVerificationController } from '@gang-chat/core/app/email_verification_controller';
import { AuthException, type EmailVerificationChallenge } from '@gang-chat/core/auth/auth_client';
import { PeriodicTimer, Timer } from '@gang-chat/core/lib/async';

import { Column, SizedBox, Text } from '../lib/layout';
import { SystemMouseCursors } from '../lib/mouse_cursors';
import { TextEditingController, TextInputAction, TextInputType } from '../lib/text_editing';
import { useViewState, ViewState } from '../lib/view_state';
import { ButtonTone } from '../ui/button';
import { DialogFrame, ResponsiveDialogAction, showFloatingErrorNotice } from '../ui/feedback';
import { Icons } from '../ui/icon';
import { Input } from '../ui/input';
import { showDialog } from '../ui/overlay';
import { UiColors, UiTypography } from '../ui/tokens';

/** PORT: the `BuildContext` argument is dropped; the dialog opens in the app's OverlayHost. */
export async function showEmailVerificationDialog(opts: {
  email: string;
  challenge: EmailVerificationChallenge;
  controller: EmailVerificationController;
}): Promise<string | null> {
  const { email, challenge, controller } = opts;
  const result = await showDialog<string>((close) => (
    <_EmailVerificationDialog email={email} challenge={challenge} controller={controller} close={close} />
  ));
  return result ?? null;
}

export function emailVerificationErrorMessage(error: unknown): string {
  if (error instanceof AuthException) return error.message;
  return '邮箱验证失败，请稍后重试';
}

export interface EmailVerificationInputActionProps {
  /** Test id of the tappable area (Dart `actionKey`). */
  actionKey?: string | null;
  label: string;
  semanticsLabel: string;
  enabled: boolean;
  onPressed: () => void;
}

export function EmailVerificationInputAction({ actionKey, label, semanticsLabel, enabled, onPressed }: EmailVerificationInputActionProps) {
  return (
    <div
      role="button"
      aria-label={semanticsLabel}
      aria-disabled={!enabled}
      data-testid={actionKey ?? undefined}
      // A tap on an input's suffix keeps the field's focus, like Flutter's
      // TextFieldTapRegion.
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
      <Text style={{ ...UiTypography.label, color: enabled ? UiColors.accent : UiColors.textMuted, whiteSpace: 'nowrap' }}>{label}</Text>
    </div>
  );
}

interface _EmailVerificationDialogProps {
  email: string;
  challenge: EmailVerificationChallenge;
  controller: EmailVerificationController;
  /** `Navigator.of(context).pop(result)`. */
  close: (result?: string) => void;
}

class _EmailVerificationDialogState extends ViewState<_EmailVerificationDialogProps> {
  readonly _code = new TextEditingController();
  _cooldownTimer: PeriodicTimer | null = null;
  _challenge!: EmailVerificationChallenge;
  _resendAvailableAt!: Date;
  _remainingSeconds = 0;
  _sending = false;
  _verifying = false;

  override initState(): void {
    this._code.addListener(this._codeChanged);
    this._applyChallenge(this.widget.challenge);
    this._startCooldownTimer();
    // PORT: Flutter's initState ran before the first build; ViewState's runs
    // in a layout effect after the first commit, so rebuild (before paint)
    // with the resolved fields.
    this.setState();
  }

  _codeChanged = (): void => {
    if (this.mounted) this.setState(() => {});
  };

  _applyChallenge(challenge: EmailVerificationChallenge): void {
    this._challenge = challenge;
    this._remainingSeconds = challenge.retryAfterSeconds;
    this._resendAvailableAt = new Date(Date.now() + challenge.retryAfterSeconds * 1000);
  }

  _currentRemainingSeconds(): number {
    const milliseconds = this._resendAvailableAt.getTime() - Date.now();
    if (milliseconds <= 0) return 0;
    return Math.ceil(milliseconds / 1000);
  }

  _sendVerificationCode = async (): Promise<void> => {
    if (this._remainingSeconds > 0 || this._sending || this._verifying) return;
    this.setState(() => {
      this._sending = true;
    });
    try {
      const challenge = await this.widget.controller.resend(this._challenge.id);
      if (!this.mounted) return;
      this.setState(() => {
        this._sending = false;
        this._applyChallenge(challenge);
      });
      this._startCooldownTimer();
    } catch (error) {
      if (!this.mounted) return;
      this.setState(() => {
        this._sending = false;
      });
      showFloatingErrorNotice(emailVerificationErrorMessage(error));
    }
  };

  _verify = async (): Promise<void> => {
    if (this._verifying || this._sending) return;
    const code = this._code.text.trim();
    if (!/^\d{6}$/.test(code)) {
      showFloatingErrorNotice('请输入 6 位数字验证码');
      return;
    }
    this.setState(() => {
      this._verifying = true;
    });
    try {
      const token = await this.widget.controller.verify({ challengeId: this._challenge.id, code });
      if (!this.mounted) return;
      this.widget.close(token);
    } catch (error) {
      if (!this.mounted) return;
      this.setState(() => {
        this._verifying = false;
      });
      showFloatingErrorNotice(emailVerificationErrorMessage(error));
    }
  };

  _startCooldownTimer(): void {
    this._cooldownTimer?.cancel();
    if (this._remainingSeconds <= 0) return;
    this._cooldownTimer = Timer.periodic(1000, () => {
      const remaining = this._currentRemainingSeconds();
      if (!this.mounted) return;
      if (remaining <= 0) this._cooldownTimer?.cancel();
      if (remaining !== this._remainingSeconds) {
        this.setState(() => {
          this._remainingSeconds = remaining;
        });
      }
    });
  }

  override dispose(): void {
    this._cooldownTimer?.cancel();
    this._code.removeListener(this._codeChanged);
    this._code.dispose();
  }
}

function _EmailVerificationDialog(props: _EmailVerificationDialogProps) {
  const state = useViewState(() => new _EmailVerificationDialogState(), props);
  const remainingSeconds = state._remainingSeconds;
  return (
    <DialogFrame
      title="邮箱验证"
      icon={Icons.mark_email_read_outlined}
      maxWidth={420}
      adaptiveActions={[
        new ResponsiveDialogAction({ label: '取消', onPressed: () => props.close() }),
        new ResponsiveDialogAction({
          label: '验证',
          onPressed: state._verifying ? null : () => void state._verify(),
          tone: ButtonTone.primary,
          loading: state._verifying,
        }),
      ]}
    >
      <Column mainAxisSize="min" crossAxisAlignment="stretch">
        <Text style={{ ...UiTypography.body, color: UiColors.textSecondary }}>已发送验证码到您的邮箱 {props.email}</Text>
        <SizedBox height={14} />
        <Input
          controller={state._code}
          hintText="请输入验证码"
          prefixIcon={Icons.password_outlined}
          suffix={
            <EmailVerificationInputAction
              actionKey="auth-email-send-code-button"
              label={remainingSeconds > 0 ? `重新发送(${remainingSeconds})` : state._sending ? '发送中' : '发送验证码'}
              semanticsLabel={remainingSeconds > 0 ? '验证码发送冷却中' : '发送验证码'}
              enabled={remainingSeconds <= 0 && !state._sending && !state._verifying}
              onPressed={() => void state._sendVerificationCode()}
            />
          }
          keyboardType={TextInputType.number}
          textInputAction={TextInputAction.done}
          onSubmitted={() => void state._verify()}
          maxLines={1}
        />
      </Column>
    </DialogFrame>
  );
}
