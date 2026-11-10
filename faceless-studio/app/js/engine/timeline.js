const LEAD = 0.35;
const GAP = 0.25;
const VOICED_GAP = 0.12;
const TAIL = 0.9;
const MIN_TITLE = 1.8;
const END_CARD = 2.6;

function distributeWords(words, start, duration) {
  const total = words.reduce((a, w) => a + w.weight, 0) || 1;
  const s0 = start + duration * 0.03;
  const span = duration * 0.94;
  let acc = 0;
  return words.map((w) => {
    const ws = s0 + (acc / total) * span;
    acc += w.weight;
    return { text: w.text, start: ws, end: s0 + (acc / total) * span };
  });
}

/**
 * items: [{ kind: 'title'|'scene', words, number?, keyword?, sceneIndex? }]
 * voice: result of synthesize()
 */
export function buildTimeline(items, voice, { template, attribution, brand } = {}) {
  const uploaded = !!voice.fullBuffer;
  const gap = uploaded ? 0 : GAP;
  let t = LEAD;
  const out = [];
  const clips = [];

  if (uploaded) clips.push({ buffer: voice.fullBuffer, start: LEAD });

  items.forEach((item, i) => {
    const v = voice.segments[i];
    let dur = v.duration;
    // A silent title stays up long enough to read. A spoken one only gets a short hold, so the
    // narration never pauses for more than about 0.4 s.
    if (item.kind === 'title' && !uploaded) dur = v.buffer ? dur + Math.min(0.15, Math.max(0, MIN_TITLE - dur)) : Math.max(MIN_TITLE, dur);
    const words = v.wordTimes
      ? item.words.map((w, wi) => ({ text: w.text, start: t + v.wordTimes[wi].start, end: t + v.wordTimes[wi].end }))
      : distributeWords(item.words, t, v.duration);
    if (v.buffer) clips.push({ buffer: v.buffer, start: t });
    out.push({ ...item, start: t, end: t + dur, words });
    // Spoken scenes follow each other closely so the narration never stalls.
    t += dur + (v.buffer ? VOICED_GAP : gap);
  });

  const lastEnd = out.length ? out[out.length - 1].end : LEAD;
  let endCard = null;
  const wantsEndCard = template === 'promo' || (template === 'quote' && attribution) || (brand?.handle && template !== 'story');
  let duration = lastEnd + TAIL;
  if (wantsEndCard) {
    endCard = { start: lastEnd + 0.3, end: lastEnd + 0.3 + END_CARD };
    duration = endCard.end + 0.2;
  }
  // Extend each scene to the next scene's start so the background never goes blank.
  for (let i = 0; i < out.length; i++) {
    out[i].visualEnd = i + 1 < out.length ? out[i + 1].start : endCard ? endCard.start : duration;
    out[i].beats = beatsFor(out[i].start, out[i].visualEnd, i === 0 ? MAX_FIRST_SHOT : MAX_SHOT);
  }

  return { duration, items: out, voiceClips: clips, endCard };
}

// Retention rule: the picture changes at least every 5 s, and within 3 s at the start (the hook).
export const MAX_SHOT = 5;
export const MAX_FIRST_SHOT = 3;

/** Splits a scene's on-screen time into equal shots no longer than `max` seconds. */
export function beatsFor(start, end, max = MAX_SHOT) {
  const n = Math.max(1, Math.ceil((end - start) / max - 1e-6));
  const len = (end - start) / n;
  return Array.from({ length: n }, (_, k) => ({ start: start + k * len, end: start + (k + 1) * len }));
}

/** Which shot of a scene is on screen at time t (0 for the first). */
export function beatAt(item, t) {
  const beats = item.beats || [];
  let k = 0;
  for (let i = 1; i < beats.length; i++) if (beats[i].start <= t) k = i;
  return k;
}

/**
 * Pauses in the narration longer than `min` seconds (between the first and last word).
 * Scans the voice audio in 20 ms windows, so it also finds pauses inside an uploaded voiceover.
 */
export function narrationPauses(timeline, { min = 0.4, threshold = 0.004 } = {}) {
  const speech = [];
  for (const clip of timeline.voiceClips || []) {
    const buf = clip.buffer;
    if (!buf) continue;
    const data = buf.getChannelData(0);
    const win = Math.max(1, Math.round(buf.sampleRate * 0.02));
    let runStart = -1;
    for (let i = 0; i < data.length; i += win) {
      let sum = 0;
      const end = Math.min(data.length, i + win);
      for (let j = i; j < end; j++) sum += data[j] * data[j];
      const loud = Math.sqrt(sum / (end - i)) > threshold;
      const at = clip.start + i / buf.sampleRate;
      if (loud && runStart < 0) runStart = at;
      if (!loud && runStart >= 0) {
        speech.push([runStart, at]);
        runStart = -1;
      }
    }
    if (runStart >= 0) speech.push([runStart, clip.start + data.length / buf.sampleRate]);
  }
  speech.sort((a, b) => a[0] - b[0]);
  const pauses = [];
  let lastEnd = null;
  for (const [s, e] of speech) {
    if (lastEnd != null && s - lastEnd > min) pauses.push({ start: lastEnd, duration: s - lastEnd });
    lastEnd = lastEnd == null ? e : Math.max(lastEnd, e);
  }
  return pauses;
}

export function itemAt(timeline, t) {
  const items = timeline.items;
  let lo = 0, hi = items.length - 1, found = 0;
  while (lo <= hi) {
    const mid = (lo + hi) >> 1;
    if (items[mid].start <= t) {
      found = mid;
      lo = mid + 1;
    } else hi = mid - 1;
  }
  return found;
}
