package com.example.approvalapp

import android.animation.ObjectAnimator
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        auth = FirebaseAuth.getInstance()

        val etUsername =
            findViewById<EditText>(R.id.etForgotUsername)

        val btnSend =
            findViewById<Button>(R.id.btnSendReset)

        val tvStatus =
            findViewById<TextView>(R.id.tvResetStatus)

        val btnBack =
            findViewById<LinearLayout>(R.id.btnBackToLogin)

        // Pre-fill email from login page
        val prefill =
            intent.getStringExtra("prefill_username")

        if (!prefill.isNullOrEmpty()) {
            etUsername.setText(prefill)
        }

        // Back button
        btnBack.setOnClickListener {
            finish()
        }

        // Send reset email
        btnSend.setOnClickListener {

            val email =
                etUsername.text.toString().trim()

            if (email.isEmpty()) {
                etUsername.error =
                    "Please enter your email"
                return@setOnClickListener
            }

            tvStatus.visibility = View.VISIBLE

            auth.sendPasswordResetEmail(email)
                .addOnCompleteListener { task ->

                    if (task.isSuccessful) {

                        tvStatus.setTextColor(
                            Color.parseColor("#4CAF50")
                        )

                        tvStatus.text =
                            "✔ Reset link sent! Check your email."

                        btnSend.isEnabled = false

                    } else {

                        tvStatus.setTextColor(
                            Color.parseColor("#E53935")
                        )

                        tvStatus.text =
                            task.exception?.localizedMessage
                                ?: "Failed to send reset email"
                    }
                }
        }

        startAnimations()
    }

    private fun startAnimations() {

        findViewById<ImageView>(R.id.fpRing1)?.let {
            ObjectAnimator.ofFloat(
                it,
                "rotation",
                0f,
                360f
            ).apply {
                duration = 20000
                repeatCount = ObjectAnimator.INFINITE
                interpolator = LinearInterpolator()
                start()
            }
        }

        findViewById<ImageView>(R.id.fpRing2)?.let {
            ObjectAnimator.ofFloat(
                it,
                "rotation",
                360f,
                0f
            ).apply {
                duration = 13000
                repeatCount = ObjectAnimator.INFINITE
                interpolator = LinearInterpolator()
                start()
            }
        }

        findViewById<ImageView>(R.id.fpGear)?.let {
            ObjectAnimator.ofFloat(
                it,
                "rotation",
                0f,
                360f
            ).apply {
                duration = 17000
                repeatCount = ObjectAnimator.INFINITE
                interpolator = LinearInterpolator()
                start()
            }
        }
    }
}