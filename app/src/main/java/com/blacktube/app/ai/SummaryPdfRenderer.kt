package com.blacktube.app.ai

import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.util.Log
import java.io.File
import java.io.FileOutputStream

class SummaryPdfRenderer {

    companion object {
        private const val TAG = "SummaryPdfRenderer"

        // Page dimensions (A4 in points at 72 dpi)
        private const val PAGE_WIDTH = 595
        private const val PAGE_HEIGHT = 842

        // Margins
        private const val MARGIN_LEFT = 56f
        private const val MARGIN_RIGHT = 56f
        private const val MARGIN_TOP = 72f
        private const val MARGIN_BOTTOM = 72f

        // Content area
        private val CONTENT_WIDTH = PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT
        private val CONTENT_TOP = MARGIN_TOP
        private val CONTENT_BOTTOM = PAGE_HEIGHT - MARGIN_BOTTOM

        // Colors
        private val COLOR_BLACK = Color.parseColor("#000000")
        private val COLOR_RED = Color.parseColor("#E50914")
        private val COLOR_RED_DARK = Color.parseColor("#B20710")
        private val COLOR_GRAY = Color.parseColor("#666666")
        private val COLOR_LIGHT_GRAY = Color.parseColor("#999999")
        private val COLOR_BG_SECTION = Color.parseColor("#FAFAFA")
        private val COLOR_DIVIDER = Color.parseColor("#E0E0E0")
        private val COLOR_WHITE = Color.parseColor("#FFFFFF")
    }

    private val paintSectionHeader = Paint().apply {
        color = COLOR_RED
        textSize = 16f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        isAntiAlias = true
    }

    private val paintBody = Paint().apply {
        color = COLOR_BLACK
        textSize = 11.5f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        isAntiAlias = true
    }

    private val paintChapterTime = Paint().apply {
        color = COLOR_RED
        textSize = 10.5f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        isAntiAlias = true
    }

    private val paintChapterText = Paint().apply {
        color = COLOR_BLACK
        textSize = 11f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        isAntiAlias = true
    }

    private val paintFooter = Paint().apply {
        color = COLOR_LIGHT_GRAY
        textSize = 9f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    private val paintCoverBadge = Paint().apply {
        color = COLOR_WHITE
        textSize = 11f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    private val paintLightDivider = Paint().apply {
        color = COLOR_DIVIDER
        strokeWidth = 1f
        isAntiAlias = true
    }

    private val paintBgRect = Paint().apply {
        color = COLOR_BG_SECTION
        isAntiAlias = true
    }

    private val paintCoverBg = Paint().apply {
        color = COLOR_BLACK
        isAntiAlias = true
    }

    /**
     * Generate the complete PDF document.
     * Returns the output File on success, null on failure.
     */
    fun render(data: SummaryPdfData, outputFile: File): File? {
        return try {
            val document = PdfDocument()

            // Page 1: Cover page
            drawCoverPage(document, data)

            // Page 2+: Content pages
            drawContentPages(document, data)

            FileOutputStream(outputFile).use { out ->
                document.writeTo(out)
            }
            document.close()

            Log.d(TAG, "PDF generated: ${outputFile.absolutePath}")
            outputFile
        } catch (e: Exception) {
            Log.e(TAG, "PDF generation failed: ${e.message}", e)
            null
        }
    }

    // ═══════════════════════════════════════════════════════
    // PAGE 1: COVER PAGE (AMOLED Black, Red Accents)
    // ═══════════════════════════════════════════════════════

    private fun drawCoverPage(document: PdfDocument, data: SummaryPdfData) {
        val pageInfo = PdfDocument.PageInfo.Builder(
            PAGE_WIDTH, PAGE_HEIGHT, 1
        ).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        // Full black background
        canvas.drawRect(
            RectF(0f, 0f, PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat()),
            paintCoverBg
        )

        var y = PAGE_HEIGHT * 0.22f

        // ── BlackTube branding ──
        val brandPaint = Paint().apply {
            color = COLOR_RED
            textSize = 16f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText("BLACKTUBE", PAGE_WIDTH / 2f, y, brandPaint)

        y += 24f
        val taglinePaint = Paint().apply {
            color = COLOR_LIGHT_GRAY
            textSize = 10f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText("AI-Generated Video Summary", PAGE_WIDTH / 2f, y, taglinePaint)

        y += 50f

        // ── Red accent line ──
        val linePaint = Paint().apply {
            color = COLOR_RED
            strokeWidth = 2f
        }
        canvas.drawLine(
            PAGE_WIDTH / 2f - 40f, y,
            PAGE_WIDTH / 2f + 40f, y,
            linePaint
        )

        y += 45f

        // ── Video title ──
        val titlePaint = Paint().apply {
            color = COLOR_WHITE
            textSize = 20f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        val titleLines = wrapText(data.videoTitle, titlePaint, CONTENT_WIDTH)
        for (line in titleLines) {
            canvas.drawText(line, PAGE_WIDTH / 2f, y, titlePaint)
            y += 28f
        }

        y += 16f

        // ── Channel name ──
        val channelPaint = Paint().apply {
            color = COLOR_LIGHT_GRAY
            textSize = 13f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText(data.channelName, PAGE_WIDTH / 2f, y, channelPaint)

        y += 26f

        // ── Category badge ──
        val categoryText = "${data.categoryEmoji} ${data.category}"
        val badgePaint = Paint().apply {
            color = COLOR_RED_DARK
            isAntiAlias = true
        }
        val badgeWidth = paintCoverBadge.measureText(categoryText) + 32f
        val badgeRect = RectF(
            PAGE_WIDTH / 2f - badgeWidth / 2f, y - 12f,
            PAGE_WIDTH / 2f + badgeWidth / 2f, y + 8f
        )
        canvas.drawRoundRect(badgeRect, 10f, 10f, badgePaint)
        canvas.drawText(categoryText, PAGE_WIDTH / 2f, y + 2f, paintCoverBadge)

        y += 50f

        // ── Video metadata ──
        val metaPaint = Paint().apply {
            color = COLOR_LIGHT_GRAY
            textSize = 10f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        if (data.videoDuration.isNotBlank()) {
            canvas.drawText("Duration: ${data.videoDuration}", PAGE_WIDTH / 2f, y, metaPaint)
            y += 18f
        }
        canvas.drawText("Engine: ${data.engineUsed}", PAGE_WIDTH / 2f, y, metaPaint)
        y += 18f
        canvas.drawText("Generated: ${data.generationDate}", PAGE_WIDTH / 2f, y, metaPaint)

        // ── Bottom branding ──
        val bottomPaint = Paint().apply {
            color = Color.parseColor("#444444")
            textSize = 9f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText(
            "Generated by BlackTube • Privacy-first YouTube client",
            PAGE_WIDTH / 2f,
            PAGE_HEIGHT - 50f,
            bottomPaint
        )

        document.finishPage(page)
    }

    // ═══════════════════════════════════════════════════════
    // PAGE 2+: CONTENT PAGES
    // ═══════════════════════════════════════════════════════

    private fun drawContentPages(document: PdfDocument, data: SummaryPdfData) {
        var pageNum = 2
        var currentY = CONTENT_TOP

        var pageInfo = createPageInfo(pageNum)
        var page = document.startPage(pageInfo)
        var canvas = page.canvas

        currentY = drawPageHeader(canvas, pageNum)

        // ── SECTION 1: Core Purpose ──
        if (data.corePurpose.isNotBlank()) {
            currentY = drawSection(
                canvas, currentY,
                "🎯 Core Purpose",
                data.corePurpose
            )
        }

        // Check if new page needed
        if (currentY > CONTENT_BOTTOM - 100) {
            drawPageFooter(canvas, pageNum)
            document.finishPage(page)
            pageNum++
            pageInfo = createPageInfo(pageNum)
            page = document.startPage(pageInfo)
            canvas = page.canvas
            currentY = drawPageHeader(canvas, pageNum)
        }

        // ── SECTION 2: Cultural Impact ──
        if (data.culturalImpact.isNotBlank()) {
            currentY += 12f
            currentY = drawSection(
                canvas, currentY,
                "🌍 Cultural Impact & Context",
                data.culturalImpact
            )
        }

        // Check page break
        if (currentY > CONTENT_BOTTOM - 150) {
            drawPageFooter(canvas, pageNum)
            document.finishPage(page)
            pageNum++
            pageInfo = createPageInfo(pageNum)
            page = document.startPage(pageInfo)
            canvas = page.canvas
            currentY = drawPageHeader(canvas, pageNum)
        }

        // ── SECTION 3: Chapters Timeline ──
        if (data.chapters.isNotEmpty()) {
            currentY += 12f
            currentY = drawSectionHeader(canvas, "📑 Chapters Timeline", currentY)
            currentY += 8f

            for ((index, chapter) in data.chapters.withIndex()) {
                val estimatedChapterHeight = estimateChapterHeight(chapter)
                if (currentY + estimatedChapterHeight > CONTENT_BOTTOM - 40) {
                    drawPageFooter(canvas, pageNum)
                    document.finishPage(page)
                    pageNum++
                    pageInfo = createPageInfo(pageNum)
                    page = document.startPage(pageInfo)
                    canvas = page.canvas
                    currentY = drawPageHeader(canvas, pageNum)
                }

                currentY = drawChapterEntry(canvas, chapter, index, currentY)
                currentY += 4f
            }
        }

        // ── SECTION 4: Vibe / Mood ──
        if (data.vibeEmoji.isNotBlank()) {
            if (currentY > CONTENT_BOTTOM - 80) {
                drawPageFooter(canvas, pageNum)
                document.finishPage(page)
                pageNum++
                pageInfo = createPageInfo(pageNum)
                page = document.startPage(pageInfo)
                canvas = page.canvas
                currentY = drawPageHeader(canvas, pageNum)
            }

            currentY += 18f
            currentY = drawVibeSection(canvas, data.vibeEmoji, currentY)
        }

        drawPageFooter(canvas, pageNum)
        document.finishPage(page)
    }

    private fun createPageInfo(pageNum: Int): PdfDocument.PageInfo {
        return PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
    }

    private fun drawPageHeader(canvas: Canvas, pageNum: Int): Float {
        val headerPaint = Paint().apply {
            color = COLOR_LIGHT_GRAY
            textSize = 8f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            isAntiAlias = true
        }

        canvas.drawText("BlackTube • Executive AI Summary", MARGIN_LEFT, 40f, headerPaint)

        val rightPaint = Paint().apply {
            color = COLOR_LIGHT_GRAY
            textSize = 8f
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }
        canvas.drawText("Page $pageNum", PAGE_WIDTH - MARGIN_RIGHT, 40f, rightPaint)

        canvas.drawLine(
            MARGIN_LEFT, 48f,
            PAGE_WIDTH - MARGIN_RIGHT, 48f,
            paintLightDivider
        )

        return MARGIN_TOP
    }

    private fun drawPageFooter(canvas: Canvas, pageNum: Int) {
        val footerY = PAGE_HEIGHT - 36f

        canvas.drawLine(
            MARGIN_LEFT, footerY - 14f,
            PAGE_WIDTH - MARGIN_RIGHT, footerY - 14f,
            paintLightDivider
        )

        canvas.drawText(
            "Generated by BlackTube — Privacy-first YouTube Client",
            PAGE_WIDTH / 2f, footerY,
            paintFooter
        )
    }

    private fun drawSection(
        canvas: Canvas,
        startY: Float,
        header: String,
        content: String
    ): Float {
        var y = startY
        y = drawSectionHeader(canvas, header, y)
        y += 6f

        val textLines = wrapText(content, paintBody, CONTENT_WIDTH - 32f)
        val lineHeight = paintBody.textSize * 1.5f
        val cardHeight = (textLines.size * lineHeight) + 20f

        val cardRect = RectF(
            MARGIN_LEFT, y,
            PAGE_WIDTH - MARGIN_RIGHT, y + cardHeight
        )
        canvas.drawRoundRect(cardRect, 6f, 6f, paintBgRect)

        var textY = y + 16f
        for (line in textLines) {
            canvas.drawText(line, MARGIN_LEFT + 16f, textY, paintBody)
            textY += lineHeight
        }

        return y + cardHeight + 12f
    }

    private fun drawSectionHeader(canvas: Canvas, title: String, y: Float): Float {
        val barPaint = Paint().apply {
            color = COLOR_RED
            isAntiAlias = true
        }
        canvas.drawRect(
            RectF(MARGIN_LEFT, y - 2f, MARGIN_LEFT + 4f, y + 14f),
            barPaint
        )

        canvas.drawText(title, MARGIN_LEFT + 12f, y + 12f, paintSectionHeader)
        return y + 22f
    }

    private fun drawChapterEntry(
        canvas: Canvas,
        chapter: PdfChapter,
        index: Int,
        startY: Float
    ): Float {
        var y = startY

        val dotPaint = Paint().apply {
            color = COLOR_RED
            isAntiAlias = true
        }
        canvas.drawCircle(MARGIN_LEFT + 6f, y + 6f, 3.5f, dotPaint)

        canvas.drawText(
            chapter.formattedTime(),
            MARGIN_LEFT + 18f, y + 9f,
            paintChapterTime
        )

        val emojiX = MARGIN_LEFT + 18f + paintChapterTime.measureText(chapter.formattedTime()) + 8f
        canvas.drawText(chapter.emoji, emojiX, y + 9f, paintChapterText)

        y += 18f
        val summaryLines = wrapText(chapter.summary, paintChapterText, CONTENT_WIDTH - 36f)
        val lineHeight = paintChapterText.textSize * 1.45f

        for (line in summaryLines) {
            canvas.drawText(line, MARGIN_LEFT + 18f, y, paintChapterText)
            y += lineHeight
        }

        return y + 4f
    }

    private fun estimateChapterHeight(chapter: PdfChapter): Float {
        val summaryLines = wrapText(chapter.summary, paintChapterText, CONTENT_WIDTH - 36f)
        return 22f + (summaryLines.size * paintChapterText.textSize * 1.45f) + 8f
    }

    private fun drawVibeSection(canvas: Canvas, vibeEmoji: String, y: Float): Float {
        val vibePaint = Paint().apply {
            color = COLOR_GRAY
            textSize = 12f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
            isAntiAlias = true
        }

        canvas.drawText(
            "Overall Vibe: $vibeEmoji",
            MARGIN_LEFT, y + 10f,
            vibePaint
        )

        return y + 24f
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val cleanedText = text
            .replace("**", "")
            .replace("###", "")
            .replace("##", "")
            .replace("#", "")
            .replace("*", "")
            .trim()

        val words = cleanedText.split("\\s+".toRegex())
        val lines = mutableListOf<String>()
        val currentLine = StringBuilder()

        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            val width = paint.measureText(testLine)

            if (width <= maxWidth) {
                currentLine.clear()
                currentLine.append(testLine)
            } else {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine.toString())
                    currentLine.clear()
                }
                if (paint.measureText(word) > maxWidth) {
                    lines.add(word)
                } else {
                    currentLine.append(word)
                }
            }
        }

        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }

        return lines
    }
}
