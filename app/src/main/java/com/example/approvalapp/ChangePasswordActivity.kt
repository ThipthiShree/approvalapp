package com.example.approvalapp

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ChangePasswordActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_password)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        val etCurrent = findViewById<EditText>(R.id.etCurrentPassword)
        val etNew     = findViewById<EditText>(R.id.etNewPassword)
        val etConfirm = findViewById<EditText>(R.id.etConfirmPassword)

        findViewById<Button>(R.id.btnUpdatePassword).setOnClickListener {
            val current = etCurrent.text.toString()
            val newPass = etNew.text.toString()
            val confirm = etConfirm.text.toString()

            when {
                current.isEmpty() -> etCurrent.error = "Enter current password"
                newPass.isEmpty()  -> etNew.error     = "Enter new password"
                newPass.length < 6 -> etNew.error     = "Minimum 6 characters"
                newPass != confirm -> etConfirm.error = "Passwords do not match"
                else -> {
                    Toast.makeText(this, "Password updated successfully", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }
}