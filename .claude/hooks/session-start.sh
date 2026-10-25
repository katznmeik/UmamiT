#!/bin/bash
# SessionStart hook for Claude Code cloud sessions (task M1-00, issue #8).
#
# Installs a pinned Android SDK (cmdline-tools, platform-tools,
# platforms;android-36, build-tools) so `./gradlew assembleDebug` works in a
# fresh cloud container. No emulator or system images: the container has no
# KVM; instrumented tests run in the CI emulator job.
#
# - Runs only when CLAUDE_CODE_REMOTE=true; otherwise exits 0 immediately.
# - SDK location: /opt/android-sdk, or $HOME/android-sdk if /opt is not
#   writable. UMAMIT_ANDROID_SDK_ROOT overrides it (for testing the hook).
# - Idempotent: when every pinned package is present it only writes the
#   environment variables and exits (about a second).
# - Writes ANDROID_HOME, ANDROID_SDK_ROOT and PATH to "$CLAUDE_ENV_FILE".
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

# ---- Pinned versions (update together; see dl.google.com/android/repository/repository2-3.xml)
CMDLINE_TOOLS_BUILD="16111833" # cmdline-tools 23.0
CMDLINE_TOOLS_SHA1="e025545c62a8e64c7559119566a569fb1dec5f60"
PLATFORM="platforms;android-36"
BUILD_TOOLS="build-tools;36.1.0"
PACKAGES=("platform-tools" "$PLATFORM" "$BUILD_TOOLS")

log() { echo "[session-start] $*" >&2; }

pick_sdk_root() {
  if [ -n "${UMAMIT_ANDROID_SDK_ROOT:-}" ]; then
    echo "$UMAMIT_ANDROID_SDK_ROOT"
    return
  fi
  local preferred="/opt/android-sdk"
  if mkdir -p "$preferred" 2>/dev/null && [ -w "$preferred" ]; then
    echo "$preferred"
  else
    echo "${HOME}/android-sdk"
  fi
}

# download URL DEST: 4 attempts with exponential backoff (2, 4, 8 s).
download() {
  local url="$1" dest="$2" attempt=1 delay=2
  while true; do
    if curl -fsSL --connect-timeout 20 -o "$dest" "$url"; then
      return 0
    fi
    if [ "$attempt" -ge 4 ]; then
      log "download failed after $attempt attempts: $url"
      return 1
    fi
    log "download attempt $attempt failed, retrying in ${delay}s: $url"
    sleep "$delay"
    attempt=$((attempt + 1))
    delay=$((delay * 2))
  done
}

# Path of a package inside the SDK ("build-tools;36.1.0" -> build-tools/36.1.0).
pkg_dir() { echo "$SDK_ROOT/${1//;//}"; }

write_env() {
  if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
    {
      echo "export ANDROID_HOME=\"$SDK_ROOT\""
      echo "export ANDROID_SDK_ROOT=\"$SDK_ROOT\""
      echo "export PATH=\"$SDK_ROOT/cmdline-tools/latest/bin:$SDK_ROOT/platform-tools:\$PATH\""
    } >>"$CLAUDE_ENV_FILE"
    log "environment written to CLAUDE_ENV_FILE"
  else
    log "CLAUDE_ENV_FILE not set; export ANDROID_HOME=$SDK_ROOT yourself"
  fi
}

SDK_ROOT="$(pick_sdk_root)"
mkdir -p "$SDK_ROOT"
SDKMANAGER="$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
STAMP="$SDK_ROOT/cmdline-tools/latest/.umamit-build-$CMDLINE_TOOLS_BUILD"

# ---- Fast path: everything pinned is already present.
all_present=true
[ -f "$STAMP" ] || all_present=false
for pkg in "${PACKAGES[@]}"; do
  [ -f "$(pkg_dir "$pkg")/package.xml" ] || all_present=false
done
if $all_present; then
  log "Android SDK up to date at $SDK_ROOT"
  write_env
  exit 0
fi

log "installing Android SDK into $SDK_ROOT"

# ---- cmdline-tools (pinned zip build).
if [ ! -f "$STAMP" ]; then
  tmp="$(mktemp -d)"
  trap 'rm -rf "$tmp"' EXIT
  zip="$tmp/cmdline-tools.zip"
  log "downloading cmdline-tools build $CMDLINE_TOOLS_BUILD"
  download "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_BUILD}_latest.zip" "$zip"
  echo "$CMDLINE_TOOLS_SHA1  $zip" | sha1sum -c --quiet - || {
    log "checksum mismatch for cmdline-tools"
    exit 1
  }
  unzip -q "$zip" -d "$tmp/unpacked"
  rm -rf "$SDK_ROOT/cmdline-tools/latest"
  mkdir -p "$SDK_ROOT/cmdline-tools"
  mv "$tmp/unpacked/cmdline-tools" "$SDK_ROOT/cmdline-tools/latest"
  touch "$STAMP"
fi

# ---- Licences and packages (sdkmanager retries itself poorly, so wrap it).
log "accepting licences"
yes 2>/dev/null | "$SDKMANAGER" --sdk_root="$SDK_ROOT" --licenses >/dev/null 2>&1 || true

attempt=1
delay=2
until "$SDKMANAGER" --sdk_root="$SDK_ROOT" --install "${PACKAGES[@]}" >/dev/null; do
  if [ "$attempt" -ge 4 ]; then
    log "sdkmanager failed after $attempt attempts"
    exit 1
  fi
  log "sdkmanager attempt $attempt failed, retrying in ${delay}s"
  sleep "$delay"
  attempt=$((attempt + 1))
  delay=$((delay * 2))
done

for pkg in "${PACKAGES[@]}"; do
  [ -f "$(pkg_dir "$pkg")/package.xml" ] || {
    log "package missing after install: $pkg"
    exit 1
  }
done

log "installed: ${PACKAGES[*]}"
write_env
