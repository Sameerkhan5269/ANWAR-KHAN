package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ElectricMeter
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SolarPower
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.core.RolePermissions
import com.example.core.safeDouble
import com.example.data.model.DcrPanel
import com.example.data.model.DiscomTestingStatus
import com.example.data.model.MeterType
import com.example.data.model.PanelStatus
import com.example.data.model.PartyProject
import com.example.data.model.RawMaterial
import com.example.data.model.SolarMeter
import com.example.data.model.StaffRole
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalSolarColors
import com.example.ui.theme.SolarAmber

@Composable
fun InventoryScreen(
    role: StaffRole,
    searchQuery: String,
    panels: List<DcrPanel>,
    meters: List<SolarMeter>,
    rawMaterials: List<RawMaterial>,
    parties: List<PartyProject>,
    onSearchChange: (String) -> Unit,
    onAddDcrPanelsBatch: (serials: String, brand: String, wattage: Int, supplier: String, invoiceNo: String) -> Unit,
    onAllocatePanel: (panelId: String, party: PartyProject?) -> Unit,
    onAddMeter: (serial: String, type: MeterType, makePhase: String, status: DiscomTestingStatus) -> Unit,
    onUpdateMeter: (meterId: String, status: DiscomTestingStatus, party: PartyProject?) -> Unit,
    onAddRawStock: (materialId: String?, name: String, category: String, unit: String, supplier: String, qty: Double, minAlert: Double) -> Unit,
    onIssueRawMaterial: (materialId: String, party: PartyProject, qty: Double) -> Unit
) {
    val solarColors = LocalSolarColors.current
    val canAllocate = RolePermissions.canAllocateInventory(role)
    val canAddStock = RolePermissions.canAddStockOrPurchase(role)

    var selectedSubTab by remember { mutableIntStateOf(0) }
    var showAddPanelDialog by remember { mutableStateOf(false) }
    var showAddMeterDialog by remember { mutableStateOf(false) }
    var showAddRawDialog by remember { mutableStateOf(false) }

    var allocatingPanel by remember { mutableStateOf<DcrPanel?>(null) }
    var updatingMeter by remember { mutableStateOf<SolarMeter?>(null) }
    var issuingMaterial by remember { mutableStateOf<RawMaterial?>(null) }
    var restockingMaterial by remember { mutableStateOf<RawMaterial?>(null) }

    val q = searchQuery.trim().lowercase()
    val filteredPanels = remember(panels, q) {
        panels.filter { p ->
            q.isEmpty() ||
                p.serialNumber.lowercase().contains(q) ||
                p.brand.lowercase().contains(q) ||
                p.supplierName.lowercase().contains(q) ||
                p.purchaseInvoiceNo.lowercase().contains(q) ||
                p.linkedPartyName.lowercase().contains(q) ||
                p.status.label.lowercase().contains(q)
        }
    }
    val filteredMeters = remember(meters, q) {
        meters.filter { m ->
            q.isEmpty() ||
                m.serialNumber.lowercase().contains(q) ||
                m.makeAndPhase.lowercase().contains(q) ||
                m.testingStatus.label.lowercase().contains(q) ||
                m.installedPartyName.lowercase().contains(q)
        }
    }
    val filteredMaterials = remember(rawMaterials, q) {
        rawMaterials.filter { rm ->
            q.isEmpty() ||
                rm.name.lowercase().contains(q) ||
                rm.category.lowercase().contains(q) ||
                rm.supplierName.lowercase().contains(q)
        }
    }
    val lowStockCount = rawMaterials.count { it.isLowStock }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Global Instant Inventory Search
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Search DCR serials, meter no., brand, supplier, party…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .testTag("inventory_search_input")
            )

            // Low-Stock Alert Strip
            if (lowStockCount > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "$lowStockCount BOS / Raw Material item(s) at or below minimum stock alert!",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            TabRow(selectedTabIndex = selectedSubTab, modifier = Modifier.padding(top = 6.dp)) {
                Tab(
                    selected = selectedSubTab == 0,
                    onClick = { selectedSubTab = 0 },
                    text = { Text("DCR Panels (${filteredPanels.size})") }
                )
                Tab(
                    selected = selectedSubTab == 1,
                    onClick = { selectedSubTab = 1 },
                    text = { Text("Meters (${filteredMeters.size})") }
                )
                Tab(
                    selected = selectedSubTab == 2,
                    onClick = { selectedSubTab = 2 },
                    text = { Text("BOS Stock (${filteredMaterials.size})") }
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (selectedSubTab) {
                    0 -> {
                        if (filteredPanels.isEmpty()) {
                            item {
                                InventoryEmptyCard(
                                    icon = Icons.Filled.SolarPower,
                                    title = "No DCR Solar Panels Found",
                                    subtitle = "Add single or batch DCR serial numbers (Waaree, Adani, etc.) or scan a supplier bill in BUSY 21."
                                )
                            }
                        } else {
                            items(filteredPanels, key = { it.id }) { panel ->
                                val isInStock = panel.status == PanelStatus.IN_STOCK
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, solarColors.hairlineBorder),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = panel.serialNumber,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontFamily = JetBrainsMonoFontFamily,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Surface(
                                                    color = if (isInStock) solarColors.emeraldContainer else solarColors.solarAmberContainer,
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text(
                                                        text = panel.status.label,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                text = "${panel.brand} • ${panel.wattage}W" +
                                                    if (panel.supplierName.isNotBlank()) " • Vendor: ${panel.supplierName}" else "" +
                                                    if (panel.purchaseInvoiceNo.isNotBlank()) " • Inv: ${panel.purchaseInvoiceNo}" else "",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (panel.linkedPartyName.isNotBlank()) {
                                                Text(
                                                    text = "Allocated Site: ${panel.linkedPartyName}",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = solarColors.debitBlue,
                                                    modifier = Modifier.padding(top = 3.dp)
                                                )
                                            }
                                        }
                                        if (canAllocate) {
                                            OutlinedButton(
                                                onClick = { allocatingPanel = panel },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Text(if (isInStock) "Allocate" else "Change")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        if (filteredMeters.isEmpty()) {
                            item {
                                InventoryEmptyCard(
                                    icon = Icons.Filled.ElectricMeter,
                                    title = "No Solar / Net Meters Logged",
                                    subtitle = "Track Bi-Directional Net Meters and Solar Generation Meters with AVVNL testing status."
                                )
                            }
                        } else {
                            items(filteredMeters, key = { it.id }) { meter ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, solarColors.hairlineBorder),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = meter.serialNumber,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontFamily = JetBrainsMonoFontFamily,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "${meter.meterType.label} • ${meter.makeAndPhase}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Surface(
                                                    color = if (meter.testingStatus == DiscomTestingStatus.INSTALLED ||
                                                        meter.testingStatus == DiscomTestingStatus.TESTED_PASSED
                                                    ) solarColors.emeraldContainer else solarColors.solarAmberContainer,
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text(
                                                        text = meter.testingStatus.label,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                                if (meter.installedPartyName.isNotBlank()) {
                                                    Text(
                                                        text = "Party: ${meter.installedPartyName}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = solarColors.debitBlue
                                                    )
                                                }
                                            }
                                        }
                                        if (canAllocate) {
                                            OutlinedButton(
                                                onClick = { updatingMeter = meter },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Text("Update")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        items(filteredMaterials, key = { it.id }) { rm ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(
                                    1.dp,
                                    if (rm.isLowStock) MaterialTheme.colorScheme.error else solarColors.hairlineBorder
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(rm.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = rm.category + if (rm.supplierName.isNotBlank()) " • Supplier: ${rm.supplierName}" else "",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (rm.isLowStock) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.errorContainer,
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = "LOW STOCK",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "Purchased: ${rm.purchasedQty} ${rm.unit}",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                        Text(
                                            "Used: ${rm.usedQty} ${rm.unit}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = solarColors.creditRed
                                        )
                                        Text(
                                            "Balance: ${rm.remainingQty} ${rm.unit}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (rm.isLowStock) MaterialTheme.colorScheme.error else solarColors.emerald
                                        )
                                    }

                                    Spacer(Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (canAllocate) {
                                            Button(
                                                onClick = { issuingMaterial = rm },
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("Issue to Site")
                                            }
                                        }
                                        if (canAddStock) {
                                            OutlinedButton(
                                                onClick = { restockingMaterial = rm },
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("+ Add Stock")
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

        val showFab = when (selectedSubTab) {
            0 -> canAddStock
            1 -> canAllocate
            else -> canAddStock
        }
        if (showFab) {
            FloatingActionButton(
                onClick = {
                    when (selectedSubTab) {
                        0 -> showAddPanelDialog = true
                        1 -> showAddMeterDialog = true
                        else -> showAddRawDialog = true
                    }
                },
                containerColor = SolarAmber,
                contentColor = DeepNavy,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .testTag("inventory_add_fab")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add Inventory")
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when (selectedSubTab) {
                            0 -> "Add DCR Serials"
                            1 -> "Add Meter"
                            else -> "New Material"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // DIALOGS
    if (showAddPanelDialog) {
        AddDcrPanelsDialog(
            onDismiss = { showAddPanelDialog = false },
            onSave = { serials, brand, watt, sup, inv ->
                onAddDcrPanelsBatch(serials, brand, watt, sup, inv)
                showAddPanelDialog = false
            }
        )
    }

    allocatingPanel?.let { panel ->
        PartyPickerDialog(
            title = "Allocate Panel ${panel.serialNumber}",
            parties = parties,
            allowClear = panel.status == PanelStatus.ALLOCATED,
            onSelect = { party ->
                onAllocatePanel(panel.id, party)
                allocatingPanel = null
            },
            onDismiss = { allocatingPanel = null }
        )
    }

    if (showAddMeterDialog) {
        AddMeterDialog(
            onDismiss = { showAddMeterDialog = false },
            onSave = { serial, type, make, status ->
                onAddMeter(serial, type, make, status)
                showAddMeterDialog = false
            }
        )
    }

    updatingMeter?.let { meter ->
        UpdateMeterDialog(
            meter = meter,
            parties = parties,
            onDismiss = { updatingMeter = null },
            onSave = { status, party ->
                onUpdateMeter(meter.id, status, party)
                updatingMeter = null
            }
        )
    }

    if (showAddRawDialog || restockingMaterial != null) {
        val target = restockingMaterial
        AddRawStockDialog(
            existing = target,
            onDismiss = {
                showAddRawDialog = false
                restockingMaterial = null
            },
            onSave = { name, cat, unit, sup, qty, min ->
                onAddRawStock(target?.id, name, cat, unit, sup, qty, min)
                showAddRawDialog = false
                restockingMaterial = null
            }
        )
    }

    issuingMaterial?.let { rm ->
        IssueMaterialDialog(
            material = rm,
            parties = parties,
            onDismiss = { issuingMaterial = null },
            onIssue = { party, qty ->
                onIssueRawMaterial(rm.id, party, qty)
                issuingMaterial = null
            }
        )
    }
}

@Composable
private fun InventoryEmptyCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    val solarColors = LocalSolarColors.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, solarColors.hairlineBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = solarColors.solarAmber, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AddDcrPanelsDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, Int, String, String) -> Unit
) {
    var serials by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("Waaree") }
    var wattage by remember { mutableStateOf("540") }
    var supplier by remember { mutableStateOf("") }
    var invoice by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add DCR Solar Panels") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = serials,
                    onValueChange = { serials = it },
                    label = { Text("Serial Numbers (one per line or comma-separated) *") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = brand,
                        onValueChange = { brand = it },
                        label = { Text("Brand (Waaree/Adani)") },
                        singleLine = true,
                        modifier = Modifier.weight(1.2f)
                    )
                    OutlinedTextField(
                        value = wattage,
                        onValueChange = { wattage = it.filter { c -> c.isDigit() } },
                        label = { Text("Wattage (W)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(0.8f)
                    )
                }
                OutlinedTextField(
                    value = supplier,
                    onValueChange = { supplier = it },
                    label = { Text("Supplier / Vendor Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = invoice,
                    onValueChange = { invoice = it },
                    label = { Text("Purchase Invoice No.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(serials, brand, wattage.toIntOrNull() ?: 540, supplier, invoice) },
                enabled = serials.isNotBlank()
            ) { Text("Add to Stock") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PartyPickerDialog(
    title: String,
    parties: List<PartyProject>,
    allowClear: Boolean,
    onSelect: (PartyProject?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (allowClear) {
                    OutlinedButton(
                        onClick = { onSelect(null) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Return to In Stock (Unallocate)") }
                }
                if (parties.isEmpty()) {
                    Text("No parties available yet. Create a party in Pipeline first.", style = MaterialTheme.typography.bodySmall)
                } else {
                    parties.forEach { p ->
                        OutlinedButton(
                            onClick = { onSelect(p) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("${p.partyName} (${p.capacityKw} kW)")
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun AddMeterDialog(
    onDismiss: () -> Unit,
    onSave: (String, MeterType, String, DiscomTestingStatus) -> Unit
) {
    var serial by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(MeterType.BI_DIRECTIONAL) }
    var makePhase by remember { mutableStateOf("L&T / 1-Phase") }
    var status by remember { mutableStateOf(DiscomTestingStatus.PENDING) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Net / Solar Meter") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = serial,
                    onValueChange = { serial = it },
                    label = { Text("Meter Serial Number *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                MeterType.entries.forEach { t ->
                    FilterChip(
                        selected = type == t,
                        onClick = { type = t },
                        label = { Text(t.label) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = makePhase,
                    onValueChange = { makePhase = it },
                    label = { Text("Make & Phase (e.g., Secure / 3-Phase)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("AVVNL DISCOM Testing Status", style = MaterialTheme.typography.labelMedium)
                DiscomTestingStatus.entries.forEach { st ->
                    FilterChip(
                        selected = status == st,
                        onClick = { status = st },
                        label = { Text(st.label) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(serial, type, makePhase, status) },
                enabled = serial.isNotBlank()
            ) { Text("Save Meter") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun UpdateMeterDialog(
    meter: SolarMeter,
    parties: List<PartyProject>,
    onDismiss: () -> Unit,
    onSave: (DiscomTestingStatus, PartyProject?) -> Unit
) {
    var status by remember { mutableStateOf(meter.testingStatus) }
    var selectedParty by remember {
        mutableStateOf(parties.firstOrNull { it.id == meter.installedPartyId })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update Meter ${meter.serialNumber}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("DISCOM Testing Status", style = MaterialTheme.typography.labelMedium)
                DiscomTestingStatus.entries.forEach { st ->
                    FilterChip(
                        selected = status == st,
                        onClick = { status = st },
                        label = { Text(st.label) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text("Installed Party Site", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        FilterChip(
                            selected = selectedParty == null,
                            onClick = { selectedParty = null },
                            label = { Text("Unassigned") }
                        )
                    }
                    items(parties) { p ->
                        FilterChip(
                            selected = selectedParty?.id == p.id,
                            onClick = { selectedParty = p },
                            label = { Text(p.partyName) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(status, selectedParty) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddRawStockDialog(
    existing: RawMaterial?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, Double, Double) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var category by remember { mutableStateOf(existing?.category ?: "BOS") }
    var unit by remember { mutableStateOf(existing?.unit ?: "kg") }
    var supplier by remember { mutableStateOf(existing?.supplierName.orEmpty()) }
    var qty by remember { mutableStateOf("") }
    var minAlert by remember { mutableStateOf((existing?.minStockAlert ?: 10.0).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing != null) "Add Stock: ${existing.name}" else "New Raw Material / BOS") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (existing == null) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Material Name *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = category,
                            onValueChange = { category = it },
                            label = { Text("Category") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = unit,
                            onValueChange = { unit = it },
                            label = { Text("Unit (kg/m/Nos)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                OutlinedTextField(
                    value = supplier,
                    onValueChange = { supplier = it },
                    label = { Text("Supplier Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = qty,
                        onValueChange = { qty = it },
                        label = { Text("Purchased Qty *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = minAlert,
                        onValueChange = { minAlert = it },
                        label = { Text("Low-Stock Alert") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(name, category, unit, supplier, safeDouble(qty, 0.0), safeDouble(minAlert, 10.0))
                },
                enabled = (existing != null || name.isNotBlank()) && safeDouble(qty, 0.0) > 0.0
            ) { Text("Update Stock") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun IssueMaterialDialog(
    material: RawMaterial,
    parties: List<PartyProject>,
    onDismiss: () -> Unit,
    onIssue: (PartyProject, Double) -> Unit
) {
    var selectedParty by remember { mutableStateOf(parties.firstOrNull()) }
    var qty by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Issue ${material.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Available Balance: ${material.remainingQty} ${material.unit}",
                    style = MaterialTheme.typography.labelLarge
                )
                OutlinedTextField(
                    value = qty,
                    onValueChange = { qty = it },
                    label = { Text("Quantity to Issue (${material.unit}) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Select Site / Party *", style = MaterialTheme.typography.labelMedium)
                if (parties.isEmpty()) {
                    Text("No parties found. Create a party first.", style = MaterialTheme.typography.bodySmall)
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(parties) { p ->
                            FilterChip(
                                selected = selectedParty?.id == p.id,
                                onClick = { selectedParty = p },
                                label = { Text(p.partyName) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { selectedParty?.let { onIssue(it, safeDouble(qty, 0.0)) } },
                enabled = selectedParty != null && safeDouble(qty, 0.0) > 0.0
            ) { Text("Issue Stock") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
