package com.amar.hadeeth

import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.BackgroundColorSpan
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView

/** Zbirka: sadržaj (kitabi, babovi, brojevi hadisa) i pretraga unutar zbirke. */
class CollectionActivity : BaseActivity() {

    private sealed class Row {
        class Kitab(val title: String) : Row()
        class Bab(val bab: String, val nums: List<Int>, val last: Boolean) : Row()
    }

    private lateinit var c: String
    private var rows: List<Row> = emptyList()

    private val adapter = object : BaseAdapter() {
        override fun getCount() = rows.size
        override fun getItem(position: Int): Any = rows[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getViewTypeCount() = 2
        override fun getItemViewType(position: Int) = if (rows[position] is Row.Kitab) 0 else 1
        override fun isEnabled(position: Int) = false
        override fun areAllItemsEnabled() = false

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            return when (val r = rows[position]) {
                is Row.Kitab -> {
                    val v = (convertView ?: inflate(R.layout.item_kitab, parent)) as TextView
                    v.text = r.title
                    v
                }
                is Row.Bab -> {
                    val v = convertView ?: inflate(R.layout.item_bab, parent)
                    v.findViewById<TextView>(R.id.babName).text = r.bab
                    val nums = v.findViewById<TextView>(R.id.babNums)
                    nums.text = numberLinks(r.nums)
                    nums.movementMethod = LinkMovementMethod.getInstance()
                    v.findViewById<View>(R.id.babDivider).visibility = if (r.last) View.GONE else View.VISIBLE
                    v
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        c = intent.getStringExtra("c") ?: "sahihayn"
        Repo.load(this) { if (!isDestroyed) render() }
    }

    override fun openCollection(c: String) {
        if (c == this.c) return
        super.openCollection(c)
    }

    private fun render() {
        findViewById<View>(R.id.loading).visibility = View.GONE
        val col = Repo.collections[c] ?: run { finish(); return }
        val list = findViewById<ListView>(R.id.list)
        val header = inflate(R.layout.header_collection, list)
        list.addHeaderView(header, null, false)
        setupTopBar(findViewById(R.id.topbar), c)

        val crumb = header.findViewById<TextView>(R.id.crumb)
        val sb = SpannableStringBuilder()
        val a = sb.length
        sb.append("الرئيسة")
        sb.setSpan(TapSpan(color(R.color.green2)) { openHome() }, a, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.append("  ‹  ").append(col.short)
        crumb.text = sb
        crumb.movementMethod = LinkMovementMethod.getInstance()

        header.findViewById<TextView>(R.id.collTitle).text = col.title
        header.findViewById<TextView>(R.id.collMeta).text =
            "${col.author} · " + (if (col.count >= col.total) "${arabicDigits(col.total)} حديثًا" else "${arabicDigits(col.count)} من ${arabicDigits(col.total)} حديثًا مُدخلًا")
        setupSearch(header, "", "ابحث عن أي حديث في ${col.short}…") { q -> openSearch(q, c) }

        // stablo: kitab → bab → brojevi
        val out = ArrayList<Row>()
        var kitab: String? = null
        var bab: String? = null
        var nums = ArrayList<Int>()
        fun flushBab(last: Boolean) {
            if (bab != null && nums.isNotEmpty()) out += Row.Bab(bab!!, nums, last)
            nums = ArrayList()
        }
        for (h in Repo.list(c)) {
            if (h.kitab != kitab) {
                flushBab(true)
                kitab = h.kitab; bab = h.bab
                out += Row.Kitab(h.kitab)
            } else if (h.bab != bab) {
                flushBab(false)
                bab = h.bab
            }
            nums.add(h.n)
        }
        flushBab(true)
        rows = out

        if (rows.isEmpty()) {
            val note = inflate(R.layout.item_note, list) as TextView
            note.text = "هذا الكتاب قيد الإدخال، ولم تُضف أحاديثه بعد."
            list.addFooterView(note, null, false)
        }
        list.adapter = adapter
    }

    private fun numberLinks(nums: List<Int>): CharSequence {
        val sb = SpannableStringBuilder()
        val bg = color(R.color.chip)
        val ink = color(R.color.ink)
        nums.forEachIndexed { i, n ->
            if (i > 0) sb.append("   ")
            val a = sb.length
            sb.append(" ").append(arabicDigits(n)).append(" ")
            sb.setSpan(BackgroundColorSpan(bg), a, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(TapSpan(ink) { openHadith(c, n) }, a, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return sb
    }
}
