import { useEffect, useState } from 'react';

export type Route =
  | { view: 'home'; q: string }
  | { view: 'collection'; c: string; q: string }
  | { view: 'hadith'; c: string; n: number }
  | { view: 'notfound' };

export const COLLECTION_KEYS = ['sahihayn', 'zawaid'];

export function parse(): Route {
  const path = decodeURIComponent(location.pathname).replace(/\/+$/, '') || '/';
  const q = new URLSearchParams(location.search).get('q') ?? '';
  if (path === '/') return { view: 'home', q };
  const m = path.match(/^\/(sahihayn|zawaid)(?::(\d+))?$/);
  if (!m) return { view: 'notfound' };
  if (m[2]) return { view: 'hadith', c: m[1], n: Number(m[2]) };
  return { view: 'collection', c: m[1], q };
}

export function navigate(to: string) {
  if (to === location.pathname + location.search) return;
  history.pushState(null, '', to);
  window.dispatchEvent(new Event('popstate'));
  window.scrollTo({ top: 0 });
}

export const hadithPath = (c: string, n: number) => `/${c}:${n}`;
export const withQuery = (base: string, q: string) => (q ? `${base}?q=${encodeURIComponent(q)}` : base);

export function useRoute(): Route {
  const [r, setR] = useState<Route>(parse);
  useEffect(() => {
    const f = () => setR(parse());
    window.addEventListener('popstate', f);
    return () => window.removeEventListener('popstate', f);
  }, []);
  return r;
}

/** presreće klik na <a href="/…"> i koristi pushState */
export function onLinkClick(e: React.MouseEvent<HTMLAnchorElement>) {
  if (e.defaultPrevented || e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;
  const href = e.currentTarget.getAttribute('href');
  if (!href || !href.startsWith('/')) return;
  e.preventDefault();
  navigate(href);
}
