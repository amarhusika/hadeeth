import type { AnchorHTMLAttributes } from 'react';
import { onLinkClick } from '../lib/router';

export default function Link(props: AnchorHTMLAttributes<HTMLAnchorElement> & { href: string }) {
  return <a {...props} onClick={(e) => { props.onClick?.(e); onLinkClick(e); }} />;
}
