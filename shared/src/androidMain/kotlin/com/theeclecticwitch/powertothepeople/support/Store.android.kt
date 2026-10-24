package com.theeclecticwitch.powertothepeople.support

import android.app.Activity
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.theeclecticwitch.powertothepeople.data.androidContext
import java.lang.ref.WeakReference

/** The screen a purchase sheet is shown over. Play Billing needs an Activity, not just a Context. */
private var activity: WeakReference<Activity>? = null

/** Called by the host's Activity on start-up. */
fun attachStoreActivity(host: Activity) {
    activity = WeakReference(host)
}

private const val UNAVAILABLE = "Google Play isn't available right now. Check your connection and try again."

/**
 * Google Play Billing. A tip is consumed as soon as it's paid, so the same amount can be given again; consuming also
 * acknowledges it, which Play requires within three days. Any paid tip not yet consumed (the app closed mid-purchase,
 * or a pending payment that cleared) is picked up on the next start.
 */
actual object Store {
    actual val way: TipWay = TipWay.STORE
    actual val name: String = "Google Play"

    private var client: BillingClient? = null
    private var products: Map<String, ProductDetails> = emptyMap()

    private fun client(): BillingClient = client ?: BillingClient.newBuilder(androidContext())
        .setListener { result, purchases ->
            when (result.responseCode) {
                BillingClient.BillingResponseCode.OK -> purchases?.let(::take)
                BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refresh()
                BillingClient.BillingResponseCode.USER_CANCELED -> Unit
                else -> TipJar.setMessage("The tip didn't go through. Please try again.")
            }
        }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()
        .also { client = it }

    actual fun connect() {
        val billing = client()
        if (billing.isReady) {
            refresh()
            if (products.isEmpty()) loadProducts()
            return
        }
        billing.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode != BillingClient.BillingResponseCode.OK) return
                refresh()
                loadProducts()
            }

            override fun onBillingServiceDisconnected() = Unit
        })
    }

    actual fun tip(dollars: Int) {
        val details = products[TipJar.productId(dollars)]
        val host = activity?.get()
        if (details == null || host == null) {
            TipJar.setMessage(UNAVAILABLE)
            connect()
            return
        }
        TipJar.setMessage(null)
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).build()),
            )
            .build()
        client().launchBillingFlow(host, params)
    }

    /** All hundred amounts, asked for twenty at a time. */
    private fun loadProducts() {
        TipJar.productIds.chunked(20).forEach { ids ->
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(
                    ids.map {
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(it)
                            .setProductType(BillingClient.ProductType.INAPP)
                            .build()
                    },
                )
                .build()
            client().queryProductDetailsAsync(params) { result, details ->
                if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
                synchronized(this) { products = products + details.productDetailsList.associateBy { it.productId } }
                details.productDetailsList.forEach { TipJar.setPrice(it.productId, it.oneTimePurchaseOfferDetails?.formattedPrice) }
            }
        }
    }

    /** Picks up any paid tip that wasn't consumed. */
    private fun refresh() {
        val billing = client()
        if (!billing.isReady) return
        billing.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) take(purchases)
        }
    }

    /** Consumes each paid tip (or old donation) and says thank you. A pending one (paid in cash at a shop, say) waits. */
    private fun take(purchases: List<Purchase>) {
        val ids = (TipJar.productIds + TipJar.oldProductIds).toSet()
        val ours = purchases.filter { p -> p.products.any { it in ids } }
        ours.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }.forEach { purchase ->
            val params = ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
            client().consumeAsync(params) { result, _ ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) TipJar.thank()
            }
        }
        if (ours.any { it.purchaseState == Purchase.PurchaseState.PENDING }) {
            TipJar.setMessage("Your payment is pending. It will go through on its own; thank you!")
        }
    }
}
