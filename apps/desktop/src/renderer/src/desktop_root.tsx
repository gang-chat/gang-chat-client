import { platform } from '@gang-chat/core/platform/platform';
import { Column, Row, Text } from '@gang-chat/ui/lib/layout';
import { Icon, Icons } from '@gang-chat/ui/ui/icon';
import { OverlayHost } from '@gang-chat/ui/ui/overlay';
import { UiColors, UiTypography } from '@gang-chat/ui/ui/tokens';

/** Temporary root used to validate the Electron + React pipeline. */
export function DesktopRoot() {
  return (
    <OverlayHost>
      <Column mainAxisAlignment="center" gap={12} style={{ flex: 1, background: UiColors.background }}>
        <Text style={UiTypography.display}>Gang Chat</Text>
        <Text style={UiTypography.dot}>MUSIC BOX 42</Text>
        <Text style={UiTypography.overline}>● LIVE · 3</Text>
        <Row mainAxisSize="min" gap={8}>
          <Icon icon={Icons.mic_off} color={UiColors.accent} />
          <Icon icon={Icons.headphones} />
          <Icon icon={Icons.settings_outlined} />
        </Row>
        <Text style={UiTypography.body}>
          中文正文 · {platform().os} · v{platform().app.version}
        </Text>
      </Column>
    </OverlayHost>
  );
}
