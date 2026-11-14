const DIG = '٠١٢٣٤٥٦٧٨٩';
export const toArabicDigits = (n: number | string) => String(n).replace(/\d/g, (d) => DIG[+d]);

export function takhrijLabel(t: string[]): string {
  if (t.length === 2 && t.includes('البخاري') && t.includes('مسلم')) return 'متفق عليه';
  return 'رواه ' + t.join(' و');
}
