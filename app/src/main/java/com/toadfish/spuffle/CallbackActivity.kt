package com.toadfish.spuffle

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Receives the OAuth redirect, extracts the code, and passes it to MainActivity.
 */
class CallbackActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val uri = intent.data
        if (uri != null && uri.scheme == "spuffle") {
            val code = uri.getQueryParameter("code")
            val error = uri.getQueryParameter("error")

            val mainIntent = Intent(this, MainActivity::class.java).apply {
                putExtra("auth_code", code)
                putExtra("auth_error", error)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(mainIntent)
        }
        finish()
    }
}