#!/usr/bin/env python3
"""Fails when a text file is not UTF-8, has a BOM, or contains mojibake.

Mojibake (UTF-8 bytes decoded as GBK or Latin-1 and saved again) has reached
user-visible strings before, so CI rejects the telltale sequences. Run from the
repository root: `python3 tool/check_utf8.py`.
"""

import os
import sys

SKIPPED_DIRECTORIES = {
    ".codegraph", ".git", ".gradle", ".idea", ".kotlin", ".vscode", ".claude",
    "build", "node_modules", "out", "dist",
}

BINARY_EXTENSIONS = {
    ".a", ".aar", ".apk", ".app", ".bin", ".dll", ".dylib", ".exe", ".gif",
    ".icns", ".ico", ".jar", ".jks", ".jpeg", ".jpg", ".keystore", ".lib",
    ".lockfile", ".otf", ".png", ".so", ".ttf", ".webp", ".woff", ".woff2",
    ".zip",
}

MOJIBAKE_MARKERS = [
    "�", "Ã", "Â", "â€", "ä¸", "ä½", "å¥", "æœ", "鈥", "锛",
    "浣犵", "娴佺", "鍥藉", "涓嶇", "鐗堟", "鏂囦欢",
]

SELF = {"tool/check_utf8.py", "tool/check_utf8.dart"}


def main() -> int:
    failures = []
    for directory, dirnames, filenames in os.walk("."):
        dirnames[:] = [d for d in dirnames if d not in SKIPPED_DIRECTORIES]
        for name in filenames:
            path = os.path.relpath(os.path.join(directory, name)).replace(os.sep, "/")
            if os.path.splitext(name)[1].lower() in BINARY_EXTENSIONS:
                continue
            with open(path, "rb") as handle:
                data = handle.read()
            if data.startswith(b"\xef\xbb\xbf"):
                failures.append(f"{path}: has UTF-8 BOM; save as UTF-8 without BOM")
                continue
            try:
                text = data.decode("utf-8")
            except UnicodeDecodeError as error:
                failures.append(f"{path}: is not valid UTF-8 ({error.reason})")
                continue
            if path in SELF or path.endswith("android-port-status.md"):
                continue
            for marker in MOJIBAKE_MARKERS:
                if marker in text:
                    failures.append(f'{path}: contains suspicious mojibake marker "{marker}"')
                    break
    if not failures:
        print("UTF-8 check passed.")
        return 0
    print("UTF-8 check failed:", file=sys.stderr)
    for failure in failures:
        print(f"- {failure}", file=sys.stderr)
    return 1


if __name__ == "__main__":
    sys.exit(main())
