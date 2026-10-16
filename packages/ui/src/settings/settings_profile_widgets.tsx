// Port of `lib/src/settings/settings_profile_widgets.dart` (a `part of
// 'settings_page.dart'`): the account-deletion dialog and the small field
// adornments of the profile / security forms. Part-private widgets are
// exported with their leading underscore (see docs/js-port-conventions.md).

import * as account_display from '@gang-chat/core/app/account_display';
import { matchesConfirmationText } from '@gang-chat/core/app/confirmation';

import { Center, Column, Row, SizedBox, Text } from '../lib/layout';
import { FocusNode, TextEditingController, UndoHistoryController } from '../lib/text_editing';
import { useListenable } from '../lib/use_listenable';
import { ViewState, useViewState } from '../lib/view_state';
import { ButtonTone } from '../ui/button';
import { ResponsiveDialogAction, ResponsiveDialogActionBar } from '../ui/feedback';
import { Icon, Icons } from '../ui/icon';
import { _EditableTextField } from '../ui/input';
import { useRoute } from '../ui/overlay';
import { buildTextFieldContextMenu, type EditableTextState } from '../ui/text_context_menu';
import { UiTooltip } from '../ui/tooltip';
import { UiColors, UiRadii, kClientFontStack } from '../ui/tokens';
import { _cyan, _danger, _primaryDarkLow, _textMuted, _textPrimary, _textSecondary } from './settings_page';

// ---------------------------------------------------------------------------
// _DeleteAccountDialog

export interface _DeleteAccountDialogProps {
  spec: account_display.AccountDeletionConfirmationSpec;
}

class _DeleteAccountDialogState extends ViewState<_DeleteAccountDialogProps> {
  readonly _controller = new TextEditingController();
  readonly _focusNode = new FocusNode();
  readonly _undoController = new UndoHistoryController();

  override initState(): void {
    // TextField(autofocus: true). The node queues the request until the
    // field attaches.
    this._focusNode.requestFocus();
  }

  override dispose(): void {
    this._controller.dispose();
    this._focusNode.dispose();
    this._undoController.dispose();
  }

  _buildContextMenu = (editableTextState: EditableTextState) => {
    return buildTextFieldContextMenu(editableTextState, { undoController: this._undoController });
  };
}

/**
 * Asks the user to type the account's username before the account is
 * deleted. Pops `true` when confirmed, `false` when cancelled.
 */
export function _DeleteAccountDialog(props: _DeleteAccountDialogProps) {
  const state = useViewState(() => new _DeleteAccountDialogState(), props);
  const route = useRoute<boolean>();
  useListenable(state._focusNode);
  const spec = props.spec;
  const matches = matchesConfirmationText(state._controller.text, spec.expectedText);
  const focused = state._focusNode.hasFocus;
  // PORT: Material `TextField` with the default (Material 3) underline
  // decoration: bodyLarge text, onSurfaceVariant hint and underline (the
  // dark scheme falls back to onSurface), a 2px primary underline while
  // focused and dense 8px vertical content padding.
  const fieldTextStyle = {
    fontFamily: kClientFontStack,
    fontSize: 16,
    lineHeight: 1.5,
    letterSpacing: 0.5,
    color: _textPrimary,
    fontWeight: 500,
  } as const;
  return (
    // Dialog(backgroundColor, RoundedRectangleBorder(radius lg, side
    // dangerBorder)) > ConstrainedBox(maxWidth: 420) > Padding(22, 20, 22, 18).
    <Column
      crossAxisAlignment="stretch"
      mainAxisSize="min"
      style={{
        width: 420,
        minWidth: 280,
        maxWidth: 'calc(100vw - 80px)',
        maxHeight: 'calc(100vh - 48px)',
        margin: '0 16px',
        backgroundColor: _primaryDarkLow,
        borderRadius: UiRadii.lg,
        border: `1px solid ${UiColors.dangerBorder}`,
        // Material paints the shape's side over the child, so the content
        // inset stays 22 / 20 / 22 / 18 from the outer edge.
        padding: '19px 21px 17px',
        boxSizing: 'border-box',
      }}
    >
      <Text style={{ color: _danger, fontSize: 18, fontWeight: 600 }}>{spec.title}</Text>
      <SizedBox height={10} />
      <Text style={{ color: _textSecondary, fontSize: 13, lineHeight: 1.5 }}>{spec.body}</Text>
      <SizedBox height={14} />
      <div
        style={{
          boxShadow: `inset 0 ${focused ? -2 : -1}px 0 ${focused ? UiColors.accentStrong : UiColors.text}`,
        }}
      >
        <_EditableTextField
          controller={state._controller}
          focusNode={state._focusNode}
          undoController={state._undoController}
          onChanged={() => state.setState()}
          style={fieldTextStyle}
          hintText={spec.inputHint}
          hintStyle={{ color: UiColors.text }}
          padding={{ top: 8, bottom: 8 }}
          contextMenuBuilder={state._buildContextMenu}
          elementStyle={{ caretColor: _textSecondary }}
          aria-label={spec.inputHint}
        />
      </div>
      <SizedBox height={18} />
      <ResponsiveDialogActionBar
        actions={[
          new ResponsiveDialogAction({ label: '取消', onPressed: () => route.close(false) }),
          new ResponsiveDialogAction({
            label: spec.confirmLabel,
            icon: Icons.delete_outline,
            tone: ButtonTone.danger,
            onPressed: matches ? () => route.close(true) : null,
          }),
        ]}
      />
    </Column>
  );
}

// ---------------------------------------------------------------------------
// _PasswordVisibilityToggle

export interface _PasswordVisibilityToggleProps {
  obscure: boolean;
  enabled: boolean;
  onPressed: () => void;
}

export function _PasswordVisibilityToggle({ obscure, enabled, onPressed }: _PasswordVisibilityToggleProps) {
  const color = enabled ? _textSecondary : _textMuted;
  const label = obscure ? '显示密码' : '隐藏密码';
  return (
    <UiTooltip message={label}>
      {/* Semantics(button) + GestureDetector(opaque): no cursor of its own,
          so the text field's text cursor shows through, as in Flutter. */}
      <div
        role="button"
        aria-label={label}
        aria-disabled={!enabled}
        onClick={(event) => {
          if (event.button !== 0 || !enabled) return;
          onPressed();
        }}
        style={{
          width: 32,
          height: 22,
          flexShrink: 0,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <Icon icon={obscure ? Icons.visibility_off_outlined : Icons.visibility_outlined} size={17} color={color} />
      </div>
    </UiTooltip>
  );
}

// ---------------------------------------------------------------------------
// _UsernameValidityIndicator

export interface _UsernameValidityIndicatorProps {
  error: string | null;
  checking: boolean;
  enabled: boolean;
}

export function _UsernameValidityIndicator({ error, checking, enabled }: _UsernameValidityIndicatorProps) {
  const valid = error == null && !checking;
  const color = !enabled ? _textMuted : checking ? _textSecondary : valid ? _cyan : _danger;
  const label = checking ? '检测中' : valid ? '合法' : '不合法';
  const message = checking ? '正在检测登录用户名是否可用' : valid ? '登录用户名可用' : error!;
  const semanticsLabel = checking ? '正在检测登录用户名是否可用' : valid ? '登录用户名可用' : '登录用户名不合法';
  return (
    <UiTooltip message={message}>
      <Row mainAxisSize="min" aria-label={semanticsLabel}>
        <Icon
          icon={checking ? Icons.hourglass_empty_outlined : valid ? Icons.check_circle_outline : Icons.error_outline}
          size={16}
          color={color}
        />
        <SizedBox width={5} />
        <Text softWrap={false} style={{ color, fontSize: 12, fontWeight: 600 }}>
          {label}
        </Text>
      </Row>
    </UiTooltip>
  );
}

// ---------------------------------------------------------------------------
// _EmailVerificationStatusIndicator

export function _EmailVerificationStatusIndicator({ checking }: { checking: boolean }) {
  const message = checking ? '正在检测邮箱是否可用' : '邮箱已验证';
  return (
    <UiTooltip message={message}>
      <div
        data-testid={checking ? 'settings-email-checking' : 'settings-email-verified'}
        style={{ padding: '0 8px', display: 'flex', alignItems: 'center', flexShrink: 0 }}
      >
        <Icon
          icon={checking ? Icons.hourglass_empty_outlined : Icons.check_circle_outline}
          size={18}
          color={checking ? _textSecondary : UiColors.accent}
        />
      </div>
    </UiTooltip>
  );
}

// ---------------------------------------------------------------------------
// _FieldLabel

export function _FieldLabel({ text }: { text: string }) {
  return (
    <Text maxLines={1} style={{ color: _textMuted, fontSize: 12, fontWeight: 600 }}>
      {text}
    </Text>
  );
}

// ---------------------------------------------------------------------------
// _SettingsEmptyState

export function _SettingsEmptyState({ text }: { text: string }) {
  return (
    // SizedBox(height: 74) > Center: the Center takes the full width of
    // whatever column it sits in, so the box stretches across.
    <SizedBox height={74} style={{ alignSelf: 'stretch' }}>
      <Center style={{ height: '100%' }}>
        <Text textAlign="center" style={{ color: _textMuted, fontSize: 13 }}>
          {text}
        </Text>
      </Center>
    </SizedBox>
  );
}
