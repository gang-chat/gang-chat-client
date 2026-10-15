#!/usr/bin/env bash
# Builds the web client for https://ky-z.com/gang-chat/app/ into build/web.
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
# Extra arguments are passed through to `flutter build web`.
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

defines=()
if [ -n "${GANG_CHAT_VERSION:-}" ]; then
  defines+=("--dart-define=GANG_CHAT_VERSION=$GANG_CHAT_VERSION")
fi

tool/pub.sh get

# --no-web-resources-cdn bundles CanvasKit instead of loading it from
# www.gstatic.com, which is blocked in mainland China. Chinese fallback fonts
# still come from fonts.gstatic.com; see docs/web-spike.md.
flutter build web \
  --release \
  --no-web-resources-cdn \
  --base-href "$base_href" \
  ${defines[@]+"${defines[@]}"} \
  "$@"

echo "Built build/web for base href $base_href"
