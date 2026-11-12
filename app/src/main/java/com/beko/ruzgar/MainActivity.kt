package com.beko.ruzgar

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
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
        ac(intent)
    }

    override fun onNewIntent(i: Intent) { super.onNewIntent(i); ac(i) }

    // A pairing link (from the Mac QR) opens straight in the app; the page reads the #e= part and saves it.
    private fun ac(i: Intent?) {
        val u = i?.data?.toString()
        if (u != null && u.startsWith(SAYFA) && u.contains("#e=")) { web.loadUrl(u); web.postDelayed({ web.reload() }, 700) }
        else if (web.url == null) web.loadUrl(SAYFA)
    }

    override fun onActivityResult(rq: Int, rs: Int, d: Intent?) {
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
        @JavascriptInterface fun balonDurum(): Boolean = balonAcik(this@MainActivity) && Settings.canDrawOverlays(this@MainActivity)
        @JavascriptInterface fun balon(ac: Boolean) = runOnUiThread {
            getSharedPreferences("ruzgar", 0).edit().putBoolean("balon", ac).apply()
            if (ac && !Settings.canDrawOverlays(this@MainActivity))
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
    }
}
