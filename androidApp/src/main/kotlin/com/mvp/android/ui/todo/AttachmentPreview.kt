package com.mvp.android.ui.todo

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import coil3.compose.AsyncImage
import com.mvp.android.ui.theme.Spacing
import com.mvp.core.designsystem.DesignSystem
import com.mvp.feature.todo.domain.model.AttachmentType
import com.mvp.feature.todo.domain.model.TodoAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Aperçu dans l'app, sans confier le fichier à une autre app (ADR 0014) : il reste derrière le verrou Face ID /
 * empreinte et hors des apps récentes quand le verrou est activé.
 */
@Composable
fun AttachmentPreview(attachment: TodoAttachment, modifier: Modifier = Modifier) {
    when (attachment.type) {
        AttachmentType.Photo -> AsyncImage(
            model = File(attachment.localPath),
            contentDescription = attachment.fileName,
            contentScale = ContentScale.Fit,
            modifier = modifier.fillMaxSize(),
        )
        AttachmentType.Pdf -> PdfPreview(attachment.localPath, modifier)
        AttachmentType.Text -> TextPreview(attachment.localPath, modifier)
    }
}

@Composable
private fun PdfPreview(path: String, modifier: Modifier) {
    val document = remember(path) { PdfDocument(File(path)) }
    DisposableEffect(document) { onDispose { document.close() } }
    val pageCount by produceState<Int?>(null, document) { value = withContext(Dispatchers.IO) { document.pageCount() } }
    when (val count = pageCount) {
        null -> Unit
        PdfDocument.UNREADABLE -> Text("Aperçu impossible pour ce PDF", style = MaterialTheme.typography.bodyLarge, modifier = modifier)
        else -> BoxWithConstraints(modifier.fillMaxSize()) {
            val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                items(minOf(count, MAX_PDF_PAGES)) { index -> PdfPage(document, index, widthPx) }
                if (count > MAX_PDF_PAGES) {
                    item { Text("Aperçu limité aux $MAX_PDF_PAGES premières pages", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@Composable
private fun PdfPage(document: PdfDocument, index: Int, widthPx: Int) {
    // Le papier reste clair même en thème sombre, comme une feuille imprimée.
    val paper = DesignSystem.colors(darkTheme = false).surface.toInt()
    val bitmap by produceState<Bitmap?>(null, document, index, widthPx) {
        value = withContext(Dispatchers.IO) { document.render(index, widthPx, paper) }
    }
    bitmap?.let { Image(it.asImageBitmap(), contentDescription = "Page ${index + 1}", modifier = Modifier.fillMaxWidth()) }
}

@Composable
private fun TextPreview(path: String, modifier: Modifier) {
    val text by produceState<String?>(null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                File(path).inputStream().use { input -> input.readNBytesCompat(MAX_TEXT_PREVIEW_BYTES).decodeToString() }
            }.getOrNull()
        }
    }
    SelectionContainer(
        modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).verticalScroll(rememberScrollState()),
    ) {
        Text(text ?: "", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * PDF ouvert avec le moteur du système. PdfRenderer n'accepte qu'une page ouverte à la fois et n'est pas
 * thread-safe : tout passe par le verrou, y compris la fermeture.
 */
private class PdfDocument(private val file: File) {
    private val lock = Any()
    private var renderer: PdfRenderer? = null
    private var closed = false

    /** Nombre de pages, ou [UNREADABLE] (fichier abîmé, protégé par mot de passe). */
    fun pageCount(): Int = synchronized(lock) { open()?.pageCount ?: UNREADABLE }

    fun render(index: Int, widthPx: Int, paperColor: Int): Bitmap? = synchronized(lock) {
        val renderer = open() ?: return null
        runCatching {
            renderer.openPage(index).use { page ->
                val height = (widthPx.toLong() * page.height / page.width.coerceAtLeast(1)).toInt().coerceIn(1, MAX_PAGE_HEIGHT_PX)
                Bitmap.createBitmap(widthPx.coerceAtLeast(1), height, Bitmap.Config.ARGB_8888).also { bitmap ->
                    bitmap.eraseColor(paperColor)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            }
        }.getOrNull()
    }

    fun close() = synchronized(lock) {
        closed = true
        renderer?.close()
        renderer = null
    }

    private fun open(): PdfRenderer? {
        if (closed) return null
        renderer?.let { return it }
        val descriptor = runCatching { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) }.getOrNull()
            ?: return null
        // PdfRenderer garde le descripteur s'il s'ouvre ; sinon il faut le fermer ici.
        return runCatching { PdfRenderer(descriptor) }
            .onFailure { descriptor.close() }
            .getOrNull()
            ?.also { renderer = it }
    }

    companion object {
        const val UNREADABLE = -1
    }
}

private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val buffer = ByteArray(limit)
    var total = 0
    while (total < limit) {
        val read = read(buffer, total, limit - total)
        if (read < 0) break
        total += read
    }
    return buffer.copyOf(total)
}

private const val MAX_PDF_PAGES = 30
private const val MAX_PAGE_HEIGHT_PX = 4096
private const val MAX_TEXT_PREVIEW_BYTES = 200 * 1024
