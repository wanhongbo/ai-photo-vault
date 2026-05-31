import Foundation
import SwiftUI

struct PaywallView: View {
    @EnvironmentObject private var router: AppRouter
    @Environment(\.dismiss) private var dismiss
    @StateObject private var viewModel: PaywallViewModel

    let dismissable: Bool
    private let horizontalPadding: CGFloat = 22

    init(dismissable: Bool, source: String) {
        self.dismissable = dismissable
        _viewModel = StateObject(wrappedValue: PaywallViewModel(source: source))
    }

    var body: some View {
        ZStack(alignment: .bottom) {
            LNGradientBackground(top: LNColor.paywallTop, bottom: LNColor.paywallBottom)
            content
            stickyPurchaseBar
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
        .edgeSwipeBack {
            if dismissable {
                closePaywall()
            }
        }
        .accessibilityIdentifier("paywall_view")
    }

    @ViewBuilder
    private var content: some View {
        switch viewModel.offeringsState {
        case .loading:
            stateScroll {
                ProgressView(L10n.commonLoading)
                    .tint(paywallPriceSelected)
                    .frame(maxWidth: .infinity)
                    .frame(height: 220)
            }
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
        stateScroll {
            VStack(spacing: 16) {
                Image(systemName: "checkmark.seal.fill")
                    .font(.system(size: 48, weight: .semibold))
                    .foregroundStyle(LNColor.success)
                Text(L10n.tr("paywall_premium_active"))
                    .font(LNTypography.titleMedium())
                    .foregroundStyle(paywallTitle)
                    .multilineTextAlignment(.center)
                Text(L10n.tr("paywall_hero_subtitle_active"))
                    .font(LNTypography.bodyMedium())
                    .foregroundStyle(paywallSubtitle)
                    .multilineTextAlignment(.center)
                LNButton(title: L10n.commonBack, variant: .secondary) { closePaywall() }
            }
            .frame(maxWidth: .infinity)
            .padding(.top, 8)
        }
    }

    private func catalogBody(packages: [PaywallPackageOffer]) -> some View {
        ScrollView {
            VStack(spacing: 16) {
                header(showClose: dismissable)
                hero
                sourceContext
                featureList
                ForEach(Array(packages.enumerated()), id: \.element.id) { index, offer in
                    packageCard(offer: offer, selected: viewModel.selectedIndex == index) {
                        viewModel.selectedIndex = index
                    }
                }
                comparisonTable
            }
            .padding(.horizontal, horizontalPadding)
            .padding(.top, 12)
            .padding(.bottom, 224)
        }
    }

    private func errorBody(message: String?) -> some View {
        stateScroll {
            Text(displayError(message))
                .font(LNTypography.bodyMedium())
                .foregroundStyle(message == SubscriptionService.errorCodeRcKeyMissing ? paywallSubtitle : paywallError)
                .multilineTextAlignment(.center)
                .padding(.top, 8)
            LNButton(title: L10n.tr("paywall_retry"), variant: .secondary) {
                Task { await viewModel.refresh() }
            }
        }
    }

    private func stateScroll<Content: View>(@ViewBuilder body: () -> Content) -> some View {
        ScrollView {
            VStack(spacing: 18) {
                header(showClose: dismissable)
                hero
                sourceContext
                body()
            }
            .padding(.horizontal, horizontalPadding)
            .padding(.top, 12)
            .padding(.bottom, 48)
        }
    }

    private func header(showClose: Bool) -> some View {
        HStack {
            if showClose {
                Spacer()
                Button { closePaywall() } label: {
                    Image(systemName: "xmark")
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundStyle(paywallSubtitle)
                        .frame(width: 44, height: 44)
                        .background(paywallCloseFill)
                        .clipShape(Circle())
                        .overlay(Circle().stroke(paywallCloseStroke, lineWidth: 1))
                }
                .buttonStyle(.lnPressable(scale: 0.92, pressedOpacity: 0.76))
                .accessibilityLabel(L10n.tr("paywall_close_cd"))
            } else {
                Spacer()
                    .frame(height: 44)
            }
        }
        .frame(height: 44)
    }

    private var hero: some View {
        VStack(spacing: 10) {
            Image(systemName: "crown.fill")
                .font(.system(size: 30, weight: .semibold))
                .foregroundStyle(paywallCrownGlyph)
                .frame(width: 64, height: 64)
                .overlay(Circle().stroke(LNColor.paywallGold, lineWidth: 2))
            Text(L10n.tr("paywall_hero_title"))
                .font(.system(size: 26, weight: .bold))
                .foregroundStyle(paywallTitle)
            Text(L10n.tr("paywall_hero_subtitle"))
                .font(LNTypography.bodyMedium())
                .foregroundStyle(paywallSubtitle)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
    }

    private var sourceContext: some View {
        HStack(spacing: 10) {
            Image(systemName: "lock.shield.fill")
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(paywallPriceSelected)
                .frame(width: 32, height: 32)
                .background(Color(hex: 0x1A2A40))
                .clipShape(Circle())
            Text(sourceText)
                .font(.system(size: 13))
                .foregroundStyle(paywallSubtitle)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
        .padding(12)
        .background(paywallCardBg)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(paywallCardStroke, lineWidth: 1)
        )
    }

    private var featureList: some View {
        VStack(alignment: .leading, spacing: 12) {
            featureRow(L10n.tr("paywall_feat_storage"), tint: paywallFeatureCheck)
            featureRow(L10n.tr("paywall_feat_watermark"), tint: paywallFeatureCheck)
            featureRow(L10n.tr("paywall_feat_ai"), tint: LNColor.paywallGold)
            featureRow(L10n.tr("paywall_feat_backup"), tint: paywallPriceSelected)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func featureRow(_ text: String, tint: Color) -> some View {
        HStack(spacing: 10) {
            Image(systemName: "checkmark")
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(tint)
                .frame(width: 16)
            Text(text)
                .font(LNTypography.bodyMedium())
                .foregroundStyle(paywallTitle)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private func packageCard(offer: PaywallPackageOffer, selected: Bool, onTap: @escaping () -> Void) -> some View {
        Button(action: onTap) {
            HStack(alignment: .center, spacing: 12) {
                VStack(alignment: .leading, spacing: 5) {
                    Text(offer.title.compactStoreText())
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundStyle(paywallTitle)
                        .lineLimit(2)
                    HStack(spacing: 6) {
                        if offer.showBestValueBadge {
                            Text(bestValueText(for: offer))
                                .font(LNTypography.labelMedium())
                                .fontWeight(.bold)
                                .foregroundStyle(.white)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 4)
                                .background(paywallBadgeBg)
                                .clipShape(Capsule())
                        }
                        if let trial = offer.freeTrialLabel {
                            Text(trial)
                                .font(LNTypography.labelMedium())
                                .foregroundStyle(LNColor.paywallGold)
                        }
                    }
                    if !offer.description.compactStoreText().isEmpty {
                        Text(offer.description.compactStoreText())
                            .font(LNTypography.labelMedium())
                            .foregroundStyle(paywallTierMeta)
                            .lineLimit(2)
                    }
                }
                Spacer(minLength: 8)
                VStack(alignment: .trailing, spacing: 2) {
                    Text(offer.pricePrimary.trimmedStoreText())
                        .font(.system(size: 20, weight: .bold))
                        .foregroundStyle(selected ? paywallPriceSelected : paywallPriceIdle)
                        .lineLimit(2)
                    Text(periodLabel(for: offer.kind))
                        .font(LNTypography.labelMedium())
                        .foregroundStyle(paywallTierMeta)
                        .lineLimit(1)
                }
                .frame(minWidth: 82, maxWidth: 112, alignment: .trailing)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .background(paywallCardBg)
            .clipShape(RoundedRectangle(cornerRadius: LNRadius.paywallCard))
            .overlay(
                RoundedRectangle(cornerRadius: LNRadius.paywallCard)
                    .stroke(selected ? paywallCardStrokeSelected : paywallCardStroke, lineWidth: selected ? 2 : 1)
            )
        }
        .buttonStyle(.lnPressable())
    }

    private var comparisonTable: some View {
        VStack(spacing: 8) {
            HStack {
                Spacer()
                Text(L10n.tr("paywall_plan_free"))
                    .font(LNTypography.labelMedium())
                    .foregroundStyle(paywallTierMeta)
                    .frame(width: 56)
                Text(L10n.tr("paywall_hero_title"))
                    .font(LNTypography.labelMedium())
                    .fontWeight(.bold)
                    .foregroundStyle(paywallPriceSelected)
                    .frame(width: 76)
            }
            comparisonRow(label: L10n.tr("paywall_compare_storage"), free: "\(FreeQuota.maxVaultItems)", pro: "∞")
            comparisonRow(label: L10n.tr("paywall_compare_ai"), free: L10n.tr("paywall_compare_ai_free"), pro: "∞")
            comparisonRow(label: L10n.tr("paywall_compare_backup"), free: L10n.tr("paywall_compare_backup_free"), pro: "∞")
            comparisonRow(label: L10n.tr("paywall_compare_watermark"), free: "—", pro: "✓")
        }
        .padding(12)
        .background(paywallCardBg)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    private func comparisonRow(label: String, free: String, pro: String) -> some View {
        HStack(spacing: 0) {
            Text(label)
                .font(.system(size: 13))
                .foregroundStyle(paywallTitle)
                .frame(maxWidth: .infinity, alignment: .leading)
                .lineLimit(1)
                .minimumScaleFactor(0.82)
            Text(free)
                .font(.system(size: 13))
                .foregroundStyle(paywallTierMeta)
                .frame(width: 56)
            Text(pro)
                .font(.system(size: 13, weight: .bold))
                .foregroundStyle(paywallPriceSelected)
                .frame(width: 76)
        }
        .frame(minHeight: 24)
    }

    @ViewBuilder
    private var stickyPurchaseBar: some View {
        if case .ready(let packages, _, let isPremium) = viewModel.offeringsState,
           !isPremium,
           !packages.isEmpty {
            VStack(spacing: 8) {
                if let err = viewModel.surfaceError {
                    Text(err)
                        .font(.system(size: 13))
                        .foregroundStyle(paywallError)
                        .multilineTextAlignment(.center)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.bottom, 2)
                }
                if let toast = viewModel.purchaseToast {
                    Text(toast)
                        .font(.system(size: 13))
                        .foregroundStyle(LNColor.success)
                        .multilineTextAlignment(.center)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.bottom, 2)
                }
                LNButton(
                    title: L10n.tr("paywall_cta_continue"),
                    variant: .primary,
                    enabled: selectedOffer(from: packages) != nil && !viewModel.purchasing,
                    loading: viewModel.purchasing
                ) {
                    Task { await viewModel.purchaseSelected() }
                }
                .accessibilityIdentifier("paywall_purchase_button")
                Button {
                    Task { await viewModel.restore() }
                } label: {
                    Text(L10n.tr("paywall_restore"))
                        .font(LNTypography.bodyMedium())
                        .foregroundStyle(paywallSubtitle)
                        .frame(maxWidth: .infinity)
                        .frame(height: 34)
                }
                .buttonStyle(.lnPressable(scale: 0.98, pressedOpacity: 0.78))
                .disabled(viewModel.purchasing)
                Text(L10n.tr("paywall_footer"))
                    .font(.system(size: 11, weight: .medium))
                    .foregroundStyle(paywallFooter)
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
                    .minimumScaleFactor(0.88)
            }
            .padding(.horizontal, horizontalPadding)
            .padding(.top, 28)
            .padding(.bottom, 8)
            .background(
                LinearGradient(
                    colors: [
                        LNColor.paywallBottom,
                        LNColor.paywallBottom,
                        LNColor.paywallBottom
                    ],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .ignoresSafeArea(edges: .bottom)
            )
        }
    }

    private func periodLabel(for kind: PaywallPlanKind) -> String {
        switch kind {
        case .monthly: return L10n.tr("paywall_period_monthly")
        case .annual: return L10n.tr("paywall_period_yearly")
        case .lifetime: return L10n.tr("paywall_period_lifetime")
        case .other: return L10n.tr("paywall_period_other")
        }
    }

    private func bestValueText(for offer: PaywallPackageOffer) -> String {
        if let savings = offer.savingsPercent, savings > 0 {
            return "\(L10n.tr("paywall_best_value")) -\(savings)%"
        }
        return L10n.tr("paywall_best_value")
    }

    private func selectedOffer(from packages: [PaywallPackageOffer]) -> PaywallPackageOffer? {
        guard !packages.isEmpty else { return nil }
        let index = min(max(viewModel.selectedIndex, 0), packages.count - 1)
        return packages[index]
    }

    private var sourceText: String {
        switch viewModel.source {
        case PaywallSource.onboarding:
            return L10n.tr("paywall_source_onboarding")
        case PaywallSource.importValue:
            return L10n.tr("paywall_source_import_value")
        case PaywallSource.vaultNearLimit:
            return L10n.tr("paywall_source_vault_near_limit")
        case PaywallSource.backupSuccess:
            return L10n.tr("paywall_source_backup_success")
        case PaywallSource.aiNearLimit:
            return L10n.tr("paywall_source_ai_near_limit")
        case PaywallSource.quotaVault:
            return L10n.tr("paywall_source_quota_vault")
        case PaywallSource.quotaBackup:
            return L10n.tr("paywall_source_quota_backup")
        case PaywallSource.quotaAI:
            return L10n.tr("paywall_source_quota_ai")
        case PaywallSource.proExport:
            return L10n.tr("paywall_source_pro_export")
        default:
            return L10n.tr("paywall_source_manual")
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

private let paywallCrownGlyph = Color(hex: 0xFFE08A)
private let paywallTitle = Color(hex: 0xF5F8FF)
private let paywallSubtitle = Color(hex: 0x8A94A8)
private let paywallCloseFill = Color(hex: 0x1A2230)
private let paywallCloseStroke = Color(hex: 0x2E3A4D)
private let paywallCardBg = Color(hex: 0x10141D)
private let paywallCardStroke = Color(hex: 0x2A3140)
private let paywallCardStrokeSelected = Color(hex: 0x4A9EFF)
private let paywallPriceSelected = Color(hex: 0x4A9EFF)
private let paywallPriceIdle = Color(hex: 0xF5F8FF)
private let paywallBadgeBg = Color(hex: 0x2563EB)
private let paywallTierMeta = Color(hex: 0x7E8AA0)
private let paywallFeatureCheck = Color(hex: 0x32D583)
private let paywallFooter = Color(hex: 0x5C6578)
private let paywallError = Color(hex: 0xFF7A8A)

private let storeTextWhitespaceRegex = try? NSRegularExpression(pattern: "\\s+")

private extension String {
    func compactStoreText() -> String {
        let trimmed = trimmedStoreText()
        guard let regex = storeTextWhitespaceRegex else { return trimmed }
        let range = NSRange(location: 0, length: (trimmed as NSString).length)
        return regex.stringByReplacingMatches(in: trimmed, range: range, withTemplate: " ")
    }

    func trimmedStoreText() -> String {
        trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
