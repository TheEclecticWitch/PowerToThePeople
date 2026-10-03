package com.theeclecticwitch.powertothepeople

import androidx.compose.foundation.background
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavGraphBuilder
import androidx.compose.material3.VerticalDivider
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import com.theeclecticwitch.powertothepeople.constitution.AmendmentScreen
import com.theeclecticwitch.powertothepeople.constitution.ArticleScreen
import com.theeclecticwitch.powertothepeople.constitution.ConstitutionScreen
import com.theeclecticwitch.powertothepeople.constitution.OriginalScreen
import com.theeclecticwitch.powertothepeople.constitution.SignaturesScreen
import com.theeclecticwitch.powertothepeople.civics.HowGovernmentWorksScreen
import com.theeclecticwitch.powertothepeople.congress.BillScreen
import com.theeclecticwitch.powertothepeople.congress.CongressNav
import com.theeclecticwitch.powertothepeople.congress.ComingUpScreen
import com.theeclecticwitch.powertothepeople.congress.StateBillScreen
import com.theeclecticwitch.powertothepeople.elections.CandidateScreen
import com.theeclecticwitch.powertothepeople.elections.ElectionsScreen
import com.theeclecticwitch.powertothepeople.elections.RaceScreen
import com.theeclecticwitch.powertothepeople.alerts.Alerts
import com.theeclecticwitch.powertothepeople.alerts.AlertsScreen
import com.theeclecticwitch.powertothepeople.alerts.Notifications
import com.theeclecticwitch.powertothepeople.congress.CongressScreen
import com.theeclecticwitch.powertothepeople.congress.LegislationScreen
import com.theeclecticwitch.powertothepeople.congress.MemberVotesScreen
import com.theeclecticwitch.powertothepeople.congress.RecentVotesScreen
import com.theeclecticwitch.powertothepeople.congress.SessionScreen
import com.theeclecticwitch.powertothepeople.congress.SponsoredBillsScreen
import com.theeclecticwitch.powertothepeople.congress.VoteScreen
import com.theeclecticwitch.powertothepeople.data.Http
import com.theeclecticwitch.powertothepeople.debt.DebtScreen
import com.theeclecticwitch.powertothepeople.doomsday.DoomsdayScreen
import com.theeclecticwitch.powertothepeople.home.HomeScreen
import com.theeclecticwitch.powertothepeople.location.LocationScreen
import com.theeclecticwitch.powertothepeople.more.AboutScreen
import com.theeclecticwitch.powertothepeople.more.MoreScreen
import com.theeclecticwitch.powertothepeople.more.SettingsScreen
import com.theeclecticwitch.powertothepeople.voting.VotingScreen
import com.theeclecticwitch.powertothepeople.more.SourcesScreen
import com.theeclecticwitch.powertothepeople.ui.LocalOpenSettings
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material.icons.filled.Info
import com.theeclecticwitch.powertothepeople.officials.DirectoryScreen
import com.theeclecticwitch.powertothepeople.officials.StateLegislatorsScreen
import com.theeclecticwitch.powertothepeople.officials.EditOfficialScreen
import com.theeclecticwitch.powertothepeople.officials.Level
import com.theeclecticwitch.powertothepeople.officials.OfficialDetailScreen
import com.theeclecticwitch.powertothepeople.officials.OfficialsScreen
import com.theeclecticwitch.powertothepeople.ui.theme.PowerTheme
import com.theeclecticwitch.powertothepeople.ui.theme.OnSurfaceColors
import com.theeclecticwitch.powertothepeople.ui.FitText
import kotlinx.serialization.Serializable

// The places the app can go. Type-safe routes: each carries exactly what its screen needs.
@Serializable object HomeRoute
@Serializable object ConstitutionRoute
@Serializable object CongressRoute
@Serializable object BillOfRightsRoute
@Serializable data class OriginalRoute(val index: Int)
@Serializable object OfficialsRoute
@Serializable object MoreRoute
@Serializable data class ArticleRoute(val number: Int)
@Serializable data class AmendmentRoute(val number: Int)
@Serializable object SignaturesRoute
@Serializable object DebtRoute
@Serializable object DoomsdayRoute
@Serializable object SessionsRoute
@Serializable object LocationRoute
@Serializable data class OfficialRoute(val id: String)
@Serializable data class EditOfficialRoute(val id: String? = null, val level: String = "Local")
@Serializable data class VoteRoute(val chamber: String, val session: Int, val roll: Int)
@Serializable data class BillRoute(val id: String)
@Serializable data class MemberVotesRoute(val id: String)
@Serializable data class SponsoredBillsRoute(val id: String)
@Serializable data class StateBillRoute(val id: String)
@Serializable object ElectionsRoute
@Serializable data class RaceRoute(val id: String)
@Serializable data class CandidateRoute(val raceId: String, val key: String)
@Serializable data class DirectoryRoute(val chamber: String? = null)
@Serializable object SettingsRoute
@Serializable object VotingRoute
@Serializable object ComingUpRoute
@Serializable object AlertsRoute
@Serializable object HowGovernmentRoute
@Serializable object LegislationRoute
@Serializable data class StateLegislatorsRoute(val state: String)
@Serializable object RecentVotesRoute
@Serializable object SourcesRoute
@Serializable object AboutRoute
@Serializable object DetailEmptyRoute

/** The list column beside the detail pane on a wide window. */
private val ListPaneWidth = 440.dp

/**
 * On a wide window these screens are lists: they keep a column on the left, and what the reader opens from
 * them (a vote, a bill, a member, an article) shows on the right. Overview stays a full-width dashboard.
 */
private val splitRoutes = listOf(
    CongressRoute::class, LegislationRoute::class, ComingUpRoute::class, RecentVotesRoute::class,
    DirectoryRoute::class, OfficialsRoute::class, StateLegislatorsRoute::class, AlertsRoute::class,
    ConstitutionRoute::class, BillOfRightsRoute::class, MemberVotesRoute::class, SponsoredBillsRoute::class,
    ElectionsRoute::class,
)

private data class Tab(val label: String, val icon: ImageVector, val route: Any)

private val tabs = listOf(
    Tab("Overview", Icons.Default.Home, HomeRoute),
    Tab("Civics", Icons.Default.Info, HowGovernmentRoute),
    Tab("Congress", Icons.AutoMirrored.Filled.List, CongressRoute),
    Tab("Constitution", Icons.Default.Star, ConstitutionRoute),
    Tab("More", Icons.Default.Menu, MoreRoute),
)

@Composable
fun App() {
    // Official portraits load over the same HTTP client as everything else.
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory(httpClient = { Http.client })) }
            .build()
    }
    // Background checks survive an update or a restore only if asked for again; asking twice is harmless.
    LaunchedEffect(Unit) { if (Alerts.prefs.value.notify) Notifications.schedule(true) }
    PowerTheme {
        val nav = rememberNavController()
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // A phone gets a bar along the bottom; a tablet or desktop window gets a rail down the side.
            val wide = maxWidth >= 720.dp
            if (wide) {
                val detail = rememberNavController()
                val mainEntry by nav.currentBackStackEntryAsState()
                val split = mainEntry?.destination?.let { d -> splitRoutes.any { d.hasRoute(it) } } == true
                // A new list, or a new tab, starts with an empty detail pane.
                LaunchedEffect(mainEntry?.id) {
                    if (detail.currentBackStackEntry != null) detail.popBackStack(DetailEmptyRoute, inclusive = false)
                }
                Row(Modifier.fillMaxSize()) {
                    OnSurfaceColors { NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        val selected = selectedTab(nav)
                        tabs.forEach { tab ->
                            // A rail item offers its label the whole window; a fixed width keeps the rail narrow.
                            NavigationRailItem(tab == selected, { goToTab(nav, tab) }, icon = { Icon(tab.icon, null) }, label = { FitText(tab.label, MaterialTheme.typography.labelMedium, Modifier.width(76.dp)) })
                        }
                    } }
                    Scaffold(
                        modifier = if (split) Modifier.width(ListPaneWidth) else Modifier.weight(1f),
                        contentWindowInsets = WindowInsets(0),
                    ) { padding ->
                        AppNavHost(nav, Modifier.padding(padding).consumeWindowInsets(padding), detail = { if (split) detail else null })
                    }
                    if (split) {
                        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Box(Modifier.weight(1f).background(MaterialTheme.colorScheme.background)) { DetailNavHost(detail, nav) }
                    }
                }
            } else {
                Scaffold(
                    contentWindowInsets = WindowInsets(0),
                    bottomBar = { OnSurfaceColors {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                            val selected = selectedTab(nav)
                            tabs.forEach { tab ->
                                NavigationBarItem(tab == selected, { goToTab(nav, tab) }, icon = { Icon(tab.icon, null) }, label = { FitText(tab.label, MaterialTheme.typography.labelMedium) })
                            }
                        } }
                    },
                ) { padding ->
                    AppNavHost(nav, Modifier.padding(padding).consumeWindowInsets(padding))
                }
            }
        }
    }
}

/**
 * Which tab is showing: the nearest tab screen beneath whatever is on top. An official's page
 * opened from the Officials tab belongs to that tab, so it stays lit.
 */
@Composable
private fun selectedTab(nav: NavHostController): Tab? {
    val stack by nav.currentBackStack.collectAsState()
    return tabOf(stack.map { it.destination })
}

private fun tabOf(destinations: List<NavDestination>): Tab? =
    destinations.asReversed().firstNotNullOfOrNull { d -> tabs.firstOrNull { d.hasRoute(it.route::class) } }

/**
 * Switches tabs, keeping each tab's own history. Tapping the tab already showing goes back to its
 * first page. Anything that sends the reader to another tab's main screen must come through here:
 * navigating there directly would stack that screen inside the current tab's history, and the
 * Home tab once came back showing an official's page because of it.
 */
private fun goToTab(nav: NavHostController, tab: Tab) {
    if (tabOf(nav.currentBackStack.value.map { it.destination }) == tab) {
        nav.popBackStack(tab.route, inclusive = false)
        return
    }
    // Every tab returns to where the reader left it, except More, which always opens at its menu (Rod's choice):
    // it is a list of places, not a place, so coming back to it should show the list.
    val leavingMore = tabOf(nav.currentBackStack.value.map { it.destination })?.route == MoreRoute
    nav.navigate(tab.route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = !leavingMore }
        launchSingleTop = true
        restoreState = tab.route != MoreRoute
    }
}

@Composable
private fun AppNavHost(nav: NavHostController, modifier: Modifier, detail: () -> NavHostController? = { null }) {
    val back: () -> Unit = { nav.popBackStack() }
    // Beside a list on a wide window, a page opens in the detail pane, replacing whatever was there.
    val open: (Any) -> Unit = { route ->
        val pane = detail()
        if (pane != null) pane.navigate(route) { popUpTo(DetailEmptyRoute) } else nav.navigate(route)
    }
    val congressNav = CongressNav(
        vote = { chamber, session, roll -> open(VoteRoute(chamber, session, roll)) },
        bill = { open(BillRoute(it)) },
        official = { open(OfficialRoute(it)) },
        memberVotes = { open(MemberVotesRoute(it)) },
        sponsoredBills = { open(SponsoredBillsRoute(it)) },
        recentVotes = { nav.navigate(RecentVotesRoute) },
        setLocation = { nav.navigate(LocationRoute) },
        stateBill = { open(StateBillRoute(it)) },
    )
    // The gear on every main screen opens Settings.
    CompositionLocalProvider(LocalOpenSettings provides { nav.navigate(SettingsRoute) }) {
    NavHost(nav, startDestination = HomeRoute, modifier = modifier) {
        composable<ComingUpRoute> { ComingUpScreen(back, congressNav) }
        composable<AlertsRoute> { AlertsScreen(back, congressNav, onLegislation = { nav.navigate(LegislationRoute) }) }
        composable<ElectionsRoute> {
            ElectionsScreen(back, onRace = { open(RaceRoute(it)) }, onSetLocation = { nav.navigate(LocationRoute) })
        }
        composable<VotingRoute> { VotingScreen(back, { nav.navigate(LocationRoute) }, onElections = { nav.navigate(ElectionsRoute) }) }
        composable<SettingsRoute> { SettingsScreen(back) { nav.navigate(LocationRoute) } }
        composable<HomeRoute> {
            HomeScreen(
                onDebt = { nav.navigate(DebtRoute) },
                onDoomsday = { nav.navigate(DoomsdayRoute) },
                onSessions = { nav.navigate(SessionsRoute) },
                onLegislation = { nav.navigate(LegislationRoute) },
                congressNav = congressNav,
                onConstitution = { goToTab(nav, tabs[3]) },
                onBillOfRights = { nav.navigate(BillOfRightsRoute) },
                onAllCongress = { nav.navigate(DirectoryRoute()) },
                onVoting = { nav.navigate(VotingRoute) },
                onHowGovernment = { goToTab(nav, tabs[1]) },
                onOfficial = { nav.navigate(OfficialRoute(it)) },
                onSetLocation = { nav.navigate(LocationRoute) },
                onAlerts = { nav.navigate(AlertsRoute) },
                onComingUp = { nav.navigate(ComingUpRoute) },
                onElections = { nav.navigate(ElectionsRoute) },
                onSources = { nav.navigate(SourcesRoute) },
            )
        }
        composable<ConstitutionRoute> {
            ConstitutionScreen(
                onArticle = { open(ArticleRoute(it)) },
                onAmendment = { open(AmendmentRoute(it)) },
                onSignatures = { nav.navigate(SignaturesRoute) },
                onOriginal = { nav.navigate(OriginalRoute(it)) },
                onHowGovernment = { goToTab(nav, tabs[1]) },
            )
        }
        composable<BillOfRightsRoute> {
            ConstitutionScreen(
                onArticle = { open(ArticleRoute(it)) },
                onAmendment = { open(AmendmentRoute(it)) },
                onSignatures = { nav.navigate(SignaturesRoute) },
                onOriginal = { nav.navigate(OriginalRoute(it)) },
                startAtBillOfRights = true,
                onBack = back,
            )
        }
        composable<OriginalRoute> { entry ->
            OriginalScreen(entry.toRoute<OriginalRoute>().index, back) { page ->
                // The typed text of the same part: the Bill of Rights opens at the First Amendment.
                when {
                    page.document == "The Bill of Rights" -> nav.navigate(AmendmentRoute(1))
                    else -> nav.navigate(ArticleRoute(listOf(1, 1, 2, 6)[page.page - 1]))
                }
            }
        }
        composable<CongressRoute> {
            CongressScreen(
                nav = congressNav,
                onLegislation = { nav.navigate(LegislationRoute) },
                onDirectory = { nav.navigate(DirectoryRoute(it)) },
                onSessions = { nav.navigate(SessionsRoute) },
                onLearn = { goToTab(nav, tabs[1]) },
                onComingUp = { nav.navigate(ComingUpRoute) },
                onOfficial = { open(OfficialRoute(it)) },
            )
        }
        pageScreens(nav, congressNav, main = nav)
        composable<SignaturesRoute> { SignaturesScreen(back) }
        composable<DebtRoute> { DebtScreen(back) }
        composable<DoomsdayRoute> { DoomsdayScreen(back) }
        composable<SessionsRoute> { SessionScreen(back) }
        composable<LocationRoute> {
            LocationScreen(onBack = back, onDone = { nav.popBackStack() })
        }
        composable<OfficialsRoute> {
            OfficialsScreen(
                onBack = back,
                onOfficial = { open(OfficialRoute(it)) },
                onDirectory = { nav.navigate(DirectoryRoute()) },
                onStateLegislators = { nav.navigate(StateLegislatorsRoute(it)) },
                onSetLocation = { nav.navigate(LocationRoute) },
                onAddOfficial = { nav.navigate(EditOfficialRoute(level = it.name)) },
            )
        }
        composable<MoreRoute> {
            MoreScreen(
                onOfficials = { nav.navigate(OfficialsRoute) },
                onSources = { nav.navigate(SourcesRoute) },
                onAbout = { nav.navigate(AboutRoute) },
                onDebt = { nav.navigate(DebtRoute) },
                onDoomsday = { nav.navigate(DoomsdayRoute) },
                onVoting = { nav.navigate(VotingRoute) },
                onDirectory = { nav.navigate(DirectoryRoute()) },
                onLegislation = { nav.navigate(LegislationRoute) },
                onAlerts = { nav.navigate(AlertsRoute) },
                onElections = { nav.navigate(ElectionsRoute) },
            )
        }
        composable<RecentVotesRoute> { RecentVotesScreen(back, congressNav) }
        composable<StateLegislatorsRoute> { entry ->
            StateLegislatorsScreen(entry.toRoute<StateLegislatorsRoute>().state, back) { open(OfficialRoute(it)) }
        }
        composable<LegislationRoute> { LegislationScreen(back, congressNav) }
        composable<HowGovernmentRoute> {
            HowGovernmentWorksScreen(
                onBack = null,
                onArticle = { nav.navigate(ArticleRoute(it)) },
                onAmendment = { nav.navigate(AmendmentRoute(it)) },
                onYourOfficials = { nav.navigate(OfficialsRoute) },
                onPresident = { nav.navigate(OfficialRoute("exec:prez")) },
            )
        }
        composable<DirectoryRoute> { entry ->
            DirectoryScreen(back, { open(OfficialRoute(it)) }, entry.toRoute<DirectoryRoute>().chamber)
        }
        composable<SourcesRoute> { SourcesScreen(back) }
        composable<AboutRoute> { AboutScreen(back) }
    }
    }
}

/**
 * The pages a list opens: a vote, a bill, a member, an article. On a phone they fill the screen; on a wide
 * window, opened from a list, they show in the detail pane, and their own links stay in that pane.
 */
private fun NavGraphBuilder.pageScreens(nav: NavHostController, congressNav: CongressNav, main: NavHostController) {
    val back: () -> Unit = { nav.popBackStack() }
        composable<ArticleRoute> { entry ->
            // Moving to the next or previous article replaces this page rather than stacking up.
            ArticleScreen(
                number = entry.toRoute<ArticleRoute>().number,
                onBack = back,
                onArticle = { nav.navigate(ArticleRoute(it)) { popUpTo<ArticleRoute> { inclusive = true } } },
                onAmendment = { nav.navigate(AmendmentRoute(it)) { popUpTo<ArticleRoute> { inclusive = true } } },
            )
        }
        composable<AmendmentRoute> { entry ->
            AmendmentScreen(
                number = entry.toRoute<AmendmentRoute>().number,
                onBack = back,
                onAmendment = { nav.navigate(AmendmentRoute(it)) { popUpTo<AmendmentRoute> { inclusive = true } } },
                onArticle = { nav.navigate(ArticleRoute(it)) { popUpTo<AmendmentRoute> { inclusive = true } } },
            )
        }
        composable<OfficialRoute> { entry ->
            OfficialDetailScreen(
                id = entry.toRoute<OfficialRoute>().id,
                onBack = back,
                onEdit = { nav.navigate(EditOfficialRoute(id = it)) },
                congressNav = congressNav,
            )
        }
        composable<VoteRoute> { entry ->
            val r = entry.toRoute<VoteRoute>()
            VoteScreen(r.chamber, r.session, r.roll, back, congressNav)
        }
        composable<BillRoute> { entry -> BillScreen(entry.toRoute<BillRoute>().id, back, congressNav) }
        composable<StateBillRoute> { entry -> StateBillScreen(entry.toRoute<StateBillRoute>().id, back) }
        composable<RaceRoute> { entry ->
            val id = entry.toRoute<RaceRoute>().id
            RaceScreen(id, back) { key -> nav.navigate(CandidateRoute(id, key)) }
        }
        composable<CandidateRoute> { entry ->
            val r = entry.toRoute<CandidateRoute>()
            CandidateScreen(r.raceId, r.key, back)
        }
        composable<MemberVotesRoute> { entry -> MemberVotesScreen(entry.toRoute<MemberVotesRoute>().id, back, congressNav) }
        composable<SponsoredBillsRoute> { entry -> SponsoredBillsScreen(entry.toRoute<SponsoredBillsRoute>().id, back, congressNav) }
        composable<EditOfficialRoute> { entry ->
            val route = entry.toRoute<EditOfficialRoute>()
            EditOfficialScreen(
                id = route.id,
                level = Level.entries.firstOrNull { it.name == route.level } ?: Level.Local,
                onBack = back,
                onSaved = { savedId ->
                    if (route.id != null) {
                        nav.popBackStack()
                    } else {
                        nav.navigate(OfficialRoute(savedId)) { popUpTo<EditOfficialRoute> { inclusive = true } }
                    }
                },
            )
        }
}

/** The right-hand pane on a wide window: empty until the reader opens something from the list. */
@Composable
private fun DetailNavHost(detail: NavHostController, main: NavHostController) {
    val congressNav = CongressNav(
        vote = { chamber, session, roll -> detail.navigate(VoteRoute(chamber, session, roll)) },
        bill = { detail.navigate(BillRoute(it)) },
        official = { detail.navigate(OfficialRoute(it)) },
        memberVotes = { detail.navigate(MemberVotesRoute(it)) },
        sponsoredBills = { detail.navigate(SponsoredBillsRoute(it)) },
        recentVotes = { main.navigate(RecentVotesRoute) },
        setLocation = { main.navigate(LocationRoute) },
        stateBill = { detail.navigate(StateBillRoute(it)) },
    )
    NavHost(detail, startDestination = DetailEmptyRoute) {
        composable<DetailEmptyRoute> {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Choose something on the left to see it here.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        pageScreens(detail, congressNav, main)
    }
}
