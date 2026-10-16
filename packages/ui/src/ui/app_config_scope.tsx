import { createContext, useContext, type ReactNode } from 'react';

import { AppConfig } from '@gang-chat/core/config/app_config';

const AppConfigContext = createContext<AppConfig | null>(null);

let _defaults: AppConfig | null = null;

function defaults(): AppConfig {
  _defaults ??= AppConfig.defaults();
  return _defaults;
}

/** Exposes the active [AppConfig] to descendants (Dart `AppConfigScope`). */
export function AppConfigScope({ config, children }: { config: AppConfig; children?: ReactNode }) {
  return <AppConfigContext.Provider value={config}>{children}</AppConfigContext.Provider>;
}

/** Port of `AppConfigScope.of(context)` (a hook). Falls back to `AppConfig.defaults()`. */
export function useAppConfig(): AppConfig {
  return useContext(AppConfigContext) ?? defaults();
}

/** `AppConfigScope.of` is the [useAppConfig] hook. */
AppConfigScope.of = useAppConfig;
