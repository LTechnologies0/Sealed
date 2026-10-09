#!/usr/bin/env bash
# Fail if a merge turned MTE off. Release must be async; debug may be sync.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET="${1:-}"

if [[ -z "$TARGET" ]]; then
  for candidate in \
    "$ROOT/app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml" \
    "$ROOT/app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml" \
    "$ROOT/app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml" \
    "$ROOT/app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml"
  do
    if [[ -f "$candidate" ]]; then
      TARGET="$candidate"
      break
    fi
  done
fi

if [[ -z "${TARGET:-}" || ! -f "$TARGET" ]]; then
  echo "No manifest found. Build first: ./gradlew :app:processDebugMainManifest" >&2
  exit 1
fi

DUMP="$(cat "$TARGET")"

if echo "$DUMP" | grep -Eiq 'memtagMode[^>]*(="?(off|none)"?|>(off|none)<)|android:memtagMode="(off|none)"'; then
  echo "FAIL: memtagMode is off/none in $TARGET" >&2
  exit 1
fi

if [[ "$TARGET" == *release* ]]; then
  if echo "$DUMP" | grep -Eq 'android:memtagMode="async"'; then
    echo "OK: memtagMode=async in $TARGET"
    exit 0
  fi
  echo "FAIL: release manifest must set android:memtagMode=async ($TARGET)" >&2
  exit 1
fi

if echo "$DUMP" | grep -Eq 'android:memtagMode="(async|sync)"'; then
  echo "OK: memtagMode active in $TARGET"
  exit 0
fi

echo "FAIL: android:memtagMode missing in $TARGET" >&2
exit 1
