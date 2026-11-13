package com.beko.ruzgar

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast

// Invisible helper: runs the phone's Turkish speech recognizer for the floating bubble,
// hands the text to the bubble's panel, then disappears.
class DinleAktivite : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "R\u00fczgar dinliyor\u2026")
        try { startActivityForResult(i, 9) } catch (e: Exception) {
            Toast.makeText(this, "Konu\u015fma tan\u0131ma bulunamad\u0131 (Google uygulamas\u0131 gerekli)", Toast.LENGTH_LONG).show()
            BalonServisi.ornek?.sesGeldi(null); finish()
        }
    }

    override fun onActivityResult(rq: Int, rs: Int, d: Intent?) {
        val t = if (rs == RESULT_OK) d?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() else null
        BalonServisi.ornek?.sesGeldi(t)
        finish(); overridePendingTransition(0, 0)
    }
}
