package com.amar.hadeeth

import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.ScrollView
import android.widget.TextView

/** Stranica jednog hadisa (bez pretrage), s prethodnim/sljedećim hadisom. */
class HadithActivity : BaseActivity() {

    private lateinit var c: String
    private var n = 1
    private var fnViews: List<TextView> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hadith)
        c = intent.getStringExtra("c") ?: "sahihayn"
        n = savedInstanceState?.getInt("n") ?: intent.getIntExtra("n", 1)
        Repo.load(this) { if (!isDestroyed) render() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("n", n)
    }

    /** unutar iste zbirke ostajemo na istom ekranu */
    override fun openHadith(c: String, n: Int) {
        if (c == this.c) { this.n = n; show() } else super.openHadith(c, n)
    }

    private fun render() {
        findViewById<View>(R.id.loading).visibility = View.GONE
        findViewById<View>(R.id.body).visibility = View.VISIBLE
        val root = findViewById<View>(R.id.root)
        setupTopBar(findViewById(R.id.topbar), c)
        show()
    }

    private fun show() {
        val col = Repo.collections[c] ?: run { finish(); return }
        val list = Repo.list(c)
        val i = list.indexOfFirst { it.n == n }
        val h = list.getOrNull(i)

        val crumb = findViewById<TextView>(R.id.crumb)
        val sb = SpannableStringBuilder()
        link(sb, "الرئيسة") { openHome() }
        sb.append("  ‹  ")
        link(sb, col.short) { openCollection(c) }
        if (h != null) sb.append("  ‹  ").append(h.kitab)
        crumb.text = sb
        crumb.movementMethod = LinkMovementMethod.getInstance()

        val card = findViewById<View>(R.id.card)
        val empty = findViewById<TextView>(R.id.empty)
        val scroll = findViewById<ScrollView>(R.id.scroll)
        if (h != null) {
            card.visibility = View.VISIBLE
            empty.visibility = View.GONE
            fnViews = bindCard(card, h, standalone = true)
            Prefs.setLast(this, c, n)
        } else {
            card.visibility = View.GONE
            empty.visibility = View.VISIBLE
            empty.text = if (n < 1 || n > col.total)
                "لا يوجد حديث بهذا الرقم؛ أحاديث ${col.short} من ١ إلى ${arabicDigits(col.total)}."
            else "الحديث رقم ${arabicDigits(n)} من ${col.short} لم يُدخل بعد."
        }

        // prethodni / sljedeći (prvi uneseni prije i poslije ovog broja)
        val prev = if (i >= 0) list.getOrNull(i - 1) else list.lastOrNull { it.n < n }
        val next = if (i >= 0) list.getOrNull(i + 1) else list.firstOrNull { it.n > n }
        val bPrev = findViewById<TextView>(R.id.prev)
        val bNext = findViewById<TextView>(R.id.next)
        bPrev.visibility = if (prev != null) View.VISIBLE else View.INVISIBLE
        bNext.visibility = if (next != null) View.VISIBLE else View.INVISIBLE
        prev?.let { p -> bPrev.text = "→ الحديث ${arabicDigits(p.n)}"; bPrev.setOnClickListener { openHadith(c, p.n) } }
        next?.let { x -> bNext.text = "الحديث ${arabicDigits(x.n)} ←"; bNext.setOnClickListener { openHadith(c, x.n) } }

        scroll.post { scroll.scrollTo(0, 0) }
    }

    private fun link(sb: SpannableStringBuilder, text: String, onTap: () -> Unit) {
        val a = sb.length
        sb.append(text)
        sb.setSpan(TapSpan(color(R.color.green2)) { onTap() }, a, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}
