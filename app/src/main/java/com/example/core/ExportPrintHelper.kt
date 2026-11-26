package com.example.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.FileProvider
import com.example.data.model.BusyAccount
import com.example.data.model.BusyVoucher
import com.example.data.model.PartyDocument
import java.io.File
import java.util.Locale
import kotlin.math.abs

object ExportPrintHelper {

    const val COMPANY_NAME = "NB ENGINEERING WORKS AND ELECTRICALS"
    const val COMPANY_ADDRESS = "Road No. 5, Madri Industrial Area, Udaipur, Rajasthan - 313003"
    const val COMPANY_GSTIN = "08AAFCN8421R1Z5"

    /**
     * Exports all BUSY 21 vouchers in standard BUSY 21 Excel/CSV Import format
     * and launches the Android Share Sheet.
     */
    fun exportBusy21Csv(context: Context, vouchers: List<BusyVoucher>): Result<String> {
        return try {
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val fileName = "BUSY21_NB_Solar_Import_${todayIsoDate()}.csv"
            val file = File(exportDir, fileName)

            val sb = StringBuilder()
            sb.appendLine(
                "VoucherType,Date,VoucherNo,PartyAccount,ContraAccount,CostCenter,BillRefType,BillRefNo,UTR_Cheque,TaxableAmt,CGST,SGST,IGST,TotalAmount,ItemsSummary,Narration"
            )
            vouchers.forEach { v ->
                val itemsSummary = v.lineItems.joinToString(" | ") { item ->
                    "${item.itemName} (${item.quantity} ${item.unit} @ ${item.rate})"
                }
                sb.appendLine(
                    listOf(
                        v.type.label,
                        v.date,
                        v.voucherNo,
                        v.partyAccountName,
                        v.contraAccountName,
                        v.costCenter.label,
                        v.billRefType.label,
                        v.billRefNo,
                        v.utrOrChequeNo,
                        String.format(Locale.US, "%.2f", v.taxableAmount),
                        String.format(Locale.US, "%.2f", v.cgstAmount),
                        String.format(Locale.US, "%.2f", v.sgstAmount),
                        String.format(Locale.US, "%.2f", v.igstAmount),
                        String.format(Locale.US, "%.2f", v.totalAmount),
                        itemsSummary,
                        v.narration
                    ).joinToString(",") { csvEscape(it) }
                )
            }
            file.writeText(sb.toString())

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "BUSY 21 Data Import - $COMPANY_NAME")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Export BUSY 21 CSV"))
            Result.success(fileName)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Prints a formatted A4 Tax Invoice using Android PrintManager + WebView.
     */
    fun printTaxInvoiceA4(context: Context, voucher: BusyVoucher) {
        val rowsHtml = if (voucher.lineItems.isEmpty()) {
            """
            <tr>
              <td>1</td>
              <td>${escapeHtml(voucher.narration.ifBlank { "Solar EPC Supply & Services" })}</td>
              <td>854140</td>
              <td class="num">1</td>
              <td class="num">${formatInr(voucher.taxableAmount, true)}</td>
              <td class="num">${formatInr(voucher.taxableAmount, true)}</td>
            </tr>
            """.trimIndent()
        } else {
            voucher.lineItems.mapIndexed { index, item ->
                """
                <tr>
                  <td>${index + 1}</td>
                  <td>${escapeHtml(item.itemName)}</td>
                  <td>${escapeHtml(item.hsnCode.ifBlank { "854140" })}</td>
                  <td class="num">${item.quantity} ${escapeHtml(item.unit)}</td>
                  <td class="num">${formatInr(item.rate, true)}</td>
                  <td class="num">${formatInr(item.taxableAmount, true)}</td>
                </tr>
                """.trimIndent()
            }.joinToString("\n")
        }

        val html = """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="utf-8" />
          <style>
            body { font-family: Arial, sans-serif; margin: 24px; color: #0F172A; font-size: 12px; }
            .header { border-bottom: 3px solid #0B162C; padding-bottom: 12px; margin-bottom: 16px; }
            .brand { font-size: 20px; font-weight: 800; color: #0B162C; letter-spacing: 0.5px; }
            .addr { color: #475569; margin-top: 4px; }
            .badge { display: inline-block; background: #F59E0B; color: #0B162C; font-weight: bold; padding: 4px 10px; border-radius: 4px; margin-top: 8px; }
            .meta { width: 100%; margin-bottom: 16px; border-collapse: collapse; }
            .meta td { padding: 6px 8px; border: 1px solid #CBD5E1; }
            table.items { width: 100%; border-collapse: collapse; margin-bottom: 16px; }
            table.items th { background: #0B162C; color: #FFFFFF; padding: 8px; text-align: left; }
            table.items td { padding: 7px 8px; border: 1px solid #CBD5E1; }
            .num { text-align: right; font-family: monospace; }
            .totals { width: 45%; margin-left: auto; border-collapse: collapse; }
            .totals td { padding: 6px 8px; border: 1px solid #CBD5E1; }
            .grand { background: #FEF3C7; font-weight: 800; font-size: 14px; }
            .footer { margin-top: 32px; display: flex; justify-content: space-between; font-size: 11px; color: #475569; }
          </style>
        </head>
        <body>
          <div class="header">
            <div class="brand">$COMPANY_NAME</div>
            <div class="addr">$COMPANY_ADDRESS | GSTIN: $COMPANY_GSTIN</div>
            <div class="badge">${escapeHtml(voucher.type.label.uppercase(Locale.US))} TAX VOUCHER</div>
          </div>
          <table class="meta">
            <tr>
              <td><b>Party Name:</b> ${escapeHtml(voucher.partyAccountName)}</td>
              <td><b>Voucher No.:</b> ${escapeHtml(voucher.voucherNo)}</td>
            </tr>
            <tr>
              <td><b>Cost Center:</b> ${escapeHtml(voucher.costCenter.label)}</td>
              <td><b>Date:</b> ${escapeHtml(voucher.date)}</td>
            </tr>
            <tr>
              <td><b>Ref / UTR:</b> ${escapeHtml(voucher.utrOrChequeNo.ifBlank { voucher.billRefNo.ifBlank { "-" } })}</td>
              <td><b>Contra A/c:</b> ${escapeHtml(voucher.contraAccountName)}</td>
            </tr>
          </table>
          <table class="items">
            <thead>
              <tr>
                <th>#</th><th>Description of Goods / Services</th><th>HSN</th>
                <th class="num">Qty</th><th class="num">Rate</th><th class="num">Taxable</th>
              </tr>
            </thead>
            <tbody>
              $rowsHtml
            </tbody>
          </table>
          <table class="totals">
            <tr><td>Taxable Value</td><td class="num">${formatInr(voucher.taxableAmount, true)}</td></tr>
            <tr><td>CGST</td><td class="num">${formatInr(voucher.cgstAmount, true)}</td></tr>
            <tr><td>SGST</td><td class="num">${formatInr(voucher.sgstAmount, true)}</td></tr>
            <tr><td>IGST</td><td class="num">${formatInr(voucher.igstAmount, true)}</td></tr>
            <tr class="grand"><td>Grand Total</td><td class="num">${formatInr(voucher.totalAmount, true)}</td></tr>
          </table>
          <p><b>Narration:</b> ${escapeHtml(voucher.narration.ifBlank { "Being solar EPC transaction recorded in BUSY 21." })}</p>
          <div class="footer">
            <div>Subject to Udaipur Jurisdiction. E. &amp; O.E.</div>
            <div><b>For $COMPANY_NAME</b><br/><br/>Authorized Signatory</div>
          </div>
        </body>
        </html>
        """.trimIndent()

        printHtmlDocument(context, "NB_Voucher_${voucher.voucherNo}", html)
    }

    /**
     * Prints a BUSY 21 Party Account Ledger Statement in A4 format.
     */
    fun printAccountLedgerA4(
        context: Context,
        account: BusyAccount,
        fromDate: String,
        toDate: String,
        rows: List<LedgerPrintRow>,
        closingBalance: Double
    ) {
        val trs = rows.joinToString("\n") { r ->
            val balPair = formatBusyDrCr(r.runningBalance)
            val balClass = if (balPair.second) "dr" else "cr"
            """
            <tr>
              <td>${escapeHtml(r.date)}</td>
              <td>${escapeHtml(r.voucherType)}</td>
              <td>${escapeHtml(r.voucherNo)}</td>
              <td>${escapeHtml(r.particulars)}<br/><small style="color:#64748B">${escapeHtml(r.narration)}</small></td>
              <td class="num dr">${if (r.debit > 0) formatInr(r.debit, true) else ""}</td>
              <td class="num cr">${if (r.credit > 0) formatInr(r.credit, true) else ""}</td>
              <td class="num $balClass">${balPair.first}</td>
            </tr>
            """.trimIndent()
        }

        val closingPair = formatBusyDrCr(closingBalance)
        val html = """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="utf-8" />
          <style>
            body { font-family: Arial, sans-serif; margin: 20px; color: #0F172A; font-size: 11px; }
            .brand { font-size: 18px; font-weight: 800; color: #0B162C; }
            .sub { color: #475569; margin-bottom: 12px; }
            table { width: 100%; border-collapse: collapse; margin-top: 10px; }
            th { background: #0B162C; color: #fff; padding: 6px; text-align: left; }
            td { border: 1px solid #CBD5E1; padding: 6px; vertical-align: top; }
            .num { text-align: right; font-family: monospace; }
            .dr { color: #1D4ED8; font-weight: 600; }
            .cr { color: #DC2626; font-weight: 600; }
            .summary { margin-top: 12px; font-weight: bold; font-size: 13px; text-align: right; }
          </style>
        </head>
        <body>
          <div class="brand">$COMPANY_NAME</div>
          <div class="sub">$COMPANY_ADDRESS</div>
          <h3>Account Ledger Statement: ${escapeHtml(account.name)} (${escapeHtml(account.group.label)})</h3>
          <div>Period: ${escapeHtml(fromDate)} to ${escapeHtml(toDate)} | Opening Balance: ${formatBusyDrCr(account.openingBalance).first}</div>
          <table>
            <thead>
              <tr><th>Date</th><th>Type</th><th>Vch No</th><th>Particulars &amp; Narration</th><th class="num">Debit (Dr)</th><th class="num">Credit (Cr)</th><th class="num">Balance</th></tr>
            </thead>
            <tbody>$trs</tbody>
          </table>
          <div class="summary">Closing Balance: ${closingPair.first}</div>
        </body>
        </html>
        """.trimIndent()

        printHtmlDocument(context, "NB_Ledger_${account.name.replace(" ", "_")}", html)
    }

    /**
     * Shares a Document Vault item via Android Share Sheet (compatible with WhatsApp).
     */
    fun shareVaultDocument(context: Context, partyName: String, doc: PartyDocument) {
        val shareText = buildString {
            appendLine("*NB Engineering Works & Electricals - Udaipur*")
            appendLine("Party: $partyName")
            appendLine("Document: ${doc.title} (${doc.category.label})")
            appendLine("Uploaded: ${doc.uploadedAt} by ${doc.uploadedBy}")
            if (doc.notes.isNotBlank()) appendLine("Notes: ${doc.notes}")
        }

        val localFile = doc.localFilePath.takeIf { it.isNotBlank() }?.let { File(it) }
        val intent = if (localFile != null && localFile.exists()) {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                localFile
            )
            Intent(Intent.ACTION_SEND).apply {
                type = doc.mimeType.ifBlank { "*/*" }
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, shareText)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
        }
        context.startActivity(Intent.createChooser(intent, "Share via WhatsApp / Apps"))
    }

    private fun printHtmlDocument(context: Context, jobName: String, html: String) {
        val webView = WebView(context)
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = false
            override fun onPageFinished(view: WebView, url: String) {
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                val printAdapter = view.createPrintDocumentAdapter(jobName)
                val attributes = PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                    .setResolution(PrintAttributes.Resolution("pdf", "pdf", 300, 300))
                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                    .build()
                printManager?.print(jobName, printAdapter, attributes)
            }
        }
        webView.loadDataWithBaseURL(null, html, "text/HTML", "UTF-8", null)
    }

    private fun csvEscape(raw: String): String {
        val clean = raw.replace("\r", " ").replace("\n", " ")
        return if (clean.contains(",") || clean.contains("\"")) {
            "\"${clean.replace("\"", "\"\"")}\""
        } else {
            clean
        }
    }

    private fun escapeHtml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}

data class LedgerPrintRow(
    val date: String,
    val voucherType: String,
    val voucherNo: String,
    val particulars: String,
    val narration: String,
    val debit: Double,
    val credit: Double,
    val runningBalance: Double
)
