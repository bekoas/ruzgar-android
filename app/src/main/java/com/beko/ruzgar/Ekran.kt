package com.beko.ruzgar

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.WindowManager
import java.io.ByteArrayOutputStream

// "Ekrana sor" (like Gemini's "ask about this screen"):
// Android asks Beko for permission every time ("Start now"), then ONE frame is taken, the capture stops at once,
// and the picture is attached in the bubble panel. Nothing is recorded or kept running.
class EkranAktivite : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val mpm = getSystemService(MediaProjectionManager::class.java)
        val i = if (Build.VERSION.SDK_INT >= 34) mpm.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
                else mpm.createScreenCaptureIntent()
        try { startActivityForResult(i, 5) } catch (e: Exception) { BalonServisi.ornek?.ekranGeldi(null); bitir() }
    }
    override fun onActivityResult(rq: Int, rs: Int, d: Intent?) {
        if (rs == RESULT_OK && d != null)
            startForegroundService(Intent(this, EkranServisi::class.java).putExtra("kod", rs).putExtra("veri", d))
        else BalonServisi.ornek?.ekranGeldi(null)
        bitir()
    }
    private fun bitir() { finish(); overridePendingTransition(0, 0) }
}

class EkranServisi : Service() {
    private val el = Handler(Looper.getMainLooper())
    private var mp: MediaProjection? = null
    private var vd: VirtualDisplay? = null
    private var ir: ImageReader? = null
    private var bitti = false

    override fun onBind(i: Intent?) = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("ekran", "Ekrana sor", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "ekran").setSmallIcon(R.drawable.ic_bildirim)
            .setContentTitle("R\u00fczgar ekrana bak\u0131yor").setContentText("Tek kare al\u0131n\u0131yor, hemen kapan\u0131r").build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(2, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION) else startForeground(2, n)
        val veri: Intent? = if (Build.VERSION.SDK_INT >= 33) i?.getParcelableExtra("veri", Intent::class.java) else @Suppress("DEPRECATION") i?.getParcelableExtra("veri")
        val kod = i?.getIntExtra("kod", 0) ?: 0
        try {
            val p = getSystemService(MediaProjectionManager::class.java).getMediaProjection(kod, veri ?: run { bitir(null); return START_NOT_STICKY })
                ?: run { bitir(null); return START_NOT_STICKY }
            mp = p
            p.registerCallback(object : MediaProjection.Callback() { override fun onStop() { if (!bitti) bitir(null) } }, el)
            val wm = getSystemService(WindowManager::class.java)
            val (w, h) = if (Build.VERSION.SDK_INT >= 30) wm.maximumWindowMetrics.bounds.let { it.width() to it.height() }
                         else resources.displayMetrics.let { it.widthPixels to it.heightPixels }
            val okuyucu = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2); ir = okuyucu
            vd = p.createVirtualDisplay("ruzgar-ekran", w, h, resources.displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, okuyucu.surface, null, el)
            el.postDelayed({ yakala(w, h, 0) }, 700)   // let the permission dialog fade out first
        } catch (e: Exception) { bitir(null) }
        return START_NOT_STICKY
    }

    private fun yakala(w: Int, h: Int, deneme: Int) {
        if (bitti) return
        val img = runCatching { ir?.acquireLatestImage() }.getOrNull()
        if (img == null) { if (deneme < 10) el.postDelayed({ yakala(w, h, deneme + 1) }, 150) else bitir(null); return }
        try {
            val p = img.planes[0]; val ps = p.pixelStride
            val tam = Bitmap.createBitmap(p.rowStride / ps, h, Bitmap.Config.ARGB_8888)
            tam.copyPixelsFromBuffer(p.buffer); img.close()
            var bmp = Bitmap.createBitmap(tam, 0, 0, w, h)
            val o = minOf(1f, 1600f / maxOf(w, h))
            if (o < 1f) bmp = Bitmap.createScaledBitmap(bmp, (w * o).toInt(), (h * o).toInt(), true)
            val cikti = ByteArrayOutputStream(); bmp.compress(Bitmap.CompressFormat.JPEG, 85, cikti)
            bitir(Base64.encodeToString(cikti.toByteArray(), Base64.NO_WRAP))
        } catch (e: Exception) { runCatching { img.close() }; bitir(null) }
    }

    private fun bitir(b64: String?) {
        if (bitti) return; bitti = true
        runCatching { vd?.release() }; runCatching { ir?.close() }; runCatching { mp?.stop() }
        vd = null; ir = null; mp = null
        BalonServisi.ornek?.ekranGeldi(b64)
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }
}
