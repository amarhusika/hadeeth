export interface Hadith {
  c: string;            // ključ zbirke: 'sahihayn' | 'zawaid'
  n: number;            // broj hadisa kao u štampanom izdanju
  volume: number;
  kitab: string;
  bab: string;
  page: number;
  /** glavni tekst; {1}, {2}… su oznake fusnota na mjestu kao u knjizi */
  text: string;
  footnotes: string[];
  takhrij: string[];
}

export interface Collection {
  title: string;
  short: string;
  author: string;
  total: number;
  count: number;
  takhrij: string[];
}

export interface Data {
  collections: Record<string, Collection>;
  hadiths: Hadith[];
}
