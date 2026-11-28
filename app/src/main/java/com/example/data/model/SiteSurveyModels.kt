package com.example.data.model

enum class SolarSystemType(
    val label: String,
    val shortLabel: String,
    val isSubsidyEligible: Boolean
) {
    ON_GRID_DCR("On-Grid DCR", "On-Grid DCR", true),
    HYBRID_LIFEPO4("Hybrid with LiFePO4 Battery", "Hybrid LiFePO4", true),
    OFF_GRID("Off-Grid", "Off-Grid", false)
}

enum class CostingStructureType(
    val label: String,
    val shortLabel: String,
    val defaultRatePerKw: Double,
    val clearanceMeters: Float,
    val defaultTiltDeg: Float,
    val toRoofType: RoofStructureType
) {
    STANDARD_RCC(
        label = "Standard RCC Low-Rise",
        shortLabel = "Standard RCC",
        defaultRatePerKw = 5200.0,
        clearanceMeters = 0.9f,
        defaultTiltDeg = 24f,
        toRoofType = RoofStructureType.RCC_ROOF
    ),
    ELEVATED_GAZEBO(
        label = "Elevated High-Rise Gazebo/Pergola",
        shortLabel = "Elevated Gazebo",
        defaultRatePerKw = 9800.0,
        clearanceMeters = 2.75f,
        defaultTiltDeg = 20f,
        toRoofType = RoofStructureType.ELEVATED_GAZEBO
    ),
    TIN_SHED(
        label = "Tin Shed",
        shortLabel = "Tin Shed",
        defaultRatePerKw = 3400.0,
        clearanceMeters = 0.35f,
        defaultTiltDeg = 12f,
        toRoofType = RoofStructureType.TIN_SHED
    );

    companion object {
        fun fromRoofType(roofType: RoofStructureType): CostingStructureType = when (roofType) {
            RoofStructureType.RCC_ROOF -> STANDARD_RCC
            RoofStructureType.ELEVATED_GAZEBO -> ELEVATED_GAZEBO
            RoofStructureType.TIN_SHED -> TIN_SHED
        }
    }
}

data class SiteSurveyDimensions(
    val roofLengthM: Double = 9.0,
    val roofWidthM: Double = 6.0,
    val panelWattageWp: Int = 540,
    val inverterDistanceM: Double = 12.0,
    val earthingDistanceM: Double = 15.0,
    val tiltDegrees: Float = 24f,
    val azimuthDegrees: Float = 180f,
    val latitude: Double = 24.5854,
    val longitude: Double = 73.7125,
    val shadingNotes: String = "Clear south-facing unobstructed roof (Udaipur Zone)"
)

data class WiringMeasurementResult(
    val panelCount: Int,
    val roofAreaSqM: Double,
    val requiredPanelAreaSqM: Double,
    val fitsOnRoof: Boolean,
    val dcCableMeters: Double,
    val acCableMeters: Double,
    val earthingWireMeters: Double,
    val pvcConduitMeters: Double,
    val estimatedStructureKg: Double
)

data class RealCostingRateConfig(
    val dcrPanelRatePerWatt: Double = 24.5,
    val inverterRatePerKw: Double = 7200.0,
    val structureRatePerKw: Double = 5200.0,
    val dcCableRatePerMeter: Double = 44.0,
    val acCableRatePerMeter: Double = 92.0,
    val earthingRatePerMeter: Double = 65.0,
    val pvcConduitRatePerMeter: Double = 38.0,
    val acdbDcdbEarthingKitCost: Double = 7800.0,
    val batteryRatePerKwh: Double = 14500.0,
    val installationLabourPerKw: Double = 3200.0,
    val netMeteringAndDiscomFee: Double = 4500.0,
    val profitMarginPercent: Double = 18.0,
    val gstPercent: Double = 13.8,
    val sourceLabel: String = "2026 Rajasthan Default Rates"
)

data class CostingLineItem(
    val title: String,
    val spec: String,
    val landingCostInr: Double
)

data class RealCostingBreakdown(
    val capacityKw: Double,
    val systemType: SolarSystemType,
    val structureType: CostingStructureType,
    val wiring: WiringMeasurementResult,
    val lineItems: List<CostingLineItem>,
    val totalLandingCostInr: Double,
    val profitMarginInr: Double,
    val profitMarginPct: Double,
    val taxableQuotedPriceInr: Double,
    val gstAmountInr: Double,
    val grossQuotedPriceInr: Double,
    val pmSuryaGharSubsidyInr: Double,
    val finalNetCostAfterSubsidyInr: Double,
    val estimatedMonthlyUnits: Int,
    val estimatedMonthlySavingsInr: Double,
    val estimatedPaybackYears: Double
)

enum class ProposalModuleType(
    val label: String,
    val shortLabel: String,
    val isDcrSubsidy: Boolean
) {
    DCR_WITH_SUBSIDY("DCR - With Subsidy", "DCR (Subsidy)", true),
    NON_DCR_WITHOUT_SUBSIDY("Non-DCR - Without Subsidy", "Non-DCR (No Subsidy)", false)
}

data class ProposalExtraWorkRow(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val amountInr: Double
)

data class DetailedProposalOptions(
    val moduleType: ProposalModuleType = ProposalModuleType.DCR_WITH_SUBSIDY,
    val capacityKwp: Double = 3.0,
    val baseSystemPriceInr: Double = 0.0,
    val subsidyAmountInr: Double = 78_000.0,
    val extraWorkRows: List<ProposalExtraWorkRow> = emptyList(),
    val panelMake: String = "ADANI / WAAREE / LUMINOUS",
    val inverterMake: String = "WAAREE / K-SOLAR / SUNGROW",
    val paymentTerms: String = "70% Advance, 20% before Material Dispatch, 10% after Installation",
    val proposalRefNo: String = "",
    val validityDays: Int = 15
)

data class DetailedProposalPricingSummary(
    val baseSystemPriceInr: Double,
    val extraWorkTotalInr: Double,
    val grossTotalWithExtrasInr: Double,
    val subsidyAmountInr: Double,
    val netEffectiveCostInr: Double,
    val paybackYears: Double
)

enum class ProposalFormat(
    val title: String,
    val shortTitle: String,
    val subtitle: String,
    val pageBadge: String
) {
    PM_SURYA_GHAR_2PG(
        title = "1. Detailed 7-Page Solar Proposal – 2026 (SITC)",
        shortTitle = "7-Page 2026 SITC",
        subtitle = "Cover, About, SLD & 3D CAD, BOM Specs, Extra Work & Subsidy, Timeline & Commercial Terms",
        pageBadge = "7-PAGE 2026 SITC"
    ),
    FULL_3D_DOSSIER_4PG(
        title = "2. Full Engineering & 3D Dossier (4-Pg)",
        shortTitle = "3D Dossier (4p)",
        subtitle = "Cover, CAD Map, 3D Structure, SLD, AI Photos & System Comparison",
        pageBadge = "4-PAGE EXECUTIVE"
    ),
    COMMERCIAL_TENDER_BOQ(
        title = "3. Commercial / Wind Tender BOQ",
        shortTitle = "Tender BOQ",
        subtitle = "BUSY 21 HSN/SAC Schedule of Rates, GST Summary & 30/60/10 Milestones",
        pageBadge = "BUSY GRID BOQ"
    ),
    WHATSAPP_QUICK_QUOTE_1PG(
        title = "4. Instant WhatsApp Quick Quote (1-Pg)",
        shortTitle = "WhatsApp Quote (1p)",
        subtitle = "Compact single-page summary card for instant customer closing",
        pageBadge = "1-PAGE EXPRESS"
    )
}

data class HealthCheckItem(
    val name: String,
    val passed: Boolean,
    val detail: String
)

data class AppHealthReport(
    val ranAt: String,
    val checks: List<HealthCheckItem>
) {
    val passedCount: Int get() = checks.count { it.passed }
    val allPassed: Boolean get() = checks.isNotEmpty() && checks.all { it.passed }
}

