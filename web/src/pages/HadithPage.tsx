import { useEffect } from 'react';
import type { Data } from '../types';
import { hadithPath } from '../lib/router';
import { toArabicDigits } from '../lib/format';
import { setSeo, plain, DESC } from '../lib/seo';
import HadithCard from '../components/HadithCard';
import Link from '../components/Link';

export default function HadithPage({ data, c, n }: { data: Data; c: string; n: number }) {
  const col = data.collections[c];
  const list = data.hadiths.filter((h) => h.c === c);
  const i = list.findIndex((h) => h.n === n);
  const h = list[i];
  useEffect(() => {
    setSeo({
      title: `${col.short} ${toArabicDigits(n)}${h ? ' — ' + plain(h.bab) : ''} | حفظ السنة`,
      desc: h ? plain(h.text).slice(0, 160) : DESC,
      path: hadithPath(c, n),
      index: !!h,
    });
  }, [col, c, n, h]);

  const prev = list[i - 1], next = list[i + 1];
  return (
    <main className="page hadith-page">
      <nav className="crumb">
        <Link href="/">الرئيسة</Link> ‹ <Link href={`/${c}`}>{col.short}</Link>
        {h && <> ‹ <span>{h.kitab}</span></>}
      </nav>
      {h ? <HadithCard h={h} col={col} standalone /> : (
        <p className="empty">{n < 1 || n > col.total
          ? <>لا يوجد حديث بهذا الرقم؛ أحاديث {col.short} من ١ إلى {toArabicDigits(col.total)}.</>
          : <>الحديث رقم {toArabicDigits(n)} من {col.short} لم يُدخل بعد.</>}</p>
      )}
      <nav className="pager">
        {prev ? <Link href={hadithPath(c, prev.n)} className="ghost">→ الحديث {toArabicDigits(prev.n)}</Link> : <span />}
        {next ? <Link href={hadithPath(c, next.n)} className="ghost">الحديث {toArabicDigits(next.n)} ←</Link> : <span />}
      </nav>
    </main>
  );
}
