#!/usr/bin/env bash
# Records the M1 motion GIFs for the PO's motion review (D-041, M1 plan §3.2).
#
# 1. Runs the record-only motion tests under Robolectric into each module's
#    build/outputs/motion/: MotionRecordingTest in core:designsystem
#    (Roborazzi's experimental recordRoboVideo) and StepsSheetMotionRecordingTest
#    in feature:brew (a whole-screen frame sequence, because the sheet has its
#    own window), whose frames ffmpeg joins into a GIF.
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
# Frame sequences (whole-screen captures of 50 ms frames, for UI in its own
# window such as Material's modal sheet) become a raw GIF first.
for raw in "${raws[@]}"; do
  for frames in "$raw"/*/; do
    [ -e "${frames}frame_000.png" ] || continue
    ffmpeg -v error -y -framerate 20 -i "${frames}frame_%03d.png" -filter_complex \
      "split[a][b];[a]palettegen=stats_mode=full[p];[b][p]paletteuse=dither=none" "${frames%/}.gif"
  done
done

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
