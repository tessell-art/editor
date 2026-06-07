#!/usr/bin/env bash
# Build the Android APK end to end: Scala.js -> Vite dist/ -> Gradle assemble.
# `npm run build` invokes the Scala.js linker (fullLinkJS) via the Vite plugin,
# so all three toolchains run from this one script (ADR-005 build pipeline).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
EDITOR_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

VARIANT="${1:-Debug}" # Debug | Release

echo "==> [1/2] Building web bundle (sbt fullLinkJS + Vite) in $EDITOR_DIR"
( cd "$EDITOR_DIR" && npm run build )

echo "==> [2/2] Gradle assemble$VARIANT"
( cd "$SCRIPT_DIR" && ./gradlew ":app:assemble${VARIANT}" )

echo "==> Done. APK(s):"
find "$SCRIPT_DIR/app/build/outputs/apk" -name '*.apk' 2>/dev/null || true
