'use strict';
/**
 * Synthesises the promo's music sketch: out/music.wav (own work, like the app's
 * alert sounds; no samples, no third-party material).
 *
 *   node music.js
 *
 * A calm piece at 60 BPM in the yo scale on D (D E G A B): a koto-like plucked
 * string (Karplus-Strong), a soft pad, a singing bowl at the phrase starts and
 * a quiet bass, in a small hall. The first beat falls at 0.5 s, so in the timer
 * scene every new digit of the countdown lands on a beat.
 *
 * Everything is seeded: the same script writes the same file.
 */
const fs = require('node:fs');
const path = require('node:path');

const SR = 44100;
const BEAT = 1.0; // 60 BPM
const FIRST_BEAT = 0.5;
const BARS = 5; // 4 beats each
const TAIL = 4.0;
const LENGTH = Math.ceil((FIRST_BEAT + BARS * 4 * BEAT + TAIL) * SR);

const left = new Float32Array(LENGTH);
const right = new Float32Array(LENGTH);
const sendLeft = new Float32Array(LENGTH); // reverb send
const sendRight = new Float32Array(LENGTH);

/** Small seeded generator (mulberry32). */
function random(seed) {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6D2B79F5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

const NOTE = { D: 2, E: 4, G: 7, A: 9, B: 11 };
/** Frequency of a note name like "D4" (A4 = 440 Hz). */
function hz(name) {
  const semitone = NOTE[name[0]] + (Number(name.slice(1)) + 1) * 12;
  return 440 * 2 ** ((semitone - 69) / 12);
}

function add(time, mono, pan, send) {
  const start = Math.round(time * SR);
  const gainLeft = Math.cos((pan + 1) * Math.PI / 4), gainRight = Math.sin((pan + 1) * Math.PI / 4);
  for (let i = 0; i < mono.length && start + i < LENGTH; i++) {
    left[start + i] += mono[i] * gainLeft;
    right[start + i] += mono[i] * gainRight;
    sendLeft[start + i] += mono[i] * gainLeft * send;
    sendRight[start + i] += mono[i] * gainRight * send;
  }
}

/** A plucked string: a soft noise burst in a damped delay line (Karplus-Strong), with a thumb-like onset. */
function pluck(time, note, velocity, pan, seed) {
  const frequency = hz(note);
  const seconds = 4.5;
  const out = new Float32Array(Math.round(seconds * SR));
  const period = SR / frequency - 0.5; // the averaging filter adds half a sample
  const size = Math.ceil(period) + 2;
  const line = new Float32Array(size);
  const noise = random(seed);
  // Excitation: noise, low-passed more for softer notes, with a comb for the plucking point.
  let previous = 0;
  const brightness = 0.35 + 0.4 * velocity;
  const burst = new Float32Array(Math.floor(period));
  for (let i = 0; i < burst.length; i++) {
    previous += brightness * ((noise() * 2 - 1) - previous);
    burst[i] = previous;
  }
  const pick = Math.max(1, Math.floor(burst.length * 0.18));
  for (let i = 0; i < burst.length; i++) line[i] = burst[i] - 0.6 * burst[(i + pick) % burst.length];

  const decay = 0.9985 - 0.0012 * Math.min(1, frequency / 800);
  let write = burst.length % size;
  let last = 0;
  for (let i = 0; i < out.length; i++) {
    const position = (write - period + size * 4) % size;
    const index = Math.floor(position), fraction = position - index;
    const delayed = line[index] * (1 - fraction) + line[(index + 1) % size] * fraction;
    const filtered = decay * 0.5 * (delayed + last);
    last = delayed;
    line[write] = filtered;
    write = (write + 1) % size;
    const attack = Math.min(1, i / (0.004 * SR));
    out[i] = delayed * attack * velocity * 0.9;
  }
  add(time, out, pan, 0.55);
}

/** A singing bowl: a few inharmonic partials, each a slowly beating pair. */
function bowl(time, frequency, gain, pan) {
  const seconds = 7;
  const out = new Float32Array(Math.round(seconds * SR));
  const partials = [[1, 1, 5.5], [2.71, 0.42, 3.6], [5.15, 0.16, 2.2], [8.1, 0.05, 1.2]];
  for (const [ratio, level, life] of partials) {
    const f = frequency * ratio;
    for (let i = 0; i < out.length; i++) {
      const t = i / SR;
      const envelope = Math.min(1, t / 0.012) * Math.exp(-t / (life / 3));
      out[i] += gain * level * envelope * 0.5 * (Math.sin(2 * Math.PI * f * t) + Math.sin(2 * Math.PI * (f * 1.0016 + 0.4) * t));
    }
  }
  add(time, out, pan, 0.7);
}

/** A soft chord: a few slightly detuned sines per note, slow in and out. */
function pad(time, seconds, notes, gain) {
  const out = [new Float32Array(Math.round((seconds + 3) * SR)), new Float32Array(Math.round((seconds + 3) * SR))];
  notes.forEach((note, n) => {
    const f = hz(note);
    for (let side = 0; side < 2; side++) {
      const detune = 1 + (side ? 0.0017 : -0.0013) * (1 + n * 0.3);
      for (let i = 0; i < out[side].length; i++) {
        const t = i / SR;
        const envelope = Math.min(1, t / 1.6) ** 1.5 * Math.min(1, Math.max(0, (seconds + 3 - t) / 3));
        const shimmer = 1 + 0.12 * Math.sin(2 * Math.PI * (0.11 + n * 0.03) * t + n + side);
        out[side][i] += gain * envelope * shimmer
          * (Math.sin(2 * Math.PI * f * detune * t) + 0.22 * Math.sin(2 * Math.PI * 2 * f * detune * t + 1));
      }
    }
  });
  const start = Math.round(time * SR);
  for (let i = 0; i < out[0].length && start + i < LENGTH; i++) {
    left[start + i] += out[0][i];
    right[start + i] += out[1][i];
    sendLeft[start + i] += out[0][i] * 0.5;
    sendRight[start + i] += out[1][i] * 0.5;
  }
}

/** A round, quiet bass note. */
function bass(time, note, seconds, gain) {
  const f = hz(note);
  const out = new Float32Array(Math.round((seconds + 1.5) * SR));
  for (let i = 0; i < out.length; i++) {
    const t = i / SR;
    const envelope = Math.min(1, t / 0.25) * Math.min(1, Math.max(0, (seconds + 1.5 - t) / 1.5));
    out[i] = gain * envelope * (Math.sin(2 * Math.PI * f * t) + 0.15 * Math.sin(2 * Math.PI * 2 * f * t));
  }
  add(time, out, 0, 0.1);
}

/** A small hall (Freeverb: eight damped combs into four all-passes per side). */
function reverb(input, spread) {
  const combs = [1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617].map((n) => n + spread);
  const allpasses = [556, 441, 341, 225].map((n) => n + spread);
  const feedback = 0.87, damp = 0.28;
  const out = new Float32Array(input.length);
  for (const size of combs) {
    const buffer = new Float32Array(size);
    let index = 0, store = 0;
    for (let i = 0; i < input.length; i++) {
      const y = buffer[index];
      store = y * (1 - damp) + store * damp;
      buffer[index] = input[i] * 0.015 + store * feedback;
      index = (index + 1) % size;
      out[i] += y;
    }
  }
  for (const size of allpasses) {
    const buffer = new Float32Array(size);
    let index = 0;
    for (let i = 0; i < out.length; i++) {
      const delayed = buffer[index];
      buffer[index] = out[i] + delayed * 0.5;
      index = (index + 1) % size;
      out[i] = delayed - out[i];
    }
  }
  return out;
}

// ---------- The piece ----------
const at = (bar, beat) => FIRST_BEAT + (bar * 4 + beat) * BEAT;

// Harmony: one chord per bar, with the bowl on bars 0 and 4.
const chords = [
  ['D3', 'A3', 'E4'], // D sus2
  ['G2', 'D3', 'B3'], // G
  ['B2', 'D3', 'A3'], // B m7 (no fifth)
  ['A2', 'E3', 'B3'], // A sus2
  ['D3', 'A3', 'E4'],
];
const roots = ['D2', 'G2', 'B2', 'A2', 'D2'];
// The pad starts before the first beat, so the scene never opens on silence.
pad(0, at(1, 0) + 0.5, chords[0], 0.02);
for (let bar = 1; bar < BARS; bar++) pad(at(bar, 0) - 0.6, 4.6, chords[bar], 0.02);
roots.forEach((root, bar) => bass(at(bar, 0), root, 3.4, 0.06));
bowl(at(0, 0), hz('D3'), 0.11, -0.1);
bowl(at(4, 0), hz('D3'), 0.09, 0.1);

// Melody: [bar, beat, note, velocity]. Bar 0 is what the five-second test scene plays.
const melody = [
  [0, 0, 'D4', 0.8], [0, 1, 'A4', 0.7], [0, 1.5, 'B4', 0.6], [0, 2, 'D5', 0.9], [0, 3, 'B4', 0.65], [0, 3.5, 'A4', 0.55],
  [1, 0, 'G4', 0.8], [1, 1, 'B4', 0.6], [1, 2, 'D5', 0.75], [1, 2.5, 'E5', 0.7], [1, 3, 'D5', 0.6],
  [2, 0, 'B4', 0.8], [2, 1, 'A4', 0.6], [2, 1.5, 'G4', 0.55], [2, 2, 'A4', 0.75], [2, 3.5, 'E4', 0.5],
  [3, 0, 'E4', 0.75], [3, 1, 'A4', 0.65], [3, 2, 'B4', 0.8], [3, 3, 'A4', 0.6], [3, 3.5, 'E4', 0.5],
  [4, 0, 'D4', 0.85], [4, 0.5, 'A4', 0.6], [4, 1, 'D5', 0.7],
];
melody.forEach(([bar, beat, note, velocity], n) => {
  pluck(at(bar, beat), note, velocity, ((n * 37) % 11) / 11 * 0.6 - 0.3, 1000 + n);
});
// A low string under each bar's first note gives the phrase its weight.
roots.forEach((root, bar) => pluck(at(bar, 0), root.replace('2', '3'), 0.4, -0.15, 2000 + bar));

// ---------- Mix ----------
const wetLeft = reverb(sendLeft, 0), wetRight = reverb(sendRight, 23);
let peak = 0;
for (let i = 0; i < LENGTH; i++) {
  left[i] += wetLeft[i] * 0.9;
  right[i] += wetRight[i] * 0.9;
  peak = Math.max(peak, Math.abs(left[i]), Math.abs(right[i]));
}
// Gentle saturation rounds the bar starts (bowl and two strings together), so the rest can sit higher.
const DRIVE = 1.7;
const master = (x) => 0.85 * Math.tanh(DRIVE * x / peak) / Math.tanh(DRIVE); // peaks at about −1.4 dBFS
const fadeOut = Math.round(2.5 * SR);
const pcm = Buffer.alloc(44 + LENGTH * 4);
pcm.write('RIFF', 0); pcm.writeUInt32LE(36 + LENGTH * 4, 4); pcm.write('WAVEfmt ', 8);
pcm.writeUInt32LE(16, 16); pcm.writeUInt16LE(1, 20); pcm.writeUInt16LE(2, 22);
pcm.writeUInt32LE(SR, 24); pcm.writeUInt32LE(SR * 4, 28); pcm.writeUInt16LE(4, 32); pcm.writeUInt16LE(16, 34);
pcm.write('data', 36); pcm.writeUInt32LE(LENGTH * 4, 40);
for (let i = 0; i < LENGTH; i++) {
  const fade = Math.min(1, (LENGTH - i) / fadeOut);
  pcm.writeInt16LE(Math.round(master(left[i]) * fade * 32767), 44 + i * 4);
  pcm.writeInt16LE(Math.round(master(right[i]) * fade * 32767), 46 + i * 4);
}
const out = path.join(__dirname, 'out', 'music.wav');
fs.mkdirSync(path.dirname(out), { recursive: true });
fs.writeFileSync(out, pcm);
console.log(`${out} (${(LENGTH / SR).toFixed(1)} s, peak before scaling ${peak.toFixed(3)})`);
