package com.alper.alpy

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URL

/** Self-update from the latest GitHub release (tag v1.0.<versionCode>, asset alpy.apk). */
object Updater {
    private const val API = "https://api.github.com/repos/avatar-lavventura/alpy/releases/latest"

    data class Release(val code: Int, val tag: String, val url: String)

    var available by mutableStateOf<Release?>(null)
    var busy by mutableStateOf(false)

    fun installed(ctx: Context) = ctx.packageManager.getPackageInfo(ctx.packageName, 0)

    /** Sets [available] when GitHub has a newer build; returns false when the check failed. */
    suspend fun check(ctx: Context): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val j = Store.json.parseToJsonElement(URL(API).readText()).jsonObject
            val tag = j["tag_name"]!!.jsonPrimitive.content
            val url = j["assets"]!!.jsonArray.first { "alpy.apk" in it.toString() }
                .jsonObject["browser_download_url"]!!.jsonPrimitive.content
            val r = Release(tag.substringAfterLast('.').toInt(), tag, url)
            available = r.takeIf { it.code > installed(ctx).longVersionCode }
        }.isSuccess
    }

    suspend fun install(ctx: Context) {
        val r = available ?: return
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(ctx, "Allow Alpy to install updates, then tap Install again", Toast.LENGTH_LONG).show()
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")))
            return
        }
        busy = true
        try {
            withContext(Dispatchers.IO) {
                val pi = ctx.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                // Silent once Alpy installed itself before (Android 12+); otherwise Android asks once.
                if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                val id = pi.createSession(params)
                pi.openSession(id).use { s ->
                    URL(r.url).openStream().use { input ->
                        s.openWrite("alpy.apk", 0, -1).use { out -> input.copyTo(out); s.fsync(out) }
                    }
                    val done = PendingIntent.getBroadcast(
                        ctx, 0, Intent(ctx, UpdateReceiver::class.java),
                        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                    s.commit(done.intentSender)
                }
            }
        } catch (e: Exception) {
            Toast.makeText(ctx, "Update failed: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            busy = false
        }
    }
}

class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        when (i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION") val confirm = i.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                ctx.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> {}
            else -> Toast.makeText(ctx, "Update failed: ${i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}", Toast.LENGTH_LONG).show()
        }
    }
}
