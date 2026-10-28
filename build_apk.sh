#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
GRADLE_VERSION="8.11.1"
CACHE_DIR="${XDG_CACHE_HOME:-$HOME/.cache}/descargasmp3-limpia"
GRADLE_BIN="$CACHE_DIR/gradle-$GRADLE_VERSION/bin/gradle"

if [[ ! -d "$SDK_DIR/platforms/android-35" ]]; then
  echo "ERROR: falta Android SDK Platform 35 en $SDK_DIR/platforms/android-35"
  exit 1
fi
if [[ ! -x "$GRADLE_BIN" ]]; then
  mkdir -p "$CACHE_DIR"
  ZIP="$CACHE_DIR/gradle-$GRADLE_VERSION-bin.zip"
  curl -fL --retry 3 "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o "$ZIP"
  unzip -q -o "$ZIP" -d "$CACHE_DIR"
fi
printf 'sdk.dir=%s\n' "$SDK_DIR" > "$ROOT/local.properties"
cd "$ROOT"
"$GRADLE_BIN" --no-daemon clean assembleDebug
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
test -s "$APK"
echo "APK GENERADA: $APK"
ls -lh "$APK"
