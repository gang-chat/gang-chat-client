#!/usr/bin/env bash
# Builds the web client (apps/web: the shared React UI, like the Electron
# desktop app) for https://ky-z.com/gang-chat/app/ into build/web.
#
# The app is served from the same origin as the API
# (https://ky-z.com/gang-chat/api/v1), so it needs no CORS setup and inherits
# the site's HTTPS, which browsers require for the microphone, camera and
# screen sharing.
#
# Usage:
#   tool/build_web.sh                      # base href /gang-chat/app/
#   GANG_WEB_BASE_HREF=/staging/ tool/build_web.sh
#   GANG_CHAT_VERSION=1.2.3 tool/build_web.sh   # version shown in Settings
#
# GANG_API_BASE_URL / GANG_ASSET_BASE_URL / GANG_RELEASE_BUCKET_URL override
# assets/config/app_config.json, and GANG_CHAT_RELEASED_AT sets the release
# time shown in Settings (the release workflow sets both version variables).
# Extra arguments are passed through to `vite build`.
set -euo pipefail

cd "$(dirname "$0")/.."

base_href="${GANG_WEB_BASE_HREF:-/gang-chat/app/}"
case "$base_href" in
  /*/ | /) ;;
  *)
    echo "GANG_WEB_BASE_HREF must start and end with '/': $base_href" >&2
    exit 1
    ;;
esac
export GANG_WEB_BASE_HREF="$base_href"

# Install the npm workspaces once; CI runs `npm ci` itself first.
if [ ! -f node_modules/.package-lock.json ]; then
  npm ci
fi

# Fonts, icons and the Material icon font are bundled into build/web/assets,
# so the page loads nothing from Google's CDNs (blocked in mainland China).
npm run build -w @gang-chat/web -- "$@"

echo "Built build/web for base href $base_href"
