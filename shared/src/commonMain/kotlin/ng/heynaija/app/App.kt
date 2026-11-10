package ng.heynaija.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
import ng.heynaija.app.compose.ReplySheet
import ng.heynaija.app.feed.HomeScreen
import ng.heynaija.app.gists.GistScreen
import ng.heynaija.app.media.ImageViewer
import ng.heynaija.app.media.VideoScreen
import ng.heynaija.app.nav.Entry
import ng.heynaija.app.nav.Motion
import ng.heynaija.app.nav.Route
import ng.heynaija.app.nav.Tab
import ng.heynaija.app.nav.WebLinks
import ng.heynaija.app.notifications.NotificationsScreen
import ng.heynaija.app.platform.Platform
import ng.heynaija.app.platform.PushEvents
import ng.heynaija.app.profile.EditProfileScreen
import ng.heynaija.app.profile.MeScreen
import ng.heynaija.app.profile.ProfileScreen
import ng.heynaija.app.report.ReportScreen
import ng.heynaija.app.search.HashtagScreen
import ng.heynaija.app.search.SearchScreen
import ng.heynaija.app.settings.BookmarksScreen
import ng.heynaija.app.settings.DeleteAccountScreen
import ng.heynaija.app.settings.NotificationSettingsScreen
import ng.heynaija.app.settings.SensitiveSettingsScreen
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
        app.pushMayHaveChanged()
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
    app.replying?.let { ReplySheet(app, it) }
    // A tapped notification, whether it started the app or found it open.
    val opened by PushEvents.opened.collectAsStateWithLifecycle()
    LaunchedEffect(opened, account) {
        val link = opened ?: return@LaunchedEffect
        if (account is Account.SignedIn) {
            app.openLink(link)
            PushEvents.handled(link)
        }
    }
    // A link to the site that opened the app. Signed out too: posts and profiles show to anyone, as on the web.
    val webLink by WebLinks.opened.collectAsStateWithLifecycle()
    LaunchedEffect(webLink) {
        val link = webLink ?: return@LaunchedEffect
        app.openWebLink(link)
        WebLinks.handled(link)
    }

    Scaffold(
        containerColor = HeyNaija.colors.bg,
        // Each screen keeps clear of the system bars itself; full-screen ones go edge to edge.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        // No count while the list is on screen, which marks them read, as on the web.
        bottomBar = {
            if (!top.route.fullScreen) TabBar(app, if (top.route == Route.Notifications) 0 else (account as? Account.SignedIn)?.me?.unreadNotifications ?: 0)
        },
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
        is Route.Thread -> ThreadScreen(route.id, route.post, route.gist)
        is Route.Profile -> ProfileScreen(route.handle)
        is Route.Community -> CommunityScreen(route.slug)
        is Route.Hashtag -> HashtagScreen(route.tag)
        Route.Bookmarks -> BookmarksScreen()
        Route.Settings -> SettingsScreen()
        Route.EditProfile -> EditProfileScreen()
        Route.NotificationSettings -> NotificationSettingsScreen()
        Route.SensitiveSettings -> SensitiveSettingsScreen()
        Route.DeleteAccount -> DeleteAccountScreen()
        is Route.Gist -> GistScreen(route.id)
        is Route.Compose -> ComposeScreen(route)
        is Route.Images -> ImageViewer(route.images, route.start)
        is Route.Video -> VideoScreen(route.url, route.startMs)
        is Route.Report -> ReportScreen(route.target, route.what)
        Route.SignIn -> SignInScreen()
        Route.SignUp -> SignUpScreen()
    }
}

/**
 * The tabs: icons on a low bar, and the selected one raised into a bubble that
 * rides on a curve in the bar's top edge, with its name under it. The curve slides
 * from tab to tab. The bubble stands above the bar's own height, so the bar takes
 * no more room from the screen than its icons need.
 */
@Composable
private fun TabBar(app: AppState, unread: Int) {
    val colors = HeyNaija.colors
    val navigator = app.navigator
    val selected = Tab.entries.indexOf(navigator.tab)
    val at by animateFloatAsState(selected.toFloat(), spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
    Box(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                val slot = size.width / Tab.entries.size
                val centre = slot * (at + 0.5f)
                val rise = TabRise.toPx()
                val half = TabCurveWidth.toPx() / 2
                val edge = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(centre - half, 0f)
                    cubicTo(centre - half * 0.45f, 0f, centre - half * 0.55f, -rise, centre, -rise)
                    cubicTo(centre + half * 0.55f, -rise, centre + half * 0.45f, 0f, centre + half, 0f)
                    lineTo(size.width, 0f)
                }
                val bar = Path().apply {
                    addPath(edge)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(bar, colors.surface)
                drawPath(edge, colors.line, style = Stroke(1.dp.toPx()))
            },
    ) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(TabBarHeight)) {
            Tab.entries.forEachIndexed { i, tab ->
                val (icon, label) = when (tab) {
                    Tab.Home -> Icons.Home to "Home"
                    Tab.Search -> Icons.Search to "Search"
                    Tab.Communities -> Icons.People to "Communities"
                    Tab.Notifications -> Icons.Bell to "Notifications"
                    Tab.Me -> Icons.User to "Profile"
                }
                TabItem(icon, label, selected = i == selected, badge = if (tab == Tab.Notifications) unread else 0) { navigator.select(tab) }
            }
        }
    }
}

private val TabBarHeight = 56.dp
/** How far the curve under the selected tab rises above the bar. */
private val TabRise = 27.dp
private val TabCurveWidth = 120.dp
private val TabBubble = 48.dp
/** How far the selected tab's bubble rises: it stops short of the curve's top, leaving a margin above it. */
private val TabLift = 24.dp

@Composable
private fun RowScope.TabItem(icon: ImageVector, label: String, selected: Boolean, badge: Int, onClick: () -> Unit) {
    val colors = HeyNaija.colors
    val lift by animateDpAsState(if (selected) -TabLift else 0.dp, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow))
    val bubble by animateFloatAsState(if (selected) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow))
    val tint by animateColorAsState(if (selected) colors.accentStrong else colors.muted)
    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .selectable(selected = selected, onClick = onClick, role = Role.Tab, indication = null, interactionSource = null)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.offset(y = lift).size(TabBubble), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { scaleX = bubble; scaleY = bubble; alpha = bubble.coerceIn(0f, 1f) }
                    .clip(CircleShape)
                    .background(colors.accentSoft)
                    .border(1.dp, colors.accent.copy(alpha = 0.18f), CircleShape),
            )
            Box {
                Icon(icon, null, Modifier.size(24.dp), tint = tint)
                if (badge > 0) CountBadge(badge, Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-4).dp))
            }
        }
        if (selected) {
            Text(
                label,
                Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp).wrapContentWidth(unbounded = true),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.accentStrong,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}
