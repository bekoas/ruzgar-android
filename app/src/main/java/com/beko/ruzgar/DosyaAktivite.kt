package com.beko.ruzgar

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.ValueCallback
import org.json.JSONObject

// Invisible helper: opens the phone's file / photo picker for the floating panel.
// The chosen files are read here, while the read permission is still valid, and passed to the page.
class DosyaAktivite : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val i = (istek ?: Intent(Intent.ACTION_GET_CONTENT).setType("*/*")).addCategory(Intent.CATEGORY_OPENABLE)
            .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        try { startActivityForResult(i, 7) } catch (e: Exception) { bitir() }
    }
    override fun onActivityResult(rq: Int, rs: Int, d: Intent?) {
        val uriler = ArrayList<Uri>()
        if (rs == RESULT_OK && d != null) {
            d.clipData?.let { c -> for (k in 0 until c.itemCount) uriler.add(c.getItemAt(k).uri) }
            if (uriler.isEmpty()) d.data?.let { uriler.add(it) }
        }
        if (uriler.isNotEmpty()) {
            val j = JSONObject().put("metin", "").put("dosyalar", dosyalarJson(this, uriler)).toString()
            BalonServisi.ornek?.paylasimVer(j)
        }
        bitir()
    }
    private fun bitir() {
        geriDonus?.onReceiveValue(null); geriDonus = null; istek = null
        BalonServisi.ornek?.panelGoster(); finish(); overridePendingTransition(0, 0)
    }
    companion object { var geriDonus: ValueCallback<Array<Uri>>? = null; var istek: Intent? = null }
}
