package io.github.Calvariaa.oshwhub

import io.github.Calvariaa.oshwhub.data.UpdateChecker
import io.github.Calvariaa.oshwhub.util.DeepLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 版本比较：`BuildConfig.VERSION_NAME` 与 GitHub tag 都可能带 `v` 前缀和预发布后缀 */
class UpdateCheckerTest {

    @Test
    fun `numeric bump is newer`() {
        assertTrue(UpdateChecker.isNewer("1.0.3", "1.0.2"))
        assertTrue(UpdateChecker.isNewer("1.1.0", "1.0.9"))
        assertTrue(UpdateChecker.isNewer("2.0.0", "1.99.99"))
    }

    @Test
    fun `same version is not newer`() {
        assertFalse(UpdateChecker.isNewer("1.0.2", "1.0.2"))
        assertFalse(UpdateChecker.isNewer("1.0", "1.0.0"))
        assertFalse(UpdateChecker.isNewer("1.0.0.0", "1.0.0"))
    }

    @Test
    fun `older remote is not newer`() {
        assertFalse(UpdateChecker.isNewer("1.0.1", "1.0.2"))
        assertFalse(UpdateChecker.isNewer("0.9.9", "1.0.0"))
    }

    @Test
    fun `v prefix and prerelease suffix are handled`() {
        assertTrue(UpdateChecker.isNewer("v1.0.3", "1.0.2"))
        // 同号下正式版比预发布版新
        assertTrue(UpdateChecker.isNewer("1.0.3", "1.0.3-rc.1"))
        assertFalse(UpdateChecker.isNewer("1.0.3-rc.1", "1.0.3"))
        // 预发布序号更大的更新
        assertTrue(UpdateChecker.isNewer("1.0.3-rc.2", "1.0.3-rc.1"))
        // build 元数据不影响比较
        assertFalse(UpdateChecker.isNewer("1.0.2+build9", "1.0.2"))
    }
}

/** 站内链接解析：正文里点链接必须落原生页面，不能跳浏览器 */
class DeepLinkTest {

    @Test
    fun `project path is recognised`() {
        val target = DeepLink.parse("https://oshwhub.com/alice/blinky-board")
        assertTrue(target is DeepLink.Target.Project)
        assertEquals("alice/blinky-board", (target as DeepLink.Target.Project).path)
    }

    @Test
    fun `article collection activity and user are recognised`() {
        assertTrue(DeepLink.parse("https://oshwhub.com/article/abc") is DeepLink.Target.Article)
        assertEquals(
            "officeRecommend",
            (DeepLink.parse("https://oshwhub.com/collection/officeRecommend") as DeepLink.Target.Collection).key,
        )
        assertEquals(
            "xinghuo2026",
            (DeepLink.parse("https://oshwhub.com/activities/xinghuo2026") as DeepLink.Target.Activity).path,
        )
        assertEquals(
            "alice",
            (DeepLink.parse("https://oshwhub.com/alice") as DeepLink.Target.User).username,
        )
    }

    @Test
    fun `bare and relative links normalise to absolute site urls`() {
        assertEquals("https://oshwhub.com/a/b", DeepLink.normalize("a/b"))
        assertEquals("https://oshwhub.com/a/b", DeepLink.normalize("/a/b"))
        assertEquals("https://oshwhub.com/a/b", DeepLink.normalize("oshwhub.com/a/b"))
        assertEquals("https://oshwhub.com/a/b", DeepLink.normalize("http://oshwhub.com/a/b"))
    }

    @Test
    fun `first link is extracted from notification content`() {
        assertEquals(
            "https://oshwhub.com/alice/blinky",
            DeepLink.firstLink("""<a href="https://oshwhub.com/alice/blinky">赞了你的工程</a>"""),
        )
        assertEquals(
            "https://oshwhub.com/alice/blinky",
            DeepLink.firstLink("[赞了你的工程](https://oshwhub.com/alice/blinky)"),
        )
        assertEquals("https://oshwhub.com/x", DeepLink.firstLink("看这里 https://oshwhub.com/x 谢谢"))
        assertEquals(null, DeepLink.firstLink("纯文本，没有链接"))
    }
}
