package org.itur.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Behaviour of the map-based models P.839, P.1510 and P.1511: longitude conventions, poles, identities. */
class MapModelsTest {

    private static final double LAT = 48.85;
    private static final double LON = 2.35;

    @Test
    void rainHeightIsTheIsothermPlus360Metres() {
        for (double[] p : new double[][] {{48.85, 2.35}, {5.16, -52.65}, {-33.9, 151.2}, {1.35, 103.82}}) {
            assertEquals(P839.isotherm0(p[0], p[1]) + 0.36, P839.rainHeight(p[0], p[1]), 1e-12);
        }
    }

    @Test
    void rainHeightIsHigherInTheTropics() {
        assertTrue(P839.rainHeight(5.16, -52.65) > P839.rainHeight(LAT, LON));
        assertTrue(P839.rainHeight(LAT, LON) > P839.rainHeight(70, 25));
    }

    @Test
    void longitudeConventionsAreEquivalent() {
        assertEquals(P839.isotherm0(LAT, LON), P839.isotherm0(LAT, LON + 360), 1e-9);
        assertEquals(P839.isotherm0(LAT, LON), P839.isotherm0(LAT, LON - 360), 1e-9);
        assertEquals(P839.isotherm0(LAT, 180), P839.isotherm0(LAT, -180), 1e-9);
        assertEquals(P1510.surfaceMeanTemperature(LAT, 270), P1510.surfaceMeanTemperature(LAT, -90), 1e-9);
        assertEquals(P1511.topographicAltitude(LAT, 200), P1511.topographicAltitude(LAT, -160), 1e-9);
    }

    @Test
    void monthlyTemperaturesAverageToTheAnnualValue() {
        double sum = 0;
        for (int m = 1; m <= 12; m++) {
            sum += P1510.surfaceMonthMeanTemperature(LAT, LON, m);
        }
        assertEquals(P1510.surfaceMeanTemperature(LAT, LON), sum / 12, 0.5);
    }

    @Test
    void seasonsAreInTheRightHemisphere() {
        assertTrue(P1510.surfaceMonthMeanTemperature(LAT, LON, 7) > P1510.surfaceMonthMeanTemperature(LAT, LON, 1));
        assertTrue(P1510.surfaceMonthMeanTemperature(-33.9, 151.2, 1)
            > P1510.surfaceMonthMeanTemperature(-33.9, 151.2, 7));
    }

    @Test
    void temperaturesArePlausible() {
        for (double[] p : new double[][] {{LAT, LON}, {-78, 106}, {25.2, 55.3}, {1.35, 103.8}}) {
            double t = P1510.surfaceMeanTemperature(p[0], p[1]);
            assertTrue(t > 200 && t < 315, "T = " + t);
        }
        assertTrue(P1510.surfaceMeanTemperature(-78, 106) < 240); // Antarctic plateau
    }

    @Test
    void invalidMonthsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> P1510.surfaceMonthMeanTemperature(LAT, LON, 0));
        assertThrows(IllegalArgumentException.class, () -> P1510.surfaceMonthMeanTemperature(LAT, LON, 13));
    }

    @Test
    void topographyIsPositiveAndRealistic() {
        assertTrue(P1511.topographicAltitude(45, -30) >= 0 && P1511.topographicAltitude(45, -30) < 1e-6); // ocean
        assertEquals(1.67, P1511.topographicAltitude(39.74, -104.99), 0.1); // Denver
        assertTrue(P1511.topographicAltitude(28.0, 86.9) > 4); // Himalaya
        assertTrue(P1511.topographicAltitude(LAT, LON) < 0.3);
    }

    @Test
    void mapsAreContinuous() {
        double a = P1511.topographicAltitude(45.0, 7.0);
        double b = P1511.topographicAltitude(45.0001, 7.0001);
        assertEquals(a, b, 0.01);
        assertEquals(P1510.surfaceMeanTemperature(LAT, LON), P1510.surfaceMeanTemperature(LAT + 1e-4, LON), 0.01);
    }
}
