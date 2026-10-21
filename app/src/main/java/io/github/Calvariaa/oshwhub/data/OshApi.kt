package io.github.Calvariaa.oshwhub.data

import io.github.Calvariaa.oshwhub.data.model.Activity
import io.github.Calvariaa.oshwhub.data.model.Article
import io.github.Calvariaa.oshwhub.data.model.Banner
import io.github.Calvariaa.oshwhub.data.model.Collection
import io.github.Calvariaa.oshwhub.data.model.CommentPage
import io.github.Calvariaa.oshwhub.data.model.ContentStatus
import io.github.Calvariaa.oshwhub.data.model.ForumPost
import io.github.Calvariaa.oshwhub.data.model.LikeCount
import io.github.Calvariaa.oshwhub.data.model.Notification
import io.github.Calvariaa.oshwhub.data.model.Paged
import io.github.Calvariaa.oshwhub.data.model.PortalPicture
import io.github.Calvariaa.oshwhub.data.model.ProImages
import io.github.Calvariaa.oshwhub.data.model.ProjectDetail
import io.github.Calvariaa.oshwhub.data.model.ProjectSummary
import io.github.Calvariaa.oshwhub.data.model.SearchPage
import io.github.Calvariaa.oshwhub.data.model.Tag
import io.github.Calvariaa.oshwhub.data.model.UnreadCount
import io.github.Calvariaa.oshwhub.data.model.UserBrief
import io.github.Calvariaa.oshwhub.data.model.UserInfo
import io.github.Calvariaa.oshwhub.data.net.ApiClient
import io.github.Calvariaa.oshwhub.data.net.ApiException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * 与 oshwhub 桌面端 web 一一对应的接口层。
 * 路径与参数来自站点自身前端 bundle（webpack module 29918 / 24945 / 55051 / 60272）。
 */
class OshApi(val client: ApiClient) {

    val json get() = client.json

    // --------------------------------------------------------------- home --

    suspend fun portalBanner(): PortalPicture? {
        val body = JsonArray(listOf(buildJsonObject { put("pictureCategory", JsonPrimitive("all_site_top_banner")) }))
        val text = client.postJsonText("/api/portal/v1/homePage/getPictureListByPictureCategoryList", body)
        val payload = client.payloadOf(text)
        return runCatching { json.decodeFromJsonElement<List<PortalPicture>>(payload).firstOrNull() }.getOrNull()
    }

    suspend fun banners(type: Int = 9): List<Banner> =
        client.getOrNull<List<Banner>>("/api/config/banner", mapOf("type" to type, "limit" to 5)) ?: emptyList()

    suspend fun topNotice(): String =
        runCatching { client.get<String>("/api/topNotices", mapOf("position" to "eda-oshwhub")) }.getOrDefault("")

    suspend fun competitions(page: Int = 1, pageSize: Int = 10): Paged<Activity> =
        client.getOrNull<Paged<Activity>>(
            "/api/activities/competition_list",
            mapOf("page" to page, "pageSize" to pageSize),
        ) ?: Paged()

    suspend fun randomProjects(): List<ProjectSummary> =
        client.getOrNull<List<ProjectSummary>>("/api/collections/random/projects") ?: emptyList()

    suspend fun recommendByCollection(path: String, page: Int = 1, pageSize: Int = 8): Paged<ProjectSummary> =
        client.getOrNull<Paged<ProjectSummary>>(
            "/api/collections/$path/projects",
            mapOf("page" to page, "pageSize" to pageSize),
        ) ?: Paged()

    // ------------------------------------------------------------ project --

    suspend fun projects(
        page: Int = 1,
        pageSize: Int = 20,
        type: String = "default",
        origin: String = "all",
        projectTags: List<String> = emptyList(),
        keywords: String = "",
        publicOnly: Boolean = true,
    ): Paged<ProjectSummary> = client.getOrNull<Paged<ProjectSummary>>(
        "/api/project",
        mapOf(
            "page" to page,
            "pageSize" to pageSize,
            "type" to type,
            "origin" to origin,
            "projectTag" to projectTags.joinToString(","),
            "keywords" to keywords,
            "public" to publicOnly,
        ),
    ) ?: Paged()

    suspend fun project(uuid: String): ProjectDetail = client.get("/api/project/$uuid")

    suspend fun proImages(uuid: String): ProImages =
        client.getOrNull<ProImages>("/api/project/$uuid/proImages") ?: ProImages()

    suspend fun relatedProjects(uuid: String, tags: List<String>, page: Int = 1, pageSize: Int = 10): Paged<ProjectSummary> =
        client.getOrNull<Paged<ProjectSummary>>(
            "/api/project/relatedProjects",
            mapOf("uuid" to uuid, "project_tags" to tags.joinToString(","), "page" to page, "pageSize" to pageSize),
        ) ?: Paged()

    suspend fun addViewCount(uuid: String) {
        runCatching { client.postJsonText("/api/common/projects/$uuid/addViewCount", JsonObject(emptyMap())) }
    }

    suspend fun addBrowseHistory(uuid: String, type: String = "project") {
        runCatching {
            client.postJsonText(
                "/api/browseHistories/add",
                buildJsonObject {
                    put("target_uuid", JsonPrimitive(uuid))
                    put("target_type", JsonPrimitive(type))
                },
            )
        }
    }

    suspend fun browseHistories(page: Int = 1, pageSize: Int = 20): Paged<ProjectSummary> =
        client.getOrNull<Paged<ProjectSummary>>("/api/browseHistories", mapOf("page" to page, "pageSize" to pageSize))
            ?: Paged()

    /**
     * 站内 `/api/common/` 下的资源路径用的是复数（projects / articles / posts / users），
     * 上层视图层统一用单数，这里做一次归一化。
     */
    private fun apiType(type: String): String = when (type.lowercase()) {
        "project", "projects" -> "projects"
        "article", "articles" -> "articles"
        "post", "posts" -> "posts"
        "user", "users" -> "users"
        "collection", "collections" -> "collections"
        else -> type
    }

    suspend fun contentStatus(type: String, uuid: String): ContentStatus? =
        client.getOrNull<ContentStatus>("/api/common/${apiType(type)}/$uuid/status")

    suspend fun like(type: String, uuid: String): Boolean = toggle("like", type, uuid, true)

    suspend fun unlike(type: String, uuid: String): Boolean = toggle("like", type, uuid, false)

    suspend fun collect(type: String, uuid: String): Boolean = toggle("collect", type, uuid, true)

    suspend fun uncollect(type: String, uuid: String): Boolean = toggle("collect", type, uuid, false)

    private suspend fun toggle(action: String, type: String, uuid: String, add: Boolean): Boolean {
        val path = "/api/common/${apiType(type)}/$uuid/$action"
        val text = if (add) client.postJsonText(path, JsonObject(emptyMap())) else client.deleteText(path)
        client.payloadOf(text)
        return true
    }

    suspend fun comments(type: String, uuid: String, sort: String = "time", page: Int = 1, pageSize: Int = 10, commentUuid: String? = null): CommentPage =
        client.getOrNull<CommentPage>(
            "/api/common/${apiType(type)}/$uuid/comments",
            mapOf("sort" to sort, "page" to page, "pageSize" to pageSize, "commentUuid" to commentUuid),
        ) ?: CommentPage()

    suspend fun createComment(type: String, uuid: String, content: String, parentUuid: String? = null): Boolean {
        val body = buildJsonObject {
            put("content", JsonPrimitive(content))
            if (!parentUuid.isNullOrBlank()) put("parent_uuid", JsonPrimitive(parentUuid))
        }
        client.payloadOf(client.postJsonText("/api/common/${apiType(type)}/$uuid/comments/create", body))
        return true
    }

    suspend fun deleteComment(type: String, uuid: String, commentUuid: String) {
        client.payloadOf(client.deleteText("/api/common/${apiType(type)}/$uuid/comments/$commentUuid"))
    }

    suspend fun likeComment(type: String, uuid: String, commentUuid: String): Boolean {
        client.payloadOf(
            client.postJsonText("/api/common/${apiType(type)}/$uuid/comments/$commentUuid/like", JsonObject(emptyMap())),
        )
        return true
    }

    suspend fun unlikeComment(type: String, uuid: String, commentUuid: String) {
        client.payloadOf(client.deleteText("/api/common/${apiType(type)}/$uuid/comments/$commentUuid/like"))
    }

    suspend fun recommendations(pageSize: Int = 6, itemId: String): List<ProjectSummary> =
        client.getOrNull<List<ProjectSummary>>(
            "/api/common/recommendations",
            mapOf("pageSize" to pageSize, "itemId" to itemId),
        ) ?: emptyList()

    // --------------------------------------------------------------- tags --

    suspend fun projectTags(recommendOnly: Boolean = false): List<Tag> =
        client.getOrNull<List<Tag>>(
            "/api/project_tags",
            if (recommendOnly) mapOf("is_recommend" to 1) else emptyMap(),
        ) ?: emptyList()

    suspend fun hotWords(): List<String> =
        runCatching { client.get<List<String>>("/api/search/hotWords") }.getOrDefault(emptyList())

    // ------------------------------------------------------------- search --

    suspend fun search(type: String, keywords: String, page: Int = 1, pageSize: Int = 20, category: String = "", sort: String = ""): SearchPage =
        client.post(
            "/api/search/data",
            buildJsonObject {
                put("type", JsonPrimitive(type))
                put("keywords", JsonPrimitive(keywords))
                put("category", JsonPrimitive(category))
                put("sort", JsonPrimitive(sort))
                put("page", JsonPrimitive(page))
                put("pageSize", JsonPrimitive(pageSize))
            },
        )

    suspend fun searchCount(type: String, keywords: String): Int =
        runCatching {
            client.payloadOf(client.postJsonText("/api/search/count", buildJsonObject {
                put("type", JsonPrimitive(type))
                put("keywords", JsonPrimitive(keywords))
            })).jsonPrimitive.intOrNullSafe()
        }.getOrNull() ?: 0

    // -------------------------------------------------------------- users --

    suspend fun me(): UserInfo = client.get("/api/users")

    suspend fun userInfo(uuid: String): UserInfo? =
        client.getOrNull("/api/users/$uuid/info".let { if (it.startsWith("/")) it else "/$it" })

    suspend fun userProfile(uuid: String): JsonObject? =
        client.getOrNull<JsonObject>("/api/users/$uuid/profile")

    suspend fun userLikeCount(uuid: String): Int =
        client.getOrNull<LikeCount>("/api/users/$uuid/like/count")?.likeCount ?: 0

    suspend fun userProjects(
        uuid: String,
        page: Int = 1,
        pageSize: Int = 12,
        status: String = "publish",
        sort: String = "new",
        keywords: String = "",
        join: Int = 0,
        publicOnly: Boolean = true,
    ): Paged<ProjectSummary> = client.getOrNull<Paged<ProjectSummary>>(
        "/api/users/$uuid/projects",
        mapOf(
            "page" to page,
            "pageSize" to pageSize,
            "status" to status,
            "origin" to "all",
            "keywords" to keywords,
            "join" to join,
            "public" to publicOnly,
            "sort" to sort,
        ),
    ) ?: Paged()

    suspend fun userArticles(uuid: String, page: Int = 1, pageSize: Int = 12, type: Int = 3): Paged<Article> =
        client.getOrNull<Paged<Article>>("/api/users/$uuid/articles", mapOf("page" to page, "pageSize" to pageSize, "type" to type))
            ?: Paged()

    suspend fun userPosts(uuid: String, page: Int = 1, pageSize: Int = 12): Paged<ForumPost> =
        client.getOrNull<Paged<ForumPost>>(
            "/api/users/$uuid/posts".let { if (it.startsWith("/")) it else "/$it" },
            mapOf("page" to page, "pageSize" to pageSize),
        ) ?: Paged()

    suspend fun userStar(uuid: String, page: Int = 1, pageSize: Int = 12): Paged<ProjectSummary> =
        client.getOrNull<Paged<ProjectSummary>>("/api/users/$uuid/star", mapOf("page" to page, "pageSize" to pageSize))
            ?: Paged()

    suspend fun userCollections(uuid: String, page: Int = 1, pageSize: Int = 12): Paged<Collection> =
        client.getOrNull<Paged<Collection>>("/api/users/$uuid/collections", mapOf("page" to page, "pageSize" to pageSize))
            ?: Paged()

    suspend fun userFollows(uuid: String, page: Int = 1, pageSize: Int = 30): Paged<UserBrief> =
        client.getOrNull<Paged<UserBrief>>("/api/users/$uuid/follows", mapOf("page" to page, "pageSize" to pageSize))
            ?: Paged()

    suspend fun userFollowers(uuid: String, page: Int = 1, pageSize: Int = 30): Paged<UserBrief> =
        client.getOrNull<Paged<UserBrief>>("/api/users/$uuid/followers".let { if (it.startsWith("/")) it else "/$it" }, mapOf("page" to page, "pageSize" to pageSize))
            ?: Paged()

    suspend fun follow(uuid: String) {
        client.payloadOf(client.postJsonText("/api/users/follow", buildJsonObject { put("uuid", JsonPrimitive(uuid)) }))
    }

    suspend fun unfollow(uuid: String) {
        client.payloadOf(client.postJsonText("/api/users/unfollow", buildJsonObject { put("uuid", JsonPrimitive(uuid)) }))
    }

    suspend fun sendMessage(receiver: String, content: String) {
        client.payloadOf(
            client.postJsonText(
                "/api/messages/send",
                buildJsonObject {
                    put("receiver", JsonPrimitive(receiver))
                    put("content", JsonPrimitive(content))
                },
            ),
        )
    }

    // ------------------------------------------------- articles / forum ----

    suspend fun articles(page: Int = 1, pageSize: Int = 20, tab: String? = null, filter: String? = null, type: Int? = null, keywords: String? = null): Paged<Article> {
        val params = buildMap<String, Any?> {
            put("page", page)
            put("pageSize", pageSize)
            if (!tab.isNullOrBlank()) put("tab", tab)
            if (!filter.isNullOrBlank()) put("filter", filter)
            if (type != null) put("type", type)
            if (!keywords.isNullOrBlank()) put("keywords", keywords)
        }
        return client.getOrNull<Paged<Article>>("/api/articles", params) ?: Paged()
    }

    suspend fun article(uuid: String): Article = client.get("/api/articles/$uuid")

    suspend fun post(uuid: String): ForumPost = client.get("/api/posts/$uuid")

    suspend fun posts(page: Int = 1, pageSize: Int = 20): Paged<ForumPost> =
        client.getOrNull<Paged<ForumPost>>("/api/posts".let { if (it.startsWith("/")) it else "/$it" }, mapOf("page" to page, "pageSize" to pageSize))
            ?: Paged()

    // ---------------------------------------------------------- collections --

    suspend fun collections(page: Int = 1, pageSize: Int = 20): Paged<Collection> =
        client.getOrNull<Paged<Collection>>("/api/collections", mapOf("page" to page, "pageSize" to pageSize)) ?: Paged()

    suspend fun collection(uuid: String): Collection? = client.getOrNull("/api/collections/$uuid")

    /**
     * 站点合集地址用的是 path（例如 `officeRecommend`），接口只认 uuid。
     * 这里先按 uuid 直取，失败再翻合集列表把 path 映射成 uuid。
     */
    suspend fun resolveCollectionKey(key: String): Collection? {
        if (key.isBlank()) return null
        client.getOrNull<Collection>("/api/collections/$key")?.let { return it }
        var page = 1
        while (page <= 10) {
            val chunk = client.getOrNull<Paged<Collection>>(
                "/api/collections",
                mapOf("page" to page, "pageSize" to 100),
            ) ?: break
            chunk.lists.firstOrNull { it.path == key || it.uuid == key }?.let { return it }
            if (page >= chunk.totalPage || chunk.lists.isEmpty()) break
            page++
        }
        return null
    }

    suspend fun collectionProjects(uuid: String, page: Int = 1, pageSize: Int = 20): Paged<ProjectSummary> =
        client.getOrNull<Paged<ProjectSummary>>("/api/collections/$uuid/projects", mapOf("page" to page, "pageSize" to pageSize))
            ?: Paged()

    // ------------------------------------------------------- notifications --

    suspend fun notifications(type: String = "allNotifications", read: Boolean = false, page: Int = 1, pageSize: Int = 20): Paged<Notification> =
        client.getOrNull<Paged<Notification>>(
            "/api/notifications",
            mapOf("type" to type, "read" to read, "page" to page, "pageSize" to pageSize),
        ) ?: Paged()

    suspend fun markNotificationsRead(uuids: List<String> = emptyList()) {
        runCatching {
            client.payloadOf(
                client.postJsonText(
                    "/api/notifications/read",
                    buildJsonObject {
                        if (uuids.isNotEmpty()) put("uuids", JsonArray(uuids.map { JsonPrimitive(it) }))
                    },
                ),
            )
        }
    }

    suspend fun unreadCount(): Int = client.getOrNull<UnreadCount>("/api/users/notification/unreadCount")?.count ?: 0

    // --------------------------------------------------------------- misc ---

    suspend fun websiteConfig(): String = runCatching { client.get<String>("/api/WebsiteConfig") }.getOrDefault("")

    suspend fun searchUuidByPath(type: String, path: String): String? =
        runCatching { client.get<String>("/api/common/$type/getUuidByPath", mapOf("path" to path)) }.getOrNull()

    suspend fun logout() {
        runCatching { client.postJsonText("/api/logout", JsonObject(emptyMap())) }
        client.cookieStore.clear()
    }

    fun isAuthError(t: Throwable): Boolean = (t as? ApiException)?.isAuthError == true
}

private fun JsonPrimitive.intOrNullSafe(): Int? = runCatching { content.toInt() }.getOrNull()

private fun JsonArray.unused(): List<JsonPrimitive> = emptyList()
