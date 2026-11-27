package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SolarPower
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.StaffRole
import com.example.data.model.StaffUser
import com.example.ui.components.CompanyCircularLogo
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LimeGreen
import com.example.ui.theme.LocalSolarColors
import com.example.ui.theme.MaroonDark
import com.example.ui.theme.MaroonPrimary

@Composable
fun LoginScreen(
    staffUsers: List<StaffUser>,
    errorMessage: String?,
    onLogin: (loginId: String, pin: String) -> Unit
) {
    var loginId by remember { mutableStateOf("owner@nb") }
    var pin by remember { mutableStateOf("") }
    var showPin by remember { mutableStateOf(false) }
    val solarColors = LocalSolarColors.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Hero Banner Header with Udaipur Solar Industrial Artwork
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_solar_hero_banner),
                    contentDescription = "NB Engineering Solar Rooftop Array Udaipur",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaroonPrimary.copy(alpha = 0.72f),
                                    MaroonDark.copy(alpha = 0.94f)
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CompanyCircularLogo(
                            size = 52.dp,
                            borderColor = LimeGreen
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "NB ENGINEERING WORKS AND ELECTRICALS",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Rama Phosphates Ltd. Plant, Shed No. 1, Udaipur (Raj.)",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFFFE4E2)
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = LimeGreen,
                            shape = RoundedCornerShape(999.dp),
                            border = BorderStroke(1.dp, Color(0xFFDCEDC8))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.CloudDone,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Supabase Live Sync",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                        Surface(
                            color = Color.White.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(999.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.Security,
                                    contentDescription = null,
                                    tint = Color(0xFFDCEDC8),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Role Privacy Guard",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            // Main Login Card (Responsive width for tablets/desktops)
            Card(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .widthIn(max = 520.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, solarColors.gridBorderStrong),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Staff Portal Sign In",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Enter your assigned Login ID & 4-digit PIN to access your workspace.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                    )

                    OutlinedTextField(
                        value = loginId,
                        onValueChange = { loginId = it },
                        label = { Text("Staff Login ID") },
                        placeholder = { Text("e.g. owner@nb") },
                        leadingIcon = { Icon(Icons.Filled.Badge, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("login_id_input")
                    )

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = pin,
                        onValueChange = { if (it.length <= 6) pin = it.filter { ch -> ch.isDigit() } },
                        label = { Text("4-Digit PIN") },
                        placeholder = { Text("••••") },
                        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { showPin = !showPin }) {
                                Icon(
                                    imageVector = if (showPin) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (showPin) "Hide PIN" else "Show PIN"
                                )
                            }
                        },
                        visualTransformation = if (showPin) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("login_pin_input")
                    )

                    if (!errorMessage.isNullOrBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = errorMessage,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = { onLogin(loginId, pin) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("login_submit_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaroonPrimary,
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = "Sign In to NB Solar & Wind ERP",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = "QUICK STAFF DIRECTORY (TAP TO SELECT ID)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))

                    WrapRow(
                        horizontalGap = 8.dp,
                        verticalGap = 8.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        staffUsers.forEach { staff ->
                            val isSelected = loginId.equals(staff.loginId, ignoreCase = true)
                            val isPrivateFieldRole = staff.role == StaffRole.SUPERVISOR || staff.role == StaffRole.HELPER
                            val divTag = staff.assignedDivision?.shortLabel ?: "All"
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) solarColors.solarAmberContainer
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) solarColors.solarAmber else solarColors.hairlineBorder
                                ),
                                modifier = Modifier
                                    .clickable {
                                        loginId = staff.loginId
                                        pin = ""
                                    }
                                    .testTag("quick_role_${staff.loginId}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isPrivateFieldRole) solarColors.emerald
                                                else solarColors.solarAmber
                                            )
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Column {
                                        Text(
                                            text = "${staff.displayName} ($divTag)",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = staff.loginId,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = JetBrainsMonoFontFamily,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WrapRow(
    modifier: Modifier = Modifier,
    horizontalGap: Dp = 8.dp,
    verticalGap: Dp = 8.dp,
    content: @Composable () -> Unit
) {
    Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        val hGapPx = horizontalGap.roundToPx()
        val vGapPx = verticalGap.roundToPx()
        val maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
        val childConstraints = constraints.copy(minWidth = 0, minHeight = 0)

        val rows = mutableListOf<List<Placeable>>()
        var currentRow = mutableListOf<Placeable>()
        var currentRowWidth = 0

        measurables.forEach { measurable ->
            val placeable = measurable.measure(childConstraints)
            val neededWidth = if (currentRow.isEmpty()) placeable.width else currentRowWidth + hGapPx + placeable.width
            if (currentRow.isNotEmpty() && neededWidth > maxWidth) {
                rows.add(currentRow)
                currentRow = mutableListOf(placeable)
                currentRowWidth = placeable.width
            } else {
                currentRow.add(placeable)
                currentRowWidth = neededWidth
            }
        }
        if (currentRow.isNotEmpty()) {
            rows.add(currentRow)
        }

        val totalWidth = if (constraints.hasBoundedWidth) {
            constraints.maxWidth
        } else {
            rows.maxOfOrNull { row ->
                row.sumOf { it.width } + (row.size - 1).coerceAtLeast(0) * hGapPx
            } ?: 0
        }.coerceIn(constraints.minWidth, constraints.maxWidth)

        val totalHeight = if (rows.isEmpty()) {
            constraints.minHeight
        } else {
            val rowHeights = rows.sumOf { row -> row.maxOfOrNull { it.height } ?: 0 }
            val gapsHeight = (rows.size - 1).coerceAtLeast(0) * vGapPx
            (rowHeights + gapsHeight).coerceIn(constraints.minHeight, constraints.maxHeight)
        }

        layout(totalWidth, totalHeight) {
            var y = 0
            rows.forEach { row ->
                var x = 0
                val rowHeight = row.maxOfOrNull { it.height } ?: 0
                row.forEach { placeable ->
                    placeable.placeRelative(x = x, y = y)
                    x += placeable.width + hGapPx
                }
                y += rowHeight + vGapPx
            }
        }
    }
}
