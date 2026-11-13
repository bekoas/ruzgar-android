package com.beko.ruzgar

import android.annotation.SuppressLint
import android.Manifest
import android.app.AlertDialog
import android.os.Build
import android.provider.OpenableColumns
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
import org.json.JSONArray
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
    private var sohbet = false            // hands-free conversation
    private var bekleyenPaylasim: String? = null   // shared text/files waiting for the page to load

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
            override fun onPageFinished(v: WebView, u: String?) { bekleyenPaylasim?.let { paylasimVer(it) } }
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
        // after an app update, drop the cached page once so the newest version loads right away
        val ayar = getSharedPreferences("ruzgar", 0); val surum = packageManager.getPackageInfo(packageName, 0).longVersionCode
        if (ayar.getLong("sayfaSurum", 0) != surum) { web.clearCache(true); ayar.edit().putLong("sayfaSurum", surum).apply() }
        web.addJavascriptInterface(Kopru(), "RuzgarAndroid")
        // Android 15 draws apps edge-to-edge: keep the page clear of the status bar, nav bar and keyboard
        val kap = android.widget.FrameLayout(this).apply { setBackgroundColor(0xFF211F3A.toInt()); addView(web) }
        setContentView(kap)
        if (Build.VERSION.SDK_INT >= 30) kap.setOnApplyWindowInsetsListener { v, i ->
            val b = i.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.ime())
            v.setPadding(b.left, b.top, b.right, b.bottom); android.view.WindowInsets.CONSUMED }
        tts = TextToSpeech(this) { d -> if (d == TextToSpeech.SUCCESS) { tts?.language = Locale("tr", "TR"); sesHazir = true } }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            @Deprecated("") override fun onError(id: String?) {}
            override fun onDone(id: String?) { if (sohbet) web.postDelayed({ if (sohbet && hasWindowFocus()) dinle() }, 450) }
        })
        izinleriSor()
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

    override fun onNewIntent(i: Intent) { super.onNewIntent(i); setIntent(i); ac(i); if (asistanMi(i)) dinle() }

    // First run / after updates: the bubble needs "display over other apps"; ask clearly instead of failing silently.
    private fun izinleriSor() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 3)
        val ayar = getSharedPreferences("ruzgar", 0)
        if (!balonAcik(this) || Settings.canDrawOverlays(this) || ayar.getLong("izinSoruldu", 0) > System.currentTimeMillis() - 6 * 3600_000) return
        ayar.edit().putLong("izinSoruldu", System.currentTimeMillis()).apply()
        AlertDialog.Builder(this)
            .setTitle("R\u00fczgar balonu")
            .setMessage("Balonun ana ekranda ve her uygulaman\u0131n \u00fcst\u00fcnde g\u00f6r\u00fcnmesi i\u00e7in bir izin gerekiyor.\n\nA\u00e7\u0131lan listede R\u00fczgar'\u0131 bul ve \"Di\u011fer uygulamalar\u0131n \u00fczerinde g\u00f6ster\" anahtar\u0131n\u0131 a\u00e7.")
            .setPositiveButton("\u0130zin ver") { _, _ -> startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }
            .setNegativeButton("\u015eimdi de\u011fil", null)
            .setNeutralButton("Balon istemiyorum") { _, _ -> ayar.edit().putBoolean("balon", false).apply() }
            .show()
    }

    // Shared from another app (Share -> Ruzgar): text goes into the box, files become attachments.
    private fun paylasim(i: Intent): String? {
        if (i.action != Intent.ACTION_SEND && i.action != Intent.ACTION_SEND_MULTIPLE) return null
        val o = JSONObject(); o.put("metin", (i.getStringExtra(Intent.EXTRA_TEXT) ?: "").take(6000))
        val uriler = ArrayList<Uri>()
        if (i.action == Intent.ACTION_SEND) (if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else @Suppress("DEPRECATION") i.getParcelableExtra(Intent.EXTRA_STREAM))?.let { uriler.add(it) }
        else (if (Build.VERSION.SDK_INT >= 33) i.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java) else @Suppress("DEPRECATION") i.getParcelableArrayListExtra(Intent.EXTRA_STREAM))?.let { uriler.addAll(it) }
        val dosyalar = dosyalarJson(this, uriler)
        o.put("dosyalar", dosyalar)
        return o.toString()
    }
    private fun paylasimVer(j: String) {
        web.evaluateJavascript("window.paylasim ? (window.paylasim($j), 1) : 0") { r -> if (r == "1") bekleyenPaylasim = null }
    }

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
        if (i?.action == "rehber") requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), 4)
        i?.let { paylasim(it) }?.let { bekleyenPaylasim = it; if (web.url != null) paylasimVer(it) }
        val u = i?.data?.toString()
        if (u != null && u.startsWith(SAYFA) && u.contains("#e=")) { web.loadUrl(u); web.postDelayed({ web.reload() }, 700) }
        else if (web.url == null) web.loadUrl(SAYFA)
    }

    override fun onActivityResult(rq: Int, rs: Int, d: Intent?) {
        if (rq == 9) {
            val t = d?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (rs != RESULT_OK || t.isNullOrBlank() || BalonServisi.BITIR.matches(t.trim().lowercase())) { sohbet = false; return }
            sohbet = true
            web.evaluateJavascript("window.sesliGonder && window.sesliGonder(" + JSONObject.quote(t) + ")", null)
            return
        }
        if (rq == 7) { dosyaGeriDonus?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(rs, d)); dosyaGeriDonus = null }
        else super.onActivityResult(rq, rs, d)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if (web.canGoBack()) web.goBack() else moveTaskToBack(true) }

    override fun onStart() { super.onStart(); BalonServisi.durdur(this) }          // app visible: hide the bubble
    override fun onStop() {
        sohbet = false                                                         // app hidden: show the bubble
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
        @JavascriptInterface fun oku(t: String) = runOnUiThread { if (sesHazir) this@MainActivity.oku(t) else sohbet = false }
        @JavascriptInterface fun sus() = runOnUiThread { sohbet = false; tts?.stop() }
        @JavascriptInterface fun eylem(j: String): String {
            val s = Eylem.yap(this@MainActivity, j)
            if (s == "REHBER_IZNI") { runOnUiThread { requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), 4) }
                return "Rehber iznini ver, sonra Onayla'ya tekrar bas" }
            return s
        }
        @JavascriptInterface fun balonDurum(): Boolean = balonAcik(this@MainActivity) && Settings.canDrawOverlays(this@MainActivity)
        @JavascriptInterface fun balon(ac: Boolean) = runOnUiThread {
            getSharedPreferences("ruzgar", 0).edit().putBoolean("balon", ac).apply()
            if (ac && !Settings.canDrawOverlays(this@MainActivity))
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
    }
}
