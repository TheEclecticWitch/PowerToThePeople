package com.theeclecticwitch.powertothepeople.support

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.theeclecticwitch.powertothepeople.ui.InfoCard

/**
 * Optional donations, in Settings and About only (Rod, 2026-10-04: no pop-ups or reminders, so
 * the app stays calm and neutral). Hidden where there's no store to pay through.
 */
@Composable
fun SupportCard() {
    if (!Store.available) return
    val prices by Donations.prices.collectAsState()
    val message by Donations.message.collectAsState()
    val thanked by Donations.thanked.collectAsState()
    LaunchedEffect(Unit) { if (prices.isEmpty()) Store.connect() }
    InfoCard(title = "Support the app") {
        Text(
            "Power to the People is free, with no ads. If you find it useful, a donation helps pay for its data, " +
                "its servers and new features. It's entirely optional and unlocks nothing: the app stays the same " +
                "for everyone.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tip.entries.forEach { tip ->
                Button(onClick = { Store.donate(tip) }, modifier = Modifier.weight(1f)) {
                    Text(prices[tip.productId] ?: tip.usualPrice, maxLines = 1)
                }
            }
        }
        if (thanked) {
            Text(
                "Thank you for your support!",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
        Text(
            "Paid through ${Store.name}. Donations aren't tax-deductible.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
