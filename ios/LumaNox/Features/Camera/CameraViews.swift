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
    @State private var zoomGestureBase: CGFloat = 1

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            if viewModel.controller.permissionDenied {
                permissionDenied
            } else {
                cameraPreview
            }

            if viewModel.showSettingsPanel {
                Color.black.opacity(0.001)
                    .ignoresSafeArea()
                    .onTapGesture { viewModel.showSettingsPanel = false }
            }

            VStack(spacing: 0) {
                topBar
                    .padding(.horizontal, 16)
                    .padding(.top, 8)

                if viewModel.showSettingsPanel {
                    CameraSettingsPanel(viewModel: viewModel)
                        .padding(.horizontal, 16)
                        .padding(.top, 10)
                        .transition(.opacity.combined(with: .move(edge: .top)))
                }

                Spacer()

                if let message = viewModel.message {
                    Text(message)
                        .font(LNTypography.bodyMedium())
                        .foregroundStyle(LNColor.title)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 20)
                        .padding(.vertical, 10)
                        .background(.black.opacity(0.48))
                        .clipShape(Capsule())
                        .padding(.bottom, 12)
                }

                if viewModel.controller.isRecording {
                    RecordingBadge(text: viewModel.recordingDurationText)
                        .padding(.bottom, 10)
                }

                ZoomRail(
                    minZoom: viewModel.controller.capabilities.minZoom,
                    maxZoom: viewModel.controller.capabilities.maxZoom,
                    zoom: viewModel.controller.zoomFactor,
                    onSelect: viewModel.setZoom
                )
                .padding(.bottom, 16)

                cameraControls
                    .padding(.horizontal, 48)
                    .padding(.bottom, 12)

                modeSwitch
                    .padding(.bottom, 28)
            }
        }
        .animation(.easeInOut(duration: 0.18), value: viewModel.showSettingsPanel)
        .onAppear { viewModel.onAppear() }
        .onDisappear { viewModel.onDisappear() }
        .accessibilityIdentifier("private_camera_view")
    }

    private var cameraPreview: some View {
        GeometryReader { proxy in
            CameraPreviewRepresentable(controller: viewModel.controller)
                .ignoresSafeArea()
                .overlay {
                    if viewModel.showGrid {
                        CameraGridOverlay()
                    }
                    if let marker = viewModel.focusMarker {
                        FocusMarker(center: CGPoint(x: marker.x * proxy.size.width, y: marker.y * proxy.size.height))
                    }
                    if let remaining = viewModel.countdownRemaining {
                        CountdownView(remaining: remaining)
                    }
                }
                .contentShape(Rectangle())
                .gesture(
                    MagnificationGesture()
                        .onChanged { value in
                            viewModel.setZoom(zoomGestureBase * value)
                        }
                        .onEnded { _ in
                            zoomGestureBase = viewModel.controller.zoomFactor
                        }
                )
                .simultaneousGesture(
                    DragGesture(minimumDistance: 0)
                        .onEnded { value in
                            let x = value.location.x / max(proxy.size.width, 1)
                            let y = value.location.y / max(proxy.size.height, 1)
                            viewModel.focus(at: CGPoint(x: x, y: y))
                        }
                )
        }
    }

    private var topBar: some View {
        HStack {
            CameraIconButton(systemName: "xmark", label: L10n.commonCancel) {
                dismiss()
            }

            Spacer()

            HStack(spacing: 8) {
                Circle()
                    .fill(LNColor.success)
                    .frame(width: 8, height: 8)
                Text(L10n.tr("camera_secure_capture"))
                    .font(LNTypography.labelMedium().weight(.semibold))
                    .foregroundStyle(LNColor.title)
            }
            .padding(.horizontal, 12)
            .frame(height: 36)
            .background(.black.opacity(0.48))
            .clipShape(Capsule())
            .overlay(Capsule().stroke(.white.opacity(0.14), lineWidth: 1))

            Spacer()

            if viewModel.isSaving {
                ProgressView()
                    .tint(LNColor.brandBlue)
                    .frame(width: 44, height: 44)
                    .background(.black.opacity(0.48))
                    .clipShape(Circle())
            } else {
                CameraIconButton(
                    systemName: "slider.horizontal.3",
                    label: L10n.tr("camera_settings_title")
                ) {
                    viewModel.showSettingsPanel.toggle()
                }
            }
        }
    }

    private var cameraControls: some View {
        HStack {
            CameraIconButton(
                systemName: "arrow.triangle.2.circlepath.camera",
                label: L10n.tr("camera_flip_lens")
            ) {
                viewModel.flipCamera()
                zoomGestureBase = 1
            }

            Spacer()

            Button {
                if viewModel.captureMode == .video && viewModel.controller.isRecording {
                    viewModel.stopVideo()
                    return
                }
                guard router.guardProFeature(.vaultImport) else { return }
                viewModel.triggerShutter()
            } label: {
                ZStack {
                    Circle()
                        .fill(viewModel.controller.isRecording ? LNColor.error.opacity(0.92) : LNColor.title.opacity(0.95))
                        .frame(width: viewModel.controller.isRecording ? 58 : 72, height: viewModel.controller.isRecording ? 58 : 72)
                    RoundedRectangle(cornerRadius: viewModel.controller.isRecording ? 6 : 29)
                        .fill(viewModel.controller.isRecording ? LNColor.title : LNColor.title)
                        .frame(width: viewModel.controller.isRecording ? 28 : 56, height: viewModel.controller.isRecording ? 28 : 56)
                        .opacity(viewModel.controller.isRecording ? 1 : 0.92)
                }
                .frame(width: 78, height: 78)
                .overlay(Circle().stroke(LNColor.navItemActive, lineWidth: 2))
            }
            .disabled(viewModel.isSaving || viewModel.countdownRemaining != nil)
            .accessibilityIdentifier("private_camera_shutter")
            .accessibilityLabel(L10n.tr("camera_shutter"))

            Spacer()

            CameraIconButton(systemName: "photo", label: L10n.tr("camera_view_last_media")) {}
                .opacity(0.75)
        }
    }

    private var modeSwitch: some View {
        HStack(spacing: 4) {
            ForEach(CameraCaptureMode.allCases, id: \.self) { mode in
                Button {
                    viewModel.captureMode = mode
                } label: {
                    Text(mode.localizedTitle)
                        .font(LNTypography.labelMedium().weight(viewModel.captureMode == mode ? .bold : .semibold))
                        .foregroundStyle(viewModel.captureMode == mode ? Color.white : LNColor.title)
                        .frame(width: 62, height: 30)
                        .background(viewModel.captureMode == mode ? LNColor.brandBlue : Color.clear)
                        .clipShape(Capsule())
                }
                .buttonStyle(.plain)
            }
        }
        .padding(5)
        .background(.white.opacity(0.14))
        .clipShape(Capsule())
        .overlay(Capsule().stroke(.white.opacity(0.12), lineWidth: 1))
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

private struct CameraSettingsPanel: View {
    @ObservedObject var viewModel: PrivateCameraViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(L10n.tr("camera_settings_title"))
                .font(LNTypography.labelMedium().weight(.semibold))
                .foregroundStyle(LNColor.subtitle)

            CameraSettingsRow(title: L10n.tr("camera_settings_row_flash")) {
                ForEach(CameraFlashMode.allCases, id: \.self) { mode in
                    CameraOptionChip(
                        title: mode.localizedTitle,
                        selected: viewModel.controller.flashMode == mode,
                        enabled: viewModel.controller.capabilities.hasFlash || viewModel.controller.capabilities.hasTorch
                    ) {
                        viewModel.setFlashMode(mode)
                    }
                }
            }

            CameraSettingsRow(title: L10n.tr("camera_settings_row_timer")) {
                ForEach(CameraTimerOption.allCases, id: \.self) { option in
                    CameraOptionChip(
                        title: option.localizedTitle,
                        selected: viewModel.timerOption == option
                    ) {
                        viewModel.setTimerOption(option)
                    }
                }
            }

            CameraSettingsRow(title: L10n.tr("camera_settings_row_grid")) {
                CameraOptionChip(title: L10n.tr("camera_pill_off"), selected: !viewModel.showGrid) {
                    viewModel.showGrid = false
                }
                CameraOptionChip(title: L10n.tr("camera_pill_on"), selected: viewModel.showGrid) {
                    viewModel.showGrid = true
                }
            }

            if viewModel.controller.capabilities.minExposureBias != viewModel.controller.capabilities.maxExposureBias {
                CameraExposureRow(viewModel: viewModel)
            }

            if viewModel.captureMode == .video {
                CameraSettingsRow(title: L10n.tr("camera_settings_row_resolution")) {
                    CameraOptionChip(
                        title: L10n.tr("camera_resolution_fhd"),
                        selected: viewModel.controller.videoResolution == .fhd
                    ) {
                        viewModel.setVideoResolution(.fhd)
                    }
                    CameraOptionChip(
                        title: L10n.tr("camera_resolution_4k"),
                        selected: viewModel.controller.videoResolution == .uhd4K,
                        enabled: viewModel.controller.capabilities.supports4K
                    ) {
                        viewModel.setVideoResolution(.uhd4K)
                    }
                }

                CameraSettingsRow(title: L10n.tr("camera_settings_row_fps")) {
                    CameraOptionChip(
                        title: L10n.tr("camera_fps_30"),
                        selected: viewModel.controller.videoFPS == .thirty
                    ) {
                        viewModel.setVideoFPS(.thirty)
                    }
                    CameraOptionChip(
                        title: L10n.tr("camera_fps_60"),
                        selected: viewModel.controller.videoFPS == .sixty,
                        enabled: viewModel.controller.capabilities.supports60FPS
                    ) {
                        viewModel.setVideoFPS(.sixty)
                    }
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .background(Color(hex: 0x2C2C2E).opacity(0.94))
        .clipShape(RoundedRectangle(cornerRadius: 28))
        .overlay(RoundedRectangle(cornerRadius: 28).stroke(.white.opacity(0.14), lineWidth: 1))
    }
}

private struct CameraSettingsRow<Content: View>: View {
    let title: String
    @ViewBuilder let content: Content

    var body: some View {
        HStack {
            Text(title)
                .font(LNTypography.bodyMedium().weight(.semibold))
                .foregroundStyle(LNColor.title)
            Spacer(minLength: 12)
            HStack(spacing: 8) {
                content
            }
        }
        .frame(minHeight: 38)
    }
}

private struct CameraExposureRow: View {
    @ObservedObject var viewModel: PrivateCameraViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(L10n.tr("camera_settings_row_exposure"))
                    .font(LNTypography.bodyMedium().weight(.semibold))
                    .foregroundStyle(LNColor.title)
                Spacer()
                Text(L10n.tr("camera_control_exposure_value", formattedExposure))
                    .font(LNTypography.labelMedium())
                    .foregroundStyle(LNColor.subtitle)
            }
            Slider(
                value: Binding(
                    get: { Double(viewModel.controller.exposureBias) },
                    set: { viewModel.setExposure(Float($0)) }
                ),
                in: Double(viewModel.controller.capabilities.minExposureBias)...Double(viewModel.controller.capabilities.maxExposureBias),
                step: 0.25
            )
            .tint(LNColor.brandBlue)
        }
    }

    private var formattedExposure: String {
        let value = viewModel.controller.exposureBias
        if abs(value) < 0.01 { return L10n.tr("camera_control_exposure_auto") }
        return value > 0 ? String(format: "+%.1f", value) : String(format: "%.1f", value)
    }
}

private struct CameraOptionChip: View {
    let title: String
    let selected: Bool
    var enabled = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(LNTypography.labelMedium().weight(.semibold))
                .foregroundStyle(foreground)
                .frame(minWidth: 36, minHeight: 30)
                .padding(.horizontal, 8)
                .background(selected ? LNColor.brandBlue : Color(hex: 0x3A3A3C))
                .clipShape(Capsule())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.36)
    }

    private var foreground: Color {
        if !enabled { return LNColor.subtitle }
        return selected ? .white : LNColor.title
    }
}

private struct ZoomRail: View {
    let minZoom: CGFloat
    let maxZoom: CGFloat
    let zoom: CGFloat
    let onSelect: (CGFloat) -> Void

    private let presets: [CGFloat] = [0.7, 1, 2]

    var body: some View {
        HStack(spacing: 10) {
            ForEach(presets, id: \.self) { preset in
                let enabled = preset >= minZoom && preset <= maxZoom
                Button {
                    onSelect(preset)
                } label: {
                    Text(label(for: preset))
                        .font(LNTypography.labelMedium().weight(isSelected(preset) ? .bold : .semibold))
                        .foregroundStyle(enabled ? (isSelected(preset) ? Color.white : LNColor.navItemActive) : LNColor.subtitle.opacity(0.48))
                        .frame(width: 48, height: 28)
                        .background(isSelected(preset) ? LNColor.brandBlue : Color.clear)
                        .clipShape(Capsule())
                }
                .buttonStyle(.plain)
                .disabled(!enabled)
            }
        }
        .padding(.horizontal, 10)
        .frame(height: 38)
        .background(.black.opacity(0.42))
        .clipShape(Capsule())
        .overlay(Capsule().stroke(.white.opacity(0.12), lineWidth: 1))
    }

    private func isSelected(_ preset: CGFloat) -> Bool {
        abs(zoom - preset) < 0.08
    }

    private func label(for preset: CGFloat) -> String {
        preset == 1 ? "1x" : preset == 0.7 ? ".7" : "2"
    }
}

private struct CameraGridOverlay: View {
    var body: some View {
        GeometryReader { proxy in
            Path { path in
                let thirdW = proxy.size.width / 3
                let thirdH = proxy.size.height / 3
                path.move(to: CGPoint(x: thirdW, y: 0))
                path.addLine(to: CGPoint(x: thirdW, y: proxy.size.height))
                path.move(to: CGPoint(x: thirdW * 2, y: 0))
                path.addLine(to: CGPoint(x: thirdW * 2, y: proxy.size.height))
                path.move(to: CGPoint(x: 0, y: thirdH))
                path.addLine(to: CGPoint(x: proxy.size.width, y: thirdH))
                path.move(to: CGPoint(x: 0, y: thirdH * 2))
                path.addLine(to: CGPoint(x: proxy.size.width, y: thirdH * 2))
            }
            .stroke(.white.opacity(0.22), lineWidth: 1)
        }
        .allowsHitTesting(false)
    }
}

private struct FocusMarker: View {
    let center: CGPoint

    var body: some View {
        Circle()
            .stroke(LNColor.amberWarning, lineWidth: 2)
            .frame(width: 70, height: 70)
            .position(center)
            .allowsHitTesting(false)
    }
}

private struct CountdownView: View {
    let remaining: Int

    var body: some View {
        Text("\(remaining)")
            .font(.system(size: 30, weight: .bold))
            .foregroundStyle(LNColor.title)
            .frame(width: 60, height: 60)
            .background(.black.opacity(0.54))
            .clipShape(Circle())
            .overlay(Circle().stroke(.white.opacity(0.28), lineWidth: 1))
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
            .allowsHitTesting(false)
    }
}

private struct RecordingBadge: View {
    let text: String

    var body: some View {
        HStack(spacing: 8) {
            Circle()
                .fill(LNColor.error)
                .frame(width: 8, height: 8)
            Text(text)
                .font(LNTypography.labelMedium().weight(.bold))
                .foregroundStyle(LNColor.title)
        }
        .padding(.horizontal, 12)
        .frame(height: 32)
        .background(.black.opacity(0.52))
        .clipShape(Capsule())
    }
}

private struct CameraIconButton: View {
    let systemName: String
    let label: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: systemName)
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(LNColor.title)
                .frame(width: 44, height: 44)
                .background(.black.opacity(0.48))
                .clipShape(Circle())
                .overlay(Circle().stroke(.white.opacity(0.14), lineWidth: 1))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
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

private extension CameraCaptureMode {
    var localizedTitle: String {
        switch self {
        case .photo: return L10n.tr("camera_mode_photo")
        case .video: return L10n.tr("camera_mode_video")
        }
    }
}

private extension CameraFlashMode {
    var localizedTitle: String {
        switch self {
        case .off: return L10n.tr("camera_pill_off")
        case .auto: return L10n.tr("camera_pill_auto")
        case .on: return L10n.tr("camera_pill_on")
        }
    }
}

private extension CameraTimerOption {
    var localizedTitle: String {
        switch self {
        case .off: return L10n.tr("camera_pill_off")
        case .three: return L10n.tr("camera_timer_3s")
        case .ten: return L10n.tr("camera_timer_10s")
        }
    }
}
