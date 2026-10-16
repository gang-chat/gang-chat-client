import type { CSSProperties, ReactNode } from 'react';

import { Text } from '../lib/layout';
import { FocusNode, TextEditingController, UndoHistoryController } from '../lib/text_editing';
import { Icon, type IconData } from './icon';
import { _EditableTextField, _useOwned } from './input';
import { buildTextFieldContextMenu } from './text_context_menu';
import { UiColors, UiRadii, UiTypography } from './tokens';

export interface TextInputProps {
  controller?: TextEditingController | null;
  label?: string | null;
  hint?: string | null;
  prefixIcon?: IconData | null;
  suffix?: ReactNode;
  obscureText?: boolean;
  minLines?: number;
  maxLines?: number;
  onSubmitted?: ((value: string) => void) | null;
  undoController?: UndoHistoryController | null;
  /** Outer box style (DOM addition). */
  style?: CSSProperties;
}

function disposeNotifier(value: { dispose(): void }): void {
  value.dispose();
}

/** Labelled text field (Dart `TextInput`; also exported as [UiTextInput]). */
export function TextInput({
  controller: controllerProp,
  label,
  hint,
  prefixIcon,
  suffix,
  obscureText = false,
  minLines = 1,
  maxLines = 1,
  onSubmitted,
  undoController: undoProp,
  style,
}: TextInputProps) {
  const controller = _useOwned(controllerProp, () => new TextEditingController(), disposeNotifier);
  const undoController = _useOwned(undoProp, () => new UndoHistoryController(), disposeNotifier);
  const focusNode = _useOwned<FocusNode>(null, () => new FocusNode(), disposeNotifier);

  const field = (
    <div
      style={{
        display: 'flex',
        flexDirection: 'row',
        alignItems: maxLines > 1 ? 'flex-start' : 'center',
        minWidth: 0,
        backgroundColor: UiColors.fieldFill,
        borderRadius: UiRadii.md,
        // A DecoratedBox border paints over the content without taking space.
        boxShadow: `inset 0 0 0 1px ${UiColors.fieldBorder}`,
      }}
    >
      {prefixIcon != null ? (
        <div style={{ paddingLeft: 12, paddingTop: 12, flexShrink: 0, display: 'flex' }}>
          <Icon icon={prefixIcon} size={18} color={UiColors.textMuted} />
        </div>
      ) : null}
      <div style={{ flex: '1 1 0', minWidth: 0 }}>
        <_EditableTextField
          controller={controller}
          focusNode={focusNode}
          undoController={undoController}
          obscureText={obscureText}
          minLines={minLines}
          maxLines={maxLines}
          onSubmitted={onSubmitted}
          style={UiTypography.body}
          hintText={hint}
          hintStyle={{ color: UiColors.textMuted }}
          padding={12}
          aria-label={label ?? hint ?? undefined}
          contextMenuBuilder={(state) =>
            buildTextFieldContextMenu(state, { undoController, allowCut: !obscureText, allowCopy: !obscureText })
          }
        />
      </div>
      {suffix != null ? <div style={{ paddingRight: 6, flexShrink: 0, display: 'flex' }}>{suffix}</div> : null}
    </div>
  );

  if (label == null) return <div style={{ minWidth: 0, ...style }}>{field}</div>;
  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'stretch', minWidth: 0, ...style }}>
      <Text style={UiTypography.label}>{label}</Text>
      <div style={{ height: 7 }} />
      {field}
    </div>
  );
}

/** The conventions doc's name for [TextInput]. */
export const UiTextInput = TextInput;
