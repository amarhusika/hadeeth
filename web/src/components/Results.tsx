import type { Collection } from '../types';
import type { Result } from '../lib/search';
import HadithCard from './HadithCard';
import { toArabicDigits } from '../lib/format';

export default function Results({ q, results, cols, scope }: {
  q: string; results: Result[]; cols: Record<string, Collection>; scope?: string;
}) {
  const full = results.filter((r) => !r.partial).length;
  const per = Object.keys(cols).map((k) => [k, results.filter((r) => r.item.h.c === k).length] as const);
  return (
    <section className="results">
      <div className="res-head">
        <p>
          {results.length
            ? <>عُثِر على <b>{toArabicDigits(full || results.length)}</b> حديثًا لـ «{q}»{scope ? ` في ${scope}` : ''}</>
            : <>لا نتائج لـ «{q}»{scope ? ` في ${scope}` : ''}</>}
        </p>
        {!scope && results.length > 0 && (
          <p className="per">{per.filter(([, n]) => n).map(([k, n]) => `${cols[k].short}: ${toArabicDigits(n)}`).join(' · ')}</p>
        )}
      </div>
      {!results.length && <p className="empty">البحث يطابق النص حرفيًّا وبنفس ترتيب الكلمات (دون اعتبار الحركات).</p>}
      <div className="list">
        {results.slice(0, 150).map((r) => (
          <HadithCard key={r.item.h.c + r.item.h.n} h={r.item.h} col={cols[r.item.h.c]} marks={r.marks} partial={r.partial} />
        ))}
      </div>
      {results.length > 150 && <p className="empty">عُرضت أول ١٥٠ نتيجة — زِد كلمات البحث لتضييق النتائج.</p>}
    </section>
  );
}
