package org.itur.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** P.676-13: physical invariants of the line-by-line model and of the slant-path methods. */
class P676Test {

    private static final double P = 1013.25;
    private static final double RHO = 7.5;
    private static final double T = 288.15;

    @Test
    void totalIsTheSumOfDryAirAndWaterVapour() {
        for (double f = 1; f <= 350; f += 7.3) {
            assertEquals(P676.gamma0Exact(f, P, RHO, T) + P676.gammaWExact(f, P, RHO, T),
                P676.gammaExact(f, P, RHO, T), 1e-12, "f = " + f);
        }
    }

    @Test
    void noVapourNoWaterAttenuation() {
        assertEquals(0, P676.gammaWExact(20, P, 0, T), 0);
        assertEquals(P676.gamma0Exact(20, P, 0, T), P676.gammaExact(20, P, 0, T), 1e-12);
    }

    @Test
    void waterVapourAttenuationIsAlmostProportionalToTheDensity() {
        double one = P676.gammaWExact(20, P, 5, T);
        double two = P676.gammaWExact(20, P, 10, T);
        assertTrue(two / one > 1.8 && two / one < 2.4, "ratio " + two / one);
    }

    @Test
    void oxygenAbsorptionPeaksAt60GHz() {
        double peak = P676.gamma0Exact(60, P, RHO, T);
        assertTrue(peak > 10, "gamma0(60 GHz) = " + peak);
        assertTrue(peak > 100 * P676.gamma0Exact(30, P, RHO, T));
        assertTrue(peak > 5 * P676.gamma0Exact(100, P, RHO, T));
        // the 118.75 GHz line is a local maximum
        assertTrue(P676.gamma0Exact(118.75, P, RHO, T) > 2 * P676.gamma0Exact(105, P, RHO, T));
    }

    @Test
    void waterVapourLinesAt22And183GHz() {
        assertTrue(P676.gammaWExact(22.235, P, RHO, T) > P676.gammaWExact(10, P, RHO, T) * 3);
        assertTrue(P676.gammaWExact(183.31, P, RHO, T) > 10 * P676.gammaWExact(140, P, RHO, T));
    }

    @Test
    void attenuationIsPositiveEverywhere() {
        for (double f = 1; f <= 350; f += 1) {
            assertTrue(P676.gammaExact(f, P, RHO, T) > 0, "f = " + f);
        }
    }

    @Test
    void lowerPressureNarrowsTheLines() {
        // at 6 km the 60 GHz peak is much lower than at the ground
        assertTrue(P676.gamma0Exact(60, 470, RHO, 250) < P676.gamma0Exact(60, P, RHO, T));
    }

    @Test
    void equivalentHeights() {
        P676.Heights h = P676.slantInclinedPathEquivalentHeight(20, P, RHO, T);
        assertTrue(h.h0() > 4 && h.h0() < 8, "h0 = " + h.h0());
        assertTrue(h.hw() > 1.5 && h.hw() < 3, "hw = " + h.hw());
    }

    @Test
    void annex2AttenuationScalesWithTheCosecantOfTheElevation() {
        double zenith = P676.gaseousAttenuationSlantPath(20, 90, RHO, P, T, null, null, P676.Mode.APPROX);
        assertEquals(2 * zenith, P676.gaseousAttenuationSlantPath(20, 30, RHO, P, T, null, null, P676.Mode.APPROX),
            1e-12);
    }

    @Test
    void rayTracingAgreesWithTheEquivalentHeights() {
        for (double el : new double[] {10, 20, 35, 60, 90}) {
            double approx = P676.gaseousAttenuationSlantPath(20, el, RHO, P, T, null, null, P676.Mode.APPROX);
            double exact = P676.gaseousAttenuationSlantPath(20, el, RHO, P, T, null, null, P676.Mode.EXACT);
            assertEquals(approx, exact, 0.15 * approx, "el = " + el);
        }
    }

    @Test
    void attenuationIncreasesWhenTheElevationDecreases() {
        double prev = 0;
        for (double el = 90; el >= 5; el -= 5) {
            double a = P676.gaseousAttenuationSlantPath(20, el, RHO, P, T, null, null, P676.Mode.EXACT);
            assertTrue(a > prev, "el = " + el);
            prev = a;
        }
    }

    @Test
    void zenithWaterVapourAttenuationIsAlmostProportionalToTheContent() {
        double a = P676.zenithWaterVapourAttenuation(48.85, 2.35, 1, 20, 20.0, 0.0);
        double b = P676.zenithWaterVapourAttenuation(48.85, 2.35, 1, 20, 40.0, 0.0);
        assertTrue(b / a > 1.8 && b / a < 2.2, "ratio " + b / a);
    }

    @Test
    void zenithValuesFromTheMapsAreUsedWhenNothingIsGiven() {
        double a = P676.zenithWaterVapourAttenuation(48.85, 2.35, 1, 20, null, null);
        assertTrue(a > 0.1 && a < 1);
    }

    @Test
    void slantPathWithAGivenWaterVapourContentUsesIt() {
        double dry = P676.gaseousAttenuationSlantPath(20, 35, RHO, P, T, 5.0, 0.0, P676.Mode.APPROX);
        double wet = P676.gaseousAttenuationSlantPath(20, 35, RHO, P, T, 40.0, 0.0, P676.Mode.APPROX);
        assertTrue(wet > dry);
    }

    @Test
    void terrestrialPathIsTheSpecificAttenuationTimesTheLength() {
        double g = P676.gammaExact(20, P, RHO, T);
        assertEquals(10 * g, P676.gaseousAttenuationTerrestrialPath(10, 20, 0, RHO, P, T, P676.Mode.EXACT), 1e-12);
        assertEquals(0, P676.gaseousAttenuationTerrestrialPath(0, 20, 0, RHO, P, T, P676.Mode.APPROX), 0);
    }

    @Test
    void inclinedPathBetweenStations() {
        double a = P676.gaseousAttenuationInclinedPath(20, 30, RHO, P, T, 0, 8, P676.Mode.APPROX);
        assertTrue(a > 0 && Double.isFinite(a));
        assertThrows(IllegalArgumentException.class,
            () -> P676.gaseousAttenuationInclinedPath(20, 30, RHO, P, T, 11, 8, P676.Mode.APPROX));
        assertThrows(IllegalArgumentException.class,
            () -> P676.gaseousAttenuationInclinedPath(20, 30, RHO, P, T, 0, 10.5, P676.Mode.APPROX));
    }

    @Test
    @SuppressWarnings("deprecation")
    void deprecatedApproximationsReturnTheTotal() {
        assertEquals(P676.gammaExact(20, P, RHO, T), P676.gamma0Approx(20, P, RHO, T), 0);
        assertEquals(P676.gammaExact(20, P, RHO, T), P676.gammaWApprox(20, P, RHO, T), 0);
    }
}
