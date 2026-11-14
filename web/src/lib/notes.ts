// Razdvajanje fusnote na "meta" dio (ko bilježi, odakle, ko je ocijenio — tahridž)
// i tekst rivajeta (metn). Vraća raspone [start, end) meta dijela u originalnom
// (vokaliziranom) tekstu.

const MARKS = /[ؐ-ًؚ-ٰٟۖ-ۭـ]/;

function strip(src: string) {
  let t = '';
  const map: number[] = [];
  for (let i = 0; i < src.length; i++) {
    if (MARKS.test(src[i])) continue;
    t += src[i];
    map.push(i);
  }
  map.push(src.length);
  return { t, map };
}

// uvod koji kaže ko bilježi rivajet; završava dvotačkom (iza nje ide metn)
// ili tačkom (cijela rečenica je samo navod, npr. «أما مسلم فرواه من حديث جابر.»)
const INTRO = new RegExp(
  '^(?:' + [
    'و?ل(?:مسلم|لبخاري|هما)[^:.\\n]{0,140}[:.]',
    'و?أما (?:مسلم|البخاري)[^:.\\n]{0,220}[:.]',
    'و?في (?:رواية|حديث|لفظ|طريق)[^:.\\n]{0,180}[:.]',
    'و?فيه:',
    'و?زاد(?: في رواية| [^:.\\n]{1,30})?:',
    'و?(?:قال|زاد) (?:مسلم|البخاري)[^:.(\\n]{0,40}:',
  ].join('|') + ')',
);

// početak tahridža / ocjene: «أصلحه أبو داود (…)»، «وصححه ابن حبان (…)»، «وقال ابن حجر في الفتح (…):»
const TAKHRIJ = new RegExp(
  '^(?:' + [
    'و?(?:روى|عزاه|عزاها|أصلحه|أصلحها|أصلحهما|رواه|رواها|رواهما|روته|صححه|صححها|صححهما|حسنه|حسنها|حسّنه|حسّنها|جوده|جودها|اجتباه|اجتباها|قواه|قواها|ضعفه|ضعفها|أخرجه|أخرجها|خرجه|ذكره|اختاره|اختارها|احتج)(?=[^.\\n]{0,90}\\(| (?:أبو داود|الترمذي|النسائي|ابن ماجه|الدارمي|أحمد|ابن خزيمة|ابن حبان|الحاكم|الطبراني|البزار|أبو يعلى|ابن أبي شيبة|إسحاق|البيهقي|الدارقطني|الضياء|الذهبي|ابن حجر|ابن معين|الهيثمي|الشوكاني|النووي|ابن القيم|ابن تيمية|ابن عبد البر|المنذري|الألباني|ابن كثير|ابن الملقن|البوصيري|ابن باز|العقيلي|أبو حاتم|ابن عدي|البغوي|ابن الصلاح|العراقي|ابن رجب|الخطابي|المباركفوري|عبد الحق|ابن القطان|السيوطي|المناوي))',
    'و?(?:قال|ذكر) [^:.\\n]{0,80}\\([^)]*\\)',
    'و?قال (?:أبو داود|الترمذي|النسائي|ابن ماجه|الدارمي|أحمد|ابن خزيمة|ابن حبان|الحاكم|الطبراني|البزار|أبو يعلى|ابن أبي شيبة|إسحاق|البيهقي|الدارقطني|الضياء|الذهبي|ابن حجر|ابن معين|الهيثمي|الشوكاني|النووي|ابن القيم|ابن تيمية|ابن عبد البر|المنذري|الألباني|ابن كثير|ابن الملقن|البوصيري|ابن باز|العقيلي|أبو حاتم|ابن عدي|البغوي|ابن الصلاح|العراقي|ابن رجب|الخطابي|المباركفوري|عبد الحق|ابن القطان|السيوطي|المناوي)[^:.\\n]{0,40}:',
  ].join('|') + ')',
);

// U uvodu koji završava dvotačkom nađi prenosioca: «من حديث عمر رضي الله عنه»، «عن أبي هريرة…»،
// «وفي حديث جابر…»، «من حديث يحيى بن يعمر قال:». Vraća početak (ns) i kraj (ne) prenosioca
// u uvodu i da li je ostatak iza njega (npr. «بلفظ:»، «بنحوه، … وفي رواية:») navod.
function splitNarrator(intro: string): { ns: number; ne: number; tailMeta: boolean } | null {
  const m = /(من حديث|في حديث|(?:^|\s)عن\s)/.exec(intro);
  if (!m) return null;
  let ns = m.index + (m[0].startsWith(' ') ? 1 : 0);
  if (intro.slice(0, ns).replace(/[\sو]/g, '') === '') ns = 0; // «وفي حديث جابر:» — nema navoda ispred
  const r = /رضي الله عن\S*/.exec(intro.slice(ns));
  let ne = r ? ns + r.index + r[0].length : intro.length;
  const tail = intro.slice(ne);
  if (/^[\s،]*(?:قال|قالت)?\s*:$/.test(tail)) return { ns, ne: intro.length, tailMeta: false };
  if (!r) {
    // bez «رضي الله عنه»: prenosilac ide do «قال:» ili do dvotačke
    const q = /\s(?:قال|قالت)\s*:$/.exec(intro);
    ne = intro.length;
    if (!q) {
      const b = /\s(?:بلفظ|بنحوه|بمعناه|وفيه)/.exec(intro.slice(ns));
      if (b) return { ns, ne: ns + b.index, tailMeta: true };
    }
    return { ns, ne, tailMeta: false };
  }
  // ostatak je navod samo ako govori o izvoru/lafzu (بلفظ، بنحوه، وفيه، tahridž…), inače je dio rivajeta
  const tailMeta = /(بلفظ|بنحوه|بمعناه|وفيه|موصولا|مرفوعا|معلقا|رواية|\()/.test(tail);
  return tailMeta ? { ns, ne, tailMeta: true } : { ns, ne: intro.length, tailMeta: false };
}

// uvod u kojem je i tahridž («وفي حديث ثوبان بنحوه، رواه أبو داود… وقال …:») nema metna iza sebe:
// sve do kraja odlomka je navod/ocjena
const TAKHRIJ_IN = /(?:^|[\s،])و?(?:رواه|رواها|رواهما|أصلحه|أصلحها|صححه|صححها|حسنه|حسنها|جوده|جودها|اجتباه|اجتباها|قواه|ضعفه|أخرجه|عزاه)(?=[\s،.:])/;

// …osim kad uvod završava uvodom u rivajet («…، وفي رواية:»): tada iza dvotačke ide metn
const RIWAYA_END = /(?:في رواية|في لفظ|فيه|بلفظ)[^،:]*:$/;

// unutar tahridža uvod («وفيه:»، «وفي رواية:»، «وزاد …:») vrijedi samo ako je tekst iza njega vokaliziran
// (rivajet); «وفيه: صالح بن حاتم…» bez harakata je govor o ravijama, dio tahridža
function vocalAfter(src: string, pos: number): boolean {
  return (src.slice(pos, pos + 40).match(/[\u064B-\u0652]/g) || []).length >= 3;
}

export function metaRanges(src: string): [number, number][] {
  const { t, map } = strip(src);
  const out: [number, number][] = [];
  let mode: 'matn' | 'meta' = 'matn';
  let metaFrom = 0;
  let skipUntil = -1;
  const close = (end: number) => {
    if (mode === 'meta' && end > metaFrom) out.push([metaFrom, end]);
    mode = 'matn';
  };

  for (let i = 0; i < t.length; i++) {
    const ch = t[i];
    if (ch === ' ') continue;
    if (i < skipUntil) continue;
    // granica rečenice: početak, novi red, •, iza . ، ؛ ili « - »
    const before = t.slice(Math.max(0, i - 3), i);
    const segStart = i === 0 || /\n\s*$/.test(before) || /•\s*$/.test(before);
    const clauseStart = segStart || /[.،؛!؟]\s+$/.test(before) || /\s-\s+$/.test(before) || (mode === 'meta' && /\)\s+$/.test(before));
    if (ch === '•') { close(i); continue; }
    if (segStart) close(i);
    if (!clauseStart) continue;

    const rest = t.slice(i);
    const m = INTRO.exec(rest);
    if (m) {
      const intro = m[0];
      const end = i + intro.length;
      if (mode === 'meta' && !vocalAfter(src, map[end])) { skipUntil = end; continue; }
      const parts = intro.endsWith(':') ? splitNarrator(intro) : null;
      if (mode !== 'meta') metaFrom = i;
      mode = 'meta';
      if (intro.endsWith(':') && TAKHRIJ_IN.test(intro) && !RIWAYA_END.test(intro)) {
        skipUntil = end; // ostaje meta (tahridž se nastavlja)
        continue;
      }
      if (!parts) {
        close(end); // cijeli uvod je navod (bez prenosioca ili bez metna iza)
      } else {
        // ko bilježi → meta; ko prenosi (ashab / ravija) → boja metna
        close(i + parts.ns);
        if (parts.tailMeta) { mode = 'meta'; metaFrom = i + parts.ne; close(end); }
      }
      skipUntil = end;
      continue;
    }
    if (mode !== 'meta' && TAKHRIJ.test(rest)) { mode = 'meta'; metaFrom = i; }
  }
  close(t.length);
  return out.map(([a, b]) => [map[a], map[b]] as [number, number]);
}

// ---------- šta se ne prikazuje: tahridž i ocjene ----------
// Unutar meta dijela ostaju samo rečenice koje govore ko bilježi / čiji je rivajet
// («ولمسلم في رواية:»، «أما مسلم فرواها من حديث عمر.»، «وفي حديث ثوبان … بنحوه»، «وفي رواية:»),
// a sakriva se tahridž («أصلحه أبو داود…»، «وصححه ابن حبان…»، «وقال ابن حجر…: رجاله ثقات»).
const KEEP_CLAUSE = /^و?(?:ل(?:مسلم|لبخاري|هما)|أما (?:مسلم|البخاري)|في (?:رواية|حديث|لفظ|طريق)|فيه:|زاد(?: في رواية| [^:.]{1,30})?:|بلفظ|بنحوه|بنحو|بمعناه|(?:قال|زاد) (?:مسلم|البخاري))/;

export function hiddenRanges(src: string): [number, number][] {
  const out: [number, number][] = [];
  const meta = metaRanges(src);
  const inMeta = (i: number) => meta.some(([x, y]) => i >= x && i < y);
  for (const [a, b] of meta) {
    // navod iza kojeg u istom odlomku nema metna («أما مسلم فرواها من حديث عمر.»، «وفي حديث ثوبان بنحوه»)
    // se ne prikazuje uopće — nije bitan za hifz
    let j = b;
    while (j < src.length && (src[j] === ' ' || src[j] === '\t')) j++;
    const endsLine = /\n/.test(/\s*$/.exec(src.slice(a, b))![0]); // raspon završava novim redom
    const followed = !endsLine && j < src.length && src[j] !== '\n' && src[j] !== '•' && !inMeta(j);
    if (!followed) { out.push([a, b]); continue; }
    const seg = src.slice(a, b);
    const clauses: { s: number; e: number; keep: boolean }[] = [];
    let prevHidden = false;
    const re = /[،.]\s+|[،.]$|\)\s+/g;
    let s = 0;
    let m: RegExpExecArray | null;
    const push = (e: number) => {
      if (e <= s) return;
      const txt = strip(seg.slice(s, e)).t.trim();
      // sakriva se samo tahridž: rečenica koja ga počinje ili se nastavlja na njega
      const hide = TAKHRIJ.test(txt) || (!KEEP_CLAUSE.test(txt) && prevHidden);
      prevHidden = hide;
      clauses.push({ s, e, keep: !hide });
      s = e;
    };
    while ((m = re.exec(seg))) push(m.index + m[0].length);
    push(seg.length);
    clauses.forEach((c, k) => {
      if (c.keep) {
        // zarez na kraju sačuvane rečenice iza koje dolazi samo skriveni tahridž → i njega sakrij
        const laterKeep = clauses.slice(k + 1).some((x) => x.keep);
        const comma = /،\s*$/.exec(seg.slice(c.s, c.e));
        if (comma && !laterKeep && k + 1 < clauses.length) out.push([a + c.s + comma.index, a + c.e]);
      } else out.push([a + c.s, a + c.e]);
    });
  }
  // novi red se nikad ne sakriva (da se odlomci ne spoje)
  const res: [number, number][] = [];
  for (const [a, b] of out) {
    let s = a;
    for (let i = a; i < b; i++) if (src[i] === '\n') { if (i > s) res.push([s, i]); s = i + 1; }
    if (b > s) res.push([s, b]);
  }
  return res;
}

/** tekst fusnote sa skrivenim dijelovima zamijenjenim razmacima (iste dužine — za pretragu) */
export function maskHidden(src: string): string {
  const arr = src.split('');
  for (const [a, b] of hiddenRanges(src)) for (let i = a; i < b; i++) if (arr[i] !== '\n') arr[i] = ' ';
  return arr.join('');
}

/** da li u fusnoti išta ostaje za prikaz */
export const hasVisible = (src: string) => /[ء-ي]/.test(maskHidden(src));
