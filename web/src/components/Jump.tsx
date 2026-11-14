import { useState } from 'react';
import type { Collection } from '../types';
import { navigate, hadithPath } from '../lib/router';
import { toArabicDigits } from '../lib/format';

/** Skok na hadis po broju: odabir zbirke + broj (1 … ukupno u štampanom izdanju) */
export default function Jump({ cols, initial = 'sahihayn', onGo, autoFocus }: {
  cols: Record<string, Collection>; initial?: string; onGo?: () => void; autoFocus?: boolean;
}) {
  const [c, setC] = useState(initial);
  const [v, setV] = useState('');
  const total = cols[c]?.total ?? 0;
  const digits = v.replace(/[٠-٩]/g, (d) => String('٠١٢٣٤٥٦٧٨٩'.indexOf(d)));
  const n = Number(digits);
  const valid = /^\d+$/.test(digits) && n >= 1 && n <= total;
  const err = v !== '' && !valid;

  return (
    <form className="jump" onSubmit={(e) => { e.preventDefault(); if (valid) { navigate(hadithPath(c, n)); onGo?.(); } }}>
      <span className="jump-label">انتقل إلى الحديث</span>
      <select value={c} onChange={(e) => setC(e.target.value)} aria-label="الكتاب">
        {Object.entries(cols).map(([k, col]) => <option key={k} value={k}>{col.short}</option>)}
      </select>
      <input value={v} onChange={(e) => setV(e.target.value.replace(/[^0-9٠-٩]/g, ''))}
        inputMode="numeric" autoFocus={autoFocus} placeholder={`١ – ${toArabicDigits(total)}`} aria-label="رقم الحديث"
        aria-invalid={err} className={err ? 'bad' : ''} />
      <button type="submit" disabled={!valid}>انتقال</button>
      {err && <span className="jump-err">الرقم يجب أن يكون بين ١ و{toArabicDigits(total)}</span>}
    </form>
  );
}
