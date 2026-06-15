package com.example.myapplication1.studio

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Save a captured scene frame to the cache and fire the system share sheet. Uses a
 * FileProvider (declared in the manifest) so the PNG can be shared safely via a content URI.
 */
fun shareSceneImage(context: Context, image: ImageBitmap, sceneTitle: String) {
    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
    val file = File(dir, "particle-studio.png")
    FileOutputStream(file).use { out ->
        image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, out)
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, "Particle Studio — $sceneTitle")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(send, "Share scene").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}
