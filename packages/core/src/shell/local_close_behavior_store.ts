import { CloseBehaviorStore, closeBehaviorFromStorageValue, closeBehaviorStorageValue } from '../app/close_behavior';
import type { CloseBehavior } from '../app/close_behavior';
import { platform } from '../platform/platform';
import type { PreferencesStore } from '../platform/platform';

export class LocalCloseBehaviorStore extends CloseBehaviorStore {
  constructor(opts: { preferences?: PreferencesStore } = {}) {
    super();
    this._preferences = opts.preferences ?? null;
  }

  private readonly _preferences: PreferencesStore | null;

  private static readonly _closeBehaviorKey = 'gang.closeBehavior';

  private get _prefs(): PreferencesStore {
    return this._preferences ?? platform().preferences;
  }

  override async read(): Promise<CloseBehavior> {
    return closeBehaviorFromStorageValue(this._prefs.getString(LocalCloseBehaviorStore._closeBehaviorKey));
  }

  override async write(behavior: CloseBehavior): Promise<void> {
    await this._prefs.setString(LocalCloseBehaviorStore._closeBehaviorKey, closeBehaviorStorageValue(behavior));
  }
}
