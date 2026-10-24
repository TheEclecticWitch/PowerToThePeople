import StoreKit
import PowerToThePeopleKit

/// The App Store side of Tip the Creator, with StoreKit 2. The shared code asks through StoreBridge
/// and is told the result through TipJar; it never sees StoreKit. Each amount (p2p_tip_1 ... p2p_tip_100) is a
/// consumable, finished as soon as it's paid, so it can be given again.
@MainActor
final class TipStore {
    static let shared = TipStore()
    private var products: [String: Product] = [:]
    private var updates: Task<Void, Never>?

    /// Hands the shared code its store, and listens for tips that finish later -
    /// approved by a parent with Ask to Buy, or interrupted when the app closed.
    func start() {
        StoreBridge.shared.connect = { Task { @MainActor in await TipStore.shared.loadProducts() } }
        StoreBridge.shared.tip = { id in Task { @MainActor in await TipStore.shared.tip(id) } }
        updates = Task {
            for await result in Transaction.updates {
                if case .verified(let transaction) = result {
                    await transaction.finish()
                    TipJar.shared.thank()
                }
            }
        }
    }

    private func loadProducts() async {
        guard let found = try? await Product.products(for: TipJar.shared.productIds) else { return }
        for product in found {
            products[product.id] = product
            TipJar.shared.setPrice(productId: product.id, price: product.displayPrice)
        }
    }

    private func tip(_ id: String) async {
        if products[id] == nil { await loadProducts() }
        guard let product = products[id] else {
            TipJar.shared.setMessage(message: "The App Store isn't available right now. Check your connection and try again.")
            return
        }
        TipJar.shared.setMessage(message: nil)
        do {
            switch try await product.purchase() {
            case .success(.verified(let transaction)):
                await transaction.finish()
                TipJar.shared.thank()
            case .success(.unverified):
                TipJar.shared.setMessage(message: "The App Store couldn't confirm the tip. Please check your App Store purchase history before trying again.")
            case .pending:
                TipJar.shared.setMessage(message: "Your tip is waiting for approval. It will go through on its own; thank you!")
            case .userCancelled:
                break
            @unknown default:
                break
            }
        } catch {
            TipJar.shared.setMessage(message: "The tip didn't go through. Please try again.")
        }
    }
}
