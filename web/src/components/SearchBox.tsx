import { useEffect, useRef, useState } from 'react';

export default function SearchBox({ initial, big, placeholder, onSubmit }: {
  initial: string; big?: boolean; placeholder?: string; onSubmit: (q: string) => void;
}) {
  const [q, setQ] = useState(initial);
  const ref = useRef<HTMLInputElement>(null);
  useEffect(() => setQ(initial), [initial]);
  useEffect(() => {
    const k = (e: KeyboardEvent) => {
      if (e.key === '/' && document.activeElement !== ref.current) { e.preventDefault(); ref.current?.focus(); }
    };
    window.addEventListener('keydown', k);
    return () => window.removeEventListener('keydown', k);
  }, []);
  return (
    <form className={`search ${big ? 'big' : ''}`} role="search"
      onSubmit={(e) => { e.preventDefault(); onSubmit(q.trim()); }}>
      <svg className="ico" viewBox="0 0 24 24" aria-hidden><circle cx="11" cy="11" r="7" /><path d="m20 20-3.5-3.5" /></svg>
      <input ref={ref} value={q} onChange={(e) => setQ(e.target.value)} dir="rtl" lang="ar" enterKeyHint="search"
        placeholder={placeholder ?? 'ابحث عن أي حديث…'} aria-label="بحث في الأحاديث" />
      {q && <button type="button" className="clear" onClick={() => { setQ(''); ref.current?.focus(); }} aria-label="مسح">×</button>}
      <button type="submit" className="go">بحث</button>
    </form>
  );
}
