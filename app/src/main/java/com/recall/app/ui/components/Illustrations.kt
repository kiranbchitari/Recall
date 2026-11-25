package com.recall.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.recall.app.ui.theme.Recall

// Small hand-drawn vector scenes. Decorative only, so they are hidden from screen readers.

private val Peach = Color(0xFFF6C6A5)
private val Terracotta = Color(0xFFE5A079)
private val Leaf = Color(0xFF63B784)
private val LeafDeep = Color(0xFF4A9C6C)
private val Cream = Color(0xFFFFF6EA)
private val Coffee = Color(0xFF6A493A)
private val Pencil = Color(0xFFF8B84A)
private val Spark = Color(0xFFF58A4B)

private fun DrawScope.rr(color: Color, x: Float, y: Float, w: Float, h: Float, r: Float) =
    drawRoundRect(color, Offset(x, y), Size(w, h), CornerRadius(r, r))

/** A cup, a plant and a couple of books: the "little space for your mind". */
@Composable
fun MindIllustration(modifier: Modifier = Modifier) {
    val p = Recall.colors
    val cup = lerp(p.primary, Color.White, if (p.dark) 0f else 0.2f)
    val book = lerp(p.primary, Color.White, 0.5f)
    Canvas(modifier.clearAndSetSemantics { }) {
        val w = size.width
        val h = size.height

        drawCircle(p.primarySoft.copy(alpha = 0.7f), radius = w * 0.44f, center = Offset(w * 0.55f, h * 0.5f))

        // Plant, behind the cup
        val base = Offset(w * 0.72f, h * 0.5f)
        listOf(
            Triple(-42f, 0.26f, Leaf), Triple(-14f, 0.40f, LeafDeep),
            Triple(14f, 0.36f, Leaf), Triple(40f, 0.27f, LeafDeep),
        ).forEach { (angle, length, color) ->
            rotate(angle, pivot = base) {
                drawOval(color, Offset(base.x - w * 0.065f, base.y - h * length), Size(w * 0.13f, h * length))
            }
        }
        drawPath(
            Path().apply {
                moveTo(w * 0.60f, h * 0.50f); lineTo(w * 0.84f, h * 0.50f)
                lineTo(w * 0.80f, h * 0.69f); lineTo(w * 0.64f, h * 0.69f); close()
            },
            Terracotta,
        )
        rr(lerp(Terracotta, Color.Black, 0.08f), w * 0.58f, h * 0.47f, w * 0.28f, h * 0.055f, w * 0.02f)

        // Books
        rr(Peach, w * 0.16f, h * 0.68f, w * 0.70f, h * 0.12f, w * 0.035f)
        rr(Cream, w * 0.24f, h * 0.71f, w * 0.62f, h * 0.06f, w * 0.015f)
        rr(book, w * 0.08f, h * 0.80f, w * 0.84f, h * 0.13f, w * 0.04f)
        rr(Cream, w * 0.08f, h * 0.835f, w * 0.72f, h * 0.06f, w * 0.015f)

        // Cup
        drawArc(
            cup, startAngle = -90f, sweepAngle = 180f, useCenter = false,
            topLeft = Offset(w * 0.42f, h * 0.47f), size = Size(w * 0.18f, h * 0.15f),
            style = Stroke(w * 0.04f, cap = StrokeCap.Round),
        )
        rr(cup, w * 0.20f, h * 0.42f, w * 0.31f, h * 0.27f, w * 0.07f)
        drawOval(Coffee, Offset(w * 0.225f, h * 0.425f), Size(w * 0.26f, h * 0.055f))
        rr(Color.White.copy(alpha = 0.22f), w * 0.235f, h * 0.51f, w * 0.035f, h * 0.13f, w * 0.02f)

        // Steam
        val steam = Stroke(w * 0.014f, cap = StrokeCap.Round)
        listOf(0.30f, 0.40f).forEachIndexed { i, x ->
            val top = if (i == 0) 0.20f else 0.25f
            drawPath(
                Path().apply {
                    moveTo(w * x, h * 0.38f)
                    cubicTo(w * (x - 0.05f), h * 0.33f, w * (x + 0.05f), h * (top + 0.06f), w * x, h * top)
                },
                p.inkFaint.copy(alpha = 0.45f),
                style = steam,
            )
        }
    }
}

/** A ticked-off clipboard with a pencil, for the review screen. */
@Composable
fun ClipboardIllustration(modifier: Modifier = Modifier) {
    val p = Recall.colors
    val board = lerp(p.primary, Color.White, if (p.dark) 0f else 0.12f)
    val paper = if (p.dark) Color(0xFFF3F0FB) else Color.White
    val line = lerp(p.primary, Color.White, 0.6f)
    Canvas(modifier.clearAndSetSemantics { }) {
        val w = size.width
        val h = size.height
        val cloud = p.primarySoft.copy(alpha = if (p.dark) 0.6f else 0.85f)

        drawCircle(cloud, h * 0.30f, Offset(w * 0.24f, h * 0.62f))
        drawCircle(cloud, h * 0.40f, Offset(w * 0.50f, h * 0.44f))
        drawCircle(cloud, h * 0.28f, Offset(w * 0.78f, h * 0.64f))
        rr(cloud, w * 0.16f, h * 0.62f, w * 0.70f, h * 0.30f, h * 0.15f)

        rotate(-8f, pivot = Offset(w * 0.46f, h * 0.5f)) {
            rr(board, w * 0.27f, h * 0.13f, w * 0.40f, h * 0.76f, w * 0.045f)
            rr(paper, w * 0.295f, h * 0.17f, w * 0.35f, h * 0.68f, w * 0.03f)
            rr(lerp(board, Color.Black, 0.18f), w * 0.40f, h * 0.075f, w * 0.14f, h * 0.11f, w * 0.03f)
            drawCircle(paper, w * 0.018f, Offset(w * 0.47f, h * 0.11f))

            val tick = Stroke(w * 0.016f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            repeat(3) { i ->
                val y = h * (0.34f + i * 0.17f)
                drawPath(
                    Path().apply {
                        moveTo(w * 0.335f, y)
                        lineTo(w * 0.355f, y + h * 0.035f)
                        lineTo(w * 0.395f, y - h * 0.035f)
                    },
                    Leaf,
                    style = tick,
                )
                rr(line, w * 0.44f, y - h * 0.02f, w * (if (i == 1) 0.13f else 0.17f), h * 0.028f, h * 0.014f)
                rr(line.copy(alpha = 0.5f), w * 0.44f, y + h * 0.03f, w * 0.09f, h * 0.022f, h * 0.011f)
            }
        }

        rotate(38f, pivot = Offset(w * 0.72f, h * 0.60f)) {
            val x = w * 0.685f
            val bw = w * 0.07f
            rr(Color(0xFFF28B9B), x, h * 0.34f, bw, h * 0.07f, bw * 0.3f)
            drawRect(line, Offset(x, h * 0.39f), Size(bw, h * 0.03f))
            drawRect(Pencil, Offset(x, h * 0.42f), Size(bw, h * 0.32f))
            drawRect(lerp(Pencil, Color.Black, 0.1f), Offset(x + bw * 0.62f, h * 0.42f), Size(bw * 0.38f, h * 0.32f))
            drawPath(
                Path().apply {
                    moveTo(x, h * 0.74f); lineTo(x + bw, h * 0.74f); lineTo(x + bw / 2, h * 0.83f); close()
                },
                Color(0xFFF3D9B8),
            )
            drawPath(
                Path().apply {
                    moveTo(x + bw * 0.32f, h * 0.798f); lineTo(x + bw * 0.68f, h * 0.798f)
                    lineTo(x + bw / 2, h * 0.83f); close()
                },
                Color(0xFF4A4458),
            )
        }

        val spark = Stroke(w * 0.012f, cap = StrokeCap.Round)
        listOf(
            Offset(0.80f, 0.16f) to Offset(0.82f, 0.09f),
            Offset(0.84f, 0.20f) to Offset(0.90f, 0.15f),
            Offset(0.85f, 0.26f) to Offset(0.91f, 0.27f),
        ).forEach { (a, b) ->
            drawLine(Spark, Offset(w * a.x, h * a.y), Offset(w * b.x, h * b.y), spark.width, StrokeCap.Round)
        }
    }
}

/** An icon resting on a soft cloud, used for empty and finished states. */
@Composable
fun BlobIcon(icon: ImageVector, modifier: Modifier = Modifier) {
    val p = Recall.colors
    Box(modifier.size(168.dp).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(168.dp)) {
            val w = size.width
            val cloud = p.primarySoft.copy(alpha = if (p.dark) 0.6f else 0.85f)
            drawCircle(cloud, w * 0.24f, Offset(w * 0.27f, w * 0.60f))
            drawCircle(cloud, w * 0.32f, Offset(w * 0.52f, w * 0.46f))
            drawCircle(cloud, w * 0.22f, Offset(w * 0.76f, w * 0.62f))
            rr(cloud, w * 0.14f, w * 0.60f, w * 0.72f, w * 0.24f, w * 0.12f)
            drawCircle(Spark.copy(alpha = 0.8f), w * 0.022f, Offset(w * 0.84f, w * 0.24f))
            drawCircle(Leaf.copy(alpha = 0.8f), w * 0.016f, Offset(w * 0.18f, w * 0.30f))
            drawCircle(p.primary.copy(alpha = 0.5f), w * 0.014f, Offset(w * 0.90f, w * 0.40f))
        }
        Icon(icon, null, tint = p.primary, modifier = Modifier.size(64.dp))
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BlobIcon(icon)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = Recall.colors.ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Recall.colors.inkSoft, textAlign = TextAlign.Center)
    }
}
