package com.theeclecticwitch.powertothepeople.support

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.theeclecticwitch.powertothepeople.ui.InfoCard

/**
 * Tip the Creator, in Settings and About only (Rod: no pop-ups or reminders, so the app stays calm and neutral).
 * The person types a whole-dollar amount, $1 to $100, paid through the phone's or the Mac's store; Windows opens
 * Ko-fi instead, where any amount can be given. A tip unlocks nothing. Hidden where there's nowhere to pay, and on
 * a store until it has priced the tips: a button for a product the store doesn't have would do nothing (and Apple
 * rejects apps with buttons that don't work).
 */
@Composable
fun TipCard(way: TipWay = Store.way, storeName: String = Store.name, startTyped: String = "") {
    if (way == TipWay.NONE) return
    val uri = LocalUriHandler.current
    val prices by TipJar.prices.collectAsState()
    val message by TipJar.message.collectAsState()
    val thanked by TipJar.thanked.collectAsState()
    var typed by remember { mutableStateOf(startTyped) }
    var noBrowser by remember { mutableStateOf(false) }
    val dollars = TipJar.dollars(typed)
    LaunchedEffect(Unit) {
        TipJar.setMessage(null)
        if (way == TipWay.STORE && prices.isEmpty()) Store.connect()
    }
    if (way == TipWay.STORE && prices.isEmpty()) return
    fun send() { dollars?.let(Store::tip) }
    InfoCard(title = "Tip the Creator") {
        Text(
            "Power to the People is free, with no ads. If you find it useful, a tip helps pay for its data, its servers " +
                "and new features. It's entirely optional and unlocks nothing: the app stays the same for everyone.",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (way == TipWay.STORE) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = typed,
                    onValueChange = { v -> typed = v.filter { it.isDigit() }.take(3) },
                    modifier = Modifier.weight(1f).semantics { contentDescription = "Tip amount in dollars" },
                    placeholder = { Text("Amount") },
                    prefix = { Text("$") },
                    singleLine = true,
                    isError = typed.isNotEmpty() && dollars == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { send() }),
                )
                Button(onClick = ::send, enabled = dollars != null) { Text(dollars?.let { "Tip $$it" } ?: "Tip", maxLines = 1) }
            }
            val wrong = typed.isNotEmpty() && dollars == null
            Text(
                when {
                    wrong -> "A whole number of dollars, from ${TipJar.LOWEST} to ${TipJar.HIGHEST}."
                    // The store's own price, in the person's currency where that isn't dollars.
                    dollars != null && prices[TipJar.productId(dollars)] != null -> "You'll pay ${prices.getValue(TipJar.productId(dollars))}."
                    else -> "Any whole amount from ${TipJar.LOWEST} to ${TipJar.HIGHEST} dollars."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (wrong) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Button(onClick = { noBrowser = runCatching { uri.openUri(TipJar.KO_FI) }.isFailure }) { Text("Open Ko-fi") }
            Text(
                if (noBrowser) "No browser opened. Visit ${TipJar.KO_FI} from any browser."
                else "Ko-fi opens in your browser, where you can give any amount. It's paid through Ko-fi, not the Microsoft Store.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (thanked) {
            Text(
                "Thank you for your tip!",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
        Text(
            if (way == TipWay.STORE) "Paid through $storeName. Tips aren't tax-deductible." else "Tips aren't tax-deductible.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
