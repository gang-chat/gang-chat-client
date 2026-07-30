part of 'settings_page.dart';

class _DeviceSection extends StatelessWidget {
  const _DeviceSection({
    required this.title,
    required this.icon,
    required this.devices,
    required this.selectedDevice,
    required this.busyDeviceId,
    required this.emptyText,
    required this.fallbackLabel,
    required this.onSelect,
  });

  final String title;
  final IconData icon;
  final List<AudioDeviceInfo> devices;
  final AudioDeviceInfo? selectedDevice;
  final String? busyDeviceId;
  final String emptyText;
  final String fallbackLabel;
  final ValueChanged<AudioDeviceInfo> onSelect;

  @override
  Widget build(BuildContext context) {
    final labels = audioDeviceLabels(
      devices,
      fallbackLabel: fallbackLabel,
      labelOf: audioDeviceInfoLabel,
      deviceIdOf: audioDeviceInfoId,
    );
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Icon(icon, color: _cyan, size: 18),
            const SizedBox(width: 9),
            Text(
              title,
              style: const TextStyle(
                color: _textPrimary,
                fontSize: 15,
                fontWeight: FontWeight.w600,
              ),
            ),
          ],
        ),
        const SizedBox(height: 12),
        devices.isEmpty
            ? _EmptyDeviceRow(text: emptyText)
            : Column(
                children: [
                  for (final entry in devices.asMap().entries)
                    _DeviceRow(
                      device: entry.value,
                      label: labels[entry.key],
                      selected: isSameAudioDevice(
                        selectedDevice,
                        entry.value,
                        kindOf: audioDeviceInfoKind,
                        deviceIdOf: audioDeviceInfoId,
                      ),
                      busy: audioDeviceBusy(
                        entry.value,
                        busyDeviceId,
                        kindOf: audioDeviceInfoKind,
                        deviceIdOf: audioDeviceInfoId,
                      ),
                      onTap: () => onSelect(entry.value),
                    ),
                ],
              ),
      ],
    );
  }
}

class _AudioControlPanel extends StatelessWidget {
  const _AudioControlPanel({
    required this.title,
    required this.icon,
    required this.volume,
    required this.level,
    required this.testing,
    required this.testTooltip,
    required this.disabled,
    required this.onVolumeChanged,
    required this.onToggleTest,
    this.testDisabled = false,
  });

  final String title;
  final IconData icon;
  final double volume;
  final double level;
  final bool testing;
  final String testTooltip;
  final bool disabled;
  final ValueChanged<double> onVolumeChanged;
  final VoidCallback onToggleTest;
  final bool testDisabled;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Icon(icon, color: _cyan, size: 18),
            const SizedBox(width: 9),
            Expanded(
              child: Text(
                title,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  color: _textPrimary,
                  fontSize: 15,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ),
            Text(
              audioVolumePercentText(volume),
              style: const TextStyle(
                color: _textSecondary,
                fontSize: 13,
                fontWeight: FontWeight.w600,
              ),
            ),
          ],
        ),
        const SizedBox(height: 8),
        Row(
          children: [
            Expanded(
              child: SliderTheme(
                data: SliderTheme.of(context).copyWith(
                  activeTrackColor: _cyan,
                  inactiveTrackColor: _borderColor,
                  thumbColor: _textPrimary,
                  overlayColor: _cyan.withValues(alpha: 0.14),
                  trackHeight: 4,
                ),
                child: Slider(
                  value: volume,
                  min: 0,
                  max: 1,
                  onChanged: disabled ? null : onVolumeChanged,
                ),
              ),
            ),
          ],
        ),
        const SizedBox(height: 8),
        Row(
          children: [
            SizedBox(
              width: 96,
              child: Button(
                onPressed: disabled || testDisabled ? null : onToggleTest,
                tooltip: testTooltip,
                height: 42,
                width: double.infinity,
                tone: testing ? ButtonTone.primary : ButtonTone.neutral,
                selected: testing,
                padding: const EdgeInsets.symmetric(horizontal: 8),
                child: Text(
                  testing ? '停止测试' : '测试',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: _LevelMeter(level: level, active: testing),
            ),
          ],
        ),
      ],
    );
  }
}

class _LevelMeter extends StatelessWidget {
  const _LevelMeter({required this.level, required this.active});

  final double level;
  final bool active;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 52,
      child: DecoratedBox(
        decoration: const BoxDecoration(color: _primaryDark),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
          child: LayoutBuilder(
            builder: (context, constraints) {
              final segmentCount = audioLevelSegmentCount(constraints.maxWidth);
              final activeCount = activeAudioLevelSegmentCount(
                level: level,
                active: active,
                segmentCount: segmentCount,
              );
              return Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  for (var i = 0; i < segmentCount; i++)
                    AnimatedContainer(
                      duration: const Duration(milliseconds: 90),
                      width: 5,
                      height: 22,
                      decoration: BoxDecoration(
                        color: i < activeCount
                            ? _cyan
                            : const Color(0xFF2A2F38),
                        borderRadius: BorderRadius.circular(3),
                      ),
                    ),
                ],
              );
            },
          ),
        ),
      ),
    );
  }
}

class _ScreenShareQualitySection extends StatelessWidget {
  const _ScreenShareQualitySection({
    required this.selectedHeight,
    required this.selectedFrameRate,
    required this.onHeightSelected,
    required this.onFrameRateSelected,
    this.remoteUnavailable = false,
  });

  final int selectedHeight;
  final int selectedFrameRate;
  final ValueChanged<int> onHeightSelected;
  final ValueChanged<int> onFrameRateSelected;
  final bool remoteUnavailable;

  @override
  Widget build(BuildContext context) {
    final selectedResolution = normalizedScreenShareMaxHeight(selectedHeight);
    final platformFrameRateCap = !kIsWeb && Platform.isAndroid
        ? androidScreenShareFrameRateCap
        : desktopScreenShareFrameRateCap;
    final selectedFps = normalizedScreenShareFrameRate(
      selectedFrameRate,
      maxFrameRate: platformFrameRateCap,
    );
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            const Icon(Icons.screen_share_outlined, color: _cyan, size: 18),
            const SizedBox(width: 9),
            const Text(
              '屏幕共享画质',
              style: TextStyle(
                color: _textPrimary,
                fontSize: 15,
                fontWeight: FontWeight.w600,
              ),
            ),
          ],
        ),
        const SizedBox(height: 8),
        const Text(
          '分辨率、帧率和码率会一起调整；较低档位可以显著节省上行带宽。',
          style: TextStyle(color: _textSecondary, fontSize: 13, height: 1.4),
        ),
        const SizedBox(height: 12),
        if (remoteUnavailable)
          const _SettingsEmptyState(text: '该设置仅保存在用户设备，无法远程读取')
        else
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                '分辨率',
                style: TextStyle(
                  color: _textSecondary,
                  fontSize: 13,
                  fontWeight: FontWeight.w600,
                ),
              ),
              const SizedBox(height: 6),
              for (final height in screenShareHeightOptions)
                _ScreenShareResolutionRow(
                  label: screenShareResolutionLabel(height),
                  selected: height == selectedResolution,
                  onTap: () => onHeightSelected(height),
                ),
              const SizedBox(height: 12),
              const Text(
                '帧率',
                style: TextStyle(
                  color: _textSecondary,
                  fontSize: 13,
                  fontWeight: FontWeight.w600,
                ),
              ),
              const SizedBox(height: 6),
              for (final frameRate in screenShareFrameRateOptions)
                if (frameRate <= platformFrameRateCap)
                  _ScreenShareResolutionRow(
                    label: screenShareFrameRateLabel(frameRate),
                    selected: frameRate == selectedFps,
                    unselectedIcon: Icons.speed_outlined,
                    onTap: () => onFrameRateSelected(frameRate),
                  ),
            ],
          ),
      ],
    );
  }
}

class _ScreenShareResolutionRow extends StatelessWidget {
  const _ScreenShareResolutionRow({
    required this.label,
    required this.selected,
    required this.onTap,
    this.unselectedIcon = Icons.high_quality_outlined,
  });

  final String label;
  final bool selected;
  final VoidCallback onTap;
  final IconData unselectedIcon;

  @override
  Widget build(BuildContext context) {
    return PressableSurface(
      onPressed: onTap,
      height: 50,
      width: double.infinity,
      selected: selected,
      backgroundColor: _primaryDarkLow,
      selectedBackgroundColor: UiColors.selected,
      pressedBackgroundColor: _primaryDark,
      borderColor: _borderColor,
      selectedBorderColor: UiColors.selectedBorder,
      hoverLift: 3,
      pressDepth: 3,
      baseDepth: 5,
      padding: const EdgeInsets.symmetric(horizontal: 14),
      child: Row(
        children: [
          Expanded(
            child: Text(
              label,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                color: selected ? _textPrimary : _textSecondary,
                fontSize: 14,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
          if (selected)
            const Icon(Icons.check, color: _cyan, size: 18)
          else
            Icon(unselectedIcon, color: _textMuted, size: 18),
        ],
      ),
    );
  }
}

class _DeviceRow extends StatelessWidget {
  const _DeviceRow({
    required this.device,
    required this.label,
    required this.selected,
    required this.busy,
    required this.onTap,
  });

  final AudioDeviceInfo device;
  final String label;
  final bool selected;
  final bool busy;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return PressableSurface(
      onPressed: busy ? null : onTap,
      height: 50,
      width: double.infinity,
      selected: selected,
      backgroundColor: _primaryDarkLow,
      selectedBackgroundColor: UiColors.selected,
      pressedBackgroundColor: _primaryDark,
      borderColor: _borderColor,
      selectedBorderColor: UiColors.selectedBorder,
      hoverLift: 3,
      pressDepth: 3,
      baseDepth: 5,
      padding: const EdgeInsets.symmetric(horizontal: 14),
      child: Row(
        children: [
          Expanded(
            child: Text(
              label,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                color: selected ? _textPrimary : _textSecondary,
                fontSize: 14,
                fontWeight: selected ? FontWeight.w600 : FontWeight.w600,
              ),
            ),
          ),
          if (busy)
            const SizedBox.square(
              dimension: 16,
              child: CircularProgressIndicator(strokeWidth: 2, color: _cyan),
            )
          else if (selected)
            const Icon(Icons.check, color: _cyan, size: 18)
          else
            Icon(
              device.kind == 'audioinput' ? Icons.mic_none : Icons.volume_up,
              color: _textMuted,
              size: 18,
            ),
        ],
      ),
    );
  }
}

class _EmptyDeviceRow extends StatelessWidget {
  const _EmptyDeviceRow({required this.text});

  final String text;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 50,
      child: Align(
        alignment: Alignment.centerLeft,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 14),
          child: Text(
            text,
            style: const TextStyle(
              color: _textMuted,
              fontSize: 13,
              fontWeight: FontWeight.w600,
            ),
          ),
        ),
      ),
    );
  }
}
