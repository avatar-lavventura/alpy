package com.alper.alpy

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import androidx.core.util.AtomicFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate

// ponytail: whole state is one JSON file rewritten on each change; fine for hundreds of items, move to Room past that.
object Store {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    val data = MutableStateFlow(Data())
    private lateinit var app: Context
    // AtomicFile syncs to disk before replacing, so a crash or power cut leaves the old file or the new one, never an empty one
    private val file get() = AtomicFile(File(app.filesDir, "data.json"))

    fun init(ctx: Context) {
        app = ctx
        if (!file.baseFile.exists()) return
        data.value = try {
            json.decodeFromString<Data>(file.readFully().decodeToString())
        } catch (e: Exception) {
            file.baseFile.copyTo(File(app.filesDir, "data.bad-${System.currentTimeMillis()}.json"))
            Data()
        }
    }

    @Synchronized
    fun update(f: (Data) -> Data): Data {
        val d = f(data.value)
        data.value = d
        val af = file
        val out = af.startWrite()
        try {
            out.write(json.encodeToString(Data.serializer(), d).toByteArray())
            af.finishWrite(out)
        } catch (e: Exception) {
            af.failWrite(out)
            throw e
        }
        dailyBackup(d)
        return d
    }

    fun editReminder(id: Int, f: (Reminder) -> Reminder) =
        update { d -> d.copy(reminders = d.reminders.map { if (it.id == id) f(it) else it }) }.reminders.find { it.id == id }

    /** Once a day, a copy to Download/Alpy/ (survives uninstall); keeps the newest 7. */
    private fun dailyBackup(d: Data) {
        val today = LocalDate.now().toString()
        val prefs = app.getSharedPreferences("store", 0)
        if (prefs.getString("backup", null) == today) return
        runCatching {
            val cr = app.contentResolver
            val coll = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val dir = Environment.DIRECTORY_DOWNLOADS + "/Alpy/"
            val uri = cr.insert(coll, ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "alpy-$today.json")
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(MediaStore.MediaColumns.RELATIVE_PATH, dir)
            })!!
            cr.openOutputStream(uri)!!.use { it.write(json.encodeToString(Data.serializer(), d).toByteArray()) }
            cr.query(
                coll, arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE 'alpy-%'",
                arrayOf(dir), "${MediaStore.MediaColumns.DISPLAY_NAME} DESC",
            )?.use { c ->
                var n = 0
                while (c.moveToNext()) if (++n > 7) cr.delete(ContentUris.withAppendedId(coll, c.getLong(0)), null, null)
            }
            prefs.edit().putString("backup", today).apply()
        }
    }
}
