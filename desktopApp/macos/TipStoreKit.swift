// Tip the Creator for the Mac App Store copy of Power to the People: StoreKit 2, which only Swift can reach, built into a small library
// (libp2pstorekit.dylib, by the desktopApp task buildMacStoreKit, on a Mac only) that the app calls through JNA -
// see shared Store.desktop.kt. Each call waits for StoreKit and answers plainly; the app calls them off the main thread.
import Foundation
import StoreKit

private func wait<T>(_ work: @escaping @Sendable () async -> T) -> T {
    let done = DispatchSemaphore(value: 0)
    let box = Box<T>()
    Task.detached {
        box.value = await work()
        done.signal()
    }
    done.wait()
    return box.value!
}

private final class Box<T>: @unchecked Sendable { var value: T? }

private func copy(_ s: String) -> UnsafeMutablePointer<CChar>? { strdup(s) }

/// The prices of the given products (ids separated by new lines), as "id<TAB>price" lines, in the reader's currency.
@_cdecl("p2p_tip_prices")
public func p2p_tip_prices(_ ids: UnsafePointer<CChar>) -> UnsafeMutablePointer<CChar>? {
    let wanted = String(cString: ids).split(separator: "\n").map(String.init)
    let found: [Product] = wait { (try? await Product.products(for: wanted)) ?? [] }
    return copy(found.map { "\($0.id)\t\($0.displayPrice)" }.joined(separator: "\n"))
}

/// Buys one tip and finishes it at once, so the same amount can be given again.
/// 0 paid, 1 cancelled, 2 waiting for approval, 3 not confirmed by the App Store, 4 failed, 5 no such product.
@_cdecl("p2p_tip_buy")
public func p2p_tip_buy(_ id: UnsafePointer<CChar>) -> Int32 {
    let wanted = String(cString: id)
    return wait {
        guard let product = try? await Product.products(for: [wanted]).first else { return 5 }
        do {
            switch try await product.purchase() {
            case .success(.verified(let transaction)):
                await transaction.finish()
                return 0
            case .success(.unverified):
                return 3
            case .pending:
                return 2
            case .userCancelled:
                return 1
            @unknown default:
                return 4
            }
        } catch {
            return 4
        }
    }
}

/// Finishes tips paid while the app was not waiting for them (Ask to Buy approved later, or the app closed
/// mid-purchase). Answers how many, so the app can say thank you.
@_cdecl("p2p_tip_finish_waiting")
public func p2p_tip_finish_waiting() -> Int32 {
    wait {
        var count: Int32 = 0
        for await result in Transaction.unfinished {
            if case .verified(let transaction) = result, transaction.productID.hasPrefix("p2p_tip_") {
                await transaction.finish()
                count += 1
            }
        }
        return count
    }
}

/// Lets go of a string this library handed back.
@_cdecl("p2p_tip_free")
public func p2p_tip_free(_ s: UnsafeMutablePointer<CChar>?) { free(s) }
