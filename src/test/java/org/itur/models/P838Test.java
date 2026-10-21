package org.itur.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** P.838-3: values published in the Recommendation, limiting cases of the polarisation formulas. */
class P838Test {

    /** Table 5 of P.838-3 (k and alpha for horizontal and vertical polarisation). */
    @Test
    void coefficientsMatchThePublishedTable() {
        double[][] table = { // f, kH, kV, aH, aV
            {10, 0.01217, 0.01129, 1.2571, 1.2156},
            {20, 0.09164, 0.09611, 1.0568, 0.9847},
            {30, 0.2403, 0.2291, 0.9485, 0.9129}};
        for (double[] t : table) {
            P838.Coefficients h = P838.coefficients(t[0], 0, 0);
            P838.Coefficients v = P838.coefficients(t[0], 0, 90);
            assertEquals(t[1], h.k(), 5e-5 * Math.max(1, t[1] * 20), "kH " + t[0]);
            assertEquals(t[2], v.k(), 5e-5 * Math.max(1, t[2] * 20), "kV " + t[0]);
            assertEquals(t[3], h.alpha(), 5e-5, "aH " + t[0]);
            assertEquals(t[4], v.alpha(), 5e-5, "aV " + t[0]);
        }
    }

    @Test
    void circularPolarisationAtZeroElevationAveragesTheTwoPolarisations() {
        double f = 20;
        P838.Coefficients h = P838.coefficients(f, 0, 0);
        P838.Coefficients v = P838.coefficients(f, 0, 90);
        P838.Coefficients c = P838.coefficients(f, 0, 45);
        assertEquals((h.k() + v.k()) / 2, c.k(), 1e-12);
        assertEquals((h.k() * h.alpha() + v.k() * v.alpha()) / (2 * c.k()), c.alpha(), 1e-12);
    }

    @Test
    void atVerticalIncidenceThePolarisationDoesNotMatter() {
        P838.Coefficients a = P838.coefficients(15, 90, 0);
        P838.Coefficients b = P838.coefficients(15, 90, 90);
        assertEquals(a.k(), b.k(), 1e-12);
        assertEquals(a.alpha(), b.alpha(), 1e-12);
    }

    @Test
    void specificAttenuationIsThePowerLaw() {
        P838.Coefficients c = P838.coefficients(20, 35, 45);
        assertEquals(c.k() * Math.pow(25, c.alpha()), P838.rainSpecificAttenuation(25, 20, 35, 45), 1e-12);
        assertEquals(0, P838.rainSpecificAttenuation(0, 20, 35, 45), 0);
    }

    @Test
    void attenuationGrowsWithRainRateAndWithFrequencyUpTo100GHz() {
        double prev = 0;
        for (double r = 1; r <= 200; r *= 2) {
            double g = P838.rainSpecificAttenuation(r, 30, 35, 45);
            assertTrue(g > prev);
            prev = g;
        }
        prev = 0;
        for (double f = 2; f <= 100; f *= 1.5) {
            double g = P838.rainSpecificAttenuation(25, f, 35, 45);
            assertTrue(g > prev, "f = " + f);
            prev = g;
        }
    }
}
