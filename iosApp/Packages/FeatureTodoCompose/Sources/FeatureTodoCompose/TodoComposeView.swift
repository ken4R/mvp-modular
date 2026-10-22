import SwiftUI
import UIKit
import Shared

/// L'écran Tâches écrit en Compose Multiplatform, affiché dans SwiftUI.
/// Rien n'est natif à l'intérieur : Compose dessine tout lui-même. Pas de DesignSystemUI ici,
/// le thème vient des mêmes tokens core:designsystem, appliqués côté Kotlin.
public struct TodoComposeView: View {
    private let onClose: (() -> Void)?

    /// `onClose` : affiche un bouton « Fermer » dans l'écran Compose.
    public init(onClose: (() -> Void)? = nil) {
        self.onClose = onClose
    }

    public var body: some View {
        ComposeController(onClose: onClose)
            // Compose gère lui-même le clavier : sans ça, SwiftUI décalerait l'écran une seconde fois.
            .ignoresSafeArea(.keyboard)
    }
}

private struct ComposeController: UIViewControllerRepresentable {
    let onClose: (() -> Void)?

    func makeUIViewController(context: Context) -> UIViewController {
        ComposeScreens.shared.todo(onClose: onClose)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
