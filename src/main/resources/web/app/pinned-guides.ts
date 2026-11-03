import {Guide} from "./types";

/**
 * Shared state for pinning guides: the catalog of pinnable guides and the guides pinned by the (anonymous) visitor.
 *
 * The catalog is the browse catalog of the documentation version being viewed: the guides of the (server-rendered)
 * guides index, read once by the `pinnable` `qs-target`. Only guides of that catalog can be pinned, so the toggle
 * never shows on pages without one (e.g. Quarkiverse), and a pinned guide always has a card to show.
 *
 * The visitor's pins are read from the browser's localStorage once, then kept in memory;
 * a `storage` listener picks up the changes made in other tabs.
 * Guides are keyed by their path after `/guides/`, so a pin follows the visitor across documentation versions:
 * it shows in every version whose catalog has the guide.
 */

export type PinType = 'none' | 'curated' | 'user';

export interface PinnableGuide extends Guide {
  key: string;
  status?: string;
}

const STORAGE_KEY = 'qs-pinned-guides';

let catalog: Map<string, PinnableGuide> = new Map();
let curated: Set<string> = new Set();
let pinned: string[] | null = null;
const listeners = new Set<() => void>();

export function pinKey(url: string): string {
  if (!url) {
    return '';
  }
  let path = url;
  try {
    path = new URL(url, window.location.origin).pathname;
  } catch (e) {
    // Not a parsable URL, use it as is
  }
  const match = path.match(/\/guides\/([^?#]+?)\/?$/);
  return match ? `guides/${match[1]}` : url;
}

/**
 * Sets the catalog of pinnable guides from the guide cards of a (server-rendered) guides index:
 * cards of a `pinned` group are the site-curated pins, the others the guides that can be pinned.
 */
export function loadCatalog(index: Element) {
  catalog = new Map();
  curated = new Set();
  index.querySelectorAll('qs-guide-group qs-guide[url]').forEach(element => {
    const key = pinKey(element.getAttribute('url'));
    if (element.closest('qs-guide-group[pinned]')) {
      curated.add(key);
    } else if (!catalog.has(key)) {
      catalog.set(key, {
        key,
        url: element.getAttribute('url'),
        title: element.getAttribute('title'),
        summary: element.getAttribute('summary'),
        type: element.getAttribute('type'),
        status: element.getAttribute('status'),
        keywords: element.getAttribute('keywords'),
        categories: element.getAttribute('categories'),
        origin: element.getAttribute('origin'),
      });
    }
  });
  notify();
}

export function pinType(url: string): PinType | undefined {
  const key = pinKey(url);
  if (curated.has(key)) {
    return 'curated';
  }
  if (!catalog.has(key)) {
    // Not pinnable
    return undefined;
  }
  return pinnedKeys().includes(key) ? 'user' : 'none';
}

/** The guides pinned by the visitor that are in the catalog, in pinning order, without the site-curated ones */
export function userPinnedGuides(): PinnableGuide[] {
  return pinnedKeys()
      .filter(key => !curated.has(key))
      .map(key => catalog.get(key))
      .filter(guide => !!guide);
}

export function togglePinned(url: string) {
  const type = pinType(url);
  if (type !== 'none' && type !== 'user') {
    return;
  }
  const key = pinKey(url);
  const keys = pinnedKeys();
  const updated = keys.includes(key) ? keys.filter(k => k !== key) : [...keys, key];
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(updated));
  } catch (e) {
    console.warn('Unable to store pinned guides', e);
    return;
  }
  pinned = updated;
  notify();
}

/**
 * Calls the listener when the catalog or the pins change, in this tab or in another one.
 * Returns a function removing the listener.
 */
export function onPinsChanged(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

function pinnedKeys(): string[] {
  if (pinned === null) {
    pinned = readPinnedKeys();
  }
  return pinned;
}

function readPinnedKeys(): string[] {
  try {
    const value = JSON.parse(window.localStorage.getItem(STORAGE_KEY) || '[]');
    return Array.isArray(value) ? value.filter(k => typeof k === 'string') : [];
  } catch (e) {
    // Storage blocked (e.g. privacy mode) or corrupted: behave as if nothing is pinned
    return [];
  }
}

function notify() {
  listeners.forEach(listener => listener());
}

window.addEventListener('storage', (e: StorageEvent) => {
  if (e.key === STORAGE_KEY || e.key === null) {
    pinned = readPinnedKeys();
    notify();
  }
});
