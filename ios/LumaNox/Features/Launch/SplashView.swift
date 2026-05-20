import SwiftUI

struct SplashView: View {
    @EnvironmentObject private var router: AppRouter
    @State private var progress: Double = 0

    private let securityStore = SecuritySettingsStore.shared

    var body: some View {
        ZStack {
            LNGradientBackground(top: LNColor.splashLeft, bottom: LNColor.splashRight)
            VStack(spacing: 16) {
                Image(systemName: "lock.shield.fill")
                    .font(.system(size: 56))
                    .foregroundStyle(LNColor.brandBlue)
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
                // 首次启动必须完成 PIN 设置向导（锁屏页 setup 流程），不可跳过进入主页。
                #if DEBUG
                router.finishSplash(goToLock: false)
                #else
                router.finishSplash(goToLock: true)
                #endif
            }
        }
        .accessibilityIdentifier("splash_view")
    }
}
