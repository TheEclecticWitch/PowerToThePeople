package com.theeclecticwitch.powertothepeople.location

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.theeclecticwitch.powertothepeople.ui.AppTopBar
import com.theeclecticwitch.powertothepeople.ui.Format
import com.theeclecticwitch.powertothepeople.ui.InfoCard
import com.theeclecticwitch.powertothepeople.ui.LoadingBox
import com.theeclecticwitch.powertothepeople.ui.ReadingColumn
import com.theeclecticwitch.powertothepeople.ui.SourceLine
import kotlinx.coroutines.launch

@Composable
fun LocationScreen(onBack: (() -> Unit)?, onDone: () -> Unit) {
    val saved by LocationStore.location.collectAsState()
    var address by remember { mutableStateOf(saved?.enteredAddress ?: "") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current

    fun find() {
        if (address.isBlank() || busy) return
        keyboard?.hide()
        busy = true
        error = null
        scope.launch {
            try {
                LocationStore.save(CensusGeocoder.lookup(address))
            } catch (e: AddressNotFound) {
                error = "The Census Bureau couldn't find that address. Include the street, city, state and ZIP code - " +
                    "for example: 200 E Gay St, Columbus, OH 43215"
            } catch (e: Exception) {
                error = "Couldn't reach the Census Bureau. Check your connection and try again."
            }
            busy = false
        }
    }

    Scaffold(topBar = { AppTopBar("Your Location", onBack) }) { padding ->
        ReadingColumn(Modifier.padding(padding)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Your home address tells us which districts you live in - and so which officials represent you.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Street address, city, state, ZIP") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { find() }),
                )
                Button(onClick = { find() }, enabled = address.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (saved == null) "Find my districts" else "Update")
                }
                if (busy) LoadingBox("Asking the Census Bureau…")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }

                saved?.let { loc ->
                    DistrictsCard(loc)
                    Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("See who represents me") }
                }

                InfoCard(title = "Your privacy") {
                    Text(
                        "Your address is kept only on this device. It is sent once, to the U.S. Census Bureau's " +
                            "address lookup, to find your districts - nowhere else, and never to us.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (saved != null) {
                        OutlinedButton(onClick = { LocationStore.clear(); address = "" }) { Text("Remove my address") }
                    }
                }
            }
        }
    }
}

@Composable
fun DistrictsCard(loc: UserLocation) {
    InfoCard(title = "Your districts") {
        Text(loc.matchedAddress, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        DistrictRow("State", loc.stateName)
        loc.county?.let { DistrictRow("County", it) }
        loc.place?.let { DistrictRow("City or town", it) }
        loc.schoolDistrict?.let { DistrictRow("School district", it) }
        DistrictRow("U.S. House", "${loc.districtLabel} (${Format.ordinal(loc.congress)} Congress)")
        if (loc.nextCongress != null && loc.nextCongressionalDistrict != null) {
            val next = if (loc.nextCongressionalDistrict == 0) "the at-large district" else "District ${loc.nextCongressionalDistrict}"
            Text(
                "New district maps: from the ${Format.ordinal(loc.nextCongress)} Congress (January ${1789 + 2 * (loc.nextCongress - 1)} onward), this address is in $next.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        loc.stateSenateDistrict?.let { DistrictRow("State Senate", it) }
        loc.stateHouseDistrict?.let { DistrictRow("State House", it) }
        SourceLine("U.S. Census Bureau Geocoder", CensusGeocoder.SOURCE_URL, "looked up ${Format.date(loc.lookedUpOn)}")
    }
}

@Composable
private fun DistrictRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.4f))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.6f))
    }
}
