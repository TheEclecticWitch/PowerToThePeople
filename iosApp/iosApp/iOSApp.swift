import SwiftUI
import UIKit
import PowerToThePeopleKit

/// Hands the whole screen to the shared Compose UI. The iOS counterpart of `MainActivity`,
/// and it should stay this small: anything that grows here ought to be shared instead.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

@main
struct PowerToThePeopleApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeView().ignoresSafeArea(.all)
        }
    }
}
