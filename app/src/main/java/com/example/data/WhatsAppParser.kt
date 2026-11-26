package com.example.data

import com.example.data.model.ParsedWhatsAppUpdate
import com.example.data.model.PartyProject

/**
 * Parses informal company WhatsApp group updates (e.g. "Ramesh Sharma 5kW installation done",
 * "Meera Devi AVVNL meter tested and net meter installed") and maps them to exact parties
 * and workflow checkpoints.
 */
object WhatsAppParser {

    private val kwRegex = Regex("""(\d+(?:\.\d+)?)\s*k\s*w""", RegexOption.IGNORE_CASE)

    fun parse(rawMessage: String, parties: List<PartyProject>): ParsedWhatsAppUpdate {
        val text = rawMessage.trim()
        val lower = text.lowercase()

        // 1. Detect kW capacity if present
        val kwMatch = kwRegex.find(text)
        val detectedKw = kwMatch?.groupValues?.getOrNull(1)?.toDoubleOrNull()

        // 2. Match existing party by full name or best token overlap
        val matchedParty = findBestMatchingParty(lower, parties)

        // 3. Detect workflow milestones mentioned in message
        val stagesToComplete = mutableListOf<String>()

        if (lower.contains("survey")) stagesToComplete.add("LEAD_FILE_1")
        if (lower.contains("feasibility") || lower.contains("tf ")) stagesToComplete.add("LEAD_FILE_2")
        if (lower.contains("discom") || lower.contains("file submit") || lower.contains("application submit")) {
            stagesToComplete.add("LEAD_FILE_3")
        }

        if (lower.contains("loan apply") || lower.contains("bank application") || lower.contains("loan file")) {
            stagesToComplete.add("LOAN_1")
        }
        if (lower.contains("1st tranche") || lower.contains("first tranche") || lower.contains("loan 1")) {
            stagesToComplete.add("LOAN_2")
        }
        if (lower.contains("2nd tranche") || lower.contains("second tranche") || lower.contains("loan 2")) {
            stagesToComplete.add("LOAN_3")
        }

        if (lower.contains("structure") || lower.contains("gi ") || lower.contains("mounting structure")) {
            stagesToComplete.add("INSTALLATION_1")
        }
        if (lower.contains("panel") || lower.contains("inverter") || lower.contains("mounting")) {
            stagesToComplete.add("INSTALLATION_2")
        }
        if (lower.contains("earthing") || lower.contains("geotag") || lower.contains("geo tag")) {
            stagesToComplete.add("INSTALLATION_3")
        }
        if (lower.contains("installation done") || lower.contains("install done") || lower.contains("installation complete")) {
            stagesToComplete.addAll(listOf("INSTALLATION_1", "INSTALLATION_2", "INSTALLATION_3"))
        }

        if (lower.contains("meter test") || lower.contains("avvnl test") || lower.contains("testing done")) {
            stagesToComplete.add("NET_METERING_1")
        }
        if (lower.contains("net meter") || lower.contains("bi-directional") || lower.contains("meter install")) {
            stagesToComplete.add("NET_METERING_2")
        }
        if (lower.contains("commission") || lower.contains("plant on") || lower.contains("grid sync")) {
            stagesToComplete.add("NET_METERING_3")
        }

        if (lower.contains("subsidy claim") || lower.contains("surya ghar") || lower.contains("subsidy applied")) {
            stagesToComplete.add("SUBSIDY_CLOSE_1")
        }
        if (lower.contains("subsidy received") || lower.contains("subsidy disbursed") || lower.contains("subsidy credited")) {
            stagesToComplete.add("SUBSIDY_CLOSE_2")
        }
        if (lower.contains("handover") || lower.contains("account close") || lower.contains("closed")) {
            stagesToComplete.add("SUBSIDY_CLOSE_3")
        }

        val distinctStages = stagesToComplete.distinct()

        // Guess party name if not matched yet (take first 2 capitalized words before numbers/keywords)
        val guessedName = matchedParty?.partyName ?: guessPartyName(text)

        val summary = buildString {
            append(text)
            if (distinctStages.isNotEmpty()) {
                append(" [Auto-tagged ${distinctStages.size} stage(s)]")
            }
        }

        return ParsedWhatsAppUpdate(
            rawMessage = text,
            matchedPartyId = matchedParty?.id,
            matchedPartyName = guessedName,
            detectedKw = detectedKw,
            stagesToComplete = distinctStages,
            summaryNote = summary
        )
    }

    private fun findBestMatchingParty(lowerText: String, parties: List<PartyProject>): PartyProject? {
        // Direct full name contains
        parties.firstOrNull { p ->
            p.partyName.isNotBlank() && lowerText.contains(p.partyName.lowercase())
        }?.let { return it }

        // Token score match
        var best: PartyProject? = null
        var bestScore = 0
        for (p in parties) {
            val tokens = p.partyName.lowercase()
                .split(Regex("""\s+"""))
                .filter { it.length >= 3 }
            val hits = tokens.count { tok -> lowerText.contains(tok) }
            if (hits > bestScore) {
                bestScore = hits
                best = p
            }
        }
        return if (bestScore >= 1) best else null
    }

    private fun guessPartyName(text: String): String {
        val cleaned = text.replace(kwRegex, " ")
            .split(Regex("""[\s,:\-]+"""))
            .filter { token ->
                token.length >= 2 &&
                    token.all { it.isLetter() } &&
                    token.lowercase() !in setOf(
                        "installation", "done", "complete", "completed", "net", "meter",
                        "avvnl", "structure", "panel", "inverter", "earthing", "geotag",
                        "subsidy", "loan", "survey", "discom", "file", "submitted", "site", "update"
                    )
            }
        return cleaned.take(2).joinToString(" ").ifBlank { "New Site Party" }
    }
}
