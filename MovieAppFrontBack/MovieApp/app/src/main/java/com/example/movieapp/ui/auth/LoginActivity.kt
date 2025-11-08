package com.example.movieapp.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.movieapp.MainActivity
import com.example.movieapp.R
import com.example.movieapp.config.AppConfig
import com.example.movieapp.data.network.ApiService
import com.example.movieapp.databinding.ActivityLoginBinding
import kotlinx.coroutines.launch

/**
 * activity per autenticazione utente
 * prima schermata dell'applicazione (launcher)
 * gestisce login e navigazione a registrazione
 */
class LoginActivity : AppCompatActivity() {

    private val TAG = "LoginActivity"
    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //inizializza view binding per accesso sicuro alle view
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //inizializza servizio api con context applicazione
        ApiService.initialize(this)

        //configura interfaccia utente e listener
        setupUI()

        Log.d(TAG, "loginactivity creata")
        Log.d(TAG, "backend: ${AppConfig.BACKEND_HOST}:${AppConfig.BACKEND_PORT}")
    }

    /**
     * configura listener per bottoni e link
     */
    private fun setupUI() {
        //bottone login principale
        binding.buttonLogin.setOnClickListener {
            val email = binding.editEmail.text.toString().trim()
            val password = binding.editPassword.text.toString().trim()

            //valida input prima di procedere
            if (validateInput(email, password)) {
                performLogin(email, password)
            }
        }

        //link per navigare a registrazione
        binding.textRegister.setOnClickListener {
            Log.d(TAG, "navigazione a registeractivity")
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }
    }

    /**
     * valida input email e password
     * mostra toast con errore specifico
     * @return true se input valido, false altrimenti
     */
    private fun validateInput(email: String, password: String): Boolean {
        //verifica email non vuota
        if (email.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_email_empty), Toast.LENGTH_SHORT).show()
            return false
        }

        //verifica formato email valido
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, getString(R.string.error_email_invalid), Toast.LENGTH_SHORT).show()
            return false
        }

        //verifica password non vuota
        if (password.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_password_empty), Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    /**
     * esegue login chiamando backend api
     * gestisce ui loading e navigazione su successo
     */
    private fun performLogin(email: String, password: String) {
        lifecycleScope.launch {
            //mostra loading e disabilita bottone
            binding.progressBar.visibility = View.VISIBLE
            binding.buttonLogin.isEnabled = false

            //chiama api login
            val result = ApiService.login(email, password)

            //nascondi loading e riabilita bottone
            binding.progressBar.visibility = View.GONE
            binding.buttonLogin.isEnabled = true

            if (result.isSuccess) {
                Log.d(TAG, "login completato con successo")

                //reinizializza apiservice per caricare token e dati utente
                ApiService.initialize(applicationContext)

                //verifica che userid sia stato caricato correttamente
                val userId = ApiService.getCurrentUserId()
                Log.d(TAG, "dopo re-init: userid = $userId")

                if (userId != null) {
                    //mostra messaggio benvenuto
                    Toast.makeText(
                        this@LoginActivity,
                        getString(R.string.login_success),
                        Toast.LENGTH_SHORT
                    ).show()

                    //naviga a mainactivity e chiudi login
                    val intent = Intent(this@LoginActivity, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                } else {
                    //errore caricamento dati utente
                    Log.e(TAG, "userid null dopo login")
                    Toast.makeText(
                        this@LoginActivity,
                        getString(R.string.error_login_failed),
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                //login fallito, mostra errore
                val errorMessage = result.exceptionOrNull()?.message
                    ?: getString(R.string.error_login_generic)

                Log.e(TAG, "errore login: $errorMessage")
                Toast.makeText(
                    this@LoginActivity,
                    getString(R.string.login_error),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * cleanup quando activity viene distrutta
     */
    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}