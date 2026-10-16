/**
 * Application-wide registry of async cleanup callbacks that must run before
 * the process exits (port of `ShutdownHooks`). Used so the window close
 * handler can flush in-flight work owned by deeply nested components (e.g.
 * leaving a live voice session) before the window is destroyed.
 *
 * Hooks run in registration order; each is best-effort. Callers wrap
 * [runAll] in their own timeout.
 */
type Hook = () => Promise<void>;

const hooks: Hook[] = [];

export const ShutdownHooks = {
  register(hook: Hook): object {
    hooks.push(hook);
    return hook;
  },

  unregister(token: object): void {
    const index = hooks.indexOf(token as Hook);
    if (index >= 0) hooks.splice(index, 1);
  },

  async runAll(): Promise<void> {
    for (const hook of [...hooks]) {
      try {
        await hook();
      } catch {
        // swallow — shutdown is best-effort
      }
    }
  },
};
