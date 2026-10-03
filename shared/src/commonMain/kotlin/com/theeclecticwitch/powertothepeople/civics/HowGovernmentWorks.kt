package com.theeclecticwitch.powertothepeople.civics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.theeclecticwitch.powertothepeople.ui.AppTopBar
import com.theeclecticwitch.powertothepeople.ui.InfoCard
import com.theeclecticwitch.powertothepeople.ui.ReadingColumn
import com.theeclecticwitch.powertothepeople.ui.SourceLine
import com.theeclecticwitch.powertothepeople.ui.openSafely
import com.theeclecticwitch.powertothepeople.ui.theme.LocalAppFonts
import kotlinx.coroutines.launch

/** Where a fact comes from in the Constitution, so the reader can tap through to the words themselves. */
data class Cite(val label: String, val article: Int? = null, val amendment: Int? = null)

data class Power(val text: String, val cite: Cite? = null)

data class Part(
    val id: String,
    val title: String,
    val role: String,
    val about: String,
    val facts: List<Pair<String, String>> = emptyList(),
    val powers: List<Power> = emptyList(),
    val yours: String? = null,
)

/**
 * How the national government is put together and what each part is for, from the Constitution itself and the
 * government's own explanations. Facts only: what the Constitution and the law say, not how well anyone does it.
 */
object Civics {
    val parts = listOf(
        Part(
            "people", "We the People", "Where the power comes from",
            "The Constitution begins \"We the People of the United States… do ordain and establish this Constitution.\" " +
                "Government's power comes from the people, who choose their representatives in elections. That is " +
                "what makes the United States a republic: the people govern through the officials they elect, and " +
                "those officials must follow a written Constitution.",
            facts = listOf(
                "You elect" to "Your U.S. representative, your two U.S. senators, and the President and Vice President " +
                    "(through the Electoral College), plus your state and local officials.",
                "Rights" to "The first ten amendments, the Bill of Rights, list freedoms the government may not take away.",
            ),
            powers = listOf(
                Power("The people elect the House of Representatives every two years.", Cite("Article I, Section 2", article = 1)),
                Power("The people elect their senators directly (since 1913).", Cite("Amendment XVII", amendment = 17)),
                Power("Powers not given to the national government are kept by the states or the people.", Cite("Amendment X", amendment = 10)),
            ),
        ),
        Part(
            "constitution", "The Constitution", "The supreme law",
            "Signed in 1787 and in effect since 1789, the Constitution creates the three branches, says what each may " +
                "and may not do, and divides power between the national government and the states. Every official " +
                "takes an oath to support it, and any law that conflicts with it is not valid.",
            facts = listOf(
                "Changing it" to "An amendment needs two-thirds of both houses of Congress (or a convention called by " +
                    "two-thirds of the states), then approval by three-fourths of the states. It has been amended 27 times.",
            ),
            powers = listOf(
                Power("The Constitution and laws made under it are \"the supreme Law of the Land.\"", Cite("Article VI", article = 6)),
                Power("How it can be amended.", Cite("Article V", article = 5)),
            ),
        ),
        Part(
            "congress", "Congress", "The legislative branch: makes the laws",
            "Congress is the part of government that writes and passes federal laws, decides how the government raises " +
                "and spends money, and oversees how laws are carried out. It has two parts, or chambers: the Senate and " +
                "the House of Representatives. A bill must pass both, in the same words, before it can become law.",
            facts = listOf(
                "Members" to "535 voting members: 100 senators and 435 representatives, plus 6 non-voting members of the " +
                    "House from D.C. and the territories.",
                "Meets" to "In the Capitol in Washington. Each two-year Congress is numbered; this is the 119th (2025 to 2027).",
            ),
            powers = listOf(
                Power("All federal lawmaking power belongs to Congress.", Cite("Article I, Section 1", article = 1)),
                Power("Taxes and borrowing, and paying the nation's debts.", Cite("Article I, Section 8", article = 1)),
                Power("Regulating trade with other nations and between the states.", Cite("Article I, Section 8", article = 1)),
                Power("Declaring war, and raising and paying for the armed forces.", Cite("Article I, Section 8", article = 1)),
                Power("Coining money, setting up post offices, and creating the courts below the Supreme Court.", Cite("Article I, Section 8", article = 1)),
                Power("No money may be spent unless Congress passes a law to spend it.", Cite("Article I, Section 9", article = 1)),
                Power("Overriding a President's veto, with two-thirds of each chamber.", Cite("Article I, Section 7", article = 1)),
            ),
        ),
        Part(
            "senate", "The Senate", "Part of Congress: two senators for every state",
            "The Senate gives every state an equal voice, large or small. Besides passing laws with the House, it has " +
                "jobs the House doesn't: it approves or rejects the President's choices for judges, Cabinet members and " +
                "ambassadors, approves treaties, and holds the trial when an official is impeached.",
            facts = listOf(
                "Members" to "100 senators, 2 from each state, elected statewide.",
                "Term" to "6 years. About a third are elected every two years, so the Senate changes gradually.",
                "To serve" to "At least 30 years old, a citizen for 9 years, and living in the state.",
                "Leaders" to "The Vice President is the Senate's president and votes only to break a tie. Day to day, " +
                    "the majority leader, chosen by the party with the most senators, sets what the Senate takes up.",
            ),
            powers = listOf(
                Power("Approving treaties, by a two-thirds vote.", Cite("Article II, Section 2", article = 2)),
                Power("Approving or rejecting nominees for judges, ambassadors and other top offices.", Cite("Article II, Section 2", article = 2)),
                Power("Trying impeachments; removing an official takes two-thirds of senators present.", Cite("Article I, Section 3", article = 1)),
            ),
            yours = "Your two senators",
        ),
        Part(
            "house", "The House of Representatives", "Part of Congress: seats by population",
            "The House gives each state seats in proportion to its population, counted in the census every ten years, " +
                "so bigger states have more representatives. Each representative speaks for one district. Because they " +
                "face election every two years, the House was designed to be closest to the people.",
            facts = listOf(
                "Members" to "435 representatives, each from one district; every state has at least one.",
                "Term" to "2 years. Every seat is up for election every two years.",
                "To serve" to "At least 25 years old, a citizen for 7 years, and living in the state.",
                "Leader" to "The Speaker of the House, chosen by the members.",
            ),
            powers = listOf(
                Power("Bills to raise taxes must start in the House.", Cite("Article I, Section 7", article = 1)),
                Power("Impeaching (formally charging) officials, by majority vote.", Cite("Article I, Section 2", article = 1)),
                Power("Choosing the President if no candidate wins a majority of electoral votes.", Cite("Amendment XII", amendment = 12)),
            ),
            yours = "Your representative",
        ),
        Part(
            "president", "The President", "The executive branch: carries out the laws",
            "The President leads the executive branch, which carries out and enforces the laws Congress passes, through " +
                "the departments and agencies of the federal government. The President is also commander in chief of " +
                "the armed forces and leads the nation's dealings with other countries.",
            facts = listOf(
                "Term" to "4 years, at most two terms.",
                "Elected by" to "The Electoral College: 538 electors chosen by the states, based mostly on each state's " +
                    "popular vote. It takes 270 to win.",
                "To serve" to "A natural-born citizen, at least 35 years old, and a U.S. resident for 14 years.",
            ),
            powers = listOf(
                Power("Signing bills into law, or vetoing them.", Cite("Article I, Section 7", article = 1)),
                Power("Commander in chief of the armed forces.", Cite("Article II, Section 2", article = 2)),
                Power("Making treaties and naming judges and officers, with the Senate's approval.", Cite("Article II, Section 2", article = 2)),
                Power("Granting pardons for federal crimes, except in impeachment.", Cite("Article II, Section 2", article = 2)),
                Power("\"Take Care that the Laws be faithfully executed.\"", Cite("Article II, Section 3", article = 2)),
                Power("Limited to two terms.", Cite("Amendment XXII", amendment = 22)),
            ),
            yours = "The President's page",
        ),
        Part(
            "vp", "The Vice President", "Part of the executive branch",
            "The Vice President becomes President if the President dies, resigns or is removed, and presides over the " +
                "Senate, where they vote only to break a tie.",
            facts = listOf("Term" to "4 years, elected on the same ticket as the President."),
            powers = listOf(
                Power("Presides over the Senate and breaks ties.", Cite("Article I, Section 3", article = 1)),
                Power("First in line to succeed the President.", Cite("Amendment XXV", amendment = 25)),
            ),
        ),
        Part(
            "cabinet", "The Cabinet and federal agencies", "Part of the executive branch: run the government day to day",
            "The Cabinet is the Vice President and the heads of the 15 executive departments, such as State, Treasury, " +
                "Defense, Justice and Education. Each department, and agencies such as the EPA and NASA, carries out laws " +
                "in its area. Department heads are chosen by the President and confirmed by the Senate.",
            facts = listOf("Departments" to "15, each headed by a Secretary (Justice is headed by the Attorney General)."),
            powers = listOf(
                Power("The President may require written opinions from the heads of departments.", Cite("Article II, Section 2", article = 2)),
            ),
        ),
        Part(
            "courts", "The Supreme Court and federal courts", "The judicial branch: interprets the laws",
            "The federal courts decide cases about federal law and the Constitution. The Supreme Court is the final word: " +
                "when it rules that a law or government action goes against the Constitution, that law or action can't " +
                "stand. Most cases start in a district court; a party who loses can appeal to a court of appeals, then " +
                "ask the Supreme Court to hear it.",
            facts = listOf(
                "Supreme Court" to "9 justices (the number is set by law), led by the Chief Justice.",
                "Courts of appeals" to "13 circuits.",
                "District courts" to "94, the federal trial courts.",
                "Term" to "Federal judges serve for life \"during good Behaviour,\" so they don't have to please voters to keep their jobs.",
                "Chosen by" to "Nominated by the President, confirmed by the Senate.",
            ),
            powers = listOf(
                Power("The judicial power of the United States: one Supreme Court and the courts Congress creates.", Cite("Article III, Section 1", article = 3)),
                Power("Cases arising under the Constitution, federal laws and treaties.", Cite("Article III, Section 2", article = 3)),
            ),
        ),
        Part(
            "states", "The states", "Their own governments, under the Constitution",
            "Each state has its own constitution and the same three branches: a governor who carries out state laws, a " +
                "legislature that makes them (every state but Nebraska has two chambers), and state courts. States handle " +
                "most of daily life: schools, police, driving, marriage, most crimes, elections and much more. The national " +
                "government has only the powers the Constitution gives it; the rest belong to the states and the people.",
            powers = listOf(
                Power("Powers not given to the national government are reserved to the states or the people.", Cite("Amendment X", amendment = 10)),
                Power("Every state is guaranteed \"a Republican Form of Government.\"", Cite("Article IV, Section 4", article = 4)),
            ),
            yours = "Your governor and state legislators",
        ),
        Part(
            "local", "Local government", "Counties, cities, towns and school districts",
            "Local governments are created by the states. They run the services closest to home: local roads, police and " +
                "fire departments, water, zoning, libraries and public schools. Mayors, council members, county " +
                "commissioners and school board members are usually elected by the people they serve.",
            yours = "Your local officials",
        ),
    )

    val checks = listOf(
        "Congress → President" to "Can override a veto with two-thirds of both chambers, controls all spending, must " +
            "approve treaties and top appointments (the Senate), and can impeach and remove a President.",
        "President → Congress" to "Can veto bills, and can call Congress into session.",
        "Courts → Congress and President" to "Can rule laws and government actions unconstitutional.",
        "President and Senate → Courts" to "The President nominates every federal judge; the Senate decides whether to confirm.",
        "Congress → Courts" to "Sets the number of judges and creates the lower courts, and can impeach and remove judges.",
        "The people → everyone" to "Elect the President and all of Congress, and can change the Constitution through amendments.",
    )

    val billSteps = listOf(
        "Introduced" to "A senator or representative introduces a bill. (Tax bills must start in the House.)",
        "Committee" to "A committee studies it, may hold hearings and change it, and decides whether to send it on.",
        "First chamber votes" to "The full House or Senate debates and votes. A simple majority passes it.",
        "Second chamber" to "The other chamber does the same. If they pass different versions, they must agree on one text.",
        "President" to "The President signs it into law or vetoes it. If the President does neither for 10 days " +
            "(Sundays excepted) while Congress is in session, it becomes law anyway.",
        "Override" to "Congress can pass a vetoed bill over the President's objection with a two-thirds vote in each chamber.",
    )
}

private const val USA_GOV = "https://www.usa.gov/branches-of-government"
private const val HOW_LAWS = "https://www.congress.gov/help/learn-about-the-legislative-process"

/** The page: a diagram to take in at a glance, then each part in plain words with the Constitution's own text a tap away. */
@Composable
fun HowGovernmentWorksScreen(
    onBack: () -> Unit,
    onArticle: (Int) -> Unit,
    onAmendment: (Int) -> Unit,
    onYourOfficials: () -> Unit,
    onPresident: () -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    // The diagram and the intro come first, then one item per part, then checks and how a bill becomes law.
    val partIndex = { id: String -> 2 + Civics.parts.indexOfFirst { it.id == id } }
    Scaffold(topBar = { AppTopBar("How Our Government Works", onBack) }) { padding ->
        ReadingColumn(Modifier.padding(padding)) {
            LazyColumn(state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text(
                        "The United States is a democratic republic: the people elect representatives, and everyone in " +
                            "government works under a written Constitution. Power is split three ways so that no one person " +
                            "or group holds it all. Tap any box to read what that part does.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                item { Diagram { id -> scope.launch { listState.animateScrollToItem(partIndex(id)) } } }
                itemsIndexed(Civics.parts) { _, part ->
                    PartCard(part, onArticle, onAmendment) {
                        when (part.id) {
                            "president" -> onPresident()
                            else -> onYourOfficials()
                        }
                    }
                }
                item {
                    InfoCard(title = "Checks and balances") {
                        Text(
                            "The branches don't report to each other; they are equal on purpose. Instead, each has ways to " +
                                "limit the others, so that no branch can act alone:",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Civics.checks.forEach { (who, what) -> Fact(who, what) }
                    }
                }
                item {
                    InfoCard(title = "How a bill becomes a law") {
                        Civics.billSteps.forEachIndexed { i, (step, what) -> Fact("${i + 1}. $step", what) }
                        LinkLine("The full process, from Congress.gov", HOW_LAWS) { openSafely(uri, it) }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SourceLine("The Constitution of the United States", "https://www.archives.gov/founding-docs/constitution-transcript")
                        SourceLine("USA.gov, Branches of the U.S. government", USA_GOV)
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PartCard(part: Part, onArticle: (Int) -> Unit, onAmendment: (Int) -> Unit, onYours: () -> Unit) {
    InfoCard(title = part.title) {
        Text(part.role, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(part.about, style = MaterialTheme.typography.bodyMedium)
        part.facts.forEach { (k, v) -> Fact(k, v) }
        if (part.powers.isNotEmpty()) {
            Text("What the Constitution says", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
            part.powers.forEach { p ->
                Column(Modifier.padding(start = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("• ${p.text}", style = MaterialTheme.typography.bodyMedium)
                    p.cite?.let { c ->
                        Text(
                            c.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier.padding(start = 12.dp).clickable {
                                c.article?.let(onArticle); c.amendment?.let(onAmendment)
                            },
                        )
                    }
                }
            }
        }
        part.yours?.let {
            Text(
                "$it ›",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.clickable(onClick = onYours).padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun Fact(label: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LinkLine(label: String, url: String, open: (String) -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier.clickable { open(url) }.padding(vertical = 4.dp),
    )
}

// --- The diagram ---

@Composable
private fun Diagram(onPart: (String) -> Unit) {
    val line = MaterialTheme.colorScheme.outline
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Node("WE THE PEOPLE", "elect Congress and the President", Modifier.fillMaxWidth(), strong = true) { onPart("people") }
        Stem(line)
        Node("THE CONSTITUTION", "the supreme law over every branch", Modifier.fillMaxWidth(), strong = true) { onPart("constitution") }
        Stem(line)
        // The three branches, side by side and equal.
        Box(Modifier.fillMaxWidth(0.68f).height(2.dp).background(line))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // Congress's two chambers are equal halves, so they sit side by side.
            Branch(Modifier.weight(1f), line, "LEGISLATIVE", "makes laws", "Congress", "congress", onPart,
                Below.SideBySide(listOf("Senate" to "senate", "House" to "house")))
            // Everyone in the executive branch answers to the President, so they share one box beneath.
            Branch(Modifier.weight(1f), line, "EXECUTIVE", "carries out laws", "President", "president", onPart,
                Below.Together(listOf("Vice President" to "vp", "Cabinet" to "cabinet", "Agencies" to "cabinet")))
            // Cases really do climb this ladder: district court, then appeals, then the Supreme Court.
            Branch(Modifier.weight(1f), line, "JUDICIAL", "interprets laws", "Supreme Court", "courts", onPart,
                Below.Ladder(listOf("Appeals courts" to "courts", "District courts" to "courts")))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "⟷  Equal branches that check one another  ⟷",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(12.dp))
        Node("THE STATES", "governor · legislature · courts", Modifier.fillMaxWidth()) { onPart("states") }
        Stem(line)
        Node("LOCAL", "counties · cities and towns · school boards", Modifier.fillMaxWidth()) { onPart("local") }
    }
}

/** How the parts under a branch's head relate: equals, a shared group, or a ladder of appeal. */
private sealed interface Below {
    val parts: List<Pair<String, String>>
    data class SideBySide(override val parts: List<Pair<String, String>>) : Below
    data class Together(override val parts: List<Pair<String, String>>) : Below
    data class Ladder(override val parts: List<Pair<String, String>>) : Below
}

@Composable
private fun Branch(
    modifier: Modifier,
    line: Color,
    branch: String,
    job: String,
    head: String,
    headId: String,
    onPart: (String) -> Unit,
    below: Below,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Stem(line, 12)
        Node(branch, job, Modifier.fillMaxWidth(), strong = true) { onPart(headId) }
        Stem(line, 10)
        Node(head, null, Modifier.fillMaxWidth()) { onPart(headId) }
        Stem(line, 10)
        when (below) {
            is Below.SideBySide -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                below.parts.forEach { (label, id) -> Node(label, null, Modifier.weight(1f), small = true) { onPart(id) } }
            }
            is Below.Together -> {
                val shape = RoundedCornerShape(10.dp)
                Column(
                    Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
                ) {
                    below.parts.forEachIndexed { i, (label, id) ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().clickable { onPart(id) }.padding(vertical = 6.dp, horizontal = 4.dp),
                        )
                    }
                }
            }
            is Below.Ladder -> below.parts.forEachIndexed { i, (label, id) ->
                if (i > 0) Stem(line, 10)
                Node(label, null, Modifier.fillMaxWidth(), small = true) { onPart(id) }
            }
        }
    }
}

@Composable
private fun Stem(color: Color, heightDp: Int = 14) {
    Box(Modifier.width(2.dp).height(heightDp.dp).background(color))
}

@Composable
private fun Node(title: String, subtitle: String?, modifier: Modifier, strong: Boolean = false, small: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier.clip(shape)
            .background(if (strong) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = if (small) 6.dp else 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            title,
            style = (if (small) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge)
                .copy(fontFamily = if (strong) LocalAppFonts.current.caslon else null),
            fontWeight = FontWeight.SemiBold,
            color = if (strong) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        subtitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = if (strong) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
