import { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import RichText from './RichText';
import { toArabicDigits } from '../lib/format';

/**
 * Prozor s fusnotom: na sredini ekrana, iste širine kao kartica s tekstom hadisa; ostatak stranice blago zamućen.
 * Zatvara se klikom van prozora, na × ili tipkom Esc. Dok je otvoren, stranica ispod se ne lista.
 */
export default function FootnotePopup({ n, text, ranges, anchor, onClose }: {
  n: number; text: string; ranges?: [number, number][]; anchor?: HTMLElement; onClose: () => void;
}) {
  // širina kartice hadisa u kojoj je kliknuta fusnota
  const cardWidth = () => anchor?.closest('.card')?.getBoundingClientRect().width;
  const [width, setWidth] = useState(cardWidth);
  useEffect(() => {
    const f = () => setWidth(cardWidth());
    window.addEventListener('resize', f);
    return () => window.removeEventListener('resize', f);
  });
  useEffect(() => {
    const k = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', k);
    return () => window.removeEventListener('keydown', k);
  }, [onClose]);

  useEffect(() => {
    const html = document.documentElement;
    const gap = window.innerWidth - html.clientWidth; // širina trake za listanje, da se stranica ne pomjeri
    const prev = { overflow: html.style.overflow, pad: document.body.style.paddingRight };
    html.style.overflow = 'hidden';
    if (gap > 0) document.body.style.paddingRight = gap + 'px';
    return () => { html.style.overflow = prev.overflow; document.body.style.paddingRight = prev.pad; };
  }, []);

  return createPortal(
    <div className="fn-layer" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <div className="fn-modal" role="dialog" aria-modal="true" aria-label="حاشية" style={width ? { width } : undefined}>
        <header className="fn-pop-head">
          <span className="fn-num">({toArabicDigits(n)})</span>
          <button className="fn-pop-x" onClick={onClose} aria-label="إغلاق">×</button>
        </header>
        <RichText text={text} ranges={ranges} className="fn-pop-text" notes />
      </div>
    </div>,
    document.body,
  );
}
