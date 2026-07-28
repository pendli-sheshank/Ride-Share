#!/usr/bin/env bash
# Fails if a built APK/AAB does not actually contain the application's own compiled code.
#
# This guards against a specific, silent failure: if the Kotlin Android plugin is not
# applied, Gradle skips every .kt file, the build reports SUCCESS, and the artifact ships
# with resources and a manifest but no app classes — installing cleanly and crashing on
# launch. That is exactly the state this repo was in before the pipeline existed.
#
# Usage: verify-app-classes.sh <archive> [dex-prefix]
#   verify-app-classes.sh app/build/outputs/apk/debug/app-debug.apk
#   verify-app-classes.sh app/build/outputs/bundle/release/app-release.aab base/dex/
set -euo pipefail

ARCHIVE="${1:?usage: verify-app-classes.sh <archive> [dex-prefix]}"
PREFIX="${2:-}"
# Classes that only exist if the project's own sources were compiled.
# Check for any of these to be resilient to refactoring.
# SplitCruiserRepository lives in :shared, so finding it also proves the shared module was
# compiled and merged in — not just :app's own sources.
NEEDLES=(
  'com/splitcruiser/app/MainActivity'
  'com/splitcruiser/app/ui/MainViewModel'
  'com/splitcruiser/app/data/SplitCruiserRepository'
)

if [ ! -f "$ARCHIVE" ]; then
  echo "::error::Artifact not found: $ARCHIVE"
  exit 1
fi

# Class code is split across multiple dex files and which one holds a given class is not
# stable across builds, so check every dex rather than assuming classes.dex.
mapfile -t DEX_FILES < <(unzip -Z1 "$ARCHIVE" "${PREFIX}classes*.dex" 2>/dev/null || true)

if [ "${#DEX_FILES[@]}" -eq 0 ]; then
  echo "::error::$ARCHIVE contains no dex files — Kotlin did not compile."
  exit 1
fi

# Extract to a file rather than piping into grep: `grep -q` exits on the first match and
# closes the pipe, which kills unzip with SIGPIPE, and `set -o pipefail` would then report
# a successful match as a failed pipeline.
TMPDIR_DEX="$(mktemp -d)"
trap 'rm -rf "$TMPDIR_DEX"' EXIT

for dex in "${DEX_FILES[@]}"; do
  unzip -p "$ARCHIVE" "$dex" > "$TMPDIR_DEX/dex.bin"
  for needle in "${NEEDLES[@]}"; do
    if grep -qa "$needle" "$TMPDIR_DEX/dex.bin"; then
      echo "OK: found $needle in $dex (${#DEX_FILES[@]} dex files total)"
      exit 0
    fi
  done
done

echo "::error::$ARCHIVE has ${#DEX_FILES[@]} dex files but none contain any expected app classes."
echo "::error::Expected one of: ${NEEDLES[*]}"
echo "::error::The app's own code was not compiled into the artifact."
exit 1
