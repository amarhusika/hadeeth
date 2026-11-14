import { useCallback, useMemo, useState } from 'react';
import type { Collection, Hadith } from '../types';
import type { Marks } from '../lib/search';
import RichText from './RichText';
import FootnotePopup from './FootnotePopup';
import Link from './Link';
import { toArabicDigits, takhrijLabel } from '../lib/format';
import { hadithPath } from '../lib/router';
import { hasVisible, maskHidden } from '../lib/notes';

interface Props {
  h: Hadith;
  col?: Collection;
  marks?: Marks;
  partial?: boolean;
  /** na stranici hadisa: bez veze "فتح" */
  standalone?: boolean;
}

export default function HadithCard({ h, col, marks = {}, partial, standalone }: Props) {
  const [copied, setCopied] = useState(false);
  const [fn, setFn] = useState<{ n: number; el: HTMLElement } | null>(null);
  const openFn = useCallback((n: number, el: HTMLElement) => setFn({ n, el }), []);
  const closeFn = useCallback(() => setFn(null), []);
  // fusnote koje su samo tahridž se ne prikazuju; ostale se numerišu redom
  const shownNo = useMemo(() => {
    let k = 0;
    return h.footnotes.map((f) => (hasVisible(f) ? ++k : 0));
  }, [h]);
  const refMap = useCallback((n: number) => shownNo[n - 1] ?? 0, [shownNo]);
  const id = `${h.c}-${h.n}`;
  const path = hadithPath(h.c, h.n);

  const copy = async () => {
    const strip = (s: string) => s.replace(/\{(\d+)\}/g, (_, n) => (shownNo[n - 1] ? `(${toArabicDigits(shownNo[n - 1])})` : ''));
    const clean = (f: string) => maskHidden(f).replace(/[ \t]+/g, ' ').replace(/ *\n */g, '\n').trim();
    const fns = h.footnotes.map((f, i) => (shownNo[i] ? `(${toArabicDigits(shownNo[i])}) ${clean(f)}` : '')).filter(Boolean).join('\n');
    const txt = `${h.bab}\n${toArabicDigits(h.n)} - ${strip(h.text)}\n${fns ? '────────\n' + fns + '\n' : ''}[${col?.title ?? ''}، ${toArabicDigits(h.n)}]`;
    try { await navigator.clipboard.writeText(txt); setCopied(true); setTimeout(() => setCopied(false), 1600); } catch { /* */ }
  };

  return (
    <article className="card" id={id}>
      <header className="card-head">
        <Link className="num" href={path} title="فتح صفحة الحديث">{toArabicDigits(h.n)}</Link>
        <div className="crumbs">
          <span className="kitab">{h.kitab}</span>
          <RichText as="span" className="bab" text={h.bab} ranges={marks.bab} />
        </div>
        {partial && <span className="tag soft">تطابق جزئي</span>}
      </header>

      <RichText text={h.text} ranges={marks.text} className="matn" idPrefix={`fn-${id}`} onRef={openFn} refMap={refMap} />

      {fn && h.footnotes[fn.n - 1] !== undefined && (
        <FootnotePopup n={shownNo[fn.n - 1]} text={h.footnotes[fn.n - 1]} ranges={marks['fn' + (fn.n - 1)]} anchor={fn.el} onClose={closeFn} />
      )}

      {shownNo.some(Boolean) && (
        <ol className="footnotes">
          {h.footnotes.map((f, i) => shownNo[i] > 0 && (
            <li key={i} id={`fn-${id}-${i + 1}`}>
              <a className="fn-num" href={`#fn-${id}-ref-${i + 1}`}>({toArabicDigits(shownNo[i])})</a>
              <RichText text={f} ranges={marks['fn' + i]} notes />
            </li>
          ))}
        </ol>
      )}

      <footer className="card-foot">
        <div className="badges">
          {h.takhrij.length > 0 && <span className="tag">{takhrijLabel(h.takhrij)}</span>}
          <span className="src">{col?.short} · ج{toArabicDigits(h.volume)} · ص{toArabicDigits(h.page)}</span>
        </div>
        <div className="actions">
          <button className="ghost" onClick={copy}>{copied ? 'نُسِخ ✓' : 'نسخ'}</button>
          {!standalone && <Link className="ghost primary" href={path}>صفحة الحديث ←</Link>}
        </div>
      </footer>
    </article>
  );
}
