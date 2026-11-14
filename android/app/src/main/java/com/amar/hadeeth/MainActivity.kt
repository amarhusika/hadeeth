package com.amar.hadeeth

import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/** Početna: globalna pretraga, skok na hadis, zbirke. */
class MainActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val root = findViewById<View>(R.id.root)
        setupTopBar(findViewById(R.id.topbar), null)

        val title = SpannableStringBuilder("ابحث في أحاديث\n")
        val a = title.length
        title.append("الصحيحين والزوائد")
        title.setSpan(ForegroundColorSpan(color(R.color.gold)), a, title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        findViewById<TextView>(R.id.heroTitle).text = title

        findViewById<TextView>(R.id.legend).text = Rich.build(this, "ما كان بهذا اللون (…) فهو من أفراد البخاري، والحواشي تحت كل حديث كما في المطبوع.")

        Repo.load(this) { if (!isDestroyed) render() }
    }

    override fun onResume() {
        super.onResume()
        if (Repo.loaded) renderContinue()
    }

    private fun render() {
        findViewById<View>(R.id.loading).visibility = View.GONE
        findViewById<View>(R.id.body).visibility = View.VISIBLE
        val root = findViewById<View>(R.id.root)
        setupSearch(root, "", "ابحث عن أي حديث…") { q -> openSearch(q, null) }

        val box = findViewById<LinearLayout>(R.id.collections)
        box.removeAllViews()
        for (c in Repo.collections.values) {
            val v = inflate(R.layout.item_collection, box)
            v.findViewById<TextView>(R.id.collShort).text = c.short
            v.findViewById<TextView>(R.id.collTitle).text = c.title
            v.findViewById<TextView>(R.id.collAuthor).visibility = View.GONE
            val pct = if (c.total > 0) Math.round(c.count * 100f / c.total) else 0
            val done = c.count >= c.total
            v.findViewById<ProgressBar>(R.id.collProgress).apply { progress = maxOf(pct, 1); visibility = if (done) View.GONE else View.VISIBLE }
            v.findViewById<TextView>(R.id.collCount).text =
                if (done) "${arabicDigits(c.total)} حديثًا"
                else if (c.count > 0) "${arabicDigits(c.count)} من ${arabicDigits(c.total)} حديثًا مُدخلًا"
                else "${arabicDigits(c.total)} حديثًا — قيد الإدخال"
            v.setOnClickListener { openCollection(c.key) }
            box.addView(v)
        }
        renderContinue()
    }

    /** "تابع القراءة" — zadnji otvoreni hadis */
    private fun renderContinue() {
        val card = findViewById<View>(R.id.continueCard)
        val last = Prefs.last(this)
        val h = last?.let { Repo.find(it.first, it.second) }
        if (h == null) { card.visibility = View.GONE; return }
        val col = Repo.collections[h.c]
        card.visibility = View.VISIBLE
        findViewById<TextView>(R.id.continueTitle).text = "${col?.short ?: ""} · الحديث ${arabicDigits(h.n)}"
        findViewById<TextView>(R.id.continueBab).text = h.bab
        card.setOnClickListener { openHadith(h.c, h.n) }
    }
}
