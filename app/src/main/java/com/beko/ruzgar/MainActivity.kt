package com.beko.ruzgar

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import org.json.JSONObject
import java.util.Locale
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

// Ruzgar: the phone face of Beko's Mac assistant.
// The page itself (end-to-end encrypted chat) lives at SAYFA; this app adds:
// in-app QR pairing, photo picking, and a floating bubble when the app is in the background.
const val SAYFA = "https://bekoas.github.io/ruzgar/"

fun balonAcik(c: Context) = c.getSharedPreferences("ruzgar", 0).getBoolean("balon", true)

class MainActivity : Activity() {
    private lateinit var web: WebView
    private var dosyaGeriDonus: ValueCallback<Array<Uri>>? = null
    private var tts: TextToSpeech? = null
    private var sesHazir = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        web = WebView(this)
        web.setBackgroundColor(0xFF14131F.toInt())
        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = true
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                if (r.url.toString().startsWith(SAYFA)) return false
                startActivity(Intent(Intent.ACTION_VIEW, r.url)); return true   // other links: open in the browser
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(v: WebView, cb: ValueCallback<Array<Uri>>, p: FileChooserParams): Boolean {
                dosyaGeriDonus?.onReceiveValue(null); dosyaGeriDonus = cb
                return try { startActivityForResult(p.createIntent(), 7); true } catch (e: Exception) { dosyaGeriDonus = null; false }
            }
        }
        web.addJavascriptInterface(Kopru(), "RuzgarAndroid")
        setContentView(web)
        tts = TextToSpeech(this) { d -> if (d == TextToSpeech.SUCCESS) { tts?.language = Locale("tr", "TR"); sesHazir = true } }
        ac(intent)
        if (asistanMi(intent)) web.postDelayed({ dinle() }, 900)
    }

    // Pairing without QR: copy the pairing link (from the Mac/chat), open the app -> it pairs itself,
    // then the clipboard is cleared so the key does not linger there.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return
        val pano = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val metin = pano.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()?.trim() ?: return
        if (!metin.startsWith(SAYFA) || !metin.contains("#e=")) return
        web.loadUrl(metin); web.postDelayed({ web.reload() }, 700)
        runCatching { pano.setPrimaryClip(ClipData.newPlainText("", "")) }
        Toast.makeText(this, "R\u00fczgar e\u015fle\u015fti \u2714", Toast.LENGTH_LONG).show()
    }

    override fun onNewIntent(i: Intent) { super.onNewIntent(i); ac(i); if (asistanMi(i)) dinle() }

    // Opened as the phone's assistant (long-press power / assist gesture) -> listen right away.
    private fun asistanMi(i: Intent?) = i?.action == Intent.ACTION_ASSIST || i?.action == "android.intent.action.VOICE_COMMAND"

    // Speech -> text (Turkish) using the phone's own recognizer; the text goes straight to Ruzgar.
    fun dinle() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "R\u00fczgar dinliyor\u2026")
        try { startActivityForResult(i, 9) } catch (e: Exception) {
            Toast.makeText(this, "Konu\u015fma tan\u0131ma bulunamad\u0131 (Google uygulamas\u0131 gerekli)", Toast.LENGTH_LONG).show() }
    }

    fun oku(t: String) { if (sesHazir) tts?.speak(t, TextToSpeech.QUEUE_FLUSH, null, "ruzgar") }

    override fun onDestroy() { tts?.shutdown(); super.onDestroy() }

    // A pairing link (from the Mac QR) opens straight in the app; the page reads the #e= part and saves it.
    private fun ac(i: Intent?) {
        val u = i?.data?.toString()
        if (u != null && u.startsWith(SAYFA) && u.contains("#e=")) { web.loadUrl(u); web.postDelayed({ web.reload() }, 700) }
        else if (web.url == null) web.loadUrl(SAYFA)
    }

    override fun onActivityResult(rq: Int, rs: Int, d: Intent?) {
        if (rq == 9) {
            val t = d?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (rs == RESULT_OK && !t.isNullOrBlank()) web.evaluateJavascript("window.sesliGonder && window.sesliGonder(" + JSONObject.quote(t) + ")", null)
            return
        }
        if (rq == 7) { dosyaGeriDonus?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(rs, d)); dosyaGeriDonus = null }
        else super.onActivityResult(rq, rs, d)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if (web.canGoBack()) web.goBack() else moveTaskToBack(true) }

    override fun onStart() { super.onStart(); BalonServisi.durdur(this) }          // app visible: hide the bubble
    override fun onStop() {                                                         // app hidden: show the bubble
        super.onStop()
        if (balonAcik(this) && Settings.canDrawOverlays(this) && !isFinishing) runCatching { BalonServisi.baslat(this) }
    }

    inner class Kopru {
        @JavascriptInterface fun qrOkut() = runOnUiThread {
            val secenek = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
            GmsBarcodeScanning.getClient(this@MainActivity, secenek).startScan()
                .addOnSuccessListener { kod ->
                    val u = kod.rawValue ?: ""
                    if (u.startsWith(SAYFA) && u.contains("#e=")) { web.loadUrl(u); web.postDelayed({ web.reload() }, 700) }
                    else Toast.makeText(this@MainActivity, "Bu R\u00fczgar e\u015fle\u015ftirme QR'\u0131 de\u011fil", Toast.LENGTH_LONG).show()
                }
        }
        @JavascriptInterface fun dinle() = runOnUiThread { this@MainActivity.dinle() }
        @JavascriptInterface fun oku(t: String) = runOnUiThread { this@MainActivity.oku(t) }
        @JavascriptInterface fun balonDurum(): Boolean = balonAcik(this@MainActivity) && Settings.canDrawOverlays(this@MainActivity)
        @JavascriptInterface fun balon(ac: Boolean) = runOnUiThread {
            getSharedPreferences("ruzgar", 0).edit().putBoolean("balon", ac).apply()
            if (ac && !Settings.canDrawOverlays(this@MainActivity))
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
    }
}
