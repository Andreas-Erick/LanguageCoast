import SwiftUI
import Shared

@main
struct iOSApp: App {
    init() {
        MainViewControllerKt.doInitKoin()
    }

    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea()
                // The launch screen's colour stays behind the shared UI, so nothing lighter shows through
                // while the window is set up.
                .background { Color("LaunchBackground").ignoresSafeArea() }
        }
    }
}

/** Shows the shared Compose UI. */
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
