import { Text, View } from 'react-native';

import { Icon, Icons } from '@gang-chat/ui/ui/icon';
import { OverlayHost } from '@gang-chat/ui/ui/overlay';
import { UiColors, UiTypography } from '@gang-chat/ui/ui/tokens';
import { platform } from '@gang-chat/core/platform/platform';

/** Temporary root used to validate the Electron + react-native-web pipeline. */
export function DesktopRoot() {
  return (
    <OverlayHost>
      <View style={{ flex: 1, backgroundColor: UiColors.background, alignItems: 'center', justifyContent: 'center', gap: 12 }}>
        <Text style={UiTypography.display}>Gang Chat</Text>
        <Text style={UiTypography.dot}>MUSIC BOX 42</Text>
        <Text style={UiTypography.overline}>● LIVE · 3</Text>
        <View style={{ flexDirection: 'row', gap: 8 }}>
          <Icon icon={Icons.mic_off} color={UiColors.accent} />
          <Icon icon={Icons.headphones} />
          <Icon icon={Icons.settings_outlined} />
        </View>
        <Text style={UiTypography.body}>中文正文 · {platform().os} · v{platform().app.version}</Text>
      </View>
    </OverlayHost>
  );
}
