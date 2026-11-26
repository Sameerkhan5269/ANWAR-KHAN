package com.example.data.model

import java.util.UUID

enum class StaffRole(val label: String, val badge: String, val description: String) {
    OWNER("Owner / CEO", "OWNER", "Full access: Pipeline, Inventory, BUSY 21 Accounting, Partner Splits, Team & Delete"),
    ACCOUNTS("Accounts Manager", "ACCOUNTS", "Full BUSY 21 Accounting, AI Bank/Bill Auto-Import, Inventory & Party Ledgers"),
    OFFICE("Office & Docs Coordinator", "OFFICE", "Manage Leads, DISCOM files, DCR/KYC Docs & Parties (Partner splits hidden)"),
    SUPERVISOR("Site Supervisor", "SUPERVISOR", "Strict Privacy: Zero ₹ visibility. Installation, Net Metering, Photos, DCR/Meters & Stock Issue"),
    HELPER("Field Helper", "HELPER", "Strict Privacy: Zero ₹ visibility. Installation checklists & site geotag photo uploads");

    companion object {
        fun fromString(raw: String?): StaffRole =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: HELPER
    }
}

data class StaffUser(
    val id: String = UUID.randomUUID().toString(),
    val loginId: String,
    val pin: String,
    val displayName: String,
    val role: StaffRole,
    val isBuiltIn: Boolean = false
)

enum class LeadSource(val label: String, val shortLabel: String) {
    NB_DIRECT("NB Engineering (Direct)", "NB Direct"),
    THAKUR_JI("Thakur Ji Reference", "Thakur Ji"),
    PERSONAL_REF("Personal Reference", "Personal Ref");

    companion object {
        fun fromString(raw: String?): LeadSource =
            entries.firstOrNull {
                it.name.equals(raw, ignoreCase = true) || it.label.equals(raw, ignoreCase = true)
            } ?: NB_DIRECT
    }
}

enum class WorkflowSection(val index: Int, val title: String, val defaultSteps: List<String>) {
    LEAD_FILE(
        1,
        "1. Lead & File Process",
        listOf("Site Survey", "Technical Feasibility", "DISCOM File Submission")
    ),
    LOAN(
        2,
        "2. Loan Process",
        listOf("Bank Application", "Loan 1st Tranche", "Loan 2nd Tranche")
    ),
    INSTALLATION(
        3,
        "3. Installation",
        listOf("GI Structure", "Panel Mounting & Inverter", "AC/DC Earthing & Geotag Photo")
    ),
    NET_METERING(
        4,
        "4. Net Metering",
        listOf("AVVNL Meter Testing", "Bi-Directional Net Meter Installation", "Commissioning")
    ),
    SUBSIDY_CLOSE(
        5,
        "5. Subsidy & Final Close Account",
        listOf("PM Surya Ghar Subsidy Claim", "Subsidy Disbursed", "Final Handover & Account Closed")
    );

    companion object {
        fun fromString(raw: String?): WorkflowSection =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: LEAD_FILE
    }
}

data class WorkflowStage(
    val id: String,
    val section: WorkflowSection,
    val title: String,
    val completed: Boolean = false,
    val completedAt: String = "",
    val completedBy: String = "",
    val remarks: String = ""
)

enum class DocumentCategory(val label: String) {
    GEOTAG_PHOTO("Geotag Photo"),
    DCR_CERT("DCR Certificate"),
    TAX_INVOICE("Tax Invoice / Bill"),
    KYC_DISCOM("KYC / DISCOM File");

    companion object {
        fun fromString(raw: String?): DocumentCategory =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) || it.label.equals(raw, ignoreCase = true) }
                ?: GEOTAG_PHOTO
    }
}

data class PartyDocument(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val category: DocumentCategory,
    val fileName: String,
    val mimeType: String = "image/jpeg",
    val localFilePath: String = "",
    val base64Thumbnail: String = "",
    val uploadedAt: String = "",
    val uploadedBy: String = "",
    val notes: String = ""
)

data class SiteLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: String,
    val author: String,
    val message: String,
    val viaWhatsAppParser: Boolean = false
)

data class PartyProject(
    val id: String = UUID.randomUUID().toString(),
    val partyName: String,
    val mobile: String = "",
    val address: String = "",
    val consumerNumber: String = "",
    val capacityKw: Double = 3.0,
    val leadSource: LeadSource = LeadSource.NB_DIRECT,
    val projectValue: Double = 0.0,
    val receivedAmount: Double = 0.0,
    val loan1Amount: Double = 0.0,
    val loan1Received: Boolean = false,
    val loan2Amount: Double = 0.0,
    val loan2Received: Boolean = false,
    val subsidyAmount: Double = 0.0,
    val partnerCommissionPct: Double = 0.0,
    val createdDate: String = "",
    val stages: List<WorkflowStage> = defaultWorkflowStages(),
    val documents: List<PartyDocument> = emptyList(),
    val siteLogs: List<SiteLogEntry> = emptyList()
) {
    val completedStagesCount: Int get() = stages.count { it.completed }
    val totalStagesCount: Int get() = stages.size.coerceAtLeast(1)
    val progressFraction: Float get() = completedStagesCount.toFloat() / totalStagesCount.toFloat()
    val pendingAmount: Double get() = (projectValue - receivedAmount).coerceAtLeast(0.0)
    val currentStageTitle: String
        get() = stages.firstOrNull { !it.completed }?.title ?: "Account Closed"
}

fun defaultWorkflowStages(): List<WorkflowStage> = WorkflowSection.entries.flatMap { section ->
    section.defaultSteps.mapIndexed { idx, stepTitle ->
        WorkflowStage(
            id = "${section.name}_${idx + 1}",
            section = section,
            title = stepTitle,
            completed = false
        )
    }
}

// --- INVENTORY MODELS ---

enum class PanelStatus(val label: String) {
    IN_STOCK("In Stock"),
    ALLOCATED("Allocated");

    companion object {
        fun fromString(raw: String?): PanelStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) || it.label.equals(raw, ignoreCase = true) }
                ?: IN_STOCK
    }
}

data class DcrPanel(
    val id: String = UUID.randomUUID().toString(),
    val serialNumber: String,
    val brand: String,
    val wattage: Int,
    val supplierName: String,
    val purchaseInvoiceNo: String,
    val status: PanelStatus = PanelStatus.IN_STOCK,
    val linkedPartyId: String = "",
    val linkedPartyName: String = "",
    val addedDate: String = ""
)

enum class MeterType(val label: String) {
    BI_DIRECTIONAL("Bi-Directional Net Meter"),
    SOLAR_GENERATION("Solar Generation Meter");

    companion object {
        fun fromString(raw: String?): MeterType =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) || it.label.equals(raw, ignoreCase = true) }
                ?: BI_DIRECTIONAL
    }
}

enum class DiscomTestingStatus(val label: String) {
    PENDING("Pending"),
    SUBMITTED("Submitted to AVVNL"),
    TESTED_PASSED("Tested & Passed"),
    INSTALLED("Installed at Site");

    companion object {
        fun fromString(raw: String?): DiscomTestingStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) || it.label.equals(raw, ignoreCase = true) }
                ?: PENDING
    }
}

data class SolarMeter(
    val id: String = UUID.randomUUID().toString(),
    val serialNumber: String,
    val meterType: MeterType = MeterType.BI_DIRECTIONAL,
    val makeAndPhase: String = "L&T / 1-Phase",
    val testingStatus: DiscomTestingStatus = DiscomTestingStatus.PENDING,
    val installedPartyId: String = "",
    val installedPartyName: String = ""
)

data class MaterialIssueLog(
    val id: String = UUID.randomUUID().toString(),
    val partyId: String,
    val partyName: String,
    val quantity: Double,
    val date: String,
    val issuedBy: String
)

data class RawMaterial(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val category: String,
    val unit: String,
    val supplierName: String = "",
    val purchasedQty: Double = 0.0,
    val usedQty: Double = 0.0,
    val minStockAlert: Double = 10.0,
    val issueLogs: List<MaterialIssueLog> = emptyList()
) {
    val remainingQty: Double get() = (purchasedQty - usedQty).coerceAtLeast(0.0)
    val isLowStock: Boolean get() = remainingQty <= minStockAlert
}

// --- BUSY 21 ACCOUNTING MODELS ---

enum class BusyAccountGroup(val label: String, val nature: String) {
    SUNDRY_DEBTORS("Sundry Debtors", "Asset"),
    SUNDRY_CREDITORS("Sundry Creditors", "Liability"),
    BANK_ACCOUNTS("Bank Accounts", "Asset"),
    CASH_IN_HAND("Cash-in-Hand", "Asset"),
    LOANS_ADVANCES("Loans & Advances", "Asset"),
    DIRECT_EXPENSES("Direct Expenses", "Expense"),
    INDIRECT_EXPENSES("Indirect Expenses", "Expense"),
    SALES_ACCOUNTS("Sales Accounts", "Income"),
    PURCHASE_ACCOUNTS("Purchase Accounts", "Expense"),
    DUTIES_TAXES("Duties & Taxes", "Liability");

    companion object {
        fun fromString(raw: String?): BusyAccountGroup =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) || it.label.equals(raw, ignoreCase = true) }
                ?: SUNDRY_DEBTORS
    }
}

data class BusyAccount(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val group: BusyAccountGroup,
    val openingBalance: Double = 0.0, // Positive = Dr, Negative = Cr
    val gstin: String = "",
    val mobile: String = "",
    val linkedPartyId: String = "",
    val defaultCostCenter: LeadSource = LeadSource.NB_DIRECT
)

data class BusyItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val hsnCode: String = "854140",
    val unit: String = "Nos",
    val gstRatePercent: Double = 5.0,
    val defaultSaleRate: Double = 0.0,
    val defaultPurchaseRate: Double = 0.0,
    val stockQty: Double = 0.0
)

enum class BusyVoucherType(val label: String, val shortcut: String, val prefix: String) {
    SALES("Sales", "F8", "INV"),
    PURCHASE("Purchase", "F9", "PUR"),
    PAYMENT("Payment", "F5", "PMT"),
    RECEIPT("Receipt", "F6", "RCP"),
    JOURNAL("Journal", "F7", "JRN"),
    CONTRA("Contra", "F4", "CNT");

    companion object {
        fun fromString(raw: String?): BusyVoucherType =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) || it.label.equals(raw, ignoreCase = true) }
                ?: SALES
    }
}

enum class BillRefType(val label: String) {
    NEW_REF("New Ref"),
    AGAINST_REF("Against Ref"),
    ADVANCE("Advance"),
    ON_ACCOUNT("On Account");

    companion object {
        fun fromString(raw: String?): BillRefType =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) || it.label.equals(raw, ignoreCase = true) }
                ?: ON_ACCOUNT
    }
}

data class BusyLineItem(
    val itemId: String = "",
    val itemName: String,
    val hsnCode: String = "854140",
    val quantity: Double = 1.0,
    val unit: String = "Nos",
    val rate: Double = 0.0,
    val gstRatePercent: Double = 5.0
) {
    val taxableAmount: Double get() = quantity * rate
    val gstAmount: Double get() = taxableAmount * (gstRatePercent / 100.0)
}

data class BusyVoucher(
    val id: String = UUID.randomUUID().toString(),
    val type: BusyVoucherType,
    val voucherNo: String,
    val date: String,
    val partyAccountId: String,
    val partyAccountName: String,
    val contraAccountId: String,
    val contraAccountName: String,
    val costCenter: LeadSource = LeadSource.NB_DIRECT,
    val isInterStateIgst: Boolean = false,
    val taxableAmount: Double = 0.0,
    val cgstAmount: Double = 0.0,
    val sgstAmount: Double = 0.0,
    val igstAmount: Double = 0.0,
    val totalAmount: Double = 0.0,
    val billRefType: BillRefType = BillRefType.ON_ACCOUNT,
    val billRefNo: String = "",
    val utrOrChequeNo: String = "",
    val narration: String = "",
    val lineItems: List<BusyLineItem> = emptyList(),
    val partyGstin: String = "",
    val createdBy: String = ""
)

// --- AI AUTO-IMPORT & WHATSAPP PARSER MODELS ---

data class ParsedBankRow(
    val id: String = UUID.randomUUID().toString(),
    val date: String,
    val voucherType: BusyVoucherType, // PAYMENT or RECEIPT
    val partyName: String,
    val matchedAccountId: String = "",
    val amount: Double,
    val utrNo: String,
    val narration: String,
    val isDuplicateUtr: Boolean = false,
    val selected: Boolean = true,
    val costCenter: LeadSource = LeadSource.NB_DIRECT
)

data class ParsedPurchaseLineItem(
    val itemName: String,
    val hsnCode: String = "854140",
    val quantity: Double = 1.0,
    val unit: String = "Nos",
    val rate: Double = 0.0,
    val gstRatePercent: Double = 5.0
)

data class ParsedPurchaseBill(
    val supplierName: String,
    val supplierGstin: String,
    val invoiceNo: String,
    val invoiceDate: String,
    val taxableAmount: Double,
    val cgstAmount: Double,
    val sgstAmount: Double,
    val igstAmount: Double,
    val totalAmount: Double,
    val lineItems: List<ParsedPurchaseLineItem>,
    val detectedDcrSerials: List<String> = emptyList(),
    val detectedPanelBrand: String = "",
    val detectedPanelWattage: Int = 540
)

data class ParsedWhatsAppUpdate(
    val rawMessage: String,
    val matchedPartyId: String?,
    val matchedPartyName: String,
    val detectedKw: Double?,
    val stagesToComplete: List<String>,
    val summaryNote: String
)

// --- UNIFIED SNAPSHOT ---

data class AppDatabaseSnapshot(
    val staffUsers: List<StaffUser> = emptyList(),
    val parties: List<PartyProject> = emptyList(),
    val dcrPanels: List<DcrPanel> = emptyList(),
    val solarMeters: List<SolarMeter> = emptyList(),
    val rawMaterials: List<RawMaterial> = emptyList(),
    val busyAccounts: List<BusyAccount> = emptyList(),
    val busyItems: List<BusyItem> = emptyList(),
    val busyVouchers: List<BusyVoucher> = emptyList()
)
