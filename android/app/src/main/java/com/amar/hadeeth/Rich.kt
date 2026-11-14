package com.amar.hadeeth

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.SuperscriptSpan
import android.view.View
import androidx.core.content.ContextCompat

private const val DIG = "٠١٢٣٤٥٦٧٨٩"

fun arabicDigits(n: Any): String = n.toString().map { if (it in '0'..'9') DIG[it - '0'] else it }.joinToString("")

fun latinDigits(s: String): String = s.map { val i = DIG.indexOf(it); if (i >= 0) '0' + i else it }.joinToString("")

fun takhrijLabel(t: List<String>): String =
    if (t.size == 2 && "البخاري" in t && "مسلم" in t) "متفق عليه" else "رواه " + t.joinToString(" و")

/** Link bez podvlačenja, u zadanoj boji. */
class TapSpan(private val color: Int, private val bold: Boolean = false, private val onTap: () -> Unit) : ClickableSpan() {
    override fun onClick(widget: View) = onTap()
    override fun updateDrawState(ds: TextPaint) {
        ds.color = color
        ds.isUnderlineText = false
        if (bold) ds.isFakeBoldText = true
    }
}

/**
 * Vokalizirani tekst hadisa u boji, kao RichText.tsx na webu:
 *  ﴿…﴾ ajeti, (…) dio koji bilježi samo Buhari, ﷺ, istaknuti pogoci pretrage,
 *  {n} → (n) kao eksponent (klik vodi na fusnotu kad je onRef zadan).
 */
object Rich {
    private const val HL = 1
    private const val QURAN = 2
    private const val BUK = 4
    private const val SAW = 8
    private const val META = 16
    private const val HIDE = 32

    // u tahridžu se ne prikazuju brojevi hadisa/tomova: «(٤٨٠٧)»، «(٧/ ٥٢٨)»، «٢٠: (٨٣٨)»
    private val REF_NUM = Regex("\\s*(?:[٠-٩0-9]+\\s*:\\s*)?\\([٠-٩0-9\\s/\\-–،,:]+\\)")
    private val REF = Regex("\\{(\\d+)\\}")

    /** notes = tekst fusnote: ko bilježi i tahridž u posebnoj boji, odvojeno od metna */
    fun build(
        ctx: Context, src: String, ranges: List<IntArray>? = null,
        notes: Boolean = false, refMap: ((Int) -> Int)? = null, onRef: ((Int) -> Unit)? = null,
    ): CharSequence {
        val cQuran = ContextCompat.getColor(ctx, R.color.quran)
        val cBuk = ContextCompat.getColor(ctx, R.color.buk)
        val cGold = ContextCompat.getColor(ctx, R.color.gold)
        val cMark = ContextCompat.getColor(ctx, R.color.mark)
        val cMeta = ContextCompat.getColor(ctx, R.color.meta)

        val flags = IntArray(src.length)
        ranges?.forEach { r0 ->
            val r = r0.copyOf()
            Arabic.extendOverMarks(src, r)
            for (i in r[0] until minOf(r[1], src.length)) flags[i] = flags[i] or HL
        }
        if (notes) Notes.metaRanges(src).forEach { r ->
            val a = r[0]
            val b = minOf(r[1], src.length)
            for (i in a until b) flags[i] = flags[i] or META
            REF_NUM.findAll(src.substring(a, b)).forEach { m ->
                for (i in a + m.range.first..a + m.range.last) flags[i] = flags[i] or HIDE
            }
        }
        // tahridž, ocjene i navodi bez metna se ne prikazuju (aplikacija je za hifz)
        if (notes) Notes.hiddenRanges(src).forEach { r ->
            for (i in r[0] until minOf(r[1], src.length)) flags[i] = flags[i] or HIDE
        }
        var q = 0
        var p = 0
        for (i in src.indices) {
            val c = src[i]
            if (c == '﴿') q++
            if (c == '(') p++
            if (q > 0) flags[i] = flags[i] or QURAN
            if (p > 0) flags[i] = flags[i] or BUK
            if (c == 'ﷺ') flags[i] = flags[i] or SAW
            if (c == '﴾') q = maxOf(0, q - 1)
            if (c == ')') p = maxOf(0, p - 1)
        }

        val sb = SpannableStringBuilder()
        var start = 0
        for (i in 1..src.length) {
            if (i == src.length || flags[i] != flags[start]) {
                val fl = flags[start]
                if (fl and HIDE != 0) { start = i; continue }
                val outStart = sb.length
                appendWithRefs(sb, src.substring(start, i), cGold, refMap, onRef)
                val outEnd = sb.length
                if (outEnd > outStart) {
                    if (fl and HL != 0) sb.setSpan(BackgroundColorSpan(cMark), outStart, outEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    when {
                        fl and SAW != 0 -> sb.setSpan(ForegroundColorSpan(cGold), outStart, outEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        fl and QURAN != 0 -> sb.setSpan(ForegroundColorSpan(cQuran), outStart, outEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        fl and META != 0 -> sb.setSpan(ForegroundColorSpan(cMeta), outStart, outEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        fl and BUK != 0 -> sb.setSpan(ForegroundColorSpan(cBuk), outStart, outEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
                start = i
            }
        }
        return sb
    }

    private fun appendWithRefs(sb: SpannableStringBuilder, s: String, gold: Int, refMap: ((Int) -> Int)?, onRef: ((Int) -> Unit)?) {
        var last = 0
        for (m in REF.findAll(s)) {
            sb.append(s, last, m.range.first)
            val n = m.groupValues[1].toInt()
            last = m.range.last + 1
            // fusnota koja se ne prikazuje nema ni oznake u tekstu; ostale su prenumerisane
            val shown = refMap?.invoke(n) ?: n
            if (shown == 0) continue
            val a = sb.length
            sb.append("(").append(arabicDigits(shown)).append(")")
            val b = sb.length
            sb.setSpan(SuperscriptSpan(), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(RelativeSizeSpan(0.62f), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(StyleSpan(Typeface.BOLD), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (onRef != null) sb.setSpan(TapSpan(gold, true) { onRef(n) }, a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            else sb.setSpan(ForegroundColorSpan(gold), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        sb.append(s, last, s.length)
    }
}
