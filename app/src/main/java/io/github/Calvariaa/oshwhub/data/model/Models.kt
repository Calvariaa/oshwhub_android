package io.github.Calvariaa.oshwhub.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

/**
 * 后端同一字段会返回 `0` 或 `""`（例如 users.info.companySize），
 * 这里统一按字符串解码，避免整条 JSON 反序列化失败导致用户信息为 null。
 */
object LenientStringSerializer : KSerializer<String> {
    override val descriptor = PrimitiveSerialDescriptor("LenientString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        val json = decoder as? JsonDecoder ?: return decoder.decodeString()
        val element: JsonElement = json.decodeJsonElement()
        return if (element is JsonPrimitive) element.content else element.toString()
    }

    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
}

/**
 * 官方接口对没有资料的账号会返回 `"info": []`（空数组而不是对象），
 * 直接反序列化会抛异常让整个 UserInfo 变成 null。这里做一次宽容处理。
 */
object LenientProfileInfoSerializer : KSerializer<ProfileInfo> {
    override val descriptor = ProfileInfo.serializer().descriptor

    override fun deserialize(decoder: Decoder): ProfileInfo {
        val json = decoder as? JsonDecoder ?: return ProfileInfo()
        val element = json.decodeJsonElement()
        if (element !is JsonObject) return ProfileInfo()
        return runCatching { json.json.decodeFromJsonElement(ProfileInfo.serializer(), element) }
            .getOrDefault(ProfileInfo())
    }

    override fun serialize(encoder: Encoder, value: ProfileInfo) {
        val json = encoder as? JsonEncoder ?: return
        json.encodeJsonElement(json.json.encodeToJsonElement(ProfileInfo.serializer(), value))
    }
}

/** 后端通用分页信封 `result = {page,pageSize,total,totalPage,lists}` */
@Serializable
data class Paged<T>(
    val page: Int = 1,
    val pageSize: Int = 0,
    val total: Int = 0,
    val totalPage: Int = 0,
    val lists: List<T> = emptyList(),
)

@Serializable
data class UserBrief(
    val uuid: String = "",
    val username: String = "",
    val nickname: String = "",
    val avatar: String = "",
    val team: Boolean = false,
)

@Serializable
data class ProjectMembers(
    val uuid: String = "",
    val username: String = "",
    val nickname: String = "",
    val avatar: String = "",
    val role: Int = 0,
)

@Serializable
data class Counts(
    val views: Int = 0,
    val star: Int = 0,
    val fork: Int = 0,
    val watch: Int = 0,
    val like: Int = 0,
)

@Serializable
data class Attachment(
    val uuid: String = "",
    val name: String = "",
    val size: Long = 0,
    val ext: String = "",
    val mime: String = "",
    val src: String = "",
    val md5: String = "",
    @SerialName("download_count") val downloadCount: Int = 0,
)

@Serializable
data class CompetitionBrief(
    val uuid: String = "",
    val name: String = "",
    val path: String = "",
    val external_page: Int = 0,
)

/** 工程卡片（列表/搜索结果共用） */
@Serializable
data class ProjectSummary(
    val uuid: String = "",
    val path: String = "",
    val name: String = "",
    val introduction: String = "",
    val thumb: String = "",
    val owner: UserBrief = UserBrief(),
    val count: Counts = Counts(),
    val origin: String = "",
    val created_at: String = "",
    val updated_at: String = "",
    val oshwhub_publish_at: String? = null,
    val comments_count: Int = 0,
    val public: Boolean = false,
    val publish: Boolean = true,
    val grade: Int = 0,
    val high_quality: Boolean = false,
    val is_draft: Boolean = false,
    val project_tags: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val members: JsonElement? = null,
    val highlight_name: String? = null,
    val highlight_introduction: String? = null,
) {
    val displayName: String get() = highlight_name?.takeIf { it.isNotBlank() } ?: name
    val displayIntro: String get() = highlight_introduction?.takeIf { it.isNotBlank() } ?: introduction
}

@Serializable
data class ReproductionCost(val total_cost: Double = 0.0)

/** 工程详情 */
@Serializable
data class ProjectDetail(
    val uuid: String = "",
    val name: String = "",
    val content: String = "",
    val grade: Int = 0,
    val origin: String = "",
    val is_draft: Boolean = false,
    val thumb: String = "",
    val public: Boolean = false,
    val publish: Boolean = true,
    val owner: UserBrief = UserBrief(),
    val creator: UserBrief = UserBrief(),
    val path: String = "",
    val created_at: String = "",
    val updated_at: String = "",
    val count: Counts = Counts(),
    val version: String = "",
    val attachments: List<Attachment> = emptyList(),
    val attachments_3d: List<Attachment> = emptyList(),
    val license: String = "",
    val project_tags: List<String> = emptyList(),
    val introduction: String = "",
    val text_editor_type: String = "markdown",
    val allow_comment: Boolean = true,
    val allow_reprint: Boolean = false,
    val from: String = "",
    val no: Int = 0,
    val reproduction_cost: ReproductionCost? = null,
    val high_quality: Boolean = false,
    val is_hide: Boolean = false,
    val oshwhub_publish_at: String? = null,
    val remarks: String? = null,
    val competition: CompetitionBrief? = null,
    val comments_count: Int = 0,
    val members: JsonElement? = null,
    val albumCollect: Boolean = false,
    val status: JsonElement? = null,
    val has_vote: Boolean = false,
    val allow_vote: Boolean = false,
    val vote_count: Int = 0,
)

/** BOM 行 */
@Serializable
data class BomRow(
    @SerialName("ID") val id: Int = 0,
    @SerialName("Name") val name: String = "",
    @SerialName("Designator") val designator: String = "",
    @SerialName("Footprint") val footprint: String = "",
    @SerialName("Quantity") val quantity: Int = 0,
    @SerialName("Manufacturer Part") val manufacturerPart: String = "",
    @SerialName("Manufacturer") val manufacturer: String = "",
    @SerialName("Supplier") val supplier: String = "",
    @SerialName("Supplier Part") val supplierPart: String = "",
)

@Serializable
data class BomBlock(
    val uuid: String = "",
    val project_uuid: String = "",
    val boms: String = "",
    val updated_at: String = "",
    val created_at: String = "",
)

@Serializable
data class SchematicDoc(
    val uuid: String = "",
    val schematic_uuid: String = "",
    val display_title: String = "",
    val thumb: String = "",
)

@Serializable
data class SchematicInfo(
    val uuid: String = "",
    val display_name: String = "",
    val boms: BomBlock? = null,
    val documents: List<SchematicDoc> = emptyList(),
)

@Serializable
data class PcbInfo(
    val uuid: String = "",
    val schematic_uuid: String = "",
    val display_title: String = "",
    val thumb: String = "",
    val boms: BomBlock? = null,
)

@Serializable
data class BoardInfo(
    val uuid: String = "",
    val name: String = "",
    val sch: String = "",
    val pcb: String = "",
    val schematic: SchematicInfo? = null,
    val pcb_info: PcbInfo? = null,
)

@Serializable
data class ThreeDInfo(
    val uuid: String = "",
    val schematic_uuid: String = "",
    val display_title: String = "",
    val thumb: String = "",
    val boms: BomBlock? = null,
)

@Serializable
data class ProImages(
    val boards: List<BoardInfo> = emptyList(),
    val out_schematics: List<SchematicDoc> = emptyList(),
    val out_pcbs: List<ThreeDInfo> = emptyList(),
    val panels: List<ThreeDInfo> = emptyList(),
)

@Serializable
data class Comment(
    val uuid: String = "",
    val comment_uuid: String? = null,
    val parent_uuid: String? = null,
    val content: String = "",
    val creator: UserBrief = UserBrief(),
    val created_at: String = "",
    val status: Int = 1,
    val like: Int = 0,
    val is_like: Boolean = false,
    val replies: List<Comment> = emptyList(),
)

@Serializable
data class CommentPage(
    val page: Int = 1,
    val pageSize: Int = 10,
    val total: Int = 0,
    val totalPage: Int = 0,
    val all: Int = 0,
    val lists: List<Comment> = emptyList(),
)

@Serializable
data class ContentStatus(
    val albumCollect: Boolean = false,
    val like: Boolean = false,
    val collect: Boolean = false,
)

@Serializable
data class Article(
    val uuid: String = "",
    val title: String = "",
    val type: Int = 0,
    val content_type: Int = 1,
    val catalog: String = "",
    val catalog_name: String = "",
    val introduction: String = "",
    val view_count: Int = 0,
    val like_count: Int = 0,
    val collect_count: Int = 0,
    val thumb: String = "",
    val path: String = "",
    val is_original: Int = 0,
    val creator: UserBrief = UserBrief(),
    val updated_at: String = "",
    val created_at: String = "",
    val oshwhub_publish_at: String? = null,
    val featured: Int? = null,
    val grade: Int = 0,
    val content: String = "",
)

@Serializable
data class ForumPost(
    val uuid: String = "",
    val title: String = "",
    val summary: String = "",
    val content: String = "",
    val grade: Int = 0,
    val status: Int = 1,
    val recommend: Int = 0,
    val views: Int = 0,
    val cover_image: String = "",
    val competition_origin: String = "",
    val updated_at: String = "",
    val created_at: String = "",
    val path: String = "",
)

@Serializable
data class Banner(
    val uuid: String = "",
    val name: String = "",
    val links: String = "",
    val images: String = "",
    val thumb: String = "",
    val type: Int = 0,
)

@Serializable
data class PortalPicture(
    val id: Long = 0,
    val pictureCategory: String = "",
    val redirectPath: String = "",
    val pictureName: String = "",
    val openNewWindow: Int = 0,
    val picturePath: String = "",
    val subPicturePath: String = "",
    val keyWord: String = "",
    val ordered: Int = 0,
)

@Serializable
data class Activity(
    val uuid: String = "",
    val name: String = "",
    val path: String = "",
    val type: Int = 0,
    val prefix: String = "",
    val project_default_image: String = "",
    val introduction: String = "",
    val activityRules: String = "",
    val registration_start_time: String = "",
    val registration_end_time: String = "",
    val competing_start_time: String = "",
    val competing_end_time: String = "",
    val index_show: Int = 0,
    val is_show: Int = 1,
)

@Serializable
data class Tag(
    val name: String = "",
    val uuid: String = "",
    val parent_uuid: String? = null,
    val children: List<Tag> = emptyList(),
)

@Serializable
data class Collection(
    val uuid: String = "",
    val name: String = "",
    val path: String = "",
    val introduction: String = "",
    val image: String = "",
    val grade: Int = 0,
    val status: Int = 1,
    val views: Int = 0,
    val projectCount: Int = 0,
    val created_at: String = "",
    val category_uuid: String? = null,
    /**
     * 只有详情接口带这个字段，形态是 `[{uuid, grade}]`（不是计数）。
     * 列表接口给的是 `projectCount`，详情接口没有 `projectCount`，
     * 所以工程数要两边兜底。
     */
    val projects: List<CollectionProjectRef> = emptyList(),
) {
    val totalProjects: Int
        get() = projectCount.takeIf { it > 0 } ?: projects.size
}

@Serializable
data class CollectionProjectRef(
    val uuid: String = "",
    val grade: Int = 0,
)

@Serializable
data class ProfileInfo(
    val country: String = "",
    val education: String = "",
    val school: String = "",
    val company: String = "",
    @Serializable(with = LenientStringSerializer::class)
    val companySize: String = "",
    val career: String = "",
    val site: String = "",
    val skills: List<String> = emptyList(),
    val share: String = "",
    val auto_join: String = "",
)

@Serializable
data class UserInfo(
    val uuid: String = "",
    val username: String = "",
    val nickname: String = "",
    val introduction: String = "",
    val avatar: String = "",
    @Serializable(with = LenientProfileInfoSerializer::class)
    val info: ProfileInfo = ProfileInfo(),
    val follower: List<UserBrief> = emptyList(),
    val following: List<UserBrief> = emptyList(),
    val created_at: String = "",
    val customerCode: String = "",
    val accountCode: String = "",
    val is_fp_office_account: Boolean = false,
    val user_agreement_accepted: Int = 0,
    val can_copy_pro_project_to_oshwlab: Boolean = false,
)

@Serializable
data class UserSummary(
    val uuid: String = "",
    val username: String = "",
    val nickname: String = "",
    val avatar: String = "",
    val team: Boolean = false,
    val introduction: String = "",
    val created_at: String = "",
    val count: UserCounts = UserCounts(),
)

@Serializable
data class UserCounts(
    @SerialName("joined_projects") val joinedProjects: Int = 0,
    val components: Int = 0,
    @SerialName("star_projects") val starProjects: Int = 0,
    val followers: Int = 0,
    val projects: Int = 0,
    val following: Int = 0,
    @SerialName("public_projects") val publicProjects: Int = 0,
    @SerialName("watch_projects") val watchProjects: Int = 0,
    @SerialName("archived_projects") val archivedProjects: Int = 0,
    @SerialName("all_projects") val allProjects: Int = 0,
    @SerialName("created_projects") val createdProjects: Int = 0,
    val modules: Int = 0,
)

@Serializable
data class LikeCount(val uuid: String = "", val likeCount: Int = 0)

/**
 * `/api/notifications` 的真实返回体只有：
 * `user, content, type, status, uuid, updated_at, created_at` —— **没有 title、没有 read**。
 * 已读状态是字符串 `status = "unread"/"read"`，别按布尔 read 判断。
 */
@Serializable
data class Notification(
    val uuid: String = "",
    val type: String = "",
    val content: String = "",
    val title: String = "",
    val read: Boolean = false,
    val status: String = "",
    val created_at: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
    @SerialName("user") val sender: UserBrief? = null,
    val target_uuid: String = "",
    val target_type: String = "",
    val extra: JsonElement? = null,
) {
    /** status 为空（老结构）时退回布尔 read。 */
    val unread: Boolean
        get() = if (status.isNotBlank()) status.equals("unread", ignoreCase = true) else !read
}

@Serializable
data class UnreadCount(val count: Int = 0)

/** `/api/common/{projects|articles}/getUuidByPath` 的返回：只有 uuid */
@Serializable
data class PathUuid(val uuid: String = "")

/** 搜索命中：type = project / user / article / post */
@Serializable
data class SearchPage(
    val page: Int = 1,
    val pageSize: Int = 20,
    val total: Int = 0,
    val totalPage: Int = 0,
    val lists: List<JsonElement> = emptyList(),
)

@Serializable
data class HotWords(val words: List<String> = emptyList())
