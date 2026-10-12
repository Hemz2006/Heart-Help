package com.hearthelp.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.hearthelp.domain.HistorySeries
import com.hearthelp.domain.MinuteSample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.ZoneId

/** Writes the selected range as CSV + PNG and opens Android's share sheet. */
object HistoryShare {
    suspend fun share(
        ctx: Context, samples: List<MinuteSample>, points: List<MinuteSample>,
        from: Long, to: Long, lowLimit: Int, highLimit: Int, rangeLabel: String,
    ) {
        val uris = try {
            withContext(Dispatchers.IO) {
                val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
                val name = "heart-rate-" + rangeLabel.replace(" ", "")
                val csv = File(dir, "$name.csv").apply { writeText(HistorySeries.csv(samples, ZoneId.systemDefault())) }
                val png = File(dir, "$name.png")
                val bmp = Bitmap.createBitmap(1600, 900, Bitmap.Config.ARGB_8888)
                HeartRateChart.draw(Canvas(bmp), 1600f, 900f, 2.5f, points, from, to, lowLimit, highLimit)
                png.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bmp.recycle()
                listOf(csv, png).map { FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", it) }
            }
        } catch (e: Exception) {
            Toast.makeText(ctx, "Could not prepare files: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }
        val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList<Uri>(uris))
            putExtra(Intent.EXTRA_SUBJECT, "Heart-rate history ($rangeLabel)")
            clipData = ClipData.newRawUri("", uris[0]).apply { addItem(ClipData.Item(uris[1])) }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            ctx.startActivity(Intent.createChooser(send, "Share heart-rate history"))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(ctx, "No app available to share with", Toast.LENGTH_LONG).show()
        }
    }
}
