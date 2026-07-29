import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';

import '../app/app_update.dart';
import '../app/settings_about.dart';
import '../ui/ui.dart';
import 'desktop_window_controller.dart';
import 'local_auto_update_prompt_store.dart';
import 'release_update_service.dart';

typedef AppUpdateAvailableCallback = void Function(AvailableAppUpdate update);

class AppUpdateGate extends StatefulWidget {
  const AppUpdateGate({
    super.key,
    required this.child,
    required this.releaseBucketUrl,
    required this.windowController,
    this.currentVersion = gangChatClientVersion,
    this.autoUpdatePromptStore = const LocalAutoUpdatePromptStore(),
    this.updateService,
    this.platformOverride,
    this.onUpdateAvailable,
  });

  final Widget child;
  final String currentVersion;
  final String releaseBucketUrl;
  final AutoUpdatePromptStore autoUpdatePromptStore;
  final ReleaseUpdateService? updateService;
  final AppUpdatePlatform? platformOverride;
  final DesktopWindowController windowController;
  final AppUpdateAvailableCallback? onUpdateAvailable;

  @override
  State<AppUpdateGate> createState() => _AppUpdateGateState();
}

class _AppUpdateGateState extends State<AppUpdateGate> {
  bool _checking = false;
  String? _notifiedAssetKey;

  ReleaseUpdateService get _updateService =>
      widget.updateService ?? ReleaseUpdateService();

  @override
  void initState() {
    super.initState();
    unawaited(_checkForUpdate());
  }

  @override
  void didUpdateWidget(AppUpdateGate oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.releaseBucketUrl != widget.releaseBucketUrl ||
        oldWidget.currentVersion != widget.currentVersion ||
        oldWidget.updateService != widget.updateService) {
      _notifiedAssetKey = null;
      unawaited(_checkForUpdate());
    }
  }

  Future<void> _checkForUpdate() async {
    if (_checking) return;
    final platform = _currentPlatform();
    if (platform == null) return;

    try {
      final enabled = await widget.autoUpdatePromptStore.read();
      if (!enabled) return;
    } catch (_) {
      return;
    }

    setState(() => _checking = true);
    try {
      final update = await _updateService.checkForUpdate(
        bucketUrl: widget.releaseBucketUrl,
        currentVersion: widget.currentVersion,
        platform: platform,
      );
      if (!mounted) return;
      setState(() => _checking = false);
      if (update == null || update.asset.key == _notifiedAssetKey) return;
      final ignoredVersion = await widget.autoUpdatePromptStore
          .readIgnoredVersion();
      if (!mounted) return;
      if (ignoredVersion != null &&
          compareAppVersions(update.latestVersion, ignoredVersion) <= 0) {
        return;
      }
      _notifiedAssetKey = update.asset.key;
      widget.onUpdateAvailable?.call(update);
    } catch (error) {
      if (!mounted) return;
      setState(() => _checking = false);
    }
  }

  AppUpdatePlatform? _currentPlatform() {
    final override = widget.platformOverride;
    if (override != null) return override;
    return appUpdatePlatformForOperatingSystem(Platform.operatingSystem);
  }

  @override
  Widget build(BuildContext context) => widget.child;
}

class AppUpdatePage extends StatelessWidget {
  const AppUpdatePage({
    super.key,
    required this.update,
    required this.checking,
    required this.downloading,
    required this.downloadedBytes,
    required this.onDownload,
    required this.onBack,
    required this.onIgnoreVersion,
    required this.onRefresh,
    this.downloadTotalBytes,
    this.error,
    this.wrapInScaffold = false,
  });

  final AvailableAppUpdate update;
  final bool checking;
  final bool downloading;
  final int downloadedBytes;
  final int? downloadTotalBytes;
  final String? error;
  final VoidCallback onDownload;
  final VoidCallback onBack;
  final VoidCallback onIgnoreVersion;
  final VoidCallback onRefresh;
  final bool wrapInScaffold;

  double? get _downloadProgress {
    final total = downloadTotalBytes;
    if (total == null || total <= 0) return null;
    return (downloadedBytes / total).clamp(0.0, 1.0);
  }

  @override
  Widget build(BuildContext context) {
    final page = SettingsScaffold(
      icon: Icons.system_update_alt_outlined,
      iconColor: UiColors.controlAccent,
      title: '发现新版本',
      onBack: downloading ? null : onBack,
      headerAction: ButtonIcon(
        tooltip: '重新检查',
        icon: const Icon(Icons.refresh),
        loading: checking,
        onPressed: checking || downloading ? null : onRefresh,
        size: 38,
      ),
      body: SettingsList(
        children: [
          SettingsCard(
            title: '版本更新',
            trailing: _UpdateBadge(version: update.latestVersion),
            children: [
              _VersionLine(
                label: '当前版本',
                value: appVersionLabel(update.currentVersion),
              ),
              _VersionLine(
                label: '最新版本',
                value: appVersionLabel(update.latestVersion),
                accent: true,
              ),
              _VersionLine(
                label: '发行时间',
                value: releaseTimeLabel(update.asset.releasedAt),
                scaleValueToFit: true,
              ),
              _VersionLog(value: releaseNotesLabel(update.releaseNotes)),
              if (downloading) ...[
                const SizedBox(height: 2),
                ClipRRect(
                  borderRadius: BorderRadius.circular(UiRadii.sm),
                  child: LinearProgressIndicator(
                    value: _downloadProgress,
                    minHeight: 8,
                    color: UiColors.controlAccent,
                    backgroundColor: UiColors.surfacePressed,
                  ),
                ),
                Text(
                  _downloadProgress == null
                      ? '正在下载更新'
                      : '正在下载更新 ${(_downloadProgress! * 100).round()}%',
                  style: UiTypography.label,
                ),
              ],
            ],
          ),
          if (error != null)
            SettingsCard(
              title: '更新失败',
              danger: true,
              children: [
                Text(
                  error!,
                  style: UiTypography.label.copyWith(
                    color: UiColors.danger,
                    height: 1.5,
                  ),
                ),
              ],
            ),
          _UpdateActions(
            downloading: downloading,
            onDownload: onDownload,
            onIgnoreVersion: onIgnoreVersion,
          ),
        ],
      ),
    );
    if (!wrapInScaffold) return page;
    return Scaffold(backgroundColor: UiColors.surfaceLow, body: page);
  }
}

class _VersionLine extends StatelessWidget {
  const _VersionLine({
    required this.label,
    required this.value,
    this.accent = false,
    this.scaleValueToFit = false,
  });

  final String label;
  final String value;
  final bool accent;
  final bool scaleValueToFit;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        SizedBox(width: 86, child: Text(label, style: UiTypography.label)),
        Expanded(child: scaleValueToFit ? _scaledValue() : _valueText()),
      ],
    );
  }

  Widget _scaledValue() {
    return Align(
      alignment: Alignment.centerLeft,
      child: FittedBox(
        fit: BoxFit.scaleDown,
        alignment: Alignment.centerLeft,
        child: _valueText(overflow: TextOverflow.visible),
      ),
    );
  }

  Widget _valueText({TextOverflow overflow = TextOverflow.ellipsis}) {
    return Text(
      value,
      maxLines: 1,
      softWrap: false,
      overflow: overflow,
      style: TextStyle(
        color: accent ? UiColors.accent : UiColors.text,
        fontSize: 15,
        fontWeight: FontWeight.w600,
        letterSpacing: 0,
      ),
    );
  }
}

class _VersionLog extends StatelessWidget {
  const _VersionLog({required this.value});

  final String value;

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SizedBox(width: 86, child: Text('版本日志', style: UiTypography.label)),
        Expanded(
          child: Text(
            value,
            style: const TextStyle(
              color: UiColors.text,
              fontSize: 14,
              fontWeight: FontWeight.w500,
              height: 1.5,
              letterSpacing: 0,
            ),
          ),
        ),
      ],
    );
  }
}

class _UpdateBadge extends StatelessWidget {
  const _UpdateBadge({required this.version});

  final String version;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: UiColors.selected,
        borderRadius: BorderRadius.circular(UiRadii.sm),
        border: Border.all(color: UiColors.selectedBorder),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
        child: Text(
          appVersionLabel(version),
          style: UiTypography.label.copyWith(
            color: UiColors.accent,
            fontWeight: FontWeight.w700,
          ),
        ),
      ),
    );
  }
}

class _UpdateActions extends StatelessWidget {
  const _UpdateActions({
    required this.downloading,
    required this.onDownload,
    required this.onIgnoreVersion,
  });

  final bool downloading;
  final VoidCallback onDownload;
  final VoidCallback onIgnoreVersion;

  @override
  Widget build(BuildContext context) {
    return ResponsiveDialogActionBar(
      expanded: true,
      actions: [
        ResponsiveDialogAction(
          label: '忽略此版本',
          icon: Icons.notifications_off_outlined,
          onPressed: downloading ? null : onIgnoreVersion,
        ),
        ResponsiveDialogAction(
          label: '下载新版本',
          icon: Icons.download_for_offline_outlined,
          tone: ButtonTone.primary,
          loading: downloading,
          onPressed: downloading ? null : onDownload,
        ),
      ],
    );
  }
}
