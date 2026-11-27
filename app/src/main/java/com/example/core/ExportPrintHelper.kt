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
import com.example.data.PartyCostingRow
import com.example.data.model.BusyAccount
import com.example.data.model.BusyLineItem
import com.example.data.model.BusyVoucher
import com.example.data.model.BusyVoucherType
import com.example.data.model.CostingStructureType
import com.example.data.model.DcrPanel
import com.example.data.model.PartyDocument
import com.example.data.model.PartyProject
import com.example.data.model.ProposalFormat
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
        "Rama Phosphates Ltd. Plant, Ground Floor, Shed No. 1, Opp. Rama Phosphates Ltd, Udaipur, Rajasthan"
    const val COMPANY_GSTIN = "08CSZPK9277N2Z8"
    const val COMPANY_TEL = "9929214883"
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
              <div class="company-meta">GSTIN: $COMPANY_GSTIN &nbsp;|&nbsp; Tel.: $COMPANY_TEL &nbsp;|&nbsp; Email: $COMPANY_EMAIL</div>
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
        includeOwnerInternalCosting: Boolean = false
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
            includeOwnerInternalCosting = includeOwnerInternalCosting
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
        includeOwnerInternalCosting: Boolean = false
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
            ProposalFormat.PM_SURYA_GHAR_2PG -> """
              <div class="sheet">
                ${letterheadHtml("PM SURYA GHAR PROPOSAL", "${breakdown.capacityKw} kW RESIDENTIAL", logoUri)}
                $customerMetaTable
                <div class="kpi-row">
                  <div class="kpi-box"><div class="kpi-label">Gross Turnkey Price</div><div class="kpi-val">${formatInr(breakdown.grossQuotedPriceInr)}</div></div>
                  <div class="kpi-box" style="border-color:#7CB342;background:#F1F8E9;"><div class="kpi-label">Govt. DBT Subsidy</div><div class="kpi-val" style="color:#2B4D12;">- ${formatInr(breakdown.pmSuryaGharSubsidyInr)}</div></div>
                  <div class="kpi-box" style="border-color:#8E1E1B;background:#FFF8F7;"><div class="kpi-label">Net Cost After Subsidy</div><div class="kpi-val" style="color:#8E1E1B;">${formatInr(breakdown.finalNetCostAfterSubsidyInr)}</div></div>
                  <div class="kpi-box"><div class="kpi-label">Payback / Generation</div><div class="kpi-val">${breakdown.estimatedPaybackYears} Yrs • ${breakdown.estimatedMonthlyUnits}u/mo</div></div>
                </div>
                $photosSection
                $pricingSummaryTable
                <table class="grid no-break" style="margin-top:6px;">
                  <thead>
                    <tr><th colspan="4">Measured Site Survey &amp; Complete BOS Wiring Included</th></tr>
                  </thead>
                  <tbody>
                    <tr>
                      <td><b>Panels:</b> ${breakdown.wiring.panelCount} × ${dimensions.panelWattageWp}Wp DCR</td>
                      <td><b>DC Cable:</b> ${breakdown.wiring.dcCableMeters} m (4 sq.mm UV)</td>
                      <td><b>AC Cable:</b> ${breakdown.wiring.acCableMeters} m (Armoured)</td>
                      <td><b>3-Pit Earth + Conduit:</b> ${breakdown.wiring.earthingWireMeters}m / ${breakdown.wiring.pvcConduitMeters}m</td>
                    </tr>
                  </tbody>
                </table>
              </div>

              <div class="sheet page-break" style="margin-top:10px;">
                ${letterheadHtml("SUBSIDY & WARRANTY", "PAGE 2 OF 2", logoUri)}
                <table class="grid no-break">
                  <thead>
                    <tr><th colspan="4">PM Surya Ghar Central Subsidy &amp; AVVNL Net Metering Roadmap</th></tr>
                    <tr><th style="width:16%;">Step</th><th style="width:28%;">Milestone</th><th style="width:36%;">Action by NB Engineering &amp; Consumer</th><th style="width:20%;">Timeline</th></tr>
                  </thead>
                  <tbody>
                    <tr><td><b>Step 1</b></td><td>National Portal Registration</td><td>PM Surya Ghar online registration with AVVNL K-Number &amp; Technical Feasibility</td><td>Day 1–3</td></tr>
                    <tr><td><b>Step 2</b></td><td>HDGI Structure &amp; Array Install</td><td>Made-in-India DCR module installation, inverter mounting, earthing &amp; LA</td><td>Day 4–7</td></tr>
                    <tr><td><b>Step 3</b></td><td>DISCOM Inspection &amp; Net Meter</td><td>AVVNL Bi-directional Smart Meter installation, Sealing &amp; DCR Upload</td><td>Day 8–18</td></tr>
                    <tr><td><b>Step 4</b></td><td>Direct Bank Subsidy (DBT)</td><td>${formatInr(breakdown.pmSuryaGharSubsidyInr)} credited directly into Consumer Bank Account</td><td>Within ~30 Days</td></tr>
                  </tbody>
                </table>
                <table class="grid no-break" style="margin-top:6px;">
                  <thead><tr><th colspan="2">25-Year MNRE DCR Warranty &amp; Financial Returns</th></tr></thead>
                  <tbody>
                    <tr><td style="width:55%;"><b>DCR Solar PV Modules:</b> 12-Yr Product + 25-Yr Linear Output Warranty (80%+)<br/><b>Grid-Tie Inverter:</b> 5 to 10-Yr OEM Warranty • <b>HDGI Structure:</b> 25-Yr Anti-Rust</td>
                        <td style="width:45%;"><b>Annual Est. Generation:</b> ~${breakdown.estimatedMonthlyUnits * 12} Units/Yr<br/><b>Annual Bill Savings:</b> ~${formatInr(breakdown.estimatedMonthlySavingsInr * 12)}/Yr<br/><b>25-Year Net Savings:</b> ~${formatInr(breakdown.estimatedMonthlySavingsInr * 12 * 22)}</td></tr>
                  </tbody>
                </table>
                $internalCostingAppendix
                ${bankAndTermsFooterHtml()}
              </div>
            """.trimIndent()

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

    /**
     * Generates a genuine multi-page A4 PDF file in `cacheDir/proposals/` via `android.graphics.pdf.PdfDocument`
     * and opens the Android Share Sheet (pre-filled for WhatsApp + PDF attachment via FileProvider).
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
        inverterAfterBitmap: Bitmap? = null
    ): Result<String> {
        return try {
            val cleanName = customerName.ifBlank { "Valued Solar Consumer" }
            val slug = cleanName.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').ifBlank { "Consumer" }
            val proposalsDir = File(context.cacheDir, "proposals").apply { mkdirs() }
            val fileName = "NB_Proposal_${slug}_${todayIsoDate()}.pdf"
            val outFile = File(proposalsDir, fileName)

            val doc = PdfDocument()
            val pageW = 595
            val pageH = 842
            val margin = 28f

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#8E1E1B")
                textSize = 15f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val darkBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0F172A")
                textSize = 10.5f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#334155")
                textSize = 9.5f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
            val greenBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#2B4D12")
                textSize = 11f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val maroonBig = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#8E1E1B")
                textSize = 16f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val borderPaint = Paint().apply {
                color = Color.parseColor("#94A3B8")
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }
            val limeBgPaint = Paint().apply {
                color = Color.parseColor("#F1F8E9")
                style = Paint.Style.FILL
            }
            val maroonBgPaint = Paint().apply {
                color = Color.parseColor("#8E1E1B")
                style = Paint.Style.FILL
            }

            // PAGE 1: Official Letterhead, Customer Info, Real Costing & Bank Footer
            val page1 = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, 1).create())
            val c1: Canvas = page1.canvas
            c1.drawColor(Color.WHITE)
            c1.drawRect(margin, margin, pageW - margin, pageH - margin, borderPaint)

            // Circular NB Logo
            val logoBmp = runCatching {
                BitmapFactory.decodeResource(context.resources, R.drawable.nb_solar_icon_fg)
            }.getOrNull()
            if (logoBmp != null) {
                val dst = RectF(margin + 10f, margin + 10f, margin + 52f, margin + 52f)
                c1.drawBitmap(logoBmp, null, dst, null)
            }

            c1.drawText(COMPANY_NAME, margin + 62f, margin + 26f, titlePaint)
            c1.drawText(COMPANY_ADDRESS.take(78), margin + 62f, margin + 40f, bodyPaint)
            c1.drawText("GSTIN: $COMPANY_GSTIN | Tel: $COMPANY_TEL | $COMPANY_EMAIL", margin + 62f, margin + 52f, darkBold)

            // Maroon header divider + Format badge
            c1.drawRect(margin, margin + 62f, pageW - margin, margin + 84f, maroonBgPaint)
            val whiteBold = Paint(darkBold).apply { color = Color.WHITE; textSize = 10.5f }
            c1.drawText("${format.shortTitle.uppercase(Locale.US)} — ${breakdown.capacityKw} kW ${breakdown.systemType.label}", margin + 10f, margin + 77f, whiteBold)

            var y = margin + 102f
            c1.drawText("Prepared For: $cleanName   |   Mobile: ${customerMobile.ifBlank { "-" }}   |   K-No: ${consumerNo.ifBlank { "-" }}", margin + 12f, y, darkBold)
            y += 15f
            c1.drawText("Site: ${siteLocation.ifBlank { "Udaipur, Rajasthan" }}   |   Date: ${todayIsoDate()}   |   Structure: ${breakdown.structureType.label}", margin + 12f, y, bodyPaint)
            y += 18f

            // Highlight "Party Ko Kitne Ka Padega" box
            c1.drawRect(margin + 10f, y, pageW - margin - 10f, y + 76f, limeBgPaint)
            c1.drawRect(margin + 10f, y, pageW - margin - 10f, y + 76f, borderPaint)
            c1.drawText("CUSTOMER NET COST SUMMARY ('PARTY KO KITNE KA PADEGA')", margin + 20f, y + 18f, greenBold)
            c1.drawText("Gross Turnkey Price (Incl. GST): ${formatInr(breakdown.grossQuotedPriceInr)}   |   PM Surya Ghar Subsidy: - ${formatInr(breakdown.pmSuryaGharSubsidyInr)}", margin + 20f, y + 36f, bodyPaint)
            c1.drawText("Final Net Cost After Subsidy: ${formatInr(breakdown.finalNetCostAfterSubsidyInr)}", margin + 20f, y + 58f, maroonBig)
            y += 92f

            // Technical BoM & Survey Grid
            c1.drawText("SURVEY, WIRING & SYSTEM SPECIFICATIONS", margin + 12f, y, titlePaint.apply { textSize = 11f })
            y += 10f
            val specRows = listOf(
                "Solar PV Modules" to "${breakdown.wiring.panelCount} Nos × ${dimensions.panelWattageWp}Wp Made-in-India DCR (25-Yr Warranty)",
                "System Architecture" to "${breakdown.capacityKw} kW ${breakdown.systemType.label} (${breakdown.structureType.label})",
                "Rooftop Dimensions" to "${dimensions.roofLengthM}m × ${dimensions.roofWidthM}m (${breakdown.wiring.roofAreaSqM} m²) | Tilt ${dimensions.tiltDegrees.roundToInt()}°",
                "Solar DC Cable (4 sq.mm)" to "${breakdown.wiring.dcCableMeters} meters UV-protected Copper",
                "AC Cable & PVC Conduit" to "AC Armoured ${breakdown.wiring.acCableMeters} m | Heavy PVC Conduit ${breakdown.wiring.pvcConduitMeters} m",
                "Earthing & HDGI Structure" to "3-Pit Chemical Earthing ${breakdown.wiring.earthingWireMeters} m | HDGI ~${breakdown.wiring.estimatedStructureKg.roundToInt()} kg",
                "Est. Generation & Payback" to "~${breakdown.estimatedMonthlyUnits} Units/mo | Savings ~${formatInr(breakdown.estimatedMonthlySavingsInr)}/mo (~${breakdown.estimatedPaybackYears} Yrs)"
            )
            specRows.forEach { (k, v) ->
                c1.drawRect(margin + 10f, y, pageW - margin - 10f, y + 22f, borderPaint)
                c1.drawText(k, margin + 16f, y + 14f, darkBold)
                c1.drawText(v, margin + 175f, y + 14f, bodyPaint)
                y += 22f
            }

            y += 22f
            c1.drawText("Amount (in words): ${amountInWords(breakdown.finalNetCostAfterSubsidyInr)}", margin + 12f, y, darkBold)

            // Bank Details Footer
            val footerY = pageH - margin - 92f
            c1.drawLine(margin, footerY, pageW - margin, footerY, borderPaint)
            c1.drawText("Company Bank Details: $BANK_NAME | Branch: $BANK_BRANCH", margin + 12f, footerY + 18f, darkBold)
            c1.drawText("A/c No.: $BANK_ACCOUNT_NO | IFSC: $BANK_IFSC", margin + 12f, footerY + 33f, bodyPaint)
            c1.drawText("1. Goods once sold will not be taken back. 2. Subject to Udaipur (Raj.) Jurisdiction. E. & O.E.", margin + 12f, footerY + 50f, bodyPaint)
            c1.drawText("For $COMPANY_NAME", pageW - margin - 210f, footerY + 72f, darkBold)
            doc.finishPage(page1)

            // Optional PAGE 2 with AI Rooftop & Inverter Photos
            if (rooftopAfterBitmap != null || inverterAfterBitmap != null) {
                val page2 = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, 2).create())
                val c2 = page2.canvas
                c2.drawColor(Color.WHITE)
                c2.drawRect(margin, margin, pageW - margin, pageH - margin, borderPaint)
                c2.drawText("$COMPANY_NAME — AI SOLAR SITE PLACEMENT", margin + 14f, margin + 26f, titlePaint)
                var py = margin + 48f
                rooftopAfterBitmap?.let { bmp ->
                    c2.drawText("AI Placed Rooftop Array (${breakdown.wiring.panelCount} Panels • ${breakdown.structureType.shortLabel})", margin + 14f, py, darkBold)
                    py += 10f
                    val targetW = pageW - margin * 2 - 28f
                    val aspect = bmp.height.toFloat() / bmp.width.coerceAtLeast(1)
                    val targetH = (targetW * aspect).coerceAtMost(300f)
                    c2.drawBitmap(bmp, null, RectF(margin + 14f, py, margin + 14f + targetW, py + targetH), null)
                    py += targetH + 28f
                }
                inverterAfterBitmap?.let { bmp ->
                    c2.drawText("AI Placed Inverter, ACDB/DCDB & Conduit Wall", margin + 14f, py, darkBold)
                    py += 10f
                    val targetW = pageW - margin * 2 - 28f
                    val aspect = bmp.height.toFloat() / bmp.width.coerceAtLeast(1)
                    val targetH = (targetW * aspect).coerceAtMost(300f)
                    c2.drawBitmap(bmp, null, RectF(margin + 14f, py, margin + 14f + targetW, py + targetH), null)
                }
                doc.finishPage(page2)
            }

            FileOutputStream(outFile).use { fos ->
                doc.writeTo(fos)
            }
            doc.close()

            val pdfUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outFile
            )
            val waText = buildString {
                appendLine("*$COMPANY_NAME — Udaipur*")
                appendLine("Solar EPC Proposal for *$cleanName* (${breakdown.capacityKw} kW ${breakdown.systemType.shortLabel})")
                appendLine("Gross Price (Incl. GST): ${formatInr(breakdown.grossQuotedPriceInr)}")
                if (breakdown.pmSuryaGharSubsidyInr > 0) {
                    appendLine("PM Surya Ghar Subsidy: - ${formatInr(breakdown.pmSuryaGharSubsidyInr)}")
                }
                appendLine("*Net Cost After Subsidy: ${formatInr(breakdown.finalNetCostAfterSubsidyInr)}*")
                appendLine("Est. Generation: ~${breakdown.estimatedMonthlyUnits} Units/mo | Payback ~${breakdown.estimatedPaybackYears} Yrs")
                appendLine("Contact: $COMPANY_TEL | GSTIN: $COMPANY_GSTIN")
            }

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, pdfUri)
                putExtra(Intent.EXTRA_SUBJECT, "Solar EPC Proposal - $cleanName ($COMPANY_NAME)")
                putExtra(Intent.EXTRA_TEXT, waText)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Proposal PDF via WhatsApp"))
            Result.success(fileName)
        } catch (e: Exception) {
            Result.failure(e)
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
