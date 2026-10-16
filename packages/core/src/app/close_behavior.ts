export enum CloseBehavior {
  askEveryTime = 'askEveryTime',
  minimizeToTray = 'minimizeToTray',
  exitProgram = 'exitProgram',
}

export const defaultCloseBehavior = CloseBehavior.askEveryTime;

/**
 * Port of the `CloseBehaviorStorage` extension: `behavior.storageValue` →
 * `CloseBehaviorStorage.storageValue(behavior)`.
 */
export const CloseBehaviorStorage = {
  storageValue(behavior: CloseBehavior): string {
    return closeBehaviorStorageValue(behavior);
  },
};

export function closeBehaviorStorageValue(behavior: CloseBehavior): string {
  switch (behavior) {
    case CloseBehavior.askEveryTime:
      return 'ask_every_time';
    case CloseBehavior.minimizeToTray:
      return 'minimize_to_tray';
    case CloseBehavior.exitProgram:
      return 'exit_program';
  }
}

export function closeBehaviorFromStorageValue(value: string | null): CloseBehavior {
  switch (value) {
    case 'minimize_to_tray':
      return CloseBehavior.minimizeToTray;
    case 'exit_program':
      return CloseBehavior.exitProgram;
    default:
      return defaultCloseBehavior;
  }
}

export function closeBehaviorLabel(behavior: CloseBehavior): string {
  switch (behavior) {
    case CloseBehavior.askEveryTime:
      return '每次询问';
    case CloseBehavior.minimizeToTray:
      return '最小化到托盘';
    case CloseBehavior.exitProgram:
      return '直接退出程序';
  }
}

export function closeBehaviorDescription(behavior: CloseBehavior): string {
  switch (behavior) {
    case CloseBehavior.askEveryTime:
      return '关闭窗口时先询问本次要后台运行还是退出账号。';
    case CloseBehavior.minimizeToTray:
      return '关闭窗口时保留当前状态并在后台运行。';
    case CloseBehavior.exitProgram:
      return '关闭窗口时离开语音、退出账号并结束程序。';
  }
}

export class CloseBehaviorStore {
  read(): Promise<CloseBehavior> {
    throw new Error('CloseBehaviorStore.read must be implemented.');
  }

  write(behavior: CloseBehavior): Promise<void> {
    throw new Error('CloseBehaviorStore.write must be implemented.');
  }
}
