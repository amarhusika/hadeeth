package com.amar.hadeeth

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

class HadeethApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(
            if (Prefs.dark(this)) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }
}

object Prefs {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("hadeeth", Context.MODE_PRIVATE)
    fun dark(ctx: Context) = sp(ctx).getBoolean("dark", false)
    fun setDark(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("dark", v).apply()
    fun last(ctx: Context): Pair<String, Int>? {
        val c = sp(ctx).getString("last_c", null) ?: return null
        val n = sp(ctx).getInt("last_n", 0)
        return if (n > 0) c to n else null
    }
    fun setLast(ctx: Context, c: String, n: Int) = sp(ctx).edit().putString("last_c", c).putInt("last_n", n).apply()
}

/** Zajedničko za sve ekrane: gornja traka, pretraga, skok na hadis, kartica hadisa. */
abstract class BaseActivity : AppCompatActivity() {

    protected val isNight: Boolean
        get() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isNight
            isAppearanceLightNavigationBars = !isNight
        }
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        val content = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
            val b = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime()
            )
            v.setPadding(b.left, b.top, b.right, b.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }

    protected fun color(id: Int) = ContextCompat.getColor(this, id)

    // ---------- navigacija ----------

    fun openHome() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }

    open fun openCollection(c: String) {
        startActivity(Intent(this, CollectionActivity::class.java).putExtra("c", c))
    }

    open fun openHadith(c: String, n: Int) {
        startActivity(Intent(this, HadithActivity::class.java).putExtra("c", c).putExtra("n", n))
    }

    open fun openSearch(q: String, c: String?) {
        startActivity(Intent(this, SearchActivity::class.java).putExtra("q", q).putExtra("c", c))
    }

    // ---------- gornja traka ----------

    protected fun setupTopBar(root: View, active: String?) {
        root.findViewById<View>(R.id.brand).setOnClickListener { if (this !is MainActivity) openHome() }
        val nav = mapOf("sahihayn" to root.findViewById<TextView>(R.id.navSahihayn), "zawaid" to root.findViewById<TextView>(R.id.navZawaid))
        for ((key, tv) in nav) {
            val on = key == active
            tv.setBackgroundResource(if (on) R.drawable.bg_pill_on else R.drawable.bg_pill)
            tv.setTextColor(color(if (on) R.color.on_accent else R.color.ink_soft))
            tv.setOnClickListener { if (!(on && this is CollectionActivity)) openCollection(key) }
        }
        root.findViewById<View>(R.id.gotoButton).setOnClickListener { showJumpDialog(active ?: "sahihayn") }
        // na uskim telefonima naziv pored ح ne stane uz dugmad (kao na webu na mobitelu)
        if (resources.configuration.screenWidthDp < 400) root.findViewById<View>(R.id.brandName).visibility = View.GONE
        val theme = root.findViewById<TextView>(R.id.themeToggle)
        // promjena dnevnog/noćnog režima samo na početnoj
        theme.visibility = if (this is MainActivity) View.VISIBLE else View.GONE
        theme.text = if (isNight) "☀" else "☾"
        theme.setOnClickListener {
            val dark = !isNight
            Prefs.setDark(this, dark)
            AppCompatDelegate.setDefaultNightMode(if (dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    // ---------- pretraga ----------

    protected fun setupSearch(root: View, initial: String, hint: String, onSubmit: (String) -> Unit): EditText {
        val input = root.findViewById<EditText>(R.id.searchInput)
        val clear = root.findViewById<View>(R.id.searchClear)
        val go = root.findViewById<View>(R.id.searchGo)
        input.hint = hint
        input.setText(initial)
        clear.visibility = if (initial.isEmpty()) View.GONE else View.VISIBLE
        input.addTextChangedListener(SimpleWatcher { clear.visibility = if (it.isEmpty()) View.GONE else View.VISIBLE })
        val submit = {
            val q = input.text.toString().trim()
            if (q.isNotEmpty()) { hideKeyboard(input); onSubmit(q) }
        }
        input.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) { submit(); true } else false
        }
        go.setOnClickListener { submit() }
        clear.setOnClickListener {
            input.setText("")
            input.requestFocus()
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(input, 0)
        }
        return input
    }

    protected fun hideKeyboard(v: View) {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(v.windowToken, 0)
        v.clearFocus()
    }

    // ---------- skok na hadis po broju (prozor iz gornje trake, kao "Idi na ajet" u tefsiru) ----------

    private fun showJumpDialog(initial: String) {
        if (!Repo.loaded) return
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        val v = LayoutInflater.from(this).inflate(R.layout.dialog_jump, null)
        v.findViewById<View>(R.id.jumpLabel).visibility = View.GONE
        v.findViewById<View>(R.id.jumpClose).setOnClickListener { dialog.dismiss() }
        setupJump(v, initial) { c, n -> dialog.dismiss(); openHadith(c, n) }
        dialog.setContentView(v)
        dialog.setCanceledOnTouchOutside(true)
        val dm = resources.displayMetrics
        dialog.window?.let { w ->
            w.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            w.setLayout(minOf(dm.widthPixels - (28 * dm.density).toInt(), (460 * dm.density).toInt()), android.view.WindowManager.LayoutParams.WRAP_CONTENT)
            w.setGravity(android.view.Gravity.CENTER)
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                w.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                w.attributes = w.attributes.apply { blurBehindRadius = 24 }
            }
            w.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            w.setDimAmount(if (isNight) 0.55f else 0.35f)
            // tastatura odmah za upis broja
            w.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        }
        v.findViewById<EditText>(R.id.jumpNumber).requestFocus()
        dialog.show()
    }

    protected fun setupJump(root: View, initial: String?, onGo: (String, Int) -> Unit) {
        val cols = Repo.collections.values.toList()
        val spinner = root.findViewById<Spinner>(R.id.jumpCollection)
        val input = root.findViewById<EditText>(R.id.jumpNumber)
        val go = root.findViewById<TextView>(R.id.jumpGo)
        val err = root.findViewById<TextView>(R.id.jumpError)

        val adapter = ArrayAdapter(this, R.layout.item_spinner, cols.map { it.short })
        adapter.setDropDownViewResource(R.layout.item_spinner_dropdown)
        spinner.adapter = adapter
        val startIdx = cols.indexOfFirst { it.key == initial }.coerceAtLeast(0)
        spinner.setSelection(startIdx)

        fun current() = cols.getOrNull(spinner.selectedItemPosition)
        fun parsed(): Int? {
            val d = latinDigits(input.text.toString().trim())
            return if (d.isNotEmpty() && d.all { it in '0'..'9' } && d.length < 7) d.toInt() else null
        }
        fun refresh() {
            val col = current() ?: return
            input.hint = "١ – " + arabicDigits(col.total)
            val raw = input.text.toString()
            val n = parsed()
            val valid = n != null && n >= 1 && n <= col.total
            go.isEnabled = valid
            go.alpha = if (valid) 1f else 0.45f
            if (raw.isNotEmpty() && !valid) {
                err.text = "الرقم يجب أن يكون بين ١ و" + arabicDigits(col.total)
                err.visibility = View.VISIBLE
            } else err.visibility = View.GONE
        }
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = refresh()
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        input.addTextChangedListener(SimpleWatcher { refresh() })
        val goNow = {
            val col = current()
            val n = parsed()
            if (col != null && n != null && n in 1..col.total) {
                hideKeyboard(input)
                input.setText("")
                onGo(col.key, n)
            }
        }
        input.setOnEditorActionListener { _, _, _ -> goNow(); true }
        go.setOnClickListener { goNow() }
        refresh()
    }

    // ---------- kartica hadisa ----------

    /**
     * Popunjava item_hadith. standalone = stranica hadisa (bez dugmeta "صفحة الحديث", fusnote klikabilne).
     */
    protected fun bindCard(
        card: View, h: Hadith, marks: Map<String, List<IntArray>> = emptyMap(),
        standalone: Boolean = false,
    ): List<TextView> {
        val col = Repo.collections[h.c]
        val num = card.findViewById<TextView>(R.id.num)
        num.text = arabicDigits(h.n)
        num.setOnClickListener { if (!standalone) openHadith(h.c, h.n) }
        card.findViewById<TextView>(R.id.kitab).text = h.kitab
        card.findViewById<TextView>(R.id.bab).text = Rich.build(this, h.bab, marks["bab"])

        val matn = card.findViewById<TextView>(R.id.matn)
        // klik na (n) u tekstu otvara fusnotu u ploči odozdo
        // fusnote koje su samo tahridž/navod se ne prikazuju; ostale se numerišu redom
        val shownNo = shownNumbers(h)
        matn.text = Rich.build(this, h.text, marks["text"], refMap = { n -> shownNo.getOrElse(n - 1) { 0 } }) { n ->
            showFootnote(h, n, shownNo.getOrElse(n - 1) { n }, marks)
        }
        matn.movementMethod = LinkMovementMethod.getInstance()

        val fnBox = card.findViewById<LinearLayout>(R.id.footnotes)
        fnBox.removeAllViews()
        val fnViews = ArrayList<TextView>()
        fnBox.visibility = if (shownNo.none { it > 0 }) View.GONE else View.VISIBLE
        card.findViewById<View>(R.id.fnRule).visibility = fnBox.visibility
        val inflater = LayoutInflater.from(this)
        h.footnotes.forEachIndexed { i, f ->
            if (shownNo[i] == 0) return@forEachIndexed
            val tv = inflater.inflate(R.layout.item_footnote, fnBox, false) as TextView
            val sb = android.text.SpannableStringBuilder()
            val label = "(" + arabicDigits(shownNo[i]) + ") "
            sb.append(label)
            sb.setSpan(android.text.style.ForegroundColorSpan(color(R.color.gold)), 0, label.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, label.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.append(Rich.build(this, f, marks["fn$i"], notes = true))
            tv.text = sb
            fnBox.addView(tv)
            fnViews += tv
        }

        val tag = card.findViewById<TextView>(R.id.takhrij)
        tag.visibility = if (h.takhrij.isEmpty()) View.GONE else View.VISIBLE
        tag.text = takhrijLabel(h.takhrij)
        card.findViewById<TextView>(R.id.src).text =
            "${col?.short ?: ""} · ج${arabicDigits(h.volume)} · ص${arabicDigits(h.page)}"

        val copy = card.findViewById<TextView>(R.id.copy)
        copy.text = "نسخ"
        copy.setOnClickListener {
            copyHadith(h, col)
            copy.text = "نُسِخ ✓"
            copy.postDelayed({ copy.text = "نسخ" }, 1600)
        }
        val open = card.findViewById<View>(R.id.open)
        open.visibility = if (standalone) View.GONE else View.VISIBLE
        open.setOnClickListener { openHadith(h.c, h.n) }
        return fnViews
    }

    /** broj pod kojim se fusnota prikazuje (0 = ne prikazuje se) */
    private fun shownNumbers(h: Hadith): IntArray {
        var k = 0
        return IntArray(h.footnotes.size) { i -> if (Notes.hasVisible(h.footnotes[i])) ++k else 0 }
    }

    /**
     * Prozor s fusnotom na sredini ekrana, iste širine kao kartica s tekstom hadisa.
     * Ostatak ekrana se blago zamuti (Android 12+) i zatamni. Zatvara se na ×, dodirom van prozora ili "nazad".
     */
    private fun showFootnote(h: Hadith, n: Int, shown: Int, marks: Map<String, List<IntArray>>) {
        val text = h.footnotes.getOrNull(n - 1) ?: return
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        val v = LayoutInflater.from(this).inflate(R.layout.dialog_footnote, null)
        v.findViewById<TextView>(R.id.fnTitle).text = "(" + arabicDigits(shown) + ")"
        v.findViewById<TextView>(R.id.fnText).text = Rich.build(this, text, marks["fn${n - 1}"], notes = true)
        v.findViewById<View>(R.id.fnClose).setOnClickListener { dialog.dismiss() }
        dialog.setContentView(v)
        dialog.setCanceledOnTouchOutside(true)

        val dm = resources.displayMetrics
        val width = dm.widthPixels - (28 * dm.density).toInt() // kartica ima po 14dp sa strane
        val maxHeight = (dm.heightPixels * 0.76f).toInt()
        // duga fusnota: prozor najviše ~3/4 ekrana, a tekst se lista unutar njega
        v.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val tall = v.measuredHeight > maxHeight
        if (tall) {
            val scroll = v.findViewById<View>(R.id.fnScroll)
            scroll.layoutParams = (scroll.layoutParams as LinearLayout.LayoutParams).apply { height = 0; weight = 1f }
        }
        dialog.window?.let { w ->
            w.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            w.setLayout(width, if (tall) maxHeight else android.view.WindowManager.LayoutParams.WRAP_CONTENT)
            w.setGravity(android.view.Gravity.CENTER)
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                w.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                w.attributes = w.attributes.apply { blurBehindRadius = 24 }
            }
            w.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            w.setDimAmount(if (isNight) 0.55f else 0.35f)
        }
        dialog.show()
    }

    private fun copyHadith(h: Hadith, col: Collection?) {
        // kopira se ono što se vidi: bez tahridža, s prenumerisanim fusnotama
        val shownNo = shownNumbers(h)
        val text = h.text.replace(Regex("\\{(\\d+)\\}")) {
            val k = shownNo.getOrElse(it.groupValues[1].toInt() - 1) { 0 }
            if (k > 0) "(" + arabicDigits(k) + ")" else ""
        }
        val clean = { f: String -> Notes.maskHidden(f).replace(Regex("[ \\t]+"), " ").replace(Regex(" *\\n *"), "\n").trim() }
        val fns = h.footnotes.mapIndexedNotNull { i, f -> if (shownNo[i] > 0) "(" + arabicDigits(shownNo[i]) + ") " + clean(f) else null }
            .joinToString("\n")
        val txt = buildString {
            append(h.bab).append('\n')
            append(arabicDigits(h.n)).append(" - ").append(text).append('\n')
            if (fns.isNotEmpty()) append("────────\n").append(fns).append('\n')
            append("[").append(col?.title ?: "").append("، ").append(arabicDigits(h.n)).append("]")
        }
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("حديث", txt))
    }

    protected fun inflate(id: Int, parent: ViewGroup?): View = LayoutInflater.from(this).inflate(id, parent, false)
}

class SimpleWatcher(private val f: (String) -> Unit) : android.text.TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    override fun afterTextChanged(s: android.text.Editable?) = f(s?.toString() ?: "")
}
