package com.example.core

import com.example.data.model.StaffRole
import com.example.data.model.WorkflowSection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Strict role-based privacy and permission matrix for NB Engineering Works & Electricals.
 */
object RolePermissions {
    fun canViewFinancials(role: StaffRole): Boolean = when (role) {
        StaffRole.OWNER, StaffRole.ACCOUNTS, StaffRole.OFFICE -> true
        StaffRole.SUPERVISOR, StaffRole.HELPER -> false
    }

    fun canAccessAccounting(role: StaffRole): Boolean = when (role) {
        StaffRole.OWNER, StaffRole.ACCOUNTS -> true
        StaffRole.OFFICE, StaffRole.SUPERVISOR, StaffRole.HELPER -> false
    }

    fun canViewPartnerSplits(role: StaffRole): Boolean = role == StaffRole.OWNER

    fun canManageUsers(role: StaffRole): Boolean = role == StaffRole.OWNER || role == StaffRole.ACCOUNTS

    fun canDeleteRecords(role: StaffRole): Boolean = role == StaffRole.OWNER

    fun canSwitchDivision(role: StaffRole): Boolean = role == StaffRole.OWNER || role == StaffRole.ACCOUNTS

    fun canCreateParty(role: StaffRole): Boolean = when (role) {
        StaffRole.OWNER, StaffRole.ACCOUNTS, StaffRole.OFFICE -> true
        StaffRole.SUPERVISOR, StaffRole.HELPER -> false
    }

    fun canViewLoanSection(role: StaffRole): Boolean = when (role) {
        StaffRole.OWNER, StaffRole.ACCOUNTS, StaffRole.OFFICE -> true
        StaffRole.SUPERVISOR, StaffRole.HELPER -> false
    }

    fun canEditWorkflowSection(role: StaffRole, section: WorkflowSection): Boolean = when (role) {
        StaffRole.OWNER, StaffRole.ACCOUNTS, StaffRole.OFFICE -> true
        StaffRole.SUPERVISOR -> section == WorkflowSection.INSTALLATION || section == WorkflowSection.NET_METERING
        StaffRole.HELPER -> section == WorkflowSection.INSTALLATION
    }

    fun canAllocateInventory(role: StaffRole): Boolean = when (role) {
        StaffRole.OWNER, StaffRole.ACCOUNTS, StaffRole.OFFICE, StaffRole.SUPERVISOR -> true
        StaffRole.HELPER -> false
    }

    fun canAddStockOrPurchase(role: StaffRole): Boolean = when (role) {
        StaffRole.OWNER, StaffRole.ACCOUNTS, StaffRole.OFFICE -> true
        StaffRole.SUPERVISOR, StaffRole.HELPER -> false
    }

    fun canViewPartyProfitability(role: StaffRole): Boolean = when (role) {
        StaffRole.OWNER, StaffRole.ACCOUNTS -> true
        StaffRole.OFFICE, StaffRole.SUPERVISOR, StaffRole.HELPER -> false
    }

    fun canManageImprest(role: StaffRole): Boolean = when (role) {
        StaffRole.OWNER, StaffRole.ACCOUNTS -> true
        StaffRole.OFFICE, StaffRole.SUPERVISOR, StaffRole.HELPER -> false
    }

    fun canLogSitePurchase(role: StaffRole): Boolean = true

    fun canUploadVaultDocs(role: StaffRole): Boolean = true
}

/**
 * Formats an amount in standard Indian Rupee format (e.g., ₹1,25,400 or ₹1,25,400.50).
 * Safe against NaN/Infinity.
 */
fun formatInr(amount: Double, showDecimals: Boolean = false): String {
    if (amount.isNaN() || amount.isInfinite()) return "₹0"
    val isNegative = amount < 0
    val absVal = abs(amount)
    val rounded = if (showDecimals) {
        String.format(Locale.US, "%.2f", absVal)
    } else {
        String.format(Locale.US, "%.0f", absVal)
    }
    val parts = rounded.split(".")
    val intPart = parts[0]
    val decPart = if (parts.size > 1) ".${parts[1]}" else ""

    val formattedInt = if (intPart.length <= 3) {
        intPart
    } else {
        val lastThree = intPart.takeLast(3)
        val rest = intPart.dropLast(3)
        val groupedRest = rest.reversed().chunked(2).joinToString(",").reversed()
        "$groupedRest,$lastThree"
    }
    val sign = if (isNegative) "-" else ""
    return "${sign}₹$formattedInt$decPart"
}

/**
 * Converts a rupee amount into Indian English words (e.g. "Rupees One Lakh Twenty Five Thousand Only").
 */
fun amountInWords(amount: Double): String {
    if (amount.isNaN() || amount.isInfinite() || amount <= 0.0) return "Rupees Zero Only"
    val whole = kotlin.math.round(amount).toLong()
    val units = arrayOf(
        "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
        "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"
    )
    val tens = arrayOf("", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety")

    fun twoDigits(n: Int): String = when {
        n == 0 -> ""
        n < 20 -> units[n]
        else -> (tens[n / 10] + " " + units[n % 10]).trim()
    }

    fun convertLong(n: Long): String {
        if (n == 0L) return ""
        val sb = StringBuilder()
        var rem = n
        val crore = (rem / 10_000_000L).toInt()
        if (crore > 0) {
            sb.append(convertLong(crore.toLong())).append(" Crore ")
            rem %= 10_000_000L
        }
        val lakh = (rem / 100_000L).toInt()
        if (lakh > 0) {
            sb.append(twoDigits(lakh)).append(" Lakh ")
            rem %= 100_000L
        }
        val thousand = (rem / 1_000L).toInt()
        if (thousand > 0) {
            sb.append(twoDigits(thousand)).append(" Thousand ")
            rem %= 1_000L
        }
        val hundred = (rem / 100L).toInt()
        if (hundred > 0) {
            sb.append(units[hundred]).append(" Hundred ")
            rem %= 100L
        }
        if (rem > 0) {
            sb.append(twoDigits(rem.toInt()))
        }
        return sb.toString().trim()
    }

    return "Rupees ${convertLong(whole)} Only"
}

/**
 * Formats a BUSY 21 balance with Dr / Cr suffix (positive = Dr, negative = Cr).
 */
fun formatBusyDrCr(balance: Double): Pair<String, Boolean> {
    val safe = if (balance.isNaN() || balance.isInfinite()) 0.0 else balance
    val isDebit = safe >= 0.0
    val suffix = if (isDebit) "Dr" else "Cr"
    return "${formatInr(abs(safe), showDecimals = true)} $suffix" to isDebit
}

fun safeDouble(text: String, fallback: Double = 0.0): Double {
    val cleaned = text.replace(",", "").replace("₹", "").trim()
    return cleaned.toDoubleOrNull()?.takeIf { !it.isNaN() && !it.isInfinite() } ?: fallback
}

fun todayIsoDate(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    return sdf.format(Date())
}

fun nowTimestamp(): String {
    val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US)
    return sdf.format(Date())
}

fun nowIsoUtc(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    return sdf.format(Date())
}
