package com.example.data

import com.example.data.model.BusyItem
import com.example.data.model.BusyVoucher
import com.example.data.model.BusyVoucherType
import com.example.data.model.CostingLineItem
import com.example.data.model.CostingStructureType
import com.example.data.model.RealCostingBreakdown
import com.example.data.model.RealCostingRateConfig
import com.example.data.model.SiteSurveyDimensions
import com.example.data.model.SolarSystemType
import com.example.data.model.WiringMeasurementResult
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Pure Kotlin Real Costing ('Kitne Ka Padega'), Wiring Measurement,
 * BUSY 21 Purchase Rate extraction, and PM Surya Ghar Subsidy calculator.
 */
object RealCostingCalculator {

    private const val PANEL_FOOTPRINT_SQ_M = 2.62 // ~2.28m x 1.13m 540Wp Mono PERC module

    /**
     * Reads latest BUSY 21 Purchase (F9) voucher line items and Item Master purchase rates,
     * falling back cleanly to 2026 Rajasthan EPC defaults when records don't specify an item.
     */
    fun extractLatestPurchaseRates(
        vouchers: List<BusyVoucher>,
        items: List<BusyItem>,
        structureType: CostingStructureType
    ): RealCostingRateConfig {
        var panelPerWatt = 24.5
        var inverterPerKw = 7200.0
        var dcCablePerM = 44.0
        var acCablePerM = 92.0
        var earthingKit = 7800.0
        var busyHits = 0

        val purchaseLines = vouchers
            .filter { it.type == BusyVoucherType.PURCHASE }
            .sortedByDescending { it.date }
            .flatMap { it.lineItems }

        fun findLatestRate(predicate: (name: String) -> Boolean): Double? {
            val fromVoucher = purchaseLines.firstOrNull { predicate(it.itemName) && it.rate > 0.0 }?.rate
            if (fromVoucher != null) return fromVoucher
            return items.firstOrNull { predicate(it.name) && it.defaultPurchaseRate > 0.0 }?.defaultPurchaseRate
        }

        findLatestRate {
            it.contains("panel", ignoreCase = true) || it.contains("module", ignoreCase = true) || it.contains("dcr", ignoreCase = true)
        }?.let { rawRate ->
            val computed = if (rawRate > 100.0) rawRate / 540.0 else rawRate
            if (computed in 15.0..60.0) {
                panelPerWatt = ((computed * 10.0).roundToInt() / 10.0)
                busyHits++
            }
        }

        findLatestRate { it.contains("inverter", ignoreCase = true) }?.let { rawInv ->
            val inferredKw = when {
                items.any { it.name.contains("5kw", ignoreCase = true) && it.defaultPurchaseRate == rawInv } -> 5.0
                items.any { it.name.contains("3kw", ignoreCase = true) && it.defaultPurchaseRate == rawInv } -> 3.0
                rawInv >= 30_000.0 -> 5.0
                else -> 3.0
            }
            val perKw = rawInv / inferredKw
            if (perKw in 4_000.0..20_000.0) {
                inverterPerKw = perKw
                busyHits++
            }
        }

        findLatestRate { it.contains("dc cable", ignoreCase = true) || it.contains("dc solar cable", ignoreCase = true) }?.let {
            if (it in 15.0..150.0) {
                dcCablePerM = it
                busyHits++
            }
        }

        findLatestRate { it.contains("ac cable", ignoreCase = true) || it.contains("armoured", ignoreCase = true) }?.let {
            if (it in 30.0..300.0) {
                acCablePerM = it
                busyHits++
            }
        }

        findLatestRate { it.contains("earthing", ignoreCase = true) }?.let {
            if (it in 1500.0..15000.0) {
                earthingKit = it + 4200.0 // Earthing kit + ACDB/DCDB protection boxes
                busyHits++
            }
        }

        val sourceLabel = if (busyHits > 0) {
            "Synced $busyHits live rate(s) from BUSY 21 Purchase + 2026 Raj. defaults"
        } else {
            "2026 Rajasthan EPC Standard Rates"
        }

        return RealCostingRateConfig(
            dcrPanelRatePerWatt = panelPerWatt,
            inverterRatePerKw = inverterPerKw,
            structureRatePerKw = structureType.defaultRatePerKw,
            dcCableRatePerMeter = dcCablePerM,
            acCableRatePerMeter = acCablePerM,
            earthingRatePerMeter = 65.0,
            pvcConduitRatePerMeter = 38.0,
            acdbDcdbEarthingKitCost = earthingKit,
            batteryRatePerKwh = 14500.0,
            installationLabourPerKw = 3200.0,
            netMeteringAndDiscomFee = 4500.0,
            profitMarginPercent = 18.0,
            gstPercent = 13.8,
            sourceLabel = sourceLabel
        )
    }

    fun computeWiringMeasurements(
        capacityKw: Double,
        dimensions: SiteSurveyDimensions,
        structureType: CostingStructureType
    ): WiringMeasurementResult {
        val safeKw = capacityKw.coerceIn(0.5, 500.0)
        val panelWp = dimensions.panelWattageWp.coerceIn(250, 750)
        val panelCount = ceil((safeKw * 1000.0) / panelWp.toDouble()).toInt().coerceAtLeast(1)

        val roofLength = dimensions.roofLengthM.coerceAtLeast(1.0)
        val roofWidth = dimensions.roofWidthM.coerceAtLeast(1.0)
        val roofAreaSqM = roofLength * roofWidth
        val requiredPanelAreaSqM = panelCount * PANEL_FOOTPRINT_SQ_M * 1.15 // 15% walkway/clearance factor
        val fitsOnRoof = roofAreaSqM >= requiredPanelAreaSqM

        val invDist = dimensions.inverterDistanceM.coerceAtLeast(1.0)
        val earthDist = dimensions.earthingDistanceM.coerceAtLeast(1.0)

        // Pair run (Red + Black) along string + down to inverter DCDB
        val dcCableMeters = ((panelCount * 1.6) + (invDist * 2.0) + 4.0).roundTo1Decimal()
        // Armoured AC run from Inverter ACDB to Main Distribution Board / Net Meter
        val acCableMeters = ((invDist * 1.25) + 6.0).roundTo1Decimal()
        // 3 dedicated earthing drops (DC, AC, Lightning Arrester)
        val earthingWireMeters = ((earthDist * 3.0) + 8.0).roundTo1Decimal()
        // Heavy UV PVC conduit protecting DC + AC runs
        val pvcConduitMeters = ((dcCableMeters * 0.55) + (acCableMeters * 0.85)).roundTo1Decimal()

        val kgPerKw = when (structureType) {
            CostingStructureType.STANDARD_RCC -> 42.0
            CostingStructureType.ELEVATED_GAZEBO -> 78.0
            CostingStructureType.TIN_SHED -> 24.0
        }

        return WiringMeasurementResult(
            panelCount = panelCount,
            roofAreaSqM = roofAreaSqM.roundTo1Decimal(),
            requiredPanelAreaSqM = requiredPanelAreaSqM.roundTo1Decimal(),
            fitsOnRoof = fitsOnRoof,
            dcCableMeters = dcCableMeters,
            acCableMeters = acCableMeters,
            earthingWireMeters = earthingWireMeters,
            pvcConduitMeters = pvcConduitMeters,
            estimatedStructureKg = (safeKw * kgPerKw).roundTo1Decimal()
        )
    }

    /**
     * PM Surya Ghar: Muft Bijli Yojana Central Subsidy:
     * - 1 kW -> ₹30,000
     * - 2 kW -> ₹60,000
     * - 3 kW and above -> ₹78,000 (capped)
     * - Off-Grid -> ₹0
     */
    fun computePmSuryaGharSubsidy(capacityKw: Double, systemType: SolarSystemType): Double {
        if (!systemType.isSubsidyEligible || capacityKw <= 0.0) return 0.0
        return when {
            capacityKw < 1.0 -> 30_000.0 * capacityKw
            capacityKw < 2.0 -> 30_000.0 + (capacityKw - 1.0) * 30_000.0
            capacityKw < 3.0 -> 60_000.0 + (capacityKw - 2.0) * 18_000.0
            else -> 78_000.0
        }.roundToInt().toDouble()
    }

    fun computeRealCosting(
        capacityKw: Double,
        systemType: SolarSystemType,
        structureType: CostingStructureType,
        dimensions: SiteSurveyDimensions,
        rates: RealCostingRateConfig
    ): RealCostingBreakdown {
        val safeKw = capacityKw.coerceIn(0.5, 500.0)
        val wiring = computeWiringMeasurements(safeKw, dimensions, structureType)
        val totalWatts = wiring.panelCount * dimensions.panelWattageWp.toDouble()

        val panelsCost = totalWatts * rates.dcrPanelRatePerWatt.coerceAtLeast(0.0)
        val inverterMultiplier = when (systemType) {
            SolarSystemType.ON_GRID_DCR -> 1.0
            SolarSystemType.HYBRID_LIFEPO4 -> 1.48
            SolarSystemType.OFF_GRID -> 1.25
        }
        val inverterCost = safeKw * rates.inverterRatePerKw.coerceAtLeast(0.0) * inverterMultiplier
        val structureCost = safeKw * rates.structureRatePerKw.coerceAtLeast(0.0)

        val dcCost = wiring.dcCableMeters * rates.dcCableRatePerMeter.coerceAtLeast(0.0)
        val acCost = wiring.acCableMeters * rates.acCableRatePerMeter.coerceAtLeast(0.0)
        val earthingCost = wiring.earthingWireMeters * rates.earthingRatePerMeter.coerceAtLeast(0.0)
        val conduitCost = wiring.pvcConduitMeters * rates.pvcConduitRatePerMeter.coerceAtLeast(0.0)
        val wiringTotalCost = dcCost + acCost + earthingCost + conduitCost

        val batteryKwh = when (systemType) {
            SolarSystemType.ON_GRID_DCR -> 0.0
            SolarSystemType.HYBRID_LIFEPO4 -> (safeKw * 1.6).coerceAtLeast(4.8)
            SolarSystemType.OFF_GRID -> (safeKw * 2.0).coerceAtLeast(5.0)
        }
        val batteryCost = batteryKwh * rates.batteryRatePerKwh.coerceAtLeast(0.0)

        val bosKitCost = rates.acdbDcdbEarthingKitCost.coerceAtLeast(0.0)
        val labourCost = safeKw * rates.installationLabourPerKw.coerceAtLeast(0.0)
        val netMeterFee = if (systemType == SolarSystemType.OFF_GRID) 0.0 else rates.netMeteringAndDiscomFee.coerceAtLeast(0.0)

        val lineItems = buildList {
            add(
                CostingLineItem(
                    title = "DCR Solar PV Modules",
                    spec = "${wiring.panelCount} × ${dimensions.panelWattageWp}Wp (${String.format(Locale.US, "%.2f", totalWatts / 1000.0)} kWp @ ₹${rates.dcrPanelRatePerWatt}/W)",
                    landingCostInr = panelsCost
                )
            )
            add(
                CostingLineItem(
                    title = "${systemType.shortLabel} Solar Inverter",
                    spec = "${String.format(Locale.US, "%.1f", safeKw)} kW MPPT Inverter",
                    landingCostInr = inverterCost
                )
            )
            add(
                CostingLineItem(
                    title = "HDGI Mounting Structure (${structureType.shortLabel})",
                    spec = "~${wiring.estimatedStructureKg.roundToInt()} kg HDGI (${structureType.clearanceMeters}m clr)",
                    landingCostInr = structureCost
                )
            )
            add(
                CostingLineItem(
                    title = "Measured DC/AC/Earthing & PVC Wiring",
                    spec = "DC ${wiring.dcCableMeters.roundToInt()}m • AC ${wiring.acCableMeters.roundToInt()}m • Earth ${wiring.earthingWireMeters.roundToInt()}m • PVC ${wiring.pvcConduitMeters.roundToInt()}m",
                    landingCostInr = wiringTotalCost
                )
            )
            add(
                CostingLineItem(
                    title = "ACDB / DCDB, LA & Chemical Earthing Kits",
                    spec = "3-Pit Earthing + Surge Protection + MC4",
                    landingCostInr = bosKitCost
                )
            )
            if (batteryCost > 0.0) {
                add(
                    CostingLineItem(
                        title = "LiFePO4 Storage Battery Bank",
                        spec = "${String.format(Locale.US, "%.1f", batteryKwh)} kWh Lithium Battery",
                        landingCostInr = batteryCost
                    )
                )
            }
            add(
                CostingLineItem(
                    title = "EPC Installation & Commissioning Labour",
                    spec = "Civil + Structure + Electrical (${String.format(Locale.US, "%.1f", safeKw)} kW)",
                    landingCostInr = labourCost
                )
            )
            if (netMeterFee > 0.0) {
                add(
                    CostingLineItem(
                        title = "AVVNL Net Metering & Portal Liaison",
                        spec = "Feasibility, Meter Testing & Subsidy File",
                        landingCostInr = netMeterFee
                    )
                )
            }
        }

        val totalLandingCost = lineItems.sumOf { it.landingCostInr }
        val marginPct = rates.profitMarginPercent.coerceIn(0.0, 60.0)
        val profitMarginInr = totalLandingCost * (marginPct / 100.0)
        val taxableQuoted = totalLandingCost + profitMarginInr
        val gstPct = rates.gstPercent.coerceIn(0.0, 28.0)
        val gstAmount = taxableQuoted * (gstPct / 100.0)
        val grossQuoted = taxableQuoted + gstAmount
        val subsidy = computePmSuryaGharSubsidy(safeKw, systemType)
        val netAfterSubsidy = (grossQuoted - subsidy).coerceAtLeast(0.0)

        val monthlyUnits = (safeKw * 4.0 * 30.0).roundToInt() // ~4 units/kW/day avg in Udaipur Rajasthan
        val monthlySavings = monthlyUnits * 8.0 // ₹8/unit residential/commercial tariff
        val paybackYears = if (monthlySavings > 0.0) {
            ((netAfterSubsidy / (monthlySavings * 12.0)) * 10.0).roundToInt() / 10.0
        } else 0.0

        return RealCostingBreakdown(
            capacityKw = safeKw,
            systemType = systemType,
            structureType = structureType,
            wiring = wiring,
            lineItems = lineItems,
            totalLandingCostInr = totalLandingCost,
            profitMarginInr = profitMarginInr,
            profitMarginPct = marginPct,
            taxableQuotedPriceInr = taxableQuoted,
            gstAmountInr = gstAmount,
            grossQuotedPriceInr = grossQuoted,
            pmSuryaGharSubsidyInr = subsidy,
            finalNetCostAfterSubsidyInr = netAfterSubsidy,
            estimatedMonthlyUnits = monthlyUnits,
            estimatedMonthlySavingsInr = monthlySavings,
            estimatedPaybackYears = paybackYears
        )
    }

    private fun Double.roundTo1Decimal(): Double = ((this * 10.0).roundToInt()) / 10.0
}
