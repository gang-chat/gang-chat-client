/**
 * Props react-native-web understands that React Native's types do not.
 * Native platforms ignore them.
 */
import 'react-native';

declare module 'react-native' {
  interface ViewProps {
    /** Rendered as `data-*` attributes on the web (e.g. `{ gangDrag: 'true' }` → `data-gang-drag`). */
    dataSet?: Record<string, string>;
  }
  interface TextProps {
    dataSet?: Record<string, string>;
  }
  interface PressableProps {
    dataSet?: Record<string, string>;
  }
  interface TextInputProps {
    dataSet?: Record<string, string>;
  }
  interface ImageProps {
    dataSet?: Record<string, string>;
    /** Web: `draggable` attribute on the underlying image. */
    draggable?: boolean;
  }
}
