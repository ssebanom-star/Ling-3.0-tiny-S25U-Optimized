package io.github.ssebanom.ling.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.jsoup.Connection
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLDecoder
import java.util.Base64

data class SearchHit(val title: String, val url: String, val snippet: String)

/** 검색 제공자. 봇 차단·빈 결과는 [Blocked] 로 알려 다음 제공자로 넘어간다 */
interface SearchProvider {
    val name: String
    fun search(query: String, n: Int): List<SearchHit>
    class Blocked(provider: String, why: String) : Exception("$provider: $why")
}

internal const val MOBILE_UA =
    "Mozilla/5.0 (Linux; Android 15; SM-S938N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

private fun connect(url: String): Connection = Jsoup.connect(url)
    .userAgent(MOBILE_UA)
    .header("Accept-Language", "ko-KR,ko;q=0.9,en;q=0.8")
    .timeout(15_000)
    .followRedirects(true)
    .ignoreHttpErrors(true)
    .maxBodySize(3 shl 20)

/** 검색 결과 HTML 파서 (네트워크 없이 단위 테스트) */
object SearchParsers {
    /** DuckDuckGo 리다이렉트(//duckduckgo.com/l/?uddg=...) → 실제 URL */
    fun unwrapDdg(href: String): String {
        val i = href.indexOf("uddg=")
        if (i < 0) return if (href.startsWith("//")) "https:$href" else href
        val enc = href.substring(i + 5).substringBefore('&')
        return URLDecoder.decode(enc, "UTF-8")
    }

    /** Bing 리다이렉트(/ck/a?...&u=a1<base64url>) → 실제 URL */
    fun unwrapBing(href: String): String {
        if (!href.contains("bing.com/ck/a")) return href
        val u = Regex("[?&]u=a1([^&]+)").find(href)?.groupValues?.get(1) ?: return href
        return runCatching { String(Base64.getUrlDecoder().decode(u.padEnd((u.length + 3) / 4 * 4, '='))) }.getOrDefault(href)
    }

    fun isDdgChallenge(doc: Document): Boolean =
        doc.text().contains("bots use DuckDuckGo", ignoreCase = true) || doc.selectFirst("form#challenge-form, .anomaly-modal") != null

    fun ddgLite(doc: Document, n: Int): List<SearchHit> {
        val links = doc.select("a.result-link")
        val snippets = doc.select("td.result-snippet")
        return links.take(n).mapIndexed { i, a ->
            SearchHit(a.text(), unwrapDdg(a.attr("href")), snippets.getOrNull(i)?.text().orEmpty())
        }.filter { it.url.startsWith("http") }
    }

    fun ddgHtml(doc: Document, n: Int): List<SearchHit> =
        doc.select("div.result:not(.result--ad)").mapNotNull { r ->
            val a = r.selectFirst("a.result__a") ?: return@mapNotNull null
            SearchHit(a.text(), unwrapDdg(a.attr("href")), r.selectFirst(".result__snippet")?.text().orEmpty())
        }.filter { it.url.startsWith("http") }.take(n)

    fun bing(doc: Document, n: Int): List<SearchHit> =
        doc.select("li.b_algo").mapNotNull { r ->
            val a = r.selectFirst("h2 a[href], .b_algoheader a[href]") ?: return@mapNotNull null
            val title = r.selectFirst("h2")?.text().orEmpty().ifEmpty { a.text() }
            SearchHit(title, unwrapBing(a.attr("href")), r.selectFirst(".b_caption p, p")?.text().orEmpty())
        }.filter { it.url.startsWith("http") }.take(n)

    fun searxng(json: String, n: Int): List<SearchHit> {
        val arr = JSONObject(json).optJSONArray("results") ?: return emptyList()
        return (0 until minOf(n, arr.length())).map { i ->
            val o = arr.getJSONObject(i)
            SearchHit(o.optString("title"), o.optString("url"), o.optString("content"))
        }
    }

    fun brave(json: String, n: Int): List<SearchHit> {
        val arr = JSONObject(json).optJSONObject("web")?.optJSONArray("results") ?: return emptyList()
        return (0 until minOf(n, arr.length())).map { i ->
            val o = arr.getJSONObject(i)
            SearchHit(o.optString("title"), o.optString("url"), Jsoup.parse(o.optString("description")).text())
        }
    }

    fun format(provider: String, query: String, hits: List<SearchHit>): String = buildString {
        append("Search results for \"").append(query).append("\" (via ").append(provider).append("):\n")
        hits.forEachIndexed { i, h ->
            append(i + 1).append(". ").append(h.title).append('\n')
            append("   ").append(h.url).append('\n')
            if (h.snippet.isNotBlank()) append("   ").append(h.snippet.take(300)).append('\n')
        }
        append("Use fetch_url on a result URL to read the page.")
    }
}

class DdgLiteProvider : SearchProvider {
    override val name = "DuckDuckGo"
    override fun search(query: String, n: Int): List<SearchHit> {
        val res = connect("https://lite.duckduckgo.com/lite/").data("q", query).data("kl", "kr-kr").method(Connection.Method.GET).execute()
        val doc = res.parse()
        if (res.statusCode() == 202 || SearchParsers.isDdgChallenge(doc)) throw SearchProvider.Blocked(name, "bot challenge")
        return SearchParsers.ddgLite(doc, n).ifEmpty { throw SearchProvider.Blocked(name, "no results (HTTP ${res.statusCode()})") }
    }
}

class DdgHtmlProvider : SearchProvider {
    override val name = "DuckDuckGo(html)"
    override fun search(query: String, n: Int): List<SearchHit> {
        val res = connect("https://html.duckduckgo.com/html/").data("q", query).data("kl", "kr-kr").method(Connection.Method.POST).execute()
        val doc = res.parse()
        if (res.statusCode() == 202 || SearchParsers.isDdgChallenge(doc)) throw SearchProvider.Blocked(name, "bot challenge")
        return SearchParsers.ddgHtml(doc, n).ifEmpty { throw SearchProvider.Blocked(name, "no results (HTTP ${res.statusCode()})") }
    }
}

class BingProvider : SearchProvider {
    override val name = "Bing"
    override fun search(query: String, n: Int): List<SearchHit> {
        val res = connect("https://www.bing.com/search").data("q", query).data("setlang", "ko").method(Connection.Method.GET).execute()
        return SearchParsers.bing(res.parse(), n).ifEmpty { throw SearchProvider.Blocked(name, "no results (HTTP ${res.statusCode()})") }
    }
}

class SearxngProvider(private val base: String) : SearchProvider {
    override val name = "SearXNG"
    override fun search(query: String, n: Int): List<SearchHit> {
        val res = connect(base.trimEnd('/') + "/search").data("q", query).data("format", "json")
            .ignoreContentType(true).method(Connection.Method.GET).execute()
        if (res.statusCode() != 200) throw SearchProvider.Blocked(name, "HTTP ${res.statusCode()} (JSON 형식이 꺼진 인스턴스일 수 있음)")
        return SearchParsers.searxng(res.body(), n).ifEmpty { throw SearchProvider.Blocked(name, "no results") }
    }
}

class BraveProvider(private val key: String) : SearchProvider {
    override val name = "Brave Search"
    override fun search(query: String, n: Int): List<SearchHit> {
        val res = connect("https://api.search.brave.com/res/v1/web/search").data("q", query).data("count", n.toString())
            .header("X-Subscription-Token", key).header("Accept", "application/json")
            .ignoreContentType(true).method(Connection.Method.GET).execute()
        if (res.statusCode() != 200) throw SearchProvider.Blocked(name, "HTTP ${res.statusCode()}")
        return SearchParsers.brave(res.body(), n).ifEmpty { throw SearchProvider.Blocked(name, "no results") }
    }
}

class WebSearchTool(private val providers: () -> List<SearchProvider>) : LocalTool {
    override val name = "web_search"
    override val group = ToolGroup.WEB
    override val description =
        "Search the internet. Returns titles, URLs and snippets. Use for recent events, facts you are unsure about, prices, weather, news."
    override val properties = linkedMapOf<String, Any?>(
        "query" to prop("string", "search query (Korean or English)"),
        "max_results" to prop("integer", "number of results, 1-10 (default 5)"),
    )
    override val required = listOf("query")

    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        val q = args.str("query")
        val n = args.int("max_results", 5).coerceIn(1, 10)
        val errors = ArrayList<String>()
        for (p in providers()) {
            try {
                return@withContext SearchParsers.format(p.name, q, p.search(q, n))
            } catch (e: Exception) {
                errors += e.message ?: e.javaClass.simpleName
            }
        }
        "error: all search providers failed: ${errors.joinToString("; ")}. " +
            "(사용자는 설정에서 Brave Search API 키나 SearXNG 주소를 넣을 수 있음)"
    }
}

/** 웹 페이지를 읽기 쉬운 텍스트로 (스크립트·내비게이션 제거, 문단 유지) */
object PageText {
    private val DROP = "script, style, noscript, svg, nav, footer, header, aside, form, iframe, button, [aria-hidden=true], .ad, .ads, .advert"
    private val BLOCKS = setOf("p", "h1", "h2", "h3", "h4", "h5", "h6", "li", "pre", "blockquote", "td", "th", "dd", "dt", "figcaption")

    fun extract(doc: Document): String {
        doc.select(DROP).remove()
        val root: Element = doc.selectFirst("article") ?: doc.selectFirst("main") ?: doc.selectFirst("[role=main]") ?: doc.body() ?: return ""
        val sb = StringBuilder()
        val title = doc.title().trim()
        if (title.isNotEmpty()) sb.append("# ").append(title).append("\n\n")
        val blocks = root.select(BLOCKS.joinToString(", "))
            .filter { el -> el.parents().none { it.tagName() in BLOCKS } } // 중첩 블록 중복 제거
        if (blocks.isEmpty()) {
            sb.append(root.text())
        } else {
            for (b in blocks) {
                val t = b.text().trim()
                if (t.isEmpty()) continue
                when {
                    b.tagName().startsWith("h") -> sb.append("\n## ").append(t).append('\n')
                    b.tagName() == "li" -> sb.append("- ").append(t).append('\n')
                    else -> sb.append(t).append('\n')
                }
            }
        }
        return sb.toString().replace(Regex("\n{3,}"), "\n\n").trim()
    }
}

class FetchUrlTool : LocalTool {
    override val name = "fetch_url"
    override val group = ToolGroup.WEB
    override val description = "Download a web page and return its main text. Use start to read further parts of a long page."
    override val properties = linkedMapOf<String, Any?>(
        "url" to prop("string", "http(s) URL"),
        "start" to prop("integer", "character offset to start from (default 0)"),
        "max_chars" to prop("integer", "max characters to return (default 4000, max 8000)"),
    )
    override val required = listOf("url")

    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        val url = args.str("url").let { if (it.startsWith("http")) it else "https://$it" }
        val scheme = runCatching { URI(url).scheme }.getOrNull()
        if (scheme != "http" && scheme != "https") throw ToolError("only http/https URLs are allowed")
        val start = args.int("start", 0).coerceAtLeast(0)
        val max = args.int("max_chars", 4000).coerceIn(500, 8000)
        val res = connect(url).ignoreContentType(true).execute()
        if (res.statusCode() >= 400) throw ToolError("HTTP ${res.statusCode()} for $url")
        val type = res.contentType().orEmpty()
        val text = when {
            type.contains("html") || type.isEmpty() -> PageText.extract(res.parse())
            type.startsWith("text/") || type.contains("json") || type.contains("xml") -> res.body()
            else -> throw ToolError("unsupported content type: $type")
        }
        val end = minOf(text.length, start + max)
        if (start >= text.length) return@withContext "(end of page; total ${text.length} chars)"
        val more = if (end < text.length) "\n…(more: call fetch_url with start=$end; total ${text.length} chars)" else ""
        "URL: ${res.url()}\n" + text.substring(start, end) + more
    }
}
