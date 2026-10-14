package com.unifiedmessages.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.unifiedmessages.app.ui.components.AppSurface
import com.unifiedmessages.app.ui.components.appHazeSource
import com.unifiedmessages.app.ui.theme.LocalAppTheme

/** First-launch introduction; SMS role and permissions are requested after Get started. */
@Composable
fun WelcomeScreen(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAppTheme.current
    BoxWithConstraints(
        modifier = modifier.fillMaxSize().background(theme.welcomeBackground),
    ) {
        Canvas(Modifier.matchParentSize().appHazeSource().clearAndSetSemantics { }) {
            drawRect(theme.welcomeBackground)
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        theme.welcomeGlowPrimary.copy(alpha = 0.78f),
                        theme.welcomeGlowSecondary.copy(alpha = 0.45f),
                        theme.welcomeBackground.copy(alpha = 0f),
                    ),
                    center = Offset(size.width * 0.50f, size.height * 0.47f),
                    radius = size.width.coerceAtLeast(1f) * 1.03f,
                ),
            )
        }

        val compact = maxHeight < 550.dp
        val markSize = if (compact) 76.dp else (maxWidth * 0.27f).coerceIn(84.dp, 112.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = 28.dp, vertical = if (compact) 20.dp else 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Spacer(Modifier.height(if (compact) 0.dp else 42.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(
                    modifier = Modifier
                        .size(markSize)
                        .clearAndSetSemantics { contentDescription = "Chats symbol" },
                ) {
                    val inset = size.minDimension * 0.19f
                    val diameter = size.minDimension - inset * 2f
                    val stroke = size.minDimension * 0.09f
                    repeat(7) { segment ->
                        drawArc(
                            color = theme.welcomeOnBackground,
                            startAngle = -92f + segment * (360f / 7f),
                            sweepAngle = if (segment % 2 == 0) 36f else 39f,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = Size(diameter, diameter),
                            style = Stroke(width = stroke, cap = StrokeCap.Butt),
                        )
                    }
                }
                Spacer(Modifier.height(if (compact) 18.dp else 27.dp))
                Text(
                    text = "Welcome to chats",
                    color = theme.welcomeOnBackground,
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Your messages in one place.",
                    color = theme.welcomeSecondary,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Next, connect SMS and MMS on this phone",
                    color = theme.welcomeSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                AppSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(62.dp)
                        .clip(RoundedCornerShape(34.dp))
                        .clickable(role = Role.Button, onClick = onContinue),
                    shape = RoundedCornerShape(34.dp),
                    color = theme.welcomeSurface,
                    contentPadding = PaddingValues(6.dp),
                    backdropColor = theme.welcomeBackground,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier.size(50.dp).background(theme.welcomeActionSurface, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                                contentDescription = null,
                                tint = theme.welcomeOnActionSurface,
                            )
                        }
                        Text(
                            text = "Get started",
                            color = theme.welcomeOnBackground,
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(50.dp))
                    }
                }
            }
        }
    }
}
