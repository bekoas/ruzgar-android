package com.beko.ruzgar

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import android.provider.OpenableColumns
import android.util.Base64
import android.widget.Toast
import java.time.OffsetDateTime
import java.time.LocalDateTime
import java.time.ZoneId

// Phone actions Ruzgar may PROPOSE in a reply as [[telefon:{...}]].
// The page shows an approval card first; only after Beko taps Onayla (or says "evet") does this run.
// Calls and SMS only open the dialer / composer filled in: the final press is always Beko's.
object Eylem {
    fun yap(c: Context, json: String): String = try {
        val o = JSONObject(json)
        when (o.optString("tip")) {
            "alarm" -> ac(c, Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, o.optInt("saat"))
                .putExtra(AlarmClock.EXTRA_MINUTES, o.optInt("dakika"))
                .putExtra(AlarmClock.EXTRA_MESSAGE, o.optString("etiket", "R\u00fczgar"))
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true), "Alarm kuruldu")
            "zamanlayici" -> ac(c, Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, o.optInt("saniye").coerceIn(1, 86400))
                .putExtra(AlarmClock.EXTRA_MESSAGE, o.optString("etiket", "R\u00fczgar"))
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true), "Zamanlay\u0131c\u0131 ba\u015flad\u0131")
            "ara" -> numara(c, o)?.let { ac(c, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(it))), "Arama ekran\u0131 a\u00e7\u0131ld\u0131, aramak i\u00e7in ye\u015fil tu\u015fa bas") }
                ?: kisiYok(c, o)
            "sms" -> numara(c, o)?.let { ac(c, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(it))).putExtra("sms_body", o.optString("metin")), "Mesaj haz\u0131r, g\u00f6ndermek i\u00e7in g\u00f6nder'e bas") }
                ?: kisiYok(c, o)
            "harita" -> ac(c, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(o.optString("yer")))), "Harita a\u00e7\u0131ld\u0131")
            "yol" -> ac(c, Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(o.optString("yer")))), "Yol tarifi ba\u015flad\u0131")
            "web" -> { val u = o.optString("url"); if (!u.startsWith("https://") && !u.startsWith("http://")) "Ge\u00e7ersiz adres" else ac(c, Intent(Intent.ACTION_VIEW, Uri.parse(u)), "Sayfa a\u00e7\u0131ld\u0131") }
            "uygulama" -> uygulama(c, o.optString("ad"))
            "takvim" -> {
                val i = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
                    .putExtra(CalendarContract.Events.TITLE, o.optString("baslik", "R\u00fczgar"))
                zaman(o.optString("zaman"))?.let { i.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it).putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it + o.optLong("dakika", 60) * 60000) }
                ac(c, i, "Takvim kayd\u0131 a\u00e7\u0131ld\u0131, kaydet'e bas")
            }
            "ayar" -> ac(c, Intent(when (o.optString("ne")) {
                "wifi" -> Settings.ACTION_WIFI_SETTINGS; "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
                "ses" -> Settings.ACTION_SOUND_SETTINGS; "ekran" -> Settings.ACTION_DISPLAY_SETTINGS
                "pil" -> Intent.ACTION_POWER_USAGE_SUMMARY; else -> Settings.ACTION_SETTINGS }), "Ayar ekran\u0131 a\u00e7\u0131ld\u0131")
            else -> "Bu i\u015fi bilmiyorum: " + o.optString("tip")
        }
    } catch (e: Exception) { "Yapamad\u0131m: " + (e.message ?: "hata") }

    private fun ac(c: Context, i: Intent, ok: String): String {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try { c.startActivity(i); ok } catch (e: Exception) { "Bunu a\u00e7acak uygulama yok" }
    }

    // A name ("annem") is resolved from the phone's own contacts; nothing leaves the phone.
    private fun numara(c: Context, o: JSONObject): String? {
        o.optString("numara").takeIf { it.isNotBlank() }?.let { return it }
        val kisi = o.optString("kisi").trim(); if (kisi.isEmpty()) return null
        if (c.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        c.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?", arrayOf("%$kisi%"), null)?.use { k ->
            if (k.moveToFirst()) return k.getString(0)
        }
        return null
    }

    private fun kisiYok(c: Context, o: JSONObject): String =
        if (o.optString("kisi").isNotBlank() && c.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) "REHBER_IZNI"
        else "Rehberde \"" + o.optString("kisi") + "\" bulunamad\u0131"

    private fun uygulama(c: Context, ad: String): String {
        val pm = c.packageManager
        val liste = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        val a = ad.trim().lowercase()
        val bul = liste.firstOrNull { it.loadLabel(pm).toString().lowercase() == a }
            ?: liste.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(a) } ?: return "\"$ad\" adl\u0131 uygulama yok"
        val i = pm.getLaunchIntentForPackage(bul.activityInfo.packageName) ?: return "A\u00e7\u0131lamad\u0131"
        return ac(c, i, bul.loadLabel(pm).toString() + " a\u00e7\u0131ld\u0131")
    }

    private fun zaman(s: String): Long? = runCatching { OffsetDateTime.parse(s).toInstant().toEpochMilli() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(s).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull()
}

// Read picked/shared files right away (the permission to read them ends when the picker closes)
// and hand them to the page as {ad, tur, b64}; the page encrypts and sends them like photos.
fun dosyalarJson(c: Context, uriler: List<Uri>): JSONArray {
    val dosyalar = JSONArray()
    for (u in uriler.take(4)) runCatching {
        var ad = "dosya"; var boy = 0L
        c.contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { k -> if (k.moveToFirst()) { ad = k.getString(0) ?: ad; boy = k.getLong(1) } }
        if (boy > 10_000_000) { Toast.makeText(c, "$ad \u00e7ok b\u00fcy\u00fck (en \u00e7ok 10 MB)", Toast.LENGTH_LONG).show(); return@runCatching }
        val b = c.contentResolver.openInputStream(u)?.use { it.readBytes() } ?: return@runCatching
        if (b.size > 10_000_000) return@runCatching
        dosyalar.put(JSONObject().put("ad", ad).put("tur", c.contentResolver.getType(u) ?: "").put("b64", Base64.encodeToString(b, Base64.NO_WRAP)))
    }
    return dosyalar
}

// Bring the bubble back by itself after a phone restart or an app update (Obtainium).
class AcilisAlici : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (balonAcik(c) && Settings.canDrawOverlays(c)) runCatching { BalonServisi.baslat(c) }
    }
}
