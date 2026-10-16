import { useLayoutEffect, useRef, type CSSProperties, type ReactNode } from 'react';

import {
  AppUpdatePlatform,
  AvailableAppUpdate,
  downloadsUpdateInBrowser,
  releaseNotesLabel,
  releaseTimeLabel,
  updateDownloadConfirmationBody,
} from '@gang-chat/core/app/app_update';
import { AutoUpdatePromptStore, appVersionLabel, compareAppVersions, gangChatClientVersion } from '@gang-chat/core/app/settings_about';
import { HostPlatform } from '@gang-chat/core/config/host_platform';
import { unawaited } from '@gang-chat/core/lib/async';
import type { DesktopWindowController } from '@gang-chat/core/shell/desktop_window_controller';
import { LocalAutoUpdatePromptStore } from '@gang-chat/core/shell/local_auto_update_prompt_store';
import { appUpdatePlatformForOperatingSystem, ReleaseUpdateService } from '@gang-chat/core/shell/release_update_service';
import { SparkleUpdater } from '@gang-chat/core/shell/sparkle_updater';

import { Expanded, Row, SizedBox, Text } from '../lib/layout';
import { useViewState, ViewState } from '../lib/view_state';
import { ButtonIcon, ButtonTone } from '../ui/button';
import { ResponsiveDialogAction, ResponsiveDialogActionBar } from '../ui/feedback';
import { Icon, Icons } from '../ui/icon';
import { SettingsCard, SettingsList, SettingsScaffold } from '../ui/settings_scaffold';
import { UiColors, UiRadii, UiTypography, kClientFontStack } from '../ui/tokens';

export type AppUpdateAvailableCallback = (update: AvailableAppUpdate) => void;

const _defaultAutoUpdatePromptStore = new LocalAutoUpdatePromptStore();
const _defaultSparkleUpdater = new SparkleUpdater();

/**
 * The release service the update gate checks with when none is injected.
 *
 * PORT: the Dart `ReleaseUpdateService` was stateless (`const`), so every
 * caller could construct its own. The TS service remembers whether the
 * native updater (electron-updater) found the release, which
 * `isNativeUpdate(update)` reads before downloading. The settings page that
 * downloads an update detected here must therefore use this same instance
 * (or check again itself) for the in-app native path to be taken.
 */
export const sharedReleaseUpdateService = new ReleaseUpdateService();

/**
 * Whether [update] is downloaded in the browser (the macOS DMG fallback).
 *
 * PORT: `downloadsUpdateInBrowser(platform)` was the whole answer in Dart.
 * When electron-updater serves the release ([ReleaseUpdateService.isNativeUpdate]),
 * macOS uses the in-app download and install path like Windows.
 */
export function updateDownloadsInBrowser(update: AvailableAppUpdate, service: ReleaseUpdateService = sharedReleaseUpdateService): boolean {
  if (service.isNativeUpdate(update)) return false;
  return downloadsUpdateInBrowser(update.asset.platform);
}

/**
 * Confirmation copy for downloading [update]: `updateDownloadConfirmationBody`
 * plus the macOS native-updater case, where electron-updater quits, installs
 * and relaunches the app.
 */
export function updateDownloadConfirmationBodyFor(
  update: AvailableAppUpdate,
  service: ReleaseUpdateService = sharedReleaseUpdateService,
): string {
  if (update.asset.platform === AppUpdatePlatform.macos && service.isNativeUpdate(update)) {
    return '将下载新版安装包。下载完成后，Gang Chat 会退出并安装新版本，然后自动重新打开。';
  }
  return updateDownloadConfirmationBody(update.asset.platform);
}

export interface AppUpdateGateProps {
  children?: ReactNode;
  currentVersion?: string;
  releaseBucketUrl: string;
  autoUpdatePromptStore?: AutoUpdatePromptStore;
  updateService?: ReleaseUpdateService | null;
  sparkleUpdater?: SparkleUpdater;
  platformOverride?: AppUpdatePlatform | null;
  windowController: DesktopWindowController;
  onUpdateAvailable?: AppUpdateAvailableCallback | null;
}

class _AppUpdateGateState extends ViewState<AppUpdateGateProps> {
  _checking = false;
  _notifiedAssetKey: string | null = null;

  get _updateService(): ReleaseUpdateService {
    return this.widget.updateService ?? sharedReleaseUpdateService;
  }

  get _currentVersion(): string {
    return this.widget.currentVersion ?? gangChatClientVersion;
  }

  get _autoUpdatePromptStore(): AutoUpdatePromptStore {
    return this.widget.autoUpdatePromptStore ?? _defaultAutoUpdatePromptStore;
  }

  override initState(): void {
    unawaited(this._checkForUpdate());
  }

  override didUpdateProps(oldProps: AppUpdateGateProps): void {
    if (
      oldProps.releaseBucketUrl !== this.widget.releaseBucketUrl ||
      (oldProps.currentVersion ?? gangChatClientVersion) !== this._currentVersion ||
      (oldProps.updateService ?? null) !== (this.widget.updateService ?? null)
    ) {
      this._notifiedAssetKey = null;
      unawaited(this._checkForUpdate());
    }
  }

  async _checkForUpdate(): Promise<void> {
    if (this._checking) return;
    const platform = this._currentPlatform();
    if (platform == null) return;

    try {
      const enabled = await this._autoUpdatePromptStore.read();
      if (!enabled) return;
    } catch {
      return;
    }

    // Sparkle checks the appcast and shows its own window when an update
    // exists, so the release bucket prompt isn't used.
    const sparkleUpdater = this.widget.sparkleUpdater ?? _defaultSparkleUpdater;
    if (platform === AppUpdatePlatform.macos && (await sparkleUpdater.isAvailable())) {
      try {
        await sparkleUpdater.checkForUpdates({ bucketUrl: this.widget.releaseBucketUrl, background: true });
      } catch {
        // ignore
      }
      return;
    }
    if (!this.mounted) return;

    this.setState(() => {
      this._checking = true;
    });
    try {
      const update = await this._updateService.checkForUpdate({
        bucketUrl: this.widget.releaseBucketUrl,
        currentVersion: this._currentVersion,
        platform,
      });
      if (!this.mounted) return;
      this.setState(() => {
        this._checking = false;
      });
      if (update == null || update.asset.key === this._notifiedAssetKey) return;
      const ignoredVersion = await this._autoUpdatePromptStore.readIgnoredVersion();
      if (!this.mounted) return;
      if (ignoredVersion != null && compareAppVersions(update.latestVersion, ignoredVersion) <= 0) {
        return;
      }
      this._notifiedAssetKey = update.asset.key;
      this.widget.onUpdateAvailable?.(update);
    } catch {
      if (!this.mounted) return;
      this.setState(() => {
        this._checking = false;
      });
    }
  }

  _currentPlatform(): AppUpdatePlatform | null {
    const override = this.widget.platformOverride;
    if (override != null) return override;
    return appUpdatePlatformForOperatingSystem(HostPlatform.operatingSystem);
  }
}

export function AppUpdateGate(props: AppUpdateGateProps) {
  useViewState(() => new _AppUpdateGateState(), props);
  return <>{props.children}</>;
}

// ---------------------------------------------------------------------------

export interface AppUpdatePageProps {
  update: AvailableAppUpdate;
  checking: boolean;
  downloading: boolean;
  downloadedBytes: number;
  downloadTotalBytes?: number | null;
  error?: string | null;
  onDownload: () => void;
  onBack: () => void;
  onIgnoreVersion: () => void;
  onRefresh: () => void;
  wrapInScaffold?: boolean;
}

export function AppUpdatePage({
  update,
  checking,
  downloading,
  downloadedBytes,
  downloadTotalBytes = null,
  error = null,
  onDownload,
  onBack,
  onIgnoreVersion,
  onRefresh,
  wrapInScaffold = false,
}: AppUpdatePageProps) {
  const total = downloadTotalBytes;
  const downloadProgress = total == null || total <= 0 ? null : Math.min(Math.max(downloadedBytes / total, 0), 1);

  const versionChildren: ReactNode[] = [
    <_VersionLine key="current" label="当前版本" value={appVersionLabel(update.currentVersion)} />,
    <_VersionLine key="latest" label="最新版本" value={appVersionLabel(update.latestVersion)} accent />,
    <_VersionLine key="released" label="发行时间" value={releaseTimeLabel(update.asset.releasedAt)} scaleValueToFit />,
    <_VersionLog key="log" value={releaseNotesLabel(update.releaseNotes)} />,
  ];
  if (downloading) {
    versionChildren.push(
      <SizedBox key="gap" height={2} />,
      <div key="progress" style={{ borderRadius: UiRadii.sm, overflow: 'hidden' }}>
        <_LinearProgressIndicator value={downloadProgress} minHeight={8} color={UiColors.controlAccent} backgroundColor={UiColors.surfacePressed} />
      </div>,
      <Text key="progress-label" style={UiTypography.label}>
        {downloadProgress == null ? '正在下载更新' : `正在下载更新 ${Math.round(downloadProgress * 100)}%`}
      </Text>,
    );
  }

  const listChildren: ReactNode[] = [
    <SettingsCard key="version" title="版本更新" trailing={<_UpdateBadge version={update.latestVersion} />}>
      {versionChildren}
    </SettingsCard>,
  ];
  if (error != null) {
    listChildren.push(
      <SettingsCard key="error" title="更新失败" danger>
        {[
          <Text key="message" style={{ ...UiTypography.label, color: UiColors.danger, lineHeight: 1.5 }}>
            {error}
          </Text>,
        ]}
      </SettingsCard>,
    );
  }
  listChildren.push(<_UpdateActions key="actions" downloading={downloading} onDownload={onDownload} onIgnoreVersion={onIgnoreVersion} />);

  const page = (
    <SettingsScaffold
      icon={Icons.system_update_alt_outlined}
      iconColor={UiColors.controlAccent}
      title="发现新版本"
      onBack={downloading ? null : onBack}
      headerAction={
        <ButtonIcon
          tooltip="重新检查"
          icon={<Icon icon={Icons.refresh} />}
          loading={checking}
          onPressed={checking || downloading ? null : onRefresh}
          size={38}
        />
      }
      body={<SettingsList>{listChildren}</SettingsList>}
    />
  );
  if (!wrapInScaffold) return page;
  return (
    <div style={{ flex: '1 1 0', minWidth: 0, minHeight: 0, display: 'flex', flexDirection: 'column', backgroundColor: UiColors.surfaceLow }}>
      {page}
    </div>
  );
}

const _versionLabelWidth = 86;

function _valueTextStyle(accent: boolean): CSSProperties {
  return {
    color: accent ? UiColors.accent : UiColors.text,
    fontFamily: kClientFontStack,
    fontSize: 15,
    fontWeight: 600,
    letterSpacing: 0,
  };
}

function _VersionLine({
  label,
  value,
  accent = false,
  scaleValueToFit = false,
}: {
  label: string;
  value: string;
  accent?: boolean;
  scaleValueToFit?: boolean;
}) {
  return (
    <Row>
      <SizedBox width={_versionLabelWidth}>
        <Text style={UiTypography.label}>{label}</Text>
      </SizedBox>
      <Expanded>
        {scaleValueToFit ? (
          <_ScaleDownText style={_valueTextStyle(accent)}>{value}</_ScaleDownText>
        ) : (
          <Text maxLines={1} softWrap={false} style={_valueTextStyle(accent)}>
            {value}
          </Text>
        )}
      </Expanded>
    </Row>
  );
}

/**
 * `Align(centerLeft) > FittedBox(fit: BoxFit.scaleDown, alignment: centerLeft)`
 * around a single-line text: shrinks the text uniformly when it is wider than
 * the available width, never enlarges it.
 */
function _ScaleDownText({ style, children }: { style: CSSProperties; children: ReactNode }) {
  const outerRef = useRef<HTMLDivElement | null>(null);
  const innerRef = useRef<HTMLSpanElement | null>(null);
  useLayoutEffect(() => {
    const outer = outerRef.current;
    const inner = innerRef.current;
    if (outer == null || inner == null) return;
    const fit = () => {
      inner.style.transform = 'none';
      const available = outer.clientWidth;
      const natural = inner.scrollWidth;
      const scale = natural > available && natural > 0 ? available / natural : 1;
      inner.style.transform = scale < 1 ? `scale(${scale})` : 'none';
      outer.style.height = `${inner.offsetHeight * scale}px`;
    };
    fit();
    if (typeof ResizeObserver === 'undefined') return;
    const observer = new ResizeObserver(fit);
    observer.observe(outer);
    return () => observer.disconnect();
  });
  return (
    <div ref={outerRef} style={{ width: '100%', minWidth: 0, overflow: 'visible', display: 'flex', alignItems: 'center' }}>
      <span
        ref={innerRef}
        style={{ ...style, display: 'inline-block', whiteSpace: 'nowrap', transformOrigin: 'left center', flexShrink: 0 }}
      >
        {children}
      </span>
    </div>
  );
}

function _VersionLog({ value }: { value: string }) {
  return (
    <Row crossAxisAlignment="start">
      <SizedBox width={_versionLabelWidth}>
        <Text style={UiTypography.label}>版本日志</Text>
      </SizedBox>
      <Expanded>
        <Text style={{ color: UiColors.text, fontFamily: kClientFontStack, fontSize: 14, fontWeight: 500, lineHeight: 1.5, letterSpacing: 0 }}>
          {value}
        </Text>
      </Expanded>
    </Row>
  );
}

function _UpdateBadge({ version }: { version: string }) {
  return (
    <div
      style={{
        backgroundColor: UiColors.selected,
        borderRadius: UiRadii.sm,
        border: `1px solid ${UiColors.selectedBorder}`,
        padding: '4px 8px',
        flexShrink: 0,
      }}
    >
      <Text style={{ ...UiTypography.label, color: UiColors.accent, fontWeight: 700 }}>{appVersionLabel(version)}</Text>
    </div>
  );
}

function _UpdateActions({
  downloading,
  onDownload,
  onIgnoreVersion,
}: {
  downloading: boolean;
  onDownload: () => void;
  onIgnoreVersion: () => void;
}) {
  return (
    <ResponsiveDialogActionBar
      expanded
      actions={[
        new ResponsiveDialogAction({
          label: '忽略此版本',
          icon: Icons.notifications_off_outlined,
          onPressed: downloading ? null : onIgnoreVersion,
        }),
        new ResponsiveDialogAction({
          label: '下载新版本',
          icon: Icons.download_for_offline_outlined,
          tone: ButtonTone.primary,
          loading: downloading,
          onPressed: downloading ? null : onDownload,
        }),
      ]}
    />
  );
}

/**
 * Port of Material's `LinearProgressIndicator`: a determinate bar for a
 * [value] in 0..1, or the indeterminate two-segment sweep when it is null.
 * PORT: the kit has no linear indicator; this local one animates with the
 * Web Animations API (no global keyframes needed).
 */
function _LinearProgressIndicator({
  value,
  minHeight = 4,
  color,
  backgroundColor,
}: {
  value: number | null;
  minHeight?: number;
  color: string;
  backgroundColor: string;
}) {
  const primaryRef = useRef<HTMLDivElement | null>(null);
  const secondaryRef = useRef<HTMLDivElement | null>(null);
  const indeterminate = value == null;
  useLayoutEffect(() => {
    if (!indeterminate) return;
    const primary = primaryRef.current;
    const secondary = secondaryRef.current;
    if (primary == null || secondary == null || typeof primary.animate !== 'function') return;
    // Material's 1800ms indeterminate cycle: two bars with staggered
    // left/right edges.
    const duration = 1800;
    const a = primary.animate(
      [
        { left: '-35%', right: '100%', offset: 0 },
        { left: '-35%', right: '100%', offset: 0.0 },
        { left: '100%', right: '-90%', offset: 0.6 },
        { left: '100%', right: '-90%', offset: 1 },
      ],
      { duration, iterations: Infinity, easing: 'cubic-bezier(0.65, 0.815, 0.735, 0.395)' },
    );
    const b = secondary.animate(
      [
        { left: '-200%', right: '100%', offset: 0 },
        { left: '-200%', right: '100%', offset: 0.4 },
        { left: '107%', right: '-8%', offset: 0.93 },
        { left: '107%', right: '-8%', offset: 1 },
      ],
      { duration, iterations: Infinity, easing: 'cubic-bezier(0.165, 0.84, 0.44, 1)' },
    );
    return () => {
      a.cancel();
      b.cancel();
    };
  }, [indeterminate]);

  return (
    <div
      role="progressbar"
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={value == null ? undefined : Math.round(value * 100)}
      style={{ position: 'relative', width: '100%', height: minHeight, overflow: 'hidden', backgroundColor }}
    >
      {indeterminate ? (
        <>
          <div ref={primaryRef} style={{ position: 'absolute', top: 0, bottom: 0, left: '-35%', right: '100%', backgroundColor: color }} />
          <div ref={secondaryRef} style={{ position: 'absolute', top: 0, bottom: 0, left: '-200%', right: '100%', backgroundColor: color }} />
        </>
      ) : (
        <div style={{ position: 'absolute', top: 0, bottom: 0, left: 0, width: `${value * 100}%`, backgroundColor: color }} />
      )}
    </div>
  );
}
