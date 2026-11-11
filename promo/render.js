'use strict';
/**
 * Renders a promo scene to MP4: every frame is drawn by the scene's
 * `window.seek(t)` in headless Chromium and piped as PNG to ffmpeg.
 *
 *   node render.js <scene> [--fps 60] [--music out/music.wav] [--out out/<scene>.mp4]
 *   node render.js <scene> --still 2.5       one frame as PNG (out/<scene>-2.50.png)
 *   node render.js <scene> --compare         the app screen alone, 822 × 1782 like the goldens
 *
 * Playwright and Chromium are preinstalled in cloud sessions; locally, install
 * `playwright` yourself. ffmpeg must be on the PATH.
 */
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const { spawn } = require('node:child_process');
const { chromium } = require('playwright');

const REPO = path.resolve(__dirname, '..');
const OUT = path.join(__dirname, 'out');
const MIME = {
  '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.png': 'image/png',
  '.webp': 'image/webp', '.ttf': 'font/ttf', '.woff2': 'font/woff2', '.svg': 'image/svg+xml',
};

function parseArgs(argv) {
  const args = { scene: argv[0], fps: 60, compare: false, still: null, music: null, out: null };
  for (let i = 1; i < argv.length; i++) {
    const flag = argv[i];
    if (flag === '--compare') args.compare = true;
    else if (flag === '--fps') args.fps = Number(argv[++i]);
    else if (flag === '--still') args.still = Number(argv[++i]);
    else if (flag === '--music') args.music = argv[++i];
    else if (flag === '--out') args.out = argv[++i];
    else throw new Error(`Unknown argument: ${flag}`);
  }
  if (!args.scene) throw new Error('Usage: node render.js <scene> [--fps 60] [--music file] [--out file] [--still t] [--compare]');
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

function encoder(args, duration, out) {
  const input = ['-y', '-loglevel', 'error', '-f', 'image2pipe', '-framerate', String(args.fps), '-c:v', 'png', '-i', '-'];
  const audio = args.music
    ? ['-i', args.music, '-af', `afade=t=out:st=${duration - 0.7}:d=0.7`, '-c:a', 'aac', '-b:a', '192k']
    : [];
  const video = [
    '-c:v', 'libx264', '-preset', 'slow', '-crf', '15', '-pix_fmt', 'yuv420p',
    '-color_primaries', 'bt709', '-color_trc', 'bt709', '-colorspace', 'bt709',
    '-vf', 'scale=out_color_matrix=bt709:out_range=tv',
    '-t', String(duration), '-movflags', '+faststart',
  ];
  return spawn('ffmpeg', [...input, ...audio, ...video, out], { stdio: ['pipe', 'inherit', 'inherit'] });
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  fs.mkdirSync(OUT, { recursive: true });
  const server = await serveRepo();
  const browser = await chromium.launch();
  try {
    const viewport = args.compare ? { width: 822, height: 1782 } : { width: 1080, height: 1920 };
    const page = await browser.newPage({ viewport, deviceScaleFactor: 1 });
    page.on('pageerror', (error) => { throw error; });
    page.on('requestfailed', (request) => { throw new Error(`Failed to load ${request.url()}`); });
    const query = args.compare ? '?compare' : '';
    await page.goto(`http://127.0.0.1:${server.address().port}/promo/scenes/${args.scene}.html${query}`);
    await page.evaluate(() => window.ready);
    const duration = await page.evaluate(() => window.DURATION);

    if (args.compare || args.still !== null) {
      const t = args.still ?? 0;
      await page.evaluate((time) => window.seek(time), t);
      const out = args.out ?? path.join(OUT, `${args.scene}-${args.compare ? 'compare' : t.toFixed(2)}.png`);
      await page.screenshot({ path: out });
      console.log(out);
      return;
    }

    const out = args.out ?? path.join(OUT, `${args.scene}.mp4`);
    const ffmpeg = encoder(args, duration, out);
    const done = new Promise((resolve, reject) => {
      ffmpeg.on('error', reject);
      ffmpeg.on('close', (code) => (code === 0 ? resolve() : reject(new Error(`ffmpeg exited with ${code}`))));
    });
    const frames = Math.round(duration * args.fps);
    for (let frame = 0; frame < frames; frame++) {
      await page.evaluate((time) => window.seek(time), frame / args.fps);
      const png = await page.screenshot({ type: 'png' });
      if (!ffmpeg.stdin.write(png)) await new Promise((resolve) => ffmpeg.stdin.once('drain', resolve));
      if (frame % args.fps === 0) process.stdout.write(`\r${frame}/${frames} frames`);
    }
    ffmpeg.stdin.end();
    await done;
    console.log(`\r${frames}/${frames} frames -> ${out}`);
  } finally {
    await browser.close();
    server.close();
  }
}

main().catch((error) => { console.error(error); process.exit(1); });
