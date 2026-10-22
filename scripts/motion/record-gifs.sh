#!/usr/bin/env bash
# Records the M1 motion GIFs for the PO's motion review (D-041, M1 plan §3.2).
#
# 1. Runs the record-only motion tests (Roborazzi's experimental recordRoboVideo
#    under Robolectric): MotionRecordingTest in core:designsystem and
#    StepsSheetMotionRecordingTest in feature:brew, into each module's
#    build/outputs/motion/.
# 2. Shrinks each GIF with ffmpeg (320 px wide, 15 fps, one optimised palette)
#    into docs/design/review/M1/motion/ and fails if one is over 1 MB.
#
# Never part of CI: the test is skipped unless -Pumamit.recordMotion=true.
# Usage: scripts/motion/record-gifs.sh   (ffmpeg and ANDROID_HOME required)
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
raws=("$root/core/designsystem/build/outputs/motion" "$root/feature/brew/build/outputs/motion")
out="$root/docs/design/review/M1/motion"
width=320
fps=15
max_bytes=$((1024 * 1024))

command -v ffmpeg >/dev/null || { echo "ffmpeg is required" >&2; exit 1; }

rm -rf "${raws[@]}"
"$root/gradlew" -p "$root" \
  :core:designsystem:testDebugUnitTest --tests '*MotionRecordingTest*' \
  :feature:brew:testDebugUnitTest --tests '*MotionRecordingTest*' \
  -Pumamit.recordMotion=true

mkdir -p "$out"
shopt -s nullglob
gifs=()
for raw in "${raws[@]}"; do gifs+=("$raw"/*.gif); done
if [ ${#gifs[@]} -eq 0 ]; then
  echo "No recordings in ${raws[*]}" >&2
  exit 1
fi

for gif in "${gifs[@]}"; do
  name="$(basename "$gif")"
  ffmpeg -v error -y -i "$gif" -filter_complex \
    "fps=$fps,scale=$width:-1:flags=lanczos,split[a][b];[a]palettegen=max_colors=96:stats_mode=full[p];[b][p]paletteuse=dither=bayer:bayer_scale=4:diff_mode=rectangle" \
    -loop 0 "$out/$name"
  bytes=$(stat -c %s "$out/$name")
  printf '%-36s %7d bytes\n' "$name" "$bytes"
  if [ "$bytes" -gt "$max_bytes" ]; then
    echo "$name is over 1 MB" >&2
    exit 1
  fi
done
