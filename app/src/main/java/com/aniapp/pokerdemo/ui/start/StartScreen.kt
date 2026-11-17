package com.aniapp.pokerdemo.ui.start

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aniapp.pokerdemo.R
import com.aniapp.pokerdemo.engine.Card
import com.aniapp.pokerdemo.engine.Suit
import com.aniapp.pokerdemo.ui.card.CardSize
import com.aniapp.pokerdemo.ui.card.PlayingCard
import com.aniapp.pokerdemo.ui.theme.CardWhite
import com.aniapp.pokerdemo.ui.theme.FeltDark
import com.aniapp.pokerdemo.ui.theme.FeltGreen
import com.aniapp.pokerdemo.ui.theme.Gold
import com.aniapp.pokerdemo.ui.theme.TableBackground

@Composable
fun StartScreen(onStart: () -> Unit) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val fade by animateFloatAsState(if (entered) 1f else 0f, tween(600), label = "startFade")
    Box(Modifier.fillMaxSize().background(TableBackground)) {
        Canvas(Modifier.fillMaxSize()) {
            drawOval(
                brush = Brush.radialGradient(listOf(FeltGreen, FeltDark), center = center, radius = size.minDimension * 0.75f),
                topLeft = Offset(center.x - size.width * 0.42f, center.y - size.height * 0.30f),
                size = Size(size.width * 0.84f, size.height * 0.60f),
                alpha = 0.35f
            )
        }
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .graphicsLayer {
                    alpha = fade
                    translationY = (1f - fade) * 60f
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(contentAlignment = Alignment.Center) {
                PlayingCard(
                    Card(14, Suit.SPADES), true, CardSize(76.dp),
                    Modifier.offset(x = (-64).dp, y = 12.dp).rotate(-16f)
                )
                PlayingCard(
                    Card(13, Suit.HEARTS), true, CardSize(76.dp),
                    Modifier.offset(x = 64.dp, y = 12.dp).rotate(16f)
                )
                Box(
                    Modifier
                        .size(116.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .border(2.dp, Gold.copy(alpha = 0.8f), RoundedCornerShape(26.dp))
                ) {
                    Image(
                        painter = painterResource(R.mipmap.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            scaleX = 1.45f
                            scaleY = 1.45f
                        }
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
            Text("德州扑克", fontSize = 38.sp, fontWeight = FontWeight.Bold, color = CardWhite)
            Spacer(Modifier.height(6.dp))
            Text("TEXAS HOLD'EM", fontSize = 13.sp, letterSpacing = 5.sp, fontWeight = FontWeight.Medium, color = Gold)
            Spacer(Modifier.height(10.dp))
            Text("单机 · 与电脑 heads-up 对决", fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f))
            Spacer(Modifier.height(44.dp))
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(0.62f).height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color(0xFF241B04))
            ) {
                Text("开始游戏", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(18.dp))
            Text("盲注 10/20 · 起始筹码 2000", fontSize = 12.sp, color = Color.White.copy(alpha = 0.45f))
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.app_version), fontSize = 11.sp, color = Color.White.copy(alpha = 0.3f))
        }
    }
}