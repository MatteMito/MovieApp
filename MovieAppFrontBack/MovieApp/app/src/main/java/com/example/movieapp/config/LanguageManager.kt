package com.example.movieapp.config

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * gestore persistente della lingua dell'app
 */
object LanguageManager {

    private const val PREFS_NAME = "language_preferences"
    private const val KEY_LANGUAGE = "selected_language"
    private const val DEFAULT_LANGUAGE = "it"

    /**
     * lingue supportate
     */
    val SUPPORTED_LANGUAGES = mapOf(
        "it" to "Italiano",
        "en" to "English",
        "es" to "Español",
        "fr" to "Français",
        "de" to "Deutsch"
    )

    /**
     * ottiene le shared preferences
     */
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * salva la lingua selezionata
     */
    fun saveLanguage(context: Context, languageCode: String) {
        getPrefs(context).edit().putString(KEY_LANGUAGE, languageCode).apply()
    }

    /**
     * ottiene la lingua salvata
     */
    fun getSavedLanguage(context: Context): String {
        return getPrefs(context).getString(KEY_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
    }

    /**
     * applica la lingua salvata all'avvio dell'app
     */
    fun applyLanguage(context: Context, languageCode: String? = null) {
        val code = languageCode ?: getSavedLanguage(context)

        val locale = Locale(code)
        val localeList = LocaleListCompat.create(locale)
        AppCompatDelegate.setApplicationLocales(localeList)

        if (languageCode != null) {
            saveLanguage(context, languageCode)
        }
    }

    /**
     * ottiene il nome della lingua corrente
     */
    fun getCurrentLanguageName(context: Context): String {
        val code = getSavedLanguage(context)
        return SUPPORTED_LANGUAGES[code] ?: "Italiano"
    }
}