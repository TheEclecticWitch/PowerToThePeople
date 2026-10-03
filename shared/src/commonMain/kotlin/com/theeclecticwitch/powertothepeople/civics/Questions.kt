package com.theeclecticwitch.powertothepeople.civics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

/** A question people often have, answered plainly, with where the answer comes from. */
data class Question(
    val topic: String,
    val question: String,
    val answer: String,
    val cite: Cite? = null,
    val source: Pair<String, String>? = null,
)

private const val SENATE_GLOSSARY = "https://www.senate.gov/legislative/glossary.htm"
private const val HOW_LAWS = "https://www.congress.gov/help/learn-about-the-legislative-process"
private const val ELECTORAL = "https://www.archives.gov/electoral-college/about"
private const val COURTS = "https://www.uscourts.gov/about-federal-courts/educational-resources/about-educational-outreach/activity-resources/about"

object CommonQuestions {
    val topics = listOf("Congress", "The President", "The courts", "The Constitution", "You and elections")

    val all = listOf(
        // --- Congress ---
        Question(
            "Congress", "Is Congress separate from the Senate and the House?",
            "No. \"Congress\" is the name for the Senate and the House of Representatives together. There is no third " +
                "group. When people say a bill \"passed Congress,\" they mean it passed both the House and the Senate.",
            Cite("Article I, Section 1", article = 1),
        ),
        Question(
            "Congress", "Does Congress ever meet all together?",
            "Each chamber normally meets on its own: the Senate in the north wing of the Capitol and the House in the " +
                "south wing, each with its own schedule, rules and votes. They meet together, in a \"joint session\" in the " +
                "House chamber, only for special occasions: the President's State of the Union address, counting the " +
                "Electoral College votes for President, and speeches by visiting foreign leaders. Laws are not passed in " +
                "joint sessions.",
            Cite("Amendment XII", amendment = 12),
        ),
        Question(
            "Congress", "Does a law need a separate vote in \"Congress\"?",
            "No. A bill must pass the House and the Senate separately, each by its own vote, in exactly the same words. " +
                "If the two pass different versions, they must agree on one text and both vote on it again. Then it goes to " +
                "the President to sign or veto. There is no extra vote of \"Congress\" as a whole.",
            Cite("Article I, Section 7", article = 1), "Congress.gov: the legislative process" to HOW_LAWS,
        ),
        Question(
            "Congress", "Why does every state get two senators but different numbers of representatives?",
            "It was a compromise at the Constitutional Convention of 1787. Large states wanted representation by " +
                "population; small states wanted equal votes. The delegates did both: the House is by population, and the " +
                "Senate gives every state two senators. A bill must pass both, so it needs support on both terms.",
            Cite("Article I, Sections 2 and 3", article = 1),
        ),
        Question(
            "Congress", "How is the number of representatives decided?",
            "The House has had 435 members since 1913, a number fixed by a law passed in 1929. After each census, every " +
                "ten years, the 435 seats are divided among the states by population, so a growing state can gain seats and " +
                "a shrinking one can lose them. Every state gets at least one.",
            Cite("Article I, Section 2", article = 1),
        ),
        Question(
            "Congress", "How long is a Congress, and what is a session?",
            "Each Congress lasts two years, from January 3 of an odd-numbered year, and is numbered: the 119th Congress " +
                "runs from 2025 to 2027. Each year of it is a session, so a Congress usually has two sessions.",
            Cite("Amendment XX", amendment = 20),
        ),
        Question(
            "Congress", "What is a committee, and why does it matter?",
            "Committees are smaller groups of members who specialize in one area, such as agriculture, the armed " +
                "services or the budget. Nearly every bill is sent to a committee first, where it is studied, changed in " +
                "\"markup,\" or simply never acted on. Most bills never leave committee, so committees decide much of what " +
                "the full House or Senate ever votes on.",
            source = "Congress.gov: the legislative process" to HOW_LAWS,
        ),
        Question(
            "Congress", "What is the difference between a roll-call vote and a voice vote?",
            "In a voice vote, members call out \"aye\" or \"no\" together and the presiding officer judges which side is " +
                "louder; no one's individual vote is recorded. In a roll-call (recorded) vote, each member's vote is " +
                "written down and made public. The votes in this app are roll-call votes, which is why each member's " +
                "position can be shown.",
            Cite("Article I, Section 5", article = 1),
        ),
        Question(
            "Congress", "What is a filibuster, and what is \"cloture\"?",
            "The Senate allows long debate, and a senator can use it to delay or block a vote: that is a filibuster. To " +
                "end debate and move to a vote, the Senate must vote for \"cloture.\" For most bills that takes three-fifths " +
                "of senators, 60 when all seats are filled. Since rule changes in 2013 and 2017, nominations need only a " +
                "simple majority. The filibuster is a Senate rule, not part of the Constitution, and the House has no such rule.",
            source = "U.S. Senate glossary" to SENATE_GLOSSARY,
        ),
        Question(
            "Congress", "What is the difference between a bill and a resolution?",
            "A bill, if passed by both chambers and signed, becomes law. A joint resolution works the same way and is " +
                "also used to propose amendments to the Constitution (which skip the President). A concurrent resolution " +
                "passes both chambers but doesn't become law; it handles their shared business. A simple resolution passes " +
                "one chamber and speaks only for it, such as its own rules or an honorary statement.",
            source = "U.S. Senate glossary" to SENATE_GLOSSARY,
        ),
        Question(
            "Congress", "Who is the Speaker of the House?",
            "The Speaker is chosen by a vote of the whole House and leads it: deciding which bills come to the floor and " +
                "running its proceedings. The Speaker is next in line to become President after the Vice President.",
            Cite("Article I, Section 2", article = 1),
        ),
        Question(
            "Congress", "What is a government shutdown?",
            "No federal money may be spent unless Congress passes a law to spend it. When the laws funding the " +
                "government (appropriations) expire without new ones, agencies must stop work the law doesn't treat as " +
                "essential, and many employees are sent home until funding is passed.",
            Cite("Article I, Section 9", article = 1),
        ),
        Question(
            "Congress", "What is the debt ceiling?",
            "A limit, set by law, on how much the Treasury may borrow in total. Raising or suspending it doesn't approve " +
                "new spending; it lets the government borrow to pay for spending and tax laws Congress has already passed. " +
                "If it isn't raised in time, the Treasury can run out of ways to pay the bills it already owes.",
            Cite("Article I, Section 8", article = 1),
        ),
        Question(
            "Congress", "What is a \"pro forma\" session?",
            "A very short meeting, often only minutes long, in which no business is done. A chamber holds them to meet " +
                "the Constitution's rule that neither chamber may adjourn for more than three days without the other's " +
                "consent, and so that it isn't formally in recess.",
            Cite("Article I, Section 5", article = 1),
        ),
        // --- The President ---
        Question(
            "The President", "What happens if the President vetoes a bill?",
            "The bill goes back to Congress with the President's objections. It becomes law anyway if two-thirds of " +
                "the House and two-thirds of the Senate vote to override the veto. Otherwise it does not become law.",
            Cite("Article I, Section 7", article = 1),
        ),
        Question(
            "The President", "What is a pocket veto?",
            "If the President neither signs nor vetoes a bill for 10 days (not counting Sundays), it becomes law without " +
                "a signature. But if Congress has adjourned in that time so the bill can't be returned, it does not become " +
                "law. That is a pocket veto, and Congress can't override it; it would have to pass the bill again.",
            Cite("Article I, Section 7", article = 1),
        ),
        Question(
            "The President", "What is an executive order?",
            "A written direction from the President to the executive branch on how to carry out its work. It must rest " +
                "on power the Constitution or a law gives the President, and it can't make new law on its own. A later " +
                "President can change or cancel it, Congress can override it by passing a law, and courts can strike it " +
                "down. The Legislation section lists every one since 1994.",
            Cite("Article II, Section 3", article = 2),
        ),
        Question(
            "The President", "Can the President declare war?",
            "No. Only Congress can declare war. The President commands the armed forces. A 1973 law, the War Powers " +
                "Resolution, requires the President to notify Congress within 48 hours of sending forces into hostilities " +
                "and to withdraw them within 60 days unless Congress approves.",
            Cite("Article I, Section 8", article = 1),
        ),
        Question(
            "The President", "How does the Electoral College work?",
            "Each state has as many electors as it has senators and representatives; Washington, D.C. has 3. That makes " +
                "538, and it takes 270 to win. In 48 states the winner of the state's popular vote gets all its electors; " +
                "Maine and Nebraska split theirs by congressional district. If no one gets 270, the House chooses the " +
                "President, with each state's delegation casting one vote.",
            Cite("Amendment XII", amendment = 12), "National Archives: the Electoral College" to ELECTORAL,
        ),
        Question(
            "The President", "Who becomes President if the President can't serve?",
            "The Vice President. After that, by law: the Speaker of the House, the President pro tempore of the Senate, " +
                "and then the Cabinet secretaries in an order set by law, starting with the Secretary of State. A President can also hand power to the Vice President for a time, for example during surgery.",
            Cite("Amendment XXV", amendment = 25),
        ),
        Question(
            "The President", "What is impeachment?",
            "Impeachment is a formal charge of \"Treason, Bribery, or other high Crimes and Misdemeanors\" against a " +
                "President, judge or other federal official. The House impeaches by majority vote. The Senate then holds a " +
                "trial; removing the official takes two-thirds of senators present. When a President is tried, the Chief " +
                "Justice presides. Being impeached is not the same as being removed.",
            Cite("Article II, Section 4", article = 2),
        ),
        // --- The courts ---
        Question(
            "The courts", "How does a case get to the Supreme Court?",
            "Most cases start in a lower court. A party who loses can ask the Supreme Court to review it by filing a " +
                "petition. The justices choose which cases to hear, and they accept only a small share of the thousands " +
                "they are asked to take each year, usually fewer than 100. A few kinds of cases, such as disputes between " +
                "states, can start there.",
            Cite("Article III, Section 2", article = 3), "U.S. Courts: about the Supreme Court" to COURTS,
        ),
        Question(
            "The courts", "What is judicial review?",
            "The power of courts to decide whether a law or government action goes against the Constitution, and if it " +
                "does, to refuse to enforce it. The Supreme Court established it in Marbury v. Madison in 1803. It is a " +
                "main way the courts check the other two branches.",
            Cite("Article III, Section 2", article = 3),
        ),
        Question(
            "The courts", "Why do federal judges serve for life?",
            "The Constitution says they hold office \"during good Behaviour\" and their pay can't be cut. The idea is that " +
                "judges should decide cases by the law, without worrying about re-election or losing their jobs. They can " +
                "still be impeached and removed.",
            Cite("Article III, Section 1", article = 3),
        ),
        // --- The Constitution ---
        Question(
            "The Constitution", "How is the Constitution changed?",
            "An amendment must first be proposed, by two-thirds of both the House and the Senate or by a convention " +
                "called by two-thirds of the states, and then ratified by three-fourths of the states. The President has " +
                "no formal role. It has been amended 27 times; the first ten amendments are the Bill of Rights.",
            Cite("Article V", article = 5),
        ),
        Question(
            "The Constitution", "Whom does the Bill of Rights protect us from?",
            "It limits what government may do to people: for example, it can't abridge freedom of speech or carry out " +
                "unreasonable searches. At first it applied only to the national government. After the Fourteenth " +
                "Amendment (1868), the courts applied most of its protections to state and local governments too. It " +
                "doesn't generally apply to private businesses or individuals.",
            Cite("Amendment XIV", amendment = 14),
        ),
        Question(
            "The Constitution", "What can the national government do, and what is left to the states?",
            "The national government has only the powers the Constitution gives it, such as coining money, regulating " +
                "trade between states and making treaties. Powers not given to it, nor denied to the states, belong to the " +
                "states or the people. That is why schools, most criminal law, driving and marriage laws differ from " +
                "state to state.",
            Cite("Amendment X", amendment = 10),
        ),
        Question(
            "The Constitution", "Is the United States a democracy or a republic?",
            "Both words describe it. It is a republic: the people govern through representatives they elect, under a " +
                "written Constitution that limits what any majority can do. It is democratic in that those representatives " +
                "are chosen by the people's votes. The Constitution itself guarantees every state \"a Republican Form of " +
                "Government.\"",
            Cite("Article IV, Section 4", article = 4),
        ),
        // --- You and elections ---
        Question(
            "You and elections", "Who can vote?",
            "U.S. citizens 18 and older, subject to their state's rules on registration and residence. Several amendments " +
                "forbid denying the vote because of race (Fifteenth), sex (Nineteenth), failure to pay a poll tax " +
                "(Twenty-fourth) or age for anyone 18 or older (Twenty-sixth).",
            Cite("Amendment XXVI", amendment = 26),
        ),
        Question(
            "You and elections", "When are federal elections held?",
            "On the Tuesday after the first Monday in November, in even-numbered years. Every House seat and about a " +
                "third of Senate seats are on the ballot each time; the President is elected every four years.",
            Cite("Article I, Section 4", article = 1),
        ),
        Question(
            "You and elections", "What is the difference between a primary and a general election?",
            "A primary chooses each party's candidate for an office; the rules (who may vote in which party's primary) " +
                "are set by each state and party. The general election, in November, chooses who actually wins the office " +
                "from among the candidates.",
            Cite("Article I, Section 4", article = 1),
        ),
        Question(
            "You and elections", "Does contacting my members of Congress make a difference?",
            "Their offices track what constituents, the people who live in their district or state, tell them, by call, " +
                "letter or message, and members often say it shapes their decisions. Offices generally respond to their " +
                "own constituents, which is why the app points you to your own members. The First Amendment protects your " +
                "right \"to petition the Government for a redress of grievances.\"",
            Cite("Amendment I", amendment = 1),
        ),
    )

    fun search(query: String, topic: String?): List<Question> {
        val words = query.lowercase().split(Regex("\\s+")).filter { it.length >= 2 }
        return all.filter { q ->
            (topic == null || q.topic == topic) &&
                words.all { w -> q.question.contains(w, true) || q.answer.contains(w, true) }
        }
    }
}

@Composable
fun CommonQuestionsList(onArticle: (Int) -> Unit, onAmendment: (Int) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var topic by rememberSaveable { mutableStateOf<String?>(null) }
    var open by remember { mutableStateOf(setOf<String>()) }
    val uri = LocalUriHandler.current
    val shown = CommonQuestions.search(query, topic)
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Questions many people have about how our government works, answered plainly. Each answer shows " +
                        "where it comes from so you can read it for yourself.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Clear, "Clear") }
                    },
                    placeholder = { Text("Search, e.g. veto or filibuster") },
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = topic == null, onClick = { topic = null }, label = { Text("All") })
                    CommonQuestions.topics.forEach { t ->
                        FilterChip(selected = topic == t, onClick = { topic = if (topic == t) null else t }, label = { Text(t) })
                    }
                }
                if (shown.isEmpty()) Text("No questions match. Try another word.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        items(shown, key = { it.question }) { q ->
            val expanded = q.question in open
            InfoCard(onClick = { open = if (expanded) open - q.question else open + q.question }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(q.question, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, if (expanded) "Hide answer" else "Show answer")
                }
                if (expanded) {
                    Text(q.answer, style = MaterialTheme.typography.bodyLarge)
                    q.cite?.let { c ->
                        Text(
                            "In the Constitution: ${c.label}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier.clickable { c.article?.let(onArticle); c.amendment?.let(onAmendment) }.padding(vertical = 2.dp),
                        )
                    }
                    q.source?.let { (label, url) ->
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
