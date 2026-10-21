package com.alper.alpy

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Self-update from the latest GitHub release (tag v1.0.<versionCode>, asset alpy.apk). */
object Updater {
    private const val API = "https://api.github.com/repos/avatar-lavventura/alpy/releases/latest"

    data class Release(val code: Int, val tag: String, val url: String)

    var available by mutableStateOf<Release?>(null)
    /** Download progress 0..100 while downloading, else null. */
    var progress by mutableStateOf<Int?>(null)

    fun installed(ctx: Context) = ctx.packageManager.getPackageInfo(ctx.packageName, 0)

    private fun open(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
    }

    /** Sets [available] when GitHub has a newer build; returns the error message when the check failed. */
    suspend fun check(ctx: Context): String? = withContext(Dispatchers.IO) {
        runCatching {
            val j = Store.json.parseToJsonElement(open(API).inputStream.use { it.readBytes().decodeToString() }).jsonObject
            val tag = j["tag_name"]!!.jsonPrimitive.content
            val url = j["assets"]!!.jsonArray.first { it.jsonObject["name"]?.jsonPrimitive?.content == "alpy.apk" }
                .jsonObject["browser_download_url"]!!.jsonPrimitive.content
            val r = Release(tag.substringAfterLast('.').toInt(), tag, url)
            available = r.takeIf { it.code > installed(ctx).longVersionCode }
        }.exceptionOrNull()?.let { it.message ?: it.toString() }
    }

    /** Downloads the APK, then opens Android's normal installer (the same path a browser download uses). */
    suspend fun install(ctx: Context) {
        val r = available ?: return
        if (progress != null) return
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(ctx, "Allow Alpy to install updates, then tap Install again", Toast.LENGTH_LONG).show()
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")))
            return
        }
        progress = 0
        val apk = File(ctx.cacheDir, "update/alpy.apk")
        try {
            withContext(Dispatchers.IO) {
                apk.parentFile!!.mkdirs()
                val c = open(r.url)
                val total = c.contentLengthLong
                c.inputStream.use { input ->
                    apk.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (total > 0) progress = (done * 100 / total).toInt()
                        }
                    }
                }
            }
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.update", apk)
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        } catch (e: Exception) {
            Toast.makeText(ctx, "Update failed: ${e.message ?: e}", Toast.LENGTH_LONG).show()
        } finally {
            progress = null
        }
    }
}
