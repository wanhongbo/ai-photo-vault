import AVFoundation
import SwiftUI

struct CameraHomeView: View {
    @EnvironmentObject private var router: AppRouter

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text(L10n.tr("camera_mode_photo"))
                .font(LNTypography.displaySmall())
                .foregroundStyle(LNColor.title)
                .padding(.horizontal, LNSpacing.screenHorizontal)
                .padding(.top, 8)

            Button { router.openPrivateCamera() } label: {
                CameraPreviewCard()
            }
            .buttonStyle(.plain)
            .padding(.horizontal, 20)
            .padding(.bottom, LNSpacing.homeNavBarHeight + 20)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .accessibilityIdentifier("camera_home_view")
    }
}

private struct CameraPreviewCard: View {
    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: LNRadius.homeCard)
                .fill(LNColor.emptyCardBg)
            RoundedRectangle(cornerRadius: LNRadius.homeCard)
                .stroke(LNColor.stroke, lineWidth: 1)

            CameraCorners()
                .stroke(LNColor.strokeStrong.opacity(0.7), lineWidth: 1)
                .padding(28)

            VStack(spacing: 14) {
                Image(systemName: "camera")
                    .font(.system(size: 28, weight: .semibold))
                    .foregroundStyle(LNColor.navItemActive)
                    .frame(width: 78, height: 78)
                    .background(LNColor.brandBlue.opacity(0.18))
                    .clipShape(Circle())
                    .overlay(Circle().stroke(LNColor.brandBlue, lineWidth: 1))
                Text(L10n.tr("camera_home_title"))
                    .font(LNTypography.headlineSmall())
                    .foregroundStyle(LNColor.title)
                Text(L10n.tr("camera_home_desc"))
                    .font(LNTypography.bodyMedium())
                    .foregroundStyle(LNColor.subtitle)
                    .multilineTextAlignment(.center)
                    .frame(width: 220)
            }

            VStack(spacing: 10) {
                Spacer()
                ZStack {
                    Capsule()
                        .stroke(LNColor.title.opacity(0.78), lineWidth: 2)
                        .frame(width: 136, height: 54)
                    Circle()
                        .fill(LNColor.title)
                        .frame(width: 36, height: 36)
                }
                Text(L10n.tr("camera_mode_photo"))
                    .font(LNTypography.labelMedium().weight(.bold))
                    .foregroundStyle(LNColor.buttonPrimaryFg)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 7)
                    .background(LNColor.brandBlue)
                    .clipShape(Capsule())
            }
            .padding(.bottom, 28)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityLabel(L10n.tr("camera_home_action"))
    }
}

private struct CameraCorners: Shape {
    func path(in rect: CGRect) -> Path {
        let length: CGFloat = 48
        var p = Path()
        p.move(to: CGPoint(x: rect.minX, y: rect.minY + length))
        p.addLine(to: CGPoint(x: rect.minX, y: rect.minY))
        p.addLine(to: CGPoint(x: rect.minX + length, y: rect.minY))
        p.move(to: CGPoint(x: rect.maxX - length, y: rect.minY))
        p.addLine(to: CGPoint(x: rect.maxX, y: rect.minY))
        p.addLine(to: CGPoint(x: rect.maxX, y: rect.minY + length))
        p.move(to: CGPoint(x: rect.minX, y: rect.maxY - length))
        p.addLine(to: CGPoint(x: rect.minX, y: rect.maxY))
        p.addLine(to: CGPoint(x: rect.minX + length, y: rect.maxY))
        p.move(to: CGPoint(x: rect.maxX - length, y: rect.maxY))
        p.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY))
        p.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY - length))
        return p
    }
}

struct PrivateCameraView: View {
    @EnvironmentObject private var router: AppRouter
    @Environment(\.dismiss) private var dismiss
    @StateObject private var viewModel = PrivateCameraViewModel()

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            if viewModel.controller.permissionDenied {
                permissionDenied
            } else {
                CameraPreviewRepresentable(controller: viewModel.controller)
                    .ignoresSafeArea()
            }

            VStack {
                HStack {
                    Button { dismiss() } label: {
                        Image(systemName: "xmark")
                            .foregroundStyle(LNColor.title)
                            .frame(width: 44, height: 44)
                    }
                    Spacer()
                    if viewModel.isSaving {
                        ProgressView().tint(LNColor.brandBlue)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)

                Spacer()

                if let message = viewModel.message {
                    Text(message)
                        .font(LNTypography.bodyMedium())
                        .foregroundStyle(LNColor.title)
                        .padding(.horizontal, 20)
                        .padding(.bottom, 8)
                }

                HStack(spacing: 32) {
                    Button { viewModel.toggleFlash() } label: {
                        Image(systemName: flashIcon)
                            .foregroundStyle(LNColor.title)
                            .frame(width: 48, height: 48)
                            .background(LNColor.sectionBg.opacity(0.6))
                            .clipShape(Circle())
                    }
                    Button {
                        guard router.guardProFeature(.vaultImport) else { return }
                        viewModel.capturePhoto()
                    } label: {
                        Circle()
                            .stroke(LNColor.title, lineWidth: 4)
                            .frame(width: 72, height: 72)
                            .overlay(
                                Circle()
                                    .fill(viewModel.controller.isRecording ? LNColor.error : LNColor.title)
                                    .frame(width: 58, height: 58)
                            )
                    }
                    .disabled(viewModel.isSaving)
                    .simultaneousGesture(
                        LongPressGesture(minimumDuration: 0.35)
                            .onEnded { _ in
                                guard router.guardProFeature(.vaultImport) else { return }
                                viewModel.toggleVideo()
                            }
                    )

                    Button { viewModel.flipCamera() } label: {
                        Image(systemName: "arrow.triangle.2.circlepath.camera")
                            .foregroundStyle(LNColor.title)
                            .frame(width: 48, height: 48)
                            .background(LNColor.sectionBg.opacity(0.6))
                            .clipShape(Circle())
                    }
                }
                .padding(.bottom, 48)
            }
        }
        .onAppear { viewModel.onAppear() }
        .onDisappear { viewModel.onDisappear() }
        .accessibilityIdentifier("private_camera_view")
    }

    private var flashIcon: String {
        switch viewModel.controller.flashMode {
        case .on: return "bolt.fill"
        case .auto: return "bolt.badge.automatic.fill"
        case .off: return "bolt.slash.fill"
        @unknown default: return "bolt.slash.fill"
        }
    }

    private var permissionDenied: some View {
        VStack(spacing: 16) {
            Text(L10n.tr("camera_permission_denied"))
                .font(LNTypography.bodyMedium())
                .foregroundStyle(LNColor.subtitle)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
            LNButton(title: L10n.tr("camera_open_settings"), variant: .secondary) {
                if let url = URL(string: UIApplication.openSettingsURLString) {
                    UIApplication.shared.open(url)
                }
            }
        }
    }
}

private struct CameraPreviewRepresentable: UIViewRepresentable {
    let controller: CameraSessionController

    func makeUIView(context: Context) -> UIView {
        let view = UIView(frame: .zero)
        view.backgroundColor = .black
        DispatchQueue.main.async {
            controller.attachPreview(to: view)
        }
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        if let layer = uiView.layer.sublayers?.first as? AVCaptureVideoPreviewLayer {
            layer.frame = uiView.bounds
        } else {
            controller.attachPreview(to: uiView)
        }
    }
}
