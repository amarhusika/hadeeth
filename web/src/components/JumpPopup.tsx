import { useEffect } from 'react';
import { createPortal } from 'react-dom';
import type { Collection } from '../types';
import Jump from './Jump';

/** "انتقل إلى الحديث" kao prozor na sredini ekrana (otvara se dugmetom # u gornjoj traci) */
export default function JumpPopup({ cols, initial, onClose }: {
  cols: Record<string, Collection>; initial: string; onClose: () => void;
}) {
  useEffect(() => {
    const k = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', k);
    return () => window.removeEventListener('keydown', k);
  }, [onClose]);
  return createPortal(
    <div className="fn-layer" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <div className="jump-modal" role="dialog" aria-modal="true" aria-label="انتقل إلى الحديث">
        <header className="fn-pop-head">
          <span className="jump-title">انتقل إلى الحديث</span>
          <button className="fn-pop-x" onClick={onClose} aria-label="إغلاق">×</button>
        </header>
        <Jump cols={cols} initial={initial} onGo={onClose} autoFocus />
      </div>
    </div>,
    document.body,
  );
}
