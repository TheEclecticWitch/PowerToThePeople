package com.theeclecticwitch.powertothepeople.officials

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.theeclecticwitch.powertothepeople.ui.AppTopBar
import com.theeclecticwitch.powertothepeople.ui.ErrorBox
import com.theeclecticwitch.powertothepeople.ui.LoadingBox
import com.theeclecticwitch.powertothepeople.ui.ReadingColumn
import com.theeclecticwitch.powertothepeople.ui.SourceLine

/** Every member of the House and Senate, searchable by name or state. */
@Composable
fun DirectoryScreen(onBack: () -> Unit, onOfficial: (String) -> Unit, initialChamber: String? = null) {
    var members by remember { mutableStateOf<List<Member>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(attempt) {
        error = null
        try {
            members = FederalOfficials.allMembers(forceRefresh = attempt > 0)
        } catch (e: Exception) {
            error = "Couldn't load the list of members. Check your connection."
        }
    }
    var query by rememberSaveable { mutableStateOf("") }
    var chamber by rememberSaveable { mutableStateOf(initialChamber) }
    var party by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(topBar = { AppTopBar("All of Congress", onBack) }) { padding ->
        ReadingColumn(Modifier.padding(padding)) {
            val all = members
            when {
                all != null -> {
                    val parties = all.mapNotNull { it.official.party }.groupingBy { it }.eachCount()
                        .entries.sortedByDescending { it.value }.map { it.key }
                    val shown = all.filter { m ->
                        (chamber == null || m.chamber == chamber) &&
                            (party == null || m.official.party == party) &&
                            matches(m, query)
                    }.sortedWith(compareBy({ StateNames.of(it.state) }, { if (it.chamber == "senate") 0 else 1 }, { it.district ?: 0 }, { it.official.name }))
                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = query,
                                    onValueChange = { query = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    leadingIcon = { Icon(Icons.Default.Search, null) },
                                    trailingIcon = {
                                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Clear, "Clear") }
                                    },
                                    placeholder = { Text("Name or state, e.g. Ohio or OH") },
                                )
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(null to "Both chambers", "senate" to "Senate", "house" to "House").forEach { (value, label) ->
                                        FilterChip(selected = chamber == value, onClick = { chamber = value }, label = { Text(label) })
                                    }
                                }
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilterChip(selected = party == null, onClick = { party = null }, label = { Text("All parties") })
                                    parties.forEach { p ->
                                        FilterChip(selected = party == p, onClick = { party = if (party == p) null else p }, label = { Text(p) })
                                    }
                                }
                                Text(
                                    "${shown.size} of ${all.size} members",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        items(shown, key = { it.official.id }) { m -> OfficialRow(m.official) { onOfficial(m.official.id) } }
                        item {
                            SourceLine(FederalOfficials.SOURCE_NAME, FederalOfficials.SOURCE_URL, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
                error != null -> ErrorBox(error!!) { attempt++ }
                else -> LoadingBox("Loading every member of Congress…")
            }
        }
    }
}

/** Every legislator in one state, searchable by name or district. */
@Composable
fun StateLegislatorsScreen(state: String, onBack: () -> Unit, onOfficial: (String) -> Unit) {
    var people by remember(state) { mutableStateOf<List<Official>?>(null) }
    var error by remember(state) { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(state, attempt) {
        error = null
        try {
            people = StateOfficials.legislators(state)
        } catch (e: Exception) {
            error = "Couldn't load the legislators. Check your connection."
        }
    }
    var query by rememberSaveable { mutableStateOf("") }
    Scaffold(topBar = { AppTopBar("${StateNames.of(state)} legislators", onBack) }) { padding ->
        ReadingColumn(Modifier.padding(padding)) {
            val all = people
            when {
                all != null -> {
                    val q = query.trim()
                    val shown = all.filter { q.isEmpty() || it.name.contains(q, true) || it.office.contains(q, true) }
                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Default.Search, null) },
                                placeholder = { Text("Name or district") },
                            )
                        }
                        item {
                            Text("${shown.size} of ${all.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        items(shown, key = { it.id }) { o -> OfficialRow(o) { onOfficial(o.id) } }
                        item { SourceLine(StateOfficials.SOURCE_NAME, StateOfficials.SOURCE_URL, modifier = Modifier.padding(top = 8.dp)) }
                    }
                }
                error != null -> ErrorBox(error!!) { attempt++ }
                else -> LoadingBox("Loading legislators…")
            }
        }
    }
}

/** A name, a state's name or its two letters ("OH" finds Ohio's delegation, not every "John"). */
internal fun matches(m: Member, query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    if (q.length == 2 && StateNames.isAbbreviation(q)) return q.equals(m.state, ignoreCase = true)
    return m.official.name.contains(q, ignoreCase = true) || StateNames.of(m.state).contains(q, ignoreCase = true)
}
