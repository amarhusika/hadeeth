import { useEffect, useMemo, useState } from 'react';
import type { Data } from './types';
import { buildIndex } from './lib/search';
import { useRoute } from './lib/router';
import Link from './components/Link';
import Home from './pages/Home';
import CollectionPage from './pages/CollectionPage';
import HadithPage from './pages/HadithPage';
import JumpPopup from './components/JumpPopup';

export default function App() {
  const route = useRoute();
  const [data, setData] = useState<Data | null>(null);
  const [jumpOpen, setJumpOpen] = useState(false);
  const [dark, setDark] = useState(() => {
    try { return localStorage.getItem('theme') === 'dark'; } catch { return false; }
  });

  useEffect(() => {
    fetch('/data/hadith.json').then((r) => r.json()).then(setData);
  }, []);
  useEffect(() => {
    document.documentElement.dataset.theme = dark ? 'dark' : 'light';
    try { localStorage.setItem('theme', dark ? 'dark' : 'light'); } catch { /* */ }
  }, [dark]);

  const index = useMemo(() => (data ? buildIndex(data.hadiths) : []), [data]);
  const active = route.view === 'collection' || route.view === 'hadith' ? route.c : '';

  return (
    <div className="shell">
      <nav className="top">
        <Link className="brand" href="/">
          <span className="seal" aria-hidden>ح</span>
          <span>حِفْظُ السُّنَّةِ</span>
        </Link>
        <div className="nav-links">
          <Link href="/sahihayn" className={active === 'sahihayn' ? 'on' : ''}>الصحيحان</Link>
          <Link href="/zawaid" className={active === 'zawaid' ? 'on' : ''}>الزوائد</Link>
        </div>
        <button className={`goto ${jumpOpen ? 'on' : ''}`} onClick={() => setJumpOpen((v) => !v)} disabled={!data}
          title="انتقل إلى الحديث" aria-label="انتقل إلى الحديث">
          <svg viewBox="0 0 24 24" aria-hidden><circle cx="11" cy="11" r="7" /><path d="M21 21l-5-5" /></svg>
        </button>
        {route.view === 'home' && (
          <button className="theme" onClick={() => setDark((d) => !d)} aria-label="الوضع الليلي">{dark ? '☀' : '☾'}</button>
        )}
      </nav>

      {data && jumpOpen && <JumpPopup cols={data.collections} initial={active || 'sahihayn'} onClose={() => setJumpOpen(false)} />}
      {!data && <div className="loading"><span /><span /><span /></div>}
      {data && route.view === 'home' && <Home data={data} index={index} q={route.q} />}
      {data && route.view === 'collection' && <CollectionPage data={data} index={index} c={route.c} q={route.q} />}
      {data && route.view === 'hadith' && <HadithPage data={data} c={route.c} n={route.n} />}
      {data && route.view === 'notfound' && (
        <main className="page"><p className="empty">الصفحة غير موجودة. <Link href="/">العودة إلى الرئيسة</Link></p></main>
      )}
    </div>
  );
}
