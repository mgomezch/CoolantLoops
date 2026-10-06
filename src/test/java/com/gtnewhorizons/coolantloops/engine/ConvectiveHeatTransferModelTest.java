package com.gtnewhorizons.coolantloops.engine;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ConvectiveHeatTransferModelTest {

    private final ConvectiveHeatTransferModel model = ConvectiveHeatTransferModel.DEFAULT;

    @Test
    void testZeroFlowYieldsZeroHeatTransfer() {
        assertEquals(0.0, model.computeH(0.0), 1e-9);
        assertEquals(0.0, model.computeH(-1.0), 1e-9);
        assertEquals(0.0, model.computeHeatTransferRate(0.0, 2.0, 500.0, 20.0), 1e-9);
    }

    @Test
    void testLaminarRegimeLinearScaling() {
        double v1 = 0.1;
        double v2 = 0.2;
        double h1 = model.computeH(v1);
        double h2 = model.computeH(v2);

        assertTrue(h1 > 0.0);
        assertEquals(2.0, h2 / h1, 0.01, "Laminar regime must scale linearly with velocity");
    }

    @Test
    void testTurbulentTransitionSteepIncrease() {
        double vTurb = model.getVTurbulent(); // 0.5
        double hAtTurb = model.computeH(vTurb);
        double hMidTurb = model.computeH(1.5);
        double hSat = model.computeH(model.getVSaturation());

        assertTrue(hMidTurb > hAtTurb);
        assertTrue(hSat > hMidTurb);

        // Rate of increase in turbulent region is significantly higher than in laminar
        double laminarDelta = hAtTurb - model.computeH(0.0);
        double turbDelta = hSat - hAtTurb;
        assertTrue(turbDelta > laminarDelta * 2.0, "Turbulent transition must provide a steep jump");
    }

    @Test
    void testBoundaryLayerSaturation() {
        double vSat = model.getVSaturation(); // 3.0
        double hAtSat = model.computeH(vSat);
        double hHigh = model.computeH(10.0);
        double hExtreme = model.computeH(100.0);

        assertTrue(hHigh >= hAtSat);
        assertTrue(hExtreme <= model.getHMax(), "Heat transfer coefficient must not exceed hMax");
        assertEquals(model.getHMax(), hExtreme, 10.0, "High velocity must asymptote towards hMax");
    }

    @Test
    void testHeatTransferRateDirectionality() {
        // Hot device, cold coolant -> positive Q (heat enters coolant)
        double qIn = model.computeHeatTransferRate(1.0, 1.0, 100.0, 20.0);
        assertTrue(qIn > 0.0);

        // Cold device, hot coolant -> negative Q (heat leaves coolant)
        double qOut = model.computeHeatTransferRate(1.0, 1.0, 20.0, 100.0);
        assertTrue(qOut < 0.0);
        assertEquals(-qIn, qOut, 1e-6);
    }
}
