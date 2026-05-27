import SwiftUI

struct SplashView: View {
    @EnvironmentObject private var router: AppRouter
    @State private var progress: Double = 0

    private let securityStore = SecuritySettingsStore.shared

    var body: some View {
        ZStack {
            LNGradientBackground(top: LNColor.splashLeft, bottom: LNColor.splashRight)
            VStack(spacing: 16) {
                Image("AppLogo")
                    .resizable()
                    .scaledToFill()
                    .frame(width: 88, height: 88)
                    .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
                    .overlay(
                        RoundedRectangle(cornerRadius: 24, style: .continuous)
                            .stroke(LNColor.brandBlue.opacity(0.36), lineWidth: 1)
                    )
                    .shadow(color: LNColor.brandBlue.opacity(0.32), radius: 24, x: 0, y: 12)
                    .accessibilityHidden(true)
                Text(L10n.appName)
                    .font(LNTypography.displayLarge())
                    .foregroundStyle(LNColor.title)
                Text(L10n.splashTagline)
                    .font(LNTypography.bodyMedium())
                    .foregroundStyle(LNColor.subtitle)
                ProgressView(value: progress)
                    .tint(LNColor.brandBlue)
                    .padding(.horizontal, 48)
                    .padding(.top, 24)
            }
        }
        .onAppear {
            securityStore.reload()
            withAnimation(.linear(duration: 1.2)) { progress = 1 }
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) {
                // 真机必须完成 PIN 设置向导；仅 DEBUG 模拟器允许直达主页以便快速调试。
                router.finishSplash(goToLock: !AppDebugPolicy.skipsPinGate)
            }
        }
        .accessibilityIdentifier("splash_view")
    }
}
