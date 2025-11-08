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

/**
 * activity di login
 *
 * utilizza apiservice per autenticazione
 */
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
            Log.d(TAG, "Navigazione a RegisterActivity")
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
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

        return true
    }

    private fun performLogin(email: String, password: String) {
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            binding.buttonLogin.isEnabled = false

            val result = ApiService.login(email, password)

            binding.progressBar.visibility = View.GONE
            binding.buttonLogin.isEnabled = true

            if (result.isSuccess) {
                Log.d(TAG, "login completato con successo")

                //reinizializza apiservice per caricare i dati utente
                ApiService.initialize(applicationContext)

                //verifica che l'utente sia stato caricato
                val userId = ApiService.getCurrentUserId()
                Log.d(TAG, "dopo re-init: userId = $userId")

                if (userId != null) {
                    Toast.makeText(this@LoginActivity, "Benvenuto!", Toast.LENGTH_SHORT).show()

                    //naviga alla mainactivity
                    val intent = Intent(this@LoginActivity, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                } else {
                    Log.e(TAG, "userid ancora null dopo re-init")
                    Toast.makeText(
                        this@LoginActivity,
                        "Errore caricamento dati utente",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                val error = result.exceptionOrNull()?.message ?: "Errore login"
                Toast.makeText(this@LoginActivity, error, Toast.LENGTH_LONG).show()
                Log.e(TAG, "login fallito: $error")
            }
        }
    }
}