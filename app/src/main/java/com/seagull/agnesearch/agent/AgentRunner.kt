package com.seagull.agnesearch.agent

import com.seagull.agnesearch.core.AppSettings
import com.seagull.agnesearch.core.SourceItem
import com.seagull.agnesearch.net.AgnesClient
import com.seagull.agnesearch.net.StreamEvent
import com.seagull.agnesearch.search.PageReader
import com.seagull.agnesearch.search.SearchEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

sealed class AgentEvent {
    /** 过程状态：正在搜索 / 正在读页面 / 模型在想什么 */
    data class Status(val text: String) : AgentEvent()
    /** 最终答案的增量 */
    data class Answer(val delta: String) : AgentEvent()
    /** 这一轮命中的来源 */
    data class Sources(val items: List<SourceItem>, val engine: String) : AgentEvent()
    /** 准备调用某个工具 */
    data class ToolCall(val name: String, val label: String) : AgentEvent()

    data class Finished(
        val answer: String,
        val sources: List<SourceItem>,
        val steps: List<String>,
        val promptTokens: Int,
        val completionTokens: Int,
        val searches: Int,
    ) : AgentEvent()

    data class Failed(val message: String) : AgentEvent()
}

/**
 * 一个真正会干活的搜索 Agent：
 *   检索 → 读正文 → 判断信息够不够 → 再检索 → 带引用的最终回答。
 * 工具调用靠模型自己决定，App 只提供 web_search / open_url 两个能力。
 */
class AgentRunner(private val cfg: AppSettings) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client = AgnesClient(cfg)

    private class ToolAcc {
        var id: String = ""
        var name: String = ""
        val args = StringBuilder()
    }

    fun run(query: String): Flow<AgentEvent> = flow {
        val started = System.currentTimeMillis()
        val steps = ArrayList<String>()
        val sources = ArrayList<SourceItem>()
        var promptTokens = 0
        var completionTokens = 0
        var searchCount = 0
        var rounds = 0

        val messages = ArrayList<JsonObject>()
        messages.add(AgnesClient.textMessage("system", systemPrompt()))
        messages.add(AgnesClient.textMessage("user", query))

        while (true) {
            val allowTools = rounds < cfg.maxRounds
            val acc = LinkedHashMap<Int, ToolAcc>()
            val full = StringBuilder()
            val buffer = StringBuilder()
            var streamed = false
            var failed: String? = null
            var finish = ""

            client.stream(
                JsonArray(messages),
                if (allowTools) toolSpecs() else null,
            ).collect { ev ->
                when (ev) {
                    is StreamEvent.Text -> {
                        full.append(ev.delta)
                        if (streamed) {
                            emit(AgentEvent.Answer(ev.delta))
                        } else {
                            buffer.append(ev.delta)
                            if (acc.isEmpty() && buffer.length > 150) {
                                streamed = true
                                emit(AgentEvent.Answer(buffer.toString()))
                                buffer.setLength(0)
                            }
                        }
                    }

                    is StreamEvent.ToolCallDelta -> {
                        val a = acc.getOrPut(ev.index) { ToolAcc() }
                        if (!ev.id.isNullOrEmpty()) a.id = ev.id
                        if (!ev.name.isNullOrEmpty()) a.name = ev.name
                        if (!ev.args.isNullOrEmpty()) a.args.append(ev.args)
                    }

                    is StreamEvent.Completed -> {
                        promptTokens += ev.promptTokens
                        completionTokens += ev.completionTokens
                        finish = ev.finishReason
                    }

                    is StreamEvent.Failure -> {
                        failed = ev.message
                    }
                }
            }

            if (failed != null) {
                emit(AgentEvent.Failed(failed))
                return@flow
            }

            // 没有工具调用 → 本轮就是最终答案
            if (acc.isEmpty()) {
                val tail = buffer.toString()
                if (tail.isNotEmpty()) emit(AgentEvent.Answer(tail))
                var answer = full.toString().trim()
                if (answer.isEmpty()) {
                    answer = if (finish == "length") {
                        "（输出被 max_tokens 截断，去设置里调大最大输出）"
                    } else {
                        "模型没有返回内容。检查一下模型名和接口设置。"
                    }
                    emit(AgentEvent.Answer(answer))
                }
                if (searchCount > 0) steps.add("完成，共检索 $searchCount 次，阅读 ${sources.count { it.content.isNotBlank() }} 个页面")
                emit(
                    AgentEvent.Finished(
                        answer = answer,
                        sources = sources.toList(),
                        steps = steps.toList(),
                        promptTokens = if (promptTokens > 0) promptTokens else estimate(query, full.length),
                        completionTokens = if (completionTokens > 0) completionTokens else full.length / 3,
                        searches = searchCount,
                    )
                )
                return@flow
            }

            // 有工具调用：先把模型本轮的文字当作过程说明
            if (buffer.isNotEmpty()) {
                steps.add("模型思考：" + buffer.toString().trim().take(140))
            }

            val toolCallsJson = buildJsonArray {
                for (a in acc.values) {
                    val name = a.name.ifEmpty { "web_search" }
                    add(
                        buildJsonObject {
                            put("id", if (a.id.isNotEmpty()) a.id else "call_" + Math.abs(name.hashCode()))
                            put("type", "function")
                            put(
                                "function",
                                buildJsonObject {
                                    put("name", name)
                                    put("arguments", a.args.toString().ifBlank { "{}" })
                                }
                            )
                        } as JsonElement
                    )
                }
            }

            messages.add(
                buildJsonObject {
                    put("role", "assistant")
                    put("content", full.toString())
                    put("tool_calls", toolCallsJson)
                }
            )

            for (a in acc.values) {
                val name = a.name.ifEmpty { "web_search" }
                val callId = if (a.id.isNotEmpty()) a.id else "call_" + Math.abs(name.hashCode())
                val result: String
                when (name) {
                    "web_search" -> {
                        val q = argString(a.args.toString(), "query").ifBlank { query }
                        val cnt = argInt(a.args.toString(), "count", cfg.maxResults)
                        emit(AgentEvent.ToolCall(name, "搜索「$q」"))
                        emit(AgentEvent.Status("正在检索：$q"))
                        searchCount++
                        steps.add("搜索：$q")
                        result = doSearch(q, cnt, sources) { msg -> emit(AgentEvent.Status(msg)) }
                        emit(AgentEvent.Sources(sources.toList(), cfg.searchProvider))
                    }

                    "open_url" -> {
                        val u = argString(a.args.toString(), "url")
                        emit(AgentEvent.ToolCall(name, "阅读网页"))
                        steps.add("阅读：" + u.take(80))
                        result = doOpen(u, sources) { msg -> emit(AgentEvent.Status(msg)) }
                    }

                    else -> result = "未知工具 $name"
                }

                messages.add(
                    buildJsonObject {
                        put("role", "tool")
                        put("tool_call_id", callId)
                        put("name", name)
                        put("content", result)
                    }
                )
            }

            rounds++
            emit(AgentEvent.Status("整理中…"))
        }
    }

    // ── 工具实现 ────────────────────────────────────────────────────────

    private suspend fun doSearch(
        query: String,
        count: Int,
        sources: MutableList<SourceItem>,
        progress: suspend (String) -> Unit,
    ): String {
        val r = SearchEngine.search(query, cfg.copy(maxResults = count.coerceIn(3, 20)))
        when (r) {
            is SearchEngine.Result.Fail -> return "搜索失败：" + r.message
            is SearchEngine.Result.Ok -> {
                val items = r.items
                val sb = StringBuilder()
                sb.append("搜索词：").append(query).append("（来源：").append(r.engine).append("）\n")
                var idx = sources.size
                var fetched = 0
                for (it in items) {
                    idx++
                    sources.add(it)
                    sb.append("\n[").append(idx).append("] ").append(it.title).append("\n")
                    sb.append("URL: ").append(it.url).append("\n")
                    if (it.snippet.isNotBlank()) sb.append("摘要: ").append(it.snippet).append("\n")
                }

                if (cfg.fetchPages > 0) {
                    for (i in items.indices) {
                        if (fetched >= cfg.fetchPages) break
                        val item = items[i]
                        progress("正在阅读：" + item.title.take(40))
                        val page = PageReader.read(item.url) ?: continue
                        fetched++
                        val body = page.text.take(2600)
                        sb.append("\n—— 正文 [").append(sources.size - items.size + i + 1).append("] ")
                            .append(page.title.take(80)).append(" ——\n").append(body).append("\n")
                        val pos = sources.indexOfFirst { it.url == item.url }
                        if (pos >= 0) sources[pos] = sources[pos].copy(content = body.take(1200))
                        if (sb.length > 16000) break
                    }
                    if (fetched > 0) progress("已阅读 $fetched 个页面")
                }
                return sb.toString().take(20000)
            }
        }
    }

    private suspend fun doOpen(
        url: String,
        sources: MutableList<SourceItem>,
        progress: suspend (String) -> Unit,
    ): String {
        if (url.isBlank() || !url.startsWith("http")) return "URL 不合法：$url"
        progress("正在阅读：" + url.take(50))
        val page = PageReader.read(url) ?: return "页面打不开或没有正文：$url"
        val pos = sources.indexOfFirst { it.url == url }
        if (pos >= 0) {
            sources[pos] = sources[pos].copy(content = page.text.take(1200))
        } else {
            sources.add(SourceItem(title = page.title, url = url, snippet = "", engine = "直接访问", content = page.text.take(1200)))
        }
        return "标题：" + page.title + "\nURL: " + url + "\n正文：\n" + page.text.take(7000)
    }

    // ── 提示词与工具声明 ────────────────────────────────────────────────

    private fun systemPrompt(): String {
        if (cfg.systemPrompt.isNotBlank()) return cfg.systemPrompt
        val lang = if (cfg.language == "en") "Answer in English." else "用简体中文回答。"
        return """
你是 Agnes 搜索助手：一个会自己上网查资料的智能体。

工作方式：
1. 先判断问题需不需要实时/外部信息。需要就先调 web_search 检索（可以换关键词多搜几轮），不需要就凭已有知识直接回答。
2. 搜索结果里如果已经有足够信息，直接给结论；如果关键页面只给了摘要，就用 open_url 打开最重要的页面读正文。
3. 最多进行 ${cfg.maxRounds} 轮工具调用，信息够用就立刻收尾，别为了搜索而搜索。

回答要求：
- $lang
- 用 Markdown 组织：先给一段直接结论，再分点展开，必要时用表格或代码块。
- 只要用了检索结果，就在句末标引用编号，例如 [1]、[2][3]，编号必须和搜索结果里的编号一致。
- 数字、时间、人名、版本号这类容易出错的细节要忠于来源；来源之间冲突时明确指出冲突。
- 不要编造来源，不要输出"根据搜索结果"这种空话，直接给内容。
- 结尾不要列参考资料清单（App 会自动展示来源卡片）。
        """.trimIndent()
    }

    private fun toolSpecs(): JsonArray = buildJsonArray {
        add(
            buildJsonObject {
                put("type", "function")
                put(
                    "function",
                    buildJsonObject {
                        put("name", "web_search")
                        put("description", "联网搜索。返回一组网页标题、URL、摘要，并自动抓取前几个页面的正文。需要查最新信息、事实、新闻、文档时使用。")
                        put(
                            "parameters",
                            buildJsonObject {
                                put("type", "object")
                                put(
                                    "properties",
                                    buildJsonObject {
                                        put(
                                            "query",
                                            buildJsonObject {
                                                put("type", "string")
                                                put("description", "检索关键词，越具体越好；可以带年份、站点名")
                                            }
                                        )
                                        put(
                                            "count",
                                            buildJsonObject {
                                                put("type", "integer")
                                                put("description", "返回条数，3-20，默认 ${cfg.maxResults}")
                                            }
                                        )
                                    }
                                )
                                put("required", buildJsonArray { add(JsonPrimitive("query")) })
                            }
                        )
                    }
                )
            } as JsonElement
        )
        add(
            buildJsonObject {
                put("type", "function")
                put(
                    "function",
                    buildJsonObject {
                        put("name", "open_url")
                        put("description", "打开一个网页并读取正文。当检索结果的摘要不够、需要看全文细节时使用。")
                        put(
                            "parameters",
                            buildJsonObject {
                                put("type", "object")
                                put(
                                    "properties",
                                    buildJsonObject {
                                        put(
                                            "url",
                                            buildJsonObject {
                                                put("type", "string")
                                                put("description", "完整的 http/https 链接")
                                            }
                                        )
                                    }
                                )
                                put("required", buildJsonArray { add(JsonPrimitive("url")) })
                            }
                        )
                    }
                )
            } as JsonElement
        )
    }

    // ── 小工具 ──────────────────────────────────────────────────────────

    private fun argString(argsJson: String, key: String): String {
        return try {
            val o = json.parseToJsonElement(argsJson).jsonObject
            o[key]?.jsonPrimitive?.contentOrNull.orEmpty()
        } catch (e: Exception) {
            // 参数被截断时兜底用正则捞
            Regex("\"" + key + "\"\\s*:\\s*\"([^\"]+)").find(argsJson)?.groupValues?.get(1).orEmpty()
        }
    }

    private fun argInt(argsJson: String, key: String, def: Int): Int {
        return try {
            val o = json.parseToJsonElement(argsJson).jsonObject
            o[key]?.jsonPrimitive?.intOrNull ?: def
        } catch (e: Exception) {
            Regex("\"" + key + "\"\\s*:\\s*(\\d+)").find(argsJson)?.groupValues?.get(1)?.toIntOrNull() ?: def
        }
    }

    private fun estimate(query: String, answerLen: Int): Int {
        val sys = systemPrompt().length
        return ((sys + query.length) * 2 / 3 + 800).coerceAtLeast(100)
    }
}
