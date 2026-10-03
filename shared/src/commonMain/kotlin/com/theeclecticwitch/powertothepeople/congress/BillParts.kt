package com.theeclecticwitch.powertothepeople.congress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theeclecticwitch.powertothepeople.ui.Format
import com.theeclecticwitch.powertothepeople.ui.InfoCard
import com.theeclecticwitch.powertothepeople.ui.SourceLine

/** How far the bill has gone, as a simple line of steps; the furthest one reached is in bold. */
@Composable
fun ProgressCard(p: BillProgress) {
    val done = MaterialTheme.colorScheme.primary
    val notYet = MaterialTheme.colorScheme.outlineVariant
    InfoCard(title = "Where it stands") {
        p.steps.forEachIndexed { i, step ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(18.dp)) {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(if (step.done) done else notYet))
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    step.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (i == p.current) FontWeight.Bold else FontWeight.Normal,
                    color = if (step.done) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (i < p.steps.lastIndex) {
                Box(Modifier.padding(start = 8.dp).width(2.dp).height(12.dp).background(if (p.steps[i + 1].done) done else notYet))
            }
        }
        if (p.vetoed) {
            Text("The President vetoed it. Congress can still pass it with a two-thirds vote in each chamber.", style = MaterialTheme.typography.bodyMedium)
        } else if (p.current <= 1 && p.steps.size > 3) {
            Text(
                "Most bills never leave committee; only a small share ever get a vote.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The Congressional Research Service's plain-English summary. CRS is part of the Library of Congress and
 * nonpartisan; its summaries describe what a bill would do, without taking a side.
 */
@Composable
fun SummaryCard(b: Bill) {
    val summary = b.summary
    var full by remember(b.bill) { mutableStateOf(false) }
    InfoCard(title = "What this bill does") {
        if (summary == null) {
            Text(
                "The Library of Congress hasn't published a summary of this bill yet; they're often written a few weeks " +
                    "after a bill is introduced. The full text is on Congress.gov, linked below.",
                style = MaterialTheme.typography.bodyMedium,
            )
            return@InfoCard
        }
        listOfNotNull(summary.stage, summary.date?.let { Format.date(it) }).joinToString(" · ").takeIf { it.isNotBlank() }?.let {
            Text("Summary as of: $it", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
        }
        val long = summary.text.length > 700
        Text(
            summary.text,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = if (long && !full) 12 else Int.MAX_VALUE,
            overflow = TextOverflow.Ellipsis,
        )
        if (long && !full) OutlinedButton(onClick = { full = true }) { Text("Read the whole summary") }
        SourceLine("Congressional Research Service, Library of Congress (nonpartisan)", b.url)
    }
}
