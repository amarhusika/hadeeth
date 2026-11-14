package com.amar.hadeeth

import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView

/** Rezultati pretrage — globalno ili unutar jedne zbirke (extra "c"). */
class SearchActivity : BaseActivity() {

    private var q = ""
    private var c: String? = null
    private var results: List<Result> = emptyList()
    private lateinit var list: ListView
    private lateinit var header: View
    private lateinit var footer: TextView
    private var seq = 0

    private val adapter = object : BaseAdapter() {
        override fun getCount() = minOf(results.size, LIMIT)
        override fun getItem(position: Int) = results[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v = convertView ?: inflate(R.layout.item_hadith, parent)
            val r = results[position]
            bindCard(v, r.h, r.marks)
            return v
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        q = intent.getStringExtra("q") ?: ""
        c = intent.getStringExtra("c")
        list = findViewById(R.id.list)
        Repo.load(this) { if (!isDestroyed) render() }
    }

    private fun render() {
        findViewById<View>(R.id.loading).visibility = View.GONE
        header = inflate(R.layout.header_search, list)
        footer = inflate(R.layout.item_note, list) as TextView
        list.addHeaderView(header, null, false)
        list.addFooterView(footer, null, false)
        list.adapter = adapter
        setupTopBar(findViewById(R.id.topbar), c)

        val col = c?.let { Repo.collections[it] }
        val crumb = header.findViewById<TextView>(R.id.crumb)
        if (col != null) {
            val sb = SpannableStringBuilder()
            appendLink(sb, "الرئيسة") { openHome() }
            sb.append("  ‹  ")
            appendLink(sb, col.short) { openCollection(col.key) }
            crumb.text = sb
            crumb.movementMethod = android.text.method.LinkMovementMethod.getInstance()
            crumb.visibility = View.VISIBLE
        } else crumb.visibility = View.GONE

        val hint = if (col != null) "ابحث عن أي حديث في ${col.short}…" else "ابحث عن أي حديث…"
        setupSearch(header, q, hint) { nq -> q = nq; runSearch() }
        runSearch()
    }

    private fun appendLink(sb: SpannableStringBuilder, text: String, onTap: () -> Unit) {
        val a = sb.length
        sb.append(text)
        sb.setSpan(TapSpan(color(R.color.green2)) { onTap() }, a, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun runSearch() {
        val my = ++seq
        val head = header.findViewById<TextView>(R.id.resHead)
        val per = header.findViewById<TextView>(R.id.resPer)
        head.text = "جارٍ البحث…"
        per.visibility = View.GONE
        footer.visibility = View.GONE
        results = emptyList()
        adapter.notifyDataSetChanged()
        Repo.withIndex { index ->
            if (isDestroyed || my != seq) return@withIndex
            results = Search.search(index, q, c)
            showResults()
        }
    }

    private fun showResults() {
        val col = c?.let { Repo.collections[it] }
        val scope = if (col != null) " في ${col.short}" else ""
        val head = header.findViewById<TextView>(R.id.resHead)
        val per = header.findViewById<TextView>(R.id.resPer)
        if (results.isNotEmpty()) {
            val sb = SpannableStringBuilder("عُثِر على ")
            val a = sb.length
            sb.append(arabicDigits(results.size))
            sb.setSpan(ForegroundColorSpan(color(R.color.gold)), a, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(StyleSpan(Typeface.BOLD), a, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.append(" حديثًا لـ «").append(q).append("»").append(scope)
            head.text = sb
            if (col == null) {
                per.text = Repo.collections.values.mapNotNull { cl ->
                    val n = results.count { it.h.c == cl.key }
                    if (n > 0) "${cl.short}: ${arabicDigits(n)}" else null
                }.joinToString(" · ")
                per.visibility = View.VISIBLE
            }
            if (results.size > LIMIT) {
                footer.text = "عُرضت أول ١٥٠ نتيجة — زِد كلمات البحث لتضييق النتائج."
                footer.visibility = View.VISIBLE
            }
        } else {
            head.text = "لا نتائج لـ «$q»$scope"
            footer.text = "البحث يطابق النص حرفيًّا وبنفس ترتيب الكلمات (دون اعتبار الحركات)."
            footer.visibility = View.VISIBLE
        }
        adapter.notifyDataSetChanged()
        list.setSelection(0)
    }

    companion object { const val LIMIT = 150 }
}
