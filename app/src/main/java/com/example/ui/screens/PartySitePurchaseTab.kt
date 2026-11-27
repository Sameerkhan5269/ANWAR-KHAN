package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.core.RolePermissions
import com.example.core.formatInr
import com.example.core.safeDouble
import com.example.data.model.PartyProject
import com.example.data.model.SiteMaterialPurchase
import com.example.data.model.SitePurchasePaymentSource
import com.example.data.model.StaffRole
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LimeGreen
import com.example.ui.theme.LimeSoft
import com.example.ui.theme.LocalSolarColors
import com.example.ui.theme.MaroonContainerLight
import com.example.ui.theme.MaroonPrimary
import java.io.File

private val QUICK_HARDWARE_ITEMS = listOf(
    "GI Pipes / Angles",
    "Welding Rods",
    "Cement / Sand / Gitti",
    "PVC Conduit Pipes",
    "Flexible Cables",
    "Earthing Salt / Charcoal / Chemical",
    "Nuts / Bolts / Fasteners",
    "Cable Ties",
    "Copper Lugs",
    "MC4 Connectors"
)

@Composable
fun PartySitePurchaseTab(
    party: PartyProject,
    role: StaffRole,
    supervisorImprestBalance: Double,
    onAddSitePurchase: (
        itemDescription: String,
        quantity: String,
        vendorName: String,
        amount: Double,
        paymentSource: SitePurchasePaymentSource,
        billPhotoUri: Uri?
    ) -> Unit
) {
    val solarColors = LocalSolarColors.current
    var showAddDialog by remember { mutableStateOf(false) }
    var previewPurchase by remember { mutableStateOf<SiteMaterialPurchase?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Imprest Cash & Total Site Procurement Banner
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, solarColors.hairlineBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "On-Site Material Purchases",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${party.sitePurchases.size} local bills logged • Auto-synced to BUSY 21 Cost Center",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(color = MaroonContainerLight, shape = RoundedCornerShape(8.dp)) {
                            Text(
                                formatInr(party.totalOnSitePurchases),
                                style = MaterialTheme.typography.titleSmall,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = MaroonPrimary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    if (role == StaffRole.SUPERVISOR || RolePermissions.canManageImprest(role)) {
                        Surface(
                            color = LimeSoft,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, LimeGreen.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.AccountBalanceWallet,
                                        contentDescription = null,
                                        tint = Color(0xFF2B4D12),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "Supervisor Imprest Cash Wallet",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color(0xFF2B4D12),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Text(
                                    formatInr(supervisorImprestBalance),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = if (supervisorImprestBalance < 0) solarColors.creditRed else Color(0xFF2B4D12)
                                )
                            }
                        }
                    }

                    Button(
                        onClick = { showAddDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("add_site_purchase_btn")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add Site Material Purchase", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (party.sitePurchases.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, solarColors.hairlineBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Filled.ShoppingCart, contentDescription = null, tint = MaroonPrimary, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("No On-Site Purchases Logged", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "Log local GI pipes, welding rods, cement/sand/gitti, conduit pipes, earthing chemical, fasteners, or MC4 connectors with kacchi parchi camera photo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        } else {
            items(party.sitePurchases, key = { it.id }) { purchase ->
                SitePurchaseCard(
                    purchase = purchase,
                    onClick = { previewPurchase = purchase }
                )
            }
        }
    }

    if (showAddDialog) {
        AddSiteMaterialPurchaseDialog(
            onDismiss = { showAddDialog = false },
            onSave = { desc, qty, vendor, amt, source, uri ->
                onAddSitePurchase(desc, qty, vendor, amt, source, uri)
                showAddDialog = false
            }
        )
    }

    previewPurchase?.let { item ->
        AlertDialog(
            onDismissRequest = { previewPurchase = null },
            title = { Text(item.itemDescription) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${item.quantity} • ${item.vendorName} • ${formatInr(item.amount)}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaroonPrimary
                    )
                    Text(
                        "Paid via: ${item.paymentSource.label}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (item.linkedVoucherNo.isNotBlank()) {
                        Text(
                            "BUSY 21 Voucher: ${item.linkedVoucherNo}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = LimeGreen
                        )
                    }
                    Text(
                        "Logged ${item.purchaseDate} by ${item.purchasedByName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val file = item.billPhotoPath.takeIf { it.isNotBlank() }?.let { File(it) }
                    if (file != null && file.exists()) {
                        AsyncImage(
                            model = file,
                            contentDescription = "Shop Bill / Kacchi Parchi",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                    } else if (item.billPhotoBase64.isNotBlank()) {
                        val bmp = remember(item.billPhotoBase64) {
                            runCatching {
                                val bytes = Base64.decode(item.billPhotoBase64, Base64.DEFAULT)
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            }.getOrNull()
                        }
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Shop Bill / Kacchi Parchi",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { previewPurchase = null }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun SitePurchaseCard(
    purchase: SiteMaterialPurchase,
    onClick: () -> Unit
) {
    val solarColors = LocalSolarColors.current
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, solarColors.hairlineBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("site_purchase_card_${purchase.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (purchase.billPhotoBase64.isNotBlank()) {
                    val bmp = remember(purchase.billPhotoBase64) {
                        runCatching {
                            val bytes = Base64.decode(purchase.billPhotoBase64, Base64.DEFAULT)
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        }.getOrNull()
                    }
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Bill photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(Icons.Filled.Receipt, contentDescription = null, tint = MaroonPrimary)
                    }
                } else {
                    Icon(Icons.Filled.Receipt, contentDescription = null, tint = MaroonPrimary)
                }
            }

            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(purchase.itemDescription, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "${purchase.quantity} • ${purchase.vendorName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = LimeSoft, shape = RoundedCornerShape(4.dp)) {
                        Text(
                            purchase.paymentSource.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF2B4D12),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    if (purchase.linkedVoucherNo.isNotBlank()) {
                        Text(
                            purchase.linkedVoucherNo,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatInr(purchase.amount),
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = MaroonPrimary
                )
                Text(
                    purchase.purchaseDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AddSiteMaterialPurchaseDialog(
    onDismiss: () -> Unit,
    onSave: (
        itemDescription: String,
        quantity: String,
        vendorName: String,
        amount: Double,
        paymentSource: SitePurchasePaymentSource,
        billPhotoUri: Uri?
    ) -> Unit
) {
    val context = LocalContext.current
    var itemDesc by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("1") }
    var vendorName by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var paymentSource by remember { mutableStateOf(SitePurchasePaymentSource.SUPERVISOR_IMPREST) }
    var selectedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && pendingCameraUri != null) {
            selectedPhotoUri = pendingCameraUri
            cameraError = null
        }
    }

    val launchCameraCapture = {
        val camDir = File(context.cacheDir, "camera").apply { mkdirs() }
        val photoFile = File(camDir, "bill_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile
        )
        pendingCameraUri = uri
        takePictureLauncher.launch(uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCameraCapture()
        } else {
            cameraError = "Camera permission denied. You can still attach a bill photo from Gallery."
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedPhotoUri = uri
            cameraError = null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Site Material Purchase", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Quick Hardware Presets", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(QUICK_HARDWARE_ITEMS) { preset ->
                        FilterChip(
                            selected = itemDesc == preset,
                            onClick = { itemDesc = preset },
                            label = { Text(preset) }
                        )
                    }
                }

                OutlinedTextField(
                    value = itemDesc,
                    onValueChange = { itemDesc = it },
                    label = { Text("Item Description *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("site_purchase_item_input")
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it },
                        label = { Text("Quantity") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Amount (₹) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("site_purchase_amount_input")
                    )
                }

                OutlinedTextField(
                    value = vendorName,
                    onValueChange = { vendorName = it },
                    label = { Text("Local Shop / Vendor Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Payment Source *", style = MaterialTheme.typography.labelMedium)
                SitePurchasePaymentSource.entries.forEach { src ->
                    FilterChip(
                        selected = paymentSource == src,
                        onClick = { paymentSource = src },
                        label = { Text(src.label) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Text("Shop Bill / Kacchi Parchi Photo", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val hasCam = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasCam) {
                                launchCameraCapture()
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("site_purchase_camera_btn")
                    ) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Camera")
                    }
                    OutlinedButton(
                        onClick = {
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("site_purchase_gallery_btn")
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Gallery")
                    }
                }

                cameraError?.let { err ->
                    Text(err, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }

                selectedPhotoUri?.let { uri ->
                    Surface(
                        color = LimeSoft,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            AsyncImage(
                                model = uri,
                                contentDescription = "Selected bill photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(6.dp))
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Bill photo attached ✓",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF2B4D12),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "Remove",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaroonPrimary,
                                modifier = Modifier
                                    .clickable { selectedPhotoUri = null }
                                    .padding(4.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            val amt = safeDouble(amountText, 0.0)
            Button(
                onClick = {
                    onSave(itemDesc, quantity, vendorName, amt, paymentSource, selectedPhotoUri)
                },
                enabled = itemDesc.isNotBlank() && amt > 0.0,
                colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary)
            ) {
                Text("Save & Sync")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
