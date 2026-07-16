package com.wordmemo.app.ui.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wordmemo.app.ui.theme.RatingAgain
import com.wordmemo.app.ui.theme.RatingEasy
import com.wordmemo.app.ui.theme.RatingGood
import com.wordmemo.app.ui.theme.RatingHard

@Composable
fun RatingBar(
    onRate: (Int) -> Unit,
    enabled: Boolean = true,
    cardState: String = "NEW",
    modifier: Modifier = Modifier
) {
    val subLabels = when (cardState) {
        "NEW" -> listOf("30s", "2m", "10m", "1d")
        "LEARNING" -> listOf("30s", "2m", "1d", "4d")
        "REVIEW" -> listOf("1m", "3d", "7d", "21d")
        "RELEARNING" -> listOf("30s", "1m", "10m", "1d")
        else -> listOf("1m", "10m", "1d", "4d")
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        RatingButton(
            label = "Again",
            subLabel = subLabels[0],
            color = RatingAgain,
            onClick = { onRate(1) },
            enabled = enabled
        )
        RatingButton(
            label = "Hard",
            subLabel = subLabels[1],
            color = RatingHard,
            onClick = { onRate(2) },
            enabled = enabled
        )
        RatingButton(
            label = "Good",
            subLabel = subLabels[2],
            color = RatingGood,
            onClick = { onRate(3) },
            enabled = enabled
        )
        RatingButton(
            label = "Easy",
            subLabel = subLabels[3],
            color = RatingEasy,
            onClick = { onRate(4) },
            enabled = enabled
        )
    }
}

@Composable
private fun RatingButton(
    label: String,
    subLabel: String,
    color: Color,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed = interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed.value) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "rating_scale"
    )

    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier
            .width(96.dp)
            .height(38.dp)
            .scale(scale),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            disabledContainerColor = color.copy(alpha = 0.3f)
        ),
        shape = MaterialTheme.shapes.small,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = subLabel,
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}
