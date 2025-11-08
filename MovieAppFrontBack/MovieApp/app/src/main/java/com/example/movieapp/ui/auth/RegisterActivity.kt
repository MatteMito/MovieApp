package com.example.movieapp.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.movieapp.MainActivity
import com.example.movieapp.config.AppConfig
import com.example.movieapp.databinding.ActivityRegisterBinding
import com.example.movieapp.data.network.ApiService
import kotlinx.coroutines.launch

/**
 * activity di registrazione
 *
 * utilizza apiservice per registrazione
 */
class RegisterActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRegisterBinding
    private val TAG = "RegisterActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // view binding per accedere alle view del layout
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // inizializza apiservice con context per shared preferences
        ApiService.initialize(this)

        setupUI()
        Log.d(TAG, "RegisterActivity creata")
        Log.d(TAG, "Backend: ${AppConfig.BACKEND_HOST}:${AppConfig.BACKEND_PORT}")
    }

    private fun setupUI() {
        // listener bottone registrazione
        binding.buttonRegister.setOnClickListener {
            val email = binding.editEmail.text.toString().trim()
            // username opzionale, se vuoto viene settato a null
            val username = binding.editUsername.text.toString().trim().ifEmpty { null }
            val password = binding.editPassword.text.toString().trim()
            val confirmPassword = binding.editConfirmPassword.text.toString().trim()

            if (validateInput(email, password, confirmPassword)) {
                performRegister(email, password, username)
            }
        }

        // torna alla schermata di login
        binding.textLogin.setOnClickListener {
            Log.d(TAG, "Torna a LoginActivity")
            finish()
        }
    }

    // validazione completa dei campi di registrazione
    private fun validateInput(email: String, password: String, confirmPassword: String): Boolean {
        if (email.isEmpty()) {
            Toast.makeText(this, "Inserisci l'email", Toast.LENGTH_SHORT).show()
            return false
        }

        // verifica formato email valido
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Email non valida", Toast.LENGTH_SHORT).show()
            return false
        }

        if (password.isEmpty()) {
            Toast.makeText(this, "Inserisci la password", Toast.LENGTH_SHORT).show()
            return false
        }

        // password deve essere almeno 6 caratteri
        if (password.length < 6) {
            Toast.makeText(this, "Password troppo corta (min 6 caratteri)", Toast.LENGTH_SHORT).show()
            return false
        }

        // verifica che le due password coincidano
        if (password != confirmPassword) {
            Toast.makeText(this, "Le password non corrispondono", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    // esegue la registrazione chiamando l'api backend
    private fun performRegister(email: String, password: String, username: String?) {
        // coroutine per chiamata asincrona
        lifecycleScope.launch {
            // mostra progress bar e disabilita bottone durante registrazione
            binding.progressBar.visibility = View.VISIBLE
            binding.buttonRegister.isEnabled = false

            val result = ApiService.register(email, password, username)

            // nasconde progress bar e riabilita bottone
            binding.progressBar.visibility = View.GONE
            binding.buttonRegister.isEnabled = true

            if (result.isSuccess) {
                Log.d(TAG, "registrazione completata con successo")

                // reinizializza apiservice per caricare i dati utente appena registrato
                ApiService.initialize(applicationContext)

                // verifica che l'utente sia stato caricato correttamente
                val userId = ApiService.getCurrentUserId()
                Log.d(TAG, "dopo re-init: userId = $userId")

                if (userId != null) {
                    Toast.makeText(
                        this@RegisterActivity,
                        "Account creato con successo!",
                        Toast.LENGTH_SHORT
                    ).show()

                    // vai a mainactivity e chiudi tutte le activity precedenti
                    val intent = Intent(this@RegisterActivity, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                } else {
                    // errore: userid non caricato dopo registrazione
                    Log.e(TAG, "userid ancora null dopo re-init")
                    Toast.makeText(
                        this@RegisterActivity,
                        "Errore caricamento dati utente",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                val error = result.exceptionOrNull()?.message ?: "Errore registrazione"
                Toast.makeText(this@RegisterActivity, error, Toast.LENGTH_LONG).show()
                Log.e(TAG, "registrazione fallita: $error")
            }
        }
    }
}