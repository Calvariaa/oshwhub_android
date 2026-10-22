package io.github.Calvariaa.oshwhub.data

import io.github.Calvariaa.oshwhub.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** GitHub Releases 里的一条（只取需要的字段） */
@Serializable
data class ReleaseInfo(
    @SerialName("tag_name") val tagName: String = "",
    val name: String = "",
    val body: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("published_at") val publishedAt: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<ReleaseAsset> = emptyList(),
)

@Serializable
data class ReleaseAsset(
    val name: String = "",
    @SerialName("browser_download_url") val downloadUrl: String = "",
    val size: Long = 0,
)

/** 检查更新的结果 */
sealed interface UpdateResult {
    data class Available(val release: ReleaseInfo, val latestVersion: String) : UpdateResult
    data class UpToDate(val currentVersion: String, val latestVersion: String) : UpdateResult
    data class Failed(val message: String) : UpdateResult
}

/**
 * 检查更新：直接读 GitHub Releases，不经过任何中转。
 *
 * 版本比较走语义化规则（`1.4.0` / `1.4.0-rc.1` / `1.4.1-dev.3+gabc1234` 都认），
 * 当前版本取 `BuildConfig.VERSION_NAME`，与 `app/build.gradle.kts` 的 tag 驱动一致。
 */
class UpdateChecker(private val repo: String = DEFAULT_REPO) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true }

    suspend fun check(): UpdateResult = withContext(Dispatchers.IO) {
        val current = BuildConfig.VERSION_NAME
        val text = runCatching { fetchLatest() }.getOrElse { e ->
            return@withContext UpdateResult.Failed(e.message ?: "网络请求失败")
        }
        val release = runCatching { json.decodeFromString<ReleaseInfo>(text) }.getOrNull()
            ?: return@withContext UpdateResult.Failed("Release 数据解析失败")
        val latest = release.tagName.removePrefix("v").removePrefix("V").ifBlank { release.name }
        if (latest.isBlank()) return@withContext UpdateResult.Failed("Release 里没有版本号")
        if (isNewer(latest, current)) {
            UpdateResult.Available(release, latest)
        } else {
            UpdateResult.UpToDate(current, latest)
        }
    }

    private fun fetchLatest(): String {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$repo/releases?per_page=1")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "oshwhub-android")
            .build()
        client.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (resp.code == 404) throw IllegalStateException("仓库还没有发布 Release")
            if (resp.code == 403) throw IllegalStateException("GitHub 限流，稍后再试")
            if (!resp.isSuccessful) throw IllegalStateException("GitHub 返回 ${resp.code}")
            // 列表 → 取第一条
            val array = json.parseToJsonElement(body)
            val first = (array as? kotlinx.serialization.json.JsonArray)?.firstOrNull()
                ?: throw IllegalStateException("仓库还没有发布 Release")
            return first.toString()
        }
    }

    companion object {
        /** 与 git remote 保持一致 */
        const val DEFAULT_REPO = "Calvariaa/oshwhub_android"

        /**
         * 判断 [remote] 是否比 [local] 新。
         * 只比较数字段，`-dev.N` / `-rc.N` 之类的后缀一律视为比同号正式版旧。
         */
        fun isNewer(remote: String, local: String): Boolean {
            val r = parse(remote)
            val l = parse(local)
            for (i in 0 until 3) {
                if (r.nums[i] != l.nums[i]) return r.nums[i] > l.nums[i]
            }
            // 主版本一致时：正式版 > 预发布版
            if (r.stable != l.stable) return r.stable
            return r.pre > l.pre
        }

        private data class Ver(val nums: List<Int>, val stable: Boolean, val pre: Int)

        private fun parse(raw: String): Ver {
            val cleaned = raw.trim().removePrefix("v").removePrefix("V")
            val core = cleaned.substringBefore('-').substringBefore('+')
            val parts = core.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
            val nums = (parts + List(3) { 0 }).take(3)
            val suffix = cleaned.substringAfter('-', "")
            val stable = suffix.isBlank()
            val pre = Regex("""(\d+)""").find(suffix)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            return Ver(nums, stable, pre)
        }
    }
}
