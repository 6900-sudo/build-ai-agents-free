#!/usr/bin/env node
// A stand-in for the Pexels API, for tests only (no Pexels key is needed).
//
// It answers /v1/search (photos) and /videos/search (clips) in Pexels' JSON format. The media are
// real Pexels photos (downloaded from Pexels' public image server) and short portrait clips made
// from them with ffmpeg. A search for "nothingmatches" returns no results, to test fallbacks.
// The key must be "test-key", otherwise it answers 401 like the real API.
//
//   node android-app/tools/fake-pexels.mjs --media DIR [--port 8787]
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import { execFileSync } from 'node:child_process';

const PHOTO_IDS = ['1643383', '271624', '1571460', '1080721'];
export const TEST_KEY = 'test-key';

/** Downloads the photos and makes one 720x1280 clip per photo (slow pan). Skips existing files. */
export function prepareMedia(dir) {
  fs.mkdirSync(dir, { recursive: true });
  PHOTO_IDS.forEach((id, i) => {
    const photo = path.join(dir, `p${i}.jpg`);
    if (!fs.existsSync(photo)) {
      execFileSync('curl', ['-sSfL', '-m', '60', '-o', photo,
        `https://images.pexels.com/photos/${id}/pexels-photo-${id}.jpeg?auto=compress&cs=tinysrgb&w=1080`]);
    }
    const clip = path.join(dir, `c${i}.mp4`);
    if (!fs.existsSync(clip)) {
      execFileSync('ffmpeg', ['-v', 'error', '-y', '-loop', '1', '-i', photo, '-t', '6', '-r', '30',
        '-vf', "scale=-2:1400,crop=720:1280:x='(in_w-720)*t/6':y=(in_h-1280)/2,format=yuv420p",
        '-c:v', 'libx264', '-preset', 'veryfast', '-crf', '26', '-movflags', '+faststart', clip]);
    }
  });
}

export function startFakePexels({ dir, port = 8787, host = '0.0.0.0', publicBase }) {
  const base = () => publicBase || `http://127.0.0.1:${port}`;
  const counter = { photo: 0, video: 0 };
  const log = [];
  const server = http.createServer((req, res) => {
    const url = new URL(req.url, 'http://x');
    const cors = { 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Headers': 'Authorization' };
    if (req.method === 'OPTIONS') return res.writeHead(204, cors).end();

    if (url.pathname.startsWith('/media/')) {
      const file = path.join(dir, path.basename(url.pathname));
      if (!fs.existsSync(file)) return res.writeHead(404, cors).end();
      const type = file.endsWith('.mp4') ? 'video/mp4' : 'image/jpeg';
      res.writeHead(200, { ...cors, 'Content-Type': type, 'Content-Length': fs.statSync(file).size });
      return fs.createReadStream(file).pipe(res);
    }

    const query = url.searchParams.get('query') || '';
    log.push(`${url.pathname}?query=${query}`);
    if (req.headers.authorization !== TEST_KEY) return res.writeHead(401, cors).end();
    const json = (body) => res.writeHead(200, { ...cors, 'Content-Type': 'application/json' }).end(JSON.stringify(body));
    const none = query === 'nothingmatches';

    if (url.pathname === '/v1/search') {
      const i = counter.photo++ % PHOTO_IDS.length;
      return json({
        total_results: none ? 0 : 1,
        photos: none ? [] : [{
          id: Number(PHOTO_IDS[i]), width: 1080, height: 720, alt: query,
          src: { large2x: `${base()}/media/p${i}.jpg`, large: `${base()}/media/p${i}.jpg` },
        }],
      });
    }
    if (url.pathname === '/videos/search') {
      const i = counter.video++ % PHOTO_IDS.length;
      return json({
        total_results: none ? 0 : 1,
        videos: none ? [] : [{
          id: 9000 + i, width: 720, height: 1280, duration: 6,
          video_files: [
            // A landscape file and an oversized one that must NOT be picked for a portrait video.
            { quality: 'hd', file_type: 'video/mp4', width: 1280, height: 720, link: `${base()}/media/missing-landscape.mp4` },
            { quality: 'uhd', file_type: 'video/mp4', width: 2160, height: 3840, link: `${base()}/media/missing-uhd.mp4` },
            { quality: 'hd', file_type: 'video/mp4', width: 720, height: 1280, link: `${base()}/media/c${i}.mp4` },
          ],
        }],
      });
    }
    res.writeHead(404, cors).end();
  });
  return new Promise((resolve) => server.listen(port, host, () => resolve({ server, log, close: () => server.close() })));
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const arg = (n, d) => (process.argv.includes(n) ? process.argv[process.argv.indexOf(n) + 1] : d);
  const dir = path.resolve(arg('--media', 'fake-pexels-media'));
  prepareMedia(dir);
  const port = Number(arg('--port', 8787));
  await startFakePexels({ dir, port, publicBase: arg('--public-base') });
  console.log(`Fake Pexels API on port ${port}, media in ${dir}`);
}
