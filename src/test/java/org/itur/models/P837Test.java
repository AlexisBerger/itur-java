package org.itur.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** P.837-7: consistency of the rainfall statistics. */
class P837Test {

    private static final double LAT = 48.85;
    private static final double LON = 2.35;

    @Test
    void rainfallRateDecreasesWithTheExceedanceProbability() {
        double prev = Double.MAX_VALUE;
        for (double p : new double[] {0.001, 0.003, 0.01, 0.03, 0.1, 0.3, 1, 2, 3, 5}) {
            double r = P837.rainfallRate(LAT, LON, p);
            assertTrue(r < prev && r > 0, "p = " + p + " r = " + r);
            prev = r;
        }
    }

    @Test
    void rateIsContinuousAroundTheMapProbability() {
        double a = P837.rainfallRate(LAT, LON, 0.01);
        double b = P837.rainfallRate(LAT, LON, 0.0100001);
        assertEquals(a, b, 0.05 * a); // the map value and the monthly statistics agree within a few %
    }

    @Test
    void noRainRateBeyondTheProbabilityOfRain() {
        double p0 = P837.rainfallProbability(LAT, LON);
        assertTrue(p0 > 1 && p0 < 15);
        assertEquals(0, P837.rainfallRate(LAT, LON, p0 + 1), 0);
    }

    @Test
    void probabilityOfRainIsHigherInTheWetTropicsThanInTheDesert() {
        assertTrue(P837.rainfallProbability(5.16, -52.65) > P837.rainfallProbability(25.0, 25.0));
    }

    @Test
    void unavailabilityInvertsTheRainfallRate() {
        for (double p : new double[] {0.05, 0.1, 0.5, 1, 3}) {
            double r = P837.rainfallRate(LAT, LON, p);
            assertEquals(p, P837.unavailabilityFromRainfallRate(LAT, LON, r), 1e-4 * p, "p = " + p);
        }
    }

    @Test
    void longitudeConventions() {
        assertEquals(P837.rainfallRate(LAT, LON, 0.1), P837.rainfallRate(LAT, LON + 360, 0.1), 1e-9);
        assertEquals(P837.rainfallRate(LAT, 200, 0.01), P837.rainfallRate(LAT, -160, 0.01), 1e-9);
        assertEquals(P837.rainfallProbability(LAT, 200), P837.rainfallProbability(LAT, -160), 1e-9);
    }

    @Test
    void tropicalRainfallIsIntense() {
        assertTrue(P837.rainfallRate(5.16, -52.65, 0.01) > 80);
        assertTrue(P837.rainfallRate(25.2, 55.3, 0.01) < 60);
    }
}
