// Small additions for older browsers and Android WebViews (before Chrome 124).
// Must be imported before vendor/kokoro/kokoro.mjs: its pronunciation engine unpacks itself with
// `for await (const chunk of readableStream)`. Without async iteration that throws silently and
// the free voice waits forever.
if (typeof ReadableStream !== 'undefined' && !ReadableStream.prototype[Symbol.asyncIterator]) {
  const values = async function* values() {
    const reader = this.getReader();
    try {
      while (true) {
        const { done, value } = await reader.read();
        if (done) return;
        yield value;
      }
    } finally {
      reader.releaseLock();
    }
  };
  Object.defineProperty(ReadableStream.prototype, Symbol.asyncIterator, { value: values, writable: true, configurable: true });
  if (!ReadableStream.prototype.values) {
    Object.defineProperty(ReadableStream.prototype, 'values', { value: values, writable: true, configurable: true });
  }
}
