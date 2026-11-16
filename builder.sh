#!/usr/bin/env bash
# builder.sh — one-command build for Locked.
#
#   ./builder.sh            build a debug APK  -> dist/locked-v<version>-debug.apk
#                           (older versions stay in dist/; index.html is refreshed)
#   ./builder.sh release    build a release APK -> dist/locked-v<version>-release-unsigned.apk
#   ./builder.sh install    build debug and `adb install -r` it
#   ./builder.sh page       only refresh the download page (index.html) from dist/
#   ./builder.sh clean      gradle clean
#
# The version comes from app/build.gradle.kts (appVersionName / appVersionCode).
#
# Installs JDK 17 and the Android SDK if missing, writes local.properties,
# bootstraps the Gradle wrapper, then runs the build. Safe to re-run.
set -euo pipefail
cd "$(dirname "$0")"

CMD="${1:-debug}"

# Commands that don't need the Android toolchain.
if [ "$CMD" = "page" ]; then
  command -v python3 >/dev/null 2>&1 || { echo "python3 is required for 'page'." >&2; exit 1; }
  exec python3 tools/update_landing.py
fi
SDK_DIR="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/android-sdk}}"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
GRADLE_VERSION="8.9"
GRADLE_URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
GRADLE_DIST_DIR="$HOME/.gradle-dist"

log()  { printf '\033[1;35m[locked]\033[0m %s\n' "$*"; }
fail() { printf '\033[1;31m[locked] %s\033[0m\n' "$*" >&2; exit 1; }

SUDO=""
if [ "$(id -u)" -ne 0 ] && command -v sudo >/dev/null 2>&1; then SUDO="sudo"; fi

# ---------------------------------------------------------------- 1. JDK 17+
java_major() {
  local java_bin="${1:-java}"
  "$java_bin" -version 2>&1 | awk -F'[".]' '/version/ { print ($2 == "1") ? $3 : $2; exit }'
}

need_jdk=1
if command -v java >/dev/null 2>&1 && command -v javac >/dev/null 2>&1; then
  major="$(java_major || echo 0)"
  if [ "${major:-0}" -ge 17 ] && [ "${major:-0}" -le 21 ]; then need_jdk=0; fi
fi

if [ "$need_jdk" -eq 1 ]; then
  log "Installing JDK 17…"
  if command -v apt-get >/dev/null 2>&1; then
    $SUDO apt-get update -y
    $SUDO apt-get install -y openjdk-17-jdk-headless curl unzip
  else
    fail "No JDK 17–21 found and apt-get isn't available. Install JDK 17 manually and re-run."
  fi
fi

java_home_major=0
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  java_home_major="$(java_major "$JAVA_HOME/bin/java" || echo 0)"
fi

if [ "$need_jdk" -eq 1 ] || [ "$java_home_major" -lt 17 ] || [ "$java_home_major" -gt 21 ]; then
  for candidate in /usr/lib/jvm/java-17-openjdk-* /usr/lib/jvm/jdk-17*; do
    if [ -x "$candidate/bin/java" ] && [ -x "$candidate/bin/javac" ]; then
      JAVA_HOME="$candidate"
      export JAVA_HOME
      break
    fi
  done
  [ -n "${JAVA_HOME:-}" ] || fail "JDK 17 was installed but its location could not be determined."
fi

if [ -n "${JAVA_HOME:-}" ]; then
  export PATH="$JAVA_HOME/bin:$PATH"
fi
log "Using Java: $(java -version 2>&1 | grep -m1 version)"

command -v curl  >/dev/null 2>&1 || fail "curl is required."
command -v unzip >/dev/null 2>&1 || fail "unzip is required."

# ---------------------------------------------------------------- 2. Android SDK
SDKMANAGER="$SDK_DIR/cmdline-tools/latest/bin/sdkmanager"

if [ ! -x "$SDKMANAGER" ]; then
  log "Installing Android command-line tools into $SDK_DIR…"
  mkdir -p "$SDK_DIR/cmdline-tools"
  tmp="$(mktemp -d)"
  curl -fL --retry 3 -o "$tmp/cmdline-tools.zip" "$CMDLINE_TOOLS_URL"
  unzip -q "$tmp/cmdline-tools.zip" -d "$tmp"
  rm -rf "$SDK_DIR/cmdline-tools/latest"
  mv "$tmp/cmdline-tools" "$SDK_DIR/cmdline-tools/latest"
  rm -rf "$tmp"
fi

export ANDROID_SDK_ROOT="$SDK_DIR"
export ANDROID_HOME="$SDK_DIR"

if [ ! -d "$SDK_DIR/platforms/android-34" ] || [ ! -d "$SDK_DIR/build-tools/34.0.0" ] || [ ! -d "$SDK_DIR/platform-tools" ]; then
  log "Accepting licenses and installing SDK packages (platform 34, build-tools 34.0.0)…"
  # `yes` gets SIGPIPE when sdkmanager exits; that's expected, so don't let pipefail trip on it.
  yes | "$SDKMANAGER" --sdk_root="$SDK_DIR" --licenses >/dev/null 2>&1 || true
  "$SDKMANAGER" --sdk_root="$SDK_DIR" "platform-tools" "platforms;android-34" "build-tools;34.0.0"
fi

# ---------------------------------------------------------------- 3. local.properties
if ! grep -qs "^sdk.dir=$SDK_DIR\$" local.properties 2>/dev/null; then
  log "Writing local.properties"
  printf 'sdk.dir=%s\n' "$SDK_DIR" > local.properties
fi

# ---------------------------------------------------------------- 4. Gradle wrapper
if [ ! -f gradle/wrapper/gradle-wrapper.jar ]; then
  log "Bootstrapping Gradle wrapper (Gradle $GRADLE_VERSION)…"
  GRADLE_HOME_DIR="$GRADLE_DIST_DIR/gradle-$GRADLE_VERSION"
  if [ ! -x "$GRADLE_HOME_DIR/bin/gradle" ]; then
    mkdir -p "$GRADLE_DIST_DIR"
    tmp="$(mktemp -d)"
    curl -fL --retry 3 -o "$tmp/gradle.zip" "$GRADLE_URL"
    unzip -q "$tmp/gradle.zip" -d "$GRADLE_DIST_DIR"
    rm -rf "$tmp"
  fi
  "$GRADLE_HOME_DIR/bin/gradle" wrapper --gradle-version "$GRADLE_VERSION" --no-daemon
fi
chmod +x ./gradlew

# ---------------------------------------------------------------- 5. Build
mkdir -p dist

# Version comes from one place: app/build.gradle.kts
VERSION_NAME="$(sed -n 's/^val appVersionName = "\(.*\)".*/\1/p' app/build.gradle.kts | head -1)"
VERSION_CODE="$(sed -n 's/^val appVersionCode = \([0-9][0-9]*\).*/\1/p' app/build.gradle.kts | head -1)"
[ -n "$VERSION_NAME" ] && [ -n "$VERSION_CODE" ] \
  || fail "Couldn't read appVersionName / appVersionCode from app/build.gradle.kts"

if [ -f CHANGELOG.md ] && ! grep -qE "^## ${VERSION_NAME//./\\.} \(${VERSION_CODE}\)" CHANGELOG.md; then
  log "Warning: CHANGELOG.md has no '## ${VERSION_NAME} (${VERSION_CODE}) — <date>' entry yet."
fi

newest_apk() { ls -t "$1"/*.apk 2>/dev/null | head -1 || true; }

# publish_apk <built-apk> <kind>  ->  dist/locked-v<version>-<kind>.apk (+ .sha256)
publish_apk() {
  local src="$1"
  local kind="$2"
  local dest="dist/locked-v${VERSION_NAME}-${kind}.apk"
  [ -n "$src" ] && [ -f "$src" ] || fail "Build finished but no APK was found."
  if [ -f "$dest" ] && ! cmp -s "$src" "$dest"; then
    log "Replacing existing $dest. For a new release, bump appVersionName/appVersionCode first."
  fi
  cp "$src" "$dest"
  (cd dist && sha256sum "$(basename "$dest")" > "$(basename "$dest").sha256")
  log "Built v${VERSION_NAME} (code ${VERSION_CODE}) → $dest"
}

update_landing() {
  if command -v python3 >/dev/null 2>&1; then
    python3 tools/update_landing.py || log "Couldn't refresh index.html (see above)."
  else
    log "python3 not found; skipped refreshing index.html (run: ./builder.sh page)."
  fi
}

case "$CMD" in
  debug)
    ./gradlew --console=plain assembleDebug
    publish_apk "$(newest_apk app/build/outputs/apk/debug)" debug
    update_landing
    ;;
  release)
    ./gradlew --console=plain assembleRelease
    publish_apk "$(newest_apk app/build/outputs/apk/release)" release-unsigned
    log "Sign the release APK before installing it."
    ;;
  install)
    ./gradlew --console=plain assembleDebug
    publish_apk "$(newest_apk app/build/outputs/apk/debug)" debug
    update_landing
    command -v adb >/dev/null 2>&1 || export PATH="$SDK_DIR/platform-tools:$PATH"
    adb install -r "dist/locked-v${VERSION_NAME}-debug.apk"
    ;;
  clean)
    ./gradlew --console=plain clean
    ;;
  *)
    fail "Unknown command '$CMD'. Use: debug | release | install | page | clean"
    ;;
esac
