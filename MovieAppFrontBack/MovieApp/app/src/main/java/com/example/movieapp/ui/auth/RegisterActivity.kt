package com.example.movieapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.movieapp.config.AppConfig
import com.example.movieapp.databinding.ActivityRegisterBinding
import com.example.movieapp.data.network.ApiService
import kotlinx.coroutines.launch

/**
 * Activity di Registrazione
 *
 * Utilizza ApiService per registrazione
 */
class RegisterActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRegisterBinding
    private val TAG = "RegisterActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ApiService.initialize(this)

        setupUI()
        Log.d(TAG, "RegisterActivity creata")
        Log.d(TAG, "Backend: ${AppConfig.BACKEND_HOST}:${AppConfig.BACKEND_PORT}")
    }

    private fun setupUI() {
        binding.buttonRegister.setOnClickListener {
            val email = binding.editEmail.text.toString().trim()
            val username = binding.editUsername.text.toString().trim().ifEmpty { null }
            val password = binding.editPassword.text.toString().trim()
            val confirmPassword = binding.editConfirmPassword.text.toString().trim()

            if (validateInput(email, password, confirmPassword)) {
                performRegister(email, password, username)
            }
        }

        binding.textLogin.setOnClickListener {
            Log.d(TAG, "Torna a LoginActivity")
            finish()
        }
    }

    private fun validateInput(email: String, password: String, confirmPassword: String): Boolean {
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

        if (password != confirmPassword) {
            Toast.makeText(this, "Le password non corrispondono", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun performRegister(email: String, password: String, username: String?) {
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            binding.buttonRegister.isEnabled = false

            val result = ApiService.register(email, password, username)

            binding.progressBar.visibility = View.GONE
            binding.buttonRegister.isEnabled = true

            if (result.isSuccess) {
                Log.d(TAG, "✅ Registrazione completata con successo")

                // 🆕 IMPORTANTE: Reinizializza ApiService per caricare i dati utente
                ApiService.initialize(applicationContext)

                // 🔍 Verifica che l'utente sia stato caricato
                val userId = ApiService.getCurrentUserId()
                Log.d(TAG, "🔍 Dopo re-init: userId = $userId")

                if (userId != null) {
                    Toast.makeText(this@RegisterActivity, "Account creato!", Toast.LENGTH_SHORT).show()

                    // Naviga alla MainActivity
                    val intent = Intent(this@RegisterActivity, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                } else {
                    Log.e(TAG, "❌ UserId ancora NULL dopo re-init!")
                    Toast.makeText(
                        this@RegisterActivity,
                        "Errore caricamento dati utente",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                val error = result.exceptionOrNull()?.message ?: "Errore registrazione"
                Toast.makeText(this@RegisterActivity, error, Toast.LENGTH_LONG).show()
                Log.e(TAG, "❌ Registrazione fallita: $error")
            }
        }
    }
}