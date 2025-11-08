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
import com.example.movieapp.databinding.ActivityLoginBinding
import com.example.movieapp.data.network.ApiService
import kotlinx.coroutines.launch

//activity di login utilizza apiservice per autenticazione
class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private val TAG = "LoginActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ApiService.initialize(this)

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
            Log.d(TAG, "Vai a RegisterActivity")
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun validateInput(email: String, password: String): Boolean {
        if (email.isEmpty()) {
            Toast.makeText(this, "Inserisci l'email", Toast.LENGTH_SHORT).show()
            return false
        }

        if (password.isEmpty()) {
            Toast.makeText(this, "Inserisci la password", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun performLogin(email: String, password: String) {
        Log.d(TAG, "Tentativo login per: $email")
        showLoading(true)

        lifecycleScope.launch {
            try {
                val result = ApiService.login(email, password)

                if (result.isSuccess) {
                    Log.d(TAG, "Login riuscito!")
                    Toast.makeText(this@LoginActivity, "Accesso effettuato!", Toast.LENGTH_SHORT).show()

                    val intent = Intent(this@LoginActivity, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                } else {
                    val error = result.exceptionOrNull()
                    Log.e(TAG, "Login fallito: ${error?.message}")
                    Toast.makeText(
                        this@LoginActivity,
                        "Errore: ${error?.message ?: "Login fallito"}",
                        Toast.LENGTH_LONG
                    ).show()
                    showLoading(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Eccezione durante login", e)
                Toast.makeText(
                    this@LoginActivity,
                    "Errore di connessione: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                showLoading(false)
            }
        }
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.buttonLogin.isEnabled = !show
        binding.editEmail.isEnabled = !show
        binding.editPassword.isEnabled = !show
    }
}