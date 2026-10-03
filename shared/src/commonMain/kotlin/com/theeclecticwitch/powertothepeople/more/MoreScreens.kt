package com.theeclecticwitch.powertothepeople.more

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
import com.theeclecticwitch.powertothepeople.location.CensusGeocoder
import com.theeclecticwitch.powertothepeople.location.LocationStore
import com.theeclecticwitch.powertothepeople.officials.FederalOfficials
import com.theeclecticwitch.powertothepeople.ui.AppTopBar
import com.theeclecticwitch.powertothepeople.ui.InfoCard
import com.theeclecticwitch.powertothepeople.ui.ReadingColumn
import com.theeclecticwitch.powertothepeople.ui.SourceLine

@Composable
fun MoreScreen(onLocation: () -> Unit, onSources: () -> Unit, onAbout: () -> Unit, onDebt: () -> Unit) {
    val location by LocationStore.location.collectAsState()
    Scaffold(topBar = { AppTopBar("More") }) { padding ->
        ReadingColumn(Modifier.padding(padding)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                InfoCard(title = "Your location", onClick = onLocation) {
                    Text(location?.matchedAddress ?: "Not set yet", style = MaterialTheme.typography.bodyLarge)
                }
                InfoCard(title = "Debt & deficit", onClick = onDebt) {
                    Text("The national debt, this year's budget, and how both have changed.", style = MaterialTheme.typography.bodyLarge)
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
        ReadingColumn(Modifier.padding(padding)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "This app reports facts and shows where each one came from. It doesn't rate, rank or take sides - " +
                        "what you make of the facts is up to you. Tap any source to check it for yourself.",
                    style = MaterialTheme.typography.bodyLarge,
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

@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(topBar = { AppTopBar("About", onBack) }) { padding ->
        ReadingColumn(Modifier.padding(padding)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                InfoCard(title = "Why this app exists") {
                    Text(
                        "Most people spend their days working and raising a family. Keeping up with what the government is doing - " +
                            "who holds which office, how they vote, what they spend - shouldn't take hours of searching. " +
                            "Power to the People puts it in one place, with the facts and their sources, so you can decide for yourself.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                InfoCard(title = "Free, for everyone") {
                    Text(
                        "The app is free and has no paywall. It is built on free public data. If you'd like to help it grow, " +
                            "a voluntary Supporter option will pay for more data sources in the future.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                InfoCard(title = "Coming next") {
                    listOf(
                        "Bills in Congress that affect you, with official plain-English summaries",
                        "How your members of Congress vote, with charts - and how often they vote the way you would",
                        "Days in session and days in recess",
                        "Your governor and state legislators, and bills in your state",
                        "Campaign donors and lobbying disclosures",
                        "Financial disclosures: reported net worth compared with salary",
                        "News about your officials from across the spectrum, with each outlet's lean labeled",
                        "Election dates, registration and polling place links",
                        "Public comment periods on new federal rules",
                        "Guides to your rights, a public-records (FOIA) request helper, and a civics quiz",
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
