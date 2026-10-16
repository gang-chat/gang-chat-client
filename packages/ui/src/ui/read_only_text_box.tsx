import { useEffect, useLayoutEffect, useRef, type CSSProperties } from 'react';

import { EdgeInsets, type EdgeInsetsValue } from '../lib/edge_insets';
import { FocusNode, TextEditingController, TextEditingValue, TextSelection, UndoHistoryController } from '../lib/text_editing';
import { _EditableTextField } from './input';
import { buildTextFieldContextMenu } from './text_context_menu';
import { UiColors, UiRadii, UiTypography } from './tokens';

export interface ReadOnlySelectableTextProps {
  value: string;
  style: CSSProperties;
  fieldKey?: string | null;
  maxLines?: number;
  textAlign?: CSSProperties['textAlign'];
  secondaryClickSelection?: TextSelection | null;
  showSelectAllInContextMenu?: boolean;
  contextMenuTapRegionGroupId?: unknown;
  onContextMenuOpenChanged?: ((open: boolean) => void) | null;
}

/** Keeps a read-only controller in sync with [value], preserving the selection. */
function useReadOnlyControllers(value: string) {
  const state = useRef<{ controller: TextEditingController; focusNode: FocusNode; undo: UndoHistoryController } | null>(null);
  if (state.current == null) {
    state.current = {
      controller: new TextEditingController({ text: value }),
      focusNode: new FocusNode(),
      undo: new UndoHistoryController(),
    };
  }
  const { controller } = state.current;
  useLayoutEffect(() => {
    if (controller.text === value) return;
    // didUpdateWidget: swap the text but keep the (clamped) selection.
    controller.value = new TextEditingValue({
      text: value,
      selection: _clampTextSelection(controller.selection, value.length),
    });
  }, [controller, value]);
  useEffect(
    () => () => {
      const s = state.current;
      if (s == null) return;
      s.controller.dispose();
      s.focusNode.dispose();
      s.undo.dispose();
    },
    [],
  );
  return state.current;
}

export function ReadOnlySelectableText({
  value,
  style,
  fieldKey,
  maxLines = 1,
  textAlign = 'start',
  secondaryClickSelection,
  showSelectAllInContextMenu = true,
  contextMenuTapRegionGroupId,
  onContextMenuOpenChanged,
}: ReadOnlySelectableTextProps) {
  const { controller, focusNode, undo } = useReadOnlyControllers(value);
  return (
    <div data-testid={fieldKey ?? undefined} style={{ minWidth: 0 }}>
      <_EditableTextField
        controller={controller}
        focusNode={focusNode}
        undoController={undo}
        readOnly
        showCursor={false}
        enableInteractiveSelection
        minLines={1}
        maxLines={maxLines}
        textAlign={textAlign}
        style={style}
        padding={0}
        secondaryClickSelection={
          secondaryClickSelection == null ? null : () => _clampTextSelection(secondaryClickSelection, value.length)
        }
        contextMenuBuilder={(state) =>
          buildTextFieldContextMenu(state, {
            readOnly: true,
            showReadOnlySelectAll: showSelectAllInContextMenu,
            tapRegionGroupId: contextMenuTapRegionGroupId,
            onOpenChanged: onContextMenuOpenChanged,
          })
        }
      />
    </div>
  );
}

export interface ReadOnlyTextBoxProps {
  value: string;
  fieldKey?: string | null;
  /** `null` grows without limit. */
  maxLines?: number | null;
  style?: CSSProperties | null;
  backgroundColor?: string;
  borderColor?: string;
  padding?: EdgeInsetsValue;
}

export function ReadOnlyTextBox({
  value,
  fieldKey,
  maxLines = 1,
  style,
  backgroundColor = UiColors.background,
  borderColor = UiColors.border,
  padding = EdgeInsets.fromLTRB(13, 11, 13, 11),
}: ReadOnlyTextBoxProps) {
  const { controller, focusNode, undo } = useReadOnlyControllers(value);
  const resolvedStyle = style ?? { ...UiTypography.body, color: UiColors.textSecondary, fontWeight: 500 };
  return (
    <div
      data-testid={fieldKey ?? undefined}
      style={{
        minWidth: 0,
        backgroundColor,
        borderRadius: UiRadii.md,
        // A DecoratedBox border paints over the content without taking space.
        boxShadow: `inset 0 0 0 1px ${borderColor}`,
      }}
    >
      <_EditableTextField
        controller={controller}
        focusNode={focusNode}
        undoController={undo}
        readOnly
        showCursor={false}
        enableInteractiveSelection
        minLines={1}
        maxLines={maxLines}
        style={resolvedStyle}
        padding={padding}
        contextMenuBuilder={(state) => buildTextFieldContextMenu(state, { readOnly: true })}
      />
    </div>
  );
}

export function _clampTextSelection(selection: TextSelection | null | undefined, length: number): TextSelection {
  if (selection == null || !selection.isValid) {
    return TextSelection.collapsed({ offset: length });
  }
  return new TextSelection({
    baseOffset: Math.min(selection.baseOffset, length),
    extentOffset: Math.min(selection.extentOffset, length),
    affinity: selection.affinity,
    isDirectional: selection.isDirectional,
  });
}
