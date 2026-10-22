#!/usr/bin/env bash
# Verifies a signed UmamiT release APK (06 §6, §6.1; M1-03):
#   - the APK signature is valid (apksigner verify);
#   - it has exactly one signer whose certificate SHA-256 matches the expected
#     public fingerprint (default: signing/release-cert-sha256.txt);
#   - package name, versionName and versionCode match (aapt2 dump badging);
#   - the APK is not debuggable.
#
# Usage:
#   scripts/verify-release-apk.sh <apk> <expected-versionName> <expected-versionCode> [expected-cert-file]
#
# The expected-cert file holds the SHA-256 fingerprint as hex (colons and case
# are ignored). It defaults to $UMAMIT_EXPECTED_CERT_FILE, then to
# signing/release-cert-sha256.txt in this repository.
# Build tools: $BUILD_TOOLS_DIR, else $ANDROID_HOME (or $ANDROID_SDK_ROOT)/build-tools/36.1.0.
#
# Exits non-zero with a message starting with "ERROR:" on any mismatch.
set -euo pipefail

readonly EXPECTED_PACKAGE="com.umamit.app"
readonly BUILD_TOOLS_VERSION="36.1.0"

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

if [[ $# -lt 3 || $# -gt 4 ]]; then
  echo "Usage: $0 <apk> <expected-versionName> <expected-versionCode> [expected-cert-file]" >&2
  exit 2
fi

apk="$1"
expected_version="$2"
expected_code="$3"
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cert_file="${4:-${UMAMIT_EXPECTED_CERT_FILE:-$repo_root/signing/release-cert-sha256.txt}}"

[[ -f "$apk" ]] || fail "APK not found: $apk"
[[ "$expected_code" =~ ^[1-9][0-9]*$ ]] || fail "expected versionCode is not a positive integer: '$expected_code'"
[[ -f "$cert_file" ]] || fail "expected-fingerprint file not found: $cert_file"

sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
tools="${BUILD_TOOLS_DIR:-${sdk:+$sdk/build-tools/$BUILD_TOOLS_VERSION}}"
[[ -n "$tools" ]] || fail "set ANDROID_HOME, ANDROID_SDK_ROOT or BUILD_TOOLS_DIR"
apksigner="$tools/apksigner"
aapt2="$tools/aapt2"
[[ -x "$apksigner" ]] || fail "apksigner not found at $apksigner"
[[ -x "$aapt2" ]] || fail "aapt2 not found at $aapt2"

# Expected fingerprint: lowercase hex without colons or whitespace.
expected_fp="$(tr -d ':[:space:]' < "$cert_file" | tr '[:upper:]' '[:lower:]')"
[[ "$expected_fp" =~ ^[0-9a-f]{64}$ ]] \
  || fail "$cert_file does not contain a SHA-256 fingerprint (64 hex characters)"

# 1. Signature and signer certificate.
if ! signer_output="$("$apksigner" verify --print-certs "$apk" 2>&1)"; then
  echo "$signer_output" >&2
  fail "apksigner verify failed: the APK is unsigned or its signature is invalid"
fi
mapfile -t actual_fps < <(
  sed -n 's/^Signer #[0-9]* certificate SHA-256 digest: *//p' <<< "$signer_output" \
    | tr -d ':[:space:]' | tr '[:upper:]' '[:lower:]'
)
[[ ${#actual_fps[@]} -eq 1 ]] \
  || fail "expected exactly one signer certificate, found ${#actual_fps[@]}"
actual_fp="${actual_fps[0]}"
if [[ "$actual_fp" != "$expected_fp" ]]; then
  fail "certificate fingerprint mismatch: APK is signed with $actual_fp, expected $expected_fp (from $cert_file)"
fi
echo "OK: signature valid; certificate SHA-256 $actual_fp"

# 2. Package, version and debuggable flag.
badging="$("$aapt2" dump badging "$apk")" || fail "aapt2 dump badging failed for $apk"
package_line="$(grep -m1 '^package:' <<< "$badging")" || fail "no package line in aapt2 output"
actual_package="$(sed -n "s/.* name='\([^']*\)'.*/\1/p" <<< "$package_line")"
actual_code="$(sed -n "s/.* versionCode='\([^']*\)'.*/\1/p" <<< "$package_line")"
actual_version="$(sed -n "s/.* versionName='\([^']*\)'.*/\1/p" <<< "$package_line")"

[[ "$actual_package" == "$EXPECTED_PACKAGE" ]] \
  || fail "package name mismatch: APK has '$actual_package', expected '$EXPECTED_PACKAGE'"
[[ "$actual_version" == "$expected_version" ]] \
  || fail "versionName mismatch: APK has '$actual_version', expected '$expected_version'"
[[ "$actual_code" == "$expected_code" ]] \
  || fail "versionCode mismatch: APK has '$actual_code', expected '$expected_code'"
if grep -q '^application-debuggable' <<< "$badging"; then
  fail "the APK is debuggable; a release APK must not be"
fi
echo "OK: $actual_package versionName $actual_version, versionCode $actual_code, not debuggable"
