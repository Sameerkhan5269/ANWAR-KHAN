package com.example.ui.screens

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.data.model.StaffUser
import com.example.data.model.TeamChatMessage
import com.example.ui.theme.LimeGreen
import com.example.ui.theme.LimeSoft
import com.example.ui.theme.LocalSolarColors
import com.example.ui.theme.MaroonContainerLight
import com.example.ui.theme.MaroonPrimary
import java.io.File

private const val LIVE_VIDEO_CALL_URL = "https://meet.jit.si/NB-Engineering-Live-Site-Call"

@Composable
fun TeamChatSection(
    currentUser: StaffUser,
    messages: List<TeamChatMessage>,
    onSendMessage: (text: String, videoUri: Uri?) -> Unit,
    onShowToast: (String) -> Unit
) {
    val context = LocalContext.current
    val solarColors = LocalSolarColors.current
    var messageInput by remember { mutableStateOf("") }
    var pendingCaptureVideoUri by remember { mutableStateOf<Uri?>(null) }

    val recordVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo()
    ) { success ->
        val uri = pendingCaptureVideoUri
        if (success && uri != null) {
            onSendMessage(messageInput, uri)
            messageInput = ""
            pendingCaptureVideoUri = null
        }
    }

    val startVideoCapture = {
        val camDir = File(context.cacheDir, "camera").apply { mkdirs() }
        val videoFile = File(camDir, "site_vid_${System.currentTimeMillis()}.mp4")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            videoFile
        )
        pendingCaptureVideoUri = uri
        recordVideoLauncher.launch(uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startVideoCapture()
        } else {
            onShowToast("Camera permission denied. Use 'Attach Video' to share an existing video.")
        }
    }

    val pickVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onSendMessage(messageInput, uri)
            messageInput = ""
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 1-Tap Live Site Video Call Banner
        Surface(
            color = MaroonContainerLight,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MaroonPrimary.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Live Site Video Call & Field Team Room",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaroonPrimary
                )
                Text(
                    "Connect Udaipur Office & Site Engineers live on video for rooftop inspection or wiring checks.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(LIVE_VIDEO_CALL_URL))
                            context.startActivity(intent)
                        } catch (_: ActivityNotFoundException) {
                            onShowToast("No browser or Jitsi Meet app available to open video call.")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LimeGreen, contentColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("join_live_video_call_btn")
                ) {
                    Icon(Icons.Filled.VideoCall, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Join Live Site Video Call", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Chat Messages Stream
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, solarColors.hairlineBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "No team messages yet. Send a text update or record a short site video for the NB Engineering team.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(messages, key = { it.id }) { msg ->
                    val isMine = msg.senderLoginId.equals(currentUser.loginId, ignoreCase = true)
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isMine) LimeSoft else MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, if (isMine) LimeGreen.copy(alpha = 0.4f) else solarColors.hairlineBorder),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${msg.senderName} (${msg.senderRole.badge})",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isMine) Color(0xFF2B4D12) else MaroonPrimary
                                )
                                Text(
                                    msg.timestamp,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (msg.text.isNotBlank()) {
                                Text(msg.text, style = MaterialTheme.typography.bodyMedium)
                            }
                            if (msg.videoPath.isNotBlank() || msg.videoLabel.isNotBlank()) {
                                val vidFile = msg.videoPath.takeIf { it.isNotBlank() }?.let { File(it) }
                                OutlinedButton(
                                    onClick = {
                                        if (vidFile != null && vidFile.exists()) {
                                            try {
                                                val uri = FileProvider.getUriForFile(
                                                    context,
                                                    "${context.packageName}.fileprovider",
                                                    vidFile
                                                )
                                                val playIntent = Intent(Intent.ACTION_VIEW).apply {
                                                    setDataAndType(uri, "video/*")
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(Intent.createChooser(playIntent, "Play / Share Site Video"))
                                            } catch (_: Exception) {
                                                onShowToast("Unable to open video player on this device.")
                                            }
                                        } else {
                                            onShowToast("Video clip recorded on sender's phone: ${msg.videoLabel}")
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Filled.PlayCircle, contentDescription = null, tint = MaroonPrimary, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(msg.videoLabel.ifBlank { "Play Site Video" }, color = MaroonPrimary)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Composer Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            border = BorderStroke(1.dp, solarColors.hairlineBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val hasCam = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasCam) {
                                startVideoCapture()
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("record_chat_video_btn")
                    ) {
                        Icon(Icons.Filled.Videocam, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Record Video")
                    }
                    OutlinedButton(
                        onClick = {
                            pickVideoLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("attach_chat_video_btn")
                    ) {
                        Icon(Icons.Filled.VideoFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Attach Video")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = messageInput,
                        onValueChange = { messageInput = it },
                        placeholder = { Text("Message NB team…") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("team_chat_input")
                    )
                    IconButton(
                        onClick = {
                            if (messageInput.isNotBlank()) {
                                onSendMessage(messageInput, null)
                                messageInput = ""
                            }
                        },
                        modifier = Modifier.testTag("team_chat_send_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send message", tint = MaroonPrimary)
                    }
                }
            }
        }
    }
}
