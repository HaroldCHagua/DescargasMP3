package com.limpia.descargasmp3

import android.content.ContentValues
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private val worker = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        var startupError: String? = null
        try {
            if (!Python.isStarted()) Python.start(AndroidPlatform(this))
            Python.getInstance().getModule("downloader").callAttr("get_version")
        } catch (t: Throwable) {
            startupError = "${t.javaClass.simpleName}: ${t.message ?: "Error al iniciar el motor Python"}"
        }

        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    DownloadScreen(startupError) { url, report ->
                        worker.execute {
                            try {
                                val dir = File(cacheDir, "mp3_work").apply {
                                    mkdirs()
                                    listFiles()?.forEach { it.deleteRecursively() }
                                }
                                report("Descargando audio…")
                                Python.getInstance().getModule("downloader")
                                    .callAttr("download", url, dir.absolutePath)

                                val audio = dir.listFiles()?.filter {
                                    it.isFile && it.extension.lowercase() in
                                        setOf("webm", "m4a", "opus", "ogg", "mp4", "aac", "mp3", "wav")
                                }?.maxByOrNull { it.lastModified() }
                                    ?: error("No se encontró el audio. Prueba con otro enlace.")
                                val stem = audio.nameWithoutExtension
                                val infoFile = File(dir, "$stem.info.json")
                                val info = if (infoFile.exists()) JSONObject(infoFile.readText()) else JSONObject()
                                val cover = dir.listFiles()?.firstOrNull {
                                    it.isFile && it.nameWithoutExtension == stem &&
                                        it.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp")
                                }
                                val output = File(dir, "$stem.mp3")
                                val args = mutableListOf("-y", "-i", audio.absolutePath)
                                if (cover != null) args.addAll(listOf("-i", cover.absolutePath))
                                args.addAll(listOf("-map", "0:a:0"))
                                if (cover != null) args.addAll(listOf("-map", "1:v:0"))
                                args.addAll(listOf("-c:a", "libmp3lame", "-b:a", "192k", "-map_metadata", "0"))
                                if (cover != null) args.addAll(listOf(
                                    "-c:v", "mjpeg", "-id3v2_version", "3",
                                    "-metadata:s:v", "title=Album cover",
                                    "-metadata:s:v", "comment=Cover (front)",
                                    "-disposition:v", "attached_pic"
                                ))
                                addMetadata(args, "title", info.optString("title", stem))
                                addMetadata(args, "artist", info.optString("artist", info.optString("uploader", "")))
                                addMetadata(args, "album", info.optString("album", ""))
                                addMetadata(args, "date", info.optString("upload_date", ""))
                                args.add(output.absolutePath)

                                report("Convirtiendo a MP3 y preparando la carátula…")
                                val session = FFmpegKit.executeWithArguments(args.toTypedArray())
                                if (!ReturnCode.isSuccess(session.returnCode)) {
                                    val detail = (session.failStackTrace ?: session.allLogsAsString).takeLast(700)
                                    error("La conversión falló: $detail")
                                }
                                check(output.exists() && output.length() > 0L) { "El MP3 quedó vacío." }
                                val name = saveToMusic(output)
                                report("LISTO: Música/DescargasMP3/$name")
                            } catch (t: Throwable) {
                                report("ERROR (${t.javaClass.simpleName}): ${t.message ?: "falló el proceso"}")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun addMetadata(args: MutableList<String>, key: String, value: String) {
        if (value.isNotBlank()) { args.add("-metadata"); args.add("$key=$value") }
    }

    private fun saveToMusic(file: File): String {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
            put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/DescargasMP3")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Android no permitió guardar el MP3.")
        try {
            contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                ?: error("No se pudo escribir el archivo.")
            contentResolver.update(uri, ContentValues().apply {
                put(MediaStore.Audio.Media.IS_PENDING, 0)
            }, null, null)
            return file.name
        } catch (t: Throwable) {
            contentResolver.delete(uri, null, null)
            throw t
        }
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }
}

@Composable
private fun DownloadScreen(
    startupError: String?,
    onDownload: (String, (String) -> Unit) -> Unit
) {
    var url by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(startupError?.let { "Error de inicio: $it" }
        ?: "Pega un enlace de contenido que tengas permiso para descargar.") }
    var busy by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Descargas MP3 Limpia", style = MaterialTheme.typography.headlineMedium)
        Text("Descarga audio, conviértelo a MP3 y guarda la carátula y los metadatos cuando estén disponibles.")
        OutlinedTextField(
            value = url, onValueChange = { url = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Enlace del vídeo") },
            placeholder = { Text("https://…") },
            singleLine = true, enabled = !busy
        )
        Button(
            onClick = {
                val clean = url.trim()
                if (!clean.startsWith("https://") && !clean.startsWith("http://")) {
                    status = "Introduce un enlace http o https válido."
                } else {
                    busy = true
                    status = "Iniciando…"
                    onDownload(clean) { msg ->
                        status = msg
                        if (msg.startsWith("LISTO:") || msg.startsWith("ERROR")) busy = false
                    }
                }
            },
            enabled = !busy && startupError == null && url.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (busy) "Procesando…" else "Descargar MP3") }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(status, style = MaterialTheme.typography.bodyMedium)
        Text("Ubicación: Música/DescargasMP3", style = MaterialTheme.typography.bodySmall)
        Text("Úsala solo con contenido propio, de dominio público o autorizado.", style = MaterialTheme.typography.bodySmall)
    }
}
