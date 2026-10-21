package io.github.Calvariaa.oshwhub

import io.github.Calvariaa.oshwhub.data.OshApi
import io.github.Calvariaa.oshwhub.data.crypto.Sm2
import io.github.Calvariaa.oshwhub.data.net.ApiClient
import io.github.Calvariaa.oshwhub.data.net.CookieStore
import kotlinx.coroutines.runBlocking
import org.bouncycastle.asn1.gm.GMNamedCurves
import org.bouncycastle.crypto.engines.SM2Engine
import org.bouncycastle.crypto.params.ECDomainParameters
import org.bouncycastle.crypto.params.ECPrivateKeyParameters
import org.bouncycastle.crypto.params.ParametersWithRandom
import org.bouncycastle.util.BigIntegers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.math.BigInteger
import java.security.SecureRandom
import java.util.Base64

/**
 * 真实接口冒烟测试（需要联网）：
 * `./gradlew :app:testDebugUnitTest`
 */
class ApiSmokeTest {

    private fun api(): OshApi {
        val dir = File(System.getProperty("java.io.tmpdir"), "oshwhub-test").apply { mkdirs() }
        val client = ApiClient(CookieStore(File(dir, "cookies.txt")), dir)
        return OshApi(client)
    }

    @Test
    fun homeEndpointsWork() = runBlocking {
        val api = api()
        val banners = api.banners(9)
        val projects = api.projects(pageSize = 3)
        val tags = api.projectTags()
        val collections = api.collections(pageSize = 3)
        assertTrue("首页工程列表为空", projects.lists.isNotEmpty())
        assertTrue("分类标签为空", tags.isNotEmpty())
        assertTrue("合集为空", collections.lists.isNotEmpty())
        println("banners=${banners.size} projects=${projects.total} tags=${tags.size} collections=${collections.total}")
    }

    @Test
    fun projectDetailAndComments() = runBlocking {
        val api = api()
        val first = api.projects(pageSize = 1).lists.first()
        val detail = api.project(first.uuid)
        assertTrue("工程详情 uuid 不一致", detail.uuid == first.uuid)
        assertTrue("工程详情没有 path", detail.path.isNotBlank())
        val images = api.proImages(detail.uuid)
        println("project=${detail.name} boards=${images.boards.size} attachments=${detail.attachments.size}")
    }

    @Test
    fun searchWorks() = runBlocking {
        val api = api()
        val page = api.search("project", "esp32", pageSize = 5)
        assertTrue("搜索结果为空", page.lists.isNotEmpty())
        assertTrue("搜索结果总数异常", page.total > 0)
        println("search total=${page.total} first=${page.lists.first()}")
    }

    @Test
    fun articlesAndPosts() = runBlocking {
        val api = api()
        val articles = api.articles(pageSize = 3)
        val posts = api.posts(pageSize = 3)
        assertTrue("文章列表为空", articles.lists.isNotEmpty())
        assertTrue("公告列表为空", posts.lists.isNotEmpty())
        println("articles=${articles.total} posts=${posts.total}")
    }

    @Test
    fun userProfileWorks() = runBlocking {
        val api = api()
        val project = api.projects(pageSize = 1).lists.first()
        val info = api.userInfo(project.owner.uuid)
        assertTrue("用户信息为空", info != null && info.uuid == project.owner.uuid)
        val projects = api.userProjects(project.owner.uuid, pageSize = 3)
        println("user=${info?.nickname} projects=${projects.total}")
    }

    /**
     * SM2 自洽性：用 BouncyCastle 生成密钥对 → 用项目的 Sm2.encrypt 加密 →
     * 用私钥解密，明文必须等于 base64(utf8(原始密码))，证明密文格式是标准 C1C3C2。
     */
    @Test
    fun sm2CipherIsStandardC1C3C2() {
        val x9 = GMNamedCurves.getByName("sm2p256v1")
        val domain = ECDomainParameters(x9.curve, x9.g, x9.n, x9.h)
        val d = BigIntegers.createRandomInRange(BigInteger.ONE, x9.n.subtract(BigInteger.ONE), SecureRandom())
        val q = x9.g.multiply(d).normalize()
        val publicHex = "04" + q.affineXCoord.toBigInteger().toString(16).padStart(64, '0') +
            q.affineYCoord.toBigInteger().toString(16).padStart(64, '0')

        val password = "Pa55w0rd!密码"
        val cipherHex = Sm2.encrypt(password, publicHex)

        // 长度校验：04||x||y(65) + C3(32) + C2(len(msg))
        val expectedMsgLen = Base64.getEncoder().encodeToString(password.toByteArray()).toByteArray().size
        assertEquals((65 + 32 + expectedMsgLen) * 2, cipherHex.length)
        assertTrue(cipherHex.startsWith("04"))

        val engine = SM2Engine(SM2Engine.Mode.C1C3C2)
        val priv = ECPrivateKeyParameters(d, domain)
        engine.init(false, priv)
        val cipherBytes = cipherHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val plain = engine.processBlock(cipherBytes, 0, cipherBytes.size)
        val decoded = String(Base64.getDecoder().decode(String(plain)))
        assertEquals(password, decoded)
    }
}
