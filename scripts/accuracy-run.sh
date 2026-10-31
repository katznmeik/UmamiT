#!/usr/bin/env bash
# Timer accuracy run (04 §12, TMR-30; #52, D-067) on a booted emulator or a
# device with adb: installs core:timer's instrumented test APK, runs
# AccuracyRunTest (50 × 60 s steps with the screen off, about 55 minutes) and
# copies its report out of the test app. `.github/workflows/accuracy.yml` runs
# it before each release candidate (09 §4a).
#
# Usage: ./gradlew :core:timer:assembleDebugAndroidTest && scripts/accuracy-run.sh
#   ACCURACY_STEPS            steps to run (default 50; fewer only to try the workflow)
#   ACCURACY_VARIANT          standard (default) or alongside: a quick timer beside
#                             every step, both timers measured (D-098, #137)
#   ACCURACY_INJECT_DELAY_MS  gate check: every pre-alert and alert this many ms late (default 0)
#   ACCURACY_OUT              report directory (default build/accuracy)
#
# Writes to ACCURACY_OUT: accuracy.md (the summary), accuracy.csv (one row per
# fire time), diagnostics.txt (the diagnostics log lines it read),
# instrumentation.txt and logcat.txt. Exits 0 only when the test passed.
set -uo pipefail

readonly APK="core/timer/build/outputs/apk/androidTest/debug/timer-debug-androidTest.apk"
readonly PACKAGE="com.umamit.core.timer.test"
readonly RUNNER="androidx.test.runner.AndroidJUnitRunner"
readonly TEST_CLASS="com.umamit.core.timer.AccuracyRunTest"
readonly REPORT_FILES=(accuracy.md accuracy.csv diagnostics.txt)

steps="${ACCURACY_STEPS:-50}"
variant="${ACCURACY_VARIANT:-standard}"
delay="${ACCURACY_INJECT_DELAY_MS:-0}"
out="${ACCURACY_OUT:-build/accuracy}"

if ! [[ "$steps" =~ ^[0-9]+$ ]] || ((steps < 1 || steps > 200)); then
  echo "::error::ACCURACY_STEPS must be a number from 1 to 200, was '$steps'."
  exit 2
fi
if [[ "$variant" != standard && "$variant" != alongside ]]; then
  echo "::error::ACCURACY_VARIANT must be standard or alongside, was '$variant'."
  exit 2
fi
if ! [[ "$delay" =~ ^[0-9]+$ ]] || ((delay > 60000)); then
  echo "::error::ACCURACY_INJECT_DELAY_MS must be a number from 0 to 60000, was '$delay'."
  exit 2
fi
if [[ ! -f "$APK" ]]; then
  echo "::error::$APK is missing: run ./gradlew :core:timer:assembleDebugAndroidTest first."
  exit 2
fi

mkdir -p "$out"
if ! adb install -r -t -g "$APK"; then
  echo "::error::Could not install $APK."
  exit 1
fi

# Progress in the job log while the run takes its hour: one line per step.
adb logcat -c || true
adb logcat -v time -s AccuracyRun:I &
logcat_pid=$!

echo "Accuracy run: $steps steps of 60 s, variant $variant, injected delay $delay ms."
adb shell am instrument -w \
  -e class "$TEST_CLASS" \
  -e umamitAccuracy true \
  -e umamitAccuracySteps "$steps" \
  -e umamitAccuracyVariant "$variant" \
  -e umamitAccuracyInjectDelayMs "$delay" \
  "$PACKAGE/$RUNNER" | tr -d '\r' | tee "$out/instrumentation.txt"

kill "$logcat_pid" 2>/dev/null || true
adb logcat -d -v time > "$out/logcat.txt" 2>/dev/null || true

# The report lives in the test app's private files (written also when a step failed).
for file in "${REPORT_FILES[@]}"; do
  adb exec-out run-as "$PACKAGE" cat "files/accuracy/$file" > "$out/$file" 2>/dev/null || true
  if [[ ! -s "$out/$file" ]] || head -n 1 "$out/$file" | grep -qE '^(run-as|cat):'; then
    echo "::warning::The test wrote no $file."
    rm -f "$out/$file"
  fi
done

if grep -q '^OK (1 test)' "$out/instrumentation.txt" &&
  grep -q '^\*\*Result: PASS\*\*' "$out/accuracy.md" 2>/dev/null; then
  echo "Accuracy run passed."
  exit 0
fi
echo "::error::The accuracy run failed or did not finish (04 §12: p95 ≤ 250 ms, max ≤ 1 s); see accuracy.md and instrumentation.txt."
exit 1
