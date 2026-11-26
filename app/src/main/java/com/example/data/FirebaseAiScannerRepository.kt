package com.example.data

import android.graphics.Bitmap
import android.util.Log
import com.example.core.todayIsoDate
import com.example.data.model.BusyAccount
import com.example.data.model.BusyVoucherType
import com.example.data.model.ParsedBankRow
import com.example.data.model.ParsedPurchaseBill
import com.example.data.model.ParsedPurchaseLineItem
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "FirebaseAiScanner"

/**
 * Smart AI Auto-Import engine inside BUSY 21 Accounting powered by Firebase AI Logic (`gemini-3.8-flash`).
 * Uses strict `Schema.obj(...)` structured JSON output and realistic 60s timeouts.
 */
class FirebaseAiScannerRepository {

    private val bankStatementSchema = Schema.obj(
        properties = mapOf(
            "transactions" to Schema.array(
                description = "List of bank transactions parsed as BUSY Payment (F5) or Receipt (F6) vouchers",
                items = Schema.obj(
                    properties = mapOf(
                        "date" to Schema.string(description = "Transaction date in YYYY-MM-DD format"),
                        "voucherType" to Schema.enumeration(
                            values = listOf("PAYMENT", "RECEIPT"),
                            description = "PAYMENT for debit/withdrawal/paid out, RECEIPT for credit/deposit/received"
                        ),
                        "partyName" to Schema.string(description = "Counterparty name matched from known ledgers or extracted from description"),
                        "amount" to Schema.double(description = "Positive numeric transaction amount in INR"),
                        "utrNo" to Schema.string(description = "UTR, IMPS, NEFT, RTGS, UPI, or Cheque reference number if present, else empty string"),
                        "narration" to Schema.string(description = "Clean BUSY 21 voucher narration")
                    )
                )
            )
        )
    )

    private val purchaseBillSchema = Schema.obj(
        properties = mapOf(
            "supplierName" to Schema.string(description = "Supplier or Vendor legal name"),
            "supplierGstin" to Schema.string(description = "15-character GSTIN of supplier if found, else empty"),
            "invoiceNo" to Schema.string(description = "Supplier Tax Invoice Number"),
            "invoiceDate" to Schema.string(description = "Invoice date in YYYY-MM-DD format"),
            "taxableAmount" to Schema.double(description = "Total taxable value before GST"),
            "cgstAmount" to Schema.double(description = "Total CGST tax amount"),
            "sgstAmount" to Schema.double(description = "Total SGST tax amount"),
            "igstAmount" to Schema.double(description = "Total IGST tax amount"),
            "totalAmount" to Schema.double(description = "Grand invoice total in INR"),
            "detectedPanelBrand" to Schema.string(description = "Solar panel brand if this bill includes DCR panels (e.g. Waaree, Adani), else empty"),
            "detectedPanelWattage" to Schema.integer(description = "Wattage per solar panel if detected (e.g. 540, 550), else 540"),
            "detectedDcrSerials" to Schema.array(
                items = Schema.string(),
                description = "Any DCR solar panel serial numbers or barcodes printed on the invoice"
            ),
            "lineItems" to Schema.array(
                description = "Extracted invoice line items",
                items = Schema.obj(
                    properties = mapOf(
                        "itemName" to Schema.string(description = "Item description"),
                        "hsnCode" to Schema.string(description = "HSN or SAC code"),
                        "quantity" to Schema.double(description = "Quantity"),
                        "unit" to Schema.string(description = "Unit e.g. Nos, kg, m, Sets"),
                        "rate" to Schema.double(description = "Unit rate before tax"),
                        "gstRatePercent" to Schema.double(description = "GST percentage e.g. 5, 12, 18, 28")
                    )
                )
            )
        ),
        optionalProperties = listOf("detectedPanelBrand", "detectedPanelWattage", "detectedDcrSerials")
    )

    private val bankModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = bankStatementSchema
            }
        )
    }

    private val billModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = purchaseBillSchema
            }
        )
    }

    /**
     * Parses a bank statement (Bitmap image, PDF bytes, or CSV/text content) into BUSY 21
     * Payment (F5) & Receipt (F6) rows, matching existing accounts and flagging duplicate UTRs.
     */
    suspend fun scanBankStatement(
        bitmap: Bitmap? = null,
        pdfBytes: ByteArray? = null,
        rawText: String? = null,
        existingAccounts: List<BusyAccount>,
        existingUtrs: Set<String>
    ): Result<List<ParsedBankRow>> {
        return try {
            val ledgerNames = existingAccounts.joinToString(", ") { it.name }
            val prompt = """
                You are an expert Indian Chartered Accountant assistant for BUSY 21 Accounting Software at NB Engineering Works & Electricals (Solar EPC, Udaipur).
                Extract every bank debit/credit transaction from the provided bank statement.
                - Debit / Withdrawal / Paid Out -> voucherType = "PAYMENT"
                - Credit / Deposit / Received -> voucherType = "RECEIPT"
                - Extract exact UTR / NEFT / RTGS / IMPS / UPI / Cheque reference numbers into `utrNo`.
                - Match `partyName` to one of these existing BUSY ledgers if closely matching: [$ledgerNames]. Otherwise extract the clean party/beneficiary name.
                - Use YYYY-MM-DD for dates (default ${todayIsoDate()} if unclear).
            """.trimIndent()

            val response = withTimeout(60_000L) {
                bankModel.generateContent(
                    content {
                        text(prompt)
                        if (bitmap != null) image(bitmap)
                        if (pdfBytes != null && pdfBytes.isNotEmpty()) {
                            inlineData(pdfBytes, "application/pdf")
                        }
                        if (!rawText.isNullOrBlank()) {
                            text("Bank statement text / CSV data:\n$rawText")
                        }
                    }
                )
            }

            val jsonStr = response.text ?: return Result.failure(IllegalStateException("AI returned an empty response."))
            val root = JSONObject(jsonStr)
            val arr = root.optJSONArray("transactions")
                ?: return Result.failure(IllegalStateException("No transactions found in bank statement."))

            val normalizedExistingUtrs = existingUtrs
                .map { it.trim().uppercase() }
                .filter { it.isNotBlank() }
                .toSet()

            val seenInBatch = mutableSetOf<String>()
            val parsed = buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val amt = o.optDouble("amount", 0.0)
                    if (amt <= 0.0 || amt.isNaN()) continue

                    val utr = o.optString("utrNo").trim()
                    val normUtr = utr.uppercase()
                    val isDup = normUtr.isNotBlank() &&
                        (normUtr in normalizedExistingUtrs || !seenInBatch.add(normUtr))

                    val rawParty = o.optString("partyName").ifBlank { "Suspense Party" }
                    val matchedAcc = existingAccounts.firstOrNull {
                        it.name.equals(rawParty, ignoreCase = true) ||
                            it.name.contains(rawParty, ignoreCase = true) ||
                            rawParty.contains(it.name, ignoreCase = true)
                    }

                    val vType = if (o.optString("voucherType").equals("RECEIPT", ignoreCase = true)) {
                        BusyVoucherType.RECEIPT
                    } else {
                        BusyVoucherType.PAYMENT
                    }

                    add(
                        ParsedBankRow(
                            date = o.optString("date").ifBlank { todayIsoDate() },
                            voucherType = vType,
                            partyName = matchedAcc?.name ?: rawParty,
                            matchedAccountId = matchedAcc?.id.orEmpty(),
                            amount = amt,
                            utrNo = utr,
                            narration = o.optString("narration").ifBlank { "Bank auto-import ($utr)" },
                            isDuplicateUtr = isDup,
                            selected = !isDup
                        )
                    )
                }
            }
            Result.success(parsed)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Bank statement scan timed out", e)
            Result.failure(Exception("AI Bank Statement scan timed out after 60s. Please retry with a smaller or clearer file."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Bank statement scan failed", e)
            Result.failure(Exception(e.localizedMessage ?: "Failed to scan bank statement."))
        }
    }

    /**
     * Scans a Supplier Purchase Bill (Photo Bitmap, PDF bytes, or text) to extract GST invoice details,
     * line items, and any DCR solar panel serial numbers for 1-click F9 Purchase + Inventory sync.
     */
    suspend fun scanPurchaseBill(
        bitmap: Bitmap? = null,
        pdfBytes: ByteArray? = null,
        rawText: String? = null
    ): Result<ParsedPurchaseBill> {
        return try {
            val prompt = """
                You are an Indian GST Accounting & Solar EPC Inventory scanner for BUSY 21 at NB Engineering Works & Electricals (Udaipur, Rajasthan).
                Extract all supplier Tax Invoice fields accurately:
                - Supplier legal name, 15-char GSTIN, Invoice Number, Invoice Date (YYYY-MM-DD, default ${todayIsoDate()}).
                - Taxable amount, CGST, SGST, IGST, and Grand Total.
                - All line items with description, HSN code, quantity, unit, unit rate, and GST%.
                - If solar panels are listed and any DCR serial numbers appear on the invoice/packing slip, extract them into `detectedDcrSerials` along with `detectedPanelBrand` and `detectedPanelWattage`.
            """.trimIndent()

            val response = withTimeout(60_000L) {
                billModel.generateContent(
                    content {
                        text(prompt)
                        if (bitmap != null) image(bitmap)
                        if (pdfBytes != null && pdfBytes.isNotEmpty()) {
                            inlineData(pdfBytes, "application/pdf")
                        }
                        if (!rawText.isNullOrBlank()) {
                            text("Purchase invoice text:\n$rawText")
                        }
                    }
                )
            }

            val jsonStr = response.text ?: return Result.failure(IllegalStateException("AI returned an empty response."))
            val o = JSONObject(jsonStr)
            val liArr = o.optJSONArray("lineItems")
            val lineItems = buildList {
                if (liArr != null) {
                    for (i in 0 until liArr.length()) {
                        val li = liArr.optJSONObject(i) ?: continue
                        add(
                            ParsedPurchaseLineItem(
                                itemName = li.optString("itemName").ifBlank { "Solar Item" },
                                hsnCode = li.optString("hsnCode", "854140"),
                                quantity = li.optDouble("quantity", 1.0).coerceAtLeast(0.01),
                                unit = li.optString("unit", "Nos"),
                                rate = li.optDouble("rate", 0.0).coerceAtLeast(0.0),
                                gstRatePercent = li.optDouble("gstRatePercent", 5.0).coerceAtLeast(0.0)
                            )
                        )
                    }
                }
            }

            val serialsArr = o.optJSONArray("detectedDcrSerials")
            val serials = buildList {
                if (serialsArr != null) {
                    for (i in 0 until serialsArr.length()) {
                        val s = serialsArr.optString(i).trim()
                        if (s.isNotBlank()) add(s)
                    }
                }
            }

            val taxable = o.optDouble("taxableAmount", 0.0).coerceAtLeast(0.0)
            val cgst = o.optDouble("cgstAmount", 0.0).coerceAtLeast(0.0)
            val sgst = o.optDouble("sgstAmount", 0.0).coerceAtLeast(0.0)
            val igst = o.optDouble("igstAmount", 0.0).coerceAtLeast(0.0)
            val total = o.optDouble("totalAmount", taxable + cgst + sgst + igst).coerceAtLeast(0.0)

            Result.success(
                ParsedPurchaseBill(
                    supplierName = o.optString("supplierName").ifBlank { "Solar Supplier" },
                    supplierGstin = o.optString("supplierGstin").trim(),
                    invoiceNo = o.optString("invoiceNo").ifBlank { "PUR-${System.currentTimeMillis() % 10000}" },
                    invoiceDate = o.optString("invoiceDate").ifBlank { todayIsoDate() },
                    taxableAmount = taxable,
                    cgstAmount = cgst,
                    sgstAmount = sgst,
                    igstAmount = igst,
                    totalAmount = total,
                    lineItems = lineItems,
                    detectedDcrSerials = serials,
                    detectedPanelBrand = o.optString("detectedPanelBrand"),
                    detectedPanelWattage = o.optInt("detectedPanelWattage", 540)
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Purchase bill scan timed out", e)
            Result.failure(Exception("AI Purchase Bill scan timed out after 60s. Please retry with a clearer photo or PDF."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Purchase bill scan failed", e)
            Result.failure(Exception(e.localizedMessage ?: "Failed to scan purchase bill."))
        }
    }
}
