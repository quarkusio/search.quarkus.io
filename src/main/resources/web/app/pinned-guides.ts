/**
 * Guides pinned by the (anonymous) visitor, kept in the browser's localStorage.
 *
 * Only a key per guide is stored. Quarkus guides are keyed by their path after `/guides/`,
 * so a pin follows the visitor across documentation versions; other guides are keyed by their full URL.
 */

const STORAGE_KEY = 'qs-pinned-guides';

export const QS_PINS_CHANGED_EVENT = 'qs-pins-changed';

export function pinKey(url: string): string {
  if (!url) {
    return '';
  }
  let path = url;
  try {
    const parsed = new URL(url, window.location.origin);
    const quarkusHost = parsed.hostname === 'quarkus.io' || parsed.hostname.endsWith('.quarkus.io');
    if (parsed.origin !== window.location.origin && !quarkusHost) {
      return url;
    }
    path = parsed.pathname;
  } catch (e) {
    // Not a parsable URL, use it as is
  }
  const match = path.match(/\/guides\/([^?#]+?)\/?$/);
  return match ? `guides/${match[1]}` : url;
}

export function getPinnedKeys(): string[] {
  try {
    const value = JSON.parse(window.localStorage.getItem(STORAGE_KEY) || '[]');
    return Array.isArray(value) ? value.filter(k => typeof k === 'string') : [];
  } catch (e) {
    // Storage blocked (e.g. privacy mode) or corrupted: behave as if nothing is pinned
    return [];
  }
}

export function isPinned(url: string): boolean {
  const key = pinKey(url);
  return !!key && getPinnedKeys().includes(key);
}

export function togglePinned(url: string): boolean {
  const key = pinKey(url);
  if (!key) {
    return false;
  }
  const keys = getPinnedKeys();
  const pinned = !keys.includes(key);
  const updated = pinned ? [...keys, key] : keys.filter(k => k !== key);
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(updated));
  } catch (e) {
    console.warn('Unable to store pinned guides', e);
    return !pinned;
  }
  window.dispatchEvent(new CustomEvent(QS_PINS_CHANGED_EVENT));
  return pinned;
}

/**
 * Calls the listener when pins change, in this tab or in another one.
 * Returns a function removing the listener.
 */
export function onPinsChanged(listener: () => void): () => void {
  const onStorage = (e: StorageEvent) => {
    if (e.key === STORAGE_KEY || e.key === null) {
      listener();
    }
  };
  window.addEventListener(QS_PINS_CHANGED_EVENT, listener);
  window.addEventListener('storage', onStorage);
  return () => {
    window.removeEventListener(QS_PINS_CHANGED_EVENT, listener);
    window.removeEventListener('storage', onStorage);
  };
}
