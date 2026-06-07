package com.example.ui.screens.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.White
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

enum class MascotState {
    Idle, User, Genesis, Recall, Pass, Loading
}

class Particle(val isBg: Boolean) {
    var x = 0f
    var y = 0f
    var originX = 0f
    var originY = 0f
    var vx = (Random.nextFloat() - 0.5f) * if (isBg) 0.5f else 0.9f
    var vy = (Random.nextFloat() - 0.5f) * if (isBg) 0.5f else 0.9f
    var r = if (isBg) (0.8f + Random.nextFloat() * 1.2f) else (2.0f + Random.nextFloat() * 2f)
    val baseR = r
    var angle = Random.nextFloat() * Math.PI.toFloat() * 2f
    var acc = 0.04f + Random.nextFloat() * 0.03f
    val id = Random.nextFloat()
}

@Composable
fun NeuralFluidMascot(
    modifier: Modifier = Modifier,
    state: MascotState = MascotState.Idle,
    isError: Boolean = false,
    blinkTrigger: Boolean = false,
    isDarkTheme: Boolean = true
) {
    val particles = remember { List(45) { Particle(false) } }
    val bgParticles = remember { List(50) { Particle(true) } }
    val triggerFrame = remember { mutableStateOf(0L) }

    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis { time ->
                triggerFrame.value = time
            }
        }
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val time = triggerFrame.value * 0.003f
        
        if (!initialized && w > 0f && h > 0f) {
            particles.forEach { 
                it.originX = Random.nextFloat() * w
                it.originY = Random.nextFloat() * h
                it.x = w / 2f + (Random.nextFloat() - 0.5f) * 100f
                it.y = h / 2f + (Random.nextFloat() - 0.5f) * 100f
            }
            bgParticles.forEach { 
                it.originX = Random.nextFloat() * w
                it.originY = Random.nextFloat() * h
                it.x = w / 2f + (Random.nextFloat() - 0.5f) * w
                it.y = h / 2f + (Random.nextFloat() - 0.5f) * h
            }
            initialized = true
        }

        val baseColor = if (isDarkTheme) Color.White else Color.Black

        // Draw Background Particles
        bgParticles.forEach { p ->
            p.originX += p.vx
            p.originY += p.vy
            if (p.originX < 0 || p.originX > w) p.vx *= -1
            if (p.originY < 0 || p.originY > h) p.vy *= -1
            
            p.x += (p.originX - p.x) * p.acc
            p.y += (p.originY - p.y) * p.acc
            
            drawCircle(
                color = baseColor.copy(alpha = 0.12f),
                radius = p.r,
                center = Offset(p.x, p.y)
            )
        }

        val basePointColor = if (isError) ErrorRed else baseColor

        // Draw Main Particles
        for (i in particles.indices) {
            val p = particles[i]
            var tx = p.originX
            var ty = p.originY

            when (state) {
                MascotState.Idle -> {
                    p.originX += p.vx
                    p.originY += p.vy
                    if (p.originX < 0 || p.originX > w) p.vx *= -1
                    if (p.originY < 0 || p.originY > h) p.vy *= -1
                    tx = p.originX
                    ty = p.originY
                }
                MascotState.User -> {
                    p.angle += 0.02f
                    tx = w / 2 + cos(p.angle + p.id) * 140f
                    ty = h / 2 + sin(p.angle + p.id) * 90f
                }
                MascotState.Genesis -> {
                    val vPos = (p.id % 0.5f) * 2f
                    val strand = if (p.id > 0.5f) Math.PI.toFloat() else 0f
                    val twistFreq = 2.5f
                    val rotSpeed = time * 0.6f
                    val hAngle = (vPos * Math.PI.toFloat() * twistFreq) - rotSpeed + strand
                    tx = w / 2 + cos(hAngle) * 80f
                    ty = h / 2 - 120f + (vPos * 240f)
                }
                MascotState.Recall -> {
                    val vPos = p.id
                    val swirlAngle = (vPos * Math.PI.toFloat() * 5f) + (time * 0.4f)
                    val radius = (1.1f - vPos) * 130f + 16f
                    tx = w / 2 + cos(swirlAngle) * radius
                    ty = h / 2 - 110f + (vPos * 220f)
                }
                MascotState.Pass -> {
                    val t = p.id * Math.PI.toFloat() * 2f
                    if (p.id > 0.4f) {
                        tx = w / 2 + 170f * cos(t)
                        ty = h / 2 + 84f * sin(t)
                    } else if (p.id > 0.1f) {
                        val prog = (p.id - 0.1f) / 0.3f - 0.5f
                        tx = w / 2 + (prog * 60f)
                        ty = h / 2 + (prog * 60f)
                    } else {
                        val prog = (p.id / 0.1f) - 0.5f
                        tx = w / 2 + (prog * 60f)
                        ty = h / 2 - (prog * 60f)
                    }
                }
                MascotState.Loading -> {
                    p.angle += 0.05f
                    val t = p.angle + (p.baseR * 10f)
                    val scale = 150f
                    val denom = 1f + sin(t).pow(2)
                    tx = w / 2 + (scale * cos(t)) / denom
                    ty = h / 2 + (scale * sin(t) * cos(t)) / denom
                }
            }
            
            p.x += (tx - p.x) * p.acc
            p.y += (ty - p.y) * p.acc

            drawCircle(
                color = basePointColor,
                radius = p.r,
                center = Offset(p.x, p.y)
            )

            // Connect lines
            for (j in i + 1 until particles.size) {
                val p2 = particles[j]
                val dx = p.x - p2.x
                val dy = p.y - p2.y
                val d = dx * dx + dy * dy
                val limit = if (state == MascotState.Genesis || state == MascotState.Pass) 7000f else 10000f
                if (d < limit) {
                    val op = 1f - sqrt(d) / sqrt(limit)
                    drawLine(
                        color = baseColor.copy(alpha = op * 0.25f),
                        start = Offset(p.x, p.y),
                        end = Offset(p2.x, p2.y),
                        strokeWidth = 1.5f
                    )
                }
            }
        }
    }
}
