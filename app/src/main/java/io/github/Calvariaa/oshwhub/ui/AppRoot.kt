package io.github.Calvariaa.oshwhub.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.Calvariaa.oshwhub.data.AppContainer
import io.github.Calvariaa.oshwhub.ui.activity.ActivityDetailScreen
import io.github.Calvariaa.oshwhub.ui.activity.ActivityListScreen
import io.github.Calvariaa.oshwhub.ui.activity.ActivityViewModel
import io.github.Calvariaa.oshwhub.ui.article.ArticleDetailScreen
import io.github.Calvariaa.oshwhub.ui.article.ArticleListScreen
import io.github.Calvariaa.oshwhub.ui.article.ArticleListViewModel
import io.github.Calvariaa.oshwhub.ui.article.ArticleViewModel
import io.github.Calvariaa.oshwhub.ui.auth.LoginScreen
import io.github.Calvariaa.oshwhub.ui.auth.WebLoginScreen
import io.github.Calvariaa.oshwhub.ui.auth.LoginViewModel
import io.github.Calvariaa.oshwhub.ui.collection.CollectionDetailScreen
import io.github.Calvariaa.oshwhub.ui.collection.CollectionListScreen
import io.github.Calvariaa.oshwhub.ui.collection.CollectionListViewModel
import io.github.Calvariaa.oshwhub.ui.collection.CollectionViewModel
import io.github.Calvariaa.oshwhub.ui.explore.ExploreScreen
import io.github.Calvariaa.oshwhub.ui.explore.ExploreViewModel
import io.github.Calvariaa.oshwhub.ui.history.HistoryScreen
import io.github.Calvariaa.oshwhub.ui.home.HomeScreen
import io.github.Calvariaa.oshwhub.ui.home.HomeViewModel
import io.github.Calvariaa.oshwhub.ui.mine.MineScreen
import io.github.Calvariaa.oshwhub.ui.mine.MineViewModel
import io.github.Calvariaa.oshwhub.ui.notification.NotificationViewModel
import io.github.Calvariaa.oshwhub.ui.notification.NotificationsScreen
import io.github.Calvariaa.oshwhub.ui.post.PostDetailScreen
import io.github.Calvariaa.oshwhub.ui.post.PostListScreen
import io.github.Calvariaa.oshwhub.ui.post.PostListViewModel
import io.github.Calvariaa.oshwhub.ui.post.PostViewModel
import io.github.Calvariaa.oshwhub.ui.project.ProjectDetailScreen
import io.github.Calvariaa.oshwhub.ui.project.ProjectViewModel
import io.github.Calvariaa.oshwhub.ui.search.SearchScreen
import io.github.Calvariaa.oshwhub.ui.search.SearchViewModel
import io.github.Calvariaa.oshwhub.ui.settings.SettingsScreen
import io.github.Calvariaa.oshwhub.ui.theme.OshTheme
import io.github.Calvariaa.oshwhub.ui.user.UserScreen
import io.github.Calvariaa.oshwhub.ui.user.UserViewModel

object Routes {
    const val HOME = "home"
    const val EXPLORE = "explore?tag={tag}"
    const val SEARCH = "search?q={q}"
    const val MINE = "mine"
    const val NOTIFICATIONS = "notifications"
    const val PROJECT = "project/{uuid}"
    const val USER = "user/{uuid}"
    const val ARTICLES = "articles"
    const val ARTICLE = "article/{uuid}"
    const val POSTS = "posts"
    const val POST = "post/{uuid}"
    const val COLLECTIONS = "collections"
    const val COLLECTION = "collection/{key}"
    const val ACTIVITIES = "activities"
    const val ACTIVITY = "activity/{uuid}"
    const val LOGIN = "login"
    const val LOGIN_WEB = "login/web"
    const val SETTINGS = "settings"
    const val HISTORY = "history"

    fun explore(tag: String? = null) = if (tag.isNullOrBlank()) "explore?tag=" else "explore?tag=$tag"
    fun search(q: String? = null) = if (q.isNullOrBlank()) "search?q=" else "search?q=${java.net.URLEncoder.encode(q, "UTF-8")}"
    fun project(uuid: String) = "project/$uuid"
    fun user(uuid: String) = "user/$uuid"
    fun article(uuid: String) = "article/$uuid"
    fun post(uuid: String) = "post/$uuid"
    fun collection(key: String) = "collection/$key"
    fun activity(uuid: String) = "activity/$uuid"
}

private data class TopLevel(val route: String, val label: String, val icon: ImageVector)

private val topLevels = listOf(
    TopLevel(Routes.HOME, "首页", Icons.Filled.Home),
    TopLevel(Routes.explore(), "广场", Icons.Filled.Explore),
    TopLevel(Routes.search(), "搜索", Icons.Filled.Search),
    TopLevel(Routes.NOTIFICATIONS, "消息", Icons.Filled.Notifications),
    TopLevel(Routes.MINE, "我的", Icons.Filled.Person),
)

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun AppRoot(container: AppContainer) {
    val themeMode by container.settings.themeMode.collectAsState(initial = "system")
    val dynamic by container.settings.dynamicColor.collectAsState(initial = true)
    val pureBlack by container.settings.pureBlack.collectAsState(initial = false)
    val columns by container.settings.gridColumns.collectAsState(initial = 2)
    val windowSize = calculateWindowSizeClass(
        androidx.compose.ui.platform.LocalContext.current as androidx.activity.ComponentActivity
    )

    OshTheme(
        themeMode = themeMode,
        dynamicColor = dynamic,
        pureBlack = pureBlack,
        widthSizeClass = windowSize.widthSizeClass,
    ) {
        AppScaffold(
            container = container,
            widthSizeClass = windowSize.widthSizeClass,
            columns = columns,
        )
    }
}

@Composable
private fun AppScaffold(
    container: AppContainer,
    widthSizeClass: WindowWidthSizeClass,
    columns: Int,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val unread by container.session.unread.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val expanded = widthSizeClass != WindowWidthSizeClass.Compact

    LaunchedEffect(Unit) {
        container.session.events.collect { message ->
            if (message.isNotBlank()) snackbar.showSnackbar(message)
        }
    }

    val showChrome = currentRoute == null || currentRoute in setOf(
        Routes.HOME, Routes.EXPLORE, Routes.SEARCH, Routes.MINE, Routes.NOTIFICATIONS,
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (!expanded && showChrome) {
                NavigationBar {
                    topLevels.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute?.substringBefore('?') == item.route.substringBefore('?'),
                            onClick = { navigateTop(navController, item.route) },
                            icon = {
                                if (item.route == Routes.NOTIFICATIONS && unread > 0) {
                                    BadgedBox(badge = { Badge { Text("$unread") } }) {
                                        Icon(item.icon, item.label)
                                    }
                                } else {
                                    Icon(item.icon, item.label)
                                }
                            },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            if (expanded) {
                NavigationRail(Modifier.fillMaxHeight().width(88.dp)) {
                    topLevels.forEach { item ->
                        NavigationRailItem(
                            selected = currentRoute?.substringBefore('?') == item.route.substringBefore('?'),
                            onClick = { navigateTop(navController, item.route) },
                            icon = {
                                if (item.route == Routes.NOTIFICATIONS && unread > 0) {
                                    BadgedBox(badge = { Badge { Text("$unread") } }) {
                                        Icon(item.icon, item.label)
                                    }
                                } else {
                                    Icon(item.icon, item.label)
                                }
                            },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
            Box(Modifier.fillMaxSize()) {
                NavHost(navController = navController, startDestination = Routes.HOME) {
                    composable(Routes.HOME) {
                        val vm: HomeViewModel = viewModel { HomeViewModel(container.api, container.session) }
                        HomeScreen(
                            vm = vm,
                            onSearch = { navController.navigate(Routes.search()) },
                            onProject = { navController.navigate(Routes.project(it)) },
                            onActivity = { navController.navigate(Routes.activity(it.uuid)) },
                            onArticleList = { navController.navigate(Routes.ARTICLES) },
                            onPostList = { navController.navigate(Routes.POSTS) },
                            onCollectionList = { navController.navigate(Routes.COLLECTIONS) },
                            onCollection = { navController.navigate(Routes.collection(it.path.ifBlank { it.uuid })) },
                            onExplore = { navigateTop(navController, Routes.explore()) },
                            onNotifications = { navigateTop(navController, Routes.NOTIFICATIONS) },
                            onLogin = { navController.navigate(Routes.LOGIN) },
                        )
                    }

                    composable(
                        Routes.EXPLORE,
                        arguments = listOf(navArgument("tag") { type = NavType.StringType; defaultValue = "" }),
                    ) { entry ->
                        val tag = entry.arguments?.getString("tag")?.takeIf { it.isNotBlank() }
                        val vm: ExploreViewModel = viewModel { ExploreViewModel(container.api) }
                        ExploreScreen(
                            vm = vm,
                            initialTag = tag,
                            columns = columns,
                            onProject = { navController.navigate(Routes.project(it)) },
                        )
                    }

                    composable(
                        Routes.SEARCH,
                        arguments = listOf(navArgument("q") { type = NavType.StringType; defaultValue = "" }),
                    ) { entry ->
                        val q = entry.arguments?.getString("q")?.takeIf { it.isNotBlank() }
                        val vm: SearchViewModel = viewModel { SearchViewModel(container.api, container.settings) }
                        SearchScreen(
                            vm = vm,
                            initialQuery = q,
                            onBack = { navController.popBackStack() },
                            onProject = { navController.navigate(Routes.project(it)) },
                            onUser = { navController.navigate(Routes.user(it)) },
                            onArticle = { navController.navigate(Routes.article(it)) },
                            onPost = { navController.navigate(Routes.post(it)) },
                        )
                    }

                    composable(
                        Routes.PROJECT,
                        arguments = listOf(navArgument("uuid") { type = NavType.StringType }),
                    ) { entry ->
                        val uuid = entry.arguments?.getString("uuid").orEmpty()
                        val vm: ProjectViewModel = viewModel(key = "project-$uuid") {
                            ProjectViewModel(container.api, container.session, uuid)
                        }
                        ProjectDetailScreen(
                            vm = vm,
                            onBack = { navController.popBackStack() },
                            onUser = { navController.navigate(Routes.user(it)) },
                            onProject = { navController.navigate(Routes.project(it)) },
                            onLogin = { navController.navigate(Routes.LOGIN) },
                        )
                    }

                    composable(
                        Routes.USER,
                        arguments = listOf(navArgument("uuid") { type = NavType.StringType }),
                    ) { entry ->
                        val uuid = entry.arguments?.getString("uuid").orEmpty()
                        val vm: UserViewModel = viewModel(key = "user-$uuid") {
                            UserViewModel(container.api, container.session, uuid)
                        }
                        UserScreen(
                            vm = vm,
                            onBack = { navController.popBackStack() },
                            onProject = { navController.navigate(Routes.project(it)) },
                            onArticle = { navController.navigate(Routes.article(it)) },
                        )
                    }

                    composable(Routes.ARTICLES) {
                        val vm: ArticleListViewModel = viewModel { ArticleListViewModel(container.api) }
                        ArticleListScreen(
                            vm = vm,
                            onBack = { navController.popBackStack() },
                            onArticle = { navController.navigate(Routes.article(it)) },
                        )
                    }

                    composable(
                        Routes.ARTICLE,
                        arguments = listOf(navArgument("uuid") { type = NavType.StringType }),
                    ) { entry ->
                        val uuid = entry.arguments?.getString("uuid").orEmpty()
                        val vm: ArticleViewModel = viewModel(key = "article-$uuid") {
                            ArticleViewModel(container.api, uuid)
                        }
                        ArticleDetailScreen(
                            vm = vm,
                            onBack = { navController.popBackStack() },
                            onUser = { navController.navigate(Routes.user(it)) },
                        )
                    }

                    composable(Routes.POSTS) {
                        val vm: PostListViewModel = viewModel { PostListViewModel(container.api) }
                        PostListScreen(
                            vm = vm,
                            onBack = { navController.popBackStack() },
                            onPost = { navController.navigate(Routes.post(it)) },
                        )
                    }

                    composable(
                        Routes.POST,
                        arguments = listOf(navArgument("uuid") { type = NavType.StringType }),
                    ) { entry ->
                        val uuid = entry.arguments?.getString("uuid").orEmpty()
                        val vm: PostViewModel = viewModel(key = "post-$uuid") { PostViewModel(container.api, uuid) }
                        PostDetailScreen(vm = vm, onBack = { navController.popBackStack() })
                    }

                    composable(Routes.COLLECTIONS) {
                        val vm: CollectionListViewModel = viewModel { CollectionListViewModel(container.api) }
                        CollectionListScreen(
                            vm = vm,
                            onBack = { navController.popBackStack() },
                            onCollection = { navController.navigate(Routes.collection(it.path.ifBlank { it.uuid })) },
                        )
                    }

                    composable(
                        Routes.COLLECTION,
                        arguments = listOf(navArgument("key") { type = NavType.StringType }),
                    ) { entry ->
                        val key = entry.arguments?.getString("key").orEmpty()
                        val vm: CollectionViewModel = viewModel(key = "collection-$key") {
                            CollectionViewModel(container.api, key)
                        }
                        CollectionDetailScreen(
                            vm = vm,
                            onBack = { navController.popBackStack() },
                            onProject = { navController.navigate(Routes.project(it)) },
                            columns = columns,
                        )
                    }

                    composable(Routes.ACTIVITIES) {
                        val vm: ActivityViewModel = viewModel { ActivityViewModel(container.api) }
                        ActivityListScreen(
                            vm = vm,
                            onBack = { navController.popBackStack() },
                            onActivity = { navController.navigate(Routes.activity(it.uuid)) },
                        )
                    }

                    composable(
                        Routes.ACTIVITY,
                        arguments = listOf(navArgument("uuid") { type = NavType.StringType }),
                    ) { entry ->
                        val uuid = entry.arguments?.getString("uuid").orEmpty()
                        val vm: ActivityViewModel = viewModel(key = "activity-$uuid") { ActivityViewModel(container.api) }
                        ActivityDetailScreen(vm = vm, uuid = uuid, onBack = { navController.popBackStack() })
                    }

                    composable(Routes.NOTIFICATIONS) {
                        val vm: NotificationViewModel = viewModel { NotificationViewModel(container.api) }
                        NotificationsScreen(
                            vm = vm,
                            onBack = { navController.popBackStack() },
                            onLogin = { navController.navigate(Routes.LOGIN) },
                        )
                    }

                    composable(Routes.MINE) {
                        val vm: MineViewModel = viewModel {
                            MineViewModel(container.api, container.session, container.history)
                        }
                        MineScreen(
                            vm = vm,
                            onLogin = { navController.navigate(Routes.LOGIN) },
                            onProject = { navController.navigate(Routes.project(it)) },
                            onArticle = { navController.navigate(Routes.article(it)) },
                            onCollection = { navController.navigate(Routes.collection(it.path.ifBlank { it.uuid })) },
                            onUser = { navController.navigate(Routes.user(it)) },
                            onNotifications = { navigateTop(navController, Routes.NOTIFICATIONS) },
                            onSettings = { navController.navigate(Routes.SETTINGS) },
                            onHistory = { navController.navigate(Routes.HISTORY) },
                            onFollowList = { navController.navigate(Routes.user(container.session.user.value?.uuid ?: "")) },
                        )
                    }

                    composable(Routes.LOGIN) {
                        val vm: LoginViewModel = viewModel { LoginViewModel(container.session) }
                        LoginScreen(
                            vm = vm,
                            onBack = { navController.popBackStack() },
                            onLoggedIn = { navController.popBackStack() },
                            onWebLogin = { navController.navigate(Routes.LOGIN_WEB) },
                        )
                    }

                    composable(Routes.LOGIN_WEB) {
                        WebLoginScreen(
                            session = container.session,
                            cookieStore = container.cookieStore,
                            onBack = { navController.popBackStack() },
                            onLoggedIn = {
                                runCatching { navController.popBackStack(Routes.LOGIN, inclusive = true) }
                                if (navController.currentDestination?.route == Routes.LOGIN_WEB) {
                                    navController.popBackStack()
                                }
                            },
                        )
                    }

                    composable(Routes.SETTINGS) {
                        SettingsScreen(
                            settings = container.settings,
                            onBack = { navController.popBackStack() },
                            onClearHistory = { container.history.clear() },
                        )
                    }

                    composable(Routes.HISTORY) {
                        HistoryScreen(
                            history = container.history,
                            onBack = { navController.popBackStack() },
                            onProject = { navController.navigate(Routes.project(it)) },
                        )
                    }
                }
            }
        }
    }
}

private fun navigateTop(navController: NavHostController, route: String) {
    navController.navigate(route) {
        popUpTo(Routes.HOME) { inclusive = false }
        launchSingleTop = true
    }
}
