package com.example.approvalapp

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.animation.LinearInterpolator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import androidx.appcompat.app.AppCompatDelegate

class LoginPage : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private var isPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isDark = getSharedPreferences("AppSettings", MODE_PRIVATE)
            .getBoolean("dark_mode", true)
        AppCompatDelegate.setDefaultNightMode(
            if (isDark) AppCompatDelegate.MODE_NIGHT_YES
            else        AppCompatDelegate.MODE_NIGHT_NO
        )
        setContentView(R.layout.activity_login_page)

        auth = FirebaseAuth.getInstance()

        val loginButton = findViewById<Button>(R.id.loginButton)
        val etUsername  = findViewById<EditText>(R.id.etUsername)
        val etPassword  = findViewById<EditText>(R.id.etPassword)
        val btnToggle   = findViewById<ImageButton>(R.id.btnTogglePassword)
        val cbShow      = findViewById<CheckBox>(R.id.cbShowPassword)
        val tvForgot    = findViewById<TextView>(R.id.tvForgotPassword)

        // ================= LOGIN =================
        loginButton.setOnClickListener {
            val email    = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Enter email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {

                        // ── Save username so MainActivity can show it ──
                        getSharedPreferences("UserPrefs", MODE_PRIVATE)
                            .edit()
                            .putString("username", email)
                            .apply()

                        // ── Save email so SettingsActivity can show it ──
                        getSharedPreferences("AppSettings", MODE_PRIVATE)
                            .edit()
                            .putString("logged_in_user", email)
                            .apply()

                        Toast.makeText(this, "Login Successful", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()

                    } else {
                        Toast.makeText(this, "Invalid Login", Toast.LENGTH_SHORT).show()
                    }
                }
        }

        // ================= PASSWORD TOGGLE =================
        btnToggle.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            if (isPasswordVisible) {
                etPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
                btnToggle.setImageResource(R.drawable.ic_eye_on)
            } else {
                etPassword.transformationMethod = PasswordTransformationMethod.getInstance()
                btnToggle.setImageResource(R.drawable.ic_eye_off)
            }
            cbShow.isChecked = isPasswordVisible
            etPassword.setSelection(etPassword.text.length)
        }

        // ================= CHECKBOX SHOW PASSWORD =================
        cbShow.setOnCheckedChangeListener { _, isChecked ->
            isPasswordVisible = isChecked
            if (isChecked) {
                etPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
                btnToggle.setImageResource(R.drawable.ic_eye_on)
            } else {
                etPassword.transformationMethod = PasswordTransformationMethod.getInstance()
                btnToggle.setImageResource(R.drawable.ic_eye_off)
            }
            etPassword.setSelection(etPassword.text.length)
        }

        // ================= FORGOT PASSWORD PAGE =================
        tvForgot.setOnClickListener {
            val intent = Intent(this, ForgotPasswordActivity::class.java)
            val email  = etUsername.text.toString().trim()
            if (email.isNotEmpty()) intent.putExtra("prefill_email", email)
            startActivity(intent)
        }

        startCyberAnimations()
    }

    private fun startCyberAnimations() {
        fun spin(id: Int, dur: Long, reverse: Boolean = false) {
            findViewById<ImageView>(id)?.let {
                ObjectAnimator.ofFloat(it, "rotation",
                    if (reverse) 360f else 0f,
                    if (reverse) 0f   else 360f
                ).apply {
                    duration     = dur
                    repeatCount  = ObjectAnimator.INFINITE
                    interpolator = LinearInterpolator()
                    start()
                }
            }
        }
        spin(R.id.cyberRing1,  22000)
        spin(R.id.cyberRing2,  14000, reverse = true)
        spin(R.id.cyberRing3,  18000)
        spin(R.id.cyberGear1,  16000)
        spin(R.id.cyberGear2,  10000, reverse = true)
    }
}