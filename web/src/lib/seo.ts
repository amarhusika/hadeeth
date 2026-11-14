// SEO po stranici: naslov, opis, canonical i og/twitter oznake (bez harakata).
export const SITE = 'https://hadeeth.pages.dev';
export const TITLE = 'حفظ السنة — الشيخ يحيى بن عبد العزيز اليحيى';
export const DESC = 'محصل دواوين السنة الثمانية عشر: زيادات السنن والمسانيد والصحاح والمعاجم للحفاظ';

export const plain = (s: string) =>
  s.replace(/\{\d+\}/g, '').replace(/[ؐ-ًؚ-ٰٟۖ-ۭـ]/g, '').replace(/\s+/g, ' ').trim();

function meta(attr: 'name' | 'property', key: string, value: string) {
  let el = document.head.querySelector<HTMLMetaElement>(`meta[${attr}="${key}"]`);
  if (!el) { el = document.createElement('meta'); el.setAttribute(attr, key); document.head.appendChild(el); }
  el.content = value;
}

export function setSeo({ title = TITLE, desc = DESC, path = '/', index = true }: {
  title?: string; desc?: string; path?: string; index?: boolean;
}) {
  const url = SITE + path;
  document.title = title;
  meta('name', 'description', desc);
  meta('name', 'robots', index ? 'index, follow' : 'noindex, follow');
  meta('property', 'og:title', title);
  meta('property', 'og:description', desc);
  meta('property', 'og:url', url);
  meta('name', 'twitter:title', title);
  meta('name', 'twitter:description', desc);
  let link = document.head.querySelector<HTMLLinkElement>('link[rel="canonical"]');
  if (!link) { link = document.createElement('link'); link.rel = 'canonical'; document.head.appendChild(link); }
  link.href = url;
}
