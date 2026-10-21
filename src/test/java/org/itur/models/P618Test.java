package org.itur.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import org.itur.util.Special;

/** P.618-13: monotonic behaviour of the prediction methods and consistency between them. */
class P618Test {

    private static final double LAT = 48.85;
    private static final double LON = 2.35;

    private static double rain(double f, double el, double p, Double r001) {
        return P618.rainAttenuation(LAT, LON, f, el, null, p, r001, 45, null);
    }

    @Test
    void rainAttenuationDecreasesWithTheExceedanceProbability() {
        double prev = Double.MAX_VALUE;
        for (double p : new double[] {0.001, 0.003, 0.01, 0.03, 0.1, 0.3, 1, 3, 5}) {
            double a = rain(20, 35, p, null);
            assertTrue(a < prev && a > 0, "p = " + p);
            prev = a;
        }
    }

    @Test
    void rainAttenuationIncreasesWithFrequencyAndDecreasesWithElevation() {
        double prev = 0;
        for (double f = 4; f <= 50; f += 2) {
            double a = rain(f, 35, 0.01, null);
            assertTrue(a > prev, "f = " + f);
            prev = a;
        }
        prev = Double.MAX_VALUE;
        for (double el = 10; el <= 90; el += 10) {
            double a = rain(20, el, 0.01, null);
            assertTrue(a < prev, "el = " + el);
            prev = a;
        }
    }

    @Test
    void rainAttenuationIncreasesWithTheRainfallRate() {
        double prev = 0;
        for (double r : new double[] {1, 10, 30, 60, 100}) {
            double a = rain(20, 35, 0.01, r);
            assertTrue(a > prev, "R = " + r);
            prev = a;
        }
        assertTrue(rain(20, 35, 0.01, 0.0001) < 0.01);
    }

    @Test
    void shortFormUsesTheDefaults() {
        assertEquals(rain(20, 35, 0.01, null), P618.rainAttenuation(LAT, LON, 20, 35), 1e-12);
    }

    @Test
    void anExplicitSlantPathAndStationAltitudeAreUsed() {
        double base = P618.rainAttenuation(LAT, LON, 20, 35, 0.0, 0.01, 30.0, 45, null);
        double shorter = P618.rainAttenuation(LAT, LON, 20, 35, 0.0, 0.01, 30.0, 45, 1.0);
        double higher = P618.rainAttenuation(LAT, LON, 20, 35, 1.5, 0.01, 30.0, 45, null);
        assertTrue(shorter < base);
        assertTrue(higher < base);
    }

    @Test
    void polarisationMattersAtLowElevation() {
        double h = P618.rainAttenuation(LAT, LON, 20, 20, null, 0.01, 30.0, 0, null);
        double v = P618.rainAttenuation(LAT, LON, 20, 20, null, 0.01, 30.0, 90, null);
        assertTrue(Math.abs(h - v) > 1e-3);
    }

    @Test
    void probabilityOfRainAttenuationCoversTheProbabilityOfRain() {
        double p0 = P837.rainfallProbability(LAT, LON);
        double p = P618.rainAttenuationProbability(LAT, LON, 35, null, null, null);
        assertTrue(p >= p0 && p < 100, p + " vs " + p0);
        assertEquals(p, P618.rainAttenuationProbability(LAT, LON, 35, null, null, p0), 1e-9);
    }

    @Test
    void lognormalFitIsARoughModelOfTheAttenuationStatistics() {
        double pk = P837.rainfallProbability(LAT, LON);
        P618.LognormalFit fit = P618.fitRainAttenuationToLognormal(LAT, LON, 20, 35, null, pk, 45);
        assertTrue(fit.sigmaLnA() > 0);
        double prev = Double.MAX_VALUE;
        for (double p : new double[] {0.01, 0.02, 0.05, 0.1, 0.2, 0.5, 1, 2, 3}) {
            double a = Math.exp(fit.mLnA() + fit.sigmaLnA() * Special.normPpf(1 - p / pk));
            double ref = rain(20, 35, p, null);
            assertTrue(a < prev, "monotonic at p = " + p);
            prev = a;
            // sanity bounds: a two-parameter log-normal does not follow the P.618 curve closely
            assertTrue(a > 0.6 * ref && a < 1.6 * ref, "p = " + p + ": " + a + " vs " + ref);
            if (p == 0.1) {
                assertEquals(ref, a, 0.1 * ref); // the fit is centred on the middle of the range
            }
        }
    }

    @Test
    void siteDiversityReducesTheOutageAndImprovesWithDistance() {
        double near = P618.siteDiversityRainOutageProbability(48.85, 2.35, 3, 35, 49.05, 2.10, 3, 35, 20, 45,
            null, null);
        double far = P618.siteDiversityRainOutageProbability(48.85, 2.35, 3, 35, 43.30, 5.37, 3, 35, 20, 45,
            null, null);
        assertTrue(far < near, near + " " + far);
        double swapped = P618.siteDiversityRainOutageProbability(43.30, 5.37, 3, 35, 48.85, 2.35, 3, 35, 20, 45,
            null, null);
        assertEquals(far, swapped, 1e-6 * far);
        // both sites must fade: less likely than the fade at one site alone
        double single = P618.rainAttenuationProbability(48.85, 2.35, 35, null, null, null);
        assertTrue(near < single);
    }

    @Test
    void largerMarginsMakeBothSiteOutagesLessLikely() {
        double three = P618.siteDiversityRainOutageProbability(48.85, 2.35, 3, 35, 43.30, 5.37, 3, 35, 20, 45,
            null, null);
        double ten = P618.siteDiversityRainOutageProbability(48.85, 2.35, 10, 35, 43.30, 5.37, 10, 35, 20, 45,
            null, null);
        assertTrue(ten < three);
    }

    @Test
    void crossPolarisationDiscriminationDecreasesWithRainAttenuation() {
        double prev = Double.MAX_VALUE;
        for (double ap = 1; ap <= 20; ap += 1) {
            double x = P618.rainCrossPolarizationDiscrimination(ap, 20, 35, 0.01, 45);
            assertTrue(x < prev, "Ap = " + ap);
            prev = x;
        }
    }

    @Test
    void scintillationTrends() {
        double base = P618.scintillationAttenuation(LAT, LON, 20, 35, 1, 1.2, 0.5, null, null, null, 1000);
        assertTrue(base > 0);
        assertTrue(P618.scintillationAttenuation(LAT, LON, 30, 35, 1, 1.2, 0.5, null, null, null, 1000) > base);
        assertTrue(P618.scintillationAttenuation(LAT, LON, 20, 10, 1, 1.2, 0.5, null, null, null, 1000) > base);
        assertTrue(P618.scintillationAttenuation(LAT, LON, 20, 35, 1, 3.0, 0.5, null, null, null, 1000) < base);
        assertTrue(P618.scintillationAttenuation(LAT, LON, 20, 35, 0.1, 1.2, 0.5, null, null, null, 1000) > base);
        assertTrue(P618.scintillationAttenuationSigma(LAT, LON, 20, 35, 1.2, 0.5, null, null, null, 1000) > 0);
    }

    @Test
    void scintillationUsesTheSurfaceMeteorologyWhenGiven() {
        double map = P618.scintillationAttenuationSigma(LAT, LON, 20, 35, 1.2, 0.5, null, null, null, 1000);
        double wet = P618.scintillationAttenuationSigma(LAT, LON, 20, 35, 1.2, 0.5, 25.0, 90.0, 1013.0, 1000);
        double dry = P618.scintillationAttenuationSigma(LAT, LON, 20, 35, 1.2, 0.5, 0.0, 20.0, 1013.0, 1000);
        assertTrue(wet > dry);
        assertTrue(map > dry && map < wet * 1.5);
    }
}
