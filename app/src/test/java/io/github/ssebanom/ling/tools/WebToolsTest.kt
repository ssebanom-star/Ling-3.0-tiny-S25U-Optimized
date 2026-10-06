package io.github.ssebanom.ling.tools

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 실제 응답을 저장한 fixture 로 파서 검증(네트워크 없음) */
class WebToolsTest {
    private fun res(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    @Test fun ddgLiteParsesResultsAndUnwrapsRedirect() {
        val doc = Jsoup.parse(res("ddg_lite_sample.html"))
        assertFalse(SearchParsers.isDdgChallenge(doc))
        val hits = SearchParsers.ddgLite(doc, 5)
        assertEquals(5, hits.size)
        assertEquals("https://www.accuweather.com/en/kr/seoul/226081/hourly-weather-forecast/226081", hits[0].url)
        assertTrue(hits[0].title.contains("AccuWeather"))
        assertTrue(hits[0].snippet.contains("Seoul"))
    }

    @Test fun ddgChallengeDetected() {
        val doc = Jsoup.parse(res("ddg_challenge_sample.html"))
        assertTrue(SearchParsers.isDdgChallenge(doc))
        assertTrue(SearchParsers.ddgHtml(doc, 5).isEmpty())
    }

    @Test fun bingParses() {
        val hits = SearchParsers.bing(Jsoup.parse(res("bing_sample.html")), 3)
        assertEquals(3, hits.size)
        assertTrue(hits.all { it.url.startsWith("https://") && it.title.isNotBlank() })
    }

    @Test fun bingRedirectDecoded() {
        val u = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("https://example.com/a?b=1".toByteArray())
        assertEquals("https://example.com/a?b=1", SearchParsers.unwrapBing("https://www.bing.com/ck/a?!&&p=x&u=a1$u&ntb=1"))
    }

    @Test fun jsonProviders() {
        val sx = SearchParsers.searxng("""{"results":[{"title":"T","url":"https://a","content":"c"}]}""", 5)
        assertEquals(listOf(SearchHit("T", "https://a", "c")), sx)
        val br = SearchParsers.brave("""{"web":{"results":[{"title":"B","url":"https://b","description":"<strong>x</strong> y"}]}}""", 5)
        assertEquals("x y", br.single().snippet)
    }

    @Test fun pageTextKeepsStructureAndDropsChrome() {
        val html = """<html><head><title>제목</title><script>var x=1</script></head><body>
            <nav>메뉴 메뉴</nav><article><h2>소제목</h2><p>첫 문단 <b>강조</b>.</p><ul><li>항목1</li><li>항목2 <p>중첩</p></li></ul></article>
            <footer>저작권</footer></body></html>"""
        val t = PageText.extract(Jsoup.parse(html))
        assertTrue(t.startsWith("# 제목"))
        assertTrue(t.contains("## 소제목"))
        assertTrue(t.contains("첫 문단 강조."))
        assertTrue(t.contains("- 항목1"))
        assertFalse(t.contains("메뉴"))
        assertFalse(t.contains("저작권"))
        assertFalse(t.contains("var x"))
        assertEquals(1, Regex("중첩").findAll(t).count())
    }

    @Test fun formatListsResults() {
        val out = SearchParsers.format("X", "q", listOf(SearchHit("t", "https://u", "s")))
        assertTrue(out.contains("1. t\n   https://u\n   s"))
    }
}
