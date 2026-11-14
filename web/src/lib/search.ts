import { normalize, normalizeWithMap, findRanges, type Normalized } from './arabic';
import type { Hadith } from '../types';
import { maskHidden } from './notes';

/*
 * Pretraga na nivou riječi, u glavnom tekstu, fusnotama i naslovu baba.
 *  - normalizacija: bez harakata, ujednačena hemza/elif/ja/ta marbuta
 *  - riječ se poredi i nakon skidanja proklitika (و ف ب ل ال…) i enklitika (ه ها هم…)
 *  - tolerancija na tipfelere: jedno slovo viška/manjka/pogrešno (الإمان → الإيمان)
 *  - rangiranje: pokrivenost, tačna fraza, blizina riječi, mjesto pogotka
 */

export interface Word { w: string; s: string; start: number; end: number }
export interface Field { key: string; norm: Normalized; words: Word[] }
export interface Indexed { h: Hadith; fields: Field[] }

export type Marks = Record<string, [number, number][]>;
export type Tier = 'exact' | 'words' | 'fuzzy';
export interface Result { item: Indexed; score: number; marks: Marks; partial: boolean; tier: Tier }

const STOP = new Set(['علي', 'من', 'في', 'عن', 'الي', 'الا', 'ان', 'لا', 'ما', 'ثم', 'قال', 'قالت', 'او', 'يا', 'لم', 'لن', 'قد', 'هو', 'هي', 'ذلك', 'هذا', 'الله', 'رسول', 'صلي', 'عليه', 'وسلم', 'رضي', 'عنه', 'عنها', 'عنهما', 'النبي', 'كان', 'اذا', 'به', 'له', 'لها', 'بن', 'ابي', 'ابو']);

const PRE = ['وبال', 'فبال', 'وكال', 'وال', 'فال', 'بال', 'كال', 'ولل', 'فلل', 'لل', 'ال', 'وب', 'فب', 'ول', 'فل', 'و', 'ف', 'ب', 'ل', 'ك'];
const SUF = ['كموها', 'تموه', 'هما', 'كما', 'ها', 'هم', 'هن', 'كم', 'كن', 'نا', 'ني', 'ه', 'ك', 'ي'];

export function stem(w: string): string {
  let s = w;
  for (const p of PRE) if (s.startsWith(p) && s.length - p.length >= 3) { s = s.slice(p.length); break; }
  for (const x of SUF) if (s.endsWith(x) && s.length - x.length >= 3) { s = s.slice(0, -x.length); break; }
  return s;
}

function tokenize(norm: string): Word[] {
  const out: Word[] = [];
  const re = /\S+/g;
  let m: RegExpExecArray | null;
  while ((m = re.exec(norm))) out.push({ w: m[0], s: stem(m[0]), start: m.index, end: m.index + m[0].length });
  return out;
}

/** Levenshtein s ranim prekidom kad udaljenost pređe max */
function lev(a: string, b: string, max: number): number {
  if (Math.abs(a.length - b.length) > max) return max + 1;
  let prev = Array.from({ length: b.length + 1 }, (_, i) => i);
  for (let i = 1; i <= a.length; i++) {
    const cur = [i];
    let rowMin = i;
    for (let j = 1; j <= b.length; j++) {
      cur[j] = Math.min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (a[i - 1] === b[j - 1] ? 0 : 1));
      rowMin = Math.min(rowMin, cur[j]);
    }
    if (rowMin > max) return max + 1;
    prev = cur;
  }
  return prev[b.length];
}

/** kvalitet podudaranja riječi upita q s riječju teksta d (0 = nema) */
function wordMatch(q: Word, d: Word): number {
  if (q.w === d.w) return 3;
  if (q.s === d.s || q.s === d.w || q.w === d.s) return 2.5;
  if (q.s.length >= 3 && d.s.startsWith(q.s)) return 1.6;
  if (q.s.length >= 4 && d.w.includes(q.s)) return 1.3;
  if (q.s.length >= 4) {
    const max = q.s.length >= 7 ? 2 : 1;
    const dist = Math.min(lev(q.s, d.s, max), lev(q.w, d.w, max));
    if (dist <= max) return dist === 1 ? 1.2 : 0.8;
  }
  return 0;
}

function field(key: string, src: string): Field {
  const norm = normalizeWithMap(src);
  return { key, norm, words: tokenize(norm.text) };
}

export function buildIndex(hadiths: Hadith[]): Indexed[] {
  return hadiths.map((h) => ({
    h,
    fields: [field('text', h.text), field('bab', h.bab), ...h.footnotes.map((f, i) => field('fn' + i, maskHidden(f)))],
  }));
}

interface Hit { qi: number; pos: number; q: number }

function bestWindow(hits: Hit[], need: number[]) {
  const needSet = new Set(need);
  const hs = hits.filter((h) => needSet.has(h.qi)).sort((a, b) => a.pos - b.pos);
  const cnt = new Map<number, number>();
  let have = 0, l = 0;
  let best: { span: number; from: number; to: number; quality: number } | null = null;
  for (let r = 0; r < hs.length; r++) {
    const c = cnt.get(hs[r].qi) ?? 0;
    if (c === 0) have++;
    cnt.set(hs[r].qi, c + 1);
    while (have === needSet.size) {
      const span = hs[r].pos - hs[l].pos;
      if (!best || span < best.span) {
        let quality = 0;
        for (let i = l; i <= r; i++) quality += hs[i].q;
        best = { span, from: hs[l].pos, to: hs[r].pos, quality: quality / (r - l + 1) };
      }
      const lc = cnt.get(hs[l].qi)! - 1;
      cnt.set(hs[l].qi, lc);
      if (lc === 0) have--;
      l++;
    }
  }
  return best;
}

/** da li se sve riječi upita pojavljuju uzastopno i istim redom */
function isPhrase(hits: Hit[], n: number): boolean {
  const at = new Set(hits.map((h) => h.qi + ':' + h.pos));
  for (const h of hits) {
    if (h.qi !== 0) continue;
    let ok = true;
    for (let i = 1; i < n && ok; i++) ok = at.has(i + ':' + (h.pos + i));
    if (ok) return true;
  }
  return false;
}

/** tačna fraza (cijele riječi, istim redom) u nekom polju */
function exactSearch(index: Indexed[], qn: string, collection?: string): Result[] {
  const out: Result[] = [];
  for (const item of index) {
    if (collection && item.h.c !== collection) continue;
    const marks: Marks = {};
    let score = 0;
    for (const f of item.fields) {
      const t = ' ' + f.norm.text + ' ';
      if (!t.includes(' ' + qn + ' ')) continue;
      const rs = findRanges(f.norm, [qn]).filter(([a]) => {
        const i = f.norm.map.indexOf(a);
        return i === 0 || f.norm.text[i - 1] === ' ';
      });
      if (!rs.length) continue;
      marks[f.key] = rs;
      score = Math.max(score, f.key === 'text' ? 3 : f.key === 'bab' ? 2 : 1);
    }
    if (score) out.push({ item, score, marks, partial: false, tier: 'exact' });
  }
  return out.sort((a, b) => b.score - a.score || a.item.h.n - b.item.h.n);
}

/** Pretraga vraća samo hadise u kojima se upisani tekst nalazi doslovno (bez obzira na harakate). */
export function search(index: Indexed[], query: string, collection?: string): Result[] {
  const qn = normalize(query);
  if (!qn) return [];
  // samo identično poklapanje: iste riječi, istim redom (harakati se zanemaruju)
  return exactSearch(index, qn, collection);
}

export function searchLoose(index: Indexed[], qn: string, collection: string | undefined, strict: boolean): Result[] {
  const qw = tokenize(qn);
  const content = qw.map((w, i) => (STOP.has(w.w) ? -1 : i)).filter((i) => i >= 0);
  const required = content.length ? content : qw.map((_, i) => i);

  // keš: riječ teksta → kvaliteti za svaku riječ upita
  const cache = new Map<string, number[]>();
  const qualities = (d: Word) => {
    let r = cache.get(d.w);
    if (!r) { r = qw.map((q) => { const m = wordMatch(q, d); return strict && m < 2.5 ? 0 : m; }); cache.set(d.w, r); }
    return r;
  };

  const results: Result[] = [];
  for (const item of index) {
    if (collection && item.h.c !== collection) continue;

    const perField = item.fields.map((f) => {
      const hits: Hit[] = [];
      f.words.forEach((d, pos) => qualities(d).forEach((q, qi) => { if (q) hits.push({ qi, pos, q }); }));
      return { f, hits, found: new Set(hits.map((h) => h.qi)) };
    });
    const covered = required.filter((i) => perField.some((p) => p.found.has(i)));
    const coverage = covered.length / required.length;
    if (coverage < 1) continue;

    let score = coverage * 100;
    const marks: Marks = {};
    let bestFieldScore = 0;

    for (const p of perField) {
      if (!p.hits.length) continue;
      const weight = p.f.key === 'text' ? 1 : p.f.key === 'bab' ? 0.9 : 0.75;
      const mark = new Set<number>();
      const inHere = required.filter((i) => p.found.has(i));
      if (inHere.length === required.length) {
        const allIdx = qw.map((_, i) => i).filter((i) => p.found.has(i));
        const win = bestWindow(p.hits, allIdx) ?? bestWindow(p.hits, required)!;
        let fs = 120 + Math.max(0, 80 - win.span * 6) + win.quality * 15;
        if (qw.length > 1 && isPhrase(p.hits, qw.length)) fs += 160; // riječi uzastopno i redom
        bestFieldScore = Math.max(bestFieldScore, fs * weight);
        for (const h of p.hits) if (h.pos >= win.from && h.pos <= win.to) mark.add(h.pos);
      } else {
        bestFieldScore = Math.max(bestFieldScore, inHere.length * 15 * weight);
      }
      for (const h of p.hits) if (!STOP.has(qw[h.qi].w) && h.q >= 1.2) mark.add(h.pos);
      if (mark.size) {
        marks[p.f.key] = [...mark].sort((a, b) => a - b).map((pos) => {
          const w = p.f.words[pos];
          return [p.f.norm.map[w.start], p.f.norm.map[w.end - 1] + 1] as [number, number];
        });
      }
    }
    score += bestFieldScore;
    results.push({ item, score, marks, partial: coverage < 1, tier: strict ? 'words' : 'fuzzy' });
  }
  results.sort((a, b) => Number(a.partial) - Number(b.partial) || b.score - a.score || a.item.h.n - b.item.h.n);
  return results;
}
