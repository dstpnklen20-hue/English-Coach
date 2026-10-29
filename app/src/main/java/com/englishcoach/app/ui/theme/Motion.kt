package com.englishcoach.app.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

/** Единые мягкие анимации: плавный разгон и торможение, без резких скачков. */
object Motion {
    fun <T> soft(durationMs: Int = 300, delayMs: Int = 0): TweenSpec<T> = tween(durationMs, delayMs, FastOutSlowInEasing)

    /** Смена экрана — «растворение»: старый быстро гаснет, новый проявляется с лёгким приближением. */
    val screenEnter: EnterTransition =
        fadeIn(tween(260, delayMillis = 80, easing = LinearOutSlowInEasing)) + scaleIn(soft(340), initialScale = 0.96f)
    val screenExit: ExitTransition = fadeOut(tween(120, easing = FastOutSlowInEasing))
    val screenPopExit: ExitTransition = fadeOut(tween(120, easing = FastOutSlowInEasing)) + scaleOut(soft(200), targetScale = 0.96f)

    /** Новое упражнение или карточка: проявляется с небольшим сдвигом справа. */
    val exerciseEnter: EnterTransition = fadeIn(soft(260)) + slideInHorizontally(soft(340)) { it / 12 }
    val exerciseExit: ExitTransition = fadeOut(soft(140)) + slideOutHorizontally(soft(240)) { -it / 12 }

    /** Появление блока (подсказка, ответ, панель): раскрывается по высоте и проявляется. */
    val appear: EnterTransition = fadeIn(soft(240, 60)) + expandVertically(soft(300))
    val disappear: ExitTransition = fadeOut(soft(140)) + shrinkVertically(soft(240))
}
