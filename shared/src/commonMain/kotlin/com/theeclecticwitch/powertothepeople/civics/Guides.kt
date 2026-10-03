package com.theeclecticwitch.powertothepeople.civics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.theeclecticwitch.powertothepeople.ui.InfoCard
import com.theeclecticwitch.powertothepeople.ui.openSafely

/** How to do one thing a citizen can do, in plain steps, with where the rules come from. */
data class Guide(
    val title: String,
    val summary: String,
    val steps: List<String>,
    val goodToKnow: List<String> = emptyList(),
    val cites: List<Cite> = emptyList(),
    val sources: List<Pair<String, String>> = emptyList(),
)

object Guides {
    val all = listOf(
        Guide(
            "Get records from the federal government (FOIA)",
            "The Freedom of Information Act lets anyone ask a federal agency for its records: emails, reports, contracts, " +
                "data. You don't have to be a citizen or say why you want them.",
            steps = listOf(
                "Find the agency that would have the records. FOIA.gov lists every agency's FOIA office, and many take " +
                    "requests right on FOIA.gov.",
                "Describe the records as exactly as you can: the subject, the dates, the office, names or document titles " +
                    "if you know them. A narrow request is answered faster.",
                "Say how much you're willing to pay in fees, or ask for a fee waiver if the records serve the public interest.",
                "Send it. The agency must decide within 20 working days, and may take 10 more in unusual circumstances.",
                "If you're turned down, in whole or part, you can appeal to the agency. You can also ask the Office of " +
                    "Government Information Services at the National Archives to help settle a dispute, at no cost.",
            ),
            goodToKnow = listOf(
                "FOIA covers federal executive agencies. It does not cover Congress, the federal courts, or state and local " +
                    "governments. Every state has its own open-records law for state and local records.",
                "Some records can be withheld under nine exemptions set in the law, such as classified national security " +
                    "information or other people's private information. The agency must release the rest.",
                "Most people asking for personal or public-interest reasons pay nothing for the first two hours of searching " +
                    "and the first 100 pages.",
            ),
            sources = listOf(
                "FOIA.gov, the Justice Department" to "https://www.foia.gov/",
                "Office of Government Information Services" to "https://www.archives.gov/ogis",
            ),
        ),
        Guide(
            "Speak up at a hearing or public meeting",
            "Lawmakers hold hearings and meetings before they decide. Many let the public watch, and many take comments " +
                "or written testimony from anyone.",
            steps = listOf(
                "Find the meeting. Congress posts committee hearings on Congress.gov (see Coming up in this app). State " +
                    "legislatures, county boards and city councils post their agendas on their own websites.",
                "Check how that body takes public input. City and county meetings usually have a public comment period, " +
                    "often with a sign-up and a time limit of a few minutes. Many state legislative committees take public " +
                    "testimony on bills.",
                "In Congress, witnesses who speak at a hearing are invited by the committee, but many committees accept a " +
                    "written statement from anyone for the hearing record. Ask the committee's clerk how and by when.",
                "Write it down. Say who you are and where you live, which bill or item you're speaking to, what you'd like " +
                    "done, and why, from your own experience. Keep it short.",
                "For a proposed federal rule, comment directly: the Comment tab under Coming up lists every rule open for " +
                    "comments, with links to Regulations.gov.",
            ),
            goodToKnow = listOf(
                "Most states have open-meetings laws requiring that public bodies meet in public and post notice ahead of time. " +
                    "The details differ by state.",
            ),
            cites = listOf(Cite("Amendment I: the right to petition the government", amendment = 1)),
            sources = listOf(
                "Congress.gov committee schedule" to "https://www.congress.gov/committee-schedule",
                "Regulations.gov" to "https://www.regulations.gov/",
            ),
        ),
        Guide(
            "Jury duty",
            "Trial by jury is a right the Constitution guarantees, and it depends on citizens serving. A summons comes by " +
                "mail from a state court or a federal court.",
            steps = listOf(
                "Read the summons right away. It says which court, when and where to report, and usually asks you to fill " +
                    "out a questionnaire online or by mail.",
                "If the date is a real hardship, ask to postpone. Courts usually allow one postponement, and some people can " +
                    "be excused. The summons says how to ask.",
                "The day before, check the court's website or phone line: many jurors are told not to come in after all.",
                "At the courthouse, you may be questioned by the judge and lawyers before being chosen for a trial. Many " +
                    "people who report are never seated, and that is still service.",
            ),
            goodToKnow = listOf(
                "Federal jurors must be U.S. citizens, at least 18, living in the court's district for a year, able to read, " +
                    "write and speak English well enough, and not facing or convicted of a felony without their civil " +
                    "rights restored. States set their own rules for state courts.",
                "Federal law forbids an employer from firing or threatening a permanent employee for serving on a federal " +
                    "jury. Most states protect state jurors too.",
                "Jurors are paid a small daily fee, and travel costs may be covered.",
                "Ignoring a summons can bring a court order to appear and a fine.",
            ),
            cites = listOf(
                Cite("Article III, Section 2: trial by jury", article = 3),
                Cite("Amendment VI: an impartial jury in criminal cases", amendment = 6),
                Cite("Amendment VII: juries in civil cases", amendment = 7),
            ),
            sources = listOf(
                "Jury service, U.S. Courts" to "https://www.uscourts.gov/services-forms/jury-service",
                "Juror qualifications, U.S. Courts" to "https://www.uscourts.gov/services-forms/jury-service/juror-qualifications",
            ),
        ),
        Guide(
            "Run for office",
            "Most elected offices in the country are local: school boards, city councils, county offices. Anyone who meets " +
                "the requirements can run.",
            steps = listOf(
                "Pick the office and learn its requirements: age, how long you've lived there, and for some offices, " +
                    "citizenship for a set number of years.",
                "Ask your state or local election office how to get on the ballot. Usually it's a filing form plus a fee, " +
                    "or a petition with a set number of voters' signatures, by a deadline.",
                "Decide whether to run in a party's primary or as an independent, where your state allows both.",
                "Follow the campaign finance rules: keep records of every dollar raised and spent, and file the reports " +
                    "your state requires.",
                "For Congress: once you raise or spend more than \$5,000, you are a candidate under federal law and must " +
                    "register with the Federal Election Commission within 15 days.",
            ),
            goodToKnow = listOf(
                "The Constitution sets the requirements for federal office. House: 25 years old, a citizen for 7 years, and " +
                    "living in the state. Senate: 30, a citizen for 9 years, and living in the state. President: 35, a natural " +
                    "born citizen, and a resident for 14 years.",
                "Under the Hatch Act, federal employees generally may not be candidates in partisan elections, though " +
                    "they may run in nonpartisan ones, like many school board races. The Office of Special Counsel " +
                    "answers questions about it.",
            ),
            cites = listOf(
                Cite("Article I, Sections 2 and 3: the House and the Senate", article = 1),
                Cite("Article II, Section 1: the President", article = 2),
            ),
            sources = listOf(
                "Find your state election office, USA.gov" to "https://www.usa.gov/state-election-office",
                "Registering as a federal candidate, FEC" to "https://www.fec.gov/help-candidates-and-committees/registering-candidate/",
                "The Hatch Act, Office of Special Counsel" to "https://www.osc.gov/services/hatch-act/federal/",
            ),
        ),
        Guide(
            "Contact your officials so it counts",
            "Offices keep track of what the people they represent tell them. A few habits make your message easier to count " +
                "and harder to overlook.",
            steps = listOf(
                "Contact your own officials. Offices respond to their constituents, so say where you live.",
                "Name the bill by its number, like H.R. 1 or S. 2403, and say whether you want them to support or oppose it.",
                "Say why in a sentence or two, especially how it affects you, your family or your work.",
                "Call the office or use its contact form. Calls are tallied the same day; a staff member answers and notes " +
                    "your view.",
                "Time it. Contact them before the vote: Coming up and Alerts in this app tell you when a bill you follow is " +
                    "scheduled.",
            ),
            cites = listOf(Cite("Amendment I: the right to petition the government", amendment = 1)),
            sources = listOf("Contacting Congress, USA.gov" to "https://www.usa.gov/elected-officials"),
        ),
    )
}

/** The Take part tab: guides, each opening to its steps. */
@Composable
fun GuidesList(onArticle: (Int) -> Unit, onAmendment: (Int) -> Unit) {
    var open by rememberSaveable { mutableStateOf(setOf<String>()) }
    val uri = LocalUriHandler.current
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text(
                "How to do the things a citizen can do, step by step, with where the rules come from.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        items(Guides.all, key = { it.title }) { g ->
            val expanded = g.title in open
            InfoCard(onClick = { open = if (expanded) open - g.title else open + g.title }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(g.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, if (expanded) "Hide steps" else "Show steps")
                }
                Text(g.summary, style = MaterialTheme.typography.bodyMedium)
                if (expanded) {
                    g.steps.forEachIndexed { i, step ->
                        Row {
                            Text("${i + 1}.", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(28.dp))
                            Text(step, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        }
                    }
                    if (g.goodToKnow.isNotEmpty()) {
                        Text("Good to know", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
                        g.goodToKnow.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    }
                    g.cites.forEach { c ->
                        Text(
                            "In the Constitution: ${c.label}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier.clickable { c.article?.let(onArticle); c.amendment?.let(onAmendment) }.padding(vertical = 2.dp),
                        )
                    }
                    g.sources.forEach { (label, url) ->
                        Text(
                            "Source: $label",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier.clickable { openSafely(uri, url) }.padding(vertical = 2.dp),
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}
