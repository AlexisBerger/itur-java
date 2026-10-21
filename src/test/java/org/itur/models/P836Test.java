package org.itur.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** P.836-6: probability interpolation, altitude correction, longitude conventions. */
class P836Test {

    private static final double LAT = 48.85;
    private static final double LON = 2.35;
    private static final double[] PS = {0.1, 0.2, 0.3, 0.5, 1, 2, 3, 5, 10, 20, 30, 50, 60, 70, 80, 90, 95, 99};

    @Test
    void valuesDecreaseWithTheExceedanceProbability() {
        double v = Double.MAX_VALUE;
        double rho = Double.MAX_VALUE;
        for (double p : PS) {
            double nv = P836.totalWaterVapourContent(LAT, LON, p, null);
            double nrho = P836.surfaceWaterVapourDensity(LAT, LON, p, null);
            assertTrue(nv <= v, "V at p = " + p);
            assertTrue(nrho <= rho, "rho at p = " + p);
            v = nv;
            rho = nrho;
        }
    }

    @Test
    void intermediateProbabilitiesAreInterpolatedBetweenTheNeighbours() {
        double a = P836.totalWaterVapourContent(LAT, LON, 1, null);
        double b = P836.totalWaterVapourContent(LAT, LON, 2, null);
        double mid = P836.totalWaterVapourContent(LAT, LON, 1.5, null);
        assertTrue(mid < a && mid > b);
        // linear in log(p)
        double expected = a + (b - a) * Math.log(1.5 / 1) / Math.log(2.0 / 1);
        assertEquals(expected, mid, 0.05);
    }

    @Test
    void humidTropicsHoldMoreVapourThanDryDeserts() {
        assertTrue(P836.totalWaterVapourContent(1.35, 103.8, 50, null)
            > 2 * P836.totalWaterVapourContent(25.0, 25.0, 50, null)); // Singapore vs Libyan desert
    }

    @Test
    void higherStationsSeeLessVapour() {
        double low = P836.totalWaterVapourContent(LAT, LON, 1, 0.0);
        double mid = P836.totalWaterVapourContent(LAT, LON, 1, 1.0);
        double high = P836.totalWaterVapourContent(LAT, LON, 1, 3.0);
        assertTrue(low > mid && mid > high);
        double rho0 = P836.surfaceWaterVapourDensity(LAT, LON, 1, 0.0);
        assertTrue(P836.surfaceWaterVapourDensity(LAT, LON, 1, 2.0) < rho0);
    }

    @Test
    void defaultAltitudeIsTheTopographicOneOfTheMap() {
        // Paris is at about 85 m: the value without altitude lies between the ones at 0 and at 1 km
        double none = P836.totalWaterVapourContent(LAT, LON, 1, null);
        double sea = P836.totalWaterVapourContent(LAT, LON, 1, 0.0);
        double km = P836.totalWaterVapourContent(LAT, LON, 1, 1.0);
        assertTrue(none < sea && none > km, sea + " " + none + " " + km);
    }

    @Test
    void longitudeConventions() {
        assertEquals(P836.totalWaterVapourContent(LAT, 200, 1, null),
            P836.totalWaterVapourContent(LAT, -160, 1, null), 1e-9);
        assertEquals(P836.surfaceWaterVapourDensity(LAT, LON, 1, null),
            P836.surfaceWaterVapourDensity(LAT, LON + 360, 1, null), 1e-9);
    }

    @Test
    void tabulatedProbabilitiesAreExact() {
        double a = P836.totalWaterVapourContent(LAT, LON, 5, null);
        assertEquals(a, P836.totalWaterVapourContent(LAT, LON, 5.0, null), 0);
        assertTrue(a > 0 && a < 80);
    }
}
