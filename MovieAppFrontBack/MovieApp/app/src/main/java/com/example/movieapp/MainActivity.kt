package com.example.movieapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.movieapp.databinding.ActivityMainBinding
import com.example.movieapp.config.AppConfig
import com.example.movieapp.data.network.ApiService
import com.example.movieapp.data.cache.CacheService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * main activity con bottom navigation che inizializza tutti i servizi app
 * include menu logout e info account nella toolbar
 */
class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //setup toolbar
        setSupportActionBar(binding.toolbar)

        //setup navigation
        val navView: BottomNavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_activity_main)

        //top level destinations
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home,
                R.id.navigation_dashboard,
                R.id.navigation_notifications
            )
        )

        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        //aggiorna titolo toolbar per ogni destinazione
        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.toolbar.title = when (destination.id) {
                R.id.navigation_home -> "🎬 MovieApp"
                R.id.navigation_dashboard -> "📊 Info"
                R.id.navigation_notifications -> "📈 Statistiche"
                else -> getString(R.string.app_name)
            }
        }

        //inizializza servizi app
        initializeServices()

        Log.d(TAG, "=== movieapp v${AppConfig.APP_VERSION} avviata ===")
        Log.d(TAG, "backend: ${AppConfig.BACKEND_HOST}:${AppConfig.BACKEND_PORT}")
    }

    /**
     * crea menu nella toolbar con opzioni account e logout
     */
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    /**
     * gestisce click su menu items
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_account -> {
                showAccountInfo()
                true
            }
            R.id.action_logout -> {
                showLogoutDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * mostra informazioni account utente in modo semplice
     */
    private fun showAccountInfo() {
        val currentUser = ApiService.getCurrentUser()

        if (currentUser != null) {
            //determina nome visualizzato
            val displayName = when {
                !currentUser.username.isNullOrBlank() -> currentUser.username
                else -> currentUser.email.substringBefore("@")
            }

            val info = buildString {
                appendLine("Il tuo account:")
                appendLine()

                //username se presente
                if (!currentUser.username.isNullOrBlank()) {
                    appendLine("Nome: ${currentUser.username}")
                }

                //email
                appendLine("Email: ${currentUser.email}")
                appendLine()
                appendLine("Versione app: ${AppConfig.APP_VERSION}")
            }

            MaterialAlertDialogBuilder(this)
                .setTitle("Ciao $displayName!")
                .setMessage(info)
                .setPositiveButton("OK", null)
                .show()
        } else {
            //fallback se non c'e' utente loggato
            MaterialAlertDialogBuilder(this)
                .setTitle("Account")
                .setMessage("Nessun account attivo.\n\nEffettua il login per continuare.")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    /**
     * mostra dialog di conferma logout
     */
    private fun showLogoutDialog() {
        val currentUser = ApiService.getCurrentUser()

        //determina nome visualizzato per dialog
        val displayName = when {
            currentUser != null && !currentUser.username.isNullOrBlank() -> currentUser.username
            currentUser != null -> currentUser.email.substringBefore("@")
            else -> "il tuo account"
        }

        val message = buildString {
            appendLine("Vuoi disconnetterti da $displayName?")
            appendLine()
            appendLine("I tuoi film rimarranno salvati.")
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Disconnessione")
            .setMessage(message)
            .setPositiveButton("Disconnetti") { _, _ ->
                performLogout()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    /**
     * esegue logout completo
     * pulisce token jwt e user info
     * torna a loginactivity
     */
    private fun performLogout() {
        Log.d(TAG, "logout in corso...")

        //logout da apiservice - pulisce token e user
        ApiService.logout()

        //naviga a loginactivity
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

            //api service
            ApiService.initialize(applicationContext)
            Log.d(TAG, "apiservice inizializzato")

            //cache service
            CacheService.getInstance(applicationContext).init(applicationContext)
            Log.d(TAG, "cacheservice inizializzato")

            //test backend connectivity
            testBackendConnection()

            Log.d(TAG, "tutti i servizi inizializzati")

        } catch (e: Exception) {
            Log.e(TAG, "errore inizializzazione servizi", e)
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