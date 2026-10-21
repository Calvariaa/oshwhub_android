package io.github.Calvariaa.oshwhub

import io.github.Calvariaa.oshwhub.data.OshApi
import io.github.Calvariaa.oshwhub.data.net.ApiClient
import io.github.Calvariaa.oshwhub.data.net.CookieStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 全站接口解析覆盖测试（需要联网）。
 *
 * 目的：本站后端字段类型不固定（同一字段可能返回对象 / 空数组 / 0 / ""），
 * 任何一处 schema 不匹配都会让整条数据变成空。这里把桌面端 web 会调的
 * 只读接口全跑一遍，确保每个响应都能解析出数据。
 */
class ApiCoverageTest {

    private fun api(): OshApi {
        val dir = File(System.getProperty("java.io.tmpdir"), "oshwhub-coverage").apply { mkdirs() }
        return OshApi(ApiClient(CookieStore(File(dir, "cookies.txt")), dir))
    }

    @Test
    fun homeSections() = runBlocking {
        val api = api()
        val banners = api.banners(9)
        val tags = api.projectTags()
        val recommendTags = api.projectTags(recommendOnly = true)
        val competitions = api.competitions(pageSize = 3)
        val random = api.randomProjects()
        val hot = api.hotWords()
        println("banners=${banners.size} tags=${tags.size} recTags=${recommendTags.size} " +
            "competitions=${competitions.total} random=${random.size} hot=${hot.size}")
        assertTrue("banner 解析为空", banners.isNotEmpty())
        assertTrue("工程分类解析为空", tags.isNotEmpty())
        assertTrue("推荐分类解析为空", recommendTags.isNotEmpty())
        assertTrue("竞赛列表解析为空", competitions.lists.isNotEmpty())
        assertTrue("随机推荐工程解析为空", random.isNotEmpty())
    }

    @Test
    fun projectSection() = runBlocking {
        val api = api()
        val page = api.projects(pageSize = 5)
        val first = page.lists.first()
        val detail = api.project(first.uuid)
        val images = api.proImages(first.uuid)
        val related = api.relatedProjects(first.uuid, detail.project_tags.take(3))
        val comments = api.comments("project", first.uuid, pageSize = 5)
        val recommendations = api.recommendations(itemId = first.uuid)
        println(
            "projects=${page.total} detail=${detail.name} boards=${images.boards.size} " +
                "related=${related.lists.size} comments=${comments.total} rec=${recommendations.size}",
        )
        assertTrue("工程列表解析为空", page.lists.isNotEmpty())
        assertTrue("工程详情为空", detail.uuid == first.uuid && detail.name.isNotBlank())
        assertTrue("工程图片(板子)为空", images.boards.isNotEmpty())
        assertTrue("相关推荐为空", related.lists.isNotEmpty())
        assertTrue("评论解析为空", comments.lists.isNotEmpty())
        assertTrue("猜你喜欢为空", recommendations.isNotEmpty())
    }

    @Test
    fun collectionSection() = runBlocking {
        val api = api()
        val page = api.collections(pageSize = 5)
        val first = page.lists.first()
        // 站点用 path 作为地址，接口只认 uuid —— 这正是 CollectionViewModel 的处理路径
        val resolved = api.resolveCollectionKey(first.path.ifBlank { first.uuid })
        val projects = api.collectionProjects(resolved!!.uuid, pageSize = 5)
        println("collections=${page.total} first=${first.name} path=${first.path} resolved=${resolved.uuid} projects=${projects.total}")
        assertTrue("合集列表解析为空", page.lists.isNotEmpty())
        assertTrue("合集 path 解析不到 uuid", resolved.uuid.isNotBlank())
        assertTrue("合集内工程为空", projects.lists.isNotEmpty())
    }

    @Test
    fun articleSection() = runBlocking {
        val api = api()
        val page = api.articles(pageSize = 5)
        val first = page.lists.first()
        val detail = api.article(first.uuid)
        val comments = api.comments("article", first.uuid, pageSize = 5)
        println("articles=${page.total} first=${first.title} detail=${detail.title} comments=${comments.total}")
        assertTrue("文章列表解析为空", page.lists.isNotEmpty())
        assertTrue("文章详情为空", detail.uuid == first.uuid && detail.title.isNotBlank())
    }

    @Test
    fun forumSection() = runBlocking {
        val api = api()
        val page = api.posts(pageSize = 5)
        val first = page.lists.first()
        val detail = api.post(first.uuid)
        println("posts=${page.total} first=${first.title} detail=${detail.title}")
        assertTrue("公告列表解析为空", page.lists.isNotEmpty())
        assertTrue("公告详情为空", detail.uuid == first.uuid || detail.title.isNotBlank())
    }

    @Test
    fun userSection() = runBlocking {
        val api = api()
        // 固定用个人作者 bhwj：团队账号（如立创开发板）的工程走 /api/teams，projects 会是 0
        val owner = "3e6aa785ebfe40da8bba58eab2ade786"
        val info = api.userInfo(owner)
        val projects = api.userProjects(owner, pageSize = 5)
        val articles = api.userArticles(owner, pageSize = 5)
        val posts = api.userPosts(owner, pageSize = 5)
        val star = api.userStar(owner, pageSize = 5)
        val collections = api.userCollections(owner, pageSize = 5)
        val follows = api.userFollows(owner, pageSize = 5)
        val followers = api.userFollowers(owner, pageSize = 5)
        val likes = api.userLikeCount(owner)
        println(
            "user=${info?.nickname}($owner) projects=${projects.total} articles=${articles.total} posts=${posts.total} " +
                "star=${star.total} collections=${collections.total} follows=${follows.total} " +
                "followers=${followers.total} likes=$likes",
        )
        assertTrue("用户信息解析为空", info != null && info.uuid == owner)
        assertTrue("用户工程为空", projects.lists.isNotEmpty())
        assertTrue("用户粉丝列表为空", followers.lists.isNotEmpty())
        assertTrue("用户点赞数解析失败", likes > 0)
    }

    @Test
    fun searchSection() = runBlocking {
        val api = api()
        val projects = api.search("project", "esp32", pageSize = 3)
        val users = api.search("user", "esp32", pageSize = 3)
        val articles = api.search("article", "esp32", pageSize = 3)
        val count = api.searchCount("project", "esp32")
        println("projects=${projects.total} users=${users.total} articles=${articles.total} count=$count")
        assertTrue("工程搜索结果为空", projects.lists.isNotEmpty())
        assertTrue("用户搜索结果为空", users.lists.isNotEmpty())
        assertTrue("搜索计数为 0", count > 0)
    }

    @Test
    fun activitySection() = runBlocking {
        val api = api()
        val page = api.competitions(pageSize = 5)
        val first = page.lists.first()
        println("activities=${page.total} first=${first.name}")
        assertTrue("活动列表解析为空", page.lists.isNotEmpty())
        assertTrue("活动标题为空", first.name.isNotBlank())
    }

    /** 首页 HomeViewModel 实际用到的全部接口，缺一个首页就会空一块。 */
    @Test
    fun homeViewModelEndpoints() = runBlocking {
        val api = api()
        val portal = api.portalBanner()
        val notice = api.topNotice()
        val featured = api.recommendByCollection("officeRecommend", pageSize = 4)
        val latest = api.projects(pageSize = 4, type = "new")
        val hot = api.projects(pageSize = 4, type = "hot")
        println(
            "portal=${portal?.pictureName ?: "-"} notice=${notice.take(20)} featured=${featured.total} " +
                "latest=${latest.lists.size} hot=${hot.lists.size}",
        )
        assertTrue("首页顶部运营位为空", portal != null && portal.picturePath.isNotBlank())
        assertTrue("首页推荐合集工程为空", featured.lists.isNotEmpty())
        assertTrue("首页最新工程为空", latest.lists.isNotEmpty())
        assertTrue("首页最热工程为空", hot.lists.isNotEmpty())
    }
}
