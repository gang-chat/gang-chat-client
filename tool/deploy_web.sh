#!/usr/bin/env bash
# Builds the web client and deploys it to the ky-z.com server behind
# https://ky-z.com/gang-chat/app/ (see deploy/web/nginx-gang-chat-app.conf).
#
# Each deploy uploads into <dir>/releases/<UTC timestamp>/ and then atomically
# repoints <dir>/current, so visitors never load a half-uploaded app. The
# newest GANG_WEB_KEEP_RELEASES releases are kept for rollback:
#   ssh <target> 'ln -sfn releases/<older> <dir>/current.tmp && mv -Tf <dir>/current.tmp <dir>/current'
#
# Usage:
#   GANG_WEB_DEPLOY_SSH=deploy@ky-z.com tool/deploy_web.sh
#   GANG_WEB_DEPLOY_SSH=deploy@ky-z.com tool/deploy_web.sh --no-build
#   GANG_WEB_DEPLOY_DRY_RUN=1 GANG_WEB_DEPLOY_SSH=deploy@ky-z.com tool/deploy_web.sh
#
# Environment:
#   GANG_WEB_DEPLOY_SSH       required; SSH target (user@host or a ~/.ssh/config alias)
#   GANG_WEB_DEPLOY_DIR       remote root, default /var/www/gang-chat-app
#   GANG_WEB_KEEP_RELEASES    releases to keep, default 5
#   GANG_WEB_DEPLOY_DRY_RUN   1 = print the commands without running them
# Build variables (GANG_WEB_BASE_HREF, GANG_CHAT_VERSION) are read by
# tool/build_web.sh.
#
# The SSH user needs write access to GANG_WEB_DEPLOY_DIR, and nginx needs read
# access to it. The server is assumed to be Linux (GNU `mv -T`).
set -euo pipefail

cd "$(dirname "$0")/.."

target="${GANG_WEB_DEPLOY_SSH:-}"
remote_dir="${GANG_WEB_DEPLOY_DIR:-/var/www/gang-chat-app}"
keep="${GANG_WEB_KEEP_RELEASES:-5}"
dry_run="${GANG_WEB_DEPLOY_DRY_RUN:-0}"
build=1

for arg in "$@"; do
  case "$arg" in
    --no-build) build=0 ;;
    *)
      echo "unknown argument: $arg" >&2
      exit 1
      ;;
  esac
done

if [ -z "$target" ]; then
  echo "Set GANG_WEB_DEPLOY_SSH to the server's SSH target, e.g. deploy@ky-z.com" >&2
  exit 1
fi
case "$keep" in
  '' | *[!0-9]* | 0)
    echo "GANG_WEB_KEEP_RELEASES must be a positive integer: $keep" >&2
    exit 1
    ;;
esac

run() {
  if [ "$dry_run" = 1 ]; then
    printf '+'
    printf ' %q' "$@"
    printf '\n'
  else
    "$@"
  fi
}

if [ "$build" = 1 ]; then
  run tool/build_web.sh
fi
if [ "$dry_run" != 1 ] && [ ! -f build/web/index.html ]; then
  echo "build/web/index.html is missing; run tool/build_web.sh first" >&2
  exit 1
fi

release="$(date -u +%Y%m%d%H%M%S)"
release_dir="$remote_dir/releases/$release"

echo "Uploading build/web to $target:$release_dir"
run ssh "$target" mkdir -p "$release_dir"
run rsync -az --delete build/web/ "$target:$release_dir/"

echo "Switching $remote_dir/current to $release"
remote_script='
set -euo pipefail
dir="$1"
release="$2"
keep="$3"
cd "$dir"
ln -sfn "releases/$release" current.tmp
mv -Tf current.tmp current
# Keep the newest releases; timestamps sort chronologically.
ls -1 releases | sort -r | tail -n "+$((keep + 1))" | while read -r old; do
  [ -n "$old" ] && [ "$old" != "$release" ] && rm -rf -- "releases/$old"
done
'
if [ "$dry_run" = 1 ]; then
  printf '+ ssh %q bash -s -- %q %q %q <<remote script>>\n' \
    "$target" "$remote_dir" "$release" "$keep"
else
  ssh "$target" bash -s -- "$remote_dir" "$release" "$keep" <<<"$remote_script"
fi

echo "Deployed $release. Open https://ky-z.com/gang-chat/app/"
