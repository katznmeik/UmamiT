#!/usr/bin/env bash
# Runs one instrumented test of core:timer many times on a booted emulator or a
# device with adb (D-084), for failures that come only now and then on the CI
# emulator, such as ProcessDeathTest's kill during a step.
# `.github/workflows/timer-stress.yml` runs it on dispatch; it is no gate.
#
# Usage: ./gradlew :core:timer:assembleDebugAndroidTest && scripts/stress-instrumented.sh
#   STRESS_TEST        class#method (default: ProcessDeathTest's kill during a step)
#   STRESS_RUNS        runs (default 20, at most 200)
#   STRESS_KILL_SWEEP  ProcessDeathTest only: "from:to:step", the ms after the step's
#                      start at which run 1, 2, … kills the process, cycling from
#                      `from` to `to` (e.g. 400:1200:50 around the update re-posted
#                      0.5 s after the start, D-071); empty: as soon as the setup
#                      checks passed, as in the regular emulator job
#   STRESS_OUT         report directory (default build/stress)
#
# Writes to STRESS_OUT: summary.md (one line per run with the kill time the test
# logged) and, for each failed run N, instrumentation-N.txt and logcat-N.txt.
# Exits 0 only when every run passed.
#
# A single run without this script, in any emulator job:
#   ./gradlew :core:timer:connectedDebugAndroidTest \
#     -Pandroid.testInstrumentationRunnerArguments.class=com.umamit.core.timer.ProcessDeathTest#aStepWhoseProcessIsKilledStillAlertsFullScreenAtItsDeadline
set -uo pipefail

readonly APK="core/timer/build/outputs/apk/androidTest/debug/timer-debug-androidTest.apk"
readonly PACKAGE="com.umamit.core.timer.test"
readonly RUNNER="androidx.test.runner.AndroidJUnitRunner"
readonly DEFAULT_TEST="com.umamit.core.timer.ProcessDeathTest#aStepWhoseProcessIsKilledStillAlertsFullScreenAtItsDeadline"

test="${STRESS_TEST:-$DEFAULT_TEST}"
runs="${STRESS_RUNS:-20}"
sweep="${STRESS_KILL_SWEEP:-}"
out="${STRESS_OUT:-build/stress}"

if ! [[ "$test" =~ ^com\.umamit\.[A-Za-z0-9_.]+(#[A-Za-z0-9_]+)?$ ]]; then
  echo "::error::STRESS_TEST must be a test class of com.umamit, optionally with #method, was '$test'."
  exit 2
fi
if ! [[ "$runs" =~ ^[0-9]+$ ]] || ((runs < 1 || runs > 200)); then
  echo "::error::STRESS_RUNS must be a number from 1 to 200, was '$runs'."
  exit 2
fi
if [[ -n "$sweep" ]]; then
  if ! [[ "$sweep" =~ ^([0-9]+):([0-9]+):([0-9]+)$ ]]; then
    echo "::error::STRESS_KILL_SWEEP must be from:to:step in ms, was '$sweep'."
    exit 2
  fi
  from="${BASH_REMATCH[1]}"
  to="${BASH_REMATCH[2]}"
  step="${BASH_REMATCH[3]}"
  if ((step < 1 || to < from || to > 15000)); then
    echo "::error::STRESS_KILL_SWEEP needs step ≥ 1 and from ≤ to ≤ 15000, was '$sweep'."
    exit 2
  fi
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

passed=0
failed=0
{
  echo "### Stress run: \`${test##*.}\`"
  echo
  echo "| Run | Kill at (ms after the start) | Result | Logged by the test |"
  echo "|---|---|---|---|"
} > "$out/summary.md"

for ((run = 1; run <= runs; run++)); do
  args=(-e class "$test")
  kill_at="—"
  if [[ -n "$sweep" ]]; then
    kill_at=$((from + ((run - 1) * step) % (to - from + step)))
    args+=(-e umamitKillAfterStartMs "$kill_at")
  fi
  adb logcat -c || true
  adb shell am instrument -w "${args[@]}" "$PACKAGE/$RUNNER" | tr -d '\r' > "$out/current.txt"
  logged="$(adb logcat -d -s ProcessDeathTest:I | grep -o 'killed pid.*' | tail -n 1 | tr '|' '/')"
  if grep -q '^OK (1 test)' "$out/current.txt"; then
    passed=$((passed + 1))
    result="pass"
  else
    failed=$((failed + 1))
    result="**FAIL**"
    mv "$out/current.txt" "$out/instrumentation-$run.txt"
    adb logcat -d -v time > "$out/logcat-$run.txt" 2>/dev/null || true
    echo "::warning::Run $run failed; see instrumentation-$run.txt and logcat-$run.txt."
  fi
  echo "| $run | $kill_at | $result | ${logged:-—} |" >> "$out/summary.md"
  echo "Run $run of $runs: $result (kill at $kill_at) ${logged}"
done
rm -f "$out/current.txt"

{
  echo
  echo "**Result: $passed passed, $failed failed** of $runs runs."
} >> "$out/summary.md"

if ((failed > 0)); then
  echo "::error::$failed of $runs runs failed."
  exit 1
fi
echo "All $runs runs passed."
