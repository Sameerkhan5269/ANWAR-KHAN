package com.example.data.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Null-safe, crash-proof org.json codec for Supabase `nb_solar_store` JSONB records.
 */
object JsonCodec {

    // --- STAFF USERS ---
    fun encodeStaffUsers(list: List<StaffUser>): JSONArray = JSONArray().apply {
        list.forEach { u ->
            put(JSONObject().apply {
                put("id", u.id)
                put("loginId", u.loginId)
                put("pin", u.pin)
                put("displayName", u.displayName)
                put("role", u.role.name)
                put("isBuiltIn", u.isBuiltIn)
                put("imprestBalance", u.imprestBalance)
                put("assignedDivision", u.assignedDivision?.name ?: "")
            })
        }
    }

    fun decodeStaffUsers(arr: JSONArray?): List<StaffUser> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val loginId = o.optString("loginId").trim()
                val pin = o.optString("pin").trim()
                if (loginId.isNotEmpty() && pin.isNotEmpty()) {
                    val divRaw = o.optString("assignedDivision").trim()
                    val assignedDiv = if (divRaw.isNotEmpty()) ProjectDivision.fromString(divRaw) else null
                    add(
                        StaffUser(
                            id = o.optString("id").ifBlank { loginId },
                            loginId = loginId,
                            pin = pin,
                            displayName = o.optString("displayName").ifBlank { loginId },
                            role = StaffRole.fromString(o.optString("role")),
                            isBuiltIn = o.optBoolean("isBuiltIn", false),
                            imprestBalance = o.optDouble("imprestBalance", 0.0).safeOrZero(),
                            assignedDivision = assignedDiv
                        )
                    )
                }
            }
        }
    }

    // --- PARTIES ---
    fun encodeParties(list: List<PartyProject>): JSONArray = JSONArray().apply {
        list.forEach { p ->
            put(JSONObject().apply {
                put("id", p.id)
                put("partyName", p.partyName)
                put("mobile", p.mobile)
                put("address", p.address)
                put("cityOrLocation", p.cityOrLocation)
                put("consumerNumber", p.consumerNumber)
                put("capacityKw", p.capacityKw)
                put("division", p.division.name)
                put("leadSource", p.leadSource.name)
                put("referrerName", p.referrerName)
                put("referrerPhone", p.referrerPhone)
                put("leadDate", p.leadDate)
                put("roofStructureType", p.roofStructureType.name)
                put("quotedRatePerKw", p.quotedRatePerKw)
                put("nextFollowUpDate", p.nextFollowUpDate)
                put("siteSurveyNotes", p.siteSurveyNotes)
                put("leadStatus", p.leadStatus.name)
                put("projectValue", p.projectValue)
                put("receivedAmount", p.receivedAmount)
                put("sanctionedLoanAmount", p.sanctionedLoanAmount)
                put("loan1Amount", p.loan1Amount)
                put("loan1Received", p.loan1Received)
                put("loan1Date", p.loan1Date)
                put("loan2Amount", p.loan2Amount)
                put("loan2Received", p.loan2Received)
                put("loan2Date", p.loan2Date)
                put("installationDoneDate", p.installationDoneDate)
                put("rsebFileApplied", p.rsebFileApplied)
                put("mcoOk", p.mcoOk)
                put("meterInstalled", p.meterInstalled)
                put("subsidyReceived", p.subsidyReceived)
                put("subsidyAmount", p.subsidyAmount)
                put("partnerCommissionPct", p.partnerCommissionPct)
                put("referralPaid", p.referralPaid)
                put("referralPayoutDate", p.referralPayoutDate)
                put("createdDate", p.createdDate)
                put("stages", JSONArray().apply {
                    p.stages.forEach { s ->
                        put(JSONObject().apply {
                            put("id", s.id)
                            put("section", s.section.name)
                            put("title", s.title)
                            put("completed", s.completed)
                            put("completedAt", s.completedAt)
                            put("completedBy", s.completedBy)
                            put("remarks", s.remarks)
                        })
                    }
                })
                put("documents", JSONArray().apply {
                    p.documents.forEach { d ->
                        put(JSONObject().apply {
                            put("id", d.id)
                            put("title", d.title)
                            put("category", d.category.name)
                            put("fileName", d.fileName)
                            put("mimeType", d.mimeType)
                            put("localFilePath", d.localFilePath)
                            put("base64Thumbnail", d.base64Thumbnail)
                            put("uploadedAt", d.uploadedAt)
                            put("uploadedBy", d.uploadedBy)
                            put("notes", d.notes)
                        })
                    }
                })
                put("siteLogs", JSONArray().apply {
                    p.siteLogs.forEach { log ->
                        put(JSONObject().apply {
                            put("id", log.id)
                            put("timestamp", log.timestamp)
                            put("author", log.author)
                            put("message", log.message)
                            put("viaWhatsAppParser", log.viaWhatsAppParser)
                        })
                    }
                })
                put("sitePurchases", JSONArray().apply {
                    p.sitePurchases.forEach { sp ->
                        put(JSONObject().apply {
                            put("id", sp.id)
                            put("itemDescription", sp.itemDescription)
                            put("quantity", sp.quantity)
                            put("vendorName", sp.vendorName)
                            put("amount", sp.amount)
                            put("paymentSource", sp.paymentSource.name)
                            put("billPhotoPath", sp.billPhotoPath)
                            put("billPhotoBase64", sp.billPhotoBase64)
                            put("purchasedByLoginId", sp.purchasedByLoginId)
                            put("purchasedByName", sp.purchasedByName)
                            put("purchaseDate", sp.purchaseDate)
                            put("linkedVoucherNo", sp.linkedVoucherNo)
                        })
                    }
                })
            })
        }
    }

    fun decodeParties(arr: JSONArray?): List<PartyProject> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val name = o.optString("partyName").ifBlank { o.optString("name") }
                if (name.isBlank()) continue

                val stagesArr = o.optJSONArray("stages")
                val parsedStages = if (stagesArr != null && stagesArr.length() > 0) {
                    buildList {
                        for (j in 0 until stagesArr.length()) {
                            val so = stagesArr.optJSONObject(j) ?: continue
                            add(
                                WorkflowStage(
                                    id = so.optString("id"),
                                    section = WorkflowSection.fromString(so.optString("section")),
                                    title = so.optString("title"),
                                    completed = so.optBoolean("completed", false),
                                    completedAt = so.optString("completedAt"),
                                    completedBy = so.optString("completedBy"),
                                    remarks = so.optString("remarks")
                                )
                            )
                        }
                    }
                } else {
                    defaultWorkflowStages()
                }

                val docsArr = o.optJSONArray("documents")
                val parsedDocs = buildList {
                    if (docsArr != null) {
                        for (j in 0 until docsArr.length()) {
                            val d = docsArr.optJSONObject(j) ?: continue
                            add(
                                PartyDocument(
                                    id = d.optString("id"),
                                    title = d.optString("title"),
                                    category = DocumentCategory.fromString(d.optString("category")),
                                    fileName = d.optString("fileName"),
                                    mimeType = d.optString("mimeType", "image/jpeg"),
                                    localFilePath = d.optString("localFilePath"),
                                    base64Thumbnail = d.optString("base64Thumbnail"),
                                    uploadedAt = d.optString("uploadedAt"),
                                    uploadedBy = d.optString("uploadedBy"),
                                    notes = d.optString("notes")
                                )
                            )
                        }
                    }
                }

                val logsArr = o.optJSONArray("siteLogs")
                val parsedLogs = buildList {
                    if (logsArr != null) {
                        for (j in 0 until logsArr.length()) {
                            val l = logsArr.optJSONObject(j) ?: continue
                            add(
                                SiteLogEntry(
                                    id = l.optString("id"),
                                    timestamp = l.optString("timestamp"),
                                    author = l.optString("author"),
                                    message = l.optString("message"),
                                    viaWhatsAppParser = l.optBoolean("viaWhatsAppParser", false)
                                )
                            )
                        }
                    }
                }

                val purchasesArr = o.optJSONArray("sitePurchases")
                val parsedPurchases = buildList {
                    if (purchasesArr != null) {
                        for (j in 0 until purchasesArr.length()) {
                            val sp = purchasesArr.optJSONObject(j) ?: continue
                            val itemDesc = sp.optString("itemDescription")
                            if (itemDesc.isBlank()) continue
                            add(
                                SiteMaterialPurchase(
                                    id = sp.optString("id").ifBlank { "${i}_${j}" },
                                    itemDescription = itemDesc,
                                    quantity = sp.optString("quantity", "1"),
                                    vendorName = sp.optString("vendorName"),
                                    amount = sp.optDouble("amount", 0.0).safeOrZero(),
                                    paymentSource = SitePurchasePaymentSource.fromString(sp.optString("paymentSource")),
                                    billPhotoPath = sp.optString("billPhotoPath"),
                                    billPhotoBase64 = sp.optString("billPhotoBase64"),
                                    purchasedByLoginId = sp.optString("purchasedByLoginId"),
                                    purchasedByName = sp.optString("purchasedByName"),
                                    purchaseDate = sp.optString("purchaseDate"),
                                    linkedVoucherNo = sp.optString("linkedVoucherNo")
                                )
                            )
                        }
                    }
                }

                val created = o.optString("createdDate")
                add(
                    PartyProject(
                        id = o.optString("id").ifBlank { name },
                        partyName = name,
                        mobile = o.optString("mobile"),
                        address = o.optString("address"),
                        cityOrLocation = o.optString("cityOrLocation").ifBlank { o.optString("address") },
                        consumerNumber = o.optString("consumerNumber"),
                        capacityKw = o.optDouble("capacityKw", 3.0).safeOrZero(),
                        division = ProjectDivision.fromString(o.optString("division")),
                        leadSource = LeadSource.fromString(o.optString("leadSource")),
                        referrerName = o.optString("referrerName"),
                        referrerPhone = o.optString("referrerPhone"),
                        leadDate = o.optString("leadDate").ifBlank { created },
                        roofStructureType = RoofStructureType.fromString(o.optString("roofStructureType")),
                        quotedRatePerKw = o.optDouble("quotedRatePerKw", 0.0).safeOrZero(),
                        nextFollowUpDate = o.optString("nextFollowUpDate"),
                        siteSurveyNotes = o.optString("siteSurveyNotes"),
                        leadStatus = LeadStatus.fromString(o.optString("leadStatus")),
                        projectValue = o.optDouble("projectValue", 0.0).safeOrZero(),
                        receivedAmount = o.optDouble("receivedAmount", 0.0).safeOrZero(),
                        sanctionedLoanAmount = o.optDouble("sanctionedLoanAmount", 0.0).safeOrZero(),
                        loan1Amount = o.optDouble("loan1Amount", 0.0).safeOrZero(),
                        loan1Received = o.optBoolean("loan1Received", false),
                        loan1Date = o.optString("loan1Date"),
                        loan2Amount = o.optDouble("loan2Amount", 0.0).safeOrZero(),
                        loan2Received = o.optBoolean("loan2Received", false),
                        loan2Date = o.optString("loan2Date"),
                        installationDoneDate = o.optString("installationDoneDate"),
                        rsebFileApplied = o.optBoolean("rsebFileApplied", false),
                        mcoOk = o.optBoolean("mcoOk", false),
                        meterInstalled = o.optBoolean("meterInstalled", false),
                        subsidyReceived = o.optBoolean("subsidyReceived", false),
                        subsidyAmount = o.optDouble("subsidyAmount", 0.0).safeOrZero(),
                        partnerCommissionPct = o.optDouble("partnerCommissionPct", 0.0).safeOrZero(),
                        referralPaid = o.optBoolean("referralPaid", false),
                        referralPayoutDate = o.optString("referralPayoutDate"),
                        createdDate = created,
                        stages = parsedStages,
                        documents = parsedDocs,
                        siteLogs = parsedLogs,
                        sitePurchases = parsedPurchases
                    )
                )
            }
        }
    }

    // --- DCR PANELS ---
    fun encodeDcrPanels(list: List<DcrPanel>): JSONArray = JSONArray().apply {
        list.forEach { p ->
            put(JSONObject().apply {
                put("id", p.id)
                put("serialNumber", p.serialNumber)
                put("brand", p.brand)
                put("wattage", p.wattage)
                put("supplierName", p.supplierName)
                put("purchaseInvoiceNo", p.purchaseInvoiceNo)
                put("status", p.status.name)
                put("linkedPartyId", p.linkedPartyId)
                put("linkedPartyName", p.linkedPartyName)
                put("addedDate", p.addedDate)
                put("unitCost", p.unitCost)
            })
        }
    }

    fun decodeDcrPanels(arr: JSONArray?): List<DcrPanel> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val serial = o.optString("serialNumber")
                if (serial.isBlank()) continue
                add(
                    DcrPanel(
                        id = o.optString("id").ifBlank { serial },
                        serialNumber = serial,
                        brand = o.optString("brand", "Waaree"),
                        wattage = o.optInt("wattage", 540),
                        supplierName = o.optString("supplierName"),
                        purchaseInvoiceNo = o.optString("purchaseInvoiceNo"),
                        status = PanelStatus.fromString(o.optString("status")),
                        linkedPartyId = o.optString("linkedPartyId"),
                        linkedPartyName = o.optString("linkedPartyName"),
                        addedDate = o.optString("addedDate"),
                        unitCost = o.optDouble("unitCost", 12200.0).safeOrZero()
                    )
                )
            }
        }
    }

    // --- SOLAR METERS ---
    fun encodeSolarMeters(list: List<SolarMeter>): JSONArray = JSONArray().apply {
        list.forEach { m ->
            put(JSONObject().apply {
                put("id", m.id)
                put("serialNumber", m.serialNumber)
                put("meterType", m.meterType.name)
                put("makeAndPhase", m.makeAndPhase)
                put("testingStatus", m.testingStatus.name)
                put("installedPartyId", m.installedPartyId)
                put("installedPartyName", m.installedPartyName)
                put("unitCost", m.unitCost)
            })
        }
    }

    fun decodeSolarMeters(arr: JSONArray?): List<SolarMeter> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val serial = o.optString("serialNumber")
                if (serial.isBlank()) continue
                add(
                    SolarMeter(
                        id = o.optString("id").ifBlank { serial },
                        serialNumber = serial,
                        meterType = MeterType.fromString(o.optString("meterType")),
                        makeAndPhase = o.optString("makeAndPhase", "L&T / 1-Phase"),
                        testingStatus = DiscomTestingStatus.fromString(o.optString("testingStatus")),
                        installedPartyId = o.optString("installedPartyId"),
                        installedPartyName = o.optString("installedPartyName"),
                        unitCost = o.optDouble("unitCost", 2400.0).safeOrZero()
                    )
                )
            }
        }
    }

    // --- RAW MATERIALS ---
    fun encodeRawMaterials(list: List<RawMaterial>): JSONArray = JSONArray().apply {
        list.forEach { r ->
            put(JSONObject().apply {
                put("id", r.id)
                put("name", r.name)
                put("category", r.category)
                put("unit", r.unit)
                put("supplierName", r.supplierName)
                put("purchasedQty", r.purchasedQty)
                put("usedQty", r.usedQty)
                put("minStockAlert", r.minStockAlert)
                put("unitCost", r.unitCost)
                put("issueLogs", JSONArray().apply {
                    r.issueLogs.forEach { log ->
                        put(JSONObject().apply {
                            put("id", log.id)
                            put("partyId", log.partyId)
                            put("partyName", log.partyName)
                            put("quantity", log.quantity)
                            put("date", log.date)
                            put("issuedBy", log.issuedBy)
                        })
                    }
                })
            })
        }
    }

    fun decodeRawMaterials(arr: JSONArray?): List<RawMaterial> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val name = o.optString("name")
                if (name.isBlank()) continue
                val logsArr = o.optJSONArray("issueLogs")
                val logs = buildList {
                    if (logsArr != null) {
                        for (j in 0 until logsArr.length()) {
                            val l = logsArr.optJSONObject(j) ?: continue
                            add(
                                MaterialIssueLog(
                                    id = l.optString("id"),
                                    partyId = l.optString("partyId"),
                                    partyName = l.optString("partyName"),
                                    quantity = l.optDouble("quantity", 0.0).safeOrZero(),
                                    date = l.optString("date"),
                                    issuedBy = l.optString("issuedBy")
                                )
                            )
                        }
                    }
                }
                add(
                    RawMaterial(
                        id = o.optString("id").ifBlank { name },
                        name = name,
                        category = o.optString("category", "BOS"),
                        unit = o.optString("unit", "Nos"),
                        supplierName = o.optString("supplierName"),
                        purchasedQty = o.optDouble("purchasedQty", 0.0).safeOrZero(),
                        usedQty = o.optDouble("usedQty", 0.0).safeOrZero(),
                        minStockAlert = o.optDouble("minStockAlert", 10.0).safeOrZero(),
                        unitCost = o.optDouble("unitCost", 0.0).safeOrZero(),
                        issueLogs = logs
                    )
                )
            }
        }
    }

    // --- LEKHA JOKHA (SITE KHARCHA) ---
    fun encodeSiteKharcha(list: List<SiteKharchaEntry>): JSONArray = JSONArray().apply {
        list.forEach { k ->
            put(JSONObject().apply {
                put("id", k.id)
                put("partyId", k.partyId)
                put("partyName", k.partyName)
                put("category", k.category.name)
                put("workerOrTitle", k.workerOrTitle)
                put("amount", k.amount)
                put("date", k.date)
                put("note", k.note)
                put("loggedBy", k.loggedBy)
                put("linkedVoucherNo", k.linkedVoucherNo)
            })
        }
    }

    fun decodeSiteKharcha(arr: JSONArray?): List<SiteKharchaEntry> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val title = o.optString("workerOrTitle")
                if (title.isBlank()) continue
                add(
                    SiteKharchaEntry(
                        id = o.optString("id").ifBlank { "kh_$i" },
                        partyId = o.optString("partyId"),
                        partyName = o.optString("partyName"),
                        category = KharchaCategory.fromString(o.optString("category")),
                        workerOrTitle = title,
                        amount = o.optDouble("amount", 0.0).safeOrZero(),
                        date = o.optString("date"),
                        note = o.optString("note"),
                        loggedBy = o.optString("loggedBy"),
                        linkedVoucherNo = o.optString("linkedVoucherNo")
                    )
                )
            }
        }
    }

    // --- TEAM CHAT ---
    fun encodeTeamChat(list: List<TeamChatMessage>): JSONArray = JSONArray().apply {
        list.forEach { m ->
            put(JSONObject().apply {
                put("id", m.id)
                put("senderLoginId", m.senderLoginId)
                put("senderName", m.senderName)
                put("senderRole", m.senderRole.name)
                put("text", m.text)
                put("videoPath", m.videoPath)
                put("videoLabel", m.videoLabel)
                put("timestamp", m.timestamp)
                put("division", m.division?.name ?: "")
            })
        }
    }

    fun decodeTeamChat(arr: JSONArray?): List<TeamChatMessage> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val sender = o.optString("senderLoginId")
                if (sender.isBlank()) continue
                val divRaw = o.optString("division").trim()
                val div = if (divRaw.isNotEmpty()) ProjectDivision.fromString(divRaw) else null
                add(
                    TeamChatMessage(
                        id = o.optString("id").ifBlank { "msg_$i" },
                        senderLoginId = sender,
                        senderName = o.optString("senderName").ifBlank { sender },
                        senderRole = StaffRole.fromString(o.optString("senderRole")),
                        text = o.optString("text"),
                        videoPath = o.optString("videoPath"),
                        videoLabel = o.optString("videoLabel"),
                        timestamp = o.optString("timestamp"),
                        division = div
                    )
                )
            }
        }
    }

    // --- BUSY ACCOUNTS ---
    fun encodeBusyAccounts(list: List<BusyAccount>): JSONArray = JSONArray().apply {
        list.forEach { a ->
            put(JSONObject().apply {
                put("id", a.id)
                put("name", a.name)
                put("group", a.group.name)
                put("openingBalance", a.openingBalance)
                put("gstin", a.gstin)
                put("mobile", a.mobile)
                put("linkedPartyId", a.linkedPartyId)
                put("defaultCostCenter", a.defaultCostCenter.name)
            })
        }
    }

    fun decodeBusyAccounts(arr: JSONArray?): List<BusyAccount> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val name = o.optString("name")
                if (name.isBlank()) continue
                add(
                    BusyAccount(
                        id = o.optString("id").ifBlank { name },
                        name = name,
                        group = BusyAccountGroup.fromString(o.optString("group")),
                        openingBalance = o.optDouble("openingBalance", 0.0).safeOrZero(),
                        gstin = o.optString("gstin"),
                        mobile = o.optString("mobile"),
                        linkedPartyId = o.optString("linkedPartyId"),
                        defaultCostCenter = LeadSource.fromString(o.optString("defaultCostCenter"))
                    )
                )
            }
        }
    }

    // --- BUSY ITEMS ---
    fun encodeBusyItems(list: List<BusyItem>): JSONArray = JSONArray().apply {
        list.forEach { item ->
            put(JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("hsnCode", item.hsnCode)
                put("unit", item.unit)
                put("gstRatePercent", item.gstRatePercent)
                put("defaultSaleRate", item.defaultSaleRate)
                put("defaultPurchaseRate", item.defaultPurchaseRate)
                put("stockQty", item.stockQty)
            })
        }
    }

    fun decodeBusyItems(arr: JSONArray?): List<BusyItem> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val name = o.optString("name")
                if (name.isBlank()) continue
                add(
                    BusyItem(
                        id = o.optString("id").ifBlank { name },
                        name = name,
                        hsnCode = o.optString("hsnCode", "854140"),
                        unit = o.optString("unit", "Nos"),
                        gstRatePercent = o.optDouble("gstRatePercent", 5.0).safeOrZero(),
                        defaultSaleRate = o.optDouble("defaultSaleRate", 0.0).safeOrZero(),
                        defaultPurchaseRate = o.optDouble("defaultPurchaseRate", 0.0).safeOrZero(),
                        stockQty = o.optDouble("stockQty", 0.0).safeOrZero()
                    )
                )
            }
        }
    }

    // --- BUSY VOUCHERS ---
    fun encodeBusyVouchers(list: List<BusyVoucher>): JSONArray = JSONArray().apply {
        list.forEach { v ->
            put(JSONObject().apply {
                put("id", v.id)
                put("type", v.type.name)
                put("voucherNo", v.voucherNo)
                put("date", v.date)
                put("partyAccountId", v.partyAccountId)
                put("partyAccountName", v.partyAccountName)
                put("contraAccountId", v.contraAccountId)
                put("contraAccountName", v.contraAccountName)
                put("costCenter", v.costCenter.name)
                put("isInterStateIgst", v.isInterStateIgst)
                put("taxableAmount", v.taxableAmount)
                put("cgstAmount", v.cgstAmount)
                put("sgstAmount", v.sgstAmount)
                put("igstAmount", v.igstAmount)
                put("totalAmount", v.totalAmount)
                put("billRefType", v.billRefType.name)
                put("billRefNo", v.billRefNo)
                put("utrOrChequeNo", v.utrOrChequeNo)
                put("paidBy", v.paidBy)
                put("narration", v.narration)
                put("partyGstin", v.partyGstin)
                put("createdBy", v.createdBy)
                put("lineItems", JSONArray().apply {
                    v.lineItems.forEach { li ->
                        put(JSONObject().apply {
                            put("itemId", li.itemId)
                            put("itemName", li.itemName)
                            put("hsnCode", li.hsnCode)
                            put("quantity", li.quantity)
                            put("unit", li.unit)
                            put("rate", li.rate)
                            put("gstRatePercent", li.gstRatePercent)
                        })
                    }
                })
            })
        }
    }

    fun decodeBusyVouchers(arr: JSONArray?): List<BusyVoucher> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val vNo = o.optString("voucherNo")
                if (vNo.isBlank()) continue
                val liArr = o.optJSONArray("lineItems")
                val items = buildList {
                    if (liArr != null) {
                        for (j in 0 until liArr.length()) {
                            val li = liArr.optJSONObject(j) ?: continue
                            add(
                                BusyLineItem(
                                    itemId = li.optString("itemId"),
                                    itemName = li.optString("itemName"),
                                    hsnCode = li.optString("hsnCode", "854140"),
                                    quantity = li.optDouble("quantity", 1.0).safeOrZero(),
                                    unit = li.optString("unit", "Nos"),
                                    rate = li.optDouble("rate", 0.0).safeOrZero(),
                                    gstRatePercent = li.optDouble("gstRatePercent", 5.0).safeOrZero()
                                )
                            )
                        }
                    }
                }
                add(
                    BusyVoucher(
                        id = o.optString("id").ifBlank { vNo },
                        type = BusyVoucherType.fromString(o.optString("type")),
                        voucherNo = vNo,
                        date = o.optString("date"),
                        partyAccountId = o.optString("partyAccountId"),
                        partyAccountName = o.optString("partyAccountName"),
                        contraAccountId = o.optString("contraAccountId"),
                        contraAccountName = o.optString("contraAccountName"),
                        costCenter = LeadSource.fromString(o.optString("costCenter")),
                        isInterStateIgst = o.optBoolean("isInterStateIgst", false),
                        taxableAmount = o.optDouble("taxableAmount", 0.0).safeOrZero(),
                        cgstAmount = o.optDouble("cgstAmount", 0.0).safeOrZero(),
                        sgstAmount = o.optDouble("sgstAmount", 0.0).safeOrZero(),
                        igstAmount = o.optDouble("igstAmount", 0.0).safeOrZero(),
                        totalAmount = o.optDouble("totalAmount", 0.0).safeOrZero(),
                        billRefType = BillRefType.fromString(o.optString("billRefType")),
                        billRefNo = o.optString("billRefNo"),
                        utrOrChequeNo = o.optString("utrOrChequeNo"),
                        paidBy = o.optString("paidBy"),
                        narration = o.optString("narration"),
                        lineItems = items,
                        partyGstin = o.optString("partyGstin"),
                        createdBy = o.optString("createdBy")
                    )
                )
            }
        }
    }

    // --- LOANS & MCO TRACKING (`nb_loans_mco`) ---
    fun encodeLoanMcoRecords(list: List<LoanMcoRecord>): JSONArray = JSONArray().apply {
        list.forEach { rec ->
            put(JSONObject().apply {
                put("partyId", rec.partyId)
                put("sanctionedLoanAmount", rec.sanctionedLoanAmount)
                put("loan1Amount", rec.loan1Amount)
                put("loan1Received", rec.loan1Received)
                put("loan1Date", rec.loan1Date)
                put("loan2Amount", rec.loan2Amount)
                put("loan2Received", rec.loan2Received)
                put("loan2Date", rec.loan2Date)
                put("installationDoneDate", rec.installationDoneDate)
                put("rsebFileApplied", rec.rsebFileApplied)
                put("mcoOk", rec.mcoOk)
                put("meterInstalled", rec.meterInstalled)
                put("subsidyReceived", rec.subsidyReceived)
                put("subsidyAmount", rec.subsidyAmount)
            })
        }
    }

    fun decodeLoanMcoRecords(arr: JSONArray?): List<LoanMcoRecord> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val partyId = o.optString("partyId").trim()
                if (partyId.isBlank()) continue
                add(
                    LoanMcoRecord(
                        partyId = partyId,
                        sanctionedLoanAmount = o.optDouble("sanctionedLoanAmount", 0.0).safeOrZero(),
                        loan1Amount = o.optDouble("loan1Amount", 0.0).safeOrZero(),
                        loan1Received = o.optBoolean("loan1Received", false),
                        loan1Date = o.optString("loan1Date"),
                        loan2Amount = o.optDouble("loan2Amount", 0.0).safeOrZero(),
                        loan2Received = o.optBoolean("loan2Received", false),
                        loan2Date = o.optString("loan2Date"),
                        installationDoneDate = o.optString("installationDoneDate"),
                        rsebFileApplied = o.optBoolean("rsebFileApplied", false),
                        mcoOk = o.optBoolean("mcoOk", false),
                        meterInstalled = o.optBoolean("meterInstalled", false),
                        subsidyReceived = o.optBoolean("subsidyReceived", false),
                        subsidyAmount = o.optDouble("subsidyAmount", 0.0).safeOrZero()
                    )
                )
            }
        }
    }

    fun extractLoanMcoRecords(parties: List<PartyProject>): List<LoanMcoRecord> =
        parties.map { p ->
            LoanMcoRecord(
                partyId = p.id,
                sanctionedLoanAmount = p.sanctionedLoanAmount,
                loan1Amount = p.loan1Amount,
                loan1Received = p.isLoan1Done,
                loan1Date = p.loan1Date,
                loan2Amount = p.loan2Amount,
                loan2Received = p.isLoan2Done,
                loan2Date = p.loan2Date,
                installationDoneDate = p.installationDoneDate,
                rsebFileApplied = p.isRsebFileApplied,
                mcoOk = p.isMcoOk,
                meterInstalled = p.isMeterInstalled,
                subsidyReceived = p.isSubsidyReceived,
                subsidyAmount = p.subsidyAmount
            )
        }

    fun mergeLoanMcoOverlay(
        parties: List<PartyProject>,
        records: List<LoanMcoRecord>
    ): List<PartyProject> {
        if (records.isEmpty()) return parties
        val byPartyId = records.associateBy { it.partyId }
        return parties.map { p ->
            val rec = byPartyId[p.id] ?: return@map p
            val syncedStages = p.stages.map { st ->
                val targetDone = when (st.id) {
                    "LEAD_FILE_3" -> rec.rsebFileApplied
                    "LOAN_2" -> rec.loan1Received
                    "LOAN_3" -> rec.loan2Received
                    "INSTALLATION_3" -> rec.installationDoneDate.isNotBlank() || st.completed
                    "NET_METERING_2" -> rec.meterInstalled
                    "NET_METERING_3" -> rec.mcoOk
                    "SUBSIDY_CLOSE_2" -> rec.subsidyReceived
                    else -> st.completed
                }
                if (st.completed == targetDone) st else st.copy(completed = targetDone)
            }
            p.copy(
                sanctionedLoanAmount = rec.sanctionedLoanAmount,
                loan1Amount = rec.loan1Amount,
                loan1Received = rec.loan1Received,
                loan1Date = rec.loan1Date,
                loan2Amount = rec.loan2Amount,
                loan2Received = rec.loan2Received,
                loan2Date = rec.loan2Date,
                installationDoneDate = rec.installationDoneDate,
                rsebFileApplied = rec.rsebFileApplied,
                mcoOk = rec.mcoOk,
                meterInstalled = rec.meterInstalled,
                subsidyReceived = rec.subsidyReceived,
                subsidyAmount = rec.subsidyAmount,
                stages = syncedStages
            )
        }
    }

    // --- BUNDLED MODULE PAYLOADS (`nb_inventory`, `nb_busy_vouchers`, `nb_security_config`) ---
    fun encodeInventoryBundle(
        dcrPanels: List<DcrPanel>,
        solarMeters: List<SolarMeter>,
        rawMaterials: List<RawMaterial>
    ): JSONObject = JSONObject().apply {
        put("dcrPanels", encodeDcrPanels(dcrPanels))
        put("solarMeters", encodeSolarMeters(solarMeters))
        put("rawMaterials", encodeRawMaterials(rawMaterials))
    }

    fun decodeInventoryBundle(
        obj: JSONObject
    ): Triple<List<DcrPanel>, List<SolarMeter>, List<RawMaterial>> = Triple(
        decodeDcrPanels(obj.optJSONArray("dcrPanels")),
        decodeSolarMeters(obj.optJSONArray("solarMeters")),
        decodeRawMaterials(obj.optJSONArray("rawMaterials"))
    )

    data class BusyVoucherBundle(
        val accounts: List<BusyAccount>,
        val items: List<BusyItem>,
        val vouchers: List<BusyVoucher>,
        val siteKharcha: List<SiteKharchaEntry>
    )

    fun encodeBusyVouchersBundle(
        accounts: List<BusyAccount>,
        items: List<BusyItem>,
        vouchers: List<BusyVoucher>,
        siteKharcha: List<SiteKharchaEntry>
    ): JSONObject = JSONObject().apply {
        put("busyAccounts", encodeBusyAccounts(accounts))
        put("busyItems", encodeBusyItems(items))
        put("busyVouchers", encodeBusyVouchers(vouchers))
        put("siteKharcha", encodeSiteKharcha(siteKharcha))
    }

    fun decodeBusyVouchersBundle(obj: JSONObject): BusyVoucherBundle = BusyVoucherBundle(
        accounts = decodeBusyAccounts(obj.optJSONArray("busyAccounts")),
        items = decodeBusyItems(obj.optJSONArray("busyItems")),
        vouchers = decodeBusyVouchers(obj.optJSONArray("busyVouchers")),
        siteKharcha = decodeSiteKharcha(obj.optJSONArray("siteKharcha"))
    )

    fun encodeSecurityConfig(staffUsers: List<StaffUser>): JSONObject = JSONObject().apply {
        put("staffUsers", encodeStaffUsers(staffUsers))
    }

    fun decodeSecurityConfig(obj: JSONObject): List<StaffUser> =
        decodeStaffUsers(obj.optJSONArray("staffUsers"))

    private fun Double.safeOrZero(): Double = if (this.isNaN() || this.isInfinite()) 0.0 else this
}
