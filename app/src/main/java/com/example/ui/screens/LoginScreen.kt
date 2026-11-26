package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.StaffRole
import com.example.data.model.StaffUser
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalSolarColors
import com.example.ui.theme.SolarAmber

@OptIn(ExperimentalLayoutApi::class)
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
                                    DeepNavy.copy(alpha = 0.65f),
                                    DeepNavy.copy(alpha = 0.92f)
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
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(SolarAmber),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SolarPower,
                                contentDescription = "NB Solar ERP Logo",
                                tint = DeepNavy,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "NB ENGINEERING WORKS & ELECTRICALS",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Road No. 5, Madri Industrial Area, Udaipur (Raj.)",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFCBD5E1)
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = solarColors.emerald.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(999.dp),
                            border = BorderStroke(1.dp, solarColors.emerald.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.CloudDone,
                                    contentDescription = null,
                                    tint = Color(0xFF6EE7B7),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Supabase Live Sync",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFD1FAE5)
                                )
                            }
                        }
                        Surface(
                            color = SolarAmber.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(999.dp),
                            border = BorderStroke(1.dp, SolarAmber.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.Security,
                                    contentDescription = null,
                                    tint = SolarAmber,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Role Privacy Guard",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFFEF3C7)
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
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, solarColors.hairlineBorder),
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
                            containerColor = DeepNavy,
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = "Sign In to NB Solar ERP",
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

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        staffUsers.forEach { staff ->
                            val isSelected = loginId.equals(staff.loginId, ignoreCase = true)
                            val isPrivateFieldRole = staff.role == StaffRole.SUPERVISOR || staff.role == StaffRole.HELPER
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
                                    .testTag("quick_role_${staff.role.name.lowercase()}")
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
                                            text = staff.displayName,
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
