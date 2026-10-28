package com.lewis.phoneagent.tools.query

import com.lewis.phoneagent.core.intent.RuleMatcher
import com.lewis.phoneagent.tools.MatchScore
import com.lewis.phoneagent.tools.RankRow
import com.lewis.phoneagent.tools.Tool
import com.lewis.phoneagent.tools.ToolArgs
import com.lewis.phoneagent.tools.ToolResult
import com.lewis.phoneagent.tools.ToolSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 联网搜索（Phone-Vibe 能力搬迁）：抓取百度搜索结果页并解析标题/链接/摘要。
 * 零配置、免费；点击结果行用浏览器打开。
 * 诚实边界：网页解析依赖页面结构，结构变化时可能失效（结果卡片注明）。
 */
class SearchTool(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
) : Tool {

    override val spec = ToolSpec(
        name = "web_search",
        description = "联网搜索并返回结果标题与链接",
    )

    override fun match(text: String): MatchScore? {
        for (prefix in listOf("搜索", "搜一下", "查一下", "帮我搜", "百度一下", "搜搜")) {
            val idx = text.indexOf(prefix)
            if (idx >= 0) {
                val query = text.substring(idx + prefix.length).trim()
                    .removePrefix("：").removePrefix(":").removePrefix("一下")
                if (query.isNotEmpty()) {
                    return MatchScore(0.9f, mapOf("query" to query))
                }
            }
        }
        return null
    }

    override suspend fun execute(args: ToolArgs): ToolResult = withContext(Dispatchers.IO) {
        val query = (args["query"] as? String)?.trim()
        if (query.isNullOrEmpty()) return@withContext ToolResult.Text("想搜什么？试试说「搜索 比特币最新行情」")

        val url = "https://www.baidu.com/s?wd=" + URLEncoder.encode(query, "UTF-8")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/110.0 Mobile Safari/537.36")
            .build()

        val html = try {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext ToolResult.Text("搜索请求失败：HTTP ${resp.code}")
                resp.body?.string() ?: return@withContext ToolResult.Text("搜索返回为空")
            }
        } catch (e: Exception) {
            return@withContext ToolResult.Text("搜索失败：${e.message ?: e.javaClass.simpleName}（检查网络）")
        }

        // 解析百度结果：<h3 class="..."><a href="..." ...>标题</a></h3>，摘要 <div class="c-abstract...">...
        val results = Regex("<h3[^>]*>\\s*<a[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
            .findAll(html)
            .take(8)
            .mapNotNull { m ->
                val href = m.groupValues[1]
                val title = m.groupValues[2]
                    .replace(Regex("<[^>]+>"), "")
                    .replace("&quot;", "\"")
                    .replace("&amp;", "&")
                    .trim()
                if (title.isEmpty()) null else RankRow(title, href.take(100), href)
            }
            .toList()

        if (results.isEmpty()) {
            return@withContext ToolResult.Text("「$query」没有搜到结果（页面结构可能变化）。")
        }
        ToolResult.Ranking(
            title = "「$query」的搜索结果（${results.size} 条）",
            rows = results,
            note = "结果抓取自百度网页，点击行用浏览器打开；页面结构变化时可能失效。",
        )
    }
}
