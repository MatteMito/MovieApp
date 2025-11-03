//file: app/src/main/java/com/example/movieapp/MainActivity.kt
//mainactivity con chiamata initializeapp al primo avvio

package com.example.movieapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.example.movieapp.databinding.ActivityMainBinding
import com.example.movieapp.config.AppConfig
import com.example.movieapp.config.LanguageManager
import com.example.movieapp.data.network.ApiService
import com.example.movieapp.ui.auth.LoginActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * main activity con bottom navigation e logo nella toolbar
 */
class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //applica lingua salvata all'avvio
        LanguageManager.applyLanguage(this)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //inizializza servizi
        initializeServices()

        //setup navigation
        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        binding.navView.setupWithNavController(navController)

        //setup menu click nella toolbar
        setupToolbarMenu()

        //inizializza database al primo avvio
        initializeAppDatabase()

        Log.d(TAG, "mainactivity creata con successo")
    }

    /**
     * setup menu nella toolbar
     */
    private fun setupToolbarMenu() {
        val menuIcon = findViewById<ImageView>(R.id.toolbar_menu)
        menuIcon?.setOnClickListener {
            showAccountMenu()
        }
    }

    /**
     * mostra menu account con opzioni migrate e visibili
     */
    private fun showAccountMenu() {
        val currentUser = ApiService.getCurrentUser()

        val displayName = when {
            currentUser != null && !currentUser.username.isNullOrBlank() -> currentUser.username
            currentUser != null -> currentUser.email.substringBefore("@")
            else -> "Utente"
        }

        val items = arrayOf(
            getString(R.string.account_info),
            getString(R.string.language),
            getString(R.string.logout)
        )

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.hello_user, displayName))
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showAccountInfo()
                    1 -> showLanguageDialog()
                    2 -> showLogoutDialog()
                }
            }
            .show()
    }

    /**
     * mostra informazioni account
     */
    private fun showAccountInfo() {
        val currentUser = ApiService.getCurrentUser()

        if (currentUser != null) {
            val message = buildString {
                append(getString(R.string.your_account))
                append("\n\n")
                if (!currentUser.username.isNullOrBlank()) {
                    append(getString(R.string.name))
                    append(" ${currentUser.username}\n")
                }
                append(getString(R.string.email))
                append(" ${currentUser.email}\n\n")
                append(getString(R.string.app_version_label))
                append(" ${AppConfig.APP_VERSION}")
            }

            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.account_info))
                .setMessage(message)
                .setPositiveButton(getString(R.string.ok), null)
                .show()
        }
    }

    /**
     * mostra dialog selezione lingua
     */
    private fun showLanguageDialog() {
        val languages = LanguageManager.SUPPORTED_LANGUAGES.values.toTypedArray()
        val languageCodes = LanguageManager.SUPPORTED_LANGUAGES.keys.toTypedArray()

        //ottieni lingua corrente salvata
        val currentLanguage = LanguageManager.getSavedLanguage(this)
        val currentIndex = languageCodes.indexOf(currentLanguage).takeIf { it >= 0 } ?: 0

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.select_language))
            .setSingleChoiceItems(languages, currentIndex) { dialog, which ->
                val selectedLanguage = languageCodes[which]
                changeLanguage(selectedLanguage)
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    /**
     * cambia lingua applicazione e salva la scelta
     */
    private fun changeLanguage(languageCode: String) {
        LanguageManager.applyLanguage(this, languageCode)
        Log.d(TAG, "lingua cambiata in: $languageCode")
    }

    /**
     * mostra dialog di conferma logout
     */
    private fun showLogoutDialog() {
        val currentUser = ApiService.getCurrentUser()

        val displayName = when {
            currentUser != null && !currentUser.username.isNullOrBlank() -> currentUser.username
            currentUser != null -> currentUser.email.substringBefore("@")
            else -> "il tuo account"
        }

        val message = getString(R.string.logout_confirm_message)

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.logout_confirm_title))
            .setMessage(message)
            .setPositiveButton(getString(R.string.logout_button)) { _, _ ->
                performLogout()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    /**
     * esegue logout completo
     */
    private fun performLogout() {
        Log.d(TAG, "logout in corso...")

        ApiService.logout()

        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()

        Log.d(TAG, "logout completato")
    }

    /**
     * inizializza tutti i servizi critici
     */
    private fun initializeServices() {
        try {
            Log.d(TAG, "inizializzazione servizi...")

            ApiService.initialize(applicationContext)
            Log.d(TAG, "apiservice inizializzato")

            testBackendConnection()

            Log.d(TAG, "tutti i servizi inizializzati")

        } catch (e: Exception) {
            Log.e(TAG, "errore inizializzazione servizi", e)
        }
    }

    /**
     * inizializza database film popolari al primo avvio
     */
    private fun initializeAppDatabase() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "controllo inizializzazione database...")

                val result = ApiService.initializeApp()

                if (result.isSuccess) {
                    val data = result.getOrNull()
                    val needsSync = data?.get("needsSync") as? Boolean ?: false
                    val moviesInDb = data?.get("moviesInDb") as? Double ?: 0.0
                    val message = data?.get("message") as? String ?: ""

                    withContext(Dispatchers.Main) {
                        Log.d(TAG, "initialize app completato")
                        Log.d(TAG, "needs sync: $needsSync")
                        Log.d(TAG, "movies in db: ${moviesInDb.toInt()}")
                        Log.d(TAG, "message: $message")

                        if (needsSync) {
                            Log.i(TAG, "sincronizzazione film popolari avviata in background")
                        } else {
                            Log.i(TAG, "database gia inizializzato con ${moviesInDb.toInt()} film")
                        }
                    }
                } else {
                    val error = result.exceptionOrNull()
                    Log.w(TAG, "errore initialize app: ${error?.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "errore initialize database", e)
            }
        }
    }

    /**
     * test connessione backend
     */
    private fun testBackendConnection() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val isConnected = ApiService.testConnection()

                withContext(Dispatchers.Main) {
                    if (isConnected) {
                        Log.i(TAG, "backend ${AppConfig.BACKEND_HOST}:${AppConfig.BACKEND_PORT} raggiungibile")
                    } else {
                        Log.w(TAG, "backend ${AppConfig.BACKEND_HOST}:${AppConfig.BACKEND_PORT} non raggiungibile")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "errore test backend", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "mainactivity distrutta")
    }
}