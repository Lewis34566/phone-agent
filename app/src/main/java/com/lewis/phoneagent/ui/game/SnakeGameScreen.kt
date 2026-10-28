package com.lewis.phoneagent.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.random.Random
import kotlinx.coroutines.delay

/** 网格 20×20 */
private const val GRID = 20

private data class Cell(val x: Int, val y: Int)

private enum class Dir(val dx: Int, val dy: Int) {
    UP(0, -1), DOWN(0, 1), LEFT(-1, 0), RIGHT(1, 0);

    fun opposite(other: Dir) = dx + other.dx == 0 && dy + other.dy == 0
}

/**
 * 贪吃蛇小游戏：说「玩贪吃蛇」从聊天进入。
 * 滑动手势控制方向，撞墙或咬到自己结束。纯本机 Canvas 实现，零依赖。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnakeGameScreen(onBack: () -> Unit) {
    var snake by remember { mutableStateOf(listOf(Cell(10, 10), Cell(9, 10), Cell(8, 10))) }
    var dir by remember { mutableStateOf(Dir.RIGHT) }
    var food by remember { mutableStateOf(Cell(15, 10)) }
    var score by remember { mutableStateOf(0) }
    var gameOver by remember { mutableStateOf(false) }
    var gameId by remember { mutableStateOf(0) } // 重新开始：改 id 重启循环

    fun restart() {
        snake = listOf(Cell(10, 10), Cell(9, 10), Cell(8, 10))
        dir = Dir.RIGHT
        food = Cell(15, 10)
        score = 0
        gameOver = false
        gameId++
    }

    // 游戏循环：每 150ms 前进一步
    LaunchedEffect(gameId, gameOver) {
        while (!gameOver) {
            delay(150)
            val head = snake.first()
            val newHead = Cell(head.x + dir.dx, head.y + dir.dy)
            // 撞墙或咬到自己 → 结束
            if (newHead.x !in 0 until GRID || newHead.y !in 0 until GRID || newHead in snake) {
                gameOver = true
                break
            }
            if (newHead == food) {
                score++
                snake = listOf(newHead) + snake
                // 随机空位生成新食物
                val occupied = snake.toSet()
                val free = (0 until GRID).flatMap { x -> (0 until GRID).map { y -> Cell(x, y) } }
                    .filter { it !in occupied }
                food = if (free.isEmpty()) { gameOver = true; food } else free[Random.nextInt(free.size)]
            } else {
                snake = listOf(newHead) + snake.dropLast(1)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Snake · Score $score") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to chat")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Swipe to steer, eat food to score",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            val bgColor = MaterialTheme.colorScheme.surfaceVariant // Canvas 闭包非 Composable，颜色先取出
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 8.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(onDragEnd = {
                            // 不需要位移判断，简单用上次拖动方向
                        }) { change, dragAmount ->
                            val (dx, dy) = dragAmount
                            val next = when {
                                abs(dx) > abs(dy) -> if (dx > 0) Dir.RIGHT else Dir.LEFT
                                else -> if (dy > 0) Dir.DOWN else Dir.UP
                            }
                            if (!next.opposite(dir)) dir = next
                            change.consume()
                        }
                    },
            ) {
                val cellW = size.width / GRID
                val cellH = size.height / GRID
                // 棋盘底色
                drawRect(bgColor)
                // 食物
                drawOval(
                    color = Color(0xFFEF4444),
                    topLeft = Offset(food.x * cellW + cellW * 0.15f, food.y * cellH + cellH * 0.15f),
                    size = Size(cellW * 0.7f, cellH * 0.7f),
                )
                // 蛇身（头部深色）
                snake.forEachIndexed { i, cell ->
                    drawRoundRect(
                        color = if (i == 0) Color(0xFF0E7490) else Color(0xFF22D3EE),
                        topLeft = Offset(cell.x * cellW + cellW * 0.05f, cell.y * cellH + cellH * 0.05f),
                        size = Size(cellW * 0.9f, cellH * 0.9f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cellW * 0.2f),
                    )
                }
            }
            if (gameOver) {
                Text("Game over! Score $score", style = MaterialTheme.typography.titleMedium)
                Button(onClick = { restart() }, modifier = Modifier.padding(8.dp)) {
                    Text("Play again")
                }
            }
        }
    }
}
