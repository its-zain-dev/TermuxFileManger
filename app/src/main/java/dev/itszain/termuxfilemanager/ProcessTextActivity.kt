package dev.itszain.termuxfilemanager

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** Route selected text to the same reviewed command flow as every other screen. */
class ProcessTextActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val code = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty()
        if(code.isNotBlank()) startActivity(Intent(this, MainActivity::class.java).putExtra("shared_command", code))
        finish()
    }
}
