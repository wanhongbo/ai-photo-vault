package com.xpx.vault.ui.vault

import android.content.Context
import androidx.annotation.StringRes
import com.xpx.vault.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val IMPORT_ORIGINALS_PREFS = "import_originals_prefs"
private const val IMPORT_ORIGINALS_ACTION = "action"

enum class ImportOriginalsAction(
    val rawValue: String,
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int,
) {
    ASK_EACH_TIME(
        rawValue = "ask_each_time",
        titleRes = R.string.import_originals_pref_ask,
        descRes = R.string.import_originals_pref_ask_desc,
    ),
    KEEP_ORIGINALS(
        rawValue = "keep_originals",
        titleRes = R.string.import_originals_pref_keep,
        descRes = R.string.import_originals_pref_keep_desc,
    ),
    DELETE_ORIGINALS(
        rawValue = "delete_originals",
        titleRes = R.string.import_originals_pref_delete,
        descRes = R.string.import_originals_pref_delete_desc,
    );

    companion object {
        fun fromRawValue(rawValue: String?): ImportOriginalsAction =
            entries.firstOrNull { it.rawValue == rawValue } ?: ASK_EACH_TIME
    }
}

object ImportOriginalsPreferenceStore {
    private val lock = Any()
    private val action = MutableStateFlow(ImportOriginalsAction.ASK_EACH_TIME)
    private var loaded = false

    fun observe(context: Context): StateFlow<ImportOriginalsAction> {
        ensureLoaded(context)
        return action.asStateFlow()
    }

    fun current(context: Context): ImportOriginalsAction {
        ensureLoaded(context)
        return action.value
    }

    fun set(context: Context, next: ImportOriginalsAction) {
        ensureLoaded(context)
        context.applicationContext
            .getSharedPreferences(IMPORT_ORIGINALS_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(IMPORT_ORIGINALS_ACTION, next.rawValue)
            .apply()
        action.value = next
    }

    private fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            val rawValue = context.applicationContext
                .getSharedPreferences(IMPORT_ORIGINALS_PREFS, Context.MODE_PRIVATE)
                .getString(IMPORT_ORIGINALS_ACTION, ImportOriginalsAction.ASK_EACH_TIME.rawValue)
            action.value = ImportOriginalsAction.fromRawValue(rawValue)
            loaded = true
        }
    }
}
