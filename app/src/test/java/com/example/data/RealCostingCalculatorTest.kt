package com.example.data

import com.example.data.model.BusyItem
import com.example.data.model.BusyLineItem
import com.example.data.model.BusyVoucher
import com.example.data.model.BusyVoucherType
import com.example.data.model.CostingStructureType
import com.example.data.model.SiteSurveyDimensions
import com.example.data.model.SolarSystemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RealCostingCalculatorTest {

    @Test
    fun `pm surya ghar subsidy matches official slab rules`() {
        assertEquals(30_000.0, RealCostingCalculator.computePmSuryaGharSubsidy(1.0, SolarSystemType.ON_GRID_DCR), 0.1)
        assertEquals(60_000.0, RealCostingCalculator.computePmSuryaGharSubsidy(2.0, SolarSystemType.ON_GRID_DCR), 0.1)
        assertEquals(78_000.0, RealCostingCalculator.computePmSuryaGharSubsidy(3.0, SolarSystemType.ON_GRID_DCR), 0.1)
        assertEquals(78_000.0, RealCostingCalculator.computePmSuryaGharSubsidy(5.0, SolarSystemType.ON_GRID_DCR), 0.1)
        assertEquals(78_000.0, RealCostingCalculator.computePmSuryaGharSubsidy(10.0, SolarSystemType.HYBRID_LIFEPO4), 0.1)
        assertEquals(0.0, RealCostingCalculator.computePmSuryaGharSubsidy(5.0, SolarSystemType.OFF_GRID), 0.1)
    }

    @Test
    fun `extractLatestPurchaseRates prefers newest BUSY purchase voucher rates`() {
        val items = listOf(
            BusyItem(name = "DCR Solar PV Module 540W", defaultPurchaseRate = 12200.0),
            BusyItem(name = "Solar DC Cable 4 sq.mm", defaultPurchaseRate = 44.0)
        )
        val vouchers = listOf(
            BusyVoucher(
                type = BusyVoucherType.PURCHASE,
                voucherNo = "PUR-001",
                date = "2026-04-01",
                partyAccountId = "v1",
                partyAccountName = "Vendor A",
                contraAccountId = "p1",
                contraAccountName = "Purchase",
                lineItems = listOf(
                    BusyLineItem(itemName = "DCR Solar Panel 540W", rate = 13500.0),
                    BusyLineItem(itemName = "Solar DC Cable 4 sq.mm", rate = 49.0)
                )
            )
        )
        val rates = RealCostingCalculator.extractLatestPurchaseRates(vouchers, items, CostingStructureType.ELEVATED_GAZEBO)
        assertEquals(25.0, rates.dcrPanelRatePerWatt, 0.1)
        assertEquals(49.0, rates.dcCableRatePerMeter, 0.1)
        assertEquals(CostingStructureType.ELEVATED_GAZEBO.defaultRatePerKw, rates.structureRatePerKw, 0.1)
    }

    @Test
    fun `wiring and costing breakdown compute valid positive net cost and savings`() {
        val dims = SiteSurveyDimensions(roofLengthM = 10.0, roofWidthM = 6.0, inverterDistanceM = 14.0, earthingDistanceM = 18.0)
        val rates = RealCostingCalculator.extractLatestPurchaseRates(emptyList(), emptyList(), CostingStructureType.STANDARD_RCC)
        val breakdown = RealCostingCalculator.computeRealCosting(
            capacityKw = 5.0,
            systemType = SolarSystemType.ON_GRID_DCR,
            structureType = CostingStructureType.STANDARD_RCC,
            dimensions = dims,
            rates = rates
        )
        assertEquals(10, breakdown.wiring.panelCount)
        assertTrue(breakdown.wiring.dcCableMeters > 0.0)
        assertTrue(breakdown.wiring.acCableMeters > 0.0)
        assertTrue(breakdown.wiring.earthingWireMeters > 0.0)
        assertTrue(breakdown.wiring.pvcConduitMeters > 0.0)
        assertTrue(breakdown.totalLandingCostInr > 0.0)
        assertEquals(78_000.0, breakdown.pmSuryaGharSubsidyInr, 0.1)
        assertEquals(breakdown.grossQuotedPriceInr - 78_000.0, breakdown.finalNetCostAfterSubsidyInr, 0.5)
        assertEquals(600, breakdown.estimatedMonthlyUnits)
        assertTrue(breakdown.estimatedPaybackYears > 0.5)
    }
}
