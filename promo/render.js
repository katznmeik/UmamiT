'use strict';
/**
 * Renders a promo scene to MP4: every frame is drawn by the scene's
 * `window.seek(t)` in headless Chromium and piped to ffmpeg.
 *
 *   node render.js <scene> [--fps 60] [--audio out/music.wav] [--workers 4] [--from 0] [--to 44] [--out out/<scene>.mp4]
 *   node render.js <scene> --still 2.5       one frame as PNG (out/<scene>-2.50.png)
 *   node render.js <scene> --sheet 1,5,9     several frames side by side (out/<scene>-sheet.png)
 *   node render.js <scene> --compare         the rebuilt app screen alone, 822 × 1782 like the goldens
 *
 * Playwright and Chromium are preinstalled in cloud sessions; locally, install
 * `playwright` yourself. ffmpeg must be on the PATH.
 */
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const { spawn, execFileSync } = require('node:child_process');
const { chromium } = require('playwright');

const REPO = path.resolve(__dirname, '..');
const OUT = path.join(__dirname, 'out');
const MIME = {
  '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.png': 'image/png',
  '.webp': 'image/webp', '.ttf': 'font/ttf', '.woff2': 'font/woff2', '.svg': 'image/svg+xml',
};
const X264 = [
  '-c:v', 'libx264', '-preset', 'slow', '-crf', '16', '-pix_fmt', 'yuv420p',
  '-color_primaries', 'bt709', '-color_trc', 'bt709', '-colorspace', 'bt709',
  '-vf', 'scale=out_color_matrix=bt709:out_range=tv',
];

function parseArgs(argv) {
  const args = { scene: argv[0], fps: 60, workers: 4, compare: false, still: null, sheet: null, audio: null, out: null, from: 0, to: null };
  for (let i = 1; i < argv.length; i++) {
    const flag = argv[i];
    if (flag === '--compare') args.compare = true;
    else if (flag === '--fps') args.fps = Number(argv[++i]);
    else if (flag === '--workers') args.workers = Number(argv[++i]);
    else if (flag === '--still') args.still = Number(argv[++i]);
    else if (flag === '--sheet') args.sheet = argv[++i].split(',').map(Number);
    else if (flag === '--audio') args.audio = argv[++i];
    else if (flag === '--from') args.from = Number(argv[++i]);
    else if (flag === '--to') args.to = Number(argv[++i]);
    else if (flag === '--out') args.out = argv[++i];
    else throw new Error(`Unknown argument: ${flag}`);
  }
  if (!args.scene) throw new Error('Usage: node render.js <scene> [--fps 60] [--audio file] [--workers 4] [--from s] [--to s] [--out file] [--still t] [--sheet t,t,…] [--compare]');
  return args;
}

/** Serves the repository read-only on localhost, so scenes use the app's own fonts and artwork by path. */
function serveRepo() {
  const server = http.createServer((request, response) => {
    const file = path.normalize(path.join(REPO, decodeURIComponent(new URL(request.url, 'http://x').pathname)));
    if (!file.startsWith(REPO + path.sep) || !fs.existsSync(file) || !fs.statSync(file).isFile()) {
      response.writeHead(404).end();
      return;
    }
    response.writeHead(200, { 'Content-Type': MIME[path.extname(file)] ?? 'application/octet-stream' });
    fs.createReadStream(file).pipe(response);
  });
  return new Promise((resolve) => server.listen(0, '127.0.0.1', () => resolve(server)));
}

async function openScene(browser, url, viewport) {
  const page = await browser.newPage({ viewport, deviceScaleFactor: 1 });
  page.on('pageerror', (error) => { console.error(error); process.exit(1); });
  page.on('requestfailed', (request) => { console.error(`Failed to load ${request.url()}`); process.exit(1); });
  await page.goto(url);
  await page.evaluate(() => window.ready);
  return page;
}

/** One worker: its own browser (Chromium draws and captures on one thread each) and its own encoder, for the frames [first, last). */
async function renderSegment(url, args, first, last, file, progress) {
  const browser = await chromium.launch();
  const page = await openScene(browser, url, { width: 1080, height: 1920 });
  const ffmpeg = spawn('ffmpeg', [
    '-y', '-loglevel', 'error', '-f', 'image2pipe', '-framerate', String(args.fps), '-c:v', 'png', '-i', '-', ...X264, file,
  ], { stdio: ['pipe', 'inherit', 'inherit'] });
  const done = new Promise((resolve, reject) => {
    ffmpeg.on('error', reject);
    ffmpeg.on('close', (code) => (code === 0 ? resolve() : reject(new Error(`ffmpeg exited with ${code}`))));
  });
  for (let frame = first; frame < last; frame++) {
    await page.evaluate((time) => window.seek(time), frame / args.fps);
    const png = await page.screenshot({ type: 'png' });
    if (!ffmpeg.stdin.write(png)) await new Promise((resolve) => ffmpeg.stdin.once('drain', resolve));
    progress();
  }
  ffmpeg.stdin.end();
  await done;
  await browser.close();
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  fs.mkdirSync(OUT, { recursive: true });
  const server = await serveRepo();
  const browser = await chromium.launch();
  try {
    const url = `http://127.0.0.1:${server.address().port}/promo/scenes/${args.scene}.html${args.compare ? '?compare' : ''}`;

    if (args.compare || args.still !== null || args.sheet) {
      const page = await openScene(browser, url, args.compare ? { width: 822, height: 1782 } : { width: 1080, height: 1920 });
      const times = args.sheet ?? [args.still ?? 0];
      const files = [];
      for (const t of times) {
        await page.evaluate((time) => window.seek(time), t);
        const file = path.join(OUT, `${args.scene}-${args.compare ? 'compare' : t.toFixed(2)}.png`);
        await page.screenshot({ path: file });
        files.push(file);
      }
      if (args.sheet) {
        const sheet = args.out ?? path.join(OUT, `${args.scene}-sheet.png`);
        execFileSync('ffmpeg', [
          '-y', '-loglevel', 'error', ...files.flatMap((file) => ['-i', file]),
          '-filter_complex', `${files.map((_, i) => `[${i}:v]scale=360:-1[v${i}]`).join(';')};${files.map((_, i) => `[v${i}]`).join('')}hstack=inputs=${files.length}`,
          '-frames:v', '1', sheet,
        ]);
        files.forEach((file) => fs.unlinkSync(file));
        console.log(sheet);
      } else {
        console.log(files[0]);
      }
      return;
    }

    const probe = await openScene(browser, url, { width: 1080, height: 1920 });
    const duration = await probe.evaluate(() => window.DURATION);
    await probe.close();
    const first = Math.round(args.from * args.fps), last = Math.round((args.to ?? duration) * args.fps);
    const total = last - first;
    const workers = Math.max(1, Math.min(args.workers, Math.ceil(total / args.fps)));
    const out = args.out ?? path.join(OUT, `${args.scene}.mp4`);

    let rendered = 0;
    const started = Date.now();
    const progress = () => {
      rendered++;
      if (rendered % 30 === 0 || rendered === total) {
        process.stdout.write(`\r${rendered}/${total} frames, ${((Date.now() - started) / 1000).toFixed(0)} s`);
      }
    };
    const segments = [];
    const jobs = [];
    for (let worker = 0; worker < workers; worker++) {
      const a = first + Math.floor(total * worker / workers), b = first + Math.floor(total * (worker + 1) / workers);
      const file = path.join(OUT, `.segment-${worker}.mp4`);
      segments.push(file);
      jobs.push(renderSegment(url, args, a, b, file, progress));
    }
    await Promise.all(jobs);

    // Join the segments without re-encoding and add the sound.
    const list = path.join(OUT, '.segments.txt');
    fs.writeFileSync(list, segments.map((file) => `file '${file}'`).join('\n'));
    const seconds = total / args.fps;
    const audio = args.audio
      ? ['-ss', String(args.from), '-i', args.audio, '-map', '0:v', '-map', '1:a',
        '-af', `afade=t=out:st=${seconds - 0.6}:d=0.6`, '-c:a', 'aac', '-b:a', '192k']
      : [];
    execFileSync('ffmpeg', [
      '-y', '-loglevel', 'error', '-f', 'concat', '-safe', '0', '-i', list, ...audio,
      '-c:v', 'copy', '-t', String(seconds), '-movflags', '+faststart', out,
    ], { stdio: 'inherit' });
    [...segments, list].forEach((file) => fs.unlinkSync(file));
    console.log(`\n${out}`);
  } finally {
    await browser.close();
    server.close();
  }
}

main().catch((error) => { console.error(error); process.exit(1); });
