import SwiftUI
import UIKit

struct SwipeBackNavigationConfigurator: UIViewControllerRepresentable {
    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    func makeUIViewController(context: Context) -> UIViewController {
        ConfiguratorViewController(coordinator: context.coordinator)
    }

    func updateUIViewController(_ viewController: UIViewController, context: Context) {
        (viewController as? ConfiguratorViewController)?.configureSwipeBack()
    }

    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        weak var navigationController: UINavigationController?

        func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
            guard let navigationController else { return false }
            return navigationController.viewControllers.count > 1
                && navigationController.transitionCoordinator == nil
        }
    }

    final class ConfiguratorViewController: UIViewController {
        private weak var coordinator: Coordinator?

        init(coordinator: Coordinator) {
            self.coordinator = coordinator
            super.init(nibName: nil, bundle: nil)
        }

        @available(*, unavailable)
        required init?(coder: NSCoder) {
            nil
        }

        override func didMove(toParent parent: UIViewController?) {
            super.didMove(toParent: parent)
            configureSwipeBack()
        }

        override func viewDidAppear(_ animated: Bool) {
            super.viewDidAppear(animated)
            configureSwipeBack()
        }

        override func viewDidLayoutSubviews() {
            super.viewDidLayoutSubviews()
            configureSwipeBack()
        }

        func configureSwipeBack() {
            guard let navigationController = nearestNavigationController() else { return }
            coordinator?.navigationController = navigationController
            navigationController.interactivePopGestureRecognizer?.isEnabled = true
            navigationController.interactivePopGestureRecognizer?.delegate = coordinator
        }

        private func nearestNavigationController() -> UINavigationController? {
            if let navigationController {
                return navigationController
            }
            var nextParent = parent
            while let candidate = nextParent {
                if let navigationController = candidate as? UINavigationController {
                    return navigationController
                }
                if let navigationController = candidate.navigationController {
                    return navigationController
                }
                nextParent = candidate.parent
            }
            return nil
        }
    }
}

extension View {
    func swipeBackEnabled() -> some View {
        background(SwipeBackNavigationConfigurator().frame(width: 0, height: 0))
    }

    func edgeSwipeBack(action: @escaping () -> Void) -> some View {
        modifier(EdgeSwipeBackModifier(action: action))
    }
}

private struct EdgeSwipeBackModifier: ViewModifier {
    let action: () -> Void

    func body(content: Content) -> some View {
        content.overlay(alignment: .leading) {
            Color.clear
                .frame(width: 32)
                .contentShape(Rectangle())
                .ignoresSafeArea(.container, edges: .vertical)
                .highPriorityGesture(
                    DragGesture(minimumDistance: 18, coordinateSpace: .global)
                        .onEnded { value in
                            guard value.translation.width > 72,
                                  value.translation.width > abs(value.translation.height) * 1.5 else {
                                return
                            }
                            action()
                        }
                )
        }
    }
}
