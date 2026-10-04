import StoreKit
import PowerToThePeopleKit

/// The App Store side of donations, with StoreKit 2. The shared code asks through StoreBridge
/// and is told the result through Donations; it never sees StoreKit. Each donation is a
/// consumable, finished as soon as it's paid, so it can be given again.
@MainActor
final class DonationStore {
    static let shared = DonationStore()
    private let productIDs = ["tip_small", "tip_medium", "tip_large"]
    private var products: [String: Product] = [:]
    private var updates: Task<Void, Never>?

    /// Hands the shared code its buttons, and listens for donations that finish later -
    /// approved by a parent with Ask to Buy, or interrupted when the app closed.
    func start() {
        StoreBridge.shared.connect = { Task { @MainActor in await DonationStore.shared.loadProducts() } }
        StoreBridge.shared.donate = { id in Task { @MainActor in await DonationStore.shared.donate(id) } }
        updates = Task {
            for await result in Transaction.updates {
                if case .verified(let transaction) = result {
                    await transaction.finish()
                    Donations.shared.thank()
                }
            }
        }
        Task { await loadProducts() }
    }

    private func loadProducts() async {
        guard let found = try? await Product.products(for: productIDs) else { return }
        for product in found {
            products[product.id] = product
            Donations.shared.setPrice(productId: product.id, price: product.displayPrice)
        }
    }

    private func donate(_ id: String) async {
        if products[id] == nil { await loadProducts() }
        guard let product = products[id] else {
            Donations.shared.setMessage(message: "The App Store isn't available right now. Check your connection and try again.")
            return
        }
        Donations.shared.setMessage(message: nil)
        do {
            switch try await product.purchase() {
            case .success(.verified(let transaction)):
                await transaction.finish()
                Donations.shared.thank()
            case .success(.unverified):
                Donations.shared.setMessage(message: "The App Store couldn't confirm the donation. Please check your App Store purchase history before trying again.")
            case .pending:
                Donations.shared.setMessage(message: "Your donation is waiting for approval. It will go through on its own; thank you!")
            case .userCancelled:
                break
            @unknown default:
                break
            }
        } catch {
            Donations.shared.setMessage(message: "The donation didn't go through. Please try again.")
        }
    }
}
