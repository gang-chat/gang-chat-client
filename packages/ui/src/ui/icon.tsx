import { Text, type StyleProp, type TextStyle } from 'react-native';

import type { IconData } from './icons';
import { UiColors } from './tokens';

export { Icons, type IconData, type IconName } from './icons';

export interface IconProps {
  icon: IconData;
  size?: number;
  color?: string;
  style?: StyleProp<TextStyle>;
  /** Accessibility label (Flutter `semanticLabel`). */
  semanticLabel?: string;
}

/**
 * Port of Flutter's `Icon` widget: draws one glyph from the Material Icons
 * font named by [icon]. Size defaults to 24 like `IconTheme`.
 */
export function Icon({ icon, size = 24, color = UiColors.text, style, semanticLabel }: IconProps) {
  return (
    <Text
      accessibilityLabel={semanticLabel}
      accessible={semanticLabel != null}
      selectable={false}
      style={[
        {
          fontFamily: icon.fontFamily,
          fontSize: size,
          lineHeight: size,
          width: size,
          height: size,
          color,
          textAlign: 'center',
          fontWeight: 'normal',
          fontStyle: 'normal',
          includeFontPadding: false,
        },
        style,
      ]}
    >
      {String.fromCodePoint(icon.codePoint)}
    </Text>
  );
}
