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
            try {
                Log.d(TAG, "Tentativo registrazione: $email")
                setLoading(true)

                val result = ApiService.register(email, password, username)

                if (result.isSuccess) {
                    val authResponse = result.getOrNull()!!
                    Log.d(TAG, "✅ Registrazione riuscita")

                    val welcomeName = authResponse.user.username ?: authResponse.user.email.substringBefore("@")
                    Toast.makeText(
                        this@RegisterActivity,
                        "Account creato! Benvenuto $welcomeName!",
                        Toast.LENGTH_SHORT
                    ).show()

                    navigateToMain()
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Registrazione non riuscita"
                    Log.e(TAG, "❌ Registrazione fallita: $error")
                    Toast.makeText(this@RegisterActivity, error, Toast.LENGTH_LONG).show()
                    setLoading(false)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Eccezione registrazione", e)
                Toast.makeText(
                    this@RegisterActivity,
                    "Impossibile completare la registrazione",
                    Toast.LENGTH_SHORT
                ).show()
                setLoading(false)
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.apply {
            progressBar.visibility = if (loading) View.VISIBLE else View.GONE
            buttonRegister.isEnabled = !loading
            editEmail.isEnabled = !loading
            editUsername.isEnabled = !loading
            editPassword.isEnabled = !loading
            editConfirmPassword.isEnabled = !loading
            textLogin.isEnabled = !loading
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}