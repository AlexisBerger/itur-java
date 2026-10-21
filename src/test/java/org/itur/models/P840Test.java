package org.itur.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** P.840-7: cloud liquid water statistics and the double-Debye coefficient. */
class P840Test {

    private static final double LAT = 48.85;
    private static final double LON = 2.35;

    @Test
    void coefficientIncreasesWithFrequencyAndDecreasesWithTemperature() {
        double prev = 0;
        for (double f = 5; f <= 200; f += 5) {
            double k = P840.specificAttenuationCoefficient(f, 0);
            assertTrue(k > prev, "f = " + f);
            prev = k;
        }
        assertTrue(P840.specificAttenuationCoefficient(20, 0) > P840.specificAttenuationCoefficient(20, 20));
    }

    @Test
    void coefficientIsAboutQuadraticInFrequencyBelow10GHz() {
        // Rayleigh regime: K_l ~ f^2 (log-log slope near 2)
        double k1 = P840.specificAttenuationCoefficient(2, 0);
        double k2 = P840.specificAttenuationCoefficient(4, 0);
        double slope = Math.log(k2 / k1) / Math.log(2);
        assertEquals(2, slope, 0.35);
    }

    @Test
    void cloudAttenuationIsLredTimesCoefficientOverSineOfElevation() {
        double lred = 1.2;
        double expected = lred * P840.specificAttenuationCoefficient(20, 0) / Math.sin(Math.toRadians(35));
        assertEquals(expected, P840.cloudAttenuation(LAT, LON, 35, 20, 1, lred), 1e-12);
        assertEquals(0, P840.cloudAttenuation(LAT, LON, 35, 20, 1, 0.0), 0);
        // the slant factor
        double z = P840.cloudAttenuation(LAT, LON, 90, 20, 1, lred);
        assertEquals(z / Math.sin(Math.toRadians(20)), P840.cloudAttenuation(LAT, LON, 20, 20, 1, lred), 1e-12);
    }

    @Test
    void reducedLiquidDecreasesWithProbabilityAndInterpolatesInBetween() {
        double prev = Double.MAX_VALUE;
        for (double p : new double[] {0.1, 0.5, 1, 5, 10, 50, 90, 99}) {
            double l = P840.columnarContentReducedLiquid(LAT, LON, p);
            assertTrue(l <= prev, "p = " + p);
            prev = l;
        }
        double a = P840.columnarContentReducedLiquid(LAT, LON, 1);
        double b = P840.columnarContentReducedLiquid(LAT, LON, 2);
        double m = P840.columnarContentReducedLiquid(LAT, LON, 1.4);
        assertTrue(m <= a && m >= b);
    }

    @Test
    void mapValueIsUsedWhenNoLredIsGiven() {
        double lred = P840.columnarContentReducedLiquid(LAT, LON, 1);
        assertEquals(P840.cloudAttenuation(LAT, LON, 35, 20, 1, lred),
            P840.cloudAttenuation(LAT, LON, 35, 20, 1, null), 1e-12);
    }

    @Test
    void lognormalCoefficientsArePlausible() {
        P840.LognormalCoefficients c = P840.lognormalApproximationCoefficients(LAT, LON);
        assertTrue(c.sigma() > 0);
        assertTrue(c.pclw() > 0 && c.pclw() <= 100);
        assertTrue(Double.isFinite(c.m()));
    }

    @Test
    void longitudeConventions() {
        assertEquals(P840.columnarContentReducedLiquid(LAT, 200, 1), P840.columnarContentReducedLiquid(LAT, -160, 1),
            1e-9);
    }
}
