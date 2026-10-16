// Port of `lib/src/settings/settings_components.dart` (a `part of
// 'settings_page.dart'`): the settings search index and the form building
// blocks (two-column groups, sub panels, labelled fields, toggles, segmented
// settings, the account-activity list). Part-private widgets are exported
// with their leading underscore (see docs/js-port-conventions.md).

import { useRef, useState, type ReactNode } from 'react';

import * as account_display from '@gang-chat/core/app/account_display';
import { SettingsSection } from '@gang-chat/core/app/settings_shell_state';
import type { UserSession } from '@gang-chat/core/protocol/models';

import { EdgeInsets } from '../lib/edge_insets';
import { Center, Column, Expanded, Row, SizedBox, Text } from '../lib/layout';
import type { MouseCursor } from '../lib/mouse_cursors';
import { CircularProgressIndicator } from '../lib/progress_indicator';
import type { TextEditingController, TextInputType } from '../lib/text_editing';
import { useElementSize } from '../lib/use_element_size';
import { HighlightedText } from '../ui/highlighted_text';
import { Icon, Icons } from '../ui/icon';
import { Input } from '../ui/input';
import { ReadOnlyTextBox } from '../ui/read_only_text_box';
import { SegmentedControl, Segment } from '../ui/segmented_control';
import { PressableSurface } from '../ui/surface';
import { UiSwitch } from '../ui/switch';
import { UiColors, UiCurves, UiRadii, UiTypography, argb, kClientFontStack } from '../ui/tokens';
import { _borderColor, _cyan, _primaryDark, _textMuted, _textPrimary, _textSecondary } from './settings_page';
import { _FieldLabel, _PasswordVisibilityToggle, _SettingsEmptyState } from './settings_profile_widgets';

export const _settingsSectionSegments: readonly Segment<SettingsSection>[] = [
  new Segment({
    value: SettingsSection.profile,
    label: '用户资料',
    icon: Icons.badge_outlined,
  }),
  new Segment({
    value: SettingsSection.preferences,
    label: '偏好设置',
    icon: Icons.tune_outlined,
  }),
  new Segment({
    value: SettingsSection.security,
    label: '隐私和安全',
    icon: Icons.shield_outlined,
  }),
  new Segment({ value: SettingsSection.voice, label: '语音和视频', icon: Icons.graphic_eq }),
  new Segment({
    value: SettingsSection.stickers,
    label: '我的表情包',
    icon: Icons.emoji_emotions_outlined,
  }),
  new Segment({
    value: SettingsSection.playlists,
    label: '我的歌单',
    icon: Icons.queue_music_outlined,
  }),
  new Segment({
    value: SettingsSection.about,
    label: '关于Gang Chat',
    icon: Icons.info_outline,
  }),
];

/** One searchable setting: where it lives and the words that find it. */
export class SettingsSearchEntry {
  constructor(
    readonly section: SettingsSection,
    readonly label: string,
    readonly keywords: string,
    opts: { androidOnly?: boolean; desktopOnly?: boolean } = {},
  ) {
    this.androidOnly = opts.androidOnly ?? false;
    this.desktopOnly = opts.desktopOnly ?? false;
  }

  readonly androidOnly: boolean;
  readonly desktopOnly: boolean;
}

export const _settingsSearchIndex: readonly SettingsSearchEntry[] = [
  new SettingsSearchEntry(SettingsSection.profile, '个人 UID', 'uid id 账号 编号'),
  new SettingsSearchEntry(SettingsSection.profile, '登录用户名', 'username 账号 登录'),
  new SettingsSearchEntry(SettingsSection.profile, '用户名', '昵称 名称 name 显示'),
  new SettingsSearchEntry(SettingsSection.profile, '性别', 'gender 男 女 保密'),
  new SettingsSearchEntry(SettingsSection.profile, '头像', 'avatar 上传 图片 照片'),
  new SettingsSearchEntry(SettingsSection.profile, '签名', 'bio 简介 个性签名 介绍'),
  new SettingsSearchEntry(SettingsSection.preferences, '关闭方式', 'close 托盘 最小化 退出 窗口', {
    desktopOnly: true,
  }),
  new SettingsSearchEntry(SettingsSection.preferences, '系统通知', 'notification 通知 消息 角标', {
    androidOnly: true,
  }),
  new SettingsSearchEntry(SettingsSection.preferences, '语言切换', 'language 语言 中文 繁體 english'),
  new SettingsSearchEntry(SettingsSection.security, '邮箱绑定', 'email 邮件 验证'),
  new SettingsSearchEntry(SettingsSection.security, '公开邮箱', 'email 隐私 公开'),
  new SettingsSearchEntry(SettingsSection.security, '手机号绑定', 'phone 电话 手机'),
  new SettingsSearchEntry(SettingsSection.security, '公开手机号', 'phone 隐私 公开'),
  new SettingsSearchEntry(SettingsSection.security, '重置密码', 'password 密码 修改 更新'),
  new SettingsSearchEntry(SettingsSection.security, '忘记密码', 'password 密码 找回'),
  new SettingsSearchEntry(SettingsSection.security, '账号活动', 'session 登录 设备 会话 记录'),
  new SettingsSearchEntry(SettingsSection.security, '注销账号', 'delete 删除 注销'),
  new SettingsSearchEntry(SettingsSection.voice, '输入源', 'mic 麦克风 设备 输入'),
  new SettingsSearchEntry(SettingsSection.voice, '输入音量', 'mic 麦克风 音量 测试'),
  new SettingsSearchEntry(SettingsSection.voice, '输出源', 'speaker 耳机 扬声器 设备'),
  new SettingsSearchEntry(SettingsSection.voice, '输出音量', 'volume 音量 声音 测试'),
  new SettingsSearchEntry(SettingsSection.voice, '屏幕共享', 'screen share 分辨率 帧率 画质 直播'),
  new SettingsSearchEntry(SettingsSection.stickers, '本地上传', 'sticker 表情 表情包 上传 图片'),
  new SettingsSearchEntry(SettingsSection.stickers, '批量管理', 'sticker 表情 删除 下载 置顶 筛选'),
  new SettingsSearchEntry(SettingsSection.playlists, '我的歌单', 'playlist music 歌单 音乐 歌曲 导入'),
  new SettingsSearchEntry(SettingsSection.about, '版本信息', 'version 版本 发行 更新时间'),
  new SettingsSearchEntry(SettingsSection.about, '自动提示更新', 'update 更新 提示'),
  new SettingsSearchEntry(SettingsSection.about, '检查更新', 'update 更新 升级 下载'),
  new SettingsSearchEntry(SettingsSection.about, '意见反馈', 'feedback 反馈 邮件 bug'),
];

/** Settings matching [query] by label, keyword or section name. */
export function settingsSearchMatches(query: string, opts: { android: boolean }): SettingsSearchEntry[] {
  const { android } = opts;
  const needle = query.trim().toLowerCase();
  if (needle.length === 0) return [];
  const sectionLabel = (section: SettingsSection): string => {
    for (const segment of _settingsSectionSegments) {
      if (segment.value === section) return segment.label;
    }
    return '';
  };

  return _settingsSearchIndex.filter(
    (entry) =>
      !(entry.androidOnly && !android) &&
      !(entry.desktopOnly && android) &&
      `${entry.label} ${entry.keywords} ${sectionLabel(entry.section)}`.toLowerCase().includes(needle),
  );
}

// ---------------------------------------------------------------------------
// _SettingsSearchResultRow

export interface _SettingsSearchResultRowProps {
  entry: SettingsSearchEntry;
  query: string;
  onPressed: () => void;
  /** Dart `key: ValueKey('settings-search-result-$i')`, as a test id. */
  testId?: string;
}

export function _SettingsSearchResultRow({ entry, query, onPressed, testId }: _SettingsSearchResultRowProps) {
  const segment = _settingsSectionSegments.find((segment) => segment.value === entry.section)!;
  const index = _settingsSectionSegments.indexOf(segment) + 1;
  return (
    <PressableSurface
      data-testid={testId}
      height={54}
      onPressed={onPressed}
      backgroundColor={UiColors.card}
      hoverBackgroundColor={UiColors.surfaceRaised}
      borderColor={UiColors.border}
      borderRadius={UiRadii.md}
      padding={EdgeInsets.symmetric({ horizontal: 14 })}
    >
      <Row>
        <div
          style={{
            width: 30,
            height: 30,
            flexShrink: 0,
            backgroundColor: UiColors.selected,
            borderRadius: UiRadii.sm,
            // Container hands the Icon tight 30×30 constraints, which centre the glyph.
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          {segment.icon != null ? <Icon icon={segment.icon} size={16} color={UiColors.accent} /> : null}
        </div>
        <SizedBox width={12} />
        <Expanded>
          <Column crossAxisAlignment="start" mainAxisAlignment="center">
            <HighlightedText
              text={entry.label}
              query={query}
              maxLines={1}
              overflow="ellipsis"
              style={{ ...UiTypography.body, fontWeight: 600, maxWidth: '100%' }}
            />
            <SizedBox height={2} />
            <Text maxLines={1} style={{ ...UiTypography.mono, fontSize: 10.5, maxWidth: '100%' }}>
              {`${index.toString().padStart(2, '0')} · ${segment.label}`}
            </Text>
          </Column>
        </Expanded>
        <Icon icon={Icons.arrow_forward_rounded} size={16} color={UiColors.textMuted} />
      </Row>
    </PressableSurface>
  );
}

// ---------------------------------------------------------------------------
// _SettingsGroup

export interface _SettingsGroupProps {
  title: string;
  children?: ReactNode;
  trailing?: ReactNode;
  danger?: boolean;
}

const _settingsGroupWideMinWidth = 700;
const _settingsGroupTitleColumnWidth = 188;

/**
 * A settings group laid out as a form row: on wide pages the title sits in
 * its own left column beside the controls' panel; narrow pages stack them.
 */
export function _SettingsGroup({ title, children, trailing = null, danger = false }: _SettingsGroupProps) {
  const ref = useRef<HTMLDivElement | null>(null);
  // LayoutBuilder: until the first measurement (before the first paint)
  // the group lays out wide.
  const maxWidth = useElementSize(ref)?.width ?? Infinity;
  const accent = danger ? UiColors.danger : UiColors.accent;
  const titleText = (
    <Text style={{ ...UiTypography.subtitle, color: danger ? UiColors.danger : UiColors.text }}>{title}</Text>
  );
  const panel = (
    <Column
      crossAxisAlignment="stretch"
      style={{
        backgroundColor: danger ? argb(0xff1a1012) : UiColors.card,
        borderRadius: UiRadii.lg,
        border: `1px solid ${danger ? UiColors.dangerBorder : UiColors.border}`,
        // Flutter paints the DecoratedBox border over the 18/16/18/18
        // padding, so the content inset from the outer edge stays the same.
        padding: '15px 17px 17px',
        boxSizing: 'border-box',
      }}
    >
      {children}
    </Column>
  );
  let content: ReactNode;
  if (maxWidth < _settingsGroupWideMinWidth) {
    content = (
      <Column crossAxisAlignment="stretch">
        <Row style={{ padding: '0 2px 10px', boxSizing: 'border-box' }}>
          <div style={{ width: 3, height: 14, flexShrink: 0, backgroundColor: accent, borderRadius: 2 }} />
          <SizedBox width={8} />
          <Expanded>{titleText}</Expanded>
          {trailing}
        </Row>
        {panel}
      </Column>
    );
  } else {
    content = (
      <Row crossAxisAlignment="start">
        <SizedBox width={_settingsGroupTitleColumnWidth}>
          <Column crossAxisAlignment="start" style={{ padding: '14px 18px 0 2px', boxSizing: 'border-box' }}>
            <div style={{ width: 18, height: 3, flexShrink: 0, backgroundColor: accent, borderRadius: 2 }} />
            <SizedBox height={10} />
            {titleText}
            {trailing != null ? (
              <>
                <SizedBox height={10} />
                {trailing}
              </>
            ) : null}
          </Column>
        </SizedBox>
        <Expanded style={{ alignItems: 'stretch' }}>{panel}</Expanded>
      </Row>
    );
  }
  return (
    <div ref={ref} style={{ minWidth: 0, display: 'flex', flexDirection: 'column' }}>
      {content}
    </div>
  );
}

// ---------------------------------------------------------------------------
// _SettingsSubPanel

export interface _SettingsSubPanelProps {
  children?: ReactNode;
  hoverable?: boolean;
  highlighted?: boolean;
  mouseCursor?: MouseCursor | null;
  /** Dart `key`, as a test id. */
  testId?: string;
}

export function _SettingsSubPanel({ children, hoverable = false, highlighted = false, mouseCursor = null, testId }: _SettingsSubPanelProps) {
  const [hovered, setHoveredState] = useState(false);
  const setHovered = (next: boolean) => {
    if (!hoverable || hovered === next) return;
    setHoveredState(next);
  };
  const isHighlighted = highlighted || (hoverable && hovered);
  const transition = (property: string) => `${property} 90ms ${UiCurves.easeOutCubic}`;
  return (
    <div
      data-testid={testId}
      onMouseEnter={hoverable ? () => setHovered(true) : undefined}
      onMouseLeave={hoverable ? () => setHovered(false) : undefined}
      style={{
        display: 'flex',
        flexDirection: 'column',
        minWidth: 0,
        boxSizing: 'border-box',
        backgroundColor: isHighlighted ? UiColors.selected : _primaryDark,
        borderRadius: UiRadii.md,
        border: `1px solid ${isHighlighted ? UiColors.selectedBorder : _borderColor}`,
        // The decoration's border paints over the 14px padding in Flutter.
        padding: 13,
        cursor: hoverable && mouseCursor != null ? mouseCursor : undefined,
        transition: `${transition('background-color')}, ${transition('border-color')}`,
      }}
    >
      {children}
    </div>
  );
}

// ---------------------------------------------------------------------------
// _LabeledTextField

export interface _LabeledTextFieldProps {
  label: string;
  controller: TextEditingController;
  enabled?: boolean;
  maxLines?: number;
  obscureText?: boolean;
  keyboardType?: TextInputType | null;
  helperText?: string | null;
  suffix?: ReactNode;
  onChanged?: ((value: string) => void) | null;
  onTogglePasswordVisibility?: (() => void) | null;
  /** Dart `key`, as a test id. */
  testId?: string;
}

export function _LabeledTextField({
  label,
  controller,
  enabled = true,
  maxLines = 1,
  obscureText = false,
  keyboardType = null,
  helperText = null,
  suffix = null,
  onChanged = null,
  onTogglePasswordVisibility = null,
  testId,
}: _LabeledTextFieldProps) {
  return (
    <Column data-testid={testId} crossAxisAlignment="stretch">
      <_FieldLabel text={label} />
      <SizedBox height={8} />
      <Input
        controller={controller}
        hintText=""
        aria-label={label}
        enabled={enabled}
        obscureText={obscureText}
        keyboardType={keyboardType}
        minLines={1}
        maxLines={obscureText ? 1 : maxLines}
        onChanged={onChanged}
        suffix={
          onTogglePasswordVisibility == null ? (
            suffix
          ) : (
            <_PasswordVisibilityToggle obscure={obscureText} enabled={enabled} onPressed={onTogglePasswordVisibility} />
          )
        }
      />
      {helperText != null ? (
        <>
          <SizedBox height={6} />
          <Text style={{ color: _textMuted, fontSize: 12 }}>{helperText}</Text>
        </>
      ) : null}
    </Column>
  );
}

// ---------------------------------------------------------------------------
// _CopyableField

export function _CopyableField({ label, value }: { label: string; value: string }) {
  return (
    <Column crossAxisAlignment="stretch">
      <_FieldLabel text={label} />
      <SizedBox height={8} />
      <ReadOnlyTextBox
        value={value}
        backgroundColor={_primaryDark}
        borderColor={_borderColor}
        style={{ fontFamily: kClientFontStack, color: _textSecondary, fontSize: 14, fontWeight: 500 }}
      />
    </Column>
  );
}

// ---------------------------------------------------------------------------
// _ReadOnlyLine

export function _ReadOnlyLine({ label, value }: { label: string; value: string }) {
  return (
    <Row>
      <SizedBox width={110}>
        <_FieldLabel text={label} />
      </SizedBox>
      <Expanded>
        <Text maxLines={1} style={{ color: _textSecondary, fontSize: 14, fontWeight: 500 }}>
          {value}
        </Text>
      </Expanded>
    </Row>
  );
}

// ---------------------------------------------------------------------------
// _ToggleSetting

export interface _ToggleSettingProps {
  label: string;
  value: boolean;
  onChanged: (value: boolean) => void;
  enabled?: boolean;
}

export function _ToggleSetting({ label, value, onChanged, enabled = true }: _ToggleSettingProps) {
  return (
    <Row>
      <Expanded>
        <_FieldLabel text={label} />
      </Expanded>
      {/* IgnorePointer(ignoring: !enabled) > Opacity(enabled ? 1 : 0.55). */}
      <div style={{ pointerEvents: enabled ? undefined : 'none', opacity: enabled ? 1 : 0.55, flexShrink: 0, display: 'flex' }}>
        <UiSwitch value={value} onChanged={onChanged} aria-label={label} />
      </div>
    </Row>
  );
}

// ---------------------------------------------------------------------------
// _SegmentedSetting

export class _SegmentOption {
  constructor(opts: { value: string; label: string }) {
    this.value = opts.value;
    this.label = opts.label;
  }

  readonly value: string;
  readonly label: string;
}

export interface _SegmentedSettingProps {
  label: string;
  value: string;
  options: readonly _SegmentOption[];
  onChanged: (value: string) => void;
  enabled?: boolean;
  /** Dart `key`, as a test id. */
  testId?: string;
}

export function _SegmentedSetting({ label, value, options, onChanged, enabled = true, testId }: _SegmentedSettingProps) {
  return (
    <Column data-testid={testId} crossAxisAlignment="stretch">
      <_FieldLabel text={label} />
      <SizedBox height={8} />
      {/* IgnorePointer(ignoring: !enabled) > Opacity(enabled ? 1 : 0.55). */}
      <div style={{ pointerEvents: enabled ? undefined : 'none', opacity: enabled ? 1 : 0.55, minWidth: 0 }}>
        <SegmentedControl<string>
          expanded
          value={value}
          onChanged={onChanged}
          segments={options.map((option) => new Segment({ value: option.value, label: option.label }))}
        />
      </div>
    </Column>
  );
}

// ---------------------------------------------------------------------------
// _SessionList

export function _SessionList({ sessions, loading }: { sessions: readonly UserSession[]; loading: boolean }) {
  const bodyState = account_display.sessionListBodyState({ loading, sessions });
  switch (bodyState) {
    case account_display.SessionListBodyState.loading:
      return (
        <SizedBox height={82}>
          <Center style={{ height: '100%' }}>
            <CircularProgressIndicator color={_cyan} />
          </Center>
        </SizedBox>
      );
    case account_display.SessionListBodyState.empty:
      return <_SettingsEmptyState text="暂无近期账号活动" />;
    case account_display.SessionListBodyState.results:
      return (
        <Column crossAxisAlignment="stretch">
          {sessions.map((session) => (
            <div key={session.id} style={{ paddingBottom: 8, display: 'flex', flexDirection: 'column' }}>
              <Column
                crossAxisAlignment="stretch"
                style={{
                  backgroundColor: _primaryDark,
                  borderRadius: UiRadii.md,
                  border: `1px solid ${_borderColor}`,
                  // The DecoratedBox border paints over the 12px padding.
                  padding: 11,
                  boxSizing: 'border-box',
                }}
              >
                <Row>
                  <Icon
                    icon={session.isCurrent ? Icons.radio_button_checked : Icons.devices_other}
                    color={session.isCurrent ? _cyan : _textMuted}
                    size={16}
                  />
                  <SizedBox width={8} />
                  <Expanded>
                    <Text maxLines={1} style={{ color: _textPrimary, fontSize: 13, fontWeight: 600 }}>
                      {account_display.sessionDeviceLabel(session)}
                    </Text>
                  </Expanded>
                  <SizedBox width={8} />
                  <Text
                    softWrap={false}
                    style={{ color: session.isActive ? _cyan : _textMuted, fontSize: 12, fontWeight: 600 }}
                  >
                    {account_display.sessionStateText(session)}
                  </Text>
                </Row>
                <SizedBox height={8} />
                <Text maxLines={3} style={{ color: _textMuted, fontSize: 12, lineHeight: 1.35 }}>
                  {account_display.sessionDetailText(session)}
                </Text>
              </Column>
            </div>
          ))}
        </Column>
      );
  }
}
