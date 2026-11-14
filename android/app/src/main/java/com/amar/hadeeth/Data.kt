package com.amar.hadeeth

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

data class Hadith(
    val c: String,          // ključ zbirke: "sahihayn" | "zawaid"
    val n: Int,             // broj hadisa kao u štampanom izdanju
    val volume: Int,
    val kitab: String,
    val bab: String,
    val page: Int,
    val text: String,       // {1}, {2}… su oznake fusnota na mjestu kao u knjizi
    val footnotes: List<String>,
    val takhrij: List<String>,
)

data class Collection(
    val key: String,
    val title: String,
    val short: String,
    val author: String,
    val total: Int,
    val count: Int,
)

/** Učitava assets/data/hadith.json (isti fajl kao public/data/hadith.json na webu). */
object Repo {
    @Volatile var loaded = false
        private set
    var collections: LinkedHashMap<String, Collection> = LinkedHashMap()
        private set
    var hadiths: List<Hadith> = emptyList()
        private set
    private var byCollection: Map<String, List<Hadith>> = emptyMap()
    @Volatile private var index: List<Indexed>? = null

    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun load(ctx: Context, done: () -> Unit) {
        if (loaded) { done(); return }
        val app = ctx.applicationContext
        worker.execute {
            if (!loaded) parse(app)
            main.post(done)
        }
    }

    /** Indeks za pretragu gradi se jednom, u pozadini. */
    fun withIndex(done: (List<Indexed>) -> Unit) {
        val ix = index
        if (ix != null) { done(ix); return }
        worker.execute {
            val built = index ?: Search.buildIndex(hadiths).also { index = it }
            main.post { done(built) }
        }
    }

    fun list(c: String): List<Hadith> = byCollection[c] ?: emptyList()
    fun find(c: String, n: Int): Hadith? = list(c).firstOrNull { it.n == n }

    private fun strings(a: JSONArray?): List<String> =
        if (a == null) emptyList() else List(a.length()) { a.getString(it) }

    private fun parse(ctx: Context) {
        val json = ctx.assets.open("data/hadith.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val root = JSONObject(json)
        val cols = root.getJSONObject("collections")
        val map = LinkedHashMap<String, Collection>()
        val keys = cols.keys().asSequence().toList()
        val ordered = listOf("sahihayn", "zawaid").filter { it in keys } + keys.filter { it != "sahihayn" && it != "zawaid" }
        for (k in ordered) {
            val o = cols.getJSONObject(k)
            map[k] = Collection(k, o.getString("title"), o.getString("short"), o.optString("author"),
                o.getInt("total"), o.optInt("count"))
        }
        val arr = root.getJSONArray("hadiths")
        val list = ArrayList<Hadith>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list += Hadith(
                c = o.getString("c"),
                n = o.getInt("n"),
                volume = o.optInt("volume"),
                kitab = o.optString("kitab"),
                bab = o.optString("bab"),
                page = o.optInt("page"),
                text = o.optString("text"),
                footnotes = strings(o.optJSONArray("footnotes")),
                takhrij = strings(o.optJSONArray("takhrij")),
            )
        }
        collections = map
        hadiths = list
        byCollection = list.groupBy { it.c }
        loaded = true
    }
}
