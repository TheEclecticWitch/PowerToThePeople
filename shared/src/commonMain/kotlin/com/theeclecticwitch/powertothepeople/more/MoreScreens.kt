package com.theeclecticwitch.powertothepeople.more

import com.theeclecticwitch.powertothepeople.ui.fullWidth
import com.theeclecticwitch.powertothepeople.ui.PageColumn
import com.theeclecticwitch.powertothepeople.ui.CardPage
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.theeclecticwitch.powertothepeople.congress.CongressData
import com.theeclecticwitch.powertothepeople.debt.TreasurySources
import com.theeclecticwitch.powertothepeople.doomsday.Doomsday
import com.theeclecticwitch.powertothepeople.location.CensusGeocoder
import com.theeclecticwitch.powertothepeople.location.LocationStore
import com.theeclecticwitch.powertothepeople.officials.FederalOfficials
import com.theeclecticwitch.powertothepeople.ui.AppTopBar
import com.theeclecticwitch.powertothepeople.ui.InfoCard
import com.theeclecticwitch.powertothepeople.ui.ReadingColumn
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalUriHandler
import com.theeclecticwitch.powertothepeople.ui.openSafely
import com.theeclecticwitch.powertothepeople.ui.SourceLine

@Composable
fun MoreScreen(
    onOfficials: () -> Unit,
    onSources: () -> Unit,
    onAbout: () -> Unit,
    onDebt: () -> Unit,
    onDoomsday: () -> Unit,
    onVoting: () -> Unit,
    onDirectory: () -> Unit,
    onLegislation: () -> Unit,
    onAlerts: () -> Unit,
    onElections: () -> Unit,
) {
    Scaffold(topBar = { AppTopBar("More") }) { padding ->
        PageColumn(Modifier.padding(padding)) {
            CardPage(spacing = 10.dp) {
                InfoCard(title = "Your vote", onClick = onVoting) {
                    Text("Register, find where to vote, and see what's on your ballot.", style = MaterialTheme.typography.bodyLarge)
                }
                InfoCard(title = "Elections", onClick = onElections) {
                    Text("Who's running in your races, every candidate shown equally, with your own private notes.", style = MaterialTheme.typography.bodyLarge)
                }
                InfoCard(title = "My officials", onClick = onOfficials) {
                    Text(
                        "Everyone who represents you, federal, state and local, with contact details, your notes and their promises.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                InfoCard(title = "Legislation", onClick = onLegislation) {
                    Text("Find any bill in Congress, any executive order, or a bill in your state legislature.", style = MaterialTheme.typography.bodyLarge)
                }
                InfoCard(title = "Alerts", onClick = onAlerts) {
                    Text("Hear when your members vote, and when bills you follow move or come up for a vote.", style = MaterialTheme.typography.bodyLarge)
                }
                InfoCard(title = "All of Congress", onClick = onDirectory) {
                    Text("Every senator and representative: their votes, bills and committees.", style = MaterialTheme.typography.bodyLarge)
                }
                InfoCard(title = "Debt & deficit", onClick = onDebt) {
                    Text("The national debt, this year's budget, and how both have changed.", style = MaterialTheme.typography.bodyLarge)
                }
                InfoCard(title = "The Doomsday Clock", onClick = onDoomsday) {
                    Text("Where the Bulletin of the Atomic Scientists has set it, and what it means.", style = MaterialTheme.typography.bodyLarge)
                }
                InfoCard(title = "Where our information comes from", onClick = onSources) {
                    Text("Every source this app uses, and how to check it yourself.", style = MaterialTheme.typography.bodyLarge)
                }
                InfoCard(title = "About Power to the People", onClick = onAbout) {
                    Text("Why this app exists, and what's coming next.", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
fun SourcesScreen(onBack: () -> Unit) {
    Scaffold(topBar = { AppTopBar("Our Sources", onBack) }) { padding ->
        PageColumn(Modifier.padding(padding)) {
            CardPage(spacing = 10.dp) {
                Text(
                    "This app reports facts and shows where each one came from. It doesn't rate, rank or take sides - " +
                        "what you make of the facts is up to you. Tap any source to check it for yourself.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fullWidth(),
                )
                Source(
                    "The Constitution",
                    "National Archives transcriptions of the Constitution, the Bill of Rights and Amendments XI–XXVII. " +
                        "The titles and one-line summaries are ours, and describe what the text says.",
                    "National Archives",
                    "https://www.archives.gov/founding-docs",
                )
                Source(
                    "Your districts",
                    "The U.S. Census Bureau's geocoder turns your address into your congressional, state legislative, " +
                        "county, city and school districts.",
                    "U.S. Census Bureau",
                    CensusGeocoder.SOURCE_URL,
                )
                Source(
                    "Members of Congress, the President and Vice President",
                    "Names, offices, terms, phone numbers, addresses and district offices, from the @unitedstates project's " +
                        "public-domain data, maintained from official House, Senate and White House records. Photos are the " +
                        "official portraits from the Government Publishing Office.",
                    FederalOfficials.SOURCE_NAME,
                    FederalOfficials.SOURCE_URL,
                )
                Source(
                    "Votes and bills",
                    "Every roll-call vote in the House and Senate, and every bill and resolution, copied as published by " +
                        "Congress.gov (Library of Congress), the Office of the Clerk of the House and the U.S. Senate. This app's " +
                        "own gatherer collects them every six hours; each vote links to its official record.",
                    CongressData.SOURCE_NAME,
                    CongressData.SOURCE_URL,
                )
                Source(
                    "Campaign money",
                    "What each member's campaign committee raised, from whom by category, spent, and had on hand, for " +
                        "their current race, as filed with the Federal Election Commission. Checked weekly.",
                    "Federal Election Commission",
                    "https://www.fec.gov/data/",
                )
                Source(
                    "Governors and state legislators",
                    "Statewide officers and every state legislator, with their districts and contact details, from the " +
                        "Open States project's public data, kept from state government websites. Checked weekly.",
                    "Open States",
                    "https://github.com/openstates/people",
                )
                Source(
                    "Executive orders",
                    "Every executive order the Federal Register has published since 1994, the official record of " +
                        "presidential documents. Each links to its full text.",
                    "Federal Register",
                    "https://www.federalregister.gov/presidential-documents/executive-orders",
                )
                Source(
                    "State bills",
                    "Bills in each state legislature, searched live at Open States through this app's own service, " +
                        "which keeps recent searches for six hours.",
                    "Open States",
                    "https://openstates.org/",
                )
                Source(
                    "Days in session",
                    "Which days the House and the Senate met, from the daily Congressional Record.",
                    "Congressional Record, via Congress.gov",
                    "https://www.congress.gov/congressional-record",
                )
                Source(
                    "How app users answered",
                    "Only if you turn on \"Add my answers to the app-wide count\": the roll call, your Yea or Nay, and a " +
                        "random code for this copy of the app, so each phone counts once. The code is scrambled before it is " +
                        "stored, differently for every vote, so no one can follow one person's answers. No name, address or " +
                        "location is ever sent. Counts appear once 10 people have answered. The service's code is public.",
                    "This app's own counting service (Cloudflare)",
                    "https://github.com/TheEclecticWitch/PowerToThePeople/tree/main/server/tally",
                )
                Source(
                    "The Doomsday Clock",
                    "The setting announced by the Bulletin of the Atomic Scientists, who keep the clock. It is their " +
                        "judgment, shown as they state it; this app's gatherer checks their page for a new setting.",
                    Doomsday.SOURCE_NAME,
                    Doomsday.PAGE,
                )
                Source(
                    "National debt",
                    "Total public debt outstanding, published every business day by the Treasury. The ticking counter is an " +
                        "estimate between official figures and is labeled as one.",
                    "U.S. Treasury, Debt to the Penny",
                    TreasurySources.DEBT_PAGE,
                )
                Source(
                    "Deficit and surplus",
                    "Money collected and spent each month, from the Treasury's Monthly Treasury Statement.",
                    "U.S. Treasury, Monthly Treasury Statement",
                    TreasurySources.MTS_PAGE,
                )
                Source(
                    "Debt history",
                    "The debt at the end of each fiscal year.",
                    "U.S. Treasury, Historical Debt Outstanding",
                    TreasurySources.HISTORY_PAGE,
                )
                Source(
                    "Officials you add",
                    "Local and state officials you enter, and your notes and campaign promises, are your own record. " +
                        "They stay on this device and are never sent anywhere.",
                    "You",
                    null,
                )
            }
        }
    }
}

@Composable
private fun Source(title: String, what: String, name: String, url: String?) {
    InfoCard {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(what, style = MaterialTheme.typography.bodyMedium)
        SourceLine(name, url)
    }
}

const val PRIVACY_POLICY_URL = "https://00theeclecticwitch00.com/power-to-the-people-privacy-policy/"

@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(topBar = { AppTopBar("About", onBack) }) { padding ->
        PageColumn(Modifier.padding(padding)) {
            CardPage(spacing = 10.dp) {
                InfoCard(title = "Why this app exists") {
                    Text(
                        "Most people spend their days working and raising a family. Keeping up with what the government is doing - " +
                            "who holds which office, how they vote, what they spend - shouldn't take hours of searching. " +
                            "Power to the People puts it in one place, with the facts and their sources, so you can decide for yourself.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                InfoCard(title = "Not part of the government") {
                    Text(
                        "Power to the People is an independent app. It is not made by, affiliated with or endorsed by the U.S. " +
                            "government, Congress, any state or any political party. Its information comes from public government " +
                            "records, such as Congress.gov, the House Clerk, Senate.gov, the National Archives, the U.S. Treasury, " +
                            "the Census Bureau and the Federal Election Commission; Our Sources lists every one, with links.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                InfoCard(title = "Free, for everyone") {
                    Text(
                        "Officials, votes, bills, elections, the Constitution and everything else built on public data are free, " +
                            "with no paywall. The one exception will be News: an optional subscription that pays for news sources, " +
                            "This Week in Congress, and the servers that count app users' answers on each vote. Until subscriptions " +
                            "are ready, This Week in Congress is free too.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                InfoCard(title = "Your privacy") {
                    Text(
                        "No accounts, no ads, no tracking. Your address, notes and what you follow stay on this device. Your " +
                            "address is sent only to look up your districts (the Census Bureau) or your polling place (Google's " +
                            "election service, through this app's own service, which doesn't keep it), and only when you ask.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    val uri = LocalUriHandler.current
                    Text(
                        "Read the full privacy policy ›",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.clickable { openSafely(uri, PRIVACY_POLICY_URL) }.padding(vertical = 4.dp),
                    )
                }
                InfoCard(title = "Coming next") {
                    listOf(
                        "News about your officials from across the spectrum, with each outlet's lean labeled",
                        "Your ballot, race by race, as states and counties publish it before each election",
                        "Candidates in state races",
                    ).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                }
                InfoCard(title = "Fonts") {
                    Text(
                        "The Constitution is set in Libre Caslon, a revival of the Caslon typefaces used by printers of the founding era, " +
                            "under the SIL Open Font License.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
