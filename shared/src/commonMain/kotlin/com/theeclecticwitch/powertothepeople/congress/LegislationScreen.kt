package com.theeclecticwitch.powertothepeople.congress

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theeclecticwitch.powertothepeople.location.LocationStore
import com.theeclecticwitch.powertothepeople.officials.StateNames
import com.theeclecticwitch.powertothepeople.ui.AppTopBar
import com.theeclecticwitch.powertothepeople.ui.LoopingTabs
import com.theeclecticwitch.powertothepeople.ui.ErrorBox
import com.theeclecticwitch.powertothepeople.ui.Format
import com.theeclecticwitch.powertothepeople.ui.LoadingBox
import com.theeclecticwitch.powertothepeople.ui.ReadingColumn
import com.theeclecticwitch.powertothepeople.ui.SourceLine
import com.theeclecticwitch.powertothepeople.ui.Tag
import com.theeclecticwitch.powertothepeople.ui.openSafely
import kotlinx.coroutines.launch

private const val PAGE = 100

/**
 * Every kind of legislation, in one place and searchable, so a bill or order heard about in the news can be
 * found by its number or a few words of its name: Congress's bills, the President's executive orders, and
 * state legislatures' bills.
 */
@Composable
fun LegislationScreen(onBack: () -> Unit, nav: CongressNav) {
    Scaffold(topBar = { AppTopBar("Legislation", onBack) }) { padding ->
        ReadingColumn(Modifier.padding(padding)) {
            LoopingTabs(listOf("Congress", "President", "States"), Modifier.fillMaxSize()) { tab ->
                when (tab) {
                    0 -> CongressBills(nav)
                    1 -> PresidentOrders()
                    else -> StateBillSearch()
                }
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String, onSearch: (() -> Unit)? = null) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        leadingIcon = { Icon(Icons.Default.Search, null) },
        trailingIcon = {
            if (value.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Default.Clear, "Clear") }
        },
        placeholder = { Text(placeholder) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke() }),
    )
}

// --- Congress ---

@Composable
private fun CongressBills(nav: CongressNav) {
    val (load, retry) = rememberLoad(Unit, "Couldn't load the list of bills. Check your connection.") { force -> CongressData.billList(force) }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(BillFilter.All) }
    var shown by remember(query, filter) { mutableIntStateOf(PAGE) }
    when (val l = load) {
        Load.Loading -> LoadingBox("Loading every bill in Congress…")
        is Load.Failed -> ErrorBox(l.message, retry)
        is Load.Done -> {
            val results = remember(l.value, query, filter) { searchBills(l.value, query, filter) }
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SearchField(query, { query = it }, "Bill number or words, e.g. HR 1 or farm bill")
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            BillFilter.entries.forEach { f ->
                                FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
                            }
                        }
                        Text(
                            when {
                                results.isEmpty() && billKeyFromQuery(query) != null -> "No bill by that number in the ${Format.ordinal(CongressData.CONGRESS)} Congress."
                                results.isEmpty() -> "No bills match. Try fewer or different words."
                                query.isBlank() -> "${Format.commas(results.size.toLong())} bills and resolutions in the ${Format.ordinal(CongressData.CONGRESS)} Congress, latest activity first."
                                else -> "${Format.commas(results.size.toLong())} match${if (results.size == 1) "" else "es"}, latest activity first."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(results.take(shown), key = { it.first }) { (key, b) ->
                    BillRow(key, b) { nav.bill(key) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                if (results.size > shown) {
                    item { OutlinedButton(onClick = { shown += PAGE }) { Text("Show more") } }
                }
                item { SourceLine("Congress.gov (Library of Congress)", "https://www.congress.gov/", "gathered every six hours", Modifier.padding(top = 8.dp)) }
            }
        }
    }
}

// --- The President ---

@Composable
private fun PresidentOrders() {
    val (load, retry) = rememberLoad(Unit, "Couldn't load executive orders. Check your connection.") { force -> ExecutiveOrders.all(force) }
    var query by rememberSaveable { mutableStateOf("") }
    var president by rememberSaveable { mutableStateOf<String?>(null) }
    var shown by remember(query, president) { mutableIntStateOf(PAGE) }
    val uri = LocalUriHandler.current
    when (val l = load) {
        Load.Loading -> LoadingBox("Loading executive orders…")
        is Load.Failed -> ErrorBox(l.message, retry)
        is Load.Done -> {
            // Newest president first, as the Federal Register lists them.
            val presidents = remember(l.value) { l.value.mapNotNull { it.president }.distinct() }
            val results = remember(l.value, query, president) { ExecutiveOrders.search(l.value, query, president) }
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SearchField(query, { query = it }, "Order number or words, e.g. 14434 or tariffs")
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = president == null, onClick = { president = null }, label = { Text("All presidents") })
                            presidents.forEach { p ->
                                FilterChip(selected = president == p, onClick = { president = if (president == p) null else p }, label = { Text(p) })
                            }
                        }
                        Text(
                            if (results.isEmpty()) "No executive orders match." else "${Format.commas(results.size.toLong())} executive orders, newest first. Tap one to read it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(results.take(shown), key = { it.number ?: it.url ?: it.title.orEmpty() }) { o ->
                    Column(
                        Modifier.fillMaxWidth().clickable(enabled = o.url != null) { o.url?.let { openSafely(uri, it) } }.padding(vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            listOfNotNull(o.number?.let { "Executive Order $it" }, o.signed?.let { "signed ${Format.date(it)}" }, o.president).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(o.title ?: "Executive order", style = MaterialTheme.typography.titleSmall)
                        o.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                if (results.size > shown) {
                    item { OutlinedButton(onClick = { shown += PAGE }) { Text("Show more") } }
                }
                item { SourceLine(ExecutiveOrders.SOURCE_NAME, ExecutiveOrders.SOURCE_URL, "checked every six hours", Modifier.padding(top = 8.dp)) }
            }
        }
    }
}

// --- The states ---

@Composable
private fun StateBillSearch() {
    val location by LocationStore.location.collectAsState()
    var state by rememberSaveable { mutableStateOf(location?.stateAbbr?.takeIf { it.length == 2 } ?: "") }
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<StateBillResults?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current

    fun run(page: Int = 1) {
        if (state.isBlank()) return
        busy = true
        error = null
        scope.launch {
            try {
                val found = StateBills.search(state, query, page)
                results = if (page > 1) results?.let { it.copy(page = found.page, bills = it.bills + found.bills) } ?: found else found
            } catch (e: StateSearchUnavailable) {
                error = e.message
            } catch (e: Exception) {
                error = "Couldn't search state bills. Check your connection."
            }
            busy = false
        }
    }
    // The reader's own state's latest bills, before they search.
    LaunchedEffect(state) { if (state.isNotBlank()) run() }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    OutlinedButton(onClick = { picking = true }) {
                        Text(if (state.isBlank()) "Choose a state" else StateNames.of(state))
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                    DropdownMenu(expanded = picking, onDismissRequest = { picking = false }) {
                        StateNames.all.forEach { (abbr, name) ->
                            DropdownMenuItem(text = { Text(name) }, onClick = { state = abbr; picking = false; results = null })
                        }
                    }
                }
                SearchField(query, { query = it }, "Bill number or words, e.g. HB 1 or paid leave", onSearch = { run() })
                Button(onClick = { run() }, enabled = state.isNotBlank() && !busy) { Text("Search") }
                val r = results
                if (r != null && error == null) {
                    Text(
                        if (r.bills.isEmpty()) "No bills match in ${StateNames.of(r.state.uppercase())}."
                        else "${Format.commas(r.total.toLong())} bill${if (r.total == 1) "" else "s"} in ${StateNames.of(r.state.uppercase())}, latest activity first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        error?.let { item { ErrorBox(it) { run() } } }
        if (busy && results == null) item { LoadingBox("Searching…") }
        results?.let { r ->
            items(r.bills, key = { it.id }) { b ->
                Column(
                    Modifier.fillMaxWidth().clickable(enabled = b.url != null) { b.url?.let { openSafely(uri, it) } }.padding(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        listOfNotNull(b.number, b.session?.let { "session $it" }, b.introduced?.let { "introduced ${Format.date(it)}" }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(b.title ?: b.number, style = MaterialTheme.typography.titleSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    b.latestAction?.let {
                        Text(
                            listOfNotNull(b.latestActionDate?.let { d -> Format.date(d) }, it).joinToString(": "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (b.subjects.isNotEmpty()) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            b.subjects.forEach { Tag(it) }
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            if (r.page < r.pages) {
                item { OutlinedButton(onClick = { run(r.page + 1) }, enabled = !busy) { Text("Show more") } }
            }
        }
        item { SourceLine(StateBills.SOURCE_NAME, StateBills.SOURCE_URL, "searched live", Modifier.padding(top = 8.dp)) }
    }
}
