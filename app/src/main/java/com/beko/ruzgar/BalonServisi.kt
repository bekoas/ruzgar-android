package com.beko.ruzgar

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs

// Floating Ruzgar bubble, like the Mac one:
//  tap        -> a small chat panel opens on top of whatever app is open (tap again / X / Back closes it)
//  long-press -> listen right away (Siri-like); the answer shows in the panel and is read aloud
//  drag       -> move (remembered); drag to the bottom edge -> bubble closes
class BalonServisi : Service() {
    private lateinit var wm: WindowManager
    private var balon: ImageView? = null
    private var balonLp: WindowManager.LayoutParams? = null
    private var panel: View? = null
    private var panelLp: WindowManager.LayoutParams? = null
    private var web: WebView? = null
    private var panelAcik = false
    private var tts: TextToSpeech? = null
    private var sesHazir = false

    override fun onBind(i: Intent?) = null

    override fun onCreate() {
        super.onCreate(); ornek = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        tts = TextToSpeech(this) { d -> if (d == TextToSpeech.SUCCESS) { tts?.language = Locale("tr", "TR"); sesHazir = true } }
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        if (i?.action == "kapat") { getSharedPreferences("ruzgar", 0).edit().putBoolean("balon", false).apply(); stopSelf(); return START_NOT_STICKY }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("balon", "R\u00fczgar balonu", NotificationManager.IMPORTANCE_MIN))
        val kapat = PendingIntent.getService(this, 1, Intent(this, BalonServisi::class.java).setAction("kapat"), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, "balon")
            .setSmallIcon(R.drawable.ic_bildirim)
            .setContentTitle("R\u00fczgar balonu a\u00e7\u0131k")
            .setContentText("Dokun: sohbet \u00b7 Bas\u0131l\u0131 tut: sesli sor")
            .setContentIntent(acmaNiyeti())
            .addAction(Notification.Action.Builder(null, "Balonu kapat", kapat).build())
            .setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(1, n)
        if (balon == null && Settings.canDrawOverlays(this)) balonEkle()
        return START_STICKY
    }

    private fun acmaNiyeti(): PendingIntent = PendingIntent.getActivity(this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
        PendingIntent.FLAG_IMMUTABLE)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // ---------- bubble ----------
    private fun balonEkle() {
        val ayar = getSharedPreferences("ruzgar", 0)
        val boy = dp(64)
        val iv = ImageView(this).apply { setImageResource(R.drawable.ruzgar); elevation = 12f; contentDescription = "R\u00fczgar" }
        val lp = WindowManager.LayoutParams(boy, boy, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            x = ayar.getInt("x", resources.displayMetrics.widthPixels - boy - dp(10))
            y = ayar.getInt("y", (resources.displayMetrics.heightPixels * 0.6).toInt())
        }
        var sx = 0f; var sy = 0f; var lx = 0; var ly = 0; var tasindi = false; var uzunBasildi = false
        val uzun = Runnable { if (!tasindi) { uzunBasildi = true; iv.performHapticFeedback(0); sesliSor() } }
        iv.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    sx = e.rawX; sy = e.rawY; lx = lp.x; ly = lp.y; tasindi = false; uzunBasildi = false
                    iv.animate().scaleX(0.88f).scaleY(0.88f).setDuration(80).start()
                    iv.postDelayed(uzun, ViewConfiguration.getLongPressTimeout().toLong())
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - sx; val dy = e.rawY - sy
                    if (abs(dx) + abs(dy) > dp(8)) { tasindi = true; iv.removeCallbacks(uzun) }
                    if (tasindi) { lp.x = lx + dx.toInt(); lp.y = ly + dy.toInt(); wm.updateViewLayout(iv, lp) }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    iv.removeCallbacks(uzun)
                    iv.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
                    if (uzunBasildi) Unit
                    else if (!tasindi) panelDegistir()
                    else if (e.rawY > resources.displayMetrics.heightPixels - boy) { ayar.edit().putBoolean("balon", false).apply(); stopSelf() }
                    else { // snap to the nearest side like the Mac bubble
                        val w = resources.displayMetrics.widthPixels
                        lp.x = if (lp.x + boy / 2 < w / 2) dp(6) else w - boy - dp(6)
                        wm.updateViewLayout(iv, lp)
                        ayar.edit().putInt("x", lp.x).putInt("y", lp.y).apply()
                    }
                }
            }
            true
        }
        wm.addView(iv, lp); balon = iv; balonLp = lp
    }

    // ---------- chat panel ----------
    @SuppressLint("SetJavaScriptEnabled")
    private fun panelHazirla() {
        if (panel != null) return
        val koyu = Color.parseColor("#14131F")
        val kutu = object : LinearLayout(this) {
            override fun dispatchKeyEvent(e: KeyEvent): Boolean {
                if (e.keyCode == KeyEvent.KEYCODE_BACK && e.action == KeyEvent.ACTION_UP) {
                    if (web?.canGoBack() == true) web?.goBack() else panelKapat(); return true }
                return super.dispatchKeyEvent(e)
            }
        }.apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(koyu); cornerRadius = dp(22).toFloat(); setStroke(dp(1), Color.parseColor("#3A3760")) }
            clipToOutline = true; elevation = 24f
        }
        // top bar: title, open full app, close
        val ust = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(8), dp(6), dp(8)); setBackgroundColor(Color.parseColor("#211F3A"))
        }
        val ikon = ImageView(this).apply { setImageResource(R.drawable.ruzgar) }
        ust.addView(ikon, LinearLayout.LayoutParams(dp(26), dp(26)))
        ust.addView(TextView(this).apply { text = "  R\u00fczgar"; setTextColor(Color.WHITE); textSize = 16f },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        fun dugme(yazi: String, aciklama: String, is_: () -> Unit) = TextView(this).apply {
            text = yazi; contentDescription = aciklama; setTextColor(Color.parseColor("#C9C5F2")); textSize = 19f
            gravity = Gravity.CENTER; setOnClickListener { is_() }
        }.also { ust.addView(it, LinearLayout.LayoutParams(dp(44), dp(40))) }
        dugme("\ud83c\udf99", "Sesli sor") { sesliSor() }
        dugme("\u2197", "Uygulamay\u0131 a\u00e7") { panelKapat(); startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) }
        dugme("\u2715", "Kapat") { panelKapat() }
        kutu.addView(ust, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val w = WebView(this).apply {
            setBackgroundColor(koyu)
            with(settings) { javaScriptEnabled = true; domStorageEnabled = true; mediaPlaybackRequiresUserGesture = false; allowFileAccess = false }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                    if (r.url.toString().startsWith(SAYFA)) return false
                    panelKapat(); startActivity(Intent(Intent.ACTION_VIEW, r.url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true
                }
            }
            addJavascriptInterface(PanelKopru(), "RuzgarAndroid")
            loadUrl(SAYFA)
        }
        kutu.addView(w, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        kutu.setOnTouchListener { _, e -> if (e.action == MotionEvent.ACTION_OUTSIDE) { panelKapat(); true } else false }

        val dm = resources.displayMetrics
        panelLp = WindowManager.LayoutParams(dm.widthPixels - dp(20), (dm.heightPixels * 0.62).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; y = dp(14)
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            windowAnimations = android.R.style.Animation_Dialog
        }
        kutu.visibility = View.GONE
        wm.addView(kutu, panelLp); panel = kutu; web = w
    }

    private fun panelAc() {
        panelHazirla()
        panel?.visibility = View.VISIBLE; panelAcik = true
        balon?.visibility = View.GONE
    }

    private fun panelKapat() {
        panel?.visibility = View.GONE; panelAcik = false
        balon?.visibility = View.VISIBLE
    }

    private fun panelDegistir() { if (panelAcik) panelKapat() else panelAc() }

    // The phone's own speech recognizer needs an activity; the panel steps aside while it listens.
    private fun sesliSor() {
        panelHazirla()
        panel?.visibility = View.GONE; balon?.visibility = View.GONE
        startActivity(Intent(this, DinleAktivite::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION))
    }

    fun sesGeldi(t: String?) {
        panelAc()
        if (!t.isNullOrBlank()) web?.evaluateJavascript("window.sesliGonder && window.sesliGonder(" + JSONObject.quote(t) + ")", null)
    }

    inner class PanelKopru {
        @JavascriptInterface fun dinle() = balon?.post { sesliSor() }
        @JavascriptInterface fun oku(t: String) { if (sesHazir) tts?.speak(t, TextToSpeech.QUEUE_FLUSH, null, "ruzgar") }
        @JavascriptInterface fun qrOkut() = balon?.post { panelKapat(); startActivity(Intent(this@BalonServisi, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        @JavascriptInterface fun balonDurum(): Boolean = true
        @JavascriptInterface fun balon(ac: Boolean) = balon?.post { if (!ac) { getSharedPreferences("ruzgar", 0).edit().putBoolean("balon", false).apply(); stopSelf() } }
    }

    override fun onDestroy() {
        if (ornek === this) ornek = null
        tts?.shutdown()
        web?.destroy()
        listOf(panel, balon).forEach { v -> v?.let { runCatching { wm.removeView(it) } } }
        panel = null; balon = null; web = null
        super.onDestroy()
    }

    companion object {
        var ornek: BalonServisi? = null
        fun baslat(c: Context) { c.startForegroundService(Intent(c, BalonServisi::class.java)) }
        fun durdur(c: Context) { c.stopService(Intent(c, BalonServisi::class.java)) }
    }
}
