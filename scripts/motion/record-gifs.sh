#!/usr/bin/env bash
# Records the M1 motion GIFs for the PO's motion review (D-041, M1 plan §3.2).
#
# 1. Runs the record-only MotionRecordingTest (Roborazzi's experimental
#    recordRoboVideo under Robolectric) into core/designsystem/build/outputs/motion/.
# 2. Shrinks each GIF with ffmpeg (320 px wide, 15 fps, one optimised palette)
#    into docs/design/review/M1/motion/ and fails if one is over 1 MB.
#
# Never part of CI: the test is skipped unless -Pumamit.recordMotion=true.
# Usage: scripts/motion/record-gifs.sh   (ffmpeg and ANDROID_HOME required)
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
raw="$root/core/designsystem/build/outputs/motion"
out="$root/docs/design/review/M1/motion"
width=320
fps=15
max_bytes=$((1024 * 1024))

command -v ffmpeg >/dev/null || { echo "ffmpeg is required" >&2; exit 1; }

rm -rf "$raw"
"$root/gradlew" -p "$root" :core:designsystem:testDebugUnitTest \
  --tests '*MotionRecordingTest*' -Pumamit.recordMotion=true

mkdir -p "$out"
shopt -s nullglob
gifs=("$raw"/*.gif)
if [ ${#gifs[@]} -eq 0 ]; then
  echo "No recordings in $raw" >&2
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
