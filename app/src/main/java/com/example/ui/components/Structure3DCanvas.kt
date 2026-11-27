package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.CostingStructureType
import com.example.ui.theme.LimeGreen
import com.example.ui.theme.MaroonPrimary
import com.example.ui.theme.TabularNumberStyle
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private data class Vec3(val x: Float, val y: Float, val z: Float)

@Composable
fun InteractiveStructure3DCard(
    structureType: CostingStructureType,
    tiltDegrees: Float,
    panelCount: Int,
    estimatedStructureKg: Double,
    modifier: Modifier = Modifier
) {
    var yawDeg by remember { mutableFloatStateOf(32f) }
    var pitchDeg by remember { mutableFloatStateOf(24f) }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFF94A3B8)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "3D Mounting Structure Diagram (Drag to Rotate)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = "${structureType.label} • Clearance ${structureType.clearanceMeters}m • Tilt ${tiltDegrees.roundToInt()}°",
                        style = MaterialTheme.typography.labelSmall.merge(TabularNumberStyle),
                        color = MaroonPrimary
                    )
                }
                TextButton(
                    onClick = {
                        yawDeg = 32f
                        pitchDeg = 24f
                    }
                ) {
                    Text("Reset 3D", color = MaroonPrimary)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            yawDeg = (yawDeg + dragAmount.x * 0.35f).coerceIn(-75f, 75f)
                            pitchDeg = (pitchDeg + dragAmount.y * 0.25f).coerceIn(8f, 65f)
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawStructure3DScene(
                        structureType = structureType,
                        tiltDegrees = tiltDegrees,
                        panelCount = panelCount.coerceIn(2, 24),
                        yawDeg = yawDeg,
                        pitchDeg = pitchDeg
                    )
                }

                // Bottom-left engineering spec pill
                Surface(
                    color = Color(0xFF0F172A).copy(alpha = 0.86f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                ) {
                    Text(
                        text = "$panelCount Modules • ~${estimatedStructureKg.roundToInt()} kg HDGI • 150 km/h Wind",
                        style = MaterialTheme.typography.labelSmall.merge(TabularNumberStyle),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Surface(
                    color = LimeGreen.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Text(
                        text = "Yaw ${yawDeg.roundToInt()}° / Pitch ${pitchDeg.roundToInt()}°",
                        style = MaterialTheme.typography.labelSmall.merge(TabularNumberStyle),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = when (structureType) {
                        CostingStructureType.STANDARD_RCC -> "Anchor: Chemical Fasteners + RCC Waterproofing"
                        CostingStructureType.ELEVATED_GAZEBO -> "Walkable 9ft Gazebo Clear Headroom + Cross Bracing"
                        CostingStructureType.TIN_SHED -> "Flush Aluminum Rail & EPDM L-Clamp Mount"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF475569)
                )
            }
        }
    }
}

private fun DrawScope.drawStructure3DScene(
    structureType: CostingStructureType,
    tiltDegrees: Float,
    panelCount: Int,
    yawDeg: Float,
    pitchDeg: Float
) {
    val scale = min(size.width, size.height) * 0.22f
    val centerX = size.width * 0.50f
    val centerY = size.height * 0.62f

    val yawRad = Math.toRadians(yawDeg.toDouble())
    val pitchRad = Math.toRadians(pitchDeg.toDouble())
    val cosY = cos(yawRad).toFloat()
    val sinY = sin(yawRad).toFloat()
    val cosP = cos(pitchRad).toFloat()
    val sinP = sin(pitchRad).toFloat()

    fun project(p: Vec3): Offset {
        // Rotate around vertical Y axis (yaw)
        val rx = p.x * cosY - p.z * sinY
        val rz = p.x * sinY + p.z * cosY
        // Rotate around horizontal X axis (pitch)
        val ry = p.y * cosP - rz * sinP
        val depth = (rz * cosP + p.y * sinP) + 6.5f
        val perspective = (6.5f / depth.coerceAtLeast(1.8f))
        return Offset(
            x = centerX + rx * scale * perspective,
            y = centerY - ry * scale * perspective
        )
    }

    // 1. Rooftop Slab or Corrugated Tin Shed base
    val baseCorners = if (structureType == CostingStructureType.TIN_SHED) {
        listOf(
            Vec3(-1.9f, 0.0f, -1.2f),
            Vec3(1.9f, 0.0f, -1.2f),
            Vec3(1.9f, 0.35f, 1.2f),
            Vec3(-1.9f, 0.35f, 1.2f)
        )
    } else {
        listOf(
            Vec3(-1.9f, 0.0f, -1.2f),
            Vec3(1.9f, 0.0f, -1.2f),
            Vec3(1.9f, 0.0f, 1.2f),
            Vec3(-1.9f, 0.0f, 1.2f)
        )
    }
    val basePts = baseCorners.map(::project)
    val basePath = Path().apply {
        moveTo(basePts[0].x, basePts[0].y)
        lineTo(basePts[1].x, basePts[1].y)
        lineTo(basePts[2].x, basePts[2].y)
        lineTo(basePts[3].x, basePts[3].y)
        close()
    }
    drawPath(
        path = basePath,
        color = if (structureType == CostingStructureType.TIN_SHED) Color(0xFFCBD5E1) else Color(0xFFE2E8F0),
        style = Fill
    )
    drawPath(
        path = basePath,
        color = Color(0xFF64748B),
        style = Stroke(width = 2f, join = StrokeJoin.Round)
    )

    // Corrugation ribs on Tin Shed
    if (structureType == CostingStructureType.TIN_SHED) {
        for (i in -3..3) {
            val x = i * 0.48f
            drawLine(
                color = Color(0xFF94A3B8),
                start = project(Vec3(x, 0f, -1.2f)),
                end = project(Vec3(x, 0.35f, 1.2f)),
                strokeWidth = 1.5f
            )
        }
    }

    // 2. Column / Leg Heights
    val frontH = when (structureType) {
        CostingStructureType.STANDARD_RCC -> 0.42f
        CostingStructureType.ELEVATED_GAZEBO -> 1.52f
        CostingStructureType.TIN_SHED -> 0.16f
    }
    val tiltRise = (sin(Math.toRadians(tiltDegrees.coerceIn(5f, 40f).toDouble())).toFloat() * 1.45f)
    val rearH = if (structureType == CostingStructureType.TIN_SHED) 0.35f + 0.24f else frontH + tiltRise

    val legXs = listOf(-1.55f, 0f, 1.55f)
    val hdgiColor = Color(0xFF475569)

    // Draw HDGI legs
    legXs.forEach { lx ->
        val frontBaseY = 0f
        val rearBaseY = if (structureType == CostingStructureType.TIN_SHED) 0.35f else 0f
        val fB = project(Vec3(lx, frontBaseY, -0.95f))
        val fT = project(Vec3(lx, frontH, -0.95f))
        val rB = project(Vec3(lx, rearBaseY, 0.95f))
        val rT = project(Vec3(lx, rearH, 0.95f))

        drawLine(hdgiColor, fB, fT, strokeWidth = 5.5f, cap = StrokeCap.Round)
        drawLine(hdgiColor, rB, rT, strokeWidth = 5.5f, cap = StrokeCap.Round)
        // Rafter connecting front and rear leg tops
        drawLine(Color(0xFF334155), fT, rT, strokeWidth = 4.5f, cap = StrokeCap.Round)

        // Elevated Gazebo cross-bracing
        if (structureType == CostingStructureType.ELEVATED_GAZEBO) {
            val midF = project(Vec3(lx, frontH * 0.55f, -0.95f))
            val midR = project(Vec3(lx, rearH * 0.55f, 0.95f))
            drawLine(Color(0xFF64748B), midF, rT, strokeWidth = 2.2f)
            drawLine(Color(0xFF64748B), midR, fT, strokeWidth = 2.2f)
        }
    }

    // Horizontal Purlins
    drawLine(
        color = Color(0xFF334155),
        start = project(Vec3(-1.7f, frontH, -0.95f)),
        end = project(Vec3(1.7f, frontH, -0.95f)),
        strokeWidth = 4f
    )
    drawLine(
        color = Color(0xFF334155),
        start = project(Vec3(-1.7f, rearH, 0.95f)),
        end = project(Vec3(1.7f, rearH, 0.95f)),
        strokeWidth = 4f
    )

    // 3. Solar PV Panel Array Plane + Grid Cells
    val pFL = project(Vec3(-1.72f, frontH + 0.04f, -1.02f))
    val pFR = project(Vec3(1.72f, frontH + 0.04f, -1.02f))
    val pRR = project(Vec3(1.72f, rearH + 0.04f, 1.02f))
    val pRL = project(Vec3(-1.72f, rearH + 0.04f, 1.02f))

    val arrayPath = Path().apply {
        moveTo(pFL.x, pFL.y)
        lineTo(pFR.x, pFR.y)
        lineTo(pRR.x, pRR.y)
        lineTo(pRL.x, pRL.y)
        close()
    }
    drawPath(arrayPath, color = Color(0xFF1E3A8A), style = Fill)
    drawPath(arrayPath, color = Color(0xFFE2E8F0), style = Stroke(width = 3.2f, join = StrokeJoin.Round))

    // Draw individual solar module dividers
    val cols = (panelCount / 2).coerceIn(2, 8)
    for (c in 1 until cols) {
        val x = -1.72f + (3.44f * c / cols.toFloat())
        drawLine(
            color = Color(0xFF93C5FD),
            start = project(Vec3(x, frontH + 0.045f, -1.02f)),
            end = project(Vec3(x, rearH + 0.045f, 1.02f)),
            strokeWidth = 1.6f
        )
    }
    // Mid-row split line
    val midY = (frontH + rearH) * 0.5f + 0.045f
    drawLine(
        color = Color(0xFF93C5FD),
        start = project(Vec3(-1.72f, midY, 0f)),
        end = project(Vec3(1.72f, midY, 0f)),
        strokeWidth = 1.6f
    )

    // Clearance height indicator arrow on right side
    val arrowBot = project(Vec3(1.95f, 0f, -0.95f))
    val arrowTop = project(Vec3(1.95f, frontH, -0.95f))
    drawLine(MaroonPrimary, arrowBot, arrowTop, strokeWidth = 2.5f)
    drawCircle(MaroonPrimary, radius = 4f, center = arrowBot)
    drawCircle(MaroonPrimary, radius = 4f, center = arrowTop)
}
