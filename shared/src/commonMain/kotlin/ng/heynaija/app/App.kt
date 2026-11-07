package ng.heynaija.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import kotlinx.coroutines.launch
import ng.heynaija.app.auth.BirthDateScreen
import ng.heynaija.app.auth.SignInScreen
import ng.heynaija.app.auth.SignUpScreen
import ng.heynaija.app.auth.WelcomeScreen
import ng.heynaija.app.communities.CommunitiesScreen
import ng.heynaija.app.communities.CommunityScreen
import ng.heynaija.app.compose.ComposeScreen
import ng.heynaija.app.feed.HomeScreen
import ng.heynaija.app.media.ImageViewer
import ng.heynaija.app.media.VideoScreen
import ng.heynaija.app.nav.Entry
import ng.heynaija.app.nav.Motion
import ng.heynaija.app.nav.Route
import ng.heynaija.app.nav.Tab
import ng.heynaija.app.notifications.NotificationsScreen
import ng.heynaija.app.platform.Platform
import ng.heynaija.app.profile.EditProfileScreen
import ng.heynaija.app.profile.MeScreen
import ng.heynaija.app.profile.ProfileScreen
import ng.heynaija.app.report.ReportScreen
import ng.heynaija.app.search.HashtagScreen
import ng.heynaija.app.search.SearchScreen
import ng.heynaija.app.settings.BookmarksScreen
import ng.heynaija.app.settings.DeleteAccountScreen
import ng.heynaija.app.settings.NotificationSettingsScreen
import ng.heynaija.app.settings.SettingsScreen
import ng.heynaija.app.theme.HeyNaija
import ng.heynaija.app.theme.HeyNaijaTheme
import ng.heynaija.app.thread.ThreadScreen
import ng.heynaija.app.ui.CountBadge
import ng.heynaija.app.ui.Icons
import ng.heynaija.app.ui.Loading

/** The whole app, the same on Android and iOS. */
@Composable
fun App(platform: Platform) {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory()) }
            .crossfade(true)
            .build()
    }
    val app = remember { AppState(platform).also { it.start() } }
    HeyNaijaTheme {
        CompositionLocalProvider(LocalApp provides app) { Root(app) }
    }
}

@Composable
private fun Root(app: AppState) {
    val account by app.account.collectAsStateWithLifecycle()
    // Coming back to the app: the unread count may have moved.
    LifecycleResumeEffect(Unit) {
        app.scope.launch { app.refreshMe() }
        onPauseOrDispose {}
    }
    Box(Modifier.fillMaxSize().background(HeyNaija.colors.bg)) {
        val me = (account as? Account.SignedIn)?.me
        when {
            account is Account.Unknown -> Loading()
            // Nothing else until a member who joined before the age question answers it (ADR 0020).
            me != null && me.birthDateNeeded -> BirthDateScreen()
            me != null && !me.onboarded -> WelcomeScreen()
            else -> Shell(app)
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Shell(app: AppState) {
    val navigator = app.navigator
    val top = navigator.top
    val snackbar = remember { SnackbarHostState() }
    val account by app.account.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { app.messages.collect { snackbar.showSnackbar(it) } }
    BackHandler(enabled = navigator.canPop) { navigator.pop() }

    Scaffold(
        containerColor = HeyNaija.colors.bg,
        // Each screen keeps clear of the system bars itself; full-screen ones go edge to edge.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { if (!top.route.fullScreen) TabBar(app, (account as? Account.SignedIn)?.me?.unreadNotifications ?: 0) },
    ) { padding ->
        val holder = rememberSaveableStateHolder()
        AnimatedContent(
            targetState = top,
            modifier = Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding()),
            transitionSpec = {
                // Deeper slides in from the right, back slides out to it, and switching tabs fades.
                when (navigator.motion) {
                    Motion.Tab -> fadeIn(tween(150)) togetherWith fadeOut(tween(150))
                    Motion.Push -> (slideInHorizontally(tween(260)) { it } + fadeIn(tween(200))) togetherWith
                        (slideOutHorizontally(tween(260)) { -it / 4 } + fadeOut(tween(200)))
                    Motion.Pop -> (slideInHorizontally(tween(260)) { -it / 4 } + fadeIn(tween(200))) togetherWith
                        (slideOutHorizontally(tween(260)) { it } + fadeOut(tween(200)))
                }
            },
            contentKey = { it.id },
        ) { entry ->
            holder.SaveableStateProvider(entry.id) {
                CompositionLocalProvider(LocalViewModelStoreOwner provides entry) { Screen(entry) }
            }
        }
    }
}

@Composable
private fun Screen(entry: Entry) {
    when (val route = entry.route) {
        Route.Home -> HomeScreen()
        Route.Search -> SearchScreen()
        Route.Communities -> CommunitiesScreen()
        Route.Notifications -> NotificationsScreen()
        Route.Me -> MeScreen()
        is Route.Thread -> ThreadScreen(route.id, route.post)
        is Route.Profile -> ProfileScreen(route.handle)
        is Route.Community -> CommunityScreen(route.slug)
        is Route.Hashtag -> HashtagScreen(route.tag)
        Route.Bookmarks -> BookmarksScreen()
        Route.Settings -> SettingsScreen()
        Route.EditProfile -> EditProfileScreen()
        Route.NotificationSettings -> NotificationSettingsScreen()
        Route.DeleteAccount -> DeleteAccountScreen()
        is Route.Compose -> ComposeScreen(route)
        is Route.Images -> ImageViewer(route.images, route.start)
        is Route.Video -> VideoScreen(route.url)
        is Route.Report -> ReportScreen(route.target, route.what)
        Route.SignIn -> SignInScreen()
        Route.SignUp -> SignUpScreen()
    }
}

@Composable
private fun TabBar(app: AppState, unread: Int) {
    val colors = HeyNaija.colors
    val navigator = app.navigator
    Column {
        HorizontalDivider(color = colors.line)
        NavigationBar(containerColor = colors.bg, tonalElevation = 0.dp) {
            for (tab in Tab.entries) {
                val (icon, label) = when (tab) {
                    Tab.Home -> Icons.Home to "Home"
                    Tab.Search -> Icons.Search to "Search"
                    Tab.Communities -> Icons.People to "Communities"
                    Tab.Notifications -> Icons.Bell to "Notifications"
                    Tab.Me -> Icons.User to "Profile"
                }
                NavigationBarItem(
                    selected = navigator.tab == tab,
                    onClick = { navigator.select(tab) },
                    icon = {
                        Box {
                            Icon(icon, label, Modifier.size(24.dp))
                            if (tab == Tab.Notifications && unread > 0) {
                                CountBadge(unread, Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-4).dp))
                            }
                        }
                    },
                    alwaysShowLabel = false,
                    label = { Text(label) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = colors.accentStrong,
                        selectedTextColor = colors.accentStrong,
                        indicatorColor = colors.accentSoft,
                        unselectedIconColor = colors.muted,
                        unselectedTextColor = colors.muted,
                    ),
                )
            }
        }
    }
}
