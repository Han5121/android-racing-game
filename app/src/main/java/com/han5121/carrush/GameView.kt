package com.han5121.carrush

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.random.Random

class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SurfaceView(context, attrs, defStyleAttr), Runnable, SurfaceHolder.Callback {

    private var gameThread: Thread? = null
    private var running = false
    private var gameOver = false
    private var score = 0
    private var roadOffset = 0f
    private var spawnTimer = 0f
    private var lastFrameTime = 0L

    private var playerX = 0f
    private var playerTargetX = 0f

    private val playerWidth = 120f
    private val playerHeight = 200f
    private val roadLeft = 150f
    private val roadRight = 930f
    private val roadWidth = roadRight - roadLeft

    private val obstacles = mutableListOf<Obstacle>()

    private val grassPaint = Paint().apply { color = Color.parseColor("#2E7D32") }
    private val roadPaint = Paint().apply { color = Color.parseColor("#303030") }
    private val lanePaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 12f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }
    private val playerPaint = Paint().apply {
        color = Color.parseColor("#29B6F6")
        isAntiAlias = true
    }
    private val obstaclePaint = Paint().apply {
        color = Color.parseColor("#EF5350")
        isAntiAlias = true
    }
    private val uiPaint = Paint().apply {
        color = Color.WHITE
        textSize = 52f
        typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true
    }
    private val gameOverPaint = Paint().apply {
        color = Color.WHITE
        textSize = 72f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    data class Obstacle(
        var x: Float,
        var y: Float,
        var width: Float,
        var height: Float,
        var speed: Float
    )

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        resetGame()
        running = true
        gameThread = Thread(this, "CarRushThread")
        gameThread?.start()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
        gameThread?.join(500)
    }

    override fun run() {
        while (running) {
            val now = System.nanoTime()
            if (lastFrameTime == 0L) {
                lastFrameTime = now
            }
            val deltaMs = ((now - lastFrameTime) / 1_000_000f)
            lastFrameTime = now

            updateGame(deltaMs)
            drawGame()

            try {
                Thread.sleep(16)
            } catch (_: InterruptedException) {
                running = false
            }
        }
    }

    private fun resetGame() {
        score = 0
        gameOver = false
        roadOffset = 0f
        spawnTimer = 0f
        obstacles.clear()
        playerX = width / 2f
        playerTargetX = playerX
        lastFrameTime = System.nanoTime()
    }

    private fun updateGame(deltaMs: Float) {
        if (gameOver) return

        val delta = deltaMs / 1000f
        roadOffset += 420f * delta
        spawnTimer -= deltaMs

        if (spawnTimer <= 0f) {
            val laneCount = 3
            val laneSize = roadWidth / laneCount
            val lane = Random.nextInt(0, laneCount)
            val obstacleCenterX = roadLeft + laneSize * (lane + 0.5f)
            obstacles.add(
                Obstacle(
                    x = obstacleCenterX,
                    y = -180f,
                    width = 110f,
                    height = 170f,
                    speed = 320f + score * 1.8f
                )
            )
            spawnTimer = 800f
        }

        playerX += (playerTargetX - playerX) * 0.18f

        val playerRect = RectF(
            playerX - playerWidth / 2f,
            height - 250f,
            playerX + playerWidth / 2f,
            height - 250f + playerHeight
        )

        for (obstacle in obstacles) {
            obstacle.y += obstacle.speed * delta
            val obstacleRect = RectF(
                obstacle.x - obstacle.width / 2f,
                obstacle.y,
                obstacle.x + obstacle.width / 2f,
                obstacle.y + obstacle.height
            )

            if (RectF.intersects(playerRect, obstacleRect)) {
                gameOver = true
            }
        }

        obstacles.removeAll { it.y > height + 200f }
        score += 1
    }

    private fun drawGame() {
        val canvas = holder.lockCanvas() ?: return
        try {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), grassPaint)
            canvas.drawRect(roadLeft, 0f, roadRight, height.toFloat(), roadPaint)

            val lineGap = 90f
            val lineStart = (roadOffset % lineGap)
            for (i in 0..((height / lineGap).toInt() + 2)) {
                val y = i * lineGap - lineStart
                canvas.drawLine(
                    roadLeft + roadWidth / 3f,
                    y,
                    roadLeft + roadWidth / 3f,
                    y + 60f,
                    lanePaint
                )
                canvas.drawLine(
                    roadLeft + (roadWidth * 2f / 3f),
                    y,
                    roadLeft + (roadWidth * 2f / 3f),
                    y + 60f,
                    lanePaint
                )
            }

            val carRect = RectF(
                playerX - playerWidth / 2f,
                height - 250f,
                playerX + playerWidth / 2f,
                height - 250f + playerHeight
            )
            canvas.drawRect(carRect, playerPaint)

            for (obstacle in obstacles) {
                val rect = RectF(
                    obstacle.x - obstacle.width / 2f,
                    obstacle.y,
                    obstacle.x + obstacle.width / 2f,
                    obstacle.y + obstacle.height
                )
                canvas.drawRect(rect, obstaclePaint)
            }

            val scoreText = context.getString(R.string.score) + ": $score"
            canvas.drawText(scoreText, 30f, 80f, uiPaint)

            if (gameOver) {
                val message = context.getString(R.string.game_over)
                canvas.drawText(message, width / 2f, height / 2f, gameOverPaint)
            }
        } finally {
            holder.unlockCanvasAndPost(canvas)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (gameOver) {
                    resetGame()
                    return true
                }
                playerTargetX = event.x.coerceIn(roadLeft + 70f, roadRight - 70f)
            }

            MotionEvent.ACTION_MOVE -> {
                if (!gameOver) {
                    playerTargetX = event.x.coerceIn(roadLeft + 70f, roadRight - 70f)
                }
            }
        }
        return true
    }
}
