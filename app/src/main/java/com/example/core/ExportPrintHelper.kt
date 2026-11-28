package com.example.core

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.Base64
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.FileProvider
import com.example.R
import com.example.data.DayByDayFinancialRow
import com.example.data.PartyCostingRow
import com.example.data.RealCostingCalculator
import com.example.data.model.BusyAccount
import com.example.data.model.BusyLineItem
import com.example.data.model.BusyVoucher
import com.example.data.model.BusyVoucherType
import com.example.data.model.CostingStructureType
import com.example.data.model.DcrPanel
import com.example.data.model.DetailedProposalOptions
import com.example.data.model.PartyDocument
import com.example.data.model.PartyProject
import com.example.data.model.ProposalFormat
import com.example.data.model.ProposalModuleType
import com.example.data.model.RawMaterial
import com.example.data.model.RealCostingBreakdown
import com.example.data.model.SiteSurveyDimensions
import com.example.data.model.SolarMeter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sqrt

object ExportPrintHelper {

    const val COMPANY_NAME = "NB ENGINEERING WORKS AND ELECTRICALS"
    const val COMPANY_ADDRESS =
        "Rama Phosphates Ltd. Plant, Ground Floor, Shed No. 1, Opp. Rama Phosphates Ltd, Udaipur - 313001, Rajasthan"
    const val COMPANY_GSTIN = "08CSZPK9277N2Z8"
    const val COMPANY_TEL = "9929214883"
    const val COMPANY_TEL_DISPLAY = "+91 99292 14883 / +91 90797 93510"
    const val COMPANY_EMAIL = "nbengineering87@gmail.com"

    const val BANK_NAME = "HDFC BANK"
    const val BANK_BRANCH = "KANPUR, UDAIPUR (RAJ)"
    const val BANK_ACCOUNT_NO = "50200079191430"
    const val BANK_IFSC = "HDFC0004861"

    @Volatile
    private var cachedLogoDataUri: String? = null

    @Volatile
    private var activePrintWebView: WebView? = null

    private const val SHARED_CSS = """
        @page { size: A4; margin: 10mm; }
        * { box-sizing: border-box; }
        body {
            font-family: 'Inter', 'Roboto', Arial, sans-serif;
            margin: 14px;
            color: #0F172A;
            background: #FFFFFF;
            font-size: 11px;
            line-height: 1.4;
            font-variant-numeric: tabular-nums;
            -webkit-print-color-adjust: exact;
            print-color-adjust: exact;
        }
        .sheet {
            border: 1.5px solid #0F172A;
            background: #FFFFFF;
        }
        .letterhead {
            border-bottom: 2px solid #8E1E1B;
            padding: 10px 14px;
            display: flex;
            justify-content: space-between;
            align-items: center;
            gap: 10px;
            break-inside: avoid;
            page-break-inside: avoid;
        }
        .brand-row {
            display: flex;
            align-items: center;
            gap: 10px;
            max-width: 74%;
        }
        .brand-logo {
            width: 46px;
            height: 46px;
            border-radius: 50%;
            border: 2px solid #7CB342;
            object-fit: cover;
            flex-shrink: 0;
        }
        .company-title {
            font-size: 17px;
            font-weight: 800;
            color: #8E1E1B;
            letter-spacing: 0.4px;
            text-transform: uppercase;
            margin: 0;
        }
        .company-sub {
            font-size: 10px;
            color: #0F172A;
            margin-top: 2px;
        }
        .company-meta {
            font-size: 10px;
            font-weight: 600;
            color: #0F172A;
            margin-top: 2px;
        }
        .doc-badge {
            background: #8E1E1B;
            color: #FFFFFF;
            font-weight: 800;
            font-size: 10.5px;
            padding: 5px 10px;
            letter-spacing: 0.5px;
            text-transform: uppercase;
            border: 1px solid #8E1E1B;
            text-align: center;
        }
        .doc-sub-badge {
            background: #F1F8E9;
            color: #2B4D12;
            border: 1px solid #7CB342;
            font-weight: 700;
            font-size: 9px;
            padding: 3px 8px;
            margin-top: 4px;
            text-align: center;
        }
        table { page-break-inside: auto; }
        thead { display: table-header-group; }
        tfoot { display: table-footer-group; }
        tr, .no-break, .bottom-split, .words-bar, .kpi-row, .kpi-box, .svg-box, .cover-hero, .milestone-box {
            break-inside: avoid;
            page-break-inside: avoid;
        }
        .page-break {
            break-before: page;
            page-break-before: always;
        }
        .page-after {
            break-after: page;
            page-break-after: always;
        }
        table.grid {
            width: 100%;
            border-collapse: collapse;
        }
        table.grid th, table.grid td {
            border: 1px solid #94A3B8;
            padding: 5px 7px;
            vertical-align: top;
        }
        table.grid th {
            background: #F1F5F9;
            color: #0F172A;
            font-weight: 700;
            text-align: left;
            font-size: 10px;
            text-transform: uppercase;
        }
        .num {
            text-align: right;
            font-variant-numeric: tabular-nums;
            white-space: nowrap;
        }
        .center { text-align: center; }
        .dr { color: #1D4ED8; font-weight: 600; }
        .cr { color: #B91C1C; font-weight: 600; }
        .grand-row td {
            background: #F1F8E9;
            font-weight: 800;
            font-size: 11.5px;
            color: #0F172A;
            border-top: 1.5px solid #0F172A;
            border-bottom: 1.5px solid #0F172A;
        }
        .words-bar {
            border-top: 1px solid #94A3B8;
            border-bottom: 1px solid #94A3B8;
            padding: 6px 10px;
            background: #F8FAFC;
            font-size: 10.5px;
        }
        .kpi-row {
            display: flex;
            gap: 6px;
            padding: 8px;
            background: #F8FAFC;
            border-bottom: 1px solid #94A3B8;
        }
        .kpi-box {
            flex: 1;
            border: 1px solid #CBD5E1;
            background: #FFFFFF;
            padding: 6px 8px;
            border-radius: 4px;
        }
        .kpi-label {
            font-size: 9px;
            text-transform: uppercase;
            color: #475569;
            font-weight: 700;
        }
        .kpi-val {
            font-size: 13px;
            font-weight: 800;
            color: #0F172A;
            margin-top: 2px;
        }
        .bottom-split {
            display: flex;
            width: 100%;
            border-top: 1px solid #94A3B8;
        }
        .bank-terms {
            width: 62%;
            border-right: 1px solid #94A3B8;
            padding: 8px 10px;
        }
        .sign-box {
            width: 38%;
            padding: 8px 10px;
            display: flex;
            flex-direction: column;
            justify-content: space-between;
            text-align: right;
        }
        .stamp-box {
            display: inline-block;
            border: 1.5px solid #8E1E1B;
            color: #8E1E1B;
            padding: 4px 10px;
            font-size: 9px;
            font-weight: 800;
            text-transform: uppercase;
            margin: 10px 0 6px auto;
            letter-spacing: 0.4px;
        }
        .section-title {
            font-size: 10.5px;
            font-weight: 700;
            color: #8E1E1B;
            text-transform: uppercase;
            margin-bottom: 3px;
        }
    """

    private fun ensureLogoDataUri(context: Context?): String? {
        cachedLogoDataUri?.let { return it }
        if (context == null) return null
        return try {
            val bmp = BitmapFactory.decodeResource(context.resources, R.drawable.nb_solar_icon_fg)
                ?: return null
            val uri = bitmapToDataUri(bmp, maxDim = 128)
            cachedLogoDataUri = uri
            uri
        } catch (_: Exception) {
            null
        }
    }

    private fun letterheadHtml(
        docTitle: String,
        subTag: String,
        logoUri: String? = cachedLogoDataUri
    ): String {
        val logoImg = if (!logoUri.isNullOrBlank()) {
            """<img src="$logoUri" class="brand-logo" alt="NB Logo" />"""
        } else {
            """<div style="width:42px;height:42px;border-radius:50%;background:#8E1E1B;color:#FFF;display:flex;align-items:center;justify-content:center;font-weight:800;font-size:13px;border:2px solid #7CB342;flex-shrink:0;">NB</div>"""
        }
        return """
        <div class="letterhead">
          <div class="brand-row">
            $logoImg
            <div>
              <div class="company-title">$COMPANY_NAME</div>
              <div class="company-sub">$COMPANY_ADDRESS</div>
              <div class="company-meta">GSTIN: $COMPANY_GSTIN &nbsp;|&nbsp; Tel.: $COMPANY_TEL_DISPLAY &nbsp;|&nbsp; Email: $COMPANY_EMAIL</div>
            </div>
          </div>
          <div style="flex-shrink:0;">
            <div class="doc-badge">${escapeHtml(docTitle)}</div>
            <div class="doc-sub-badge">${escapeHtml(subTag)}</div>
          </div>
        </div>
        """.trimIndent()
    }

    private fun bankAndTermsFooterHtml(): String = """
        <div class="bottom-split">
          <div class="bank-terms">
            <div class="section-title">Company Bank Details</div>
            <div><b>Bank Name:</b> $BANK_NAME &nbsp;|&nbsp; <b>Branch:</b> $BANK_BRANCH</div>
            <div><b>A/c No.:</b> $BANK_ACCOUNT_NO &nbsp;|&nbsp; <b>IFSC Code:</b> $BANK_IFSC</div>
            <div class="section-title" style="margin-top:8px;">Terms &amp; Conditions</div>
            <div style="font-size:9.5px;color:#334155;">
              1. Goods once sold will not be taken back.<br/>
              2. Interest @ 18% p.a. will be charged if payment is not made within due date.<br/>
              3. All disputes subject to Udaipur (Rajasthan) Jurisdiction only. E. &amp; O.E.
            </div>
          </div>
          <div class="sign-box">
            <div style="font-weight:700;color:#0F172A;">For $COMPANY_NAME</div>
            <div class="stamp-box">NB ENGINEERING WORKS<br/>UDAIPUR • VERIFIED</div>
            <div style="font-weight:600;border-top:1px solid #94A3B8;padding-top:4px;">Authorized Signatory</div>
          </div>
        </div>
    """.trimIndent()

    /**
     * Exports all BUSY 21 vouchers in standard BUSY 21 Excel/CSV Import format
     * and launches the Android Share Sheet.
     */
    fun exportBusy21Csv(context: Context, vouchers: List<BusyVoucher>): Result<String> {
        return try {
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val fileName = "BUSY21_NB_Solar_Wind_Import_${todayIsoDate()}.csv"
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
     * Prints an official BUSY 21 style A4 Tax Invoice (`NB XX/2026-27`) with exact columns,
     * CGST/SGST/IGST, Round Off, Amount in Words, HDFC Bank Details, Terms & Stamp.
     */
    fun printTaxInvoiceA4(context: Context, voucher: BusyVoucher) {
        ensureLogoDataUri(context)
        if (voucher.type == BusyVoucherType.RECEIPT) {
            printPaymentReceiptA4(context, voucher)
            return
        }

        val docHeading = when (voucher.type) {
            BusyVoucherType.SALES -> "TAX INVOICE"
            BusyVoucherType.PURCHASE -> "PURCHASE VOUCHER"
            BusyVoucherType.PAYMENT -> "PAYMENT VOUCHER"
            BusyVoucherType.JOURNAL -> "JOURNAL VOUCHER"
            BusyVoucherType.CONTRA -> "CONTRA VOUCHER"
            BusyVoucherType.RECEIPT -> "PAYMENT RECEIPT"
        }

        val rowsHtml = if (voucher.lineItems.isEmpty()) {
            """
            <tr>
              <td class="center">1</td>
              <td>${escapeHtml(voucher.narration.ifBlank { "Solar / Wind EPC Supply & Installation Services" })}</td>
              <td class="center">85414300</td>
              <td class="num">1.00</td>
              <td class="center">Job</td>
              <td class="num">${formatInr(voucher.taxableAmount, true)}</td>
              <td class="num">${formatInr(voucher.taxableAmount, true)}</td>
            </tr>
            """.trimIndent()
        } else {
            voucher.lineItems.mapIndexed { index, item ->
                """
                <tr>
                  <td class="center">${index + 1}</td>
                  <td>${escapeHtml(item.itemName)}</td>
                  <td class="center">${escapeHtml(item.hsnCode.ifBlank { "85414300" })}</td>
                  <td class="num">${String.format(Locale.US, "%.2f", item.quantity)}</td>
                  <td class="center">${escapeHtml(item.unit)}</td>
                  <td class="num">${formatInr(item.rate, true)}</td>
                  <td class="num">${formatInr(item.taxableAmount, true)}</td>
                </tr>
                """.trimIndent()
            }.joinToString("\n")
        }

        val taxable = voucher.taxableAmount.coerceAtLeast(0.0)
        val cgstPct = if (taxable > 0 && voucher.cgstAmount > 0) (voucher.cgstAmount / taxable) * 100.0 else 9.0
        val sgstPct = if (taxable > 0 && voucher.sgstAmount > 0) (voucher.sgstAmount / taxable) * 100.0 else 9.0
        val igstPct = if (taxable > 0 && voucher.igstAmount > 0) (voucher.igstAmount / taxable) * 100.0 else 18.0

        val exactTotal = voucher.totalAmount
        val roundedTotal = exactTotal.roundToLong().toDouble()
        val roundOff = roundedTotal - exactTotal

        val html = """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="utf-8" />
          <style>$SHARED_CSS</style>
        </head>
        <body>
          <div class="sheet">
            ${letterheadHtml(docHeading, "ORIGINAL FOR RECIPIENT")}
            <table class="grid">
              <tr>
                <td style="width:55%;">
                  <b>Billed To / Party:</b> ${escapeHtml(voucher.partyAccountName)}<br/>
                  <b>Party GSTIN:</b> ${escapeHtml(voucher.partyGstin.ifBlank { "Unregistered / B2C Consumer" })}<br/>
                  <b>Cost Center:</b> ${escapeHtml(voucher.costCenter.label)}
                </td>
                <td style="width:45%;">
                  <b>Invoice / Vch No.:</b> ${escapeHtml(voucher.voucherNo)}<br/>
                  <b>Date:</b> ${escapeHtml(voucher.date)}<br/>
                  <b>Ref / UTR:</b> ${escapeHtml(voucher.utrOrChequeNo.ifBlank { voucher.billRefNo.ifBlank { "-" } })}
                </td>
              </tr>
            </table>
            <table class="grid">
              <thead>
                <tr>
                  <th class="center" style="width:6%;">S.N.</th>
                  <th style="width:36%;">Description of Goods / Services</th>
                  <th class="center" style="width:14%;">HSN/SAC Code</th>
                  <th class="num" style="width:9%;">Qty.</th>
                  <th class="center" style="width:8%;">Unit</th>
                  <th class="num" style="width:13%;">Price</th>
                  <th class="num" style="width:14%;">Amount (₹)</th>
                </tr>
              </thead>
              <tbody>
                $rowsHtml
              </tbody>
            </table>
            <table class="grid">
              <tr>
                <td rowspan="5" style="width:55%;vertical-align:top;">
                  <b>Narration / Remarks:</b><br/>
                  ${escapeHtml(voucher.narration.ifBlank { "Supply & installation under NB Solar & Wind EPC." })}<br/><br/>
                  <b>Contra Ledger:</b> ${escapeHtml(voucher.contraAccountName)}
                </td>
                <td style="width:27%;">Taxable Value</td>
                <td class="num" style="width:18%;">${formatInr(taxable, true)}</td>
              </tr>
              <tr>
                <td>Add: CGST @ ${String.format(Locale.US, "%.2f", cgstPct)}%</td>
                <td class="num">${formatInr(voucher.cgstAmount, true)}</td>
              </tr>
              <tr>
                <td>Add: SGST @ ${String.format(Locale.US, "%.2f", sgstPct)}%</td>
                <td class="num">${formatInr(voucher.sgstAmount, true)}</td>
              </tr>
              <tr>
                <td>${if (voucher.igstAmount > 0) "Add: IGST @ ${String.format(Locale.US, "%.2f", igstPct)}%" else "Round Off (+/-)"}</td>
                <td class="num">${if (voucher.igstAmount > 0) formatInr(voucher.igstAmount, true) else String.format(Locale.US, "%+.2f", roundOff)}</td>
              </tr>
              <tr class="grand-row">
                <td>Grand Total</td>
                <td class="num">${formatInr(roundedTotal, true)}</td>
              </tr>
            </table>
            <div class="words-bar">
              <b>Amount Chargeable (in words):</b> ${escapeHtml(amountInWords(roundedTotal))}
            </div>
            ${bankAndTermsFooterHtml()}
          </div>
        </body>
        </html>
        """.trimIndent()

        printHtmlDocument(context, "NB_Invoice_${voucher.voucherNo.replace("/", "_")}", html)
    }

    /**
     * Prints an official Delivery Challan PDF (`S.N.`, `Description of Goods`, `HSN/SAC Code`, `Qty.`, `Unit`).
     */
    fun printDeliveryChallanA4(
        context: Context,
        partyName: String,
        partyAddress: String,
        partyPhone: String,
        challanNo: String,
        date: String,
        items: List<BusyLineItem>,
        remarks: String = ""
    ) {
        ensureLogoDataUri(context)
        val rowsHtml = if (items.isEmpty()) {
            """<tr><td colspan="5" class="center">No material items listed for dispatch yet.</td></tr>"""
        } else {
            items.mapIndexed { idx, itm ->
                """
                <tr>
                  <td class="center">${idx + 1}</td>
                  <td>${escapeHtml(itm.itemName)}</td>
                  <td class="center">${escapeHtml(itm.hsnCode.ifBlank { "85414300" })}</td>
                  <td class="num">${String.format(Locale.US, "%.2f", itm.quantity)}</td>
                  <td class="center">${escapeHtml(itm.unit)}</td>
                </tr>
                """.trimIndent()
            }.joinToString("\n")
        }

        val totalQty = items.sumOf { it.quantity }
        val html = """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="utf-8" />
          <style>$SHARED_CSS</style>
        </head>
        <body>
          <div class="sheet">
            ${letterheadHtml("DELIVERY CHALLAN", "SITE DISPATCH COPY")}
            <table class="grid">
              <tr>
                <td style="width:58%;">
                  <b>Consignee / Party:</b> ${escapeHtml(partyName)}<br/>
                  <b>Site Address:</b> ${escapeHtml(partyAddress.ifBlank { "Udaipur, Rajasthan" })}<br/>
                  <b>Contact Mobile:</b> ${escapeHtml(partyPhone.ifBlank { "-" })}
                </td>
                <td style="width:42%;">
                  <b>Challan No.:</b> ${escapeHtml(challanNo)}<br/>
                  <b>Dispatch Date:</b> ${escapeHtml(date)}<br/>
                  <b>Transport:</b> Company Site Dispatch
                </td>
              </tr>
            </table>
            <table class="grid">
              <thead>
                <tr>
                  <th class="center" style="width:8%;">S.N.</th>
                  <th style="width:50%;">Description of Goods</th>
                  <th class="center" style="width:18%;">HSN/SAC Code</th>
                  <th class="num" style="width:12%;">Qty.</th>
                  <th class="center" style="width:12%;">Unit</th>
                </tr>
              </thead>
              <tbody>
                $rowsHtml
                <tr class="grand-row">
                  <td colspan="3" class="num">Total Dispatched Quantity</td>
                  <td class="num">${String.format(Locale.US, "%.2f", totalQty)}</td>
                  <td class="center">Units</td>
                </tr>
              </tbody>
            </table>
            <div class="words-bar">
              <b>Dispatch Remarks:</b> ${escapeHtml(remarks.ifBlank { "Material dispatched in good condition for Solar / Wind EPC installation." })}
            </div>
            <div class="bottom-split">
              <div class="bank-terms">
                <div class="section-title">Receiver's Acknowledgement</div>
                <div style="font-size:10px;color:#334155;margin-top:4px;">
                  Received the above materials in good condition.<br/><br/><br/>
                  <b>Receiver Signature &amp; Date:</b> ________________________
                </div>
              </div>
              <div class="sign-box">
                <div style="font-weight:700;color:#0F172A;">For $COMPANY_NAME</div>
                <div class="stamp-box">NB ENGINEERING WORKS<br/>DISPATCH VERIFIED</div>
                <div style="font-weight:600;border-top:1px solid #94A3B8;padding-top:4px;">Authorized Signatory</div>
              </div>
            </div>
          </div>
        </body>
        </html>
        """.trimIndent()

        printHtmlDocument(context, "NB_Challan_${challanNo.replace("/", "_")}", html)
    }

    fun printPartyHardwareChallanA4(
        context: Context,
        party: PartyProject,
        allocatedPanels: List<DcrPanel>,
        allocatedMeters: List<SolarMeter>,
        rawMaterials: List<RawMaterial>
    ) {
        val lineItems = buildList {
            if (allocatedPanels.isNotEmpty()) {
                val serialPreview = allocatedPanels.joinToString(", ") { it.serialNumber }
                add(
                    BusyLineItem(
                        itemName = "${allocatedPanels.first().brand} ${allocatedPanels.first().wattage}W DCR Solar Panels (S/N: $serialPreview)",
                        hsnCode = "85414300",
                        quantity = allocatedPanels.size.toDouble(),
                        unit = "Nos"
                    )
                )
            }
            allocatedMeters.forEach { m ->
                add(
                    BusyLineItem(
                        itemName = "${m.meterType.label} - ${m.makeAndPhase} (S/N: ${m.serialNumber})",
                        hsnCode = "90283010",
                        quantity = 1.0,
                        unit = "Nos"
                    )
                )
            }
            rawMaterials.forEach { rm ->
                val issuedQty = rm.issueLogs.filter { it.partyId == party.id }.sumOf { it.quantity }
                if (issuedQty > 0.0) {
                    val hsn = if (rm.category.contains("Structure", ignoreCase = true)) "73089090" else "85444999"
                    add(
                        BusyLineItem(
                            itemName = "${rm.name} (${rm.category})",
                            hsnCode = hsn,
                            quantity = issuedQty,
                            unit = rm.unit
                        )
                    )
                }
            }
        }
        val challanNo = "DC-${party.partyName.take(4).uppercase(Locale.US).replace(" ", "")}-${todayIsoDate().replace("-", "")}"
        printDeliveryChallanA4(
            context = context,
            partyName = party.partyName,
            partyAddress = party.cityOrLocation.ifBlank { party.address },
            partyPhone = party.mobile,
            challanNo = challanNo,
            date = todayIsoDate(),
            items = lineItems,
            remarks = "Capacity: ${party.capacityKw} kW (${party.division.label}) | K-No: ${party.consumerNumber.ifBlank { "-" }}"
        )
    }

    /**
     * Prints an official Payment Receipt PDF (`NB Receipt`) with HDFC Bank details & Amount in Words.
     */
    fun printPaymentReceiptA4(context: Context, voucher: BusyVoucher) {
        ensureLogoDataUri(context)
        val html = """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="utf-8" />
          <style>$SHARED_CSS</style>
        </head>
        <body>
          <div class="sheet">
            ${letterheadHtml("PAYMENT RECEIPT", "OFFICIAL ACKNOWLEDGEMENT")}
            <table class="grid">
              <tr>
                <td style="width:55%;"><b>Receipt No.:</b> ${escapeHtml(voucher.voucherNo)}</td>
                <td style="width:45%;"><b>Receipt Date:</b> ${escapeHtml(voucher.date)}</td>
              </tr>
              <tr>
                <td><b>Received From (Party):</b> ${escapeHtml(voucher.partyAccountName)}</td>
                <td><b>Credited To Account:</b> ${escapeHtml(voucher.contraAccountName)}</td>
              </tr>
              <tr>
                <td><b>Mode / UTR / Cheque No.:</b> ${escapeHtml(voucher.utrOrChequeNo.ifBlank { "Cash / UPI" })}</td>
                <td><b>Paid By / Cost Center:</b> ${escapeHtml(voucher.paidBy.ifBlank { voucher.partyAccountName })} (${escapeHtml(voucher.costCenter.label)})</td>
              </tr>
            </table>
            <table class="grid">
              <thead>
                <tr>
                  <th class="center" style="width:10%;">S.N.</th>
                  <th style="width:65%;">Particulars</th>
                  <th class="num" style="width:25%;">Amount Received (₹)</th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td class="center">1</td>
                  <td>Being payment received from <b>${escapeHtml(voucher.partyAccountName)}</b> towards Solar / Wind EPC Project.<br/><small>${escapeHtml(voucher.narration)}</small></td>
                  <td class="num" style="font-weight:700;">${formatInr(voucher.totalAmount, true)}</td>
                </tr>
                <tr class="grand-row">
                  <td colspan="2" class="num">Total Received</td>
                  <td class="num">${formatInr(voucher.totalAmount, true)}</td>
                </tr>
              </tbody>
            </table>
            <div class="words-bar">
              <b>Sum of Rupees (in words):</b> ${escapeHtml(amountInWords(voucher.totalAmount))}
            </div>
            ${bankAndTermsFooterHtml()}
          </div>
        </body>
        </html>
        """.trimIndent()

        printHtmlDocument(context, "NB_Receipt_${voucher.voucherNo.replace("/", "_")}", html)
    }

    /**
     * Prints a BUSY 21 Party Account Ledger Statement in A4 1px bordered grid format.
     */
    fun printAccountLedgerA4(
        context: Context,
        account: BusyAccount,
        fromDate: String,
        toDate: String,
        rows: List<LedgerPrintRow>,
        closingBalance: Double
    ) {
        ensureLogoDataUri(context)
        val trs = rows.joinToString("\n") { r ->
            val balPair = formatBusyDrCr(r.runningBalance)
            val balClass = if (balPair.second) "dr" else "cr"
            """
            <tr>
              <td>${escapeHtml(r.date)}</td>
              <td>${escapeHtml(r.voucherType)}</td>
              <td>${escapeHtml(r.voucherNo)}</td>
              <td>${escapeHtml(r.particulars)}<br/><small style="color:#475569">${escapeHtml(r.narration)}</small></td>
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
          <style>$SHARED_CSS</style>
        </head>
        <body>
          <div class="sheet">
            ${letterheadHtml("ACCOUNT LEDGER", "BUSY 21 STATEMENT")}
            <table class="grid">
              <tr>
                <td><b>Account:</b> ${escapeHtml(account.name)} (${escapeHtml(account.group.label)})</td>
                <td><b>Period:</b> ${escapeHtml(fromDate)} to ${escapeHtml(toDate)}</td>
                <td class="num"><b>Opening:</b> ${formatBusyDrCr(account.openingBalance).first}</td>
              </tr>
            </table>
            <table class="grid">
              <thead>
                <tr>
                  <th>Date</th><th>Type</th><th>Vch No</th><th>Particulars &amp; Narration</th>
                  <th class="num">Debit (Dr)</th><th class="num">Credit (Cr)</th><th class="num">Balance</th>
                </tr>
              </thead>
              <tbody>
                $trs
                <tr class="grand-row">
                  <td colspan="6" class="num">Closing Balance</td>
                  <td class="num">${closingPair.first}</td>
                </tr>
              </tbody>
            </table>
            ${bankAndTermsFooterHtml()}
          </div>
        </body>
        </html>
        """.trimIndent()

        printHtmlDocument(context, "NB_Ledger_${account.name.replace(" ", "_")}", html)
    }

    /**
     * Generates and prints an MNRE PM Surya Ghar Domestic Content Requirement (DCR)
     * Undertaking & Serial Number Certificate PDF for the party.
     */
    fun printDcrCertificatePdf(
        context: Context,
        party: PartyProject,
        allocatedPanels: List<DcrPanel>
    ) {
        ensureLogoDataUri(context)
        val totalWp = allocatedPanels.sumOf { it.wattage }
        val panelRows = if (allocatedPanels.isEmpty()) {
            """<tr><td colspan="5" class="center">No DCR panel serial numbers allocated yet.</td></tr>"""
        } else {
            allocatedPanels.mapIndexed { idx, p ->
                """
                <tr>
                  <td class="center">${idx + 1}</td>
                  <td style="font-weight:700;">${escapeHtml(p.serialNumber)}</td>
                  <td>${escapeHtml(p.brand)}</td>
                  <td class="num">${p.wattage} Wp</td>
                  <td>${escapeHtml(p.purchaseInvoiceNo.ifBlank { "-" })}</td>
                </tr>
                """.trimIndent()
            }.joinToString("\n")
        }

        val html = """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="utf-8" />
          <style>$SHARED_CSS</style>
        </head>
        <body>
          <div class="sheet">
            ${letterheadHtml("DCR UNDERTAKING", "MNRE PM SURYA GHAR")}
            <table class="grid">
              <tr>
                <td style="width:55%;"><b>Consumer / Party:</b> ${escapeHtml(party.partyName)}</td>
                <td style="width:45%;"><b>AVVNL K-Number:</b> ${escapeHtml(party.consumerNumber.ifBlank { "-" })}</td>
              </tr>
              <tr>
                <td><b>Site Location:</b> ${escapeHtml(party.cityOrLocation.ifBlank { party.address })}</td>
                <td><b>Capacity:</b> ${party.capacityKw} kW (${escapeHtml(party.roofStructureType.label)})</td>
              </tr>
              <tr>
                <td><b>Consumer Mobile:</b> ${escapeHtml(party.mobile.ifBlank { "-" })}</td>
                <td><b>Certificate Date:</b> ${todayIsoDate()}</td>
              </tr>
            </table>
            <div class="words-bar">
              <b>Allocated Made-in-India DCR PV Modules:</b> ${allocatedPanels.size} Modules &nbsp;|&nbsp; <b>Total Capacity:</b> $totalWp Wp
            </div>
            <table class="grid">
              <thead>
                <tr>
                  <th class="center" style="width:8%;">S.N.</th>
                  <th style="width:36%;">Module Serial Number</th>
                  <th style="width:22%;">OEM Make</th>
                  <th class="num" style="width:14%;">Wattage</th>
                  <th style="width:20%;">Invoice Ref</th>
                </tr>
              </thead>
              <tbody>$panelRows</tbody>
            </table>
            <div class="words-bar" style="background:#F1F8E9;">
              <b>Self-Declaration (PM Surya Ghar: Muft Bijli Yojana):</b> Certified that all solar PV modules listed above are manufactured in India with domestic solar cells &amp; modules compliant with MNRE Domestic Content Requirement (DCR) norms and installed at the consumer premises.
            </div>
            ${bankAndTermsFooterHtml()}
          </div>
        </body>
        </html>
        """.trimIndent()

        printHtmlDocument(context, "DCR_Certificate_${party.partyName.replace(" ", "_")}", html)
    }

    /**
     * Prints a detailed Party Costing & Profit Report PDF for Owner / Accounts.
     */
    fun printPartyCostingReportA4(
        context: Context,
        row: PartyCostingRow,
        allocatedPanels: List<DcrPanel>,
        allocatedMeters: List<SolarMeter>
    ) {
        ensureLogoDataUri(context)
        val party = row.party
        val html = """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="utf-8" />
          <style>$SHARED_CSS</style>
        </head>
        <body>
          <div class="sheet">
            ${letterheadHtml("PARTY COSTING REPORT", "PROFITABILITY AUDIT")}
            <table class="grid">
              <tr>
                <td style="width:55%;">
                  <b>Party Name:</b> ${escapeHtml(party.partyName)}<br/>
                  <b>Division &amp; Source:</b> ${escapeHtml(party.division.label)} • ${escapeHtml(party.leadSource.label)}<br/>
                  <b>Location:</b> ${escapeHtml(party.cityOrLocation.ifBlank { party.address })}
                </td>
                <td style="width:45%;">
                  <b>System Capacity:</b> ${party.capacityKw} kW (${escapeHtml(party.roofStructureType.label)})<br/>
                  <b>Report Date:</b> ${todayIsoDate()}<br/>
                  <b>Payment Recd / Due:</b> ${formatInr(party.receivedAmount)} / ${formatInr(party.pendingAmount)}
                </td>
              </tr>
            </table>
            <table class="grid">
              <thead>
                <tr>
                  <th class="center" style="width:8%;">S.N.</th>
                  <th style="width:67%;">Costing &amp; Revenue Particulars</th>
                  <th class="num" style="width:25%;">Amount (₹)</th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td class="center">1</td>
                  <td><b>Gross Contract Deal Value</b> (${party.capacityKw} kW @ ${formatInr(party.quotedRatePerKw)}/kW)</td>
                  <td class="num dr">${formatInr(row.dealValue, true)}</td>
                </tr>
                <tr>
                  <td class="center">2</td>
                  <td>(-) Main Solar Equipment (${allocatedPanels.size} DCR Panels, ${allocatedMeters.size} Meters &amp; Issued BOS)</td>
                  <td class="num cr">- ${formatInr(row.mainMaterialCost, true)}</td>
                </tr>
                <tr>
                  <td class="center">3</td>
                  <td>(-) On-Site Local Material Purchases (${party.sitePurchases.size} Site Bills)</td>
                  <td class="num cr">- ${formatInr(row.onSitePurchaseCost, true)}</td>
                </tr>
                <tr>
                  <td class="center">4</td>
                  <td>(-) Daily Site Kharcha, Labour &amp; Worker Advances</td>
                  <td class="num cr">- ${formatInr(row.dailyKharchaCost, true)}</td>
                </tr>
                <tr>
                  <td class="center">5</td>
                  <td>(-) Partner / Referral Commission (${String.format(Locale.US, "%.1f", party.partnerCommissionPct)}% • ${escapeHtml(party.referrerName.ifBlank { party.leadSource.shortLabel })})</td>
                  <td class="num cr">- ${formatInr(row.referralCommission, true)}</td>
                </tr>
                <tr class="grand-row">
                  <td colspan="2" class="num">Net Party Profit (${String.format(Locale.US, "%.1f", row.marginPct)}% Margin)</td>
                  <td class="num">${formatInr(row.netProfit, true)}</td>
                </tr>
              </tbody>
            </table>
            <div class="words-bar">
              <b>Net Profit (in words):</b> ${escapeHtml(amountInWords(abs(row.netProfit)))} ${if (row.netProfit < 0) "(Net Deficit)" else ""}
            </div>
            ${bankAndTermsFooterHtml()}
          </div>
        </body>
        </html>
        """.trimIndent()

        printHtmlDocument(context, "NB_Costing_${party.partyName.replace(" ", "_")}", html)
    }

    /**
     * Generates and prints an A4 Solar EPC Proposal PDF in one of 4 customer-ready formats.
     */
    fun printProposalWithCostingA4(
        context: Context,
        format: ProposalFormat = ProposalFormat.PM_SURYA_GHAR_2PG,
        customerName: String,
        customerMobile: String,
        siteLocation: String,
        consumerNo: String,
        breakdown: RealCostingBreakdown,
        comparisonBreakdowns: List<RealCostingBreakdown> = emptyList(),
        dimensions: SiteSurveyDimensions,
        rooftopBeforeBitmap: Bitmap? = null,
        rooftopAfterBitmap: Bitmap? = null,
        inverterBeforeBitmap: Bitmap? = null,
        inverterAfterBitmap: Bitmap? = null,
        includeOwnerInternalCosting: Boolean = false,
        detailedOptions: DetailedProposalOptions = DetailedProposalOptions(
            capacityKwp = breakdown.capacityKw,
            baseSystemPriceInr = breakdown.grossQuotedPriceInr,
            subsidyAmountInr = breakdown.pmSuryaGharSubsidyInr
        )
    ) {
        val cleanName = customerName.ifBlank { "Valued Solar Consumer" }
        val html = buildProposalHtml(
            context = context,
            format = format,
            customerName = cleanName,
            customerMobile = customerMobile,
            siteLocation = siteLocation,
            consumerNo = consumerNo,
            breakdown = breakdown,
            comparisonBreakdowns = comparisonBreakdowns,
            dimensions = dimensions,
            rooftopBeforeBitmap = rooftopBeforeBitmap,
            rooftopAfterBitmap = rooftopAfterBitmap,
            inverterBeforeBitmap = inverterBeforeBitmap,
            inverterAfterBitmap = inverterAfterBitmap,
            includeOwnerInternalCosting = includeOwnerInternalCosting,
            detailedOptions = detailedOptions
        )
        val slug = cleanName.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').ifBlank { "Consumer" }
        printHtmlDocument(context, "NB_Proposal_${format.name}_$slug", html)
    }

    /**
     * Builds the exact HTML rendered in both the Interactive Live Proposal Preview dialog
     * and the A4 PDF PrintManager output for all 4 Proposal Formats.
     */
    fun buildProposalHtml(
        context: Context?,
        format: ProposalFormat,
        customerName: String,
        customerMobile: String,
        siteLocation: String,
        consumerNo: String,
        breakdown: RealCostingBreakdown,
        comparisonBreakdowns: List<RealCostingBreakdown> = emptyList(),
        dimensions: SiteSurveyDimensions,
        rooftopBeforeBitmap: Bitmap? = null,
        rooftopAfterBitmap: Bitmap? = null,
        inverterBeforeBitmap: Bitmap? = null,
        inverterAfterBitmap: Bitmap? = null,
        includeOwnerInternalCosting: Boolean = false,
        detailedOptions: DetailedProposalOptions = DetailedProposalOptions(
            capacityKwp = breakdown.capacityKw,
            baseSystemPriceInr = breakdown.grossQuotedPriceInr,
            subsidyAmountInr = breakdown.pmSuryaGharSubsidyInr
        )
    ): String {
        val logoUri = ensureLogoDataUri(context)
        val cleanName = customerName.ifBlank { "Valued Solar Consumer" }
        val cleanLoc = siteLocation.ifBlank { "Udaipur, Rajasthan" }
        val cleanMob = customerMobile.ifBlank { "-" }
        val cleanKNo = consumerNo.ifBlank { "-" }
        val proposalRef = "NB-Q/${todayIsoDate().replace("-", "")}/${breakdown.capacityKw.roundToInt()}KW"

        val roofBeforeUri = bitmapToDataUri(rooftopBeforeBitmap)
        val roofAfterUri = bitmapToDataUri(rooftopAfterBitmap)
        val invBeforeUri = bitmapToDataUri(inverterBeforeBitmap)
        val invAfterUri = bitmapToDataUri(inverterAfterBitmap)

        val photosSection = if (roofBeforeUri != null || roofAfterUri != null || invBeforeUri != null || invAfterUri != null) {
            """
            <table class="grid no-break" style="margin-top:6px;">
              <thead>
                <tr>
                  <th colspan="2">AI Site Photo Placement (Before &amp; After Solar Visualization)</th>
                </tr>
              </thead>
              <tbody>
                ${if (roofBeforeUri != null || roofAfterUri != null) """
                <tr>
                  <td style="width:50%;text-align:center;">
                    <div style="font-weight:700;margin-bottom:4px;">Rooftop Before</div>
                    ${if (roofBeforeUri != null) """<img src="$roofBeforeUri" style="max-width:100%;max-height:155px;border:1px solid #CBD5E1;border-radius:4px;" />""" else "<i>Not uploaded</i>"}
                  </td>
                  <td style="width:50%;text-align:center;">
                    <div style="font-weight:700;color:#8E1E1B;margin-bottom:4px;">AI Rooftop Placement (${breakdown.wiring.panelCount} Panels • ${escapeHtml(breakdown.structureType.shortLabel)})</div>
                    ${if (roofAfterUri != null) """<img src="$roofAfterUri" style="max-width:100%;max-height:155px;border:1.5px solid #7CB342;border-radius:4px;" />""" else "<i>AI placement pending</i>"}
                  </td>
                </tr>
                """ else ""}
                ${if (invBeforeUri != null || invAfterUri != null) """
                <tr>
                  <td style="width:50%;text-align:center;">
                    <div style="font-weight:700;margin-bottom:4px;">Inverter Wall Before</div>
                    ${if (invBeforeUri != null) """<img src="$invBeforeUri" style="max-width:100%;max-height:155px;border:1px solid #CBD5E1;border-radius:4px;" />""" else "<i>Not uploaded</i>"}
                  </td>
                  <td style="width:50%;text-align:center;">
                    <div style="font-weight:700;color:#8E1E1B;margin-bottom:4px;">AI Inverter, ACDB/DCDB &amp; Conduit Placement</div>
                    ${if (invAfterUri != null) """<img src="$invAfterUri" style="max-width:100%;max-height:155px;border:1.5px solid #7CB342;border-radius:4px;" />""" else "<i>AI placement pending</i>"}
                  </td>
                </tr>
                """ else ""}
              </tbody>
            </table>
            """.trimIndent()
        } else ""

        val structureSvg = build3DStructureSvg(breakdown.structureType, dimensions.tiltDegrees, breakdown.wiring.panelCount)
        val rooftopCadSvg = buildRooftopLayoutSvg(dimensions, breakdown)
        val sldSvg = buildSingleLineDiagramSvg(breakdown)

        val internalCostingAppendix = if (includeOwnerInternalCosting) {
            val itemRows = breakdown.lineItems.mapIndexed { i, li ->
                """
                <tr>
                  <td class="center">${i + 1}</td>
                  <td><b>${escapeHtml(li.title)}</b><br/><small style="color:#475569;">${escapeHtml(li.spec)}</small></td>
                  <td class="num">${formatInr(li.landingCostInr, true)}</td>
                </tr>
                """.trimIndent()
            }.joinToString("\n")
            """
            <table class="grid no-break" style="margin-top:8px;">
              <thead>
                <tr><th colspan="3" style="background:#FFF1F2;color:#8E1E1B;">CONFIDENTIAL — OWNER &amp; ACCOUNTS REAL COSTING AUDIT</th></tr>
                <tr><th class="center" style="width:8%;">S.N.</th><th style="width:67%;">EPC Landing Cost Component</th><th class="num" style="width:25%;">Landing Cost (₹)</th></tr>
              </thead>
              <tbody>
                $itemRows
                <tr><td colspan="2" class="num"><b>Total EPC Real Landing Cost</b></td><td class="num cr">${formatInr(breakdown.totalLandingCostInr, true)}</td></tr>
                <tr><td colspan="2" class="num"><b>EPC Profit Margin (${String.format(Locale.US, "%.1f", breakdown.profitMarginPct)}%)</b></td><td class="num" style="color:#2B4D12;font-weight:700;">+ ${formatInr(breakdown.profitMarginInr, true)}</td></tr>
                <tr class="grand-row"><td colspan="2" class="num">Taxable Quoted Value (Excl. GST)</td><td class="num">${formatInr(breakdown.taxableQuotedPriceInr, true)}</td></tr>
              </tbody>
            </table>
            """.trimIndent()
        } else ""

        val customerMetaTable = """
            <table class="grid no-break">
              <tr>
                <td style="width:55%;">
                  <b>Prepared For:</b> ${escapeHtml(cleanName)}<br/>
                  <b>Site Location:</b> ${escapeHtml(cleanLoc)}<br/>
                  <b>Mobile / AVVNL K-No.:</b> ${escapeHtml(cleanMob)} &nbsp;|&nbsp; ${escapeHtml(cleanKNo)}
                </td>
                <td style="width:45%;">
                  <b>Proposal Ref / Date:</b> $proposalRef (${todayIsoDate()})<br/>
                  <b>System Capacity:</b> ${breakdown.capacityKw} kW (${escapeHtml(breakdown.systemType.label)})<br/>
                  <b>Mounting Structure:</b> ${escapeHtml(breakdown.structureType.label)}
                </td>
              </tr>
            </table>
        """.trimIndent()

        val pricingSummaryTable = """
            <table class="grid no-break" style="margin-top:6px;">
              <thead>
                <tr>
                  <th class="center" style="width:8%;">S.N.</th>
                  <th style="width:67%;">Customer Proposal Price &amp; PM Surya Ghar Subsidy ("Kitne Ka Padega")</th>
                  <th class="num" style="width:25%;">Amount (₹)</th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td class="center">1</td>
                  <td>Turnkey ${breakdown.capacityKw} kW ${escapeHtml(breakdown.systemType.label)} EPC Package (Excl. GST)</td>
                  <td class="num">${formatInr(breakdown.taxableQuotedPriceInr, true)}</td>
                </tr>
                <tr>
                  <td class="center">2</td>
                  <td>Add: Composite Solar GST (CGST + SGST)</td>
                  <td class="num">${formatInr(breakdown.gstAmountInr, true)}</td>
                </tr>
                <tr>
                  <td class="center">3</td>
                  <td><b>Gross Quoted Turnkey Price (Incl. GST, Structure, Wiring &amp; Net Metering)</b></td>
                  <td class="num dr">${formatInr(breakdown.grossQuotedPriceInr, true)}</td>
                </tr>
                <tr>
                  <td class="center">4</td>
                  <td>(-) PM Surya Ghar: Muft Bijli Yojana Central Subsidy (DBT to Consumer Bank)</td>
                  <td class="num" style="color:#2B4D12;font-weight:700;">- ${formatInr(breakdown.pmSuryaGharSubsidyInr, true)}</td>
                </tr>
                <tr class="grand-row">
                  <td colspan="2" class="num">FINAL EFFECTIVE NET COST AFTER SUBSIDY</td>
                  <td class="num">${formatInr(breakdown.finalNetCostAfterSubsidyInr, true)}</td>
                </tr>
              </tbody>
            </table>
            <div class="words-bar">
              <b>Effective Net Cost (in words):</b> ${escapeHtml(amountInWords(breakdown.finalNetCostAfterSubsidyInr))} &nbsp;|&nbsp;
              <b>Est. Monthly Savings:</b> ${formatInr(breakdown.estimatedMonthlySavingsInr)}/mo (~${breakdown.estimatedPaybackYears} Yrs Payback)
            </div>
        """.trimIndent()

        val bodyHtml = when (format) {
            ProposalFormat.PM_SURYA_GHAR_2PG -> buildSevenPageProposal2026Html(
                logoUri = logoUri,
                proposalRef = proposalRef,
                cleanName = cleanName,
                cleanMob = cleanMob,
                cleanLoc = cleanLoc,
                cleanKNo = cleanKNo,
                breakdown = breakdown,
                dimensions = dimensions,
                options = detailedOptions,
                photosSection = photosSection,
                rooftopCadSvg = rooftopCadSvg,
                structureSvg = structureSvg,
                sldSvg = sldSvg,
                internalCostingAppendix = internalCostingAppendix
            )

            ProposalFormat.FULL_3D_DOSSIER_4PG -> {
                val matrixOptions = if (comparisonBreakdowns.isNotEmpty()) comparisonBreakdowns else listOf(breakdown)
                val matrixRows = matrixOptions.joinToString("\n") { opt ->
                    val highlight = if (opt.systemType == breakdown.systemType) "style=\"background:#F1F8E9;font-weight:700;\"" else ""
                    """
                    <tr $highlight>
                      <td>${escapeHtml(opt.systemType.label)}${if (opt.systemType == breakdown.systemType) " ★" else ""}</td>
                      <td class="num">${formatInr(opt.grossQuotedPriceInr)}</td>
                      <td class="num" style="color:#2B4D12;">${if (opt.pmSuryaGharSubsidyInr > 0) "- ${formatInr(opt.pmSuryaGharSubsidyInr)}" else "₹0"}</td>
                      <td class="num" style="color:#8E1E1B;"><b>${formatInr(opt.finalNetCostAfterSubsidyInr)}</b></td>
                      <td class="num">${opt.estimatedPaybackYears} Yrs</td>
                    </tr>
                    """.trimIndent()
                }
                """
                <!-- PAGE 1: EXECUTIVE COVER & COSTING -->
                <div class="sheet">
                  ${letterheadHtml("EXECUTIVE EPC DOSSIER", "4-PAGE ENGINEERING PROPOSAL", logoUri)}
                  <div class="cover-hero" style="background:linear-gradient(135deg,#8E1E1B 0%,#5A110F 100%);color:#FFF;padding:14px 16px;">
                    <div style="font-size:10px;letter-spacing:1px;text-transform:uppercase;color:#DCEDC8;font-weight:700;">ENGINEERED FOR HIGH-YIELD SOLAR PERFORMANCE</div>
                    <div style="font-size:18px;font-weight:800;margin-top:4px;">${breakdown.capacityKw} kW ${escapeHtml(breakdown.systemType.label)} Solar Power Plant</div>
                    <div style="font-size:11px;margin-top:3px;color:#F8FAFC;">Custom Engineered Proposal for <b>${escapeHtml(cleanName)}</b> • ${escapeHtml(cleanLoc)}</div>
                  </div>
                  $customerMetaTable
                  <div class="kpi-row">
                    <div class="kpi-box"><div class="kpi-label">Modules &amp; Area</div><div class="kpi-val">${breakdown.wiring.panelCount} × ${dimensions.panelWattageWp}Wp</div></div>
                    <div class="kpi-box"><div class="kpi-label">Gross Quote (Incl. GST)</div><div class="kpi-val">${formatInr(breakdown.grossQuotedPriceInr)}</div></div>
                    <div class="kpi-box" style="border-color:#7CB342;background:#F1F8E9;"><div class="kpi-label">PM Surya Ghar Subsidy</div><div class="kpi-val" style="color:#2B4D12;">- ${formatInr(breakdown.pmSuryaGharSubsidyInr)}</div></div>
                    <div class="kpi-box" style="border-color:#8E1E1B;background:#FFF8F7;"><div class="kpi-label">Final Net Price</div><div class="kpi-val" style="color:#8E1E1B;">${formatInr(breakdown.finalNetCostAfterSubsidyInr)}</div></div>
                  </div>
                  $pricingSummaryTable
                </div>

                <!-- PAGE 2: CAD ROOFTOP MAP + 3D STRUCTURE + SLD + WIRING -->
                <div class="sheet page-break" style="margin-top:10px;">
                  ${letterheadHtml("3D & ELECTRICAL DESIGN", "PAGE 2 OF 4", logoUri)}
                  <table class="grid no-break">
                    <thead>
                      <tr>
                        <th style="width:50%;">Top-Down Rooftop CAD Layout (${dimensions.roofLengthM}m × ${dimensions.roofWidthM}m)</th>
                        <th style="width:50%;">3D Mounting Structure (${escapeHtml(breakdown.structureType.shortLabel)})</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr>
                        <td class="center svg-box">$rooftopCadSvg</td>
                        <td class="center svg-box">
                          $structureSvg
                          <div style="font-size:9.5px;color:#334155;">Tilt: ${dimensions.tiltDegrees.roundToInt()}° | Azimuth: ${dimensions.azimuthDegrees.roundToInt()}° | Clearance: ${breakdown.structureType.clearanceMeters}m | HDGI ~${breakdown.wiring.estimatedStructureKg.roundToInt()} kg</div>
                        </td>
                      </tr>
                    </tbody>
                  </table>
                  <table class="grid no-break" style="margin-top:6px;">
                    <thead>
                      <tr>
                        <th style="width:56%;">Electrical Single Line Diagram (SLD)</th>
                        <th style="width:44%;">Measured Wiring &amp; Survey Quantities</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr>
                        <td class="center svg-box">$sldSvg</td>
                        <td style="padding:0;">
                          <table class="grid">
                            <tr><td><b>Roof Area</b></td><td class="num">${breakdown.wiring.roofAreaSqM} m² (Req ${breakdown.wiring.requiredPanelAreaSqM} m²)</td></tr>
                            <tr><td><b>Solar DC Cable (4 sq.mm)</b></td><td class="num">${breakdown.wiring.dcCableMeters} m</td></tr>
                            <tr><td><b>Armoured AC Cable</b></td><td class="num">${breakdown.wiring.acCableMeters} m</td></tr>
                            <tr><td><b>Earthing Wire (3-Pit)</b></td><td class="num">${breakdown.wiring.earthingWireMeters} m</td></tr>
                            <tr><td><b>Heavy UV PVC Conduit</b></td><td class="num">${breakdown.wiring.pvcConduitMeters} m</td></tr>
                            <tr><td><b>HDGI Steel Weight</b></td><td class="num">~${breakdown.wiring.estimatedStructureKg.roundToInt()} kg</td></tr>
                          </table>
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </div>

                <!-- PAGE 3: AI SITE PHOTOS + SYSTEM COMPARISON MATRIX -->
                <div class="sheet page-break" style="margin-top:10px;">
                  ${letterheadHtml("AI SITE VISUAL & OPTIONS", "PAGE 3 OF 4", logoUri)}
                  ${if (photosSection.isNotBlank()) photosSection else """
                  <div class="words-bar no-break" style="padding:12px;">
                    <b>Site Visual Survey Notes:</b> ${escapeHtml(dimensions.shadingNotes)} • Roof ${dimensions.roofLengthM}m × ${dimensions.roofWidthM}m cleared for ${breakdown.wiring.panelCount} Nos ${dimensions.panelWattageWp}Wp modules.
                  </div>
                  """}
                  <table class="grid no-break" style="margin-top:8px;">
                    <thead>
                      <tr><th colspan="5">Side-by-Side System Comparison (${breakdown.capacityKw} kW • ${escapeHtml(breakdown.structureType.shortLabel)})</th></tr>
                      <tr>
                        <th style="width:30%;">System Architecture</th>
                        <th class="num" style="width:18%;">Gross Price</th>
                        <th class="num" style="width:17%;">Govt. Subsidy</th>
                        <th class="num" style="width:20%;">Final Net Cost</th>
                        <th class="num" style="width:15%;">Payback</th>
                      </tr>
                    </thead>
                    <tbody>$matrixRows</tbody>
                  </table>
                </div>

                <!-- PAGE 4: BOM, WARRANTY, INTERNAL AUDIT & SIGNATORY -->
                <div class="sheet page-break" style="margin-top:10px;">
                  ${letterheadHtml("BOM, BANK & APPROVAL", "PAGE 4 OF 4", logoUri)}
                  <table class="grid no-break">
                    <thead>
                      <tr><th colspan="4">Approved Make Bill of Materials (MNRE &amp; BIS Certified)</th></tr>
                      <tr><th style="width:24%;">Component</th><th style="width:38%;">Technical Specification</th><th style="width:22%;">Approved Make</th><th style="width:16%;">Warranty</th></tr>
                    </thead>
                    <tbody>
                      <tr><td><b>Solar PV Modules</b></td><td>${breakdown.wiring.panelCount} × ${dimensions.panelWattageWp}Wp DCR Mono PERC / TOPCon</td><td>Waaree / Adani / Vikram</td><td>25 Years</td></tr>
                      <tr><td><b>Solar Inverter</b></td><td>${breakdown.capacityKw} kW ${escapeHtml(breakdown.systemType.shortLabel)} MPPT</td><td>Sungrow / Growatt / Havells</td><td>5–10 Years</td></tr>
                      <tr><td><b>Mounting Structure</b></td><td>Hot-Dip Galvanized Iron (${breakdown.structureType.shortLabel})</td><td>NB Fabrication Shed-1</td><td>25 Years</td></tr>
                      <tr><td><b>BOS &amp; Safety</b></td><td>DC/AC Cables, SPD, MCB, 3-Pit Chemical Earthing &amp; LA</td><td>Polycab / Havells / L&amp;T</td><td>5 Years</td></tr>
                    </tbody>
                  </table>
                  $internalCostingAppendix
                  ${bankAndTermsFooterHtml()}
                </div>
                """.trimIndent()
            }

            ProposalFormat.COMMERCIAL_TENDER_BOQ -> {
                val taxable = breakdown.taxableQuotedPriceInr
                val gstHalf = breakdown.gstAmountInr / 2.0
                val netBeforeRound = taxable + breakdown.gstAmountInr
                val grandRounded = netBeforeRound.roundToLong().toDouble()
                val roundOff = grandRounded - netBeforeRound
                val boqItems = listOf(
                    Triple("85414300", "${breakdown.wiring.panelCount} Nos × ${dimensions.panelWattageWp}Wp DCR Solar PV Modules (MNRE Approved)", 0.48),
                    Triple("85044090", "${breakdown.capacityKw} kW ${breakdown.systemType.label} Grid-Tie / Hybrid Inverter", 0.19),
                    Triple("73089090", "Hot-Dip GI Mounting Structure (${breakdown.structureType.label}, ~${breakdown.wiring.estimatedStructureKg.roundToInt()} kg)", 0.14),
                    Triple("85444999", "DC Cable ${breakdown.wiring.dcCableMeters}m + AC Armoured Cable ${breakdown.wiring.acCableMeters}m + PVC Conduit ${breakdown.wiring.pvcConduitMeters}m", 0.08),
                    Triple("85354090", "ACDB, DCDB, Surge Protection & 3-Pit Chemical Earthing (${breakdown.wiring.earthingWireMeters}m) + LA", 0.05),
                    Triple("995461", "Turnkey EPC Installation, Commissioning, AVVNL Net Metering & CEIG Coordination", 0.06)
                )
                val boqRows = boqItems.mapIndexed { idx, (hsn, desc, share) ->
                    val lineTaxable = taxable * share
                    """
                    <tr>
                      <td class="center">${idx + 1}</td>
                      <td>${escapeHtml(desc)}</td>
                      <td class="center">$hsn</td>
                      <td class="num">1.00</td>
                      <td class="center">Lot</td>
                      <td class="num">${formatInr(lineTaxable, true)}</td>
                      <td class="num">${formatInr(lineTaxable, true)}</td>
                    </tr>
                    """.trimIndent()
                }.joinToString("\n")
                """
                <div class="sheet">
                  ${letterheadHtml("EPC TENDER BOQ", "COMMERCIAL / WIND / INDUSTRIAL", logoUri)}
                  $customerMetaTable
                  <table class="grid">
                    <thead>
                      <tr>
                        <th class="center" style="width:6%;">S.N.</th>
                        <th style="width:42%;">Description of Goods &amp; Turnkey EPC Scope</th>
                        <th class="center" style="width:12%;">HSN/SAC</th>
                        <th class="num" style="width:7%;">Qty.</th>
                        <th class="center" style="width:7%;">Unit</th>
                        <th class="num" style="width:13%;">Rate (₹)</th>
                        <th class="num" style="width:13%;">Amount (₹)</th>
                      </tr>
                    </thead>
                    <tbody>
                      $boqRows
                      <tr><td colspan="6" class="num"><b>Total Taxable Schedule Value</b></td><td class="num">${formatInr(taxable, true)}</td></tr>
                      <tr><td colspan="6" class="num">Add: CGST (Composite Solar Rate)</td><td class="num">${formatInr(gstHalf, true)}</td></tr>
                      <tr><td colspan="6" class="num">Add: SGST (Composite Solar Rate)</td><td class="num">${formatInr(gstHalf, true)}</td></tr>
                      <tr><td colspan="6" class="num">Rounded Off (+/-)</td><td class="num">${String.format(Locale.US, "%+.2f", roundOff)}</td></tr>
                      <tr class="grand-row"><td colspan="6" class="num">Grand Tender Total (Incl. GST)</td><td class="num">${formatInr(grandRounded, true)}</td></tr>
                    </tbody>
                  </table>
                  <div class="words-bar">
                    <b>Amount Chargeable (in words):</b> ${escapeHtml(amountInWords(grandRounded))}
                  </div>
                  <table class="grid no-break" style="margin-top:6px;">
                    <thead>
                      <tr><th colspan="3">Commercial Payment Milestones &amp; Delivery Terms</th></tr>
                      <tr><th style="width:20%;">Milestone</th><th style="width:58%;">Payment Stage Description</th><th class="num" style="width:22%;">Share &amp; Amount</th></tr>
                    </thead>
                    <tbody>
                      <tr class="milestone-box"><td><b>Stage 1 (30%)</b></td><td>Advance along with Confirmed Purchase Order / Work Order</td><td class="num"><b>30% • ${formatInr(grandRounded * 0.30)}</b></td></tr>
                      <tr class="milestone-box"><td><b>Stage 2 (60%)</b></td><td>Against Proforma / E-Way Bill Dispatch of Modules, Inverter &amp; Structure</td><td class="num"><b>60% • ${formatInr(grandRounded * 0.60)}</b></td></tr>
                      <tr class="milestone-box"><td><b>Stage 3 (10%)</b></td><td>Upon Successful Commissioning, Synchronisation &amp; Net Metering</td><td class="num"><b>10% • ${formatInr(grandRounded * 0.10)}</b></td></tr>
                    </tbody>
                  </table>
                  $internalCostingAppendix
                  ${bankAndTermsFooterHtml()}
                </div>
                """.trimIndent()
            }

            ProposalFormat.WHATSAPP_QUICK_QUOTE_1PG -> """
              <div class="sheet">
                ${letterheadHtml("INSTANT SOLAR QUOTE", "1-PAGE EXPRESS", logoUri)}
                $customerMetaTable
                <div style="background:#F1F8E9;border-bottom:1.5px solid #7CB342;padding:12px 14px;" class="no-break">
                  <div style="display:flex;justify-content:space-between;align-items:center;">
                    <div>
                      <div style="font-size:10px;font-weight:800;color:#2B4D12;text-transform:uppercase;">"PARTY KO KITNE KA PADEGA" — FINAL NET OFFER</div>
                      <div style="font-size:22px;font-weight:800;color:#8E1E1B;margin-top:2px;">${formatInr(breakdown.finalNetCostAfterSubsidyInr)} <span style="font-size:11px;color:#2B4D12;">After PM Surya Ghar Subsidy</span></div>
                    </div>
                    <div style="text-align:right;">
                      <div><b>Gross Price (Incl. GST):</b> ${formatInr(breakdown.grossQuotedPriceInr)}</div>
                      <div style="color:#2B4D12;font-weight:700;"><b>Govt. DBT Subsidy:</b> - ${formatInr(breakdown.pmSuryaGharSubsidyInr)}</div>
                    </div>
                  </div>
                </div>
                <table class="grid no-break">
                  <thead>
                    <tr><th colspan="2">Turnkey Scope &amp; Performance Snapshot (${breakdown.capacityKw} kW ${escapeHtml(breakdown.systemType.shortLabel)})</th></tr>
                  </thead>
                  <tbody>
                    <tr><td style="width:45%;"><b>DCR Modules</b></td><td>${breakdown.wiring.panelCount} × ${dimensions.panelWattageWp}Wp Made-in-India DCR (25-Yr Warranty)</td></tr>
                    <tr><td><b>Mounting Structure</b></td><td>${escapeHtml(breakdown.structureType.label)} (HDGI ~${breakdown.wiring.estimatedStructureKg.roundToInt()} kg)</td></tr>
                    <tr><td><b>Wiring &amp; Earthing Included</b></td><td>DC ${breakdown.wiring.dcCableMeters}m • AC ${breakdown.wiring.acCableMeters}m • 3-Pit Earthing ${breakdown.wiring.earthingWireMeters}m • Conduit ${breakdown.wiring.pvcConduitMeters}m</td></tr>
                    <tr><td><b>Est. Generation &amp; Payback</b></td><td>~${breakdown.estimatedMonthlyUnits} Units/month • Savings ~${formatInr(breakdown.estimatedMonthlySavingsInr)}/mo • <b>Payback ~${breakdown.estimatedPaybackYears} Yrs</b></td></tr>
                  </tbody>
                </table>
                ${if (roofAfterUri != null) """
                <div style="padding:8px;text-align:center;border-top:1px solid #94A3B8;" class="no-break">
                  <div style="font-weight:700;color:#8E1E1B;margin-bottom:4px;">AI Rooftop Preview (${breakdown.wiring.panelCount} Panels)</div>
                  <img src="$roofAfterUri" style="max-width:75%;max-height:145px;border:1.5px solid #7CB342;border-radius:4px;" />
                </div>
                """ else ""}
                $internalCostingAppendix
                ${bankAndTermsFooterHtml()}
              </div>
            """.trimIndent()
        }

        return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="utf-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1.0" />
          <style>$SHARED_CSS</style>
        </head>
        <body>
          $bodyHtml
        </body>
        </html>
        """.trimIndent()
    }

    private fun buildSevenPageProposal2026Html(
        logoUri: String?,
        proposalRef: String,
        cleanName: String,
        cleanMob: String,
        cleanLoc: String,
        cleanKNo: String,
        breakdown: RealCostingBreakdown,
        dimensions: SiteSurveyDimensions,
        options: DetailedProposalOptions,
        photosSection: String,
        rooftopCadSvg: String,
        structureSvg: String,
        sldSvg: String,
        internalCostingAppendix: String
    ): String {
        val pricing = RealCostingCalculator.computeDetailedProposalSummary(breakdown, options)
        val capDisplay = if (options.capacityKwp % 1.0 == 0.0) options.capacityKwp.toInt().toString() else options.capacityKwp.toString()
        val refNo = options.proposalRefNo.ifBlank { proposalRef }
        val subsidyLabel = if (options.moduleType.isDcrSubsidy) {
            "National Portal Subsidy (PM Surya Ghar DBT)"
        } else {
            "National Portal Subsidy (N/A — Non-DCR Module)"
        }
        val subsidyValueDisplay = if (options.moduleType.isDcrSubsidy && pricing.subsidyAmountInr > 0.0) {
            "- ${formatInr(pricing.subsidyAmountInr, true)}"
        } else {
            "₹0 / N/A"
        }
        val extraRowsHtml = if (options.extraWorkRows.isEmpty()) {
            """
            <tr>
              <td class="center">2</td>
              <td>Additional / Extra Work (Load/kVA Difference, Elevated Structure, Net Meter Deposit)</td>
              <td class="center">As Applicable</td>
              <td class="num">₹0.00</td>
            </tr>
            """.trimIndent()
        } else {
            options.extraWorkRows.mapIndexed { idx, row ->
                """
                <tr>
                  <td class="center">${idx + 2}</td>
                  <td>Extra Work: ${escapeHtml(row.title)}</td>
                  <td class="center">1 Lot</td>
                  <td class="num">${formatInr(row.amountInr, true)}</td>
                </tr>
                """.trimIndent()
            }.joinToString("\n")
        }

        val annualUnits = (options.capacityKwp * 1440.0).roundToInt()
        val annualSavings = annualUnits * 8.0
        val twentyFiveYrSavings = annualSavings * 22.0
        val co2Tonnes = String.format(Locale.US, "%.1f", options.capacityKwp * 1.18 * 25.0)

        return """
        <!-- PAGE 1: EXECUTIVE COVER & PROJECT SNAPSHOT -->
        <div class="sheet page-after">
          ${letterheadHtml("PROJECT PROPOSAL – 2026", "PAGE 1 OF 7 • SITC", logoUri)}
          <div class="cover-hero" style="background:linear-gradient(135deg,#8E1E1B 0%,#5A110F 100%);color:#FFFFFF;padding:18px 18px;border-bottom:3px solid #7CB342;">
            <div style="font-size:10px;letter-spacing:1.2px;text-transform:uppercase;color:#DCEDC8;font-weight:800;">SOLAR EPC TURNKEY PROPOSAL • MNRE &amp; DISCOM COMPLIANT</div>
            <div style="font-size:20px;font-weight:800;margin-top:5px;">DETAILED SOLAR PROJECT PROPOSAL – 2026 (SITC)</div>
            <div style="font-size:12px;margin-top:4px;color:#F8FAFC;">
              Supply, Installation, Testing &amp; Commissioning of <b>$capDisplay KWp ${escapeHtml(breakdown.systemType.label)}</b> Grid-Connected Solar PV Power Plant
            </div>
            <div style="margin-top:8px;display:flex;gap:8px;flex-wrap:wrap;">
              <span style="background:rgba(255,255,255,0.16);border:1px solid rgba(255,255,255,0.35);padding:3px 8px;border-radius:4px;font-size:10px;font-weight:700;">Module Type: ${escapeHtml(options.moduleType.label)}</span>
              <span style="background:rgba(124,179,66,0.28);border:1px solid #DCEDC8;padding:3px 8px;border-radius:4px;font-size:10px;font-weight:700;">Panels: ${escapeHtml(options.panelMake)}</span>
              <span style="background:rgba(255,255,255,0.16);border:1px solid rgba(255,255,255,0.35);padding:3px 8px;border-radius:4px;font-size:10px;font-weight:700;">Inverter: ${escapeHtml(options.inverterMake)}</span>
            </div>
          </div>

          <table class="grid no-break" style="margin-top:8px;">
            <thead>
              <tr><th colspan="2">Client &amp; Proposal Identification</th></tr>
            </thead>
            <tbody>
              <tr>
                <td style="width:54%;">
                  <b>Prepared For:</b> ${escapeHtml(cleanName)}<br/>
                  <b>Site Address:</b> ${escapeHtml(cleanLoc)}<br/>
                  <b>Contact Mobile:</b> ${escapeHtml(cleanMob)}<br/>
                  <b>DISCOM / AVVNL K-No.:</b> ${escapeHtml(cleanKNo)}
                </td>
                <td style="width:46%;">
                  <b>Proposal Ref. No.:</b> ${escapeHtml(refNo)}<br/>
                  <b>Proposal Date:</b> ${todayIsoDate()}<br/>
                  <b>Offer Validity:</b> ${options.validityDays} Days from Date of Issue<br/>
                  <b>Mounting Structure:</b> ${escapeHtml(breakdown.structureType.label)}
                </td>
              </tr>
            </tbody>
          </table>

          <div class="kpi-row" style="margin-top:8px;">
            <div class="kpi-box"><div class="kpi-label">Plant Capacity</div><div class="kpi-val">$capDisplay KWp</div></div>
            <div class="kpi-box"><div class="kpi-label">Base + Extra Work</div><div class="kpi-val">${formatInr(pricing.grossTotalWithExtrasInr)}</div></div>
            <div class="kpi-box" style="border-color:#7CB342;background:#F1F8E9;"><div class="kpi-label">Portal Subsidy</div><div class="kpi-val" style="color:#2B4D12;">$subsidyValueDisplay</div></div>
            <div class="kpi-box" style="border-color:#8E1E1B;background:#FFF8F7;"><div class="kpi-label">Net Effective Cost</div><div class="kpi-val" style="color:#8E1E1B;">${formatInr(pricing.netEffectiveCostInr)}</div></div>
          </div>

          <table class="grid no-break" style="margin-top:8px;">
            <thead>
              <tr><th colspan="4">Executive Summary — Performance &amp; Commercial Highlights</th></tr>
            </thead>
            <tbody>
              <tr>
                <td><b>Module Category</b><br/>${escapeHtml(options.moduleType.label)}</td>
                <td><b>Approved Panel Make</b><br/>${escapeHtml(options.panelMake)}</td>
                <td><b>Approved Inverter Make</b><br/>${escapeHtml(options.inverterMake)}</td>
                <td><b>Est. Generation / Payback</b><br/>~$annualUnits Units/Yr • ~${pricing.paybackYears} Yrs</td>
              </tr>
              <tr>
                <td colspan="4"><b>Payment Terms:</b> ${escapeHtml(options.paymentTerms)}</td>
              </tr>
            </tbody>
          </table>

          <div class="words-bar" style="margin-top:8px;">
            <b>Submitted By:</b> $COMPANY_NAME • $COMPANY_ADDRESS<br/>
            <b>GSTIN:</b> $COMPANY_GSTIN &nbsp;|&nbsp; <b>Tel:</b> $COMPANY_TEL_DISPLAY &nbsp;|&nbsp; <b>Email:</b> $COMPANY_EMAIL
          </div>
        </div>

        <!-- PAGE 2: ABOUT NB ENGINEERING, SOLAR ROI & SYSTEM ARCHITECTURE -->
        <div class="sheet page-after" style="margin-top:10px;">
          ${letterheadHtml("ABOUT & ARCHITECTURE", "PAGE 2 OF 7 • SITC", logoUri)}
          <table class="grid no-break">
            <thead>
              <tr><th colspan="2">About $COMPANY_NAME</th></tr>
            </thead>
            <tbody>
              <tr>
                <td style="width:60%;">
                  <b>Turnkey Solar &amp; Wind EPC Leader in Rajasthan:</b> Headquartered at Rama Phosphates Ltd. Plant, Shed No. 1, Udaipur - 313001, NB Engineering Works &amp; Electricals delivers engineered rooftop &amp; ground-mounted solar power plants compliant with MNRE, PM Surya Ghar Muft Bijli Yojana, and AVVNL/JVVNL/JDVVNL norms.<br/>
                  <b>In-House Fabrication &amp; Execution:</b> Dedicated Hot-Dip GI structural fabrication bay, certified electrical engineers, and end-to-end DISCOM net-metering &amp; portal liaison.
                </td>
                <td style="width:40%;background:#F8FAFC;">
                  <b>25-Year Financial &amp; Green Impact</b><br/>
                  • Annual Solar Yield: <b>~$annualUnits kWh/Yr</b><br/>
                  • Annual Bill Savings: <b>~${formatInr(annualSavings)}/Yr</b><br/>
                  • Est. Simple Payback: <b>~${pricing.paybackYears} Years</b><br/>
                  • 25-Yr Cumulative Savings: <b>~${formatInr(twentyFiveYrSavings)}</b><br/>
                  • CO₂ Offset (25 Yrs): <b>~$co2Tonnes Tonnes</b>
                </td>
              </tr>
            </tbody>
          </table>

          <table class="grid no-break" style="margin-top:8px;">
            <thead>
              <tr><th>System Architecture &amp; Single-Line Electrical Schematic (SLD)</th></tr>
            </thead>
            <tbody>
              <tr>
                <td class="center svg-box" style="padding:10px;">
                  $sldSvg
                  <div style="font-size:10px;color:#334155;margin-top:6px;">
                    <b>Power Flow:</b> ${breakdown.wiring.panelCount} × ${dimensions.panelWattageWp}Wp (${escapeHtml(options.panelMake)}) → DCDB (Type-II SPD + DC Fuse) → ${escapeHtml(options.inverterMake)} Grid-Tie Inverter ($capDisplay KWp) → ACDB (MCB/MCCB + AC SPD) → Bi-Directional Net Meter → AVVNL Grid + 3-Pit Chemical Earthing &amp; LA.
                  </div>
                </td>
              </tr>
            </tbody>
          </table>

          <table class="grid no-break" style="margin-top:8px;">
            <thead>
              <tr><th colspan="4">Key Engineering &amp; Safety Protections Built-In</th></tr>
            </thead>
            <tbody>
              <tr>
                <td><b>Anti-Islanding</b><br/>Automatic grid disconnect during utility outage per IEEE 1547.</td>
                <td><b>Surge &amp; Lightning</b><br/>Dual AC/DC Type-II SPD + dedicated roof Lightning Arrester.</td>
                <td><b>3-Pit Chemical Earthing</b><br/>Separate DC, AC &amp; LA copper-bonded earth pits (&lt; 2 Ohm).</td>
                <td><b>Smart Monitoring</b><br/>Mobile Wi-Fi / GPRS app for real-time kWh generation tracking.</td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- PAGE 3: SITE ENGINEERING LAYOUT, 3D STRUCTURE & WIRING QUANTITIES -->
        <div class="sheet page-after" style="margin-top:10px;">
          ${letterheadHtml("3D LAYOUT & SURVEY", "PAGE 3 OF 7 • SITC", logoUri)}
          <table class="grid no-break">
            <thead>
              <tr>
                <th style="width:50%;">Top-Down Rooftop CAD Layout (${dimensions.roofLengthM}m × ${dimensions.roofWidthM}m)</th>
                <th style="width:50%;">3D Mounting Structure (${escapeHtml(breakdown.structureType.label)})</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td class="center svg-box">$rooftopCadSvg</td>
                <td class="center svg-box">
                  $structureSvg
                  <div style="font-size:9.5px;color:#334155;margin-top:4px;">
                    Tilt: ${dimensions.tiltDegrees.roundToInt()}° | Azimuth: ${dimensions.azimuthDegrees.roundToInt()}° | Clearance: ${breakdown.structureType.clearanceMeters}m | HDGI ~${breakdown.wiring.estimatedStructureKg.roundToInt()} kg
                  </div>
                </td>
              </tr>
            </tbody>
          </table>

          <table class="grid no-break" style="margin-top:8px;">
            <thead>
              <tr><th colspan="4">Measured Site Survey &amp; Balance of System (BOS) Quantities</th></tr>
            </thead>
            <tbody>
              <tr>
                <td><b>Total Modules</b><br/>${breakdown.wiring.panelCount} Nos × ${dimensions.panelWattageWp}Wp</td>
                <td><b>Roof Area vs Required</b><br/>${breakdown.wiring.roofAreaSqM} m² (Req: ${breakdown.wiring.requiredPanelAreaSqM} m²)</td>
                <td><b>Solar DC Cable (4 sq.mm UV)</b><br/>${breakdown.wiring.dcCableMeters} Meters</td>
                <td><b>Armoured AC Cable</b><br/>${breakdown.wiring.acCableMeters} Meters</td>
              </tr>
              <tr>
                <td><b>3-Pit Earthing Strip/Wire</b><br/>${breakdown.wiring.earthingWireMeters} Meters</td>
                <td><b>Heavy UV PVC Conduit</b><br/>${breakdown.wiring.pvcConduitMeters} Meters</td>
                <td><b>HDGI Steel Structure</b><br/>~${breakdown.wiring.estimatedStructureKg.roundToInt()} kg (IS 2062)</td>
                <td><b>Site Shading Notes</b><br/>${escapeHtml(dimensions.shadingNotes)}</td>
              </tr>
            </tbody>
          </table>

          ${if (photosSection.isNotBlank()) photosSection else """
          <div class="words-bar no-break" style="margin-top:8px;padding:10px;">
            <b>Site Survey Inspection:</b> Unobstructed south-oriented roof verified for ${breakdown.wiring.panelCount} solar modules (${escapeHtml(options.panelMake)}) with ${breakdown.structureType.label} mounting and safe inverter wall routing (${dimensions.inverterDistanceM}m run).
          </div>
          """}
        </div>

        <!-- PAGE 4: DETAILED BILL OF MATERIALS (BOM) & TECHNICAL SPECIFICATIONS -->
        <div class="sheet page-after" style="margin-top:10px;">
          ${letterheadHtml("TECHNICAL BOM (2026)", "PAGE 4 OF 7 • SITC", logoUri)}
          <table class="grid no-break">
            <thead>
              <tr><th colspan="6">Standard SITC Bill of Materials &amp; Approved Makes (${escapeHtml(options.moduleType.label)})</th></tr>
              <tr>
                <th class="center" style="width:6%;">S.N.</th>
                <th style="width:20%;">Equipment / Item</th>
                <th style="width:36%;">Technical Specification &amp; Standards</th>
                <th style="width:18%;">Approved Make</th>
                <th style="width:10%;">Qty</th>
                <th style="width:10%;">Warranty</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td class="center">1</td>
                <td><b>Solar PV Modules</b></td>
                <td>${dimensions.panelWattageWp}Wp Mono PERC / TOPCon Half-Cut (${escapeHtml(options.moduleType.label)}), IEC 61215, 61730 &amp; BIS Approved</td>
                <td><b>${escapeHtml(options.panelMake)}</b></td>
                <td>${breakdown.wiring.panelCount} Nos</td>
                <td>12 Yr Prod / 25 Yr Perf</td>
              </tr>
              <tr>
                <td class="center">2</td>
                <td><b>Solar Inverter</b></td>
                <td>$capDisplay KWp ${escapeHtml(breakdown.systemType.shortLabel)} MPPT Inverter, IP65 Outdoor, Anti-Islanding, Built-in Wi-Fi Logger</td>
                <td><b>${escapeHtml(options.inverterMake)}</b></td>
                <td>1 Set</td>
                <td>5–10 Yrs OEM</td>
              </tr>
              <tr>
                <td class="center">3</td>
                <td><b>Mounting Structure</b></td>
                <td>Hot-Dip Galvanized Iron (HDGI ≥ 80 Micron, IS 2062), Wind-rated 150 km/h, SS-304 Nut-Bolts &amp; Anchor Fasteners (${escapeHtml(breakdown.structureType.shortLabel)})</td>
                <td>NB Shed-1 / Tata / Jindal</td>
                <td>1 Lot (~${breakdown.wiring.estimatedStructureKg.roundToInt()} kg)</td>
                <td>25 Yrs Anti-Rust</td>
              </tr>
              <tr>
                <td class="center">4</td>
                <td><b>DCDB &amp; DC Cables</b></td>
                <td>IP65 DC Distribution Box with Type-II DC SPD &amp; Fuse; TUV-approved 4 sq.mm UV-protected Tinned Copper Solar Cable + MC4</td>
                <td>Polycab / Siechem / Elmex</td>
                <td>1 Set + ${breakdown.wiring.dcCableMeters}m</td>
                <td>5 Yrs</td>
              </tr>
              <tr>
                <td class="center">5</td>
                <td><b>ACDB &amp; AC Cables</b></td>
                <td>IP65 AC Distribution Box with 4P MCB/MCCB &amp; Type-II AC SPD; Heavy-Duty Armoured Cable &amp; UV PVC Conduit</td>
                <td>Havells / L&amp;T / Polycab</td>
                <td>1 Set + ${breakdown.wiring.acCableMeters}m</td>
                <td>5 Yrs</td>
              </tr>
              <tr>
                <td class="center">6</td>
                <td><b>Earthing &amp; LA</b></td>
                <td>3 Nos Chemical Copper-Bonded Earthing Pits with Backfill Compound + 1 No Roof Lightning Arrester &amp; GI/Cu Strip</td>
                <td>Ashlok / TruePower / ISI</td>
                <td>3 Pits + 1 LA</td>
                <td>5 Yrs</td>
              </tr>
              <tr>
                <td class="center">7</td>
                <td><b>SITC &amp; Net Metering</b></td>
                <td>Complete Mechanical Erection, Electrical Termination, Testing, Commissioning, DISCOM Feasibility &amp; Portal Liaison</td>
                <td>NB Engineering Team</td>
                <td>1 Turnkey Job</td>
                <td>1 Yr Workmanship</td>
              </tr>
            </tbody>
          </table>

          <div class="words-bar no-break" style="margin-top:8px;">
            <b>Quality &amp; Compliance Assurance:</b> All supplied modules carry valid serial barcodes &amp; MNRE ALMM / DCR test certificates (where DCR selected) for seamless National Portal verification.
          </div>
        </div>

        <!-- PAGE 5: COMMERCIAL PRICE SCHEDULE, EXTRA WORK, SUBSIDY & PAYMENT TERMS -->
        <div class="sheet page-after" style="margin-top:10px;">
          ${letterheadHtml("COMMERCIAL OFFER (2026)", "PAGE 5 OF 7 • SITC", logoUri)}
          <table class="grid no-break">
            <thead>
              <tr><th colspan="4">Commercial Price Schedule — $capDisplay KWp (${escapeHtml(options.moduleType.label)})</th></tr>
              <tr>
                <th class="center" style="width:7%;">S.N.</th>
                <th style="width:55%;">Scope of Supply, Installation, Testing &amp; Commissioning (SITC)</th>
                <th class="center" style="width:15%;">Basis</th>
                <th class="num" style="width:23%;">Amount (₹)</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td class="center">1</td>
                <td>
                  <b>Base Solar System Package ($capDisplay KWp • ${escapeHtml(options.moduleType.label)})</b><br/>
                  <small style="color:#475569;">Panels: ${escapeHtml(options.panelMake)} | Inverter: ${escapeHtml(options.inverterMake)} | Structure: ${escapeHtml(breakdown.structureType.shortLabel)} | Incl. Standard GST &amp; BOS</small>
                </td>
                <td class="center">Turnkey SITC</td>
                <td class="num"><b>${formatInr(pricing.baseSystemPriceInr, true)}</b></td>
              </tr>
              $extraRowsHtml
              <tr>
                <td colspan="3" class="num"><b>Gross Total Project Value (Base System + Extra Work, Incl. GST)</b></td>
                <td class="num dr">${formatInr(pricing.grossTotalWithExtrasInr, true)}</td>
              </tr>
              <tr>
                <td colspan="3" class="num" style="color:#2B4D12;"><b>(-) $subsidyLabel</b></td>
                <td class="num" style="color:#2B4D12;font-weight:800;">$subsidyValueDisplay</td>
              </tr>
              <tr class="grand-row">
                <td colspan="3" class="num">FINAL EFFECTIVE NET COST TO CUSTOMER</td>
                <td class="num">${formatInr(pricing.netEffectiveCostInr, true)}</td>
              </tr>
            </tbody>
          </table>

          <div class="words-bar no-break">
            <b>Net Effective Cost (in words):</b> ${escapeHtml(amountInWords(pricing.netEffectiveCostInr))}
          </div>

          <table class="grid no-break" style="margin-top:8px;">
            <thead>
              <tr><th colspan="2">Payment Terms &amp; Release Schedule</th></tr>
            </thead>
            <tbody>
              <tr>
                <td style="width:32%;"><b>Agreed Payment Terms</b></td>
                <td><b>${escapeHtml(options.paymentTerms)}</b></td>
              </tr>
              <tr>
                <td><b>Gross Contract Reference</b></td>
                <td>Gross Order Value: <b>${formatInr(pricing.grossTotalWithExtrasInr)}</b> (Subsidy of ${subsidyValueDisplay} is credited via DBT directly to Customer's Bank A/C for DCR orders)</td>
              </tr>
            </tbody>
          </table>

          $internalCostingAppendix
        </div>

        <!-- PAGE 6: EXECUTION TIMELINE, DISCOM WORKFLOW & SCOPE MATRIX -->
        <div class="sheet page-after" style="margin-top:10px;">
          ${letterheadHtml("TIMELINE & SCOPE MATRIX", "PAGE 6 OF 7 • SITC", logoUri)}
          <table class="grid no-break">
            <thead>
              <tr><th colspan="4">Project Execution Timeline &amp; PM Surya Ghar / DISCOM Roadmap</th></tr>
              <tr><th style="width:14%;">Phase</th><th style="width:26%;">Milestone</th><th style="width:42%;">Key Activities</th><th style="width:18%;">Est. Timeline</th></tr>
            </thead>
            <tbody>
              <tr><td><b>Phase 1</b></td><td>Order, Survey &amp; Feasibility</td><td>PO &amp; Advance receipt, final roof measurement, DISCOM / National Portal online application &amp; Technical Feasibility</td><td>Day 1 – Day 3</td></tr>
              <tr><td><b>Phase 2</b></td><td>Material Dispatch</td><td>Dispatch of ${escapeHtml(options.panelMake)} modules, ${escapeHtml(options.inverterMake)} inverter, HDGI structure &amp; BOS kits from Udaipur depot</td><td>Day 4 – Day 6</td></tr>
              <tr><td><b>Phase 3</b></td><td>SITC Execution</td><td>Civil anchoring, structure erection, module mounting, DC/AC cabling, ACDB/DCDB, 3-pit earthing &amp; LA commissioning</td><td>Day 7 – Day 10</td></tr>
              <tr><td><b>Phase 4</b></td><td>DISCOM Inspection &amp; Meter</td><td>CEIG/DISCOM site inspection, Bi-directional Net Meter testing, installation, MCO &amp; DCR certificate upload</td><td>Day 11 – Day 20</td></tr>
              <tr><td><b>Phase 5</b></td><td>Handover &amp; Subsidy DBT</td><td>App monitoring setup, handover kit &amp; ${if (options.moduleType.isDcrSubsidy) "DBT Subsidy credit into Client Bank A/C" else "Final plant performance sign-off"}</td><td>Day 21 – Day 30</td></tr>
            </tbody>
          </table>

          <table class="grid no-break" style="margin-top:8px;">
            <thead>
              <tr>
                <th style="width:50%;">NB Engineering Works Scope (Included)</th>
                <th style="width:50%;">Client Scope &amp; Pre-Requisites</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td>
                  • Complete supply of PV Modules, Inverter, HDGI Structure &amp; BOS<br/>
                  • Mechanical erection, chemical earthing &amp; lightning arrestor<br/>
                  • DC/AC electrical wiring, testing &amp; grid synchronization<br/>
                  • DISCOM / AVVNL net-metering documentation &amp; portal upload
                </td>
                <td>
                  • Unobstructed roof access, safe material storage &amp; electricity/water during installation<br/>
                  • Latest electricity bill (K-No.), Aadhaar, PAN &amp; cancelled cheque for portal/DBT<br/>
                  • Stable home/office Wi-Fi range near inverter for online monitoring<br/>
                  • Statutory DISCOM load enhancement / meter deposit charges if not in Extra Work
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- PAGE 7: COMMERCIAL TERMS & CONDITIONS, BANK DETAILS & ACCEPTANCE -->
        <div class="sheet" style="margin-top:10px;">
          ${letterheadHtml("TERMS & ACCEPTANCE", "PAGE 7 OF 7 • SITC", logoUri)}
          <table class="grid no-break">
            <thead>
              <tr><th>Commercial Terms &amp; Conditions (2026 SITC)</th></tr>
            </thead>
            <tbody>
              <tr>
                <td style="font-size:10px;line-height:1.45;">
                  <b>1. Validity:</b> This proposal is valid for <b>${options.validityDays} days</b> from the date of issue. Module/inverter prices are subject to revision if order is delayed beyond validity.<br/>
                  <b>2. Payment Terms:</b> ${escapeHtml(options.paymentTerms)}. Delayed payments beyond due milestones attract 18% p.a. interest.<br/>
                  <b>3. Taxes &amp; Duties:</b> Composite Solar GST is included in Base System Price as applicable on date of quotation; any statutory tax change by Govt. of India/Rajasthan shall be to Client's account.<br/>
                  <b>4. Subsidy Disclaimer:</b> Central subsidy (${subsidyValueDisplay}) under PM Surya Ghar applies strictly to eligible residential <b>DCR</b> installations and is disbursed directly by MNRE/Portal to Consumer Bank A/C per prevailing Govt. rules.<br/>
                  <b>5. Warranty:</b> PV Modules carry OEM 12-Yr Product &amp; 25-Yr Linear Power Warranty; Inverter carries 5–10 Yr OEM Warranty. Physical damage, grid surge beyond SPD rating, or unauthorized tampering voids warranty.<br/>
                  <b>6. Civil &amp; Waterproofing:</b> Standard chemical anchor / RCC grouting is included; specialized full-terrace waterproofing treatment, if desired, is in Client scope.<br/>
                  <b>7. Force Majeure &amp; Jurisdiction:</b> Delays due to DISCOM meter stock, weather, or Force Majeure are excluded from penalty. All disputes are subject to <b>Udaipur (Rajasthan)</b> jurisdiction only.
                </td>
              </tr>
            </tbody>
          </table>

          <table class="grid no-break" style="margin-top:8px;">
            <thead>
              <tr><th colspan="2">Company Bank Remittance &amp; Contact Details</th></tr>
            </thead>
            <tbody>
              <tr>
                <td style="width:55%;">
                  <b>Account Name:</b> $COMPANY_NAME<br/>
                  <b>Bank &amp; Branch:</b> $BANK_NAME • $BANK_BRANCH<br/>
                  <b>Account Number:</b> <b>$BANK_ACCOUNT_NO</b> &nbsp;|&nbsp; <b>IFSC:</b> <b>$BANK_IFSC</b>
                </td>
                <td style="width:45%;">
                  <b>GSTIN:</b> $COMPANY_GSTIN<br/>
                  <b>Tel / WhatsApp:</b> $COMPANY_TEL_DISPLAY<br/>
                  <b>Email:</b> $COMPANY_EMAIL
                </td>
              </tr>
            </tbody>
          </table>

          <div class="bottom-split" style="margin-top:8px;">
            <div class="bank-terms" style="width:50%;">
              <div class="section-title">Client Order Acceptance</div>
              <div style="font-size:9.5px;color:#334155;">
                We accept this 7-Page Detailed Solar Project Proposal (${escapeHtml(refNo)}) for <b>$capDisplay KWp</b> (${escapeHtml(options.moduleType.label)}) at Net Cost <b>${formatInr(pricing.netEffectiveCostInr)}</b>.
              </div>
              <div style="margin-top:28px;font-weight:600;border-top:1px solid #94A3B8;padding-top:4px;">
                Client Signature &amp; Date (${escapeHtml(cleanName)})
              </div>
            </div>
            <div class="sign-box" style="width:50%;">
              <div style="font-weight:700;color:#0F172A;">For $COMPANY_NAME</div>
              <div class="stamp-box">NB ENGINEERING WORKS<br/>UDAIPUR - 313001 • VERIFIED</div>
              <div style="font-weight:600;border-top:1px solid #94A3B8;padding-top:4px;">Authorized Signatory ($COMPANY_TEL_DISPLAY)</div>
            </div>
          </div>
        </div>
        """.trimIndent()
    }

    /**
     * Generates a genuine multi-page A4 PDF file in `cacheDir/exports/` via `android.graphics.pdf.PdfDocument`
     * for any of the 4 ProposalFormats and launches Android's Share Sheet (WhatsApp / Save PDF).
     */
    fun shareProposalPdfOnWhatsApp(
        context: Context,
        format: ProposalFormat,
        customerName: String,
        customerMobile: String,
        siteLocation: String,
        consumerNo: String,
        breakdown: RealCostingBreakdown,
        dimensions: SiteSurveyDimensions,
        rooftopAfterBitmap: Bitmap? = null,
        inverterAfterBitmap: Bitmap? = null,
        comparisonBreakdowns: List<RealCostingBreakdown> = emptyList(),
        rooftopBeforeBitmap: Bitmap? = null,
        inverterBeforeBitmap: Bitmap? = null,
        includeOwnerInternalCosting: Boolean = false,
        detailedOptions: DetailedProposalOptions = DetailedProposalOptions(
            capacityKwp = breakdown.capacityKw,
            baseSystemPriceInr = breakdown.grossQuotedPriceInr,
            subsidyAmountInr = breakdown.pmSuryaGharSubsidyInr
        )
    ): Result<String> {
        return try {
            val cleanName = customerName.ifBlank { "Valued Solar Consumer" }
            val slug = sanitizeFileSlug(cleanName, "Consumer")
            val fileName = "NB_Proposal_${format.name}_${slug}_${todayIsoDate()}.pdf"
            if (format == ProposalFormat.PM_SURYA_GHAR_2PG) {
                val pricing = RealCostingCalculator.computeDetailedProposalSummary(breakdown, detailedOptions)
                val outFile = writeSevenPageProposalPdf(
                    context = context,
                    fileName = fileName,
                    cleanName = cleanName,
                    customerMobile = customerMobile,
                    siteLocation = siteLocation,
                    consumerNo = consumerNo,
                    breakdown = breakdown,
                    dimensions = dimensions,
                    options = detailedOptions,
                    pricing = pricing,
                    rooftopBeforeBitmap = rooftopBeforeBitmap,
                    rooftopAfterBitmap = rooftopAfterBitmap,
                    inverterBeforeBitmap = inverterBeforeBitmap,
                    inverterAfterBitmap = inverterAfterBitmap,
                    includeOwnerInternalCosting = includeOwnerInternalCosting
                )
                val waText = buildString {
                    appendLine("*$COMPANY_NAME — Udaipur*")
                    appendLine("Detailed 7-Page Solar Project Proposal – 2026 (SITC) for *$cleanName*")
                    appendLine("System: ${detailedOptions.capacityKwp} KWp (${detailedOptions.moduleType.label})")
                    appendLine("Panel Make: ${detailedOptions.panelMake} | Inverter: ${detailedOptions.inverterMake}")
                    appendLine("Gross Project Price: ${formatInr(pricing.grossTotalWithExtrasInr)}")
                    if (detailedOptions.moduleType.isDcrSubsidy && pricing.subsidyAmountInr > 0) {
                        appendLine("National Portal Subsidy: - ${formatInr(pricing.subsidyAmountInr)}")
                    } else {
                        appendLine("National Portal Subsidy: ₹0 / N/A (Non-DCR)")
                    }
                    appendLine("*Net Effective Cost: ${formatInr(pricing.netEffectiveCostInr)}*")
                    appendLine("Payment Terms: ${detailedOptions.paymentTerms}")
                    appendLine("Contact: $COMPANY_TEL_DISPLAY | GSTIN: $COMPANY_GSTIN")
                }
                sharePdfFile(context, outFile, "Share 7-Page Proposal PDF via WhatsApp", "Solar Project Proposal 2026 - $cleanName ($COMPANY_NAME)", waText)
                return Result.success(fileName)
            }
            val pdf = PdfA4Writer(context, format.shortTitle.uppercase(Locale.US), "${breakdown.capacityKw} kW ${breakdown.systemType.shortLabel}")

            pdf.drawMetaBox(
                listOf(
                    "Prepared For: $cleanName   |   Mobile: ${customerMobile.ifBlank { "-" }}   |   K-No: ${consumerNo.ifBlank { "-" }}",
                    "Site: ${siteLocation.ifBlank { "Udaipur, Rajasthan" }}   |   Date: ${todayIsoDate()}   |   Structure: ${breakdown.structureType.label}"
                )
            )

            // Prominent "Party Ko Kitne Ka Padega" Highlight Box
            pdf.drawHighlightBox(
                heading = "CUSTOMER NET COST SUMMARY ('PARTY KO KITNE KA PADEGA')",
                subLine = "Gross Price (Incl. GST): ${formatInr(breakdown.grossQuotedPriceInr)}   |   PM Surya Ghar Subsidy: - ${formatInr(breakdown.pmSuryaGharSubsidyInr)}",
                bigValue = "Final Net Cost After Subsidy: ${formatInr(breakdown.finalNetCostAfterSubsidyInr)}"
            )

            // Format-specific Comparison Matrix (for Full 3D Dossier & Commercial Tender BOQ)
            if ((format == ProposalFormat.FULL_3D_DOSSIER_4PG || format == ProposalFormat.COMMERCIAL_TENDER_BOQ) &&
                comparisonBreakdowns.isNotEmpty()
            ) {
                pdf.drawSectionHeading("SYSTEM CAPACITY OPTIONS & SUBSIDY COMPARISON")
                val cols = listOf(0.18f, 0.20f, 0.22f, 0.20f, 0.20f)
                pdf.drawTableHeader(listOf("Capacity", "Solar PV", "Gross Price", "Subsidy", "Net Cost"), cols)
                comparisonBreakdowns.forEach { opt ->
                    pdf.drawTableRow(
                        cells = listOf(
                            "${opt.capacityKw} kW",
                            "${opt.wiring.panelCount} Panels",
                            formatInr(opt.grossQuotedPriceInr),
                            "- ${formatInr(opt.pmSuryaGharSubsidyInr)}",
                            formatInr(opt.finalNetCostAfterSubsidyInr)
                        ),
                        fractions = cols,
                        boldLast = true
                    )
                }
                pdf.addSpacing(8f)
            }

            // Survey, Wiring & Technical Specifications Table
            pdf.drawSectionHeading("SURVEY, WIRING & SYSTEM SPECIFICATIONS")
            val specCols = listOf(0.32f, 0.68f)
            pdf.drawTableHeader(listOf("Specification", "Details"), specCols)
            val specRows = listOf(
                "Solar PV Modules" to "${breakdown.wiring.panelCount} Nos x ${dimensions.panelWattageWp}Wp Made-in-India DCR (25-Yr Warranty)",
                "System Architecture" to "${breakdown.capacityKw} kW ${breakdown.systemType.label} (${breakdown.structureType.label})",
                "Rooftop Dimensions" to "${dimensions.roofLengthM}m x ${dimensions.roofWidthM}m (${breakdown.wiring.roofAreaSqM} m2) | Tilt ${dimensions.tiltDegrees.roundToInt()} deg",
                "Solar DC Cable (4 sq.mm)" to "${breakdown.wiring.dcCableMeters} meters UV-protected Copper",
                "AC Cable & PVC Conduit" to "AC Armoured ${breakdown.wiring.acCableMeters} m | Heavy PVC Conduit ${breakdown.wiring.pvcConduitMeters} m",
                "Earthing & HDGI Structure" to "3-Pit Chemical Earthing ${breakdown.wiring.earthingWireMeters} m | HDGI ~${breakdown.wiring.estimatedStructureKg.roundToInt()} kg",
                "Generation & Payback" to "~${breakdown.estimatedMonthlyUnits} Units/mo | Savings ~${formatInr(breakdown.estimatedMonthlySavingsInr)}/mo (~${breakdown.estimatedPaybackYears} Yrs)"
            )
            specRows.forEach { (k, v) ->
                pdf.drawTableRow(listOf(k, v), specCols)
            }

            // Tender / Technical Quote Commercial BoQ + Payment Milestones
            if (format == ProposalFormat.COMMERCIAL_TENDER_BOQ || format == ProposalFormat.PM_SURYA_GHAR_2PG) {
                pdf.addSpacing(8f)
                pdf.drawSectionHeading("COMMERCIAL BREAKUP & PAYMENT MILESTONES")
                val boqCols = listOf(0.46f, 0.18f, 0.16f, 0.20f)
                pdf.drawTableHeader(listOf("Description", "Taxable", "GST", "Total (INR)"), boqCols)
                val gstTotal = breakdown.gstAmountInr
                pdf.drawTableRow(
                    listOf(
                        "${breakdown.capacityKw} kW Complete Turnkey Solar EPC",
                        formatInr(breakdown.taxableQuotedPriceInr),
                        formatInr(gstTotal),
                        formatInr(breakdown.grossQuotedPriceInr)
                    ),
                    boqCols,
                    boldLast = true
                )
                if (format == ProposalFormat.COMMERCIAL_TENDER_BOQ) {
                    val grand = breakdown.grossQuotedPriceInr.roundToLong().toDouble()
                    pdf.drawTableRow(listOf("Milestone 1 (30% Advance PO)", "-", "-", formatInr(grand * 0.30)), boqCols)
                    pdf.drawTableRow(listOf("Milestone 2 (60% On Dispatch)", "-", "-", formatInr(grand * 0.60)), boqCols)
                    pdf.drawTableRow(listOf("Milestone 3 (10% Commissioning)", "-", "-", formatInr(grand * 0.10)), boqCols)
                }
            }

            // Optional Owner Internal Costing Breakdown (Strictly Hidden Unless Enabled by Owner)
            if (includeOwnerInternalCosting) {
                pdf.addSpacing(8f)
                pdf.drawSectionHeading("OWNER INTERNAL EPC REAL COSTING (CONFIDENTIAL)")
                val costCols = listOf(0.40f, 0.35f, 0.25f)
                pdf.drawTableHeader(listOf("Cost Head", "Purchase / Qty Basis", "Landing Cost"), costCols)
                pdf.drawTableRow(listOf("DCR Panels + Inverter + BOS", "${breakdown.wiring.panelCount} Panels + ${breakdown.capacityKw} kW", formatInr(breakdown.totalLandingCostInr)), costCols)
                pdf.drawTableRow(listOf("Expected Gross Profit Margin", "${String.format(Locale.US, "%.1f", breakdown.profitMarginPct)}% on Taxable", formatInr(breakdown.profitMarginInr)), costCols, boldLast = true)
            }

            pdf.addSpacing(8f)
            pdf.drawWordsBar("Amount Chargeable (in words): ${amountInWords(breakdown.finalNetCostAfterSubsidyInr)}")
            pdf.drawBankAndTermsFooter()

            // Optional Page 2: AI Rooftop & Inverter Placement Photos
            val photos = listOfNotNull(
                rooftopBeforeBitmap?.let { "Rooftop Site (Before Solar)" to it },
                rooftopAfterBitmap?.let { "AI Placed Rooftop Array (${breakdown.wiring.panelCount} Panels • ${breakdown.structureType.shortLabel})" to it },
                inverterBeforeBitmap?.let { "Inverter Wall (Before)" to it },
                inverterAfterBitmap?.let { "AI Placed Inverter, ACDB/DCDB & Conduit" to it }
            )
            if (photos.isNotEmpty()) {
                pdf.startNewPage("AI SITE PHOTO PLACEMENT — $cleanName")
                photos.forEach { (caption, bmp) ->
                    pdf.drawCaptionedBitmap(caption, bmp, maxHeightPt = 260f)
                }
                pdf.drawBankAndTermsFooter()
            }

            val outFile = pdf.writeToExportsFile(fileName)
            val waText = buildString {
                appendLine("*$COMPANY_NAME — Udaipur*")
                appendLine("Solar EPC Proposal (${format.shortTitle}) for *$cleanName* (${breakdown.capacityKw} kW ${breakdown.systemType.shortLabel})")
                appendLine("Gross Price (Incl. GST): ${formatInr(breakdown.grossQuotedPriceInr)}")
                if (breakdown.pmSuryaGharSubsidyInr > 0) {
                    appendLine("PM Surya Ghar Subsidy: - ${formatInr(breakdown.pmSuryaGharSubsidyInr)}")
                }
                appendLine("*Net Cost After Subsidy: ${formatInr(breakdown.finalNetCostAfterSubsidyInr)}*")
                appendLine("Est. Generation: ~${breakdown.estimatedMonthlyUnits} Units/mo | Payback ~${breakdown.estimatedPaybackYears} Yrs")
                appendLine("Contact: $COMPANY_TEL | GSTIN: $COMPANY_GSTIN")
            }
            sharePdfFile(context, outFile, "Share Proposal PDF via WhatsApp", "Solar EPC Proposal - $cleanName ($COMPANY_NAME)", waText)
            Result.success(fileName)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun writeSevenPageProposalPdf(
        context: Context,
        fileName: String,
        cleanName: String,
        customerMobile: String,
        siteLocation: String,
        consumerNo: String,
        breakdown: RealCostingBreakdown,
        dimensions: SiteSurveyDimensions,
        options: DetailedProposalOptions,
        pricing: com.example.data.model.DetailedProposalPricingSummary,
        rooftopBeforeBitmap: Bitmap?,
        rooftopAfterBitmap: Bitmap?,
        inverterBeforeBitmap: Bitmap?,
        inverterAfterBitmap: Bitmap?,
        includeOwnerInternalCosting: Boolean
    ): File {
        val capDisplay = if (options.capacityKwp % 1.0 == 0.0) options.capacityKwp.toInt().toString() else options.capacityKwp.toString()
        val refNo = options.proposalRefNo.ifBlank { "NB-Q/${todayIsoDate().replace("-", "")}/${capDisplay}KW" }
        val subsidyDisp = if (options.moduleType.isDcrSubsidy && pricing.subsidyAmountInr > 0.0) {
            "- ${formatInr(pricing.subsidyAmountInr)}"
        } else {
            "₹0 / N/A"
        }
        val annualUnits = (options.capacityKwp * 1440.0).roundToInt()
        val annualSavings = annualUnits * 8.0

        val pdf = PdfA4Writer(context, "SOLAR PROJECT PROPOSAL – 2026 (SITC)", "PAGE 1 OF 7")

        // PAGE 1: EXECUTIVE COVER & SNAPSHOT
        pdf.drawCoverHeroBanner(
            title = "DETAILED SOLAR PROJECT PROPOSAL – 2026 (SITC)",
            subtitle = "$capDisplay KWp ${breakdown.systemType.label} Grid-Connected Solar PV Power Plant",
            chipsLine = "${options.moduleType.label} | Panels: ${options.panelMake} | Inverter: ${options.inverterMake}"
        )
        pdf.drawMetaBox(
            listOf(
                "Prepared For: $cleanName   |   Mobile: ${customerMobile.ifBlank { "-" }}   |   K-No: ${consumerNo.ifBlank { "-" }}",
                "Site Address: ${siteLocation.ifBlank { "Udaipur, Rajasthan" }}   |   Ref: $refNo",
                "Proposal Date: ${todayIsoDate()}   |   Validity: ${options.validityDays} Days   |   Structure: ${breakdown.structureType.label}"
            )
        )
        pdf.drawHighlightBox(
            heading = "NET EFFECTIVE INVESTMENT SUMMARY (${options.moduleType.shortLabel})",
            subLine = "Base + Extra Work: ${formatInr(pricing.grossTotalWithExtrasInr)}   |   National Portal Subsidy: $subsidyDisp",
            bigValue = "Final Net Effective Cost: ${formatInr(pricing.netEffectiveCostInr)}"
        )
        val p1Cols = listOf(0.32f, 0.68f)
        pdf.drawTableHeader(listOf("Parameter", "Project Proposal Snapshot"), p1Cols)
        pdf.drawTableRow(listOf("Module Type & Panels", "${options.moduleType.label} — ${options.panelMake} (${breakdown.wiring.panelCount} × ${dimensions.panelWattageWp}Wp)"), p1Cols)
        pdf.drawTableRow(listOf("Inverter & Structure", "${options.inverterMake} ($capDisplay KWp) | ${breakdown.structureType.label}"), p1Cols)
        pdf.drawTableRow(listOf("Payment Terms", options.paymentTerms), p1Cols)
        pdf.drawTableRow(listOf("Annual Yield & Payback", "~$annualUnits Units/Yr | Savings ~${formatInr(annualSavings)}/Yr | Payback ~${pricing.paybackYears} Yrs"), p1Cols, boldLast = true)

        // PAGE 2: ABOUT NB ENGINEERING & SYSTEM ARCHITECTURE (SLD)
        pdf.startNewPage("ABOUT NB ENGINEERING & SYSTEM ARCHITECTURE (PAGE 2 OF 7)", fullLetterhead = true)
        pdf.drawSectionHeading("ABOUT $COMPANY_NAME")
        pdf.drawBulletLines(
            listOf(
                "Headquartered at Rama Phosphates Ltd. Plant, Shed No. 1, Udaipur - 313001, Rajasthan (GSTIN: $COMPANY_GSTIN).",
                "Turnkey Solar & Wind EPC specialist for Residential, Commercial & Industrial grid-tied rooftop systems.",
                "In-house Hot-Dip GI fabrication, certified electrical commissioning & full DISCOM / PM Surya Ghar portal liaison.",
                "25-Yr Financial Impact: ~$annualUnits kWh/Yr | Annual Savings ~${formatInr(annualSavings)} | 25-Yr Est. Savings ~${formatInr(annualSavings * 22.0)}."
            )
        )
        pdf.drawSectionHeading("SINGLE-LINE ELECTRICAL ARCHITECTURE DIAGRAM (SLD)")
        pdf.drawSldCanvasDiagram(
            capacityKwp = options.capacityKwp,
            panelCount = breakdown.wiring.panelCount,
            inverterMake = options.inverterMake,
            earthingMeters = breakdown.wiring.earthingWireMeters
        )
        val p2Cols = listOf(0.28f, 0.72f)
        pdf.drawTableHeader(listOf("Safety Feature", "Engineering Protection Included"), p2Cols)
        pdf.drawTableRow(listOf("Anti-Islanding", "Automatic grid isolation during DISCOM outage per IEEE 1547 & CEA standards."), p2Cols)
        pdf.drawTableRow(listOf("Surge & Lightning", "Type-II AC/DC SPDs inside IP65 enclosures + roof Lightning Arrester."), p2Cols)
        pdf.drawTableRow(listOf("3-Pit Earthing", "Dedicated chemical copper-bonded earth pits for DC, AC & LA (< 2 Ohm)."), p2Cols)

        // PAGE 3: SITE ENGINEERING, WIRING QUANTITIES & AI PHOTOS
        pdf.startNewPage("SITE LAYOUT, 3D STRUCTURE & WIRING SURVEY (PAGE 3 OF 7)", fullLetterhead = true)
        pdf.drawSectionHeading("MEASURED ROOFTOP SURVEY & BALANCE OF SYSTEM (BOS)")
        val p3Cols = listOf(0.36f, 0.64f)
        pdf.drawTableHeader(listOf("Survey Parameter", "Measured Specification"), p3Cols)
        pdf.drawTableRow(listOf("Roof Dimensions & Area", "${dimensions.roofLengthM}m × ${dimensions.roofWidthM}m (${breakdown.wiring.roofAreaSqM} m² available vs ${breakdown.wiring.requiredPanelAreaSqM} m² req)"), p3Cols)
        pdf.drawTableRow(listOf("Mounting Structure", "${breakdown.structureType.label} | Tilt ${dimensions.tiltDegrees.roundToInt()}° | Clearance ${breakdown.structureType.clearanceMeters}m | HDGI ~${breakdown.wiring.estimatedStructureKg.roundToInt()} kg"), p3Cols)
        pdf.drawTableRow(listOf("Solar DC Cable (4 sq.mm)", "${breakdown.wiring.dcCableMeters} meters UV-protected Tinned Copper"), p3Cols)
        pdf.drawTableRow(listOf("Armoured AC Cable & Conduit", "AC ${breakdown.wiring.acCableMeters} m | Heavy UV PVC Conduit ${breakdown.wiring.pvcConduitMeters} m"), p3Cols)
        pdf.drawTableRow(listOf("3-Pit Chemical Earthing", "${breakdown.wiring.earthingWireMeters} m Cu/GI Earthing Strip + Lightning Arrester"), p3Cols)
        pdf.addSpacing(6f)
        val photos = listOfNotNull(
            rooftopAfterBitmap?.let { "AI Placed Rooftop Array (${breakdown.wiring.panelCount} Panels • ${breakdown.structureType.shortLabel})" to it },
            inverterAfterBitmap?.let { "AI Placed Inverter, ACDB/DCDB & Conduit" to it },
            rooftopBeforeBitmap?.let { "Rooftop Site (Before Solar)" to it },
            inverterBeforeBitmap?.let { "Inverter Wall (Before)" to it }
        ).take(2)
        if (photos.isNotEmpty()) {
            photos.forEach { (caption, bmp) ->
                pdf.drawCaptionedBitmap(caption, bmp, maxHeightPt = 210f)
            }
        } else {
            pdf.drawWordsBar("Site Inspection: ${dimensions.shadingNotes}")
        }

        // PAGE 4: DETAILED BILL OF MATERIALS (BOM) & TECHNICAL SPECS
        pdf.startNewPage("DETAILED BILL OF MATERIALS & TECHNICAL SPECS (PAGE 4 OF 7)", fullLetterhead = true)
        pdf.drawSectionHeading("SITC APPROVED MAKE BILL OF MATERIALS (2026)")
        val bomCols = listOf(0.08f, 0.24f, 0.34f, 0.20f, 0.14f)
        pdf.drawTableHeader(listOf("S.N.", "Component", "Specification", "Approved Make", "Warranty"), bomCols)
        pdf.drawTableRow(listOf("1", "Solar PV Modules", "${breakdown.wiring.panelCount} × ${dimensions.panelWattageWp}Wp (${options.moduleType.shortLabel})", options.panelMake, "12 / 25 Yrs"), bomCols)
        pdf.drawTableRow(listOf("2", "Solar Inverter", "$capDisplay KWp ${breakdown.systemType.shortLabel} MPPT Wi-Fi", options.inverterMake, "5–10 Yrs"), bomCols)
        pdf.drawTableRow(listOf("3", "HDGI Structure", "${breakdown.structureType.shortLabel} (~${breakdown.wiring.estimatedStructureKg.roundToInt()} kg, IS 2062)", "NB Shed-1 / Tata", "25 Yrs"), bomCols)
        pdf.drawTableRow(listOf("4", "DCDB & DC Cable", "Type-II DC SPD + 4 sq.mm (${breakdown.wiring.dcCableMeters}m)", "Polycab / Siechem", "5 Yrs"), bomCols)
        pdf.drawTableRow(listOf("5", "ACDB & AC Cable", "4P MCB + AC SPD + Armoured (${breakdown.wiring.acCableMeters}m)", "Havells / L&T", "5 Yrs"), bomCols)
        pdf.drawTableRow(listOf("6", "Earthing & LA", "3 Chemical Earth Pits (${breakdown.wiring.earthingWireMeters}m) + 1 LA", "Ashlok / ISI", "5 Yrs"), bomCols)
        pdf.drawTableRow(listOf("7", "SITC & Net Meter", "Erection, Commissioning & DISCOM Liaison", "NB Engineering", "1 Yr Work"), bomCols)
        pdf.addSpacing(8f)
        pdf.drawWordsBar("All modules supplied with MNRE ALMM / DCR test certificates & serial barcodes where applicable.")

        // PAGE 5: COMMERCIAL PRICE SCHEDULE, EXTRA WORK, SUBSIDY & PAYMENT TERMS
        pdf.startNewPage("COMMERCIAL PRICE SCHEDULE & PAYMENT TERMS (PAGE 5 OF 7)", fullLetterhead = true)
        pdf.drawSectionHeading("COMMERCIAL OFFER — $capDisplay KWp (${options.moduleType.label})")
        val commCols = listOf(0.08f, 0.52f, 0.16f, 0.24f)
        pdf.drawTableHeader(listOf("S.N.", "Scope / Description", "Basis", "Amount (INR)"), commCols)
        pdf.drawTableRow(
            listOf("1", "Base System ($capDisplay KWp ${options.moduleType.shortLabel} • ${options.panelMake} / ${options.inverterMake})", "Turnkey SITC", formatInr(pricing.baseSystemPriceInr, true)),
            commCols
        )
        if (options.extraWorkRows.isEmpty()) {
            pdf.drawTableRow(listOf("2", "Extra Work (Load/kVA, Elevated Structure, Net Meter Deposit)", "As Applicable", "₹0.00"), commCols)
        } else {
            options.extraWorkRows.forEachIndexed { idx, row ->
                pdf.drawTableRow(listOf("${idx + 2}", "Extra Work: ${row.title}", "1 Lot", formatInr(row.amountInr, true)), commCols)
            }
        }
        pdf.drawTableRow(listOf("", "Gross Project Total (Incl. GST)", "Subtotal", formatInr(pricing.grossTotalWithExtrasInr, true)), commCols, boldLast = true)
        pdf.drawTableRow(
            listOf("", if (options.moduleType.isDcrSubsidy) "(-) National Portal Subsidy (DBT)" else "(-) National Portal Subsidy (Non-DCR)", "Govt. DBT", subsidyDisp),
            commCols
        )
        pdf.drawTableRow(listOf("", "FINAL EFFECTIVE NET COST TO CUSTOMER", "Net Total", formatInr(pricing.netEffectiveCostInr, true)), commCols, boldLast = true, highlightRow = true)
        pdf.addSpacing(6f)
        pdf.drawWordsBar("In Words: ${amountInWords(pricing.netEffectiveCostInr)}")
        pdf.drawSectionHeading("PAYMENT TERMS & MILESTONES")
        pdf.drawMetaBox(listOf("Payment Terms: ${options.paymentTerms}"))
        if (includeOwnerInternalCosting) {
            pdf.drawSectionHeading("OWNER CONFIDENTIAL REAL COSTING AUDIT")
            pdf.drawMetaBox(
                listOf(
                    "Landing Cost: ${formatInr(breakdown.totalLandingCostInr)} | Profit Margin (${String.format(Locale.US, "%.1f", breakdown.profitMarginPct)}%): ${formatInr(breakdown.profitMarginInr)}"
                )
            )
        }

        // PAGE 6: EXECUTION TIMELINE & SCOPE RESPONSIBILITY MATRIX
        pdf.startNewPage("EXECUTION TIMELINE & SCOPE MATRIX (PAGE 6 OF 7)", fullLetterhead = true)
        pdf.drawSectionHeading("5-PHASE PROJECT EXECUTION TIMELINE")
        val tCols = listOf(0.14f, 0.26f, 0.42f, 0.18f)
        pdf.drawTableHeader(listOf("Phase", "Milestone", "Activities", "Timeline"), tCols)
        pdf.drawTableRow(listOf("Phase 1", "Order & Feasibility", "PO advance, survey & DISCOM portal feasibility", "Day 1 – 3"), tCols)
        pdf.drawTableRow(listOf("Phase 2", "Material Dispatch", "Dispatch of panels, inverter, HDGI & BOS from Udaipur", "Day 4 – 6"), tCols)
        pdf.drawTableRow(listOf("Phase 3", "SITC Execution", "Structure erection, cabling, earthing & commissioning", "Day 7 – 10"), tCols)
        pdf.drawTableRow(listOf("Phase 4", "DISCOM & Net Meter", "Inspection, Bi-directional meter, MCO & DCR upload", "Day 11 – 20"), tCols)
        pdf.drawTableRow(listOf("Phase 5", "Handover & DBT", "Monitoring app setup & subsidy DBT / sign-off", "Day 21 – 30"), tCols)
        pdf.addSpacing(8f)
        pdf.drawSectionHeading("SCOPE RESPONSIBILITY MATRIX")
        pdf.drawBulletLines(
            listOf(
                "NB Engineering Scope: Supply of Modules, Inverter, HDGI Structure, ACDB/DCDB, Cables, 3-Pit Earthing, LA, Erection & Net Metering.",
                "Client Scope: Roof access, storage space, electricity/water during erection, K-No. bill/KYC/cancelled cheque & Wi-Fi for monitoring."
            )
        )

        // PAGE 7: COMMERCIAL TERMS & CONDITIONS, BANK DETAILS & SIGNATURES
        pdf.startNewPage("COMMERCIAL TERMS, BANK DETAILS & ACCEPTANCE (PAGE 7 OF 7)", fullLetterhead = true)
        pdf.drawSectionHeading("COMMERCIAL TERMS & CONDITIONS (2026 SITC)")
        pdf.drawBulletLines(
            listOf(
                "1. Offer Validity: Valid for ${options.validityDays} days from issue date; prices subject to revision thereafter.",
                "2. Payment Terms: ${options.paymentTerms}. Overdue milestone balances attract 18% p.a. interest.",
                "3. Taxes & Duties: Composite Solar GST included in base price; any statutory rate revision is to Client's account.",
                "4. Subsidy: Central DBT subsidy ($subsidyDisp) applies only to eligible residential DCR plants per MNRE norms.",
                "5. Warranty: Modules 12-Yr Product / 25-Yr Power OEM Warranty; Inverter 5–10 Yrs OEM Warranty.",
                "6. Civil & Waterproofing: Standard chemical anchor grouting included; full-terrace waterproofing in Client scope.",
                "7. Jurisdiction: Force Majeure / DISCOM delays excluded. Subject to Udaipur (Rajasthan) jurisdiction only."
            )
        )
        pdf.drawDualSignatureFooter(cleanName, refNo, formatInr(pricing.netEffectiveCostInr))
        return pdf.writeToExportsFile(fileName)
    }

    /**
     * Generates a valid A4 Tax Invoice / Voucher `.pdf` file and launches WhatsApp / Android Share Sheet.
     */
    fun exportAndShareTaxInvoicePdf(context: Context, voucher: BusyVoucher): Result<String> {
        if (voucher.type == BusyVoucherType.RECEIPT) {
            return exportAndSharePaymentReceiptPdf(context, voucher)
        }
        return try {
            val docHeading = when (voucher.type) {
                BusyVoucherType.SALES -> "TAX INVOICE"
                BusyVoucherType.PURCHASE -> "PURCHASE VOUCHER"
                BusyVoucherType.PAYMENT -> "PAYMENT VOUCHER"
                BusyVoucherType.JOURNAL -> "JOURNAL VOUCHER"
                BusyVoucherType.CONTRA -> "CONTRA VOUCHER"
                BusyVoucherType.RECEIPT -> "PAYMENT RECEIPT"
            }
            val safeVch = sanitizeFileSlug(voucher.voucherNo, "VCH")
            val fileName = "NB_${voucher.type.prefix}_$safeVch.pdf"
            val pdf = PdfA4Writer(context, docHeading, "ORIGINAL FOR RECIPIENT")

            pdf.drawMetaBox(
                listOf(
                    "Billed To / Party: ${voucher.partyAccountName}   |   Invoice/Vch No: ${voucher.voucherNo}",
                    "Party GSTIN: ${voucher.partyGstin.ifBlank { "Unregistered / B2C" }}   |   Date: ${voucher.date}   |   Cost Center: ${voucher.costCenter.label}",
                    "Contra Ledger: ${voucher.contraAccountName}   |   UTR/Ref: ${voucher.utrOrChequeNo.ifBlank { voucher.billRefNo.ifBlank { "-" } }}"
                )
            )

            val cols = listOf(0.07f, 0.37f, 0.14f, 0.10f, 0.08f, 0.11f, 0.13f)
            pdf.drawTableHeader(listOf("S.N.", "Description", "HSN/SAC", "Qty", "Unit", "Rate", "Amount"), cols)
            if (voucher.lineItems.isEmpty()) {
                pdf.drawTableRow(
                    listOf(
                        "1",
                        voucher.narration.ifBlank { "Solar / Wind EPC Supply & Services" },
                        "85414300",
                        "1.00",
                        "Job",
                        formatInr(voucher.taxableAmount, true),
                        formatInr(voucher.taxableAmount, true)
                    ),
                    cols,
                    boldLast = true
                )
            } else {
                voucher.lineItems.forEachIndexed { idx, item ->
                    pdf.drawTableRow(
                        listOf(
                            "${idx + 1}",
                            item.itemName,
                            item.hsnCode.ifBlank { "85414300" },
                            String.format(Locale.US, "%.2f", item.quantity),
                            item.unit,
                            formatInr(item.rate, true),
                            formatInr(item.taxableAmount, true)
                        ),
                        cols
                    )
                }
            }

            val taxable = voucher.taxableAmount.coerceAtLeast(0.0)
            val roundedTotal = voucher.totalAmount.roundToLong().toDouble()
            val roundOff = roundedTotal - voucher.totalAmount
            pdf.addSpacing(6f)
            val taxCols = listOf(0.70f, 0.30f)
            pdf.drawTableRow(listOf("Taxable Value", formatInr(taxable, true)), taxCols)
            if (voucher.cgstAmount > 0 || voucher.sgstAmount > 0) {
                pdf.drawTableRow(listOf("Add: CGST", formatInr(voucher.cgstAmount, true)), taxCols)
                pdf.drawTableRow(listOf("Add: SGST", formatInr(voucher.sgstAmount, true)), taxCols)
            }
            if (voucher.igstAmount > 0) {
                pdf.drawTableRow(listOf("Add: IGST", formatInr(voucher.igstAmount, true)), taxCols)
            } else if (abs(roundOff) > 0.005) {
                pdf.drawTableRow(listOf("Round Off (+/-)", String.format(Locale.US, "%+.2f", roundOff)), taxCols)
            }
            pdf.drawTableRow(listOf("GRAND TOTAL", formatInr(roundedTotal, true)), taxCols, boldLast = true, highlightRow = true)

            pdf.addSpacing(8f)
            pdf.drawWordsBar("Amount Chargeable (in words): ${amountInWords(roundedTotal)}")
            pdf.drawBankAndTermsFooter()

            val outFile = pdf.writeToExportsFile(fileName)
            val msg = "*$COMPANY_NAME*\n$docHeading #${voucher.voucherNo} (${voucher.date})\nParty: ${voucher.partyAccountName}\nTotal: ${formatInr(roundedTotal, true)}"
            sharePdfFile(context, outFile, "Share $docHeading PDF", "$docHeading ${voucher.voucherNo} - $COMPANY_NAME", msg)
            Result.success(fileName)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Generates a valid A4 Payment Receipt `.pdf` file and launches WhatsApp / Android Share Sheet.
     */
    fun exportAndSharePaymentReceiptPdf(context: Context, voucher: BusyVoucher): Result<String> {
        return try {
            val safeVch = sanitizeFileSlug(voucher.voucherNo, "RCPT")
            val fileName = "NB_Receipt_$safeVch.pdf"
            val pdf = PdfA4Writer(context, "PAYMENT RECEIPT", "OFFICIAL ACKNOWLEDGEMENT")

            pdf.drawMetaBox(
                listOf(
                    "Receipt No: ${voucher.voucherNo}   |   Receipt Date: ${voucher.date}",
                    "Received From: ${voucher.partyAccountName}   |   Credited To: ${voucher.contraAccountName}",
                    "Mode / UTR / Cheque: ${voucher.utrOrChequeNo.ifBlank { "Cash / UPI" }}   |   Paid By: ${voucher.paidBy.ifBlank { voucher.partyAccountName }}"
                )
            )

            val cols = listOf(0.08f, 0.67f, 0.25f)
            pdf.drawTableHeader(listOf("S.N.", "Particulars", "Received (INR)"), cols)
            pdf.drawTableRow(
                listOf(
                    "1",
                    "Received from ${voucher.partyAccountName} towards Solar / Wind EPC. ${voucher.narration}".trim(),
                    formatInr(voucher.totalAmount, true)
                ),
                cols
            )
            pdf.drawTableRow(listOf("", "TOTAL RECEIVED", formatInr(voucher.totalAmount, true)), cols, boldLast = true, highlightRow = true)

            pdf.addSpacing(8f)
            pdf.drawWordsBar("Sum of Rupees (in words): ${amountInWords(voucher.totalAmount)}")
            pdf.drawBankAndTermsFooter()

            val outFile = pdf.writeToExportsFile(fileName)
            val msg = "*$COMPANY_NAME*\nPayment Receipt #${voucher.voucherNo} (${voucher.date})\nReceived From: ${voucher.partyAccountName}\nAmount: ${formatInr(voucher.totalAmount, true)}\nMode/UTR: ${voucher.utrOrChequeNo.ifBlank { "Cash / Online" }}"
            sharePdfFile(context, outFile, "Share Payment Receipt PDF", "Payment Receipt ${voucher.voucherNo} - $COMPANY_NAME", msg)
            Result.success(fileName)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Generates a valid A4 Delivery Challan `.pdf` file and launches WhatsApp / Android Share Sheet.
     */
    fun exportAndShareDeliveryChallanPdf(
        context: Context,
        partyName: String,
        partyAddress: String,
        partyPhone: String,
        challanNo: String,
        date: String,
        items: List<BusyLineItem>,
        remarks: String = ""
    ): Result<String> {
        return try {
            val fileName = "NB_Challan_${sanitizeFileSlug(challanNo, "DC")}.pdf"
            val pdf = PdfA4Writer(context, "DELIVERY CHALLAN", "SITE DISPATCH COPY")

            pdf.drawMetaBox(
                listOf(
                    "Consignee / Party: $partyName   |   Challan No: $challanNo",
                    "Site Address: ${partyAddress.ifBlank { "Udaipur, Rajasthan" }}   |   Mobile: ${partyPhone.ifBlank { "-" }}",
                    "Dispatch Date: $date   |   Transport: Company Site Dispatch"
                )
            )

            val cols = listOf(0.08f, 0.50f, 0.18f, 0.12f, 0.12f)
            pdf.drawTableHeader(listOf("S.N.", "Description of Goods", "HSN/SAC", "Qty.", "Unit"), cols)
            if (items.isEmpty()) {
                pdf.drawTableRow(listOf("-", "No material items listed for dispatch yet.", "-", "0.00", "-"), cols)
            } else {
                items.forEachIndexed { idx, itm ->
                    pdf.drawTableRow(
                        listOf(
                            "${idx + 1}",
                            itm.itemName,
                            itm.hsnCode.ifBlank { "85414300" },
                            String.format(Locale.US, "%.2f", itm.quantity),
                            itm.unit
                        ),
                        cols
                    )
                }
            }
            val totalQty = items.sumOf { it.quantity }
            pdf.drawTableRow(
                listOf("", "TOTAL DISPATCHED QUANTITY", "", String.format(Locale.US, "%.2f", totalQty), "Units"),
                cols,
                boldLast = true,
                highlightRow = true
            )

            pdf.addSpacing(8f)
            pdf.drawWordsBar("Dispatch Remarks: ${remarks.ifBlank { "Material dispatched in good condition for Solar / Wind EPC installation." }}")
            pdf.drawBankAndTermsFooter(receiverLine = "Receiver's Signature & Date: ________________________")

            val outFile = pdf.writeToExportsFile(fileName)
            val msg = "*$COMPANY_NAME*\nDelivery Challan #$challanNo ($date)\nParty: $partyName\nItems: ${items.size} | Total Qty: ${String.format(Locale.US, "%.2f", totalQty)}"
            sharePdfFile(context, outFile, "Share Delivery Challan PDF", "Delivery Challan $challanNo - $partyName", msg)
            Result.success(fileName)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun exportAndSharePartyHardwareChallanPdf(
        context: Context,
        party: PartyProject,
        allocatedPanels: List<DcrPanel>,
        allocatedMeters: List<SolarMeter>,
        rawMaterials: List<RawMaterial>
    ): Result<String> {
        val lineItems = buildPartyChallanItems(party, allocatedPanels, allocatedMeters, rawMaterials)
        val challanNo = "DC-${party.partyName.take(4).uppercase(Locale.US).replace(" ", "")}-${todayIsoDate().replace("-", "")}"
        return exportAndShareDeliveryChallanPdf(
            context = context,
            partyName = party.partyName,
            partyAddress = party.cityOrLocation.ifBlank { party.address },
            partyPhone = party.mobile,
            challanNo = challanNo,
            date = todayIsoDate(),
            items = lineItems,
            remarks = "Capacity: ${party.capacityKw} kW (${party.division.label}) | K-No: ${party.consumerNumber.ifBlank { "-" }}"
        )
    }

    /**
     * Generates a valid A4 MNRE DCR Certificate `.pdf` file and launches WhatsApp / Android Share Sheet.
     */
    fun exportAndShareDcrCertificatePdf(
        context: Context,
        party: PartyProject,
        allocatedPanels: List<DcrPanel>
    ): Result<String> {
        return try {
            val fileName = "DCR_Certificate_${sanitizeFileSlug(party.partyName, "Party")}_${todayIsoDate()}.pdf"
            val pdf = PdfA4Writer(context, "DCR UNDERTAKING", "MNRE PM SURYA GHAR")
            val totalWp = allocatedPanels.sumOf { it.wattage }

            pdf.drawMetaBox(
                listOf(
                    "Consumer / Party: ${party.partyName}   |   AVVNL K-No: ${party.consumerNumber.ifBlank { "-" }}",
                    "Site Location: ${party.cityOrLocation.ifBlank { party.address }}   |   Mobile: ${party.mobile.ifBlank { "-" }}",
                    "Capacity: ${party.capacityKw} kW (${party.roofStructureType.label})   |   Date: ${todayIsoDate()}"
                )
            )
            pdf.drawWordsBar("Allocated Made-in-India DCR PV Modules: ${allocatedPanels.size} Modules   |   Total Capacity: $totalWp Wp")
            pdf.addSpacing(6f)

            val cols = listOf(0.08f, 0.36f, 0.22f, 0.14f, 0.20f)
            pdf.drawTableHeader(listOf("S.N.", "Module Serial No.", "OEM Make", "Wattage", "Invoice Ref"), cols)
            if (allocatedPanels.isEmpty()) {
                pdf.drawTableRow(listOf("-", "No DCR panel serial numbers allocated yet.", "-", "-", "-"), cols)
            } else {
                allocatedPanels.forEachIndexed { idx, p ->
                    pdf.drawTableRow(
                        listOf("${idx + 1}", p.serialNumber, p.brand, "${p.wattage} Wp", p.purchaseInvoiceNo.ifBlank { "-" }),
                        cols
                    )
                }
            }

            pdf.addSpacing(8f)
            pdf.drawWordsBar("Self-Declaration: All PV modules listed above are manufactured in India complying with MNRE DCR norms.")
            pdf.drawBankAndTermsFooter()

            val outFile = pdf.writeToExportsFile(fileName)
            val msg = "*$COMPANY_NAME*\nMNRE DCR Certificate for *${party.partyName}* (${party.capacityKw} kW)\nAllocated Panels: ${allocatedPanels.size} ($totalWp Wp)"
            sharePdfFile(context, outFile, "Share DCR Certificate PDF", "DCR Certificate - ${party.partyName}", msg)
            Result.success(fileName)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Generates a valid A4 Day-by-Day Financial Register `.pdf` file and launches Android Share Sheet.
     */
    fun exportAndShareDayByDayReportPdf(
        context: Context,
        rows: List<DayByDayFinancialRow>
    ): Result<String> {
        return try {
            val fileName = "NB_DayByDay_Report_${todayIsoDate()}.pdf"
            val pdf = PdfA4Writer(context, "DAY-BY-DAY REPORT", "DAILY CASH FLOW REGISTER")

            pdf.drawMetaBox(
                listOf(
                    "Company: $COMPANY_NAME   |   Generated: ${todayIsoDate()}",
                    "Active Days: ${rows.size}   |   Total Sales: ${formatInr(rows.sumOf { it.salesBilled })}   |   Total Receipts: ${formatInr(rows.sumOf { it.receiptsCollected })}"
                )
            )

            val cols = listOf(0.16f, 0.08f, 0.19f, 0.19f, 0.19f, 0.19f)
            pdf.drawTableHeader(listOf("Date", "Txns", "Sales", "Receipts", "Pay / Exp", "Net Cash"), cols)
            if (rows.isEmpty()) {
                pdf.drawTableRow(listOf("-", "0", "0", "0", "0", "0"), cols)
            } else {
                rows.forEach { d ->
                    pdf.drawTableRow(
                        listOf(
                            d.date,
                            "${d.voucherCount}",
                            formatInr(d.salesBilled),
                            formatInr(d.receiptsCollected),
                            formatInr(d.paymentsAndKharcha),
                            formatInr(d.netCashFlow)
                        ),
                        cols,
                        boldLast = true
                    )
                }
                pdf.drawTableRow(
                    listOf(
                        "TOTAL",
                        "${rows.sumOf { it.voucherCount }}",
                        formatInr(rows.sumOf { it.salesBilled }),
                        formatInr(rows.sumOf { it.receiptsCollected }),
                        formatInr(rows.sumOf { it.paymentsAndKharcha }),
                        formatInr(rows.sumOf { it.netCashFlow })
                    ),
                    cols,
                    boldLast = true,
                    highlightRow = true
                )
            }
            pdf.addSpacing(8f)
            pdf.drawBankAndTermsFooter()

            val outFile = pdf.writeToExportsFile(fileName)
            val msg = "*$COMPANY_NAME*\nDay-by-Day Financial Report (${todayIsoDate()})\nNet Cash Flow: ${formatInr(rows.sumOf { it.netCashFlow })}"
            sharePdfFile(context, outFile, "Share Day-by-Day Report PDF", "NB Day-by-Day Report - ${todayIsoDate()}", msg)
            Result.success(fileName)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Generates a valid A4 Party Costing & Profit Report `.pdf` file and launches Android Share Sheet.
     */
    fun exportAndSharePartyCostingReportPdf(
        context: Context,
        row: PartyCostingRow,
        allocatedPanels: List<DcrPanel>,
        allocatedMeters: List<SolarMeter>
    ): Result<String> {
        return try {
            val party = row.party
            val fileName = "NB_Costing_${sanitizeFileSlug(party.partyName, "Party")}_${todayIsoDate()}.pdf"
            val pdf = PdfA4Writer(context, "PARTY COSTING REPORT", "PROFITABILITY AUDIT")

            pdf.drawMetaBox(
                listOf(
                    "Party: ${party.partyName}   |   Division: ${party.division.label} • ${party.leadSource.label}",
                    "System: ${party.capacityKw} kW (${party.roofStructureType.label})   |   Location: ${party.cityOrLocation.ifBlank { party.address }}",
                    "Received / Pending: ${formatInr(party.receivedAmount)} / ${formatInr(party.pendingAmount)}   |   Date: ${todayIsoDate()}"
                )
            )

            val cols = listOf(0.08f, 0.67f, 0.25f)
            pdf.drawTableHeader(listOf("S.N.", "Costing & Revenue Particulars", "Amount (INR)"), cols)
            pdf.drawTableRow(listOf("1", "Gross Contract Deal Value (${party.capacityKw} kW)", formatInr(row.dealValue, true)), cols)
            pdf.drawTableRow(listOf("2", "(-) Main Hardware (${allocatedPanels.size} Panels, ${allocatedMeters.size} Meters, BOS)", "- ${formatInr(row.mainMaterialCost, true)}"), cols)
            pdf.drawTableRow(listOf("3", "(-) On-Site Local Material Purchases (${party.sitePurchases.size} Bills)", "- ${formatInr(row.onSitePurchaseCost, true)}"), cols)
            pdf.drawTableRow(listOf("4", "(-) Daily Site Kharcha & Labour", "- ${formatInr(row.dailyKharchaCost, true)}"), cols)
            pdf.drawTableRow(listOf("5", "(-) Partner / Referral Commission (${String.format(Locale.US, "%.1f", party.partnerCommissionPct)}%)", "- ${formatInr(row.referralCommission, true)}"), cols)
            pdf.drawTableRow(
                listOf("", "NET PARTY PROFIT (${String.format(Locale.US, "%.1f", row.marginPct)}% Margin)", formatInr(row.netProfit, true)),
                cols,
                boldLast = true,
                highlightRow = true
            )

            pdf.addSpacing(8f)
            pdf.drawWordsBar("Net Profit (in words): ${amountInWords(abs(row.netProfit))}${if (row.netProfit < 0) " (Net Deficit)" else ""}")
            pdf.drawBankAndTermsFooter()

            val outFile = pdf.writeToExportsFile(fileName)
            val msg = "*$COMPANY_NAME*\nParty Costing Report: *${party.partyName}*\nDeal: ${formatInr(row.dealValue)} | Net Profit: ${formatInr(row.netProfit)} (${String.format(Locale.US, "%.1f", row.marginPct)}%)"
            sharePdfFile(context, outFile, "Share Party Costing PDF", "Party Costing - ${party.partyName}", msg)
            Result.success(fileName)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Generates a valid A4 BUSY 21 Account Ledger Statement `.pdf` file and launches Android Share Sheet.
     */
    fun exportAndShareAccountLedgerPdf(
        context: Context,
        account: BusyAccount,
        fromDate: String,
        toDate: String,
        rows: List<LedgerPrintRow>,
        closingBalance: Double
    ): Result<String> {
        return try {
            val fileName = "NB_Ledger_${sanitizeFileSlug(account.name, "Account")}_${todayIsoDate()}.pdf"
            val pdf = PdfA4Writer(context, "ACCOUNT LEDGER", "BUSY 21 STATEMENT")

            pdf.drawMetaBox(
                listOf(
                    "Account: ${account.name} (${account.group.label})",
                    "Period: $fromDate to $toDate   |   Opening: ${formatBusyDrCr(account.openingBalance).first}"
                )
            )

            val cols = listOf(0.14f, 0.14f, 0.28f, 0.14f, 0.14f, 0.16f)
            pdf.drawTableHeader(listOf("Date", "Vch No", "Particulars", "Debit (Dr)", "Credit (Cr)", "Balance"), cols)
            rows.forEach { r ->
                val (balTxt, _) = formatBusyDrCr(r.runningBalance)
                pdf.drawTableRow(
                    listOf(
                        r.date,
                        r.voucherNo,
                        r.particulars,
                        if (r.debit > 0) formatInr(r.debit, true) else "-",
                        if (r.credit > 0) formatInr(r.credit, true) else "-",
                        balTxt
                    ),
                    cols
                )
            }
            pdf.drawTableRow(
                listOf("", "", "CLOSING BALANCE", "", "", formatBusyDrCr(closingBalance).first),
                cols,
                boldLast = true,
                highlightRow = true
            )

            pdf.addSpacing(8f)
            pdf.drawBankAndTermsFooter()

            val outFile = pdf.writeToExportsFile(fileName)
            val msg = "*$COMPANY_NAME*\nAccount Ledger: *${account.name}*\nClosing Balance: ${formatBusyDrCr(closingBalance).first}"
            sharePdfFile(context, outFile, "Share Account Ledger PDF", "Account Ledger - ${account.name}", msg)
            Result.success(fileName)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildPartyChallanItems(
        party: PartyProject,
        allocatedPanels: List<DcrPanel>,
        allocatedMeters: List<SolarMeter>,
        rawMaterials: List<RawMaterial>
    ): List<BusyLineItem> = buildList {
        if (allocatedPanels.isNotEmpty()) {
            val serialPreview = allocatedPanels.joinToString(", ") { it.serialNumber }
            add(
                BusyLineItem(
                    itemName = "${allocatedPanels.first().brand} ${allocatedPanels.first().wattage}W DCR Solar Panels (S/N: $serialPreview)",
                    hsnCode = "85414300",
                    quantity = allocatedPanels.size.toDouble(),
                    unit = "Nos"
                )
            )
        }
        allocatedMeters.forEach { m ->
            add(
                BusyLineItem(
                    itemName = "${m.meterType.label} - ${m.makeAndPhase} (S/N: ${m.serialNumber})",
                    hsnCode = "90283010",
                    quantity = 1.0,
                    unit = "Nos"
                )
            )
        }
        rawMaterials.forEach { rm ->
            val issuedQty = rm.issueLogs.filter { it.partyId == party.id }.sumOf { it.quantity }
            if (issuedQty > 0.0) {
                val hsn = if (rm.category.contains("Structure", ignoreCase = true)) "73089090" else "85444999"
                add(
                    BusyLineItem(
                        itemName = "${rm.name} (${rm.category})",
                        hsnCode = hsn,
                        quantity = issuedQty,
                        unit = rm.unit
                    )
                )
            }
        }
    }

    private fun sanitizeFileSlug(raw: String, fallback: String): String =
        raw.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').ifBlank { fallback }

    private fun sharePdfFile(
        context: Context,
        pdfFile: File,
        chooserTitle: String,
        subject: String,
        bodyText: String
    ) {
        val pdfUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, pdfUri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, bodyText)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, chooserTitle))
    }

    /**
     * Reusable paginated A4 (`595 x 842 pt`) native Android PdfDocument canvas builder.
     */
    private class PdfA4Writer(
        private val context: Context,
        private val docTitle: String,
        private val subTag: String
    ) {
        private val pageW = 595
        private val pageH = 842
        private val margin = 26f
        private val contentW = pageW - margin * 2f

        private val doc = PdfDocument()
        private var pageNumber = 0
        private var currentPage: PdfDocument.Page? = null
        private var canvas: Canvas = Canvas()
        private var y = margin

        private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#8E1E1B")
            textSize = 14f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        private val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#8E1E1B")
            textSize = 10.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        private val darkBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 9.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#334155")
            textSize = 9f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        private val greenBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2B4D12")
            textSize = 10.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        private val maroonBig = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#8E1E1B")
            textSize = 15f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        private val whiteBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 10f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        private val borderPaint = Paint().apply {
            color = Color.parseColor("#94A3B8")
            style = Paint.Style.STROKE
            strokeWidth = 0.9f
        }
        private val limeBgPaint = Paint().apply {
            color = Color.parseColor("#F1F8E9")
            style = Paint.Style.FILL
        }
        private val slateBgPaint = Paint().apply {
            color = Color.parseColor("#F1F5F9")
            style = Paint.Style.FILL
        }
        private val maroonBgPaint = Paint().apply {
            color = Color.parseColor("#8E1E1B")
            style = Paint.Style.FILL
        }

        private val logoBmp: Bitmap? by lazy {
            runCatching {
                BitmapFactory.decodeResource(context.resources, R.drawable.nb_solar_icon_fg)
            }.getOrNull()
        }

        init {
            startNewPage("$docTitle — $subTag", fullLetterhead = true)
        }

        fun startNewPage(bannerText: String, fullLetterhead: Boolean = false) {
            currentPage?.let { doc.finishPage(it) }
            pageNumber++
            val page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNumber).create())
            currentPage = page
            canvas = page.canvas
            canvas.drawColor(Color.WHITE)
            canvas.drawRect(margin, margin, pageW - margin, pageH - margin, borderPaint)

            if (fullLetterhead) {
                logoBmp?.let { bmp ->
                    canvas.drawBitmap(bmp, null, RectF(margin + 10f, margin + 8f, margin + 50f, margin + 48f), null)
                }
                canvas.drawText(COMPANY_NAME, margin + 60f, margin + 23f, titlePaint)
                canvas.drawText(fitText(COMPANY_ADDRESS, bodyPaint, contentW - 72f), margin + 60f, margin + 36f, bodyPaint)
                canvas.drawText("GSTIN: $COMPANY_GSTIN | Tel: $COMPANY_TEL_DISPLAY | $COMPANY_EMAIL", margin + 60f, margin + 48f, darkBold)

                canvas.drawRect(margin, margin + 56f, pageW - margin, margin + 78f, maroonBgPaint)
                canvas.drawText(fitText(bannerText, whiteBold, contentW - 20f), margin + 10f, margin + 71f, whiteBold)
                y = margin + 90f
            } else {
                canvas.drawRect(margin, margin, pageW - margin, margin + 24f, maroonBgPaint)
                canvas.drawText(fitText("$COMPANY_NAME • $bannerText (Pg $pageNumber)", whiteBold, contentW - 20f), margin + 10f, margin + 16f, whiteBold)
                y = margin + 38f
            }
        }

        private fun ensureSpace(neededPt: Float) {
            if (y + neededPt > pageH - margin - 24f) {
                startNewPage("$docTitle (Continued)", fullLetterhead = false)
            }
        }

        fun addSpacing(pt: Float) {
            y += pt
        }

        fun drawMetaBox(lines: List<String>) {
            val h = lines.size * 15f + 10f
            ensureSpace(h)
            canvas.drawRect(margin + 8f, y, pageW - margin - 8f, y + h, slateBgPaint)
            canvas.drawRect(margin + 8f, y, pageW - margin - 8f, y + h, borderPaint)
            lines.forEachIndexed { idx, line ->
                canvas.drawText(fitText(line, darkBold, contentW - 28f), margin + 14f, y + 15f + idx * 15f, darkBold)
            }
            y += h + 8f
        }

        fun drawHighlightBox(heading: String, subLine: String, bigValue: String) {
            ensureSpace(78f)
            canvas.drawRect(margin + 8f, y, pageW - margin - 8f, y + 70f, limeBgPaint)
            canvas.drawRect(margin + 8f, y, pageW - margin - 8f, y + 70f, borderPaint)
            canvas.drawText(fitText(heading, greenBold, contentW - 28f), margin + 16f, y + 18f, greenBold)
            canvas.drawText(fitText(subLine, bodyPaint, contentW - 28f), margin + 16f, y + 34f, bodyPaint)
            canvas.drawText(fitText(bigValue, maroonBig, contentW - 28f), margin + 16f, y + 55f, maroonBig)
            y += 78f
        }

        fun drawSectionHeading(title: String) {
            ensureSpace(22f)
            canvas.drawText(fitText(title, sectionPaint, contentW - 20f), margin + 8f, y + 10f, sectionPaint)
            y += 16f
        }

        fun drawTableHeader(headers: List<String>, fractions: List<Float>) {
            ensureSpace(24f)
            val usableW = contentW - 16f
            var x = margin + 8f
            canvas.drawRect(x, y, x + usableW, y + 20f, slateBgPaint)
            canvas.drawRect(x, y, x + usableW, y + 20f, borderPaint)
            headers.forEachIndexed { idx, label ->
                val cw = usableW * fractions.getOrElse(idx) { 1f / headers.size }
                canvas.drawRect(x, y, x + cw, y + 20f, borderPaint)
                canvas.drawText(fitText(label, darkBold, cw - 8f), x + 4f, y + 13.5f, darkBold)
                x += cw
            }
            y += 20f
        }

        fun drawTableRow(
            cells: List<String>,
            fractions: List<Float>,
            boldLast: Boolean = false,
            highlightRow: Boolean = false
        ) {
            val rowH = 20f
            ensureSpace(rowH + 4f)
            val usableW = contentW - 16f
            var x = margin + 8f
            if (highlightRow) {
                canvas.drawRect(x, y, x + usableW, y + rowH, limeBgPaint)
            }
            cells.forEachIndexed { idx, text ->
                val cw = usableW * fractions.getOrElse(idx) { 1f / cells.size }
                canvas.drawRect(x, y, x + cw, y + rowH, borderPaint)
                val p = if (highlightRow || (boldLast && idx == cells.lastIndex)) darkBold else bodyPaint
                canvas.drawText(fitText(text, p, cw - 8f), x + 4f, y + 13.5f, p)
                x += cw
            }
            y += rowH
        }

        fun drawWordsBar(text: String) {
            ensureSpace(26f)
            canvas.drawRect(margin + 8f, y, pageW - margin - 8f, y + 22f, slateBgPaint)
            canvas.drawRect(margin + 8f, y, pageW - margin - 8f, y + 22f, borderPaint)
            canvas.drawText(fitText(text, darkBold, contentW - 26f), margin + 14f, y + 14.5f, darkBold)
            y += 28f
        }

        fun drawCaptionedBitmap(caption: String, bmp: Bitmap, maxHeightPt: Float = 260f) {
            val targetW = contentW - 20f
            val aspect = bmp.height.toFloat() / bmp.width.coerceAtLeast(1)
            val targetH = (targetW * aspect).coerceAtMost(maxHeightPt)
            ensureSpace(targetH + 34f)
            canvas.drawText(fitText(caption, darkBold, targetW), margin + 10f, y + 12f, darkBold)
            y += 18f
            canvas.drawBitmap(bmp, null, RectF(margin + 10f, y, margin + 10f + targetW, y + targetH), null)
            canvas.drawRect(margin + 10f, y, margin + 10f + targetW, y + targetH, borderPaint)
            y += targetH + 14f
        }

        fun drawBankAndTermsFooter(receiverLine: String? = null) {
            ensureSpace(88f)
            val fy = maxOf(y + 8f, pageH - margin - 84f)
            canvas.drawLine(margin, fy, pageW - margin, fy, borderPaint)
            canvas.drawText("Bank: $BANK_NAME | Branch: $BANK_BRANCH", margin + 10f, fy + 16f, darkBold)
            canvas.drawText("A/c No.: $BANK_ACCOUNT_NO | IFSC: $BANK_IFSC", margin + 10f, fy + 30f, bodyPaint)
            val terms = receiverLine ?: "1. Goods once sold will not be taken back. 2. Subject to Udaipur (Raj.) Jurisdiction. E. & O.E."
            canvas.drawText(fitText(terms, bodyPaint, contentW - 180f), margin + 10f, fy + 46f, bodyPaint)
            canvas.drawText("For $COMPANY_NAME", pageW - margin - 210f, fy + 66f, darkBold)
            y = fy + 76f
        }

        fun drawDualSignatureFooter(clientName: String, refNo: String, netCostLabel: String) {
            ensureSpace(125f)
            val fy = maxOf(y + 8f, pageH - margin - 120f)
            canvas.drawLine(margin, fy, pageW - margin, fy, borderPaint)
            canvas.drawText("Bank: $BANK_NAME | Branch: $BANK_BRANCH | A/c: $BANK_ACCOUNT_NO | IFSC: $BANK_IFSC", margin + 10f, fy + 16f, darkBold)
            canvas.drawText("GSTIN: $COMPANY_GSTIN | Tel: $COMPANY_TEL_DISPLAY | Email: $COMPANY_EMAIL", margin + 10f, fy + 30f, bodyPaint)
            val midX = margin + contentW * 0.5f
            canvas.drawLine(midX, fy + 38f, midX, pageH - margin - 6f, borderPaint)
            canvas.drawText(fitText("Client Acceptance ($refNo | $netCostLabel)", darkBold, contentW * 0.48f), margin + 10f, fy + 52f, darkBold)
            canvas.drawText(fitText("Signature & Date: $clientName", bodyPaint, contentW * 0.48f), margin + 10f, fy + 98f, bodyPaint)
            canvas.drawText("For $COMPANY_NAME", midX + 12f, fy + 52f, darkBold)
            canvas.drawText("Authorized Signatory (Udaipur - 313001)", midX + 12f, fy + 98f, bodyPaint)
            y = fy + 110f
        }

        fun drawCoverHeroBanner(title: String, subtitle: String, chipsLine: String) {
            ensureSpace(88f)
            canvas.drawRect(margin + 8f, y, pageW - margin - 8f, y + 78f, maroonBgPaint)
            val bigWhite = Paint(whiteBold).apply { textSize = 13.5f }
            val subWhite = Paint(whiteBold).apply { textSize = 9.5f }
            canvas.drawText(fitText(title, bigWhite, contentW - 28f), margin + 16f, y + 24f, bigWhite)
            canvas.drawText(fitText(subtitle, subWhite, contentW - 28f), margin + 16f, y + 44f, subWhite)
            canvas.drawText(fitText(chipsLine, subWhite, contentW - 28f), margin + 16f, y + 63f, subWhite)
            y += 88f
        }

        fun drawSldCanvasDiagram(capacityKwp: Double, panelCount: Int, inverterMake: String, earthingMeters: Double) {
            ensureSpace(112f)
            val boxLeft = margin + 8f
            val boxRight = pageW - margin - 8f
            canvas.drawRect(boxLeft, y, boxRight, y + 100f, slateBgPaint)
            canvas.drawRect(boxLeft, y, boxRight, y + 100f, borderPaint)

            val w = boxRight - boxLeft
            val blocks = listOf(
                "PV ARRAY" to "$panelCount Panels",
                "DCDB" to "SPD + Fuse",
                "INVERTER" to "$capacityKwp KWp",
                "ACDB + NET" to "AVVNL Grid"
            )
            val bw = 96f
            val gap = (w - 24f - bw * 4f) / 3f
            var bx = boxLeft + 12f
            val by = y + 20f
            blocks.forEachIndexed { idx, (head, sub) ->
                canvas.drawRect(bx, by, bx + bw, by + 44f, limeBgPaint)
                canvas.drawRect(bx, by, bx + bw, by + 44f, borderPaint)
                canvas.drawText(fitText(head, darkBold, bw - 8f), bx + 6f, by + 18f, darkBold)
                canvas.drawText(fitText(sub, bodyPaint, bw - 8f), bx + 6f, by + 34f, bodyPaint)
                if (idx < blocks.lastIndex) {
                    canvas.drawLine(bx + bw, by + 22f, bx + bw + gap, by + 22f, borderPaint)
                }
                bx += bw + gap
            }
            canvas.drawText(
                fitText("Inverter: $inverterMake  |  3-Pit Chemical Earthing (${earthingMeters}m) + Roof Lightning Arrester", greenBold, w - 20f),
                boxLeft + 12f,
                y + 84f,
                greenBold
            )
            y += 108f
        }

        fun drawBulletLines(lines: List<String>) {
            lines.forEach { line ->
                ensureSpace(16f)
                canvas.drawText(fitText("• $line", bodyPaint, contentW - 24f), margin + 12f, y + 11f, bodyPaint)
                y += 15f
            }
            y += 4f
        }

        fun writeToExportsFile(fileName: String): File {
            currentPage?.let {
                doc.finishPage(it)
                currentPage = null
            }
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val outFile = File(exportDir, fileName)
            try {
                FileOutputStream(outFile).use { fos ->
                    doc.writeTo(fos)
                }
            } finally {
                doc.close()
            }
            return outFile
        }

        private fun fitText(raw: String, paint: Paint, maxWidth: Float): String {
            if (raw.isEmpty() || paint.measureText(raw) <= maxWidth) return raw
            val ellipsis = "…"
            var end = raw.length
            while (end > 1 && paint.measureText(raw.substring(0, end) + ellipsis) > maxWidth) {
                end--
            }
            return raw.substring(0, end) + ellipsis
        }
    }

    private fun bitmapToDataUri(bmp: Bitmap?, maxDim: Int = 720): String? {
        if (bmp == null) return null
        return try {
            val scale = minOf(1.0f, maxDim.toFloat() / maxOf(bmp.width, bmp.height).coerceAtLeast(1))
            val scaled = if (scale < 1.0f) {
                Bitmap.createScaledBitmap(
                    bmp,
                    (bmp.width * scale).roundToInt().coerceAtLeast(1),
                    (bmp.height * scale).roundToInt().coerceAtLeast(1),
                    true
                )
            } else bmp
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 72, out)
            val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            "data:image/jpeg;base64,$b64"
        } catch (_: Exception) {
            null
        }
    }

    private fun buildRooftopLayoutSvg(
        dimensions: SiteSurveyDimensions,
        breakdown: RealCostingBreakdown
    ): String {
        val count = breakdown.wiring.panelCount.coerceAtLeast(1)
        val cols = ceil(sqrt(count.toDouble() * 1.4)).toInt().coerceIn(2, 8)
        val rows = ceil(count.toDouble() / cols).toInt().coerceIn(1, 5)
        val roofW = 165.0
        val roofH = 92.0
        val rx = 14.0
        val ry = 18.0
        val cellW = (roofW - 20.0) / cols
        val cellH = minOf((roofH - 18.0) / rows, cellW * 1.35)
        val rects = StringBuilder()
        var drawn = 0
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (drawn >= count) break
                val px = (rx + 10.0 + c * cellW + 1.5).roundToInt()
                val py = (ry + 9.0 + r * cellH + 1.5).roundToInt()
                val pw = (cellW - 3.0).roundToInt().coerceAtLeast(6)
                val ph = (cellH - 3.0).roundToInt().coerceAtLeast(6)
                rects.append("""<rect x="$px" y="$py" width="$pw" height="$ph" fill="#1D4ED8" stroke="#BFDBFE" stroke-width="0.8"/>""")
                drawn++
            }
        }
        return """
        <svg viewBox="0 0 250 135" width="230" height="124" xmlns="http://www.w3.org/2000/svg">
          <rect x="4" y="4" width="242" height="127" rx="6" fill="#0F172A"/>
          <rect x="${rx.roundToInt()}" y="${ry.roundToInt()}" width="${roofW.roundToInt()}" height="${roofH.roundToInt()}" fill="#1E293B" stroke="#7CB342" stroke-width="1.5"/>
          $rects
          <line x1="179" y1="48" x2="215" y2="38" stroke="#EF4444" stroke-width="2" stroke-dasharray="4,2"/>
          <line x1="215" y1="38" x2="215" y2="96" stroke="#7CB342" stroke-width="2"/>
          <rect x="206" y="30" width="18" height="16" fill="#F59E0B" rx="2"/>
          <circle cx="215" cy="98" r="7" fill="#7CB342"/>
          <text x="215" y="24" font-size="7.5" fill="#F8FAFC" text-anchor="middle">INVERTER</text>
          <text x="215" y="114" font-size="7.5" fill="#DCEDC8" text-anchor="middle">3-PIT EARTH</text>
          <text x="96" y="124" font-size="8" fill="#E2E8F0" text-anchor="middle">Roof ${dimensions.roofLengthM}m × ${dimensions.roofWidthM}m • DC ${breakdown.wiring.dcCableMeters}m</text>
        </svg>
        """.trimIndent()
    }

    private fun buildSingleLineDiagramSvg(breakdown: RealCostingBreakdown): String = """
        <svg viewBox="0 0 290 112" width="275" height="106" xmlns="http://www.w3.org/2000/svg">
          <rect x="2" y="2" width="286" height="108" rx="4" fill="#F8FAFC" stroke="#CBD5E1"/>
          <rect x="10" y="24" width="54" height="44" rx="4" fill="#DBEAFE" stroke="#1D4ED8" stroke-width="1.2"/>
          <text x="37" y="44" font-size="8" font-weight="bold" text-anchor="middle" fill="#1E3A8A">PV ARRAY</text>
          <text x="37" y="56" font-size="7.5" text-anchor="middle" fill="#1E3A8A">${breakdown.wiring.panelCount} Panels</text>
          <line x1="64" y1="46" x2="86" y2="46" stroke="#DC2626" stroke-width="2"/>
          <rect x="86" y="28" width="42" height="36" rx="3" fill="#FEE2E2" stroke="#8E1E1B" stroke-width="1.2"/>
          <text x="107" y="45" font-size="8" font-weight="bold" text-anchor="middle" fill="#8E1E1B">DCDB</text>
          <text x="107" y="55" font-size="7" text-anchor="middle" fill="#8E1E1B">SPD+Fuse</text>
          <line x1="128" y1="46" x2="148" y2="46" stroke="#DC2626" stroke-width="2"/>
          <rect x="148" y="24" width="52" height="44" rx="4" fill="#FEF3C7" stroke="#D97706" stroke-width="1.2"/>
          <text x="174" y="44" font-size="8" font-weight="bold" text-anchor="middle" fill="#92400E">INVERTER</text>
          <text x="174" y="56" font-size="7.5" text-anchor="middle" fill="#92400E">${breakdown.capacityKw} kW</text>
          <line x1="200" y1="46" x2="218" y2="46" stroke="#2563EB" stroke-width="2"/>
          <rect x="218" y="28" width="60" height="36" rx="3" fill="#DCFCE7" stroke="#2B4D12" stroke-width="1.2"/>
          <text x="248" y="45" font-size="8" font-weight="bold" text-anchor="middle" fill="#2B4D12">ACDB + NET</text>
          <text x="248" y="55" font-size="7" text-anchor="middle" fill="#2B4D12">AVVNL GRID</text>
          <line x1="174" y1="68" x2="174" y2="92" stroke="#16A34A" stroke-width="1.8"/>
          <text x="174" y="102" font-size="7.5" font-weight="bold" text-anchor="middle" fill="#166534">3-Pit Copper Earthing (${breakdown.wiring.earthingWireMeters}m) + LA</text>
        </svg>
    """.trimIndent()

    private fun build3DStructureSvg(
        structureType: CostingStructureType,
        tiltDeg: Float,
        panelCount: Int
    ): String {
        val legHeight = when (structureType) {
            CostingStructureType.STANDARD_RCC -> 32
            CostingStructureType.ELEVATED_GAZEBO -> 68
            CostingStructureType.TIN_SHED -> 16
        }
        val baseY = 122
        val topBackY = baseY - legHeight - (tiltDeg * 0.45f).roundToInt().coerceIn(8, 22)
        val topFrontY = baseY - legHeight + 8
        return """
        <svg viewBox="0 0 240 140" width="215" height="125" xmlns="http://www.w3.org/2000/svg">
          <polygon points="20,$baseY 195,$baseY 220,${baseY - 18} 45,${baseY - 18}" fill="#E2E8F0" stroke="#94A3B8" stroke-width="1"/>
          <line x1="40" y1="$baseY" x2="40" y2="$topFrontY" stroke="#475569" stroke-width="3"/>
          <line x1="175" y1="$baseY" x2="175" y2="$topFrontY" stroke="#475569" stroke-width="3"/>
          <line x1="65" y1="${baseY - 18}" x2="65" y2="$topBackY" stroke="#64748B" stroke-width="2.5"/>
          <line x1="200" y1="${baseY - 18}" x2="200" y2="$topBackY" stroke="#64748B" stroke-width="2.5"/>
          <polygon points="34,$topFrontY 182,$topFrontY 208,$topBackY 60,$topBackY" fill="#1E3A8A" stroke="#0F172A" stroke-width="1.5"/>
          <line x1="83" y1="$topFrontY" x2="109" y2="$topBackY" stroke="#93C5FD" stroke-width="1"/>
          <line x1="132" y1="$topFrontY" x2="158" y2="$topBackY" stroke="#93C5FD" stroke-width="1"/>
          <text x="120" y="136" font-size="9" text-anchor="middle" fill="#8E1E1B" font-weight="bold">$panelCount Modules • ${structureType.shortLabel}</text>
        </svg>
        """.trimIndent()
    }

    /**
     * Shares a Document Vault item via Android Share Sheet (compatible with WhatsApp).
     */
    fun shareVaultDocument(context: Context, partyName: String, doc: PartyDocument) {
        val shareText = buildString {
            appendLine("*$COMPANY_NAME - Udaipur*")
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
        ensureLogoDataUri(context)
        val webView = WebView(context)
        activePrintWebView = webView
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

    /**
     * Writes and shares the sample Bulk Loan Parties CSV template via FileProvider.
     */
    fun exportSampleLoanPartiesCsv(context: Context): Result<File> {
        return try {
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(exportDir, "NB_Loan_Parties_Sample.csv")
            file.writeText(com.example.data.BulkLoanImporter.buildSampleLoanPartiesCsv(), Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "NB Solar Bulk Loan Parties CSV Template")
                putExtra(Intent.EXTRA_TEXT, "NB Engineering Works — Sample CSV Template for Bulk Loan Parties Import.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Save or Share Sample Loan Parties CSV"))
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Saves and shares Full Company Backup (.json) via FileProvider for Owner recovery/archival.
     */
    fun exportAndShareFullBackupJson(context: Context, jsonContent: String, dateStamp: String): Result<File> {
        return exportAndShareBytes(
            context = context,
            fileName = "NB_Solar_Wind_Full_Backup_$dateStamp.json",
            mimeType = "application/json",
            bytes = jsonContent.toByteArray(Charsets.UTF_8),
            subject = "NB Engineering Works Full Company Backup ($dateStamp)",
            bodyText = "Full NB Solar & Wind ERP backup JSON (Parties, Leads, Inventory, Challans, Receipts, Loans/MCO, BUSY 21).",
            chooserTitle = "Save or Share Full Company Backup (.json)"
        )
    }

    /**
     * Saves and shares Master Multi-Sheet Excel Workbook (.xlsx) via FileProvider.
     */
    fun exportAndShareMasterExcelXlsx(context: Context, xlsxBytes: ByteArray, dateStamp: String): Result<File> {
        return exportAndShareBytes(
            context = context,
            fileName = "NB_Solar_Wind_Master_Workbook_$dateStamp.xlsx",
            mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            bytes = xlsxBytes,
            subject = "NB Engineering Works Master Excel Workbook ($dateStamp)",
            bodyText = "NB Engineering Works Master Workbook (.xlsx) — Parties, Loans/MCO, Inventory & Challans, BUSY 21 Vouchers, Commissions.",
            chooserTitle = "Open or Share Master Excel Workbook (.xlsx)"
        )
    }

    /**
     * Saves and shares Master Summary CSV via FileProvider.
     */
    fun exportAndShareMasterSummaryCsv(context: Context, csvContent: String, dateStamp: String): Result<File> {
        return exportAndShareBytes(
            context = context,
            fileName = "NB_Solar_Wind_Master_Data_$dateStamp.csv",
            mimeType = "text/csv",
            bytes = csvContent.toByteArray(Charsets.UTF_8),
            subject = "NB Engineering Works Master CSV ($dateStamp)",
            bodyText = "NB Engineering Works Master Summary CSV Export.",
            chooserTitle = "Save or Share Master CSV"
        )
    }

    private fun exportAndShareBytes(
        context: Context,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        subject: String,
        bodyText: String,
        chooserTitle: String
    ): Result<File> {
        return try {
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(exportDir, fileName)
            file.writeBytes(bytes)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, bodyText)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, chooserTitle))
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
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
