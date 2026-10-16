// A tiny method + path-pattern router. Patterns use `:name` segments
// (`/rooms/:roomId/messages`). When several patterns match, the one with the
// fewest parameters wins, so `/rooms/search` beats `/rooms/:roomId` without
// depending on registration order.

/**
 * A route table entry. `options.public` skips the bearer-token check; every
 * other route requires `Authorization: Bearer <access token>`.
 */
export function route(method, path, handler, options = {}) {
  return { method, path, handler, ...options };
}

export class Router {
  #routes = [];

  add({ method, path, handler, ...options }) {
    const keys = [];
    const source = path.replace(/:([A-Za-z_][A-Za-z0-9_]*)/g, (_, key) => {
      keys.push(key);
      return '([^/]+)';
    });
    this.#routes.push({ method, path, handler, options, keys, regex: new RegExp(`^${source}/?$`) });
  }

  /** Registers a route table, prefixing every path with `prefix`. */
  addAll(prefix, routes) {
    for (const entry of routes) this.add({ ...entry, path: `${prefix}${entry.path}` });
  }

  /**
   * Returns `{ route, params }` for a match, or `{ allowedMethods }` (possibly
   * empty) when nothing matches the method.
   */
  match(method, path) {
    const lookupMethod = method === 'HEAD' ? 'GET' : method;
    const allowedMethods = new Set();
    let best = null;
    for (const candidate of this.#routes) {
      const found = candidate.regex.exec(path);
      if (!found) continue;
      allowedMethods.add(candidate.method);
      if (candidate.method !== lookupMethod) continue;
      if (best == null || candidate.keys.length < best.candidate.keys.length) best = { candidate, found };
    }
    if (best == null) return { allowedMethods: [...allowedMethods] };
    const params = {};
    best.candidate.keys.forEach((key, index) => {
      params[key] = decodeURIComponent(best.found[index + 1]);
    });
    return { route: best.candidate, params };
  }

  get size() {
    return this.#routes.length;
  }
}
