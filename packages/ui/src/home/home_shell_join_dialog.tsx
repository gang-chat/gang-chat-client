import { useEffect, useState } from 'react';

import type { PublicRoom } from '@gang-chat/core/protocol/models';

import { Expanded, Row, SizedBox, Text } from '../lib/layout';
import { TextEditingController } from '../lib/text_editing';
import { useAppConfig } from '../ui/app_config_scope';
import { Avatar } from '../ui/avatar';
import { ButtonIcon, ButtonTone } from '../ui/button';
import { ResponsiveDialogAction, ResponsiveDialogActionBar } from '../ui/feedback';
import { Icon, Icons } from '../ui/icon';
import { Input } from '../ui/input';
import { useRoute } from '../ui/overlay';
import { UiColors, UiRadii, UiTypography } from '../ui/tokens';

export interface _JoinApplicationDialogProps {
  room: PublicRoom;

  /**
   * Closes the dialog with the application reason (or `undefined` when
   * cancelled). Defaults to popping the enclosing route
   * (`Navigator.of(context, rootNavigator: true).pop(result)`), so the dialog
   * works directly inside `showDialog<string>(() => <_JoinApplicationDialog … />)`.
   */
  onClose?: ((result?: string) => void) | null;
}

// Flutter's `Dialog(insetPadding: EdgeInsets.symmetric(horizontal: 20,
// vertical: 20))`; showDialog's centring layer already pads by 24.
const _dialogInset = 20;
const _showDialogInset = 24;

export function _JoinApplicationDialog({ room, onClose }: _JoinApplicationDialogProps) {
  const route = useRoute<string>();
  const [reasonController] = useState(() => new TextEditingController());
  useEffect(() => () => reasonController.dispose(), [reasonController]);
  const config = useAppConfig();

  const close = (result?: string) => {
    if (onClose != null) {
      onClose(result);
      return;
    }
    route.close(result);
  };
  const cancel = () => close();
  const send = () => close(reasonController.text);

  const insetCorrection = _showDialogInset - _dialogInset;
  return (
    <div
      role="dialog"
      aria-label="申请加入"
      style={{
        width: 430,
        maxWidth: `calc(100% + ${insetCorrection * 2}px)`,
        margin: -insetCorrection,
        boxSizing: 'border-box',
        backgroundColor: UiColors.surfaceLow,
        borderRadius: UiRadii.lg,
        border: `1px solid ${UiColors.border}`,
        padding: '18px 20px 20px',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'stretch',
      }}
    >
      <Row>
        <Icon icon={Icons.how_to_reg_outlined} color={UiColors.accent} size={20} />
        <SizedBox width={8} />
        <Expanded>
          <Text style={{ ...UiTypography.title, fontWeight: 600 }}>申请加入</Text>
        </Expanded>
        <ButtonIcon tooltip="关闭" icon={<Icon icon={Icons.close} />} onPressed={cancel} size={34} />
      </Row>
      <SizedBox height={18} />
      {/* Wrap(spacing: 6, runSpacing: 8, crossAxisAlignment: center) */}
      <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', columnGap: 6, rowGap: 8 }}>
        <Text style={{ ...UiTypography.body, color: UiColors.textSecondary }}>您需要等待</Text>
        <Avatar
          label={room.name}
          imageUrl={config.resolveAssetUrl(room.avatarUrl)}
          defaultAvatarKey={room.defaultAvatarKey}
          size={28}
        />
        <div style={{ maxWidth: 230, minWidth: 0 }}>
          <Text maxLines={1} style={{ ...UiTypography.body, fontWeight: 600 }}>
            {room.name}
          </Text>
        </div>
        <Text style={{ ...UiTypography.body, color: UiColors.textSecondary }}>的管理员批准</Text>
      </div>
      <SizedBox height={14} />
      <Input controller={reasonController} hintText="申请说明" minLines={3} maxLines={4} />
      <SizedBox height={16} />
      <ResponsiveDialogActionBar
        actions={[
          new ResponsiveDialogAction({ label: '取消', onPressed: cancel }),
          new ResponsiveDialogAction({ label: '发送申请', tone: ButtonTone.primary, onPressed: send }),
        ]}
      />
    </div>
  );
}
