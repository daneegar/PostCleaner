package app.postcleaner

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class CleanerModel(app: Application) : AndroidViewModel(app) {
    private val api = XApi()
    private val prefs = app.getSharedPreferences("oauth_pending", 0)
    var clientId by mutableStateOf(prefs.getString("client", "").orEmpty())
    var username by mutableStateOf(""); private set
    var connected by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var notice by mutableStateOf<String?>(null); private set
    val remaining = mutableStateListOf<Post>()
    val queue = mutableStateListOf<Post>()
    private val history = mutableListOf<Pair<Post, Boolean>>()
    var reviewed by mutableIntStateOf(0); private set
    var deleted by mutableIntStateOf(0); private set
    var more by mutableStateOf(false); private set
    private var cursor: String? = null
    private var token: String? = null
    private var user = ""
    val canUndo get() = history.isNotEmpty()
    private fun job(block: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null; notice = null
        viewModelScope.launch {
            try { block() } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "Не удалось выполнить запрос. Проверьте интернет." }
            finally { busy = false }
        }
    }
    fun login(): Intent? {
        if (busy) return null
        if (clientId.isBlank()) { error = "Введите OAuth 2.0 Client ID из X Developer Console."; return null }
        val verifier = Pkce.random(); val state = Pkce.random()
        prefs.edit().putString("client", clientId.trim()).putString("verifier", verifier).putString("state", state)
            .putLong("started", System.currentTimeMillis()).commit()
        return Intent(Intent.ACTION_VIEW, Pkce.authorize(clientId.trim(), state, verifier))
    }
    fun callback(uri: Uri?) {
        if (uri == null || uri.scheme != "app.postcleaner" || uri.host != "oauth" || uri.path != "/callback") return
        val expected = prefs.getString("state", null)
        val verifier = prefs.getString("verifier", null)
        val started = prefs.getLong("started", 0)
        if (!Pkce.validCallback(expected, uri.getQueryParameter("state"), verifier, started, System.currentTimeMillis())) {
            error = "Вход не подтверждён или устарел. Начните вход заново."; return
        }
        prefs.edit().remove("state").remove("verifier").remove("started").commit()
        val code = uri.getQueryParameter("code")
        if (uri.getQueryParameter("error") != null || code == null) { error = "Вход в X отменён."; return }
        job {
            val access = api.exchange(clientId.trim(), code, requireNotNull(verifier))
            val profile = api.me(access)
            token = access; user = profile.first; username = profile.second; connected = true
            loadPage()
        }
    }
    private suspend fun loadPage() {
        val page = api.posts(requireNotNull(token), user, cursor)
        remaining.addAll(page.posts.filter { post -> remaining.none { it.id == post.id } && queue.none { it.id == post.id } && history.none { it.first.id == post.id } })
        cursor = page.next; more = cursor != null
    }
    fun load() = job { loadPage() }
    fun choose(remove: Boolean) {
        if (busy || remaining.isEmpty()) return
        val post = remaining.removeAt(0)
        if (remove) queue.add(post)
        history.add(post to remove); reviewed++
    }
    fun undo() {
        if (busy || history.isEmpty()) return
        val (post, remove) = history.removeAt(history.lastIndex)
        if (remove) queue.remove(post)
        remaining.add(0, post); reviewed--
    }
    fun restore(post: Post) {
        if (busy) return
        queue.remove(post); history.removeAll { it.first.id == post.id }
        remaining.add(0, post); reviewed--
    }
    fun deleteConfirmed() = job {
        val selected = queue.toList()
        for (post in selected) {
            api.delete(requireNotNull(token), post.id)
            queue.remove(post); history.removeAll { it.first.id == post.id }; deleted++
        }
        notice = "Удалено: ${selected.size}"
    }
    fun logout() {
        if (busy) return
        token = null; user = ""; username = ""; connected = false; cursor = null; more = false
        remaining.clear(); queue.clear(); history.clear(); reviewed = 0; deleted = 0; error = null; notice = null
        prefs.edit().remove("state").remove("verifier").remove("started").apply()
    }
}
