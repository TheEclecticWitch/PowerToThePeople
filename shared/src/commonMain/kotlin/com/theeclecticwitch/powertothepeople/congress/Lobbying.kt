package com.theeclecticwitch.powertothepeople.congress

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theeclecticwitch.powertothepeople.data.Http
import com.theeclecticwitch.powertothepeople.ui.Format
import com.theeclecticwitch.powertothepeople.ui.InfoCard
import com.theeclecticwitch.powertothepeople.ui.LoadingBox
import com.theeclecticwitch.powertothepeople.ui.SourceLine
import com.theeclecticwitch.powertothepeople.ui.openSafely
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/** One quarterly lobbying report that names a bill, as filed under the Lobbying Disclosure Act. */
@Serializable
data class LobbyingReport(
    val id: String,
    val client: String? = null,
    val registrant: String? = null,
    val year: Int? = null,
    val period: String? = null,
    val posted: String? = null,
    val amount: String? = null,
    val issues: List<String> = emptyList(),
    val excerpt: String? = null,
    val url: String? = null,
)

@Serializable
data class LobbyingResults(val bill: String, val label: String = "", val reports: List<LobbyingReport> = emptyList())

object Lobbying {
    const val SOURCE_URL = "https://lda.gov/"

    suspend fun forBill(bill: String): LobbyingResults =
        Http.json.decodeFromString(Http.getText("${AppTally.BASE}/lobbying?bill=$bill"))
}

/**
 * On a bill's page: lobbying reports from this Congress that name the bill, looked up when asked. Shown as filed,
 * newest first, every report the same way; the app draws no conclusion from who lobbied.
 */
@Composable
fun LobbyingCard(bill: String) {
    var results by remember(bill) { mutableStateOf<LobbyingResults?>(null) }
    var busy by remember(bill) { mutableStateOf(false) }
    var failed by remember(bill) { mutableStateOf(false) }
    var all by remember(bill) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    InfoCard(title = "Lobbying on this bill") {
        Text(
            "Groups that pay to lobby Congress must report each quarter which bills they worked on and how much they " +
                "spent, under the Lobbying Disclosure Act. Lobbying is legal; the reports make it public.",
            style = MaterialTheme.typography.bodyMedium,
        )
        val r = results
        when {
            r == null && busy -> LoadingBox("Searching lobbying reports…")
            r == null -> {
                if (failed) Text("The lobbying database didn't answer. Try again later.", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = {
                    busy = true
                    failed = false
                    scope.launch {
                        results = try { Lobbying.forBill(bill) } catch (e: Exception) { failed = true; null }
                        busy = false
                    }
                }) { Text("Look up lobbying reports") }
            }
            r.reports.isEmpty() -> Text("No lobbying reports filed this Congress name ${r.label}.", style = MaterialTheme.typography.bodyLarge)
            else -> {
                Text(
                    "${r.reports.size} report${if (r.reports.size == 1) "" else "s"} filed this Congress name ${r.label}, newest first.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                (if (all) r.reports else r.reports.take(5)).forEach { rep ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(
                        Modifier.fillMaxWidth().clickable(enabled = rep.url != null) { rep.url?.let { openSafely(uri, it) } }.padding(vertical = 4.dp),
                    ) {
                        Text(listOfNotNull(rep.period, rep.year?.toString()).joinToString(" "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("For: ${rep.client ?: "not given"}", style = MaterialTheme.typography.titleSmall)
                        if (rep.registrant != null && rep.registrant != rep.client) {
                            Text("Lobbying firm: ${rep.registrant}", style = MaterialTheme.typography.bodyMedium)
                        }
                        rep.amount?.toDoubleOrNull()?.let {
                            Text("Reported for the quarter: \$${Format.commas(it.toLong())}", style = MaterialTheme.typography.bodyMedium)
                        }
                        rep.excerpt?.let { Text("“…$it…”", style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, maxLines = 4, overflow = TextOverflow.Ellipsis) }
                        Text(
                            "Read the report",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline,
                        )
                    }
                }
                if (r.reports.size > 5) {
                    Text(
                        if (all) "Show fewer" else "Show all ${r.reports.size}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.clickable { all = !all }.padding(vertical = 4.dp),
                    )
                }
                Text(
                    "An amount covers all of that filer's lobbying in the quarter, on every issue it lists, not only this bill. " +
                        "Reports name bills by number as the filer wrote them, so a few may be missed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SourceLine("Lobbying Disclosure Act reports, lda.gov", Lobbying.SOURCE_URL)
    }
}
