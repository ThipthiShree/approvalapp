package com.example.approvalapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.view.View
import java.io.FileOutputStream
import java.io.IOException

class PrintDocumentAdapterHelper(
    private val context: Context,
    private val jobName: String,
    private val targetView: View          // pass the View you want to print
) : PrintDocumentAdapter() {

    // A4 points: 595 x 842
    private val PAGE_WIDTH  = 595
    private val PAGE_HEIGHT = 842

    private var capturedBitmap: Bitmap? = null

    /** Call this before PrintManager.print() so the bitmap is ready */
    fun captureView() {
        val bmp = Bitmap.createBitmap(
            targetView.width.coerceAtLeast(1),
            targetView.height.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)       // white background
        targetView.draw(canvas)
        capturedBitmap = bmp
    }

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal,
        callback: LayoutResultCallback,
        extras: Bundle?
    ) {
        if (cancellationSignal.isCanceled) {
            callback.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder(jobName)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(1)
            .build()
        callback.onLayoutFinished(info, oldAttributes != newAttributes)
    }

    override fun onWrite(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal,
        callback: WriteResultCallback
    ) {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        val bitmap = capturedBitmap
        if (bitmap != null && !bitmap.isRecycled) {
            // Scale bitmap to fit A4 with padding
            val padding = 36f
            val availW = PAGE_WIDTH  - padding * 2
            val availH = PAGE_HEIGHT - padding * 2
            val scale  = minOf(availW / bitmap.width, availH / bitmap.height)
            val drawW  = bitmap.width  * scale
            val drawH  = bitmap.height * scale
            val left   = padding + (availW - drawW) / 2f
            val top    = padding + (availH - drawH) / 2f

            val paint = Paint().apply { isFilterBitmap = true }
            canvas.drawBitmap(
                Bitmap.createScaledBitmap(bitmap, drawW.toInt(), drawH.toInt(), true),
                left, top, paint
            )
        } else {
            // Fallback if capture wasn't called
            val paint = Paint().apply {
                color = Color.BLACK
                textSize = 20f
            }
            canvas.drawText("Nothing to print — open Approval page first.", 36f, 60f, paint)
        }

        pdfDocument.finishPage(page)

        try {
            pdfDocument.writeTo(FileOutputStream(destination.fileDescriptor))
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: IOException) {
            callback.onWriteFailed(e.message)
        } finally {
            pdfDocument.close()
        }
    }
}