package com.amar.hadeeth

/**
 * Razdvajanje fusnote na "meta" dio (ko bilježi, odakle, ko je ocijenio — tahridž)
 * i tekst rivajeta (metn). Isto kao src/lib/notes.ts na webu.
 * Vraća raspone [start, end) meta dijela u originalnom (vokaliziranom) tekstu.
 */
object Notes {
    // uvod koji kaže ko bilježi rivajet; završava dvotačkom (iza nje metn) ili tačkom (samo navod)
    private val INTRO = Regex(
        "^(?:" + listOf(
            "و?ل(?:مسلم|لبخاري|هما)[^:.\\n]{0,140}[:.]",
            "و?أما (?:مسلم|البخاري)[^:.\\n]{0,220}[:.]",
            "و?في (?:رواية|حديث|لفظ|طريق)[^:.\\n]{0,180}[:.]",
            "و?فيه:",
            "و?زاد(?: في رواية| [^:.\\n]{1,30})?:",
            "و?(?:قال|زاد) (?:مسلم|البخاري)[^:.(\\n]{0,40}:",
        ).joinToString("|") + ")"
    )

    // početak tahridža / ocjene
    private val TAKHRIJ = Regex(
        "^(?:" + listOf(
            "و?(?:روى|عزاه|عزاها|أصلحه|أصلحها|أصلحهما|رواه|رواها|رواهما|روته|صححه|صححها|صححهما|حسنه|حسنها|حسّنه|حسّنها|جوده|جودها|اجتباه|اجتباها|قواه|قواها|ضعفه|ضعفها|أخرجه|أخرجها|خرجه|ذكره|اختاره|اختارها|احتج)(?=[^.\\n]{0,90}\\(| (?:أبو داود|الترمذي|النسائي|ابن ماجه|الدارمي|أحمد|ابن خزيمة|ابن حبان|الحاكم|الطبراني|البزار|أبو يعلى|ابن أبي شيبة|إسحاق|البيهقي|الدارقطني|الضياء|الذهبي|ابن حجر|ابن معين|الهيثمي|الشوكاني|النووي|ابن القيم|ابن تيمية|ابن عبد البر|المنذري|الألباني|ابن كثير|ابن الملقن|البوصيري|ابن باز|العقيلي|أبو حاتم|ابن عدي|البغوي|ابن الصلاح|العراقي|ابن رجب|الخطابي|المباركفوري|عبد الحق|ابن القطان|السيوطي|المناوي))",
            "و?(?:قال|ذكر) [^:.\\n]{0,80}\\([^)]*\\)",
            "و?قال (?:أبو داود|الترمذي|النسائي|ابن ماجه|الدارمي|أحمد|ابن خزيمة|ابن حبان|الحاكم|الطبراني|البزار|أبو يعلى|ابن أبي شيبة|إسحاق|البيهقي|الدارقطني|الضياء|الذهبي|ابن حجر|ابن معين|الهيثمي|الشوكاني|النووي|ابن القيم|ابن تيمية|ابن عبد البر|المنذري|الألباني|ابن كثير|ابن الملقن|البوصيري|ابن باز|العقيلي|أبو حاتم|ابن عدي|البغوي|ابن الصلاح|العراقي|ابن رجب|الخطابي|المباركفوري|عبد الحق|ابن القطان|السيوطي|المناوي)[^:.\\n]{0,40}:",
        ).joinToString("|") + ")"
    )

    // uvod u kojem je i tahridž nema metna iza sebe: sve do kraja odlomka je navod/ocjena
    private val TAKHRIJ_IN = Regex("(?:^|[\\s،])و?(?:رواه|رواها|رواهما|أصلحه|أصلحها|صححه|صححها|حسنه|حسنها|جوده|جودها|اجتباه|اجتباها|قواه|ضعفه|أخرجه|عزاه)(?=[\\s،.:])")
    // …osim kad uvod završava uvodom u rivajet («…، وفي رواية:»): tada iza dvotačke ide metn
    private val RIWAYA_END = Regex("(?:في رواية|في لفظ|فيه|بلفظ)[^،:]*:$")
    private val NARRATOR = Regex("(من حديث|في حديث|(?:^|\\s)عن\\s)")
    private val RADI = Regex("رضي الله عن\\S*")
    private val TAIL_QALA = Regex("^[\\s،]*(?:قال|قالت)?\\s*:$")
    private val END_QALA = Regex("\\s(?:قال|قالت)\\s*:$")
    private val LAFZ = Regex("\\s(?:بلفظ|بنحوه|بمعناه|وفيه)")
    private val TAIL_META = Regex("(بلفظ|بنحوه|بمعناه|وفيه|موصولا|مرفوعا|معلقا|رواية|\\()")
    private val ONLY_WAW = Regex("[\\sو]")
    private val SEG_NL = Regex("\\n\\s*$")
    private val SEG_BULLET = Regex("•\\s*$")
    private val PUNCT = Regex("[.،؛!؟]\\s+$")
    private val DASH = Regex("\\s-\\s+$")
    private val PAREN = Regex("\\)\\s+$")

    private class Split(val ns: Int, val ne: Int, val tailMeta: Boolean)

    /** prenosilac u uvodu: «من حديث عمر رضي الله عنه»، «عن أبي سعيد…»، «وفي حديث جابر…» */
    private fun splitNarrator(intro: String): Split? {
        val m = NARRATOR.find(intro) ?: return null
        var ns = m.range.first + if (m.value.first().isWhitespace()) 1 else 0
        if (intro.substring(0, ns).replace(ONLY_WAW, "").isEmpty()) ns = 0
        val r = RADI.find(intro.substring(ns))
        var ne = if (r != null) ns + r.range.first + r.value.length else intro.length
        val tail = intro.substring(ne)
        if (TAIL_QALA.containsMatchIn(tail)) return Split(ns, intro.length, false)
        if (r == null) {
            ne = intro.length
            if (END_QALA.find(intro) == null) {
                val b = LAFZ.find(intro.substring(ns))
                if (b != null) return Split(ns, ns + b.range.first, true)
            }
            return Split(ns, ne, false)
        }
        return if (TAIL_META.containsMatchIn(tail)) Split(ns, ne, true) else Split(ns, intro.length, false)
    }

    // unutar tahridža uvod vrijedi samo ako je tekst iza njega vokaliziran (rivajet)
    private val HARAKA = Regex("[\\u064B-\\u0652]")
    private fun vocalAfter(src: String, pos: Int) = HARAKA.findAll(src.substring(pos, minOf(src.length, pos + 40))).count() >= 3

    fun metaRanges(src: String): List<IntArray> {
        val sb = StringBuilder(src.length)
        val map = ArrayList<Int>(src.length + 1)
        for (i in src.indices) {
            if (Arabic.isDiacritic(src[i])) continue
            sb.append(src[i]); map.add(i)
        }
        map.add(src.length)
        val t = sb.toString()

        val out = ArrayList<IntArray>()
        var meta = false
        var metaFrom = 0
        var skipUntil = -1
        fun close(end: Int) {
            if (meta && end > metaFrom) out.add(intArrayOf(metaFrom, end))
            meta = false
        }

        for (i in t.indices) {
            val ch = t[i]
            if (ch == ' ' || i < skipUntil) continue
            val before = t.substring(maxOf(0, i - 3), i)
            val segStart = i == 0 || SEG_NL.containsMatchIn(before) || SEG_BULLET.containsMatchIn(before)
            val clauseStart = segStart || PUNCT.containsMatchIn(before) || DASH.containsMatchIn(before) || (meta && PAREN.containsMatchIn(before))
            if (ch == '•') { close(i); continue }
            if (segStart) close(i)
            if (!clauseStart) continue

            val rest = t.substring(i)
            val m = INTRO.find(rest)
            if (m != null) {
                val intro = m.value
                val end = i + intro.length
                if (meta && !vocalAfter(src, map[end])) { skipUntil = end; continue }
                val parts = if (intro.endsWith(":")) splitNarrator(intro) else null
                if (!meta) metaFrom = i
                meta = true
                if (intro.endsWith(":") && TAKHRIJ_IN.containsMatchIn(intro) && !RIWAYA_END.containsMatchIn(intro)) {
                    skipUntil = end // ostaje meta (tahridž se nastavlja)
                    continue
                }
                if (parts == null) {
                    close(end)
                } else {
                    // ko bilježi → meta; ko prenosi (ashab / ravija) → boja metna
                    close(i + parts.ns)
                    if (parts.tailMeta) { meta = true; metaFrom = i + parts.ne; close(end) }
                }
                skipUntil = end
                continue
            }
            if (!meta && TAKHRIJ.containsMatchIn(rest)) { meta = true; metaFrom = i }
        }
        close(t.length)
        return out.map { intArrayOf(map[it[0]], map[it[1]]) }
    }

    // ---------- šta se ne prikazuje: tahridž, ocjene i navodi bez metna ----------
    private val KEEP_CLAUSE = Regex("^و?(?:ل(?:مسلم|لبخاري|هما)|أما (?:مسلم|البخاري)|في (?:رواية|حديث|لفظ|طريق)|فيه:|زاد(?: في رواية| [^:.]{1,30})?:|بلفظ|بنحوه|بنحو|بمعناه|(?:قال|زاد) (?:مسلم|البخاري))")
    private val CLAUSE_END = Regex("[،.]\\s+|[،.]$|\\)\\s+")
    private val TRAIL_COMMA = Regex("،\\s*$")
    private val ARABIC_LETTER = Regex("[\\u0621-\\u064A]")

    private fun stripMarks(s: String) = s.filterNot { Arabic.isDiacritic(it) }

    /** rasponi koji se ne prikazuju (isto kao hiddenRanges u src/lib/notes.ts) */
    fun hiddenRanges(src: String): List<IntArray> {
        val out = ArrayList<IntArray>()
        val meta = metaRanges(src)
        fun inMeta(i: Int) = meta.any { i >= it[0] && i < it[1] }
        for (r in meta) {
            val a = r[0]
            val b = r[1]
            val seg = src.substring(a, b)
            // navod iza kojeg u istom odlomku nema metna se ne prikazuje uopće
            var j = b
            while (j < src.length && (src[j] == ' ' || src[j] == '\t')) j++
            var e = seg.length
            var endsLine = false
            while (e > 0 && seg[e - 1].isWhitespace()) { if (seg[e - 1] == '\n') endsLine = true; e-- }
            val followed = !endsLine && j < src.length && src[j] != '\n' && src[j] != '•' && !inMeta(j)
            if (!followed) { out.add(intArrayOf(a, b)); continue }

            // inače ostaju samo uvodi (ko bilježi / čiji je rivajet), a tahridž se sakriva
            val cs = ArrayList<IntArray>() // [start, end, keep]
            var s = 0
            var prevHidden = false
            fun push(end: Int) {
                if (end <= s) return
                val txt = stripMarks(seg.substring(s, end)).trim()
                val hide = TAKHRIJ.containsMatchIn(txt) || (!KEEP_CLAUSE.containsMatchIn(txt) && prevHidden)
                prevHidden = hide
                cs.add(intArrayOf(s, end, if (hide) 0 else 1))
                s = end
            }
            for (m in CLAUSE_END.findAll(seg)) push(m.range.last + 1)
            push(seg.length)
            cs.forEachIndexed { k, c ->
                if (c[2] == 1) {
                    val laterKeep = (k + 1 until cs.size).any { cs[it][2] == 1 }
                    val cm = TRAIL_COMMA.find(seg.substring(c[0], c[1]))
                    if (cm != null && !laterKeep && k + 1 < cs.size) out.add(intArrayOf(a + c[0] + cm.range.first, a + c[1]))
                } else out.add(intArrayOf(a + c[0], a + c[1]))
            }
        }
        // novi red se nikad ne sakriva
        val res = ArrayList<IntArray>()
        for (r in out) {
            var s = r[0]
            for (i in r[0] until r[1]) if (src[i] == '\n') { if (i > s) res.add(intArrayOf(s, i)); s = i + 1 }
            if (r[1] > s) res.add(intArrayOf(s, r[1]))
        }
        return res
    }

    /** skriveni dijelovi zamijenjeni razmacima (iste dužine — za pretragu i kopiranje) */
    fun maskHidden(src: String): String {
        val arr = src.toCharArray()
        for (r in hiddenRanges(src)) for (i in r[0] until r[1]) if (arr[i] != '\n') arr[i] = ' '
        return String(arr)
    }

    /** da li u fusnoti išta ostaje za prikaz */
    fun hasVisible(src: String) = ARABIC_LETTER.containsMatchIn(maskHidden(src))
}
