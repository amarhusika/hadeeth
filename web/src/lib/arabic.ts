// Normalizacija arapskog teksta za pretragu bez harakata.
// Svaki znak originala mapira se na poziciju u normaliziranom tekstu,
// kako bi se pogoci mogli istaći u vokaliziranom (originalnom) tekstu.

const DIACRITICS = /[ؐ-ًؚ-ٰٟۖ-ۭـ]/;

const MAP: Record<string, string> = {
  'أ': 'ا', 'إ': 'ا', 'آ': 'ا', 'ٱ': 'ا', 'ٲ': 'ا', 'ٳ': 'ا',
  // sva sjedišta hemze → elif (رءوس = رؤوس، يتساءلون = يتسائلون)
  'ء': 'ا', 'ئ': 'ا', 'ؤ': 'ا',
  'ى': 'ي', 'ی': 'ي',
  'ة': 'ه',
  'ک': 'ك',
};

// ﷺ i slične ligature razvijaju se u puni tekst
const EXPAND: Record<string, string> = {
  'ﷺ': 'صلي الله عليه وسلم',
  'ﷻ': 'جل جلاله',
};

const KEEP = /[ء-ي0-9a-z٠-٩]/;

export interface Normalized {
  text: string;
  /** za svaki znak u `text` — indeks znaka u originalu */
  map: number[];
}

export function normalizeWithMap(src: string): Normalized {
  let text = '';
  const map: number[] = [];
  let lastSpace = true;
  for (let i = 0; i < src.length; i++) {
    let ch = src[i];
    // oznaka fusnote {12} — preskače se u pretrazi
    if (ch === '{') {
      const j = src.indexOf('}', i);
      if (j > i && /^\{\d+\}$/.test(src.slice(i, j + 1))) { i = j; continue; }
    }
    if (DIACRITICS.test(ch)) continue;
    if (EXPAND[ch]) {
      if (!lastSpace) { text += ' '; map.push(i); }
      for (const c of EXPAND[ch]) { text += c; map.push(i); }
      lastSpace = false;
      continue;
    }
    ch = MAP[ch] ?? ch.toLowerCase();
    if (KEEP.test(ch)) {
      if (ch === 'ا' && text.endsWith('ا')) continue; // اا → ا
      text += ch; map.push(i); lastSpace = false;
    } else if (!lastSpace) {
      text += ' '; map.push(i); lastSpace = true;
    }
  }
  return { text, map };
}

export function normalize(src: string): string {
  return normalizeWithMap(src).text.trim();
}

/** Pronalazi raspone [start,end) u originalnom tekstu koji odgovaraju terminima. */
export function findRanges(norm: Normalized, terms: string[]): [number, number][] {
  const ranges: [number, number][] = [];
  for (const t of terms) {
    if (!t) continue;
    let from = 0;
    for (;;) {
      const idx = norm.text.indexOf(t, from);
      if (idx < 0) break;
      const start = norm.map[idx];
      const endNorm = idx + t.length - 1;
      const end = norm.map[endNorm] + 1;
      ranges.push([start, end]);
      from = idx + t.length;
    }
  }
  ranges.sort((a, b) => a[0] - b[0]);
  const merged: [number, number][] = [];
  for (const r of ranges) {
    const last = merged[merged.length - 1];
    if (last && r[0] <= last[1]) last[1] = Math.max(last[1], r[1]);
    else merged.push([...r]);
  }
  return merged;
}

/** Proširi kraj raspona preko harakata koji slijede. */
export function extendOverMarks(src: string, ranges: [number, number][]) {
  for (const r of ranges) {
    while (r[1] < src.length && DIACRITICS.test(src[r[1]])) r[1]++;
  }
  return ranges;
}
