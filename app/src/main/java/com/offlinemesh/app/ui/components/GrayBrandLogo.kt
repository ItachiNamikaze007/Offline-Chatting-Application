package com.offlinemesh.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offlinemesh.app.R
import com.offlinemesh.app.ui.theme.MeshCyan
import com.offlinemesh.app.ui.theme.MeshEmerald
import com.offlinemesh.app.ui.theme.MeshIndigo

/**
 * Official GRAY Brand Presentation Component.
 * Displays the supplied official GRAY logo without modification,
 * accompanied by a clean, modern, premium sans-serif GRAY wordmark.
 */
@Composable
fun GrayBrandLogo(
    modifier: Modifier = Modifier,
    logoSize: Dp = 120.dp,
    showWordmark: Boolean = true,
    wordmarkSize: Int = 32,
    subtitle: String? = null,
    showGlowBorder: Boolean = false
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = if (showGlowBorder) {
                Modifier
                    .size(logoSize + 12.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MeshCyan.copy(alpha = 0.35f),
                                MeshEmerald.copy(alpha = 0.25f),
                                MeshIndigo.copy(alpha = 0.35f)
                            )
                        )
                    )
                    .padding(2.dp)
            } else {
                Modifier
            }
        ) {
            Image(
                painter = painterResource(id = R.drawable.gray_logo),
                contentDescription = "Official GRAY App Logo",
                modifier = Modifier
                    .size(logoSize)
                    .clip(RoundedCornerShape(24.dp)),
                contentScale = ContentScale.Fit
            )
        }

        if (showWordmark) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "GRAY",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    fontSize = wordmarkSize.sp,
                    letterSpacing = 6.sp,
                    textAlign = TextAlign.Center
                ),
                color = MaterialTheme.colorScheme.onBackground
            )

            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = subtitle.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 2.5.sp,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center
                    ),
                    color = MeshCyan.copy(alpha = 0.9f)
                )
            }
        }
    }
}

/**
 * Compact Header Brand Badge for Top Bars & In-App Navigation Headers.
 */
@Composable
fun GrayHeaderBrand(
    modifier: Modifier = Modifier,
    logoSize: Dp = 38.dp,
    subtitle: String = "Off-Grid P2P Radio Mesh"
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = R.drawable.gray_logo),
            contentDescription = "GRAY Logo",
            modifier = Modifier
                .size(logoSize)
                .clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Fit
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = "GRAY",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 3.sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 0.5.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
