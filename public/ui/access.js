// Access token bootstrap. Runs before any network call.
// Also polyfills Web APIs gated on [SecureContext] (such as crypto.randomUUID) when served over HTTP LAN.
(function ensureInsecureContextPolyfills() {
  const g = typeof globalThis !== 'undefined' ? globalThis : (typeof window !== 'undefined' ? window : {});
  const generateUUIDv4 = () => {
    const bytes = new Uint8Array(16);
    if (g.crypto && typeof g.crypto.getRandomValues === 'function') {
      g.crypto.getRandomValues(bytes);
    } else {
      for (let i = 0; i < 16; i++) bytes[i] = Math.floor(Math.random() * 256);
    }
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    const hex = Array.from(bytes, b => b.toString(16).padStart(2, '0'));
    return `${hex[0]}${hex[1]}${hex[2]}${hex[3]}-${hex[4]}${hex[5]}-${hex[6]}${hex[7]}-${hex[8]}${hex[9]}-${hex[10]}${hex[11]}${hex[12]}${hex[13]}${hex[14]}${hex[15]}`;
  };
  try {
    if (typeof Crypto !== 'undefined' && Crypto.prototype && typeof Crypto.prototype.randomUUID !== 'function') {
      Object.defineProperty(Crypto.prototype, 'randomUUID', { value: generateUUIDv4, configurable: true, writable: true });
    }
  } catch {}
  try {
    if (!g.crypto) g.crypto = {};
    if (typeof g.crypto.randomUUID !== 'function') {
      Object.defineProperty(g.crypto, 'randomUUID', { value: generateUUIDv4, configurable: true, writable: true });
    }
  } catch {}
  try {
    if (typeof navigator !== 'undefined' && (!navigator.clipboard || typeof navigator.clipboard.writeText !== 'function')) {
      const writeText = async text => {
        if (typeof window !== 'undefined' && window.CodexLightNative?.copyToClipboard) {
          window.CodexLightNative.copyToClipboard(String(text ?? ''));
          return;
        }
        const ta = document.createElement('textarea');
        ta.value = String(text ?? '');
        ta.style.position = 'fixed';
        ta.style.opacity = '0';
        document.body.appendChild(ta);
        ta.select();
        try { document.execCommand('copy'); } finally { ta.remove(); }
      };
      if (!navigator.clipboard) {
        Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });
      } else {
        navigator.clipboard.writeText = writeText;
      }
    }
  } catch {}
})();

// Loopback (this computer): the fragment token is kept in sessionStorage so a reload keeps working.
// LAN (any other hostname, e.g. a phone): the token lives in memory only. Nothing is written to
// session/local storage, and the fragment is removed from the address bar immediately.
const LOOPBACK = new Set(['127.0.0.1', 'localhost', '[::1]', '::1']);
export const TOKEN_KEY = 'codex-light-token';

export function isLoopbackHost(hostname) { return LOOPBACK.has(String(hostname || '').toLowerCase()); }

/**
 * @param {{hostname:string, hash:string, pathname:string, search:string}} location
 * @param {Storage|null} storage  sessionStorage (may throw)
 * @param {(url:string)=>void} replaceUrl  history.replaceState wrapper
 * @returns {{token:string|null, lan:boolean, fromFragment:boolean}}
 */
export function bootstrapToken(location, storage, replaceUrl) {
  const lan = !isLoopbackHost(location.hostname);
  let nativeToken = null;
  try { nativeToken = (typeof window !== 'undefined' && window.CodexLightNative?.getToken) ? window.CodexLightNative.getToken() : null; } catch {}
  const fromHash = new URLSearchParams(String(location.hash || '').slice(1)).get('token') || nativeToken;
  // Clear the fragment first so the secret does not linger in the visible URL or history entry.
  if (location.hash) replaceUrl(location.pathname + location.search);
  if (lan) {
    // Remove any value a previous build may have stored; never read it back.
    try { storage?.removeItem(TOKEN_KEY); } catch {}
    return { token: fromHash || null, lan: true, fromFragment: !!fromHash };
  }
  let stored = null;
  try { stored = storage?.getItem(TOKEN_KEY) ?? null; } catch {}
  const token = fromHash || stored;
  if (fromHash) { try { storage?.setItem(TOKEN_KEY, fromHash); } catch {} }
  return { token, lan: false, fromFragment: !!fromHash };
}
