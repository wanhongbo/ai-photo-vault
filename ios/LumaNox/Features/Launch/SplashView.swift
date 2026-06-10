import SwiftUI

struct SplashView: View {
    @EnvironmentObject private var router: AppRouter
    @State private var progress: Double = 0

    private let securityStore = SecuritySettingsStore.shared
    private let contentWidth: CGFloat = 446

    var body: some View {
        GeometryReader { proxy in
            ZStack {
                LNGradientBackground(top: LNColor.splashLeft, bottom: LNColor.splashRight)

                VStack(spacing: 0) {
                    Spacer(minLength: 0)

                    VStack(spacing: 0) {
                        Image("AppLogo")
                            .resizable()
                            .scaledToFill()
                            .frame(width: 104, height: 104)
                            .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
                            .overlay(
                                RoundedRectangle(cornerRadius: 28, style: .continuous)
                                    .stroke(LNColor.brandBlue.opacity(0.34), lineWidth: 1)
                            )
                            .shadow(color: LNColor.brandBlue.opacity(0.30), radius: 34, x: 0, y: 0)
                            .accessibilityHidden(true)

                        Text(L10n.appName)
                            .font(.system(size: 42, weight: .heavy))
                            .foregroundStyle(LNColor.title)
                            .padding(.top, 30)

                        Text(L10n.splashTagline)
                            .font(.system(size: 18, weight: .medium))
                            .foregroundStyle(LNColor.subtitle)
                            .padding(.top, 12)

                        ProgressView(value: progress)
                            .tint(LNColor.brandBlue)
                            .frame(maxWidth: .infinity)
                            .padding(.top, 58)
                    }
                    .frame(width: min(contentWidth, proxy.size.width - 48))
                    .offset(y: -proxy.safeAreaInsets.bottom * 0.3)

                    Spacer(minLength: 0)
                }
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
