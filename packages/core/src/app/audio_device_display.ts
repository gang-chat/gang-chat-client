export type AudioDeviceKindOf<T> = (device: T) => string;
export type AudioDeviceIdOf<T> = (device: T) => string;
export type AudioDeviceLabelOf<T> = (device: T) => string;
export type AudioDeviceGroupIdOf<T> = (device: T) => string;

export function audioDeviceKey(opts: { kind: string; deviceId: string }): string {
  return `${opts.kind}:${opts.deviceId}`;
}

export function audioDeviceKeyOf<T>(
  device: T,
  opts: { kindOf: AudioDeviceKindOf<T>; deviceIdOf: AudioDeviceIdOf<T> },
): string {
  return audioDeviceKey({ kind: opts.kindOf(device), deviceId: opts.deviceIdOf(device) });
}

export function canStartAudioDeviceSelection(busyDeviceId: string | null): boolean {
  return busyDeviceId == null;
}

export function audioDeviceBusy<T>(
  device: T,
  busyDeviceId: string | null,
  opts: { kindOf: AudioDeviceKindOf<T>; deviceIdOf: AudioDeviceIdOf<T> },
): boolean {
  return busyDeviceId === audioDeviceKeyOf(device, opts);
}

export function audioDevicePreferenceSaveFailureMessage(error: unknown): string {
  return '无法保存音频设备偏好';
}

export function isSameAudioDevice<T>(
  left: T | null | undefined,
  right: T,
  opts: { kindOf: AudioDeviceKindOf<T>; deviceIdOf: AudioDeviceIdOf<T> },
): boolean {
  const { kindOf, deviceIdOf } = opts;
  return left != null && deviceIdOf(left) === deviceIdOf(right) && kindOf(left) === kindOf(right);
}

export function audioDevicesByKind<T>(devices: readonly T[], kind: string, opts: { kindOf: AudioDeviceKindOf<T> }): T[] {
  return devices.filter((device) => opts.kindOf(device) === kind);
}

export function selectedAudioDeviceFrom<T>(
  devices: readonly T[],
  candidates: Iterable<T | null | undefined>,
  opts: { kindOf: AudioDeviceKindOf<T>; deviceIdOf: AudioDeviceIdOf<T> },
): T | null {
  if (devices.length === 0) return null;
  for (const candidate of candidates) {
    if (candidate == null) continue;
    for (const device of devices) {
      if (isSameAudioDevice(candidate, device, opts)) {
        return device;
      }
    }
  }
  return devices[0]!;
}

export function audioDeviceLabels<T>(
  devices: readonly T[],
  opts: { fallbackLabel: string; labelOf: AudioDeviceLabelOf<T>; deviceIdOf: AudioDeviceIdOf<T> },
): string[] {
  const { fallbackLabel, labelOf, deviceIdOf } = opts;
  const baseLabels = devices.map((device, index) =>
    audioDeviceLabel({
      label: labelOf(device),
      deviceId: deviceIdOf(device),
      index,
      fallbackLabel,
    }),
  );
  const totals = new Map<string, number>();
  for (const label of baseLabels) {
    totals.set(label, (totals.get(label) ?? 0) + 1);
  }
  const seen = new Map<string, number>();
  return baseLabels.map((label) => (totals.get(label) === 1 ? label : _labelWithDuplicateSuffix(label, seen)));
}

export function audioDeviceLabel(opts: { label: string; deviceId: string; index: number; fallbackLabel: string }): string {
  const { label, deviceId, index, fallbackLabel } = opts;
  const trimmed = label.trim();
  if (trimmed.length > 0) return trimmed;
  if (deviceId === 'default') return '系统默认';
  if (deviceId === 'communications') return '通信设备';
  return `${fallbackLabel} ${index + 1}`;
}

export function audioInputTestTooltip(testing: boolean): string {
  return testing ? '停止输入测试' : '测试输入音量';
}

export function audioOutputTestTooltip(testing: boolean): string {
  return testing ? '停止输出测试' : '测试输出音量';
}

function _labelWithDuplicateSuffix(label: string, seen: Map<string, number>): string {
  const count = (seen.get(label) ?? 0) + 1;
  seen.set(label, count);
  if (count === 1) return label;
  return `${label} #${count}`;
}
