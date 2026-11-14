package com.amar.hadeeth

/*
 * Pretraga bez harakata — isto kao src/lib/arabic.ts i src/lib/search.ts na webu.
 * Vraća samo hadise u kojima se upisani tekst nalazi doslovno:
 * iste riječi, istim redom (harakati se zanemaruju).
 */

class Normalized(val text: String, val map: IntArray)

object Arabic {
    fun isDiacritic(ch: Char): Boolean {
        val c = ch.code
        return c in 0x610..0x61A || c in 0x64B..0x65F || c == 0x670 || c in 0x6D6..0x6ED || c == 0x640
    }

    private fun mapChar(ch: Char): Char = when (ch) {
        'أ', 'إ', 'آ', 'ٱ', 'ٲ', 'ٳ', 'ء', 'ئ', 'ؤ' -> 'ا'
        'ى', 'ی' -> 'ي'
        'ة' -> 'ه'
        'ک' -> 'ك'
        else -> ch.lowercaseChar()
    }

    private val EXPAND = mapOf('ﷺ' to "صلي الله عليه وسلم", 'ﷻ' to "جل جلاله")

    private fun keep(ch: Char): Boolean {
        val c = ch.code
        return c in 0x621..0x64A || ch in '0'..'9' || ch in 'a'..'z' || c in 0x660..0x669
    }

    /** Normalizira tekst i pamti za svaki znak poziciju u originalu (za isticanje pogodaka). */
    fun normalizeWithMap(src: String): Normalized {
        val sb = StringBuilder(src.length)
        var map = IntArray(src.length + 16)
        var size = 0
        fun push(i: Int) {
            if (size == map.size) map = map.copyOf(map.size * 2)
            map[size++] = i
        }
        var lastSpace = true
        var i = 0
        while (i < src.length) {
            var ch = src[i]
            // oznaka fusnote {12} se preskače
            if (ch == '{') {
                val j = src.indexOf('}', i)
                if (j > i + 1 && (i + 1 until j).all { src[it] in '0'..'9' }) { i = j + 1; continue }
            }
            if (isDiacritic(ch)) { i++; continue }
            val ex = EXPAND[ch]
            if (ex != null) {
                if (!lastSpace) { sb.append(' '); push(i) }
                for (c in ex) { sb.append(c); push(i) }
                lastSpace = false
                i++; continue
            }
            ch = mapChar(ch)
            if (keep(ch)) {
                if (!(ch == 'ا' && sb.isNotEmpty() && sb[sb.length - 1] == 'ا')) {
                    sb.append(ch); push(i); lastSpace = false
                }
            } else if (!lastSpace) {
                sb.append(' '); push(i); lastSpace = true
            }
            i++
        }
        return Normalized(sb.toString(), map.copyOf(size))
    }

    fun normalize(src: String): String = normalizeWithMap(src).text.trim()

    /** Rasponi [start, end) u originalu gdje se nalazi fraza t (cijele riječi). */
    fun findRanges(norm: Normalized, t: String): List<IntArray> {
        val out = ArrayList<IntArray>()
        if (t.isEmpty()) return out
        val s = norm.text
        var from = 0
        while (true) {
            val idx = s.indexOf(t, from)
            if (idx < 0) break
            val end = idx + t.length
            val okStart = idx == 0 || s[idx - 1] == ' '
            val okEnd = end == s.length || s[end] == ' '
            if (okStart && okEnd) {
                out += intArrayOf(norm.map[idx], norm.map[end - 1] + 1)
                from = end
            } else from = idx + 1
        }
        return out
    }

    /** proširi kraj raspona preko harakata koji slijede */
    fun extendOverMarks(src: String, r: IntArray) {
        while (r[1] < src.length && isDiacritic(src[r[1]])) r[1]++
    }
}

class Indexed(val h: Hadith, val fields: List<Pair<String, Normalized>>)

class Result(val h: Hadith, val score: Int, val marks: Map<String, List<IntArray>>)

object Search {
    fun buildIndex(hadiths: List<Hadith>): List<Indexed> = hadiths.map { h ->
        val f = ArrayList<Pair<String, Normalized>>(2 + h.footnotes.size)
        f += "text" to Arabic.normalizeWithMap(h.text)
        f += "bab" to Arabic.normalizeWithMap(h.bab)
        // skriveni tahridž se ne pretražuje (zamijenjen razmacima iste dužine)
        h.footnotes.forEachIndexed { i, s -> f += "fn$i" to Arabic.normalizeWithMap(Notes.maskHidden(s)) }
        Indexed(h, f)
    }

    fun search(index: List<Indexed>, query: String, collection: String? = null): List<Result> {
        val qn = Arabic.normalize(query)
        if (qn.isEmpty()) return emptyList()
        val out = ArrayList<Result>()
        for (item in index) {
            if (collection != null && item.h.c != collection) continue
            val marks = HashMap<String, List<IntArray>>()
            var score = 0
            for ((key, norm) in item.fields) {
                val rs = Arabic.findRanges(norm, qn)
                if (rs.isEmpty()) continue
                marks[key] = rs
                score = maxOf(score, when (key) { "text" -> 3; "bab" -> 2; else -> 1 })
            }
            if (score > 0) out += Result(item.h, score, marks)
        }
        out.sortWith(compareByDescending<Result> { it.score }.thenBy { it.h.n })
        return out
    }
}
