// Serves Hugging Face model files to the test browser from a local cache (downloaded once with curl).
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';

const CACHE = process.env.FS_HF_CACHE || path.join(process.env.HOME || '/tmp', '.cache', 'faceless-studio-hf');

export async function routeHuggingFace(context) {
  fs.mkdirSync(CACHE, { recursive: true });
  await context.route(/https:\/\/(huggingface\.co|cdn-lfs[^/]*\.hf\.co|[^/]*\.huggingface\.co)\//, async (route) => {
    const url = route.request().url();
    const file = path.join(CACHE, crypto.createHash('sha1').update(url).digest('hex'));
    if (!fs.existsSync(file)) {
      try {
        execFileSync('curl', ['-sSfL', '--retry', '3', '-o', file + '.part', url], { stdio: 'pipe', timeout: 600000 });
        fs.renameSync(file + '.part', file);
      } catch (e) {
        return route.fulfill({ status: 404, body: 'not found' });
      }
    }
    const body = fs.readFileSync(file);
    const ct = url.endsWith('.json') ? 'application/json' : 'application/octet-stream';
    await route.fulfill({ status: 200, body, headers: { 'content-type': ct, 'access-control-allow-origin': '*', 'content-length': String(body.length) } });
  });
}
