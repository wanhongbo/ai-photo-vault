import SwiftUI

struct PaywallView: View {
    @EnvironmentObject private var router: AppRouter
    @Environment(\.dismiss) private var dismiss
    @StateObject private var viewModel: PaywallViewModel

    let dismissable: Bool

    init(dismissable: Bool, source: String) {
        self.dismissable = dismissable
        _viewModel = StateObject(wrappedValue: PaywallViewModel(source: source))
    }

    var body: some View {
        ZStack {
            LNGradientBackground(top: LNColor.paywallTop, bottom: LNColor.paywallBottom)
            content
            if viewModel.purchasing {
                Color.black.opacity(0.35).ignoresSafeArea()
                ProgressView(L10n.commonLoading).tint(LNColor.brandBlue)
            }
        }
        .onAppear { viewModel.onAppear() }
        .onChange(of: viewModel.shouldDismissAfterSuccess) { ok in
            if ok { closePaywall() }
        }
        .onChange(of: viewModel.purchaseToast) { toast in
            guard let toast, !toast.isEmpty else { return }
            // Toast via alert-free: subtitle area shows message briefly via surfaceError clear
        }
        .accessibilityIdentifier("paywall_view")
    }

    @ViewBuilder
    private var content: some View {
        switch viewModel.offeringsState {
        case .loading:
            ProgressView(L10n.commonLoading).tint(LNColor.brandBlue)
        case .error(let message):
            errorBody(message: message)
        case .ready(let packages, _, _):
            if viewModel.isPremium {
                premiumActiveBody
            } else if packages.isEmpty {
                errorBody(message: L10n.tr("paywall_error_generic"))
            } else {
                catalogBody(packages: packages)
            }
        }
    }

    private var premiumActiveBody: some View {
        VStack(spacing: 16) {
            header(showClose: dismissable)
            Image(systemName: "checkmark.seal.fill")
                .font(.system(size: 48))
                .foregroundStyle(LNColor.success)
            Text(L10n.tr("paywall_hero_subtitle_active"))
                .font(LNTypography.bodyMedium())
                .foregroundStyle(LNColor.subtitle)
                .multilineTextAlignment(.center)
            LNButton(title: L10n.commonBack, variant: .secondary) { closePaywall() }
        }
        .padding(LNSpacing.screenHorizontal)
    }

    private func catalogBody(packages: [PaywallPackageOffer]) -> some View {
        ScrollView {
            VStack(spacing: 16) {
                header(showClose: dismissable)
                hero
                featureList
                ForEach(Array(packages.enumerated()), id: \.element.id) { index, offer in
                    packageCard(offer: offer, selected: viewModel.selectedIndex == index) {
                        viewModel.selectedIndex = index
                    }
                }
                if let err = viewModel.surfaceError {
                    Text(err)
                        .font(LNTypography.bodyMedium())
                        .foregroundStyle(LNColor.error)
                        .multilineTextAlignment(.center)
                }
                if let toast = viewModel.purchaseToast {
                    Text(toast)
                        .font(LNTypography.bodyMedium())
                        .foregroundStyle(LNColor.success)
                        .multilineTextAlignment(.center)
                }
                LNButton(
                    title: L10n.tr("paywall_cta_continue"),
                    variant: .primary,
                    enabled: !viewModel.purchasing,
                    loading: viewModel.purchasing
                ) {
                    Task { await viewModel.purchaseSelected() }
                }
                Button {
                    Task { await viewModel.restore() }
                } label: {
                    Text(L10n.tr("paywall_restore"))
                        .font(LNTypography.titleMedium())
                        .foregroundStyle(LNColor.brandBlue)
                }
                .disabled(viewModel.purchasing)
                Text(L10n.tr("paywall_footer"))
                    .font(LNTypography.labelMedium())
                    .foregroundStyle(LNColor.subtitle)
                    .multilineTextAlignment(.center)
            }
            .padding(LNSpacing.screenHorizontal)
            .padding(.bottom, 32)
        }
    }

    private func errorBody(message: String?) -> some View {
        VStack(spacing: 16) {
            header(showClose: dismissable)
            Text(displayError(message))
                .font(LNTypography.bodyMedium())
                .foregroundStyle(LNColor.error)
                .multilineTextAlignment(.center)
            LNButton(title: L10n.tr("paywall_retry"), variant: .secondary) {
                Task { await viewModel.refresh() }
            }
        }
        .padding(LNSpacing.screenHorizontal)
    }

    private func header(showClose: Bool) -> some View {
        HStack {
            if showClose {
                Spacer()
                Button { closePaywall() } label: {
                    Image(systemName: "xmark")
                        .foregroundStyle(LNColor.title)
                        .frame(width: 44, height: 44)
                        .background(LNColor.sectionBg)
                        .clipShape(Circle())
                }
                .accessibilityLabel(L10n.tr("paywall_close_cd"))
            }
        }
    }

    private var hero: some View {
        VStack(spacing: 8) {
            Image(systemName: "crown.fill")
                .font(.system(size: 48))
                .foregroundStyle(LNColor.paywallGold)
            Text(L10n.tr("paywall_hero_title"))
                .font(LNTypography.displaySmall())
                .foregroundStyle(LNColor.title)
            Text(L10n.tr("paywall_hero_subtitle"))
                .font(LNTypography.bodyMedium())
                .foregroundStyle(LNColor.subtitle)
                .multilineTextAlignment(.center)
        }
    }

    private var featureList: some View {
        VStack(alignment: .leading, spacing: 8) {
            featureRow(L10n.tr("paywall_feat_storage"))
            featureRow(L10n.tr("paywall_feat_watermark"))
            featureRow(L10n.tr("paywall_feat_ai"))
            featureRow(L10n.tr("paywall_feat_backup"))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func featureRow(_ text: String) -> some View {
        HStack(spacing: 8) {
            Image(systemName: "checkmark.circle.fill")
                .foregroundStyle(LNColor.brandBlue)
            Text(text)
                .font(LNTypography.bodyMedium())
                .foregroundStyle(LNColor.subtitle)
        }
    }

    private func packageCard(offer: PaywallPackageOffer, selected: Bool, onTap: @escaping () -> Void) -> some View {
        Button(action: onTap) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 6) {
                        if offer.showBestValueBadge {
                            Text(L10n.tr("paywall_best_value"))
                                .font(LNTypography.labelMedium())
                                .foregroundStyle(.white)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 2)
                                .background(LNColor.brandBlue)
                                .clipShape(Capsule())
                        }
                        if let trial = offer.freeTrialLabel {
                            Text(trial)
                                .font(LNTypography.labelMedium())
                                .foregroundStyle(LNColor.amberWarning)
                        }
                    }
                    Text(offer.title)
                        .font(LNTypography.titleMedium())
                        .foregroundStyle(LNColor.title)
                        .lineLimit(2)
                    Text(periodLabel(for: offer.kind))
                        .font(LNTypography.labelMedium())
                        .foregroundStyle(LNColor.subtitle)
                    Text(offer.pricePrimary)
                        .font(LNTypography.bodyLarge())
                        .foregroundStyle(selected ? LNColor.brandBlue : LNColor.title)
                    if let savings = offer.savingsPercent, savings > 0 {
                        Text(L10n.tr("paywall_savings_fmt", savings))
                            .font(LNTypography.labelMedium())
                            .foregroundStyle(LNColor.success)
                    }
                }
                Spacer()
            }
            .padding(LNSpacing.cardPadding)
            .background(LNColor.sectionBg)
            .clipShape(RoundedRectangle(cornerRadius: LNRadius.paywallCard))
            .overlay(
                RoundedRectangle(cornerRadius: LNRadius.paywallCard)
                    .stroke(selected ? LNColor.brandBlue : LNColor.stroke, lineWidth: selected ? 2 : 1)
            )
        }
        .buttonStyle(.plain)
    }

    private func periodLabel(for kind: PaywallPlanKind) -> String {
        switch kind {
        case .monthly: return L10n.tr("paywall_period_monthly")
        case .annual: return L10n.tr("paywall_period_yearly")
        case .lifetime: return L10n.tr("paywall_period_lifetime")
        case .other: return L10n.tr("paywall_period_other")
        }
    }

    private func displayError(_ message: String?) -> String {
        if message == SubscriptionService.errorCodeRcKeyMissing {
            return L10n.tr("paywall_not_configured")
        }
        return message ?? L10n.tr("paywall_error_generic")
    }

    private func closePaywall() {
        dismiss()
        router.dismissPresented()
    }
}
