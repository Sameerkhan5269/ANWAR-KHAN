package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.core.formatInr
import com.example.core.safeDouble
import com.example.core.todayIsoDate
import com.example.data.model.LeadSource
import com.example.data.model.LeadStatus
import com.example.data.model.ParsedLekhaJokhaLine
import com.example.data.model.ParsedWhatsAppUpdate
import com.example.data.model.PartyProject
import com.example.data.model.ProjectDivision
import com.example.data.model.RoofStructureType
import com.example.ui.theme.LimeGreen
import com.example.ui.theme.LocalSolarColors
import com.example.ui.theme.MaroonPrimary
import com.example.ui.theme.TabularNumberStyle

@Composable
fun AddPartyDialog(
    canSetPartnerSplit: Boolean,
    initialDivision: ProjectDivision = ProjectDivision.SOLAR,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        mobile: String,
        address: String,
        cityOrLocation: String,
        consumerNo: String,
        capacityKw: Double,
        leadSource: LeadSource,
        referrerName: String,
        referrerPhone: String,
        leadDate: String,
        roofStructureType: RoofStructureType,
        quotedRatePerKw: Double,
        nextFollowUpDate: String,
        siteSurveyNotes: String,
        leadStatus: LeadStatus,
        projectValue: Double,
        loan1: Double,
        loan2: Double,
        subsidy: Double,
        partnerPct: Double,
        division: ProjectDivision
    ) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var cityOrLocation by remember { mutableStateOf("Udaipur") }
    var address by remember { mutableStateOf("") }
    var consumerNo by remember { mutableStateOf("") }
    var capacityKw by remember { mutableStateOf("3.0") }
    var selectedDivision by remember(initialDivision) { mutableStateOf(initialDivision) }
    var selectedSource by remember { mutableStateOf(LeadSource.NB_DIRECT) }
    var referrerName by remember { mutableStateOf("") }
    var referrerPhone by remember { mutableStateOf("") }
    var leadDate by remember { mutableStateOf(todayIsoDate()) }
    var roofType by remember { mutableStateOf(RoofStructureType.RCC_ROOF) }
    var quotedRatePerKw by remember { mutableStateOf("56000") }
    var nextFollowUpDate by remember { mutableStateOf("") }
    var siteSurveyNotes by remember { mutableStateOf("") }
    var leadStatus by remember { mutableStateOf(LeadStatus.NEW_ENQUIRY) }
    var projectValue by remember { mutableStateOf("168000") }
    var loan1 by remember { mutableStateOf("100000") }
    var loan2 by remember { mutableStateOf("50000") }
    var subsidy by remember { mutableStateOf("78000") }
    var partnerPct by remember { mutableStateOf("0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Party / Solar & Wind Lead", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Customer / Party Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("new_party_name_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = mobile,
                        onValueChange = { mobile = it },
                        label = { Text("Consumer Mobile") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.weight(1.2f)
                    )
                    OutlinedTextField(
                        value = capacityKw,
                        onValueChange = { capacityKw = it },
                        label = { Text("Proposed kW") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = cityOrLocation,
                        onValueChange = { cityOrLocation = it },
                        label = { Text("Site Location / City") },
                        singleLine = true,
                        modifier = Modifier.weight(1.2f)
                    )
                    OutlinedTextField(
                        value = leadDate,
                        onValueChange = { leadDate = it },
                        label = { Text("Lead Date") },
                        placeholder = { Text("YYYY-MM-DD") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Full Site Address") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = consumerNo,
                    onValueChange = { consumerNo = it },
                    label = { Text("DISCOM K-Number / Consumer No.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Project Division *", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(ProjectDivision.entries) { div ->
                        FilterChip(
                            selected = selectedDivision == div,
                            onClick = { selectedDivision = div },
                            label = { Text(div.label) }
                        )
                    }
                }

                Text("Lead Source Priority *", style = MaterialTheme.typography.labelMedium)
                LeadSource.entries.forEach { src ->
                    FilterChip(
                        selected = selectedSource == src,
                        onClick = {
                            selectedSource = src
                            if (src == LeadSource.THAKUR_JI && partnerPct == "0") partnerPct = "10"
                        },
                        label = { Text(src.label) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (selectedSource != LeadSource.NB_DIRECT) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = referrerName,
                            onValueChange = { referrerName = it },
                            label = { Text("Referrer Name") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = referrerPhone,
                            onValueChange = { referrerPhone = it },
                            label = { Text("Referrer Phone") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Text("Roof Structure Type", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(RoofStructureType.entries) { rt ->
                        FilterChip(
                            selected = roofType == rt,
                            onClick = { roofType = rt },
                            label = { Text(rt.label) }
                        )
                    }
                }

                Text("Lead Status", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(LeadStatus.entries) { st ->
                        FilterChip(
                            selected = leadStatus == st,
                            onClick = { leadStatus = st },
                            label = { Text(st.label) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = quotedRatePerKw,
                        onValueChange = {
                            quotedRatePerKw = it
                            val kw = safeDouble(capacityKw, 3.0)
                            val rate = safeDouble(it, 0.0)
                            if (rate > 0.0) projectValue = (kw * rate).toLong().toString()
                        },
                        label = { Text("Quoted Rate (₹/kW)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = projectValue,
                        onValueChange = { projectValue = it },
                        label = { Text("Deal Value (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = nextFollowUpDate,
                        onValueChange = { nextFollowUpDate = it },
                        label = { Text("Next Follow-Up Date") },
                        placeholder = { Text("YYYY-MM-DD") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = subsidy,
                        onValueChange = { subsidy = it },
                        label = { Text("Subsidy (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = siteSurveyNotes,
                    onValueChange = { siteSurveyNotes = it },
                    label = { Text("Site Survey Notes / Shading / Sanction Load") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = loan1,
                        onValueChange = { loan1 = it },
                        label = { Text("Loan 1st Tranche") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = loan2,
                        onValueChange = { loan2 = it },
                        label = { Text("Loan 2nd Tranche") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (canSetPartnerSplit) {
                    OutlinedTextField(
                        value = partnerPct,
                        onValueChange = { partnerPct = it },
                        label = { Text("Partner Split / Commission % (Owner Only)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        name,
                        mobile,
                        address.ifBlank { cityOrLocation },
                        cityOrLocation,
                        consumerNo,
                        safeDouble(capacityKw, 3.0),
                        selectedSource,
                        referrerName,
                        referrerPhone,
                        leadDate,
                        roofType,
                        safeDouble(quotedRatePerKw, 0.0),
                        nextFollowUpDate,
                        siteSurveyNotes,
                        leadStatus,
                        safeDouble(projectValue, 0.0),
                        safeDouble(loan1, 0.0),
                        safeDouble(loan2, 0.0),
                        safeDouble(subsidy, 0.0),
                        if (canSetPartnerSplit) safeDouble(partnerPct, 0.0) else 0.0,
                        selectedDivision
                    )
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary)
            ) {
                Text("Create Party")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun WhatsAppParserDialog(
    parties: List<PartyProject>,
    onPreview: (String) -> ParsedWhatsAppUpdate,
    onApply: (ParsedWhatsAppUpdate, String?) -> Unit,
    onDismiss: () -> Unit
) {
    var message by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<ParsedWhatsAppUpdate?>(null) }
    var overridePartyId by remember { mutableStateOf<String?>(null) }
    val solarColors = LocalSolarColors.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("WhatsApp Stage Parser") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Paste a site update from the NB Solar WhatsApp group (e.g., \"Ramesh Sharma 5kW installation done\" or \"Meera Devi AVVNL net meter installed\").",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = message,
                    onValueChange = {
                        message = it
                        preview = if (it.isNotBlank()) onPreview(it) else null
                        overridePartyId = preview?.matchedPartyId
                    },
                    label = { Text("Paste WhatsApp Message") },
                    minLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("whatsapp_message_input")
                )

                preview?.let { p ->
                    Surface(
                        color = solarColors.emeraldContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Detected Party: ${p.matchedPartyName}" +
                                    if (p.matchedPartyId != null) " (Matched)" else " (New/Unmatched)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            if (p.detectedKw != null) {
                                Text("Detected Capacity: ${p.detectedKw} kW", style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                text = if (p.stagesToComplete.isNotEmpty()) {
                                    "Stages to mark done: ${p.stagesToComplete.joinToString(", ")}"
                                } else {
                                    "Will attach as timestamped site update note."
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    if (parties.isNotEmpty()) {
                        Text("Or link to existing party:", style = MaterialTheme.typography.labelSmall)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(parties.take(8)) { party ->
                                FilterChip(
                                    selected = overridePartyId == party.id,
                                    onClick = { overridePartyId = party.id },
                                    label = { Text(party.partyName) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = preview ?: onPreview(message)
                    onApply(p, overridePartyId)
                },
                enabled = message.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = LimeGreen, contentColor = Color.White)
            ) {
                Text("Apply & Sync")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun LekhaJokhaBulkDialog(
    onPreview: (String) -> List<ParsedLekhaJokhaLine>,
    onApply: (List<ParsedLekhaJokhaLine>) -> Unit,
    onDismiss: () -> Unit
) {
    var rawText by remember { mutableStateOf("") }
    var parsedLines by remember { mutableStateOf<List<ParsedLekhaJokhaLine>>(emptyList()) }
    val solarColors = LocalSolarColors.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lekha Jokha Bulk Parser") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Paste daily WhatsApp site kharcha & worker advances (one per line), e.g.:\nRamesh Sharma chai nashta kharcha 350\nRaju helper advance 2000\nMeera Devi labour fitting 3500",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = rawText,
                    onValueChange = {
                        rawText = it
                        parsedLines = if (it.isNotBlank()) onPreview(it) else emptyList()
                    },
                    label = { Text("Paste Lekha Jokha Lines") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth().testTag("lekha_jokha_input")
                )
                if (parsedLines.isNotEmpty()) {
                    Text(
                        "Detected ${parsedLines.size} entries • Total: ${formatInr(parsedLines.sumOf { it.amount })}",
                        style = TabularNumberStyle,
                        color = MaroonPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    parsedLines.forEach { item ->
                        Surface(
                            color = solarColors.emeraldContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.workerOrTitle, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Text("${item.category.label} • ${item.matchedPartyName}", style = MaterialTheme.typography.labelSmall)
                                }
                                Text(
                                    formatInr(item.amount),
                                    style = TabularNumberStyle,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onApply(parsedLines) },
                enabled = parsedLines.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary)
            ) {
                Text("Post to Kharcha & BUSY 21")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
