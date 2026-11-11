'use strict';
/**
 * Synthesises the film's sound: out/music.wav (own work, like the app's alert
 * sounds; no samples and no third-party material).
 *
 *   node music.js
 *
 * A calm piece at 60 BPM in the yo scale on D (D E G A B), eleven bars of 4 s:
 * singing bowls (struck, and sung as a drone), small high bowls, a koto-like
 * plucked string, wind in leaves, in a hall. The first beat falls at 0.5 s, so
 * the timer's digits change on the beat. Two of the app's own sounds are mixed
 * in where the film shows them: the pre-alert tick (B5) and the Klangschale
 * (A5), both notes of the scale.
 *
 * Everything is seeded: the same script writes the same file.
 */
const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');

const SR = 44100;
const FIRST_BEAT = 0.5;
const BARS = 11;
const SECONDS = 44 + 3; // the film, and a tail for the last bowl
const LENGTH = SECONDS * SR;
// The film's moments (scenes/film.html, T).
const PRE_ALERT = 17.5, ALERT = 20.5, LAPSE_1 = 16.0, LAPSE_2 = 19.0;
const RAW = path.join(__dirname, '..', 'core', 'content', 'src', 'main', 'res', 'raw');

const left = new Float32Array(LENGTH), right = new Float32Array(LENGTH);
const sendLeft = new Float32Array(LENGTH), sendRight = new Float32Array(LENGTH); // to the hall
/** The app's own sounds go on their own bus: the music ducks under them. */
const appLeft = new Float32Array(LENGTH), appRight = new Float32Array(LENGTH);

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

/** Smooth random curve in 0..1 with about `rate` turns per second. */
function drift(seed, rate) {
  const next = random(seed);
  const points = Array.from({ length: Math.ceil(SECONDS * rate) + 3 }, () => next());
  return (t) => {
    // Times before the film or after its tail wrap around.
    const x = Math.abs(t) * rate, i = Math.floor(x), f = x - i, s = f * f * (3 - 2 * f);
    return points[i % points.length] * (1 - s) + points[(i + 1) % points.length] * s;
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
  for (let i = Math.max(0, -start); i < mono.length && start + i < LENGTH; i++) {
    left[start + i] += mono[i] * gainLeft;
    right[start + i] += mono[i] * gainRight;
    sendLeft[start + i] += mono[i] * gainLeft * send;
    sendRight[start + i] += mono[i] * gainRight * send;
  }
}

/** RBJ band-pass (constant peak gain) as a per-sample function. */
function bandpass(frequency, q) {
  const w = 2 * Math.PI * frequency / SR, alpha = Math.sin(w) / (2 * q), a0 = 1 + alpha;
  const b0 = alpha / a0, a1 = -2 * Math.cos(w) / a0, a2 = (1 - alpha) / a0;
  let x1 = 0, x2 = 0, y1 = 0, y2 = 0;
  return (x) => {
    const y = b0 * x - b0 * x2 - a1 * y1 - a2 * y2;
    x2 = x1; x1 = x; y2 = y1; y1 = y;
    return y;
  };
}

// ---------- Instruments ----------

/**
 * A bowl's modes: frequency ratio, level, ring time (s to −60 dB, for a bowl
 * at 150 Hz). Every mode is a pair a fraction of a hertz apart, which gives a
 * bowl its slow beating; no two bowls here beat alike.
 */
const BOWL_MODES = [[1, 1, 11], [2.76, 0.5, 7.5], [5.4, 0.22, 4.2], [8.93, 0.09, 2.4], [13.34, 0.035, 1.3]];

/** A struck singing bowl: the mallet's soft knock, then the modes ringing out. */
function bowl(time, frequency, gain, pan, seed) {
  const next = random(seed);
  const size = Math.min(1, (150 / frequency) ** 0.45); // small bowls ring shorter
  const out = new Float32Array(Math.round(Math.min(12, 11 * size + 1) * SR));
  for (const [ratio, level, ring] of BOWL_MODES) {
    const f = frequency * ratio;
    if (f > 16000) continue;
    const split = 0.25 + 1.6 * next(), phaseA = 2 * Math.PI * next(), phaseB = 2 * Math.PI * next();
    const tau = ring * size / 6.91, onset = 0.004 + 0.012 * next();
    for (let i = 0; i < out.length; i++) {
      const t = i / SR;
      const envelope = Math.min(1, t / onset) * Math.exp(-t / tau);
      out[i] += gain * level * envelope * 0.5
        * (Math.sin(2 * Math.PI * f * t + phaseA) + Math.sin(2 * Math.PI * (f + split) * t + phaseB));
    }
  }
  // The mallet: a few milliseconds of dull noise.
  const knock = bandpass(Math.min(2400, frequency * 3), 0.8);
  for (let i = 0; i < 0.03 * SR; i++) out[i] += gain * 0.5 * knock(next() * 2 - 1) * Math.exp(-i / (0.006 * SR));
  add(time, out, pan, 0.8);
}

/** A sung bowl: the rim rubbed, the two lowest modes swelling in and out, never quite steady. */
function sungBowl(time, seconds, frequency, gain, pan, seed) {
  const next = random(seed);
  const wobble = drift(seed + 1, 0.7);
  const out = new Float32Array(Math.round((seconds + 4) * SR));
  for (const [ratio, level] of [[1, 1], [2.76, 0.16], [5.4, 0.03]]) {
    const f = frequency * ratio, split = 0.3 + 0.9 * next(), phase = 2 * Math.PI * next();
    for (let i = 0; i < out.length; i++) {
      const t = i / SR;
      const envelope = Math.min(1, t / 2.2) ** 2 * Math.min(1, Math.max(0, (seconds + 4 - t) / 4)) ** 1.5;
      out[i] += gain * level * envelope * (0.75 + 0.25 * wobble(time + t)) * 0.5
        * (Math.sin(2 * Math.PI * f * t + phase) + Math.sin(2 * Math.PI * (f + split) * t));
    }
  }
  add(time, out, pan, 0.7);
}

/** A plucked string: a soft noise burst in a damped delay line (Karplus-Strong), bending into tune like a pressed koto string. */
function pluck(time, note, velocity, pan, seed) {
  const frequency = hz(note);
  const out = new Float32Array(Math.round(4.5 * SR));
  const noise = random(seed);
  const size = Math.ceil(SR / frequency) + 4;
  const line = new Float32Array(size);
  const length = Math.floor(SR / frequency);
  let previous = 0;
  const brightness = 0.3 + 0.4 * velocity;
  const burst = new Float32Array(length);
  for (let i = 0; i < length; i++) {
    previous += brightness * ((noise() * 2 - 1) - previous);
    burst[i] = previous;
  }
  const pick = Math.max(1, Math.floor(length * (0.14 + 0.1 * noise())));
  for (let i = 0; i < length; i++) line[i] = burst[i] - 0.6 * burst[(i + pick) % length];

  const decay = 0.9982 - 0.0014 * Math.min(1, frequency / 800);
  let write = length % size, last = 0;
  for (let i = 0; i < out.length; i++) {
    // A few cents sharp at the pluck, in tune within a tenth of a second.
    const bend = 1 + 0.004 * velocity * Math.exp(-i / (0.05 * SR));
    const period = SR / (frequency * bend) - 0.5; // the averaging filter adds half a sample
    const position = (write - period + size * 4) % size;
    const index = Math.floor(position), fraction = position - index;
    const delayed = line[index] * (1 - fraction) + line[(index + 1) % size] * fraction;
    line[write] = decay * 0.5 * (delayed + last);
    last = delayed;
    write = (write + 1) % size;
    out[i] = delayed * Math.min(1, i / (0.004 * SR)) * velocity;
  }
  add(time, out, pan, 0.6);
}

/**
 * Wind in leaves, for the whole film: gusts that come and go, a soft rush of
 * air, the hiss of many leaves, and single leaves ticking against each other
 * when a gust is up. Left and right are different leaves in the same wind.
 */
function wind(gain) {
  const gust = drift(11, 0.16), eddy = drift(12, 0.9);
  for (const [channel, send, seed, lean] of [[left, sendLeft, 21, 0.0], [right, sendRight, 22, 0.35]]) {
    const next = random(seed);
    const flutter = drift(seed + 100, 11), shimmer = drift(seed + 200, 4);
    const air = bandpass(420, 0.6), leaves = bandpass(4200, 0.7), high = bandpass(7600, 1.1), leaf = bandpass(5600, 2.5);
    let tick = 0;
    for (let i = 0; i < LENGTH; i++) {
      const t = i / SR;
      // Gusts: slow swells with smaller eddies on top; the film opens and closes on one.
      const swell = Math.max(0, gust(t + lean) * 1.9 - 0.6) * (0.6 + 0.4 * eddy(t + lean))
        + 0.5 * Math.exp(-(((t - 1.6) / 1.6) ** 2)) + 0.45 * Math.exp(-(((t - 41.5) / 2.2) ** 2));
      const white = next() * 2 - 1;
      // Single leaves: more of them the stronger the gust.
      if (next() < swell * 26 / SR) tick = 0.5 + 0.5 * next();
      tick *= 0.9965;
      const sample = gain * swell * (
        0.5 * air(white)
        + leaves(white) * (0.35 + 0.65 * flutter(t)) * 1.1
        + high(white) * (0.2 + 0.8 * shimmer(t)) * 0.5
        + leaf(white) * tick * 2.2);
      channel[i] += sample;
      send[i] += sample * 0.25;
    }
  }
}

/** A small hall (Freeverb: eight damped combs into four all-passes per side), 25 ms away. */
function hall(input, spread) {
  const combs = [1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617].map((n) => n + spread);
  const allpasses = [556, 441, 341, 225].map((n) => n + spread);
  const feedback = 0.9, damp = 0.34, predelay = Math.round(0.025 * SR);
  const out = new Float32Array(input.length);
  for (const size of combs) {
    const buffer = new Float32Array(size);
    let index = 0, store = 0;
    for (let i = predelay; i < input.length; i++) {
      const y = buffer[index];
      store = y * (1 - damp) + store * damp;
      buffer[index] = input[i - predelay] * 0.015 + store * feedback;
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

/** One of the app's own sounds, decoded by ffmpeg, onto the app bus. */
function appSound(name, time, gain) {
  const raw = execFileSync('ffmpeg', ['-v', 'error', '-i', path.join(RAW, `${name}.ogg`), '-ac', '1', '-ar', String(SR), '-f', 'f32le', '-'],
    { maxBuffer: 64 * 1024 * 1024 });
  const samples = new Float32Array(raw.buffer, raw.byteOffset, raw.length / 4);
  const start = Math.round(time * SR);
  for (let i = 0; i < samples.length && start + i < LENGTH; i++) {
    appLeft[start + i] += samples[i] * gain;
    appRight[start + i] += samples[i] * gain;
    sendLeft[start + i] += samples[i] * gain * 0.35;
    sendRight[start + i] += samples[i] * gain * 0.35;
  }
}

// ---------- The piece ----------
const at = (bar, beat) => FIRST_BEAT + bar * 4 + beat;
const human = random(5);
/** A hand is never exactly on the beat or exactly as loud. */
const hand = (time, spread = 0.012) => time + (human() - 0.5) * 2 * spread;

// The drone: two sung bowls per bar, root and fifth (or third), overlapping into the next bar.
const drone = [
  ['D3', 'A3'], ['D3', 'A3'], ['G3', 'D4'], ['D3', 'A3'], ['A3', 'E4'], ['D3', 'A3'],
  ['G3', 'D4'], ['B3', 'D4'], ['A3', 'E4'], ['D3', 'A3'], ['D3', 'A3'],
];
drone.forEach(([low, high], bar) => {
  const quiet = bar === 5 ? 0.6 : 1; // under the Klangschale
  sungBowl(at(bar, 0) - 1.6, 4.2, hz(low), 0.06 * quiet, -0.25, 300 + bar);
  sungBowl(at(bar, 0) - 1.1, 4.2, hz(high), 0.036 * quiet, 0.3, 400 + bar);
});

// Struck bowls: [bar, beat, note, gain, pan]. The large ones mark the film's chapters.
[
  [0, 0, 'D3', 0.34, -0.1], [0, 2, 'A5', 0.05, 0.4], [0, 3.5, 'D5', 0.06, -0.35],
  [1, 0, 'A3', 0.16, 0.2], [1, 2.5, 'E5', 0.05, 0.45],
  [2, 0, 'G3', 0.18, -0.2], [2, 1.5, 'B5', 0.045, -0.4], [2, 3, 'D5', 0.05, 0.3],
  [3, 0, 'D4', 0.14, 0.15], [3, 2, 'A5', 0.04, -0.3],
  [4, 0, 'A3', 0.13, -0.15],
  [5, 2.5, 'D3', 0.14, 0.1],
  [6, 0, 'G3', 0.24, -0.1], [6, 2, 'D5', 0.06, 0.4], [6, 3.5, 'B5', 0.04, -0.4],
  [7, 0, 'B3', 0.16, 0.2], [7, 2.5, 'D6', 0.035, 0.35], [7, 3, 'A4', 0.07, -0.3],
  [8, 0, 'A3', 0.17, -0.2], [8, 1.5, 'E5', 0.05, 0.3], [8, 3, 'A5', 0.04, -0.35],
  [9, 0, 'D4', 0.16, 0.15], [9, 2, 'A5', 0.045, 0.4], [9, 3.5, 'E5', 0.045, -0.3],
  [10, 0, 'D3', 0.36, 0], [10, 0.02, 'A3', 0.13, 0.3], [10, 1.5, 'A5', 0.05, -0.4], [10, 2.5, 'D6', 0.04, 0.4],
].forEach(([bar, beat, note, gain, pan], n) => bowl(hand(at(bar, beat), 0.008), hz(note), gain, pan, 700 + n));

// The string: [bar, beat, note, velocity]. It rests in the opening, under the alert and in the close.
const melody = [
  [0, 3, 'D4', 0.4], [0, 3.5, 'A4', 0.45],
  [1, 0, 'D4', 0.75], [1, 1, 'A4', 0.6], [1, 1.5, 'B4', 0.55], [1, 2, 'D5', 0.8], [1, 3, 'B4', 0.55], [1, 3.5, 'A4', 0.5],
  [2, 0, 'G4', 0.7], [2, 1, 'B4', 0.55], [2, 2, 'D5', 0.7], [2, 2.5, 'E5', 0.6], [2, 3, 'D5', 0.55],
  // The timer runs: one note per second, like a clock.
  [3, 0, 'D4', 0.7], [3, 1, 'A4', 0.6], [3, 2, 'D4', 0.6], [3, 3, 'A4', 0.6],
  [6, 0, 'G4', 0.75], [6, 1, 'B4', 0.6], [6, 1.5, 'D5', 0.6], [6, 2, 'G5', 0.75], [6, 3, 'E5', 0.6], [6, 3.5, 'D5', 0.5],
  [7, 0, 'B4', 0.7], [7, 1, 'A4', 0.55], [7, 1.5, 'G4', 0.5], [7, 2, 'A4', 0.7], [7, 3.5, 'D5', 0.5],
  [8, 0, 'E4', 0.7], [8, 1, 'A4', 0.6], [8, 2, 'B4', 0.7], [8, 3, 'A4', 0.55], [8, 3.5, 'E4', 0.45],
  [9, 0, 'D4', 0.75], [9, 1, 'A4', 0.6], [9, 1.5, 'B4', 0.55], [9, 2, 'D5', 0.75], [9, 3, 'E5', 0.6], [9, 3.5, 'D5', 0.5],
  [10, 0, 'D4', 0.7], [10, 0.5, 'A4', 0.5], [10, 1, 'D5', 0.55],
];
melody.forEach(([bar, beat, note, velocity], n) => {
  pluck(hand(at(bar, beat)), note, velocity * (0.5 + 0.12 * human()), ((n * 37) % 11) / 11 * 0.6 - 0.3, 1000 + n);
});
// A low string under most bars.
[[1, 'D3'], [2, 'G3'], [3, 'D3'], [6, 'G3'], [7, 'B3'], [8, 'A3'], [9, 'D3'], [10, 'D3']]
  .forEach(([bar, note], n) => pluck(hand(at(bar, 0)), note, 0.24, -0.15, 2000 + n));
// The two time-lapses: the string runs up the scale, faster than the clock, into the tick and into the Klangschale.
const run = ['D4', 'E4', 'G4', 'A4', 'B4', 'D5', 'E5', 'G5'];
run.forEach((note, n) => pluck(hand(LAPSE_1 + 0.1 + n * 0.165, 0.006), note, 0.22 + 0.025 * n, n / 7 * 0.7 - 0.35, 3000 + n));
run.forEach((note, n) => pluck(hand(LAPSE_2 + 0.1 + n * 0.165, 0.006), note === 'G5' ? 'A5' : note, 0.24 + 0.035 * n, 0.35 - n / 7 * 0.7, 3100 + n));

wind(0.07);

// The app's own sounds, where the film shows them.
appSound('tick', PRE_ALERT, 1.3);
appSound('klangschale', ALERT, 0.55);

// ---------- Mix ----------
const wetLeft = hall(sendLeft, 0), wetRight = hall(sendRight, 23);
/** The music steps back a little while the app's sounds play. */
const duck = (t) => 1
  - 0.3 * Math.min(1, Math.max(0, (t - PRE_ALERT + 0.1) / 0.1)) * Math.min(1, Math.max(0, (PRE_ALERT + 1.0 - t) / 0.5))
  - 0.45 * Math.min(1, Math.max(0, (t - ALERT + 0.15) / 0.15)) * Math.min(1, Math.max(0, (ALERT + 3.4 - t) / 1.2));
let peak = 0;
for (let i = 0; i < LENGTH; i++) {
  const level = duck(i / SR);
  left[i] = (left[i] + wetLeft[i]) * level + appLeft[i];
  right[i] = (right[i] + wetRight[i]) * level + appRight[i];
  peak = Math.max(peak, Math.abs(left[i]), Math.abs(right[i]));
}
// Gentle saturation rounds the loudest strikes, so the rest can sit higher.
const DRIVE = 1.6;
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
console.log(`${out} (${SECONDS} s, peak before mastering ${peak.toFixed(3)})`);
