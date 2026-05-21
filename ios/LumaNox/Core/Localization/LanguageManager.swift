import Foundation

final class LanguageManager: ObservableObject {
    static let shared = LanguageManager()

    static let supportedLocaleIdentifiers: [String] = ["zh-Hans", "en"]
    private let storageKey = "ln_app_language"

    @Published var selection: AppLanguage

    init() {
        let saved = UserDefaults.standard.string(forKey: storageKey)
        self.selection = AppLanguage(rawValue: saved ?? AppLanguage.system.rawValue) ?? .system
    }

    var effectiveLocaleIdentifier: String {
        switch selection {
        case .system:
            return Self.resolveSystemLocaleIdentifier()
        case .simplifiedChinese:
            return AppLanguage.simplifiedChinese.rawValue
        case .english:
            return AppLanguage.english.rawValue
        }
    }

    var effectiveLocale: Locale {
        Locale(identifier: effectiveLocaleIdentifier)
    }

    var effectiveLanguageLabel: String {
        switch effectiveLocaleIdentifier {
        case AppLanguage.simplifiedChinese.rawValue:
            return L10n.tr("language_simplified_chinese")
        case AppLanguage.english.rawValue:
            return L10n.tr("language_english")
        default:
            return L10n.tr("language_english")
        }
    }

    func setSelection(_ newSelection: AppLanguage) {
        selection = newSelection
        UserDefaults.standard.set(newSelection.rawValue, forKey: storageKey)
    }

    func localizedString(_ key: String) -> String {
        let localized = lookupString(for: effectiveLocaleIdentifier, key: key)
        let english = lookupString(for: AppLanguage.english.rawValue, key: key)

        if localized == key { return english }
        return localized
    }

    private func lookupString(for localeIdentifier: String, key: String) -> String {
        guard let path = Bundle.main.path(forResource: localeIdentifier, ofType: "lproj"),
              let localizedBundle = Bundle(path: path) else {
            return Bundle.main.localizedString(forKey: key, value: key, table: nil)
        }

        return localizedBundle.localizedString(forKey: key, value: key, table: nil)
    }

    static func resolveSystemLocaleIdentifier() -> String {
        for preferred in Locale.preferredLanguages {
            let candidate = resolveLocaleIdentifier(from: preferred)
            if let candidate {
                return candidate
            }
        }
        return AppLanguage.english.rawValue
    }

    private static func resolveLocaleIdentifier(from identifier: String) -> String? {
        let locale = Locale(identifier: identifier.replacingOccurrences(of: "_", with: "-"))
        guard let languageCode = locale.language.languageCode?.identifier else {
            return nil
        }
        let script = locale.language.script?.identifier

        if languageCode == "zh" {
            if let script, !script.isEmpty {
                if script == "Hans" {
                    return AppLanguage.simplifiedChinese.rawValue
                }
                return nil
            }
            return AppLanguage.simplifiedChinese.rawValue
        }

        if supportedLocaleIdentifiers.contains(languageCode) {
            return languageCode
        }

        return nil
    }
}

enum AppLanguage: String, CaseIterable, Identifiable, Hashable {
    case system = "system"
    case simplifiedChinese = "zh-Hans"
    case english = "en"

    var id: String { rawValue }

    var labelKey: String {
        switch self {
        case .system:
            return "language_follow_system"
        case .simplifiedChinese:
            return "language_simplified_chinese"
        case .english:
            return "language_english"
        }
    }
}
