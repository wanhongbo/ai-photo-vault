import Foundation

enum ImportOriginalsAction: String, CaseIterable, Identifiable {
    case askEachTime
    case keepOriginals
    case deleteOriginals

    var id: String { rawValue }

    var title: String {
        switch self {
        case .askEachTime: return L10n.tr("import_originals_pref_ask")
        case .keepOriginals: return L10n.tr("import_originals_pref_keep")
        case .deleteOriginals: return L10n.tr("import_originals_pref_delete")
        }
    }

    var subtitle: String {
        switch self {
        case .askEachTime: return L10n.tr("import_originals_pref_ask_desc")
        case .keepOriginals: return L10n.tr("import_originals_pref_keep_desc")
        case .deleteOriginals: return L10n.tr("import_originals_pref_delete_desc")
        }
    }

    var systemImage: String {
        switch self {
        case .askEachTime: return "message"
        case .keepOriginals: return "photo.on.rectangle"
        case .deleteOriginals: return "trash"
        }
    }
}

@MainActor
final class ImportOriginalsPreferenceStore: ObservableObject {
    static let shared = ImportOriginalsPreferenceStore()

    @Published var action: ImportOriginalsAction {
        didSet {
            UserDefaults.standard.set(action.rawValue, forKey: Self.userDefaultsKey)
        }
    }

    private static let userDefaultsKey = "import_originals_action_v1"

    private init() {
        let rawValue = UserDefaults.standard.string(forKey: Self.userDefaultsKey)
        action = rawValue.flatMap(ImportOriginalsAction.init(rawValue:)) ?? .askEachTime
    }
}

