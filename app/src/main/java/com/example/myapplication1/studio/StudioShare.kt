package com.example.myapplication1.studio

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Save a captured scene frame to the cache and fire the system share sheet. Uses a
 * FileProvider (declared in the manifest) so the PNG can be shared safely via a content URI.
 *
 * `suspend`, with the PNG compression + file write on [Dispatchers.IO] — compressing a
 * full-screen bitmap is 100–500ms and must never run on the main thread (jank / ANR). Call from
 * a coroutine (e.g. `scope.launch { shareSceneImage(...) }`); `startActivity` resumes on the
 * caller's dispatcher (Main).
 */
suspend fun shareSceneImage(context: Context, image: ImageBitmap, sceneTitle: String) {
    val uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        // Unique name so re-sharing never overwrites a file the previous chooser is still reading.
        val file = File(dir, "particle-studio-${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, "Particle Studio — $sceneTitle")
        // ClipData carries the read grant to the chooser's preview process too (not just the
        // eventual share target), so the sheet shows a thumbnail instead of a permission denial.
        clipData = ClipData.newUri(context.contentResolver, sceneTitle, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(send, "Share scene").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}
