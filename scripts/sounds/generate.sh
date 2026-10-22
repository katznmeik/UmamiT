#!/usr/bin/env bash
# Synthesises UmamiT's bundled alert sounds with ffmpeg (04 §9.2, D-036; M1-03a, #16):
#   - klangschale.ogg  "Klangschale" (singing bowl): additive inharmonic
#                      partials with exponential decay, the default alert;
#   - tick.ogg         short, soft small-bowl "ting" for the pre-alert.
# Both are variant C of the Q-15 listening round (2026-10-22, chosen by the PO
# after the probe: the first draft was not bright enough).
# Own work, no third-party audio (docs/design/assets/source/CREDITS.md).
#
# Every file is mono, 48 kHz, Ogg Vorbis, ≤ 60 KB and loudness-normalised:
# the script measures the synthesised signal (EBU R128, ffmpeg ebur128 with
# true peak) and applies one linear gain so that it reaches TARGET_LUFS,
# unless that would push the true peak above CEILING_DBTP (then the ceiling
# wins). No limiter or compressor touches the waveform.
#   - klangschale: -16 LUFS integrated, true peak ≤ -1.5 dBTP before encoding.
#   - tick: -24 LUFS integrated (measured padded to at least one 400 ms EBU
#     block), 8 LU below the alert (a pre-alert is a reduced alert, 04 §9.1).
#
# Reproducibility: the synthesis is plain arithmetic (aevalsrc, no random
# source) and the encoder runs in bit-exact mode without metadata, so the same
# ffmpeg/libvorbis build reproduces the committed files byte for byte. Other
# builds may round the float maths or the Vorbis encoder differently; the
# decoded audio is then the same within inaudible tolerance, but not the
# bytes. The committed files were made with REFERENCE_FFMPEG below; `--check`
# says whether this machine reproduces them byte for byte.
#
# Usage:
#   scripts/sounds/generate.sh            # (re)writes the files in OUT_DIR
#   scripts/sounds/generate.sh --check    # regenerates into a temp dir and
#                                         # compares with the committed files
#   OUT_DIR=/some/dir scripts/sounds/generate.sh
set -euo pipefail

readonly REFERENCE_FFMPEG="6.1.1-3ubuntu5"
readonly SAMPLE_RATE=48000
readonly MAX_BYTES=61440 # 60 KB (04 §9.2)
readonly VORBIS_QUALITY=6

readonly BOWL_TARGET_LUFS=-16
readonly BOWL_CEILING_DBTP=-1.5
readonly TICK_TARGET_LUFS=-24
readonly TICK_CEILING_DBTP=-3

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
readonly DEFAULT_OUT_DIR="$repo_root/core/content/src/main/res/raw"

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

command -v ffmpeg > /dev/null || fail "ffmpeg is required"
command -v ffprobe > /dev/null || fail "ffprobe is required"

ffmpeg_version="$(ffmpeg -version | head -n 1 | awk '{print $3}')"
if [[ "$ffmpeg_version" != "$REFERENCE_FFMPEG" ]]; then
  echo "NOTE: ffmpeg $ffmpeg_version differs from the reference $REFERENCE_FFMPEG;" \
    "the output may differ byte-wise (see the header)." >&2
fi

# --- Synthesis -------------------------------------------------------------

# Singing bowl ("hell und klar"): a fundamental of 880 Hz and inharmonic
# partials at the ratios of a struck bowl (≈ 1 : 2.76 : 5.40 : 8.93). The two
# lowest modes come as close pairs (1.004, 2.7605) so they beat slowly, the
# bowl's shimmer. Higher partials decay faster. A 1.5 ms attack keeps the
# strike clear but soft; the last 0.6 s fade out so a repeat (alert length
# "Normal", 04 §9.1) starts from silence. The start phases are spread to lower
# the crest factor (more loudness at the same true peak).
readonly BOWL_F0=880
readonly BOWL_SECONDS=3.5
bowl_expr() {
  local f=$BOWL_F0
  printf '%s' "(1-exp(-t/0.0015))*0.22*(" \
    "1.00*sin(2*PI*$f*t)*exp(-t/1.6)" \
    "+0.50*sin(2*PI*$f*1.004*t+2.0)*exp(-t/1.4)" \
    "+0.55*sin(2*PI*$f*2.756*t+4.0)*exp(-t/1.0)" \
    "+0.25*sin(2*PI*$f*2.7605*t+1.0)*exp(-t/0.9)" \
    "+0.25*sin(2*PI*$f*5.404*t+3.0)*exp(-t/0.6)" \
    "+0.10*sin(2*PI*$f*8.933*t+5.0)*exp(-t/0.3)" \
    ")"
}

# Tick ("warm lang"): a small bowl struck softly — 990 Hz with the same bowl
# ratios and a slow beat, about 0.8 s long, ending in a 0.2 s fade.
readonly TICK_F0=990
readonly TICK_SECONDS=0.8
tick_expr() {
  local f=$TICK_F0
  printf '%s' "(1-exp(-t/0.0015))*0.4*(" \
    "1.00*sin(2*PI*$f*t)*exp(-t/0.35)" \
    "+0.50*sin(2*PI*$f*1.003*t+2.0)*exp(-t/0.3)" \
    "+0.40*sin(2*PI*$f*2.756*t+4.0)*exp(-t/0.18)" \
    "+0.15*sin(2*PI*$f*5.404*t+3.0)*exp(-t/0.09)" \
    ")"
}

# synth <expr> <seconds> <fade-out start> <fade-out length> <out.wav>
synth() {
  ffmpeg -hide_banner -loglevel error -y \
    -f lavfi -i "aevalsrc=exprs='$1':s=$SAMPLE_RATE:d=$2:c=mono" \
    -af "afade=t=out:st=$3:d=$4" \
    -c:a pcm_f32le "$5"
}

# --- Loudness --------------------------------------------------------------

# measure <wav> <pad seconds> → prints "<integrated LUFS> <true peak dBTP>"
measure() {
  local filter="ebur128=peak=true"
  if [[ "$2" != "0" ]]; then filter="apad=whole_dur=$2,$filter"; fi
  ffmpeg -hide_banner -nostats -i "$1" -af "$filter" -f null - 2>&1 |
    awk '/Integrated loudness:/ {section="i"} /True peak:/ {section="tp"}
         section=="i" && $1=="I:" {i=$2}
         section=="tp" && $1=="Peak:" {tp=$2}
         END {print i, tp}'
}

# gain_db <lufs> <tp> <target lufs> <ceiling dbtp> → gain in dB (one decimal)
gain_db() {
  awk -v i="$1" -v tp="$2" -v target="$3" -v ceiling="$4" 'BEGIN {
    g = target - i
    if (tp + g > ceiling) g = ceiling - tp
    printf "%.1f", g
  }'
}

# encode <in.wav> <gain dB> <out.ogg>
encode() {
  ffmpeg -hide_banner -loglevel error -y -i "$1" \
    -af "volume=${2}dB" \
    -ac 1 -ar "$SAMPLE_RATE" \
    -c:a libvorbis -q:a "$VORBIS_QUALITY" \
    -map_metadata -1 -fflags +bitexact -flags:a +bitexact \
    "$3"
}

# make_sound <name> <expr> <seconds> <fade start> <fade length> <target> <ceiling> <measure pad> <out dir>
make_sound() {
  local name=$1 expr=$2 seconds=$3 fade_start=$4 fade_len=$5 target=$6 ceiling=$7 pad=$8 out_dir=$9
  local raw="$work/$name.wav"
  synth "$expr" "$seconds" "$fade_start" "$fade_len" "$raw"
  local lufs tp gain
  read -r lufs tp < <(measure "$raw" "$pad")
  gain="$(gain_db "$lufs" "$tp" "$target" "$ceiling")"
  encode "$raw" "$gain" "$out_dir/$name.ogg"
  local size
  size=$(wc -c < "$out_dir/$name.ogg")
  ((size <= MAX_BYTES)) || fail "$name.ogg is $size bytes (> $MAX_BYTES)"
  echo "$name.ogg: measured $lufs LUFS / $tp dBTP, gain ${gain} dB, $size bytes"
}

generate_into() {
  local out_dir=$1
  mkdir -p "$out_dir"
  make_sound klangschale "$(bowl_expr)" "$BOWL_SECONDS" 2.9 0.6 \
    "$BOWL_TARGET_LUFS" "$BOWL_CEILING_DBTP" 0 "$out_dir"
  make_sound tick "$(tick_expr)" "$TICK_SECONDS" 0.6 0.2 \
    "$TICK_TARGET_LUFS" "$TICK_CEILING_DBTP" 0.4 "$out_dir"
}

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

if [[ "${1:-}" == "--check" ]]; then
  generate_into "$work/out"
  status=0
  for name in klangschale tick; do
    if cmp -s "$work/out/$name.ogg" "$DEFAULT_OUT_DIR/$name.ogg"; then
      echo "OK: $name.ogg reproduced byte for byte"
    else
      echo "DIFFERS: $name.ogg (see the header on reproducibility)" >&2
      status=1
    fi
  done
  exit "$status"
fi

generate_into "${OUT_DIR:-$DEFAULT_OUT_DIR}"
