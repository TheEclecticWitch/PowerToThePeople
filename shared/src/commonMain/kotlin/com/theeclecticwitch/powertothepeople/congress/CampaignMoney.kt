package com.theeclecticwitch.powertothepeople.congress

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.theeclecticwitch.powertothepeople.ui.Format
import com.theeclecticwitch.powertothepeople.ui.InfoCard
import com.theeclecticwitch.powertothepeople.ui.SourceLine

/**
 * What a member's campaign committee raised and spent for their current race, as filed with the Federal
 * Election Commission. Shown as reported, line by line; nothing is added up or ranked here.
 */
@Composable
fun CampaignMoneyCard(bioguide: String) {
    var money by remember(bioguide) { mutableStateOf<CampaignMoney?>(null) }
    LaunchedEffect(bioguide) {
        money = try { CongressData.campaignMoney(bioguide) } catch (e: Exception) { null }
    }
    val m = money ?: return
    InfoCard(title = "Campaign money") {
        Text(
            listOfNotNull(
                m.electionYear?.let { "For the $it election" },
                if (m.from != null && m.through != null) "${Format.date(m.from)} to ${Format.date(m.through)}" else null,
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MoneyLine("Raised", m.receipts, bold = true)
        MoneyLine("From individuals", m.individuals, indent = true)
        MoneyLine("in gifts over \$200 (listed by name)", m.individualsItemized, indent = true, deeper = true)
        MoneyLine("in gifts of \$200 or less", m.individualsUnitemized, indent = true, deeper = true)
        MoneyLine("From PACs and other committees", m.pacs, indent = true)
        MoneyLine("From party committees", m.parties, indent = true)
        MoneyLine("From the candidate", m.candidate, indent = true)
        MoneyLine("Loans from the candidate", m.candidateLoans, indent = true)
        MoneyLine("Transfers from their other committees", m.transfers, indent = true)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        MoneyLine("Spent", m.disbursements, bold = true)
        MoneyLine("Cash on hand", m.cashOnHand, bold = true)
        MoneyLine("Debts owed", m.debts)
        Text(
            "Totals for their campaign committee only, as filed" + (m.lastReport?.let { " (latest: ${it.lowercase()} report)" } ?: "") +
                ". Federal law requires a donor's name once their gifts top \$200; smaller gifts are reported as one total.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SourceLine("Federal Election Commission", m.source ?: "https://www.fec.gov/data/", "checked weekly")
    }
}

@Composable
private fun MoneyLine(label: String, amount: Long?, bold: Boolean = false, indent: Boolean = false, deeper: Boolean = false) {
    if (amount == null || (amount == 0L && !bold)) return
    Row(Modifier.padding(start = if (deeper) 24.dp else if (indent) 12.dp else 0.dp)) {
        Text(
            label,
            style = if (deeper) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            Format.dollars(amount.toDouble()),
            style = if (deeper) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
