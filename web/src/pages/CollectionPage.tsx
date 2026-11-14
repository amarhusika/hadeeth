import { useEffect, useMemo } from 'react';
import type { Data } from '../types';
import { search, type Indexed } from '../lib/search';
import { navigate, withQuery, hadithPath } from '../lib/router';
import { toArabicDigits } from '../lib/format';
import { setSeo, plain } from '../lib/seo';
import SearchBox from '../components/SearchBox';
import Results from '../components/Results';
import Link from '../components/Link';

export default function CollectionPage({ data, index, c, q }: { data: Data; index: Indexed[]; c: string; q: string }) {
  const col = data.collections[c];
  useEffect(() => {
    setSeo({
      title: `${plain(col.title)} | حفظ السنة`,
      desc: `${plain(col.title)} — ${col.author}. البحث في أحاديث ${col.short} بلا حركات، مع الحواشي كما في المطبوع.`,
      path: `/${c}`,
      index: !q,
    });
  }, [col, c, q]);
  const results = useMemo(() => (q ? search(index, q, c) : []), [index, q, c]);
  const tree = useMemo(() => {
    const kutub: { kitab: string; babs: { bab: string; nums: number[] }[] }[] = [];
    for (const h of data.hadiths) {
      if (h.c !== c) continue;
      let k = kutub[kutub.length - 1];
      if (!k || k.kitab !== h.kitab) { k = { kitab: h.kitab, babs: [] }; kutub.push(k); }
      let b = k.babs[k.babs.length - 1];
      if (!b || b.bab !== h.bab) { b = { bab: h.bab, nums: [] }; k.babs.push(b); }
      b.nums.push(h.n);
    }
    return kutub;
  }, [data, c]);

  return (
    <main className="page">
      <header className="coll-head">
        <nav className="crumb"><Link href="/">الرئيسة</Link> ‹ <span>{col.short}</span></nav>
        <h1>{col.title}</h1>
        <p className="muted">{col.author} · {col.count >= col.total ? `${toArabicDigits(col.total)} حديثًا` : `${toArabicDigits(col.count)} من ${toArabicDigits(col.total)} حديثًا مُدخلًا`}</p>
        <SearchBox initial={q} placeholder={`ابحث عن أي حديث في ${col.short}…`} onSubmit={(v) => navigate(withQuery(`/${c}`, v))} />
      </header>

      {q ? <Results q={q} results={results} cols={data.collections} scope={col.short} /> : (
        tree.length === 0
          ? <p className="empty">هذا الكتاب قيد الإدخال، ولم تُضف أحاديثه بعد.</p>
          : (
            <section className="index">
              {tree.map((k) => (
                <div key={k.kitab} className="kitab-block">
                  <h2 className="sec-title">{k.kitab}</h2>
                  <ul>
                    {k.babs.map((b) => (
                      <li key={b.bab}>
                        <span className="bab-name">{b.bab}</span>
                        <span className="bab-nums">
                          {b.nums.map((n) => <Link key={n} href={hadithPath(c, n)}>{toArabicDigits(n)}</Link>)}
                        </span>
                      </li>
                    ))}
                  </ul>
                </div>
              ))}
            </section>
          )
      )}
    </main>
  );
}
