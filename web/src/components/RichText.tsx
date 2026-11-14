import { useMemo, type ReactNode } from 'react';
import { extendOverMarks } from '../lib/arabic';
import { toArabicDigits } from '../lib/format';
import { metaRanges, hiddenRanges } from '../lib/notes';

const HL = 1, QURAN = 2, BUK = 4, SAW = 8, META = 16, HIDE = 32;

// u tahridžu se ne prikazuju brojevi hadisa/tomova: «(٤٨٠٧)»، «(٧/ ٥٢٨)»، «٢٠: (٨٣٨)»
const REF_NUM = /\s*(?:[٠-٩0-9]+\s*:\s*)?\([٠-٩0-9\s/\-–،,:]+\)/g;

function flagsFor(src: string, ranges?: [number, number][], notes?: boolean): Uint8Array {
  const f = new Uint8Array(src.length);
  // fusnota: ko bilježi / tahridž (meta) odvojeno od metna
  if (notes) {
    for (const [a, b] of metaRanges(src)) {
      for (let i = a; i < b; i++) f[i] |= META;
      for (const m of src.slice(a, b).matchAll(REF_NUM))
        for (let i = a + m.index!; i < a + m.index! + m[0].length; i++) f[i] |= HIDE;
    }
    // tahridž i ocjene se ne prikazuju (aplikacija je za hifz)
    for (const [a, b] of hiddenRanges(src)) for (let i = a; i < b; i++) f[i] |= HIDE;
  }
  if (ranges?.length) {
    for (const [a, b] of extendOverMarks(src, ranges.map((r) => [...r] as [number, number])))
      for (let i = a; i < b; i++) f[i] |= HL;
  }
  let q = 0, p = 0;
  for (let i = 0; i < src.length; i++) {
    const c = src[i];
    if (c === '﴿') q++;
    if (c === '(') p++;
    if (q) f[i] |= QURAN;
    if (p) f[i] |= BUK;
    if (c === 'ﷺ') f[i] |= SAW;
    if (c === '﴾') q = Math.max(0, q - 1);
    if (c === ')') p = Math.max(0, p - 1);
  }
  return f;
}

/** {3} → (٣) kao eksponent s vezom na fusnotu */
export type OnRef = (n: number, el: HTMLElement) => void;

export type RefMap = (n: number) => number;

function withRefs(s: string, idPrefix: string | undefined, key: string, onRef?: OnRef, refMap?: RefMap): ReactNode[] {
  const parts = s.split(/(\{\d+\})/);
  return parts.map((part, i) => {
    const m = part.match(/^\{(\d+)\}$/);
    if (!m) return part;
    const n = Number(m[1]);
    const shown = refMap ? refMap(n) : n;
    if (!shown) return null;
    return (
      <sup key={key + 'r' + i} className="fnref">
        {idPrefix ? <a href={`#${idPrefix}-${n}`} id={`${idPrefix}-ref-${n}`} role={onRef ? 'button' : undefined}
          onClick={onRef ? (e) => { e.preventDefault(); onRef(n, e.currentTarget); } : undefined}>({toArabicDigits(shown)})</a> : `(${toArabicDigits(shown)})`}
      </sup>
    );
  });
}

function renderLine(src: string, flags: Uint8Array, offset: number, key: string, idPrefix?: string, onRef?: OnRef, refMap?: RefMap): ReactNode[] {
  const out: ReactNode[] = [];
  let start = 0;
  for (let i = 1; i <= src.length; i++) {
    if (i === src.length || flags[offset + i] !== flags[offset + start]) {
      const k = `${key}-${start}`;
      const fl = flags[offset + start];
      if (fl & HIDE) { start = i; continue; }
      let node: ReactNode = withRefs(src.slice(start, i), idPrefix, k, onRef, refMap);
      if (fl & SAW) node = <span className="saw">{node}</span>;
      if (fl & HL) node = <mark>{node}</mark>;
      if (fl & QURAN) node = <span className="quran">{node}</span>;
      else if (fl & META) node = <span className="meta">{node}</span>;
      else if (fl & BUK) node = <span className="buk">{node}</span>;
      out.push(<span key={k}>{node}</span>);
      start = i;
    }
  }
  return out;
}

export default function RichText({ text, ranges, className = '', idPrefix, as = 'div', onRef, notes, refMap }: {
  text: string; ranges?: [number, number][]; className?: string; idPrefix?: string; as?: 'div' | 'span'; onRef?: OnRef;
  /** tekst fusnote: oboji ko bilježi i tahridž */
  notes?: boolean;
  /** broj fusnote za prikaz (0 = ne prikazuj oznaku) */
  refMap?: RefMap;
}) {
  const content = useMemo(() => {
    const flags = flagsFor(text, ranges, notes);
    let off = 0;
    return text.split('\n').map((line, li) => {
      const node = renderLine(line, flags, off, String(li), idPrefix, onRef, refMap);
      off += line.length + 1;
      if (as === 'span') return <span key={li}>{node}</span>;
      return <p key={li} className={line.trimStart().startsWith('•') ? 'bullet' : undefined}>{node}</p>;
    });
  }, [text, ranges, idPrefix, as, onRef, notes, refMap]);
  const Tag = as;
  return <Tag className={`rich ${className}`}>{content}</Tag>;
}
