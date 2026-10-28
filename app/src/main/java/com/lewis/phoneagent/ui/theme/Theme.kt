package com.lewis.phoneagent.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// 品牌色：青蓝（与图标背景一致）
val BrandBlue = Color(0xFF0E7490)
val BrandBlueLight = Color(0xFF7DD3FC)
val BrandBlueDark = Color(0xFF155E75)

private val LightColors = lightColorScheme(
    primary = BrandBlue,
    secondary = BrandBlueLight,
)

private val DarkColors = darkColorScheme(
    primary = BrandBlueLight,
    secondary = BrandBlueDark,
)

/**
 * App 主题：Android 12+ 跟随系统壁纸取色（dynamic color），
 * 老版本和禁用动态色时使用品牌色。
 */
@Composable
fun PhoneAgentTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
