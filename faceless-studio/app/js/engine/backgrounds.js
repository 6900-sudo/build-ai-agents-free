import { mulberry32, rgba, shade, clamp, UserError } from './util.js';
import { ClipReader } from './clipreader.js';

// Tests can switch the fast clip reader off to compare against the old <video> seeking.
let clipDecoding = true;
export function setClipDecoding(enabled) {
  clipDecoding = !!enabled;
}

export const BG_STYLES = {
  aurora: 'Aurora glow',
  gradient: 'Smooth gradient',
  particles: 'Floating particles',
  grid: 'Retro grid',
  bokeh: 'Bokeh lights',
  waves: 'Waves',
  halftone: 'Halftone dots',
  scanlines: 'Scanlines',
  solid: 'Solid brand colour',
  mix: 'Mix (changes every scene)',
};

const MIX_ORDER = ['aurora', 'particles', 'gradient', 'bokeh', 'waves', 'grid', 'halftone', 'scanlines'];

function palette(brand, seed) {
  const r = mulberry32(seed);
  const p = brand.primary || '#6d28d9';
  const a = brand.accent || '#f59e0b';
  const picks = [p, a, shade(p, 0.35), shade(a, -0.3), shade(p, -0.5)];
  for (let i = picks.length - 1; i > 0; i--) {
    const j = Math.floor(r() * (i + 1));
    [picks[i], picks[j]] = [picks[j], picks[i]];
  }
  return { base: shade(p, -0.82), colors: picks };
}

function drawAurora(ctx, W, H, t, rand, pal) {
  ctx.fillStyle = pal.base;
  ctx.fillRect(0, 0, W, H);
  ctx.globalCompositeOperation = 'lighter';
  for (let i = 0; i < 4; i++) {
    const fx = 0.05 + rand() * 0.12, fy = 0.04 + rand() * 0.1, ph = rand() * 6.28;
    const x = W * (0.5 + 0.4 * Math.sin(t * fx * 6.28 + ph));
    const y = H * (0.5 + 0.4 * Math.cos(t * fy * 6.28 + ph * 1.3));
    const rad = Math.max(W, H) * (0.45 + rand() * 0.25);
    const g = ctx.createRadialGradient(x, y, 0, x, y, rad);
    g.addColorStop(0, rgba(pal.colors[i % pal.colors.length], 0.55));
    g.addColorStop(1, rgba(pal.colors[i % pal.colors.length], 0));
    ctx.fillStyle = g;
    ctx.fillRect(0, 0, W, H);
  }
  ctx.globalCompositeOperation = 'source-over';
}

function drawGradient(ctx, W, H, t, rand, pal) {
  const ang = rand() * 6.28 + t * 0.15;
  const cx = W / 2, cy = H / 2, R = Math.hypot(W, H) / 2;
  const g = ctx.createLinearGradient(cx - Math.cos(ang) * R, cy - Math.sin(ang) * R, cx + Math.cos(ang) * R, cy + Math.sin(ang) * R);
  g.addColorStop(0, shade(pal.colors[0], -0.35));
  g.addColorStop(0.5, shade(pal.colors[1], -0.25));
  g.addColorStop(1, shade(pal.colors[2], -0.45));
  ctx.fillStyle = g;
  ctx.fillRect(0, 0, W, H);
}

function drawParticles(ctx, W, H, t, rand, pal) {
  const g = ctx.createLinearGradient(0, 0, 0, H);
  g.addColorStop(0, pal.base);
  g.addColorStop(1, shade(pal.colors[0], -0.6));
  ctx.fillStyle = g;
  ctx.fillRect(0, 0, W, H);
  const n = 70;
  for (let i = 0; i < n; i++) {
    const x0 = rand() * W, y0 = rand() * H, sp = 20 + rand() * 60, sz = 2 + rand() * 7, ph = rand() * 6.28;
    const y = ((y0 - t * sp) % H + H) % H;
    const x = x0 + Math.sin(t * 0.8 + ph) * 25;
    ctx.fillStyle = rgba(pal.colors[i % pal.colors.length], 0.35 + 0.4 * (0.5 + 0.5 * Math.sin(t * 2 + ph)));
    ctx.beginPath();
    ctx.arc(x, y, sz * (W / 1080), 0, 6.283);
    ctx.fill();
  }
}

function drawGrid(ctx, W, H, t, rand, pal) {
  ctx.fillStyle = pal.base;
  ctx.fillRect(0, 0, W, H);
  const horizon = H * 0.42;
  const sky = ctx.createLinearGradient(0, 0, 0, horizon);
  sky.addColorStop(0, pal.base);
  sky.addColorStop(1, rgba(pal.colors[1], 0.55));
  ctx.fillStyle = sky;
  ctx.fillRect(0, 0, W, horizon);
  ctx.strokeStyle = rgba(pal.colors[0], 0.7);
  ctx.lineWidth = Math.max(1.5, W / 540);
  const vx = W / 2;
  for (let i = -14; i <= 14; i++) {
    ctx.beginPath();
    ctx.moveTo(vx, horizon);
    ctx.lineTo(vx + i * W * 0.18, H);
    ctx.stroke();
  }
  const off = (t * 0.6) % 1;
  for (let k = 0; k < 14; k++) {
    const z = (k + off) / 14;
    const y = horizon + (H - horizon) * z * z;
    ctx.globalAlpha = clamp(z * 1.4, 0, 1);
    ctx.beginPath();
    ctx.moveTo(0, y);
    ctx.lineTo(W, y);
    ctx.stroke();
  }
  ctx.globalAlpha = 1;
}

function drawBokeh(ctx, W, H, t, rand, pal) {
  ctx.fillStyle = pal.base;
  ctx.fillRect(0, 0, W, H);
  ctx.globalCompositeOperation = 'lighter';
  for (let i = 0; i < 26; i++) {
    const x = rand() * W + Math.sin(t * 0.3 + i) * 40;
    const y = rand() * H + Math.cos(t * 0.25 + i) * 50;
    const r = (40 + rand() * 140) * (W / 1080);
    const g = ctx.createRadialGradient(x, y, r * 0.2, x, y, r);
    const c = pal.colors[i % pal.colors.length];
    g.addColorStop(0, rgba(c, 0.28));
    g.addColorStop(0.8, rgba(c, 0.12));
    g.addColorStop(1, rgba(c, 0));
    ctx.fillStyle = g;
    ctx.beginPath();
    ctx.arc(x, y, r, 0, 6.283);
    ctx.fill();
  }
  ctx.globalCompositeOperation = 'source-over';
}

function drawWaves(ctx, W, H, t, rand, pal) {
  ctx.fillStyle = pal.base;
  ctx.fillRect(0, 0, W, H);
  for (let L = 0; L < 5; L++) {
    const amp = H * (0.03 + rand() * 0.04), freq = 1.5 + rand() * 2, sp = 0.4 + rand() * 0.6;
    const baseY = H * (0.35 + L * 0.13);
    ctx.fillStyle = rgba(pal.colors[L % pal.colors.length], 0.22 + L * 0.06);
    ctx.beginPath();
    ctx.moveTo(0, H);
    for (let x = 0; x <= W; x += W / 60) ctx.lineTo(x, baseY + Math.sin((x / W) * freq * 6.28 + t * sp + L) * amp);
    ctx.lineTo(W, H);
    ctx.closePath();
    ctx.fill();
  }
}

function drawSolid(ctx, W, H, t, rand, pal, brand) {
  const c = brand.primary || '#111827';
  const g = ctx.createRadialGradient(W / 2, H / 2, 0, W / 2, H / 2, Math.hypot(W, H) / 1.6);
  g.addColorStop(0, shade(c, 0.08));
  g.addColorStop(1, shade(c, -0.45));
  ctx.fillStyle = g;
  ctx.fillRect(0, 0, W, H);
}

// Halftone: a grid of dots whose size follows two drifting glows.
function drawHalftone(ctx, W, H, t, rand, pal) {
  ctx.fillStyle = pal.base;
  ctx.fillRect(0, 0, W, H);
  const step = Math.max(14, Math.round(W / 34));
  const blobs = [0, 1].map((i) => ({ ph: rand() * 6.28, fx: 0.05 + rand() * 0.08, c: pal.colors[i] }));
  const R = Math.max(W, H) * 0.55;
  for (let y = step / 2; y < H; y += step) {
    for (let x = step / 2; x < W; x += step) {
      let best = 0, color = blobs[0].c;
      for (const b of blobs) {
        const bx = W * (0.5 + 0.38 * Math.sin(t * b.fx * 6.28 + b.ph));
        const by = H * (0.5 + 0.38 * Math.cos(t * b.fx * 5.1 + b.ph * 1.7));
        const v = 1 - Math.hypot(x - bx, y - by) / R;
        if (v > best) { best = v; color = b.c; }
      }
      if (best <= 0.02) continue;
      ctx.fillStyle = rgba(color, 0.35 + 0.5 * best);
      ctx.beginPath();
      ctx.arc(x, y, (step / 2) * Math.min(1, best * 1.15), 0, 6.283);
      ctx.fill();
    }
  }
}

// Scanlines: a soft brand glow behind moving horizontal lines and a light film grain.
function drawScanlines(ctx, W, H, t, rand, pal) {
  const g = ctx.createLinearGradient(0, 0, 0, H);
  g.addColorStop(0, shade(pal.colors[0], -0.55));
  g.addColorStop(1, shade(pal.colors[1], -0.65));
  ctx.fillStyle = g;
  ctx.fillRect(0, 0, W, H);
  const cy = H * (0.5 + 0.3 * Math.sin(t * 0.35 + rand() * 6.28));
  const glow = ctx.createRadialGradient(W / 2, cy, 0, W / 2, cy, Math.max(W, H) * 0.6);
  glow.addColorStop(0, rgba(pal.colors[2], 0.45));
  glow.addColorStop(1, rgba(pal.colors[2], 0));
  ctx.fillStyle = glow;
  ctx.fillRect(0, 0, W, H);
  const gap = Math.max(4, Math.round(H / 320));
  ctx.fillStyle = 'rgba(0,0,0,0.28)';
  const off = Math.floor((t * 40) % (gap * 2));
  for (let y = -gap * 2 + off; y < H; y += gap * 2) ctx.fillRect(0, y, W, gap);
  // Grain is seeded per frame time, so the same video always renders the same.
  const grain = mulberry32((Math.floor(t * 30) * 2654435761) >>> 0);
  const dot = Math.max(2, Math.round(W / 360));
  for (let i = 0; i < 900; i++) {
    ctx.fillStyle = grain() > 0.5 ? 'rgba(255,255,255,0.07)' : 'rgba(0,0,0,0.12)';
    ctx.fillRect(grain() * W, grain() * H, dot, dot);
  }
}

const DRAW = { halftone: drawHalftone, scanlines: drawScanlines, aurora: drawAurora, gradient: drawGradient, particles: drawParticles, grid: drawGrid, bokeh: drawBokeh, waves: drawWaves, solid: drawSolid };

export function drawProcedural(ctx, W, H, style, sceneIndex, tLocal, brand, baseSeed) {
  const s = style === 'mix' ? MIX_ORDER[sceneIndex % MIX_ORDER.length] : style;
  const seed = (baseSeed + sceneIndex * 7919) >>> 0;
  const fn = DRAW[s] || drawAurora;
  fn(ctx, W, H, tLocal, mulberry32(seed), palette(brand, seed), brand);
}

/**
 * Draws a photo or clip frame filling the screen, with a slow zoom and pan (Ken Burns).
 * shot > 0 re-frames the same picture for a new shot: the zoom direction flips and odd shots
 * are a tighter "punch-in", so the cut is clearly visible.
 */
export function drawCover(ctx, W, H, source, sw, sh, progress, seed, shot = 0) {
  const r = mulberry32(seed);
  const zoomIn = (r() > 0.5) !== (shot % 2 === 1);
  const p = clamp(progress, 0, 1);
  // Later shots punch in (tighter on odd shots) and move the focus left or right of centre.
  const punch = shot === 0 ? 1 : shot % 2 === 1 ? 1.25 : 1.12;
  const focus = shot === 0 ? 0 : shot % 2 === 1 ? 0.35 : -0.35;
  const zoom = (zoomIn ? 1.05 + 0.13 * p : 1.18 - 0.13 * p) * punch;
  const scale = Math.max(W / sw, H / sh) * zoom;
  const dw = sw * scale, dh = sh * scale;
  const dirX = r() * 2 - 1, dirY = r() * 2 - 1;
  const x = clamp((W - dw) / 2 + (dirX * 0.8 * (p - 0.5) + focus) * (dw - W), W - dw, 0);
  const y = (H - dh) / 2 + dirY * (dh - H) * 0.8 * (p - 0.5);
  ctx.drawImage(source, x, y, dw, dh);
}

async function videoElement(blob) {
  const el = document.createElement('video');
  el.muted = true;
  el.playsInline = true;
  el.preload = 'auto';
  el.src = URL.createObjectURL(blob);
  await new Promise((res, rej) => {
    el.onloadeddata = res;
    el.onerror = () => rej(new UserError('One of your background videos could not be opened. Try MP4 (H.264).'));
  });
  return el;
}

export async function loadMediaItem(blob) {
  if (blob.type.startsWith('video/')) {
    // When this browser can decode the clip itself, the renderer reads its frames in order
    // (much faster than seeking a <video> player, and no player is needed at all).
    if (clipDecoding) {
      try {
        const bytes = await blob.arrayBuffer();
        const probe = await ClipReader.open(bytes);
        if (probe) {
          const item = { kind: 'video', el: null, blob, w: probe.w, h: probe.h, duration: probe.duration || 1, clipBytes: bytes };
          probe.close();
          return item;
        }
      } catch {
        // unusual file: use the <video> player below
      }
    }
    const el = await videoElement(blob);
    return { kind: 'video', el, blob, w: el.videoWidth, h: el.videoHeight, duration: el.duration || 1, clipBytes: null };
  }
  try {
    const bmp = await createImageBitmap(blob);
    return { kind: 'image', el: bmp, w: bmp.width, h: bmp.height };
  } catch {
    throw new UserError('One of your background images could not be opened. Use JPG, PNG or WebP.');
  }
}

export function openClipReader(item) {
  return item.clipBytes ? ClipReader.open(item.clipBytes) : Promise.resolve(null);
}

export async function seekVideo(item, time) {
  if (!item.el) item.el = await videoElement(item.blob); // the fast reader failed part-way
  const el = item.el;
  const target = item.duration > 0 ? time % item.duration : 0;
  if (Math.abs(el.currentTime - target) < 0.001 && el.readyState >= 2) return;
  await new Promise((res) => {
    const done = () => {
      el.removeEventListener('seeked', done);
      res();
    };
    el.addEventListener('seeked', done);
    el.currentTime = target;
    setTimeout(done, 2000);
  });
}

// Pexels API address. Tests point it at a local fake server; the media files themselves come
// from the addresses the API returns.
let STOCK_BASE = 'https://api.pexels.com';
export function setStockBase(url) {
  STOCK_BASE = String(url || 'https://api.pexels.com').replace(/\/+$/, '');
}

const MAX_CLIP_BYTES = 15 * 1024 * 1024;

async function pexelsSearch(apiKey, path) {
  const res = await fetch(`${STOCK_BASE}${path}`, { headers: { Authorization: apiKey } });
  if (res.status === 401 || res.status === 403) throw new UserError('Pexels rejected your API key. Check it in Settings.');
  if (!res.ok) throw new Error('Pexels HTTP ' + res.status);
  return res.json();
}

async function loadPhoto(photo) {
  const img = await fetch(photo.src.large2x || photo.src.large);
  if (!img.ok) throw new Error('image HTTP ' + img.status);
  return loadMediaItem(await img.blob());
}

/** The top photo for a keyword; `extras` more results (best effort) go in item.extras. */
async function pexelsPhoto(apiKey, kw, orientation, extras = 0) {
  const json = await pexelsSearch(apiKey, `/v1/search?query=${encodeURIComponent(kw)}&orientation=${orientation}&per_page=5`);
  const photos = json.photos || [];
  if (!photos[0]) return null;
  const item = await loadPhoto(photos[0]);
  item.extras = [];
  for (const photo of photos.slice(1, 1 + extras)) {
    try {
      item.extras.push(await loadPhoto(photo));
    } catch {
      // A missing extra photo just means that shot re-frames the first one.
    }
  }
  return item;
}

/** Picks the smallest MP4 that is at least 720 px on its short side (or the largest smaller one). */
export function pickClipFile(files, orientation) {
  const mp4 = (files || []).filter((f) => f && f.link && (f.file_type || 'video/mp4') === 'video/mp4' && f.width && f.height);
  const wantPortrait = orientation === 'portrait';
  const shaped = mp4.filter((f) => (orientation === 'square' ? true : (f.height >= f.width) === wantPortrait));
  const pool = shaped.length ? shaped : mp4;
  const short = (f) => Math.min(f.width, f.height);
  const big = pool.filter((f) => short(f) >= 720 && short(f) <= 1440).sort((a, b) => short(a) - short(b));
  if (big.length) return big[0];
  return pool.filter((f) => short(f) < 720).sort((a, b) => short(b) - short(a))[0] || null;
}

async function pexelsClip(apiKey, kw, orientation) {
  const json = await pexelsSearch(apiKey, `/videos/search?query=${encodeURIComponent(kw)}&orientation=${orientation}&per_page=8&size=medium`);
  for (const video of json.videos || []) {
    const file = pickClipFile(video.video_files, orientation);
    if (!file) continue;
    const res = await fetch(file.link);
    if (!res.ok) continue;
    const size = Number(res.headers.get('content-length') || 0);
    if (size > MAX_CLIP_BYTES) {
      res.body?.cancel?.();
      continue;
    }
    const blob = await res.blob();
    if (blob.size > MAX_CLIP_BYTES) continue;
    try {
      return await loadMediaItem(blob.type.startsWith('video/') ? blob : new Blob([blob], { type: 'video/mp4' }));
    } catch {
      // This device can't open that clip; try the next one (or a photo).
    }
  }
  return null;
}

/**
 * Finds one stock background per keyword. kind 'video' looks for a clip first, then a photo.
 * A keyword with nothing usable gets null, which the renderer draws as an animated background.
 */
export async function fetchPexelsMedia(apiKey, keywords, orientation, { kind = 'photo', extras = [], onWarn, onProgress } = {}) {
  const cache = new Map();
  const out = [];
  // Extra photos for long scenes (one per later shot, at most 2), shared when keywords repeat.
  const wantExtra = new Map();
  keywords.forEach((kw, i) => wantExtra.set(kw, Math.max(wantExtra.get(kw) || 0, Math.min(2, extras[i] || 0))));
  let done = 0;
  for (const kw of keywords) {
    if (!cache.has(kw)) {
      cache.set(
        kw,
        (async () => {
          if (kind === 'video') {
            const clip = await pexelsClip(apiKey, kw, orientation);
            if (clip) return clip;
            const photo = await pexelsPhoto(apiKey, kw, orientation);
            if (photo) onWarn?.(`No stock clip for "${kw}", so a photo was used for that scene.`);
            return photo;
          }
          return pexelsPhoto(apiKey, kw, orientation, wantExtra.get(kw));
        })()
          .then((item) => {
            if (!item) onWarn?.(`No stock ${kind === 'video' ? 'clip or photo' : 'photo'} for "${kw}". Using an animated background for that scene.`);
            return item;
          })
          .catch((err) => {
            if (err instanceof UserError) throw err;
            onWarn?.(`No stock ${kind === 'video' ? 'clip' : 'photo'} for "${kw}" (${err.message}). Using an animated background for that scene.`);
            return null;
          })
      );
    }
    out.push(await cache.get(kw));
    onProgress?.({ stage: 'stock', done: ++done, total: keywords.length });
  }
  return out;
}

/** Kept for existing callers: photo backgrounds only. */
export function fetchPexelsImages(apiKey, keywords, orientation, onWarn) {
  return fetchPexelsMedia(apiKey, keywords, orientation, { kind: 'photo', onWarn });
}
