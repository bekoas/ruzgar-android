package com.beko.ruzgar

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import kotlin.math.abs

// Floating Ruzgar bubble: always on top while the app is in the background.
// Tap: open the app. Drag: move (position is remembered). Drag to the bottom edge: close.
class BalonServisi : Service() {
    private var gorunum: View? = null

    override fun onBind(i: Intent?) = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        if (i?.action == "kapat") { getSharedPreferences("ruzgar", 0).edit().putBoolean("balon", false).apply(); stopSelf(); return START_NOT_STICKY }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("balon", "R\u00fczgar balonu", NotificationManager.IMPORTANCE_MIN))
        val kapat = PendingIntent.getService(this, 1, Intent(this, BalonServisi::class.java).setAction("kapat"), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, "balon")
            .setSmallIcon(R.drawable.ic_bildirim)
            .setContentTitle("R\u00fczgar balonu a\u00e7\u0131k")
            .setContentText("Dokun: R\u00fczgar'\u0131 a\u00e7")
            .setContentIntent(acmaNiyeti())
            .addAction(Notification.Action.Builder(null, "Balonu kapat", kapat).build())
            .setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(1, n)
        if (gorunum == null && Settings.canDrawOverlays(this)) ekle()
        return START_STICKY
    }

    private fun acmaNiyeti(): PendingIntent = PendingIntent.getActivity(this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
        PendingIntent.FLAG_IMMUTABLE)

    private fun ekle() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val ayar = getSharedPreferences("ruzgar", 0)
        val boy = (68 * resources.displayMetrics.density).toInt()
        val iv = ImageView(this).apply { setImageResource(R.drawable.ruzgar); elevation = 12f }
        val lp = WindowManager.LayoutParams(boy, boy, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            x = ayar.getInt("x", resources.displayMetrics.widthPixels - boy - 24)
            y = ayar.getInt("y", (resources.displayMetrics.heightPixels * 0.6).toInt())
        }
        var sx = 0f; var sy = 0f; var lx = 0; var ly = 0; var tasindi = false
        iv.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; lx = lp.x; ly = lp.y; tasindi = false; iv.animate().scaleX(0.9f).scaleY(0.9f).setDuration(80).start() }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - sx; val dy = e.rawY - sy
                    if (abs(dx) + abs(dy) > 14) tasindi = true
                    lp.x = lx + dx.toInt(); lp.y = ly + dy.toInt(); wm.updateViewLayout(iv, lp)
                }
                MotionEvent.ACTION_UP -> {
                    iv.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
                    if (!tasindi) startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                    else if (e.rawY > resources.displayMetrics.heightPixels - boy) { ayar.edit().putBoolean("balon", false).apply(); stopSelf() }
                    else ayar.edit().putInt("x", lp.x).putInt("y", lp.y).apply()
                }
            }
            true
        }
        wm.addView(iv, lp); gorunum = iv
    }

    override fun onDestroy() {
        gorunum?.let { runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) } }
        gorunum = null; super.onDestroy()
    }

    companion object {
        fun baslat(c: Context) { c.startForegroundService(Intent(c, BalonServisi::class.java)) }
        fun durdur(c: Context) { c.stopService(Intent(c, BalonServisi::class.java)) }
    }
}
