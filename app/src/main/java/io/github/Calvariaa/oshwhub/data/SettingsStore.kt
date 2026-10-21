package io.github.Calvariaa.oshwhub.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "osh_settings")

class SettingsStore(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")       // system / light / dark
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val PURE_BLACK = booleanPreferencesKey("pure_black")
        val GRID_COLUMNS = intPreferencesKey("grid_columns")      // 1..3
        val SEARCH_HISTORY = stringPreferencesKey("search_history")
        val ENABLE_LOG = booleanPreferencesKey("enable_log")
    }

    val themeMode: Flow<String> = context.settingsDataStore.data.map { it[Keys.THEME_MODE] ?: "system" }
    val dynamicColor: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.DYNAMIC_COLOR] ?: true }
    val pureBlack: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.PURE_BLACK] ?: false }
    val gridColumns: Flow<Int> = context.settingsDataStore.data.map { it[Keys.GRID_COLUMNS] ?: 2 }
    val enableLog: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.ENABLE_LOG] ?: false }
    val searchHistory: Flow<List<String>> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.SEARCH_HISTORY]?.split("\u0001")?.filter { it.isNotBlank() } ?: emptyList()
    }

    suspend fun setThemeMode(mode: String) = context.settingsDataStore.edit { it[Keys.THEME_MODE] = mode }
    suspend fun setDynamicColor(enabled: Boolean) = context.settingsDataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    suspend fun setPureBlack(enabled: Boolean) = context.settingsDataStore.edit { it[Keys.PURE_BLACK] = enabled }
    suspend fun setGridColumns(columns: Int) = context.settingsDataStore.edit { it[Keys.GRID_COLUMNS] = columns.coerceIn(1, 3) }
    suspend fun setEnableLog(enabled: Boolean) = context.settingsDataStore.edit { it[Keys.ENABLE_LOG] = enabled }

    suspend fun pushSearchHistory(keyword: String) {
        val k = keyword.trim()
        if (k.isEmpty()) return
        context.settingsDataStore.edit { prefs ->
            val current = prefs[Keys.SEARCH_HISTORY]?.split("\u0001")?.filter { it.isNotBlank() } ?: emptyList()
            val next = (listOf(k) + current.filter { it != k }).take(20)
            prefs[Keys.SEARCH_HISTORY] = next.joinToString("\u0001")
        }
    }

    suspend fun clearSearchHistory() = context.settingsDataStore.edit { it.remove(Keys.SEARCH_HISTORY) }
}
