package app.postcleaner

import android.net.Uri
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

data class Post(val id: String, val text: String, val date: String, val likes: Int, val replies: Int, val reposts: Int)
data class PostPage(val posts: List<Post>, val next: String?)
object Pkce {
    fun validCallback(expected: String?, received: String?, verifier: String?, started: Long, now: Long): Boolean =
        expected != null && expected == received && !verifier.isNullOrBlank() && now >= started && now - started <= 600_000
    const val redirect = "app.postcleaner://oauth/callback"
    fun random(): String = ByteArray(32).also { SecureRandom().nextBytes(it) }.let(::encode)
    private fun encode(bytes: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    fun challenge(verifier: String) = encode(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
    fun authorize(client: String, state: String, verifier: String): Uri = Uri.parse("https://x.com/i/oauth2/authorize").buildUpon()
        .appendQueryParameter("response_type", "code").appendQueryParameter("client_id", client)
        .appendQueryParameter("redirect_uri", redirect).appendQueryParameter("scope", "tweet.read tweet.write users.read")
        .appendQueryParameter("state", state).appendQueryParameter("code_challenge", challenge(verifier))
        .appendQueryParameter("code_challenge_method", "S256").build()
}
class XApi {
    private val http = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
    private suspend fun call(request: Request): JSONObject = withContext(Dispatchers.IO) {
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException(when (response.code) {
                401 -> "Сессия X истекла. Выйдите и войдите снова."
                402 -> "X требует оплату API. Проверьте баланс в Developer Console."
                403 -> "X отказал в доступе. Проверьте разрешения и доступ приложения к API."
                429 -> "Лимит X API исчерпан. Попробуйте позже; очередь сохранена в этой сессии."
                else -> "Ошибка X API (${response.code}). Попробуйте ещё раз."
            })
            JSONObject(body)
        }
    }
    suspend fun exchange(client: String, code: String, verifier: String): String {
        val form = FormBody.Builder().add("grant_type", "authorization_code").add("client_id", client)
            .add("code", code).add("redirect_uri", Pkce.redirect).add("code_verifier", verifier).build()
        return call(Request.Builder().url("https://api.x.com/2/oauth2/token").post(form).build()).getString("access_token")
    }
    private fun request(path: String, token: String) = Request.Builder().url("https://api.x.com/2/$path").header("Authorization", "Bearer $token")
    suspend fun me(token: String): Pair<String, String> {
        val data = call(request("users/me", token).build()).getJSONObject("data")
        return data.getString("id") to data.getString("username")
    }
    suspend fun posts(token: String, user: String, cursor: String?): PostPage {
        val url = Uri.parse("https://api.x.com/2/users/$user/tweets").buildUpon()
            .appendQueryParameter("max_results", "50").appendQueryParameter("tweet.fields", "created_at,public_metrics,note_tweet")
            .appendQueryParameter("exclude", "retweets")
        cursor?.let { url.appendQueryParameter("pagination_token", it) }
        val json = call(Request.Builder().url(url.build().toString()).header("Authorization", "Bearer $token").build())
        if (json.has("errors")) error("X вернул неполные данные. Повторите загрузку.")
        val data = json.optJSONArray("data")
        val posts = (0 until (data?.length() ?: 0)).map { i ->
            val p = data!!.getJSONObject(i)
            val m = p.optJSONObject("public_metrics") ?: JSONObject()
            Post(p.getString("id"), p.optJSONObject("note_tweet")?.optString("text")?.takeIf { it.isNotBlank() } ?: p.getString("text"),
                p.optString("created_at"), m.optInt("like_count"), m.optInt("reply_count"), m.optInt("retweet_count"))
        }
        return PostPage(posts, json.optJSONObject("meta")?.optString("next_token")?.takeIf { it.isNotBlank() })
    }
    suspend fun delete(token: String, id: String) {
        check(call(request("tweets/$id", token).delete().build()).getJSONObject("data").optBoolean("deleted")) { "X не подтвердил удаление." }
    }
}
