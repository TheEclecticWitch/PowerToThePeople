package com.theeclecticwitch.powertothepeople.officials

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import coil3.compose.SubcomposeAsyncImage
import com.theeclecticwitch.powertothepeople.congress.CampaignMoneyCard
import com.theeclecticwitch.powertothepeople.congress.CongressNav
import com.theeclecticwitch.powertothepeople.congress.MemberRecordCards
import com.theeclecticwitch.powertothepeople.congress.OfficeNewsCard
import com.theeclecticwitch.powertothepeople.congress.WhiteHouseNewsCard
import com.theeclecticwitch.powertothepeople.location.LocationStore
import com.theeclecticwitch.powertothepeople.location.communityName
import com.theeclecticwitch.powertothepeople.location.UserLocation
import com.theeclecticwitch.powertothepeople.ui.AppTopBar
import com.theeclecticwitch.powertothepeople.ui.CenteredMessage
import com.theeclecticwitch.powertothepeople.ui.ErrorBox
import com.theeclecticwitch.powertothepeople.ui.Format
import com.theeclecticwitch.powertothepeople.ui.InfoCard
import com.theeclecticwitch.powertothepeople.ui.InitialsBadge
import com.theeclecticwitch.powertothepeople.ui.LoadingBox
import com.theeclecticwitch.powertothepeople.ui.ReadingColumn
import com.theeclecticwitch.powertothepeople.ui.SourceLine
import com.theeclecticwitch.powertothepeople.ui.Tag
import com.theeclecticwitch.powertothepeople.ui.openSafely
import kotlinx.coroutines.delay

/** Loads the federal delegation for a location, with a retry. */
@Composable
fun rememberDelegation(location: UserLocation?): Triple<FederalDelegation?, String?, () -> Unit> {
    var value by remember(location) { mutableStateOf<FederalDelegation?>(null) }
    var error by remember(location) { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(location, attempt) {
        if (location == null) return@LaunchedEffect
        error = null
        try {
            value = FederalOfficials.forLocation(location, forceRefresh = attempt > 0)
        } catch (e: Exception) {
            error = "Couldn't load the list of officials. Check your connection."
        }
    }
    return Triple(value, error, { attempt++ })
}

/** Loads the reader's state officials, with a retry. */
@Composable
fun rememberStateDelegation(location: UserLocation?): Triple<StateDelegation?, String?, () -> Unit> {
    var value by remember(location) { mutableStateOf<StateDelegation?>(null) }
    var error by remember(location) { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(location, attempt) {
        if (location == null) return@LaunchedEffect
        error = null
        try {
            value = StateOfficials.forLocation(location, force = attempt > 0)
        } catch (e: Exception) {
            error = "Couldn't load your state officials. Check your connection."
        }
    }
    return Triple(value, error, { attempt++ })
}

@Composable
fun OfficialsScreen(
    onBack: () -> Unit,
    onOfficial: (String) -> Unit,
    onDirectory: () -> Unit,
    onStateLegislators: (String) -> Unit,
    onSetLocation: () -> Unit,
    onAddOfficial: (Level) -> Unit,
) {
    val location by LocationStore.location.collectAsState()
    val mine by MyRecords.officialsFlow.collectAsState()
    val (delegation, error, retry) = rememberDelegation(location)
    val (stateDelegation, stateError, stateRetry) = rememberStateDelegation(location)
    Scaffold(topBar = { AppTopBar("My Officials", onBack) }) { padding ->
        ReadingColumn(Modifier.padding(padding)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val loc = location
                if (loc == null) {
                    InfoCard(title = "Start here") {
                        Text(
                            "Enter your address to see who represents you in Washington, and which state and local districts you're in.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Button(onClick = onSetLocation) { Text("Set my location") }
                    }
                } else {
                    Text(
                        "${loc.cityOrCounty}, ${loc.stateAbbr} · ${loc.congressionalDistrictLabel}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable(onClick = onSetLocation),
                    )
                    LevelHeading("Federal", "Washington, D.C.")
                    when {
                        delegation != null -> {
                            val d = delegation
                            listOfNotNull(d.president, d.vicePresident).forEach { OfficialRow(it) { onOfficial(it.id) } }
                            d.senators.forEach { OfficialRow(it) { onOfficial(it.id) } }
                            if (d.senators.isEmpty() && loc.stateAbbr !in setOf("DC", "PR", "GU", "VI", "AS", "MP")) {
                                Text("No senators found for ${loc.stateName}. A seat may be vacant.", style = MaterialTheme.typography.bodyMedium)
                            }
                            d.representative?.let { OfficialRow(it) { onOfficial(it.id) } }
                                ?: Text(
                                    "No one currently holds the seat for your district (${loc.districtLabel}). It may be vacant.",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            SourceLine(
                                FederalOfficials.SOURCE_NAME,
                                FederalOfficials.SOURCE_URL,
                                if (d.isStale) "offline - showing the last copy saved" else "checked ${Format.date(d.fetchedAt.toString())}",
                            )
                        }
                        error != null -> ErrorBox(error, retry)
                        else -> LoadingBox("Finding your representatives…")
                    }
                    OutlinedButton(onClick = onDirectory) { Text("Browse all of Congress") }

                    LevelHeading("State", loc.stateName)
                    val stateDistricts = listOfNotNull(loc.stateSenateDistrict, loc.stateHouseDistrict)
                    if (stateDistricts.isNotEmpty()) {
                        Text("You're in ${stateDistricts.joinToString(" and ")}.", style = MaterialTheme.typography.bodyMedium)
                    }
                    when {
                        stateDelegation != null && !stateDelegation.available -> Text(
                            "Your governor and state legislators will appear here after the next data update.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        stateDelegation != null -> {
                            val sd = stateDelegation
                            sd.executives.forEach { OfficialRow(it) { onOfficial(it.id) } }
                            (sd.senators + sd.representatives).forEach { OfficialRow(it) { onOfficial(it.id) } }
                            if (!sd.matched) {
                                Text(
                                    "Your state legislators couldn't be matched to your district automatically.",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            OutlinedButton(onClick = { onStateLegislators(loc.stateAbbr) }) { Text("All of ${loc.stateName}'s legislators") }
                            SourceLine(StateOfficials.SOURCE_NAME, StateOfficials.SOURCE_URL, "checked weekly")
                        }
                        stateError != null -> ErrorBox(stateError, stateRetry)
                        else -> LoadingBox("Finding your state officials…")
                    }
                    mine.filter { it.level == Level.State }.forEach { OfficialRow(it) { onOfficial(it.id) } }
                    OutlinedButton(onClick = { onAddOfficial(Level.State) }) {
                        Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Add someone missing")
                    }

                    LevelHeading("Local", listOfNotNull(loc.place?.let(::communityName), loc.county).joinToString(" · ").ifBlank { "Your area" })
                    mine.filter { it.level == Level.Local }.forEach { OfficialRow(it) { onOfficial(it.id) } }
                    InfoCard {
                        Text(
                            "There's no free national list of mayors, council members, county commissioners or school board members, " +
                                "so this is your own record. Add the people who represent you here, and keep their contact details, " +
                                "your notes, and their campaign promises in one place.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        loc.schoolDistrict?.let {
                            Text("Your school district: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(onClick = { onAddOfficial(Level.Local) }) {
                            Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Add a local official")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LevelHeading(title: String, subtitle: String) {
    Column(Modifier.padding(top = 12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun OfficialPhoto(official: Official, sizeDp: Int) {
    val url = official.photoUrl
    if (url == null) {
        InitialsBadge(official.name, sizeDp)
        return
    }
    SubcomposeAsyncImage(
        model = url,
        contentDescription = "Photo of ${official.name}",
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(sizeDp.dp).clip(RoundedCornerShape(12.dp)),
        loading = { InitialsBadge(official.name, sizeDp) },
        error = { InitialsBadge(official.name, sizeDp) },
    )
}

@Composable
fun OfficialRow(official: Official, onClick: () -> Unit) {
    InfoCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OfficialPhoto(official, 60)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(official.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(official.office, style = MaterialTheme.typography.bodyMedium)
                official.party?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun OfficialDetailScreen(id: String, onBack: () -> Unit, onEdit: (String) -> Unit, congressNav: CongressNav) {
    val mine by MyRecords.officialsFlow.collectAsState()
    val userEntered = mine.firstOrNull { it.id == id }
    var fromPublic by remember(id) { mutableStateOf<Official?>(null) }
    var loaded by remember(id) { mutableStateOf(false) }
    LaunchedEffect(id) {
        if (userEntered == null) {
            fromPublic = try {
                if (id.startsWith("state:")) StateOfficials.byId(id) else FederalOfficials.byId(id)
            } catch (e: Exception) {
                null
            }
        }
        loaded = true
    }
    val official = userEntered ?: fromPublic
    var confirmDelete by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            AppTopBar(official?.name ?: "Official", onBack) {
                if (official?.userEntered == true) {
                    IconButton(onClick = { onEdit(official.id) }) { Icon(Icons.Default.Edit, "Edit") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete") }
                }
            }
        },
    ) { padding ->
        if (official == null) {
            if (loaded) CenteredMessage("This official couldn't be found.") else LoadingBox()
            return@Scaffold
        }
        ReadingColumn(Modifier.padding(padding)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OfficialPhoto(official, 110)
                    Spacer(Modifier.width(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(official.name, style = MaterialTheme.typography.headlineSmall)
                        Text(official.office, style = MaterialTheme.typography.bodyLarge)
                        official.party?.takeIf { it.isNotBlank() }?.let { Tag(it) }
                    }
                }
                if (official.servingSince != null || official.termEnds != null) {
                    InfoCard(title = "In office") {
                        official.servingSince?.let { Text("Serving since ${Format.date(it)}", style = MaterialTheme.typography.bodyMedium) }
                        official.termEnds?.let { Text("Current term ends ${Format.date(it)}", style = MaterialTheme.typography.bodyMedium) }
                    }
                }
                // Senators and representatives; the President and Vice President don't cast roll-call votes.
                if (!official.userEntered && official.level == Level.Federal && !official.id.startsWith("exec:")) {
                    MemberRecordCards(official.id, congressNav)
                    CommitteesCard(official.id)
                    CampaignMoneyCard(official.id)
                    OfficeNewsCard(official.id, official.website)
                }
                if (official.id.startsWith("exec:")) WhiteHouseNewsCard()
                ContactCard(official)
                if (official.districtOffices.isNotEmpty()) {
                    InfoCard(title = "Offices back home") {
                        official.districtOffices.forEachIndexed { i, o ->
                            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Text(o.city, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(o.address, style = MaterialTheme.typography.bodyMedium)
                            o.phone?.let { LinkText(it, "tel:${it.filter { c -> c.isDigit() }}") }
                        }
                    }
                }
                if (official.social.isNotEmpty()) {
                    InfoCard(title = "On social media") {
                        official.social.forEach { LinkText(it.label, it.url) }
                        Text(
                            "Opens in the app or website. Power to the People doesn't show posts; follow them there if you'd like.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (official.links.isNotEmpty()) {
                    InfoCard(title = "Look deeper") {
                        official.links.forEach { LinkText(it.label, it.url) }
                    }
                }
                NotesCard(official.id)
                PromisesCard(official.id)
                if (official.sourceName != null) {
                    SourceLine(official.sourceName, official.sourceUrl)
                } else if (official.userEntered) {
                    Text("You added this official. The details are yours and stay on this device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    if (confirmDelete && official != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${official.name}?") },
            text = { Text("Their contact details, your notes and their campaign promises will be removed from this device.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; MyRecords.deleteOfficial(official.id); onBack() }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

/** Committee and subcommittee seats, full committees first with their subcommittees beneath. */
@Composable
private fun CommitteesCard(bioguide: String) {
    var seats by remember(bioguide) { mutableStateOf<List<CommitteeSeat>?>(null) }
    var failed by remember(bioguide) { mutableStateOf(false) }
    LaunchedEffect(bioguide) {
        seats = try { Committees.forMember(bioguide) } catch (e: Exception) { failed = true; null }
    }
    val list = seats
    if (failed || (list != null && list.isEmpty())) return
    InfoCard(title = "Committees") {
        if (list == null) {
            LoadingBox("Loading committees…")
            return@InfoCard
        }
        list.groupBy { it.committee }.entries.forEachIndexed { i, (committee, inIt) ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val full = inIt.firstOrNull { it.subcommittee == null }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(committee, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                full?.title?.let { Spacer(Modifier.width(8.dp)); Tag(it) }
            }
            inIt.filter { it.subcommittee != null }.forEach { sub ->
                Text(
                    listOfNotNull(sub.subcommittee, sub.title?.let { "($it)" }).joinToString(" "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
        SourceLine(FederalOfficials.SOURCE_NAME, FederalOfficials.SOURCE_URL)
    }
}

@Composable
private fun ContactCard(o: Official) {
    InfoCard(title = "Contact") {
        val any = listOfNotNull(o.phone, o.email, o.contactForm, o.website, o.address).isNotEmpty()
        if (!any) Text("No contact details yet.", style = MaterialTheme.typography.bodyMedium)
        o.phone?.let { LinkText("Call $it", "tel:${it.filter { c -> c.isDigit() }}") }
        o.email?.let { LinkText("Email $it", "mailto:$it") }
        o.contactForm?.let { LinkText("Send a message (official contact form)", it) }
        o.website?.let { LinkText("Official website", it) }
        o.address?.let {
            Text("Mailing address", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
        if (o.level == Level.Federal && o.email == null && o.contactForm != null) {
            Text(
                "Members of Congress don't publish email addresses; their contact form is how email reaches them.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LinkText(label: String, url: String) {
    val uri = LocalUriHandler.current
    Text(
        label,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier.fillMaxWidth().clickable { openSafely(uri, url) }.padding(vertical = 4.dp),
    )
}

@Composable
private fun NotesCard(officialId: String) {
    val records by MyRecords.recordsFlow.collectAsState()
    val saved = records[officialId]?.notes ?: ""
    var text by remember(officialId) { mutableStateOf(saved) }
    // Saved a moment after typing stops, rather than on every key.
    LaunchedEffect(text) {
        if (text != saved) {
            delay(600)
            MyRecords.setNotes(officialId, text)
        }
    }
    InfoCard(title = "My notes") {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            placeholder = { Text("Anything you want to remember - a meeting, a phone call, how they answered a question.") },
        )
        Text("Private: kept only on this device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PromisesCard(officialId: String) {
    val records by MyRecords.recordsFlow.collectAsState()
    val promises = records[officialId]?.promises.orEmpty()
    var adding by remember { mutableStateOf(false) }
    InfoCard(title = "Campaign promises") {
        if (promises.isEmpty()) {
            Text(
                "Keep track of what they promised, and whether it happened.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        promises.forEachIndexed { i, p ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            PromiseRow(officialId, p)
        }
        OutlinedButton(onClick = { adding = true }) {
            Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Add a promise")
        }
    }
    if (adding) AddPromiseDialog(onDismiss = { adding = false }) { text, source ->
        MyRecords.addPromise(officialId, text, source)
        adding = false
    }
}

@Composable
private fun PromiseRow(officialId: String, p: Promise) {
    var menu by remember { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(p.text, style = MaterialTheme.typography.bodyLarge)
        if (p.source.isNotBlank()) {
            val isLink = p.source.startsWith("http")
            Text(
                "Source: ${p.source}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = if (isLink) TextDecoration.Underline else null,
                modifier = if (isLink) Modifier.clickable { openSafely(uri, p.source) } else Modifier,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column {
                FilterChip(selected = true, onClick = { menu = true }, label = { Text(p.status.label) })
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    PromiseStatus.entries.forEach { s ->
                        DropdownMenuItem(text = { Text(s.label) }, onClick = {
                            MyRecords.setPromiseStatus(officialId, p.id, s); menu = false
                        })
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Text("Added ${Format.date(p.added)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = { MyRecords.deletePromise(officialId, p.id) }) { Icon(Icons.Default.Delete, "Delete promise") }
        }
    }
}

@Composable
private fun AddPromiseDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var text by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a campaign promise") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(text, { text = it }, label = { Text("What did they promise?") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    source, { source = it },
                    label = { Text("Where? (link or description)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text, source) }, enabled = text.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Adding or editing an official the reader keeps track of themselves. */
@Composable
fun EditOfficialScreen(id: String?, level: Level, onBack: () -> Unit, onSaved: (String) -> Unit) {
    val mine by MyRecords.officialsFlow.collectAsState()
    val existing = id?.let { key -> mine.firstOrNull { it.id == key } }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var office by remember { mutableStateOf(existing?.office ?: "") }
    var chosenLevel by remember { mutableStateOf(existing?.level ?: level) }
    var party by remember { mutableStateOf(existing?.party ?: "") }
    var phone by remember { mutableStateOf(existing?.phone ?: "") }
    var email by remember { mutableStateOf(existing?.email ?: "") }
    var website by remember { mutableStateOf(existing?.website ?: "") }
    var address by remember { mutableStateOf(existing?.address ?: "") }
    var termEnds by remember { mutableStateOf(existing?.termEnds ?: "") }

    fun save() {
        val official = Official(
            id = existing?.id ?: "mine:${newId()}",
            name = name.trim(),
            office = office.trim(),
            level = chosenLevel,
            party = party.trim().ifBlank { null },
            phone = phone.trim().ifBlank { null },
            email = email.trim().ifBlank { null },
            website = website.trim().ifBlank { null }?.let { if (it.startsWith("http")) it else "https://$it" },
            address = address.trim().ifBlank { null },
            termEnds = termEnds.trim().ifBlank { null },
            userEntered = true,
        )
        MyRecords.saveOfficial(official)
        onSaved(official.id)
    }

    Scaffold(topBar = { AppTopBar(if (existing == null) "Add an Official" else "Edit ${existing.name}", onBack) }) { padding ->
        ReadingColumn(Modifier.padding(padding)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Level.Local, Level.State).forEach { l ->
                        FilterChip(selected = chosenLevel == l, onClick = { chosenLevel = l }, label = { Text(l.label) })
                    }
                }
                Field(name, { name = it }, "Name")
                Field(office, { office = it }, "Office (e.g. Mayor, City Council Ward 3, State Senator)")
                Field(party, { party = it }, "Party (optional)")
                Field(phone, { phone = it }, "Office phone", KeyboardType.Phone)
                Field(email, { email = it }, "Email", KeyboardType.Email)
                Field(website, { website = it }, "Website", KeyboardType.Uri)
                OutlinedTextField(address, { address = it }, label = { Text("Office address") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                Field(termEnds, { termEnds = it }, "Term ends (optional, e.g. 2027-12-31)")
                Text(
                    "Tip: most city, county and school board websites list their officials' contact details.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = { save() }, enabled = name.isNotBlank() && office.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    Text("Save")
                }
            }
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, type: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(
        value, onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = type),
        modifier = Modifier.fillMaxWidth(),
    )
}
