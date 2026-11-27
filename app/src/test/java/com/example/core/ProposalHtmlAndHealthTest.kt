package com.example.core

import com.example.data.RealCostingCalculator
import com.example.data.model.AppHealthReport
import com.example.data.model.CostingStructureType
import com.example.data.model.HealthCheckItem
import com.example.data.model.ProposalFormat
import com.example.data.model.RealCostingRateConfig
import com.example.data.model.SiteSurveyDimensions
import com.example.data.model.SolarSystemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProposalHtmlAndHealthTest {

    @Test
    fun allFourProposalFormats_renderValidCompanyAndBreakInsideCss() {
        val dims = SiteSurveyDimensions()
        val rates = RealCostingRateConfig()
        val breakdown = RealCostingCalculator.computeRealCosting(
            capacityKw = 5.0,
            systemType = SolarSystemType.ON_GRID_DCR,
            structureType = CostingStructureType.ELEVATED_GAZEBO,
            dimensions = dims,
            rates = rates
        )
        val comparisons = SolarSystemType.entries.map { sys ->
            RealCostingCalculator.computeRealCosting(
                capacityKw = 5.0,
                systemType = sys,
                structureType = CostingStructureType.ELEVATED_GAZEBO,
                dimensions = dims,
                rates = rates
            )
        }

        ProposalFormat.entries.forEach { format ->
            val html = ExportPrintHelper.buildProposalHtml(
                context = null,
                format = format,
                customerName = "Ramesh Sharma",
                customerMobile = "9829012345",
                siteLocation = "Sector 14, Udaipur",
                consumerNo = "K-1402001122",
                breakdown = breakdown,
                comparisonBreakdowns = comparisons,
                dimensions = dims,
                includeOwnerInternalCosting = false
            )

            assertTrue("Missing company name in ${format.name}", html.contains("NB ENGINEERING WORKS AND ELECTRICALS"))
            assertTrue("Missing GSTIN in ${format.name}", html.contains("08CSZPK9277N2Z8"))
            assertTrue("Missing HDFC Bank A/c in ${format.name}", html.contains("50200079191430"))
            assertTrue("Missing print page-break protection in ${format.name}", html.contains("break-inside: avoid"))
            assertTrue("Missing customer name in ${format.name}", html.contains("Ramesh Sharma"))
        }

        val dossierHtml = ExportPrintHelper.buildProposalHtml(
            context = null,
            format = ProposalFormat.FULL_3D_DOSSIER_4PG,
            customerName = "Ramesh Sharma",
            customerMobile = "9829012345",
            siteLocation = "Udaipur",
            consumerNo = "K-1402001122",
            breakdown = breakdown,
            comparisonBreakdowns = comparisons,
            dimensions = dims,
            includeOwnerInternalCosting = true
        )
        assertTrue(dossierHtml.contains("page-break"))
        assertTrue(dossierHtml.contains("Single Line Diagram"))
        assertTrue(dossierHtml.contains("On-Grid DCR"))
        assertTrue(dossierHtml.contains("Hybrid with LiFePO4 Battery"))
        assertTrue(dossierHtml.contains("Off-Grid"))
        assertTrue(dossierHtml.contains("CONFIDENTIAL"))

        val tenderHtml = ExportPrintHelper.buildProposalHtml(
            context = null,
            format = ProposalFormat.COMMERCIAL_TENDER_BOQ,
            customerName = "Mewar Spinning Mills",
            customerMobile = "9929214883",
            siteLocation = "RIICO Madri",
            consumerNo = "HT-8899",
            breakdown = breakdown,
            comparisonBreakdowns = comparisons,
            dimensions = dims
        )
        assertTrue(tenderHtml.contains("85414300"))
        assertTrue(tenderHtml.contains("73089090"))
        assertTrue(tenderHtml.contains("Stage 1 (30%)"))
        assertTrue(tenderHtml.contains("Stage 2 (60%)"))
        assertTrue(tenderHtml.contains("Stage 3 (10%)"))
    }

    @Test
    fun appHealthReport_computesPassedCountAndAllPassed() {
        val passing = AppHealthReport(
            ranAt = "2026-11-27 12:00",
            checks = listOf(
                HealthCheckItem("Supabase", true, "OK"),
                HealthCheckItem("Costing", true, "OK")
            )
        )
        assertTrue(passing.allPassed)
        assertEquals(2, passing.passedCount)

        val partial = AppHealthReport(
            ranAt = "2026-11-27 12:05",
            checks = listOf(
                HealthCheckItem("Supabase", false, "42501"),
                HealthCheckItem("Costing", true, "OK")
            )
        )
        assertFalse(partial.allPassed)
        assertEquals(1, partial.passedCount)
    }
}
