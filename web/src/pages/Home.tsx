import { useEffect, useMemo } from 'react';
import type { Data } from '../types';
import { search, type Indexed } from '../lib/search';
import { navigate, withQuery } from '../lib/router';
import { toArabicDigits } from '../lib/format';
import { setSeo } from '../lib/seo';
import SearchBox from '../components/SearchBox';
import Results from '../components/Results';
import Link from '../components/Link';

export default function Home({ data, index, q }: { data: Data; index: Indexed[]; q: string }) {
  useEffect(() => { setSeo({ path: '/', index: !q }); }, [q]);
  const results = useMemo(() => (q ? search(index, q) : []), [index, q]);
  const cols = data.collections;
  return (
    <main className="page home">
      <section className={`hero ${q ? 'compact' : ''}`}>
        {!q && <>
          <p className="eyebrow">موسوعة الحفاظ</p>
          <h1>ابحث في أحاديث<br /><em>الصحيحين والزوائد</em></h1>
        </>}
        <SearchBox initial={q} big placeholder="ابحث عن أي حديث…" onSubmit={(v) => navigate(withQuery('/', v))} />
      </section>

      {q ? <Results q={q} results={results} cols={cols} /> : (
        <section className="collections">
          {Object.entries(cols).map(([key, c]) => {
            const pct = Math.round((c.count / c.total) * 100);
            return (
              <Link key={key} href={`/${key}`} className={`coll coll-${key}`}>
                <span className="coll-k">{c.short}</span>
                <h2>{c.title}</h2>
                {c.count < c.total && <div className="progress" aria-label="نسبة الإدخال"><span style={{ width: `${Math.max(pct, 1)}%` }} /></div>}
                <p className="muted">
                  {c.count >= c.total ? `${toArabicDigits(c.total)} حديثًا` : c.count ? `${toArabicDigits(c.count)} من ${toArabicDigits(c.total)} حديثًا مُدخلًا` : `${toArabicDigits(c.total)} حديثًا — قيد الإدخال`}
                </p>
                <span className="coll-go">تصفّح وابحث ←</span>
              </Link>
            );
          })}
        </section>
      )}

      {!q && (
        <footer className="foot">
          <p className="based">عُمِلَ هَذَا الْمَوْقِعُ وَفْقَ كِتَابِ<br /><b>«مُحَصِّلُ دَوَاوِينِ السُّنَّةِ الثَّمَانِيَةَ عَشَرَ: زِيَادَاتُ السُّنَنِ وَالْمَسَانِيدِ وَالصِّحَاحِ وَالْمَعَاجِمِ لِلْحُفَّاظِ»</b></p>
          <p>النصوص من «الجمع بين الصحيحين للحفاظ» و«الجمع بين زوائد السنن» ليحيى بن عبد العزيز اليحيى — طبعة دار الرفقة.</p>
          <p><a className="app-link" href="https://github.com/amarhusika/hadeeth" target="_blank" rel="noopener"><span className="app-ico" aria-hidden />تطبيق الجوال</a></p>
          <p className="muted">ما كان بهذا اللون <span className="buk">(…)</span> فهو من أفراد البخاري، والحواشي تحت كل حديث كما في المطبوع.</p>
        </footer>
      )}
    </main>
  );
}
