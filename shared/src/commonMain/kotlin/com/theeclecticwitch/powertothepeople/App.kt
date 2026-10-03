package com.theeclecticwitch.powertothepeople

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
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
import com.theeclecticwitch.powertothepeople.constitution.SignaturesScreen
import com.theeclecticwitch.powertothepeople.congress.BillScreen
import com.theeclecticwitch.powertothepeople.congress.CongressNav
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
import com.theeclecticwitch.powertothepeople.more.SourcesScreen
import com.theeclecticwitch.powertothepeople.officials.DirectoryScreen
import com.theeclecticwitch.powertothepeople.officials.StateLegislatorsScreen
import com.theeclecticwitch.powertothepeople.officials.EditOfficialScreen
import com.theeclecticwitch.powertothepeople.officials.Level
import com.theeclecticwitch.powertothepeople.officials.OfficialDetailScreen
import com.theeclecticwitch.powertothepeople.officials.OfficialsScreen
import com.theeclecticwitch.powertothepeople.ui.theme.PowerTheme
import kotlinx.serialization.Serializable

// The places the app can go. Type-safe routes: each carries exactly what its screen needs.
@Serializable object HomeRoute
@Serializable object ConstitutionRoute
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
@Serializable object DirectoryRoute
@Serializable object LegislationRoute
@Serializable data class StateLegislatorsRoute(val state: String)
@Serializable object RecentVotesRoute
@Serializable object SourcesRoute
@Serializable object AboutRoute

private data class Tab(val label: String, val icon: ImageVector, val route: Any)

private val tabs = listOf(
    Tab("Home", Icons.Default.Home, HomeRoute),
    Tab("Constitution", Icons.Default.Star, ConstitutionRoute),
    Tab("Officials", Icons.Default.Person, OfficialsRoute),
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
    PowerTheme {
        val nav = rememberNavController()
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // A phone gets a bar along the bottom; a tablet or desktop window gets a rail down the side.
            val wide = maxWidth >= 720.dp
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        val selected = selectedTab(nav)
                        tabs.forEach { tab ->
                            NavigationRailItem(tab == selected, { goToTab(nav, tab) }, icon = { Icon(tab.icon, null) }, label = { Text(tab.label) })
                        }
                    }
                    Scaffold(contentWindowInsets = WindowInsets(0)) { padding ->
                        AppNavHost(nav, Modifier.padding(padding).consumeWindowInsets(padding))
                    }
                }
            } else {
                Scaffold(
                    contentWindowInsets = WindowInsets(0),
                    bottomBar = {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                            val selected = selectedTab(nav)
                            tabs.forEach { tab ->
                                NavigationBarItem(tab == selected, { goToTab(nav, tab) }, icon = { Icon(tab.icon, null) }, label = { Text(tab.label) })
                            }
                        }
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
    nav.navigate(tab.route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun AppNavHost(nav: NavHostController, modifier: Modifier) {
    val back: () -> Unit = { nav.popBackStack() }
    val congressNav = CongressNav(
        vote = { chamber, session, roll -> nav.navigate(VoteRoute(chamber, session, roll)) },
        bill = { nav.navigate(BillRoute(it)) },
        official = { nav.navigate(OfficialRoute(it)) },
        memberVotes = { nav.navigate(MemberVotesRoute(it)) },
        sponsoredBills = { nav.navigate(SponsoredBillsRoute(it)) },
        recentVotes = { nav.navigate(RecentVotesRoute) },
        setLocation = { nav.navigate(LocationRoute) },
    )
    NavHost(nav, startDestination = HomeRoute, modifier = modifier) {
        composable<HomeRoute> {
            HomeScreen(
                onDebt = { nav.navigate(DebtRoute) },
                onDoomsday = { nav.navigate(DoomsdayRoute) },
                onSessions = { nav.navigate(SessionsRoute) },
                onLegislation = { nav.navigate(LegislationRoute) },
                congressNav = congressNav,
                onConstitution = { goToTab(nav, tabs[1]) },
                onOfficials = { goToTab(nav, tabs[2]) },
                onOfficial = { nav.navigate(OfficialRoute(it)) },
                onSetLocation = { nav.navigate(LocationRoute) },
            )
        }
        composable<ConstitutionRoute> {
            ConstitutionScreen(
                onArticle = { nav.navigate(ArticleRoute(it)) },
                onAmendment = { nav.navigate(AmendmentRoute(it)) },
                onSignatures = { nav.navigate(SignaturesRoute) },
            )
        }
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
        composable<SignaturesRoute> { SignaturesScreen(back) }
        composable<DebtRoute> { DebtScreen(back) }
        composable<DoomsdayRoute> { DoomsdayScreen(back) }
        composable<SessionsRoute> { SessionScreen(back) }
        composable<LocationRoute> {
            LocationScreen(onBack = back, onDone = {
                nav.popBackStack()
                goToTab(nav, tabs[2])
            })
        }
        composable<OfficialsRoute> {
            OfficialsScreen(
                onOfficial = { nav.navigate(OfficialRoute(it)) },
                onDirectory = { nav.navigate(DirectoryRoute) },
                onStateLegislators = { nav.navigate(StateLegislatorsRoute(it)) },
                onSetLocation = { nav.navigate(LocationRoute) },
                onAddOfficial = { nav.navigate(EditOfficialRoute(level = it.name)) },
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
        composable<MoreRoute> {
            MoreScreen(
                onLocation = { nav.navigate(LocationRoute) },
                onSources = { nav.navigate(SourcesRoute) },
                onAbout = { nav.navigate(AboutRoute) },
                onDebt = { nav.navigate(DebtRoute) },
                onDirectory = { nav.navigate(DirectoryRoute) },
                onLegislation = { nav.navigate(LegislationRoute) },
            )
        }
        composable<RecentVotesRoute> { RecentVotesScreen(back, congressNav) }
        composable<StateLegislatorsRoute> { entry ->
            StateLegislatorsScreen(entry.toRoute<StateLegislatorsRoute>().state, back) { nav.navigate(OfficialRoute(it)) }
        }
        composable<LegislationRoute> { LegislationScreen(back, congressNav) }
        composable<DirectoryRoute> { DirectoryScreen(back) { nav.navigate(OfficialRoute(it)) } }
        composable<SourcesRoute> { SourcesScreen(back) }
        composable<AboutRoute> { AboutScreen(back) }
    }
}
