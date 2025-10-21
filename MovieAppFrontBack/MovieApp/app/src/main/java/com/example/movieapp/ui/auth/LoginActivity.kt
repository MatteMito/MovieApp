package com.example.movieapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.movieapp.config.AppConfig
import com.example.movieapp.databinding.ActivityLoginBinding
import com.example.movieapp.data.network.ApiService
import kotlinx.coroutines.launch

/**
 * Activity di Login
 *
 * Utilizza ApiService per autenticazione
 */
class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private val TAG = "LoginActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inizializza ApiService
        ApiService.initialize(this)

        // Controlla se già autenticato
        if (ApiService.isAuthenticated()) {
            Log.d(TAG, "Utente già autenticato")
            navigateToMain()
            return
        }

        setupUI()
        Log.d(TAG, "LoginActivity creata")
        Log.d(TAG, "Backend: ${AppConfig.BACKEND_HOST}:${AppConfig.BACKEND_PORT}")
    }

    private fun setupUI() {
        binding.buttonLogin.setOnClickListener {
            val email = binding.editEmail.text.toString().trim()
            val password = binding.editPassword.text.toString().trim()

            if (validateInput(email, password)) {
                performLogin(email, password)
            }
        }

        binding.textRegister.setOnClickListener {
            Log.d(TAG, "Navigazione a RegisterActivity")
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun validateInput(email: String, password: String): Boolean {
        if (email.isEmpty()) {
            Toast.makeText(this, "Inserisci l'email", Toast.LENGTH_SHORT).show()
            return false
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Email non valida", Toast.LENGTH_SHORT).show()
            return false
        }

        if (password.isEmpty()) {
            Toast.makeText(this, "Inserisci la password", Toast.LENGTH_SHORT).show()
            return false
        }

        if (password.length < 6) {
            Toast.makeText(this, "Password troppo corta (min 6 caratteri)", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun performLogin(email: String, password: String) {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Tentativo login: $email")
                setLoading(true)

                val result = ApiService.login(email, password)

                if (result.isSuccess) {
                    val authResponse = result.getOrNull()!!
                    Log.d(TAG, "✅ Login riuscito")

                    val welcomeName = authResponse.user.username ?: authResponse.user.email.substringBefore("@")
                    Toast.makeText(
                        this@LoginActivity,
                        "Benvenuto $welcomeName!",
                        Toast.LENGTH_SHORT
                    ).show()

                    navigateToMain()
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Accesso non riuscito"
                    Log.e(TAG, "❌ Login fallito: $error")
                    Toast.makeText(this@LoginActivity, error, Toast.LENGTH_LONG).show()
                    setLoading(false)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Eccezione login", e)
                Toast.makeText(
                    this@LoginActivity,
                    "Impossibile accedere. Riprova.",
                    Toast.LENGTH_SHORT
                ).show()
                setLoading(false)
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.apply {
            progressBar.visibility = if (loading) View.VISIBLE else View.GONE
            buttonLogin.isEnabled = !loading
            editEmail.isEnabled = !loading
            editPassword.isEnabled = !loading
            textRegister.isEnabled = !loading
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}