package org.itur.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** P.835 reference atmosphere: anchor values of the standard atmosphere and physical invariants. */
class P835Test {

    @Test
    void seaLevelValues() {
        assertEquals(288.15, P835.standardTemperature(0), 1e-9);
        assertEquals(1013.25, P835.standardPressure(0), 1e-9);
        assertEquals(7.5, P835.standardWaterVapourDensity(0), 1e-12);
    }

    @Test
    void valuesOfTheStandardAtmosphere() {
        // US Standard Atmosphere 1976, geometric altitude: 5 km 540.48 hPa, 20 km 216.65 K and 55.29 hPa
        assertEquals(540.48, P835.standardPressure(5), 0.01);
        assertEquals(216.65, P835.standardTemperature(20), 1e-9);
        assertEquals(55.29, P835.standardPressure(20), 0.01);
        assertEquals(270.65, P835.standardTemperature(50), 1e-9);
    }

    @Test
    void temperatureIsContinuousEverywhere() {
        double prev = P835.standardTemperature(0);
        for (double h = 0.01; h <= 100; h += 0.01) {
            double t = P835.standardTemperature(h);
            assertTrue(Math.abs(t - prev) < 0.5, "jump at h = " + h);
            prev = t;
        }
    }

    @Test
    void pressureAndWaterVapourDecreaseWithHeight() {
        double p = Double.MAX_VALUE;
        double rho = Double.MAX_VALUE;
        for (double h = 0; h <= 100; h += 0.5) {
            double pp = P835.standardPressure(h);
            double rr = P835.standardWaterVapourDensity(h);
            assertTrue(pp < p && pp > 0, "pressure at " + h);
            assertTrue(rr < rho && rr > 0, "density at " + h);
            p = pp;
            rho = rr;
        }
    }

    @Test
    void waterVapourScaleHeight() {
        assertEquals(7.5 / Math.E, P835.standardWaterVapourDensity(2), 1e-12);
        assertEquals(10 * Math.exp(-0.5), P835.standardWaterVapourDensity(1, 2, 10), 1e-12);
    }

    @Test
    void waterVapourPressureFollowsTheIdealGasLaw() {
        double h = 3;
        double expected = P835.standardWaterVapourDensity(h, 2, 7.5) * P835.standardTemperature(h) / 216.7;
        assertEquals(expected, P835.standardWaterVapourPressure(h, 2, 7.5), 1e-12);
        // at sea level, 7.5 g/m3 at 288.15 K is about 10 hPa
        assertEquals(9.97, P835.standardWaterVapourPressure(0, 2, 7.5), 0.01);
    }
}
