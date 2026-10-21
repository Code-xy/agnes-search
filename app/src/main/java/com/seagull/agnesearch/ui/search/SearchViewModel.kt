package com.seagull.agnesearch.ui.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seagull.agnesearch.agent.AgentEvent
import com.seagull.agnesearch.agent.AgentRunner
import com.seagull.agnesearch.core.Session
import com.seagull.agnesearch.core.SourceItem
import com.seagull.agnesearch.core.Store
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class SearchState(
    val query: String = "",
    val running: Boolean = false,
    val done: Boolean = false,
    val answer: String = "",
    val sources: List<SourceItem> = emptyList(),
    val steps: List<String> = emptyList(),
    val status: String = "",
    val error: String = "",
    val elapsedMs: Long = 0L,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val model: String = "",
)

class SearchViewModel : ViewModel() {

    var state by mutableStateOf(SearchState())
        private set

    private var job: Job? = null

    fun setQuery(q: String) {
        state = state.copy(query = q)
    }

    /** 从推荐词 / 历史直接发起搜索。 */
    fun useQuery(q: String) {
        state = SearchState(query = q)
        submit()
    }

    fun submit() {
        val q = state.query.trim()
        if (q.isEmpty() || state.running) return
        job?.cancel()

        val cfg = Store.settings.value
        if (cfg.apiKey.isBlank()) {
            state = state.copy(
                error = "还没填 API Key。点右上角进「接口设置」，填上 Agnes 的 Key 再搜。",
                running = false,
            )
            return
        }

        state = SearchState(query = q, running = true, model = cfg.model)
        val started = System.currentTimeMillis()

        job = viewModelScope.launch {
            val runner = AgentRunner(cfg)
            val answer = StringBuilder()
            try {
                runner.run(q).collect { ev ->
                    when (ev) {
                        is AgentEvent.Status -> state = state.copy(status = ev.text)

                        is AgentEvent.ToolCall -> state = state.copy(status = ev.label)

                        is AgentEvent.Answer -> {
                            answer.append(ev.delta)
                            state = state.copy(answer = answer.toString())
                        }

                        is AgentEvent.Sources -> state = state.copy(sources = ev.items)

                        is AgentEvent.Finished -> {
                            val elapsed = System.currentTimeMillis() - started
                            Store.addSession(
                                Session(
                                    id = Store.newId(),
                                    query = q,
                                    answer = ev.answer,
                                    sources = ev.sources,
                                    steps = ev.steps,
                                    model = cfg.model,
                                    createdAt = System.currentTimeMillis(),
                                    elapsedMs = elapsed,
                                    promptTokens = ev.promptTokens,
                                    completionTokens = ev.completionTokens,
                                )
                            )
                            Store.addUsage(ev.promptTokens, ev.completionTokens, ev.searches)
                            state = state.copy(
                                running = false,
                                done = true,
                                answer = ev.answer,
                                sources = ev.sources,
                                steps = ev.steps,
                                status = "",
                                elapsedMs = elapsed,
                                promptTokens = ev.promptTokens,
                                completionTokens = ev.completionTokens,
                            )
                        }

                        is AgentEvent.Failed -> state = state.copy(
                            running = false,
                            error = ev.message,
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state = state.copy(running = false, error = e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        val partial = state.answer
        if (partial.isNotBlank()) {
            Store.addSession(
                Session(
                    id = Store.newId(),
                    query = state.query,
                    answer = partial,
                    sources = state.sources,
                    steps = state.steps,
                    model = state.model,
                    createdAt = System.currentTimeMillis(),
                )
            )
        }
        state = state.copy(running = false, done = true, status = "")
    }

    fun clear() {
        job?.cancel()
        job = null
        state = SearchState()
    }

    fun showSession(session: Session) {
        job?.cancel()
        job = null
        state = SearchState(
            query = session.query,
            done = true,
            answer = session.answer,
            sources = session.sources,
            steps = session.steps,
            elapsedMs = session.elapsedMs,
            promptTokens = session.promptTokens,
            completionTokens = session.completionTokens,
            model = session.model,
        )
    }
}
