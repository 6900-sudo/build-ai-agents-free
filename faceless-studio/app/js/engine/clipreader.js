import { createFile, DataStream, MP4BoxBuffer, Endianness } from '../../vendor/mp4box.mjs';

/**
 * Reads the frames of an MP4 clip in order with WebCodecs, for video backgrounds.
 *
 * Rendering asks for one frame per output frame, always moving forward in time. Seeking a <video>
 * element for each of those makes the browser decode again from the last keyframe every time,
 * which is very slow on phones. This reader decodes each frame once, in order, and keeps only the
 * frames it still needs. When the clip loops it starts again from the beginning.
 */
export class ClipReader {
  static async open(arrayBuffer) {
    if (typeof VideoDecoder === 'undefined' || typeof EncodedVideoChunk === 'undefined') return null;
    const demuxed = demux(arrayBuffer);
    if (!demuxed) return null;
    const support = await VideoDecoder.isConfigSupported(demuxed.config).catch(() => null);
    if (!support?.supported) return null;
    return new ClipReader(demuxed);
  }

  constructor({ config, samples, start, duration, width, height }) {
    this.config = config;
    this.samples = samples;
    this.start = start; // presentation time (µs) of the first frame
    this.duration = duration; // seconds
    this.w = width;
    this.h = height;
    this.frames = []; // decoded frames waiting to be shown, oldest first
    this.lastTarget = -1;
    this.waiters = [];
    this.broken = null;
    this.reset();
  }

  reset() {
    try { this.decoder?.close(); } catch {}
    for (const f of this.frames) f.close();
    this.frames = [];
    this.next = 0;
    this.flushed = false;
    this.decoder = new VideoDecoder({
      output: (frame) => {
        this.frames.push(frame);
        this.wake();
      },
      error: (err) => {
        this.broken = err;
        this.wake();
      },
    });
    this.decoder.configure(this.config);
  }

  wake() {
    const w = this.waiters;
    this.waiters = [];
    for (const r of w) r();
  }

  waitForDecoder() {
    return new Promise((resolve) => {
      this.waiters.push(resolve);
      setTimeout(resolve, 20); // also re-check regularly, in case no frame comes out
    });
  }

  /** The frame to show at `seconds` into the scene (the clip loops). */
  async frameAt(seconds) {
    if (this.broken) throw this.broken;
    const local = this.duration > 0 ? seconds % this.duration : 0;
    const target = this.start + local * 1e6;
    if (target < this.lastTarget) this.reset(); // looped back to the start
    this.lastTarget = target;

    for (let guard = 0; guard < 100000; guard++) {
      if (this.broken) throw this.broken;
      // Drop frames that are fully in the past.
      while (this.frames.length > 1 && this.frames[1].timestamp <= target) this.frames.shift().close();
      const newest = this.frames[this.frames.length - 1];
      if (newest && (newest.timestamp >= target || this.flushed)) return this.frames[0];
      if (this.flushed) return this.frames[0] || null;

      if (this.next < this.samples.length) {
        // Feed a few frames at a time, then wait for the decoder.
        for (let k = 0; k < 4 && this.next < this.samples.length; k++) this.decoder.decode(this.samples[this.next++]);
        if (this.decoder.decodeQueueSize > 2 || this.frames.length === 0) await this.waitForDecoder();
      } else {
        await this.decoder.flush().catch((err) => (this.broken = err));
        this.flushed = true;
      }
    }
    throw new Error('Clip decoder made no progress.');
  }

  close() {
    for (const f of this.frames) f.close();
    this.frames = [];
    try { this.decoder?.close(); } catch {}
    this.decoder = null;
  }
}

/** Reads the first video track of an MP4 into WebCodecs chunks and a decoder config. */
function demux(arrayBuffer) {
  const file = createFile();
  let track = null;
  let failed = false;
  const raw = [];
  file.onSamples = (_id, _user, samples) => raw.push(...samples);
  // Samples are only handed out when extraction starts from inside onReady.
  file.onReady = (info) => {
    track = info.videoTracks?.[0] || null;
    if (!track) return;
    file.setExtractionOptions(track.id, null, { nbSamples: 1e6 });
    file.start();
  };
  file.onError = () => (failed = true);
  file.appendBuffer(MP4BoxBuffer.fromArrayBuffer(arrayBuffer.slice(0), 0));
  file.flush();
  if (failed || !track || !raw.length) return null;

  const description = codecDescription(file.getTrackById(track.id));
  const config = {
    codec: track.codec.startsWith('vp08') ? 'vp8' : track.codec,
    codedWidth: track.video?.width || track.track_width,
    codedHeight: track.video?.height || track.track_height,
    ...(description ? { description } : {}),
  };
  const samples = raw.map((s) => new EncodedVideoChunk({
    type: s.is_sync ? 'key' : 'delta',
    timestamp: Math.round((1e6 * s.cts) / s.timescale),
    duration: Math.round((1e6 * s.duration) / s.timescale),
    data: s.data,
  }));
  if (samples[0].type !== 'key') return null;
  const times = raw.map((s) => s.cts / s.timescale);
  const ends = raw.map((s) => (s.cts + s.duration) / s.timescale);
  const startS = Math.min(...times);
  return {
    config,
    samples,
    start: Math.round(startS * 1e6),
    duration: Math.max(...ends) - startS,
    width: config.codedWidth,
    height: config.codedHeight,
  };
}

function codecDescription(trak) {
  for (const entry of trak?.mdia?.minf?.stbl?.stsd?.entries || []) {
    const box = entry.avcC || entry.hvcC || entry.vpcC || entry.av1C;
    if (box) {
      const stream = new DataStream(undefined, 0, Endianness.BIG_ENDIAN);
      box.write(stream);
      return new Uint8Array(stream.buffer, 8); // skip the box header
    }
  }
  return null;
}
