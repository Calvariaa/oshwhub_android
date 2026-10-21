package io.github.Calvariaa.oshwhub.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.Calvariaa.oshwhub.data.OshApi
import io.github.Calvariaa.oshwhub.data.SessionManager
import io.github.Calvariaa.oshwhub.data.model.Activity
import io.github.Calvariaa.oshwhub.data.model.Banner
import io.github.Calvariaa.oshwhub.data.model.Collection
import io.github.Calvariaa.oshwhub.data.model.PortalPicture
import io.github.Calvariaa.oshwhub.data.model.ProjectSummary
import io.github.Calvariaa.oshwhub.data.model.UserInfo
import io.github.Calvariaa.oshwhub.ui.components.Avatar
import io.github.Calvariaa.oshwhub.ui.components.BannerCarousel
import io.github.Calvariaa.oshwhub.ui.components.BannerItem
import io.github.Calvariaa.oshwhub.ui.components.LoadingBox
import io.github.Calvariaa.oshwhub.ui.components.MoreRow
import io.github.Calvariaa.oshwhub.ui.components.ProjectGridCard
import io.github.Calvariaa.oshwhub.ui.components.ProjectRowCard
import io.github.Calvariaa.oshwhub.ui.components.ProjectThumb
import io.github.Calvariaa.oshwhub.ui.components.SectionHeader
import io.github.Calvariaa.oshwhub.util.Fmt
import io.github.Calvariaa.oshwhub.util.Html
import io.github.Calvariaa.oshwhub.util.Url
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeState(
    val loading: Boolean = true,
    val error: String? = null,
    val portal: PortalPicture? = null,
    val banners: List<Banner> = emptyList(),
    val notice: String = "",
    val activities: List<Activity> = emptyList(),
    val collections: List<Collection> = emptyList(),
    val featured: List<ProjectSummary> = emptyList(),
    val recommended: List<ProjectSummary> = emptyList(),
    val latest: List<ProjectSummary> = emptyList(),
    val hot: List<ProjectSummary> = emptyList(),
)

class HomeViewModel(
    private val api: OshApi,
    private val session: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    val user: StateFlow<UserInfo?> = session.user

    fun load(force: Boolean = false) {
        if (!force && !_state.value.loading && _state.value.error == null && _state.value.featured.isNotEmpty()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching {
                val portal = async { runCatching { api.portalBanner() }.getOrNull() }
                val banners = async { api.banners(9) }
                val notice = async { api.topNotice() }
                val activities = async { api.competitions(pageSize = 6).lists }
                val collections = async { api.collections(pageSize = 6).lists }
                val featured = async { api.recommendByCollection("officeRecommend", pageSize = 6).lists }
                val recommended = async { api.randomProjects() }
                val latest = async { api.projects(pageSize = 6, type = "new").lists }
                val hot = async { api.projects(pageSize = 6, type = "hot").lists }
                HomeState(
                    loading = false,
                    portal = portal.await(),
                    banners = banners.await(),
                    notice = notice.await(),
                    activities = activities.await(),
                    collections = collections.await(),
                    featured = featured.await(),
                    recommended = recommended.await(),
                    latest = latest.await(),
                    hot = hot.await(),
                )
            }.onSuccess { loaded ->
                _state.value = loaded
            }.onFailure { e ->
                _state.value = _state.value.copy(loading = false, error = e.message ?: "加载失败")
            }
        }
    }

    fun refresh() = load(force = true)
}

@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onSearch: () -> Unit,
    onProject: (String) -> Unit,
    onActivity: (Activity) -> Unit,
    onArticleList: () -> Unit,
    onPostList: () -> Unit,
    onCollectionList: () -> Unit,
    onCollection: (Collection) -> Unit,
    onExplore: () -> Unit,
    onNotifications: () -> Unit,
    onLogin: () -> Unit,
) {
    val state by vm.state.collectAsState()
    val user by vm.user.collectAsState()
    val unread by vm.let { _ -> remember { mutableStateOf(0) } }

    LaunchedEffect(Unit) { vm.load() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            HomeTopBar(
                user = user,
                onSearch = onSearch,
                onNotifications = onNotifications,
                onLogin = onLogin,
            )
        }

        if (state.notice.isNotBlank()) {
            item {
                NoticeBar(state.notice, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            }
        }

        val bannerItems = buildList {
            state.portal?.let { p ->
                add(BannerItem(image = p.picturePath, title = p.pictureName, subtitle = p.keyWord))
            }
            state.banners.forEach { b ->
                add(BannerItem(image = b.images, title = b.name))
            }
        }
        if (bannerItems.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                BannerCarousel(bannerItems)
                Spacer(Modifier.height(6.dp))
            }
        }

        item {
            QuickEntries(
                onArticleList = onArticleList,
                onPostList = onPostList,
                onCollectionList = onCollectionList,
                onExplore = onExplore,
            )
        }

        if (state.activities.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "精彩活动",
                    subtitle = "立创开源硬件平台赛事与征集",
                    action = { MoreRow("全部", onClick = { onArticleList() }) },
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.activities) { activity ->
                        ActivityCard(activity) { onActivity(activity) }
                    }
                }
            }
        }

        if (state.featured.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "官方推荐",
                    subtitle = "立创开源硬件推荐合集",
                    action = { MoreRow("更多", onClick = { onCollectionList() }) },
                )
            }
            item { ProjectGrid(state.featured.take(4), onProject) }
        }

        if (state.hot.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "热门工程",
                    subtitle = "按热度排序的开源硬件项目",
                    action = { MoreRow("广场", onClick = { onExplore() }) },
                )
            }
            item { ProjectGrid(state.hot.take(4), onProject) }
        }

        if (state.recommended.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "随机发现",
                    subtitle = "换一批看看别人的作品",
                    action = { MoreRow("刷新", onClick = { vm.refresh() }) },
                )
            }
            items(state.recommended.take(6)) { project ->
                ProjectRowCard(project, onClick = { onProject(project.uuid) })
            }
        }

        if (state.collections.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "工程合集",
                    action = { MoreRow("全部", onClick = { onCollectionList() }) },
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.collections) { collection ->
                        CollectionMiniCard(collection) { onCollection(collection) }
                    }
                }
            }
        }

        if (state.latest.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "最新发布",
                    action = { MoreRow("更多", onClick = { onExplore() }) },
                )
            }
            items(state.latest) { project ->
                ProjectRowCard(project, onClick = { onProject(project.uuid) })
            }
        }

        if (state.loading) {
            item { LoadingBox() }
        }
    }
}

@Composable
private fun HomeTopBar(
    user: UserInfo?,
    onSearch: () -> Unit,
    onNotifications: () -> Unit,
    onLogin: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "立创开源",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "oshwhub",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onNotifications) {
                BadgedBox(badge = { }) {
                    Icon(Icons.Filled.Notifications, contentDescription = "消息")
                }
            }
            Box(Modifier.clickable(onClick = onLogin)) {
                if (user != null) {
                    Avatar(user.avatar, size = 32)
                } else {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            "登录",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Surface(
            onClick = onSearch,
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Search,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "搜索工程、用户、文章…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NoticeBar(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Campaign, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(6.dp))
            Text(
                Html.toPlain(text),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun QuickEntries(
    onArticleList: () -> Unit,
    onPostList: () -> Unit,
    onCollectionList: () -> Unit,
    onExplore: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        QuickEntry("开源广场", Icons.Outlined.EmojiEvents, onExplore)
        QuickEntry("技术文章", Icons.Outlined.Article, onArticleList)
        QuickEntry("平台公告", Icons.Outlined.Campaign, onPostList)
        QuickEntry("工程合集", Icons.Outlined.CollectionsBookmark, onCollectionList)
    }
}

@Composable
private fun QuickEntry(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ActivityCard(activity: Activity, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.width(240.dp),
    ) {
        Column {
            ProjectThumb(
                url = activity.project_default_image,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)),
            )
            Column(Modifier.padding(10.dp)) {
                Text(
                    activity.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    Html.toPlain(activity.introduction),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "${Fmt.date(activity.competing_start_time)} - ${Fmt.date(activity.competing_end_time)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun CollectionMiniCard(collection: Collection, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.width(200.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            ProjectThumb(
                url = collection.image,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                collection.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${collection.totalProjects} 个工程 · ${Fmt.count(collection.views)} 浏览",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProjectGrid(projects: List<ProjectSummary>, onProject: (String) -> Unit) {
    Column(Modifier.padding(horizontal = 12.dp)) {
        projects.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { project ->
                    Box(Modifier.weight(1f)) {
                        ProjectGridCard(project, onClick = { onProject(project.uuid) })
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
