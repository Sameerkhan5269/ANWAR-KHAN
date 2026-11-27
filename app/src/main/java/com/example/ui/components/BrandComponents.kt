package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.LimeGreen
import com.example.ui.theme.MaroonPrimary

/**
 * Circular "NB Engineering Works and Electricals" brand logo with Daylight White frame,
 * Deep Maroon Red ring, and Lime Green accent.
 */
@Composable
fun CompanyCircularLogo(
    size: Dp = 44.dp,
    borderColor: Color = LimeGreen,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = Color.White,
        border = BorderStroke(2.dp, borderColor),
        shadowElevation = 2.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.nb_solar_icon_fg),
                contentDescription = "NB Engineering Works and Electricals Logo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .size(width = size * 0.4f, height = 3.dp),
                color = MaroonPrimary,
                shape = CircleShape
            ) {}
        }
    }
}
