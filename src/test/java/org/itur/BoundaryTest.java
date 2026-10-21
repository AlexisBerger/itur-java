package org.itur;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.itur.models.P1510;
import org.itur.models.P1511;
import org.itur.models.P453;
import org.itur.models.P618;
import org.itur.models.P676;
import org.itur.models.P835;
import org.itur.models.P836;
import org.itur.models.P837;
import org.itur.models.P838;
import org.itur.models.P839;
import org.itur.models.P840;
import org.junit.jupiter.api.Test;

/**
 * Behaviour at the limits of the domains: poles and the longitude seam of the maps, the ends of the
 * probability, elevation and frequency ranges, the places where a Recommendation switches from one
 * formula to another, and degenerate inputs (no rain, no path, tiny or huge values).
 */
class BoundaryTest {

    private static final double LAT = 48.85;
    private static final double LON = 2.35;
    private static final double EPS = 1e-4;

    private static boolean finite(double v) {
        return Double.isFinite(v);
    }

    /** Relative difference between two values. */
    private static double rel(double a, double b) {
        return Math.abs(a - b) / Math.max(Math.abs(a), Math.abs(b));
    }

    // ---- maps: poles and longitude seam ---------------------------------------------------------

    @Test
    void mapsAtTheNorthPole() {
        assertTrue(finite(P839.rainHeight(90, 0)));
        assertTrue(finite(P1510.surfaceMeanTemperature(90, 0)));
        assertTrue(finite(P1511.topographicAltitude(90, 0)));
        assertTrue(finite(P837.rainfallRate(90, 0, 0.01)));
        assertTrue(finite(P836.totalWaterVapourContent(90, 0, 1, null)));
        assertTrue(finite(P453.mapWetTermRadioRefractivity(90, 0, 50)));
        assertTrue(finite(P840.columnarContentReducedLiquid(90, 0, 1)));
    }

    @Test
    void mapsAtTheSouthPole() {
        assertTrue(finite(P839.rainHeight(-90, 0)));
        assertTrue(finite(P1510.surfaceMeanTemperature(-90, 0)));
        assertTrue(finite(P1511.topographicAltitude(-90, 0)));
        assertTrue(finite(P837.rainfallRate(-90, 0, 0.01)));
        assertTrue(finite(P840.columnarContentReducedLiquid(-90, 0, 1)));
        // inherited from ITU-Rpy: the P.836 and P.453 maps do not cover the last latitude row
        assertTrue(Double.isNaN(P836.totalWaterVapourContent(-90, 0, 1, null)));
        assertTrue(Double.isNaN(P453.mapWetTermRadioRefractivity(-90, 0, 50)));
        assertTrue(finite(P836.totalWaterVapourContent(-89.9999, 0, 1, null)));
        assertTrue(finite(P453.mapWetTermRadioRefractivity(-89.9999, 0, 50)));
    }

    @Test
    void mapsAreContinuousAcrossTheLongitudeSeam() {
        double[][] seam = {{-180, 180}, {0, 360}};
        for (double[] s : seam) {
            assertEquals(P836.totalWaterVapourContent(LAT, s[0], 1, null),
                P836.totalWaterVapourContent(LAT, s[1], 1, null), 1e-9);
            assertEquals(P837.rainfallRate(LAT, s[0], 0.01), P837.rainfallRate(LAT, s[1], 0.01), 1e-9);
            assertEquals(P1511.topographicAltitude(LAT, s[0]), P1511.topographicAltitude(LAT, s[1]), 1e-9);
            assertEquals(P1510.surfaceMeanTemperature(LAT, s[0]), P1510.surfaceMeanTemperature(LAT, s[1]), 1e-9);
            assertEquals(P839.isotherm0(LAT, s[0]), P839.isotherm0(LAT, s[1]), 1e-9);
            assertEquals(P840.columnarContentReducedLiquid(LAT, s[0], 1),
                P840.columnarContentReducedLiquid(LAT, s[1], 1), 1e-9);
            assertEquals(P453.mapWetTermRadioRefractivity(LAT, s[0], 50),
                P453.mapWetTermRadioRefractivity(LAT, s[1], 50), 1e-9);
        }
        // and just beside the seam
        assertTrue(rel(P836.totalWaterVapourContent(LAT, 359.9999, 1, null),
            P836.totalWaterVapourContent(LAT, 360, 1, null)) < 1e-3);
        assertTrue(rel(P836.totalWaterVapourContent(LAT, -179.9999, 1, null),
            P836.totalWaterVapourContent(LAT, 180, 1, null)) < 1e-3);
        assertTrue(Math.abs(P1511.topographicAltitude(LAT, 359.9999) - P1511.topographicAltitude(LAT, 0)) < 1e-3);
    }

    @Test
    void theLargestAllowedLongitudesAreWrapped() {
        assertEquals(P839.isotherm0(LAT, 10), P839.isotherm0(LAT, 10 + 3600), 1e-9);
        assertEquals(P839.isotherm0(LAT, -10), P839.isotherm0(LAT, -10 - 3600), 1e-9);
    }

    // ---- probabilities ----------------------------------------------------------------------------

    @Test
    void p836ProbabilityLimits() {
        double at01 = P836.totalWaterVapourContent(LAT, LON, 0.1, null);
        assertTrue(rel(at01, P836.totalWaterVapourContent(LAT, LON, 0.0999, null)) < 1e-3); // extrapolated below
        assertTrue(P836.totalWaterVapourContent(LAT, LON, 0.05, null) > at01);
        assertTrue(finite(P836.totalWaterVapourContent(LAT, LON, 99, null)));
        assertTrue(Double.isNaN(P836.totalWaterVapourContent(LAT, LON, 99.0001, null))); // not tabulated
        assertTrue(Double.isNaN(P836.totalWaterVapourContent(LAT, LON, 100, null)));
        // no jump at a tabulated probability
        assertTrue(rel(P836.totalWaterVapourContent(LAT, LON, 1 - 1e-9, null),
            P836.totalWaterVapourContent(LAT, LON, 1 + 1e-9, null)) < 1e-6);
    }

    @Test
    void p840ProbabilityLimits() {
        assertTrue(finite(P840.columnarContentReducedLiquid(LAT, LON, 0.1)));
        assertTrue(finite(P840.columnarContentReducedLiquid(LAT, LON, 99)));
        assertThrows(IllegalArgumentException.class, () -> P840.columnarContentReducedLiquid(LAT, LON, 99.0001));
        assertThrows(IllegalArgumentException.class, () -> P840.columnarContentReducedLiquid(LAT, LON, 100));
    }

    @Test
    void p837ProbabilityLimits() {
        double p0 = P837.rainfallProbability(LAT, LON);
        double justBelow = P837.rainfallRate(LAT, LON, p0 - 1e-6);
        assertTrue(justBelow >= 0 && justBelow < 0.01, "the rate vanishes at the probability of rain: " + justBelow);
        assertEquals(0, P837.rainfallRate(LAT, LON, p0 + 1e-9), 0);
        assertEquals(0, P837.rainfallRate(LAT, LON, 100), 0);
        double rare = P837.rainfallRate(LAT, LON, 1e-5);
        assertTrue(rare > 100 && finite(rare));
        // the map value (p = 0.01) and the monthly statistics (any other p) agree around 0.01 %
        assertTrue(rel(P837.rainfallRate(LAT, LON, 0.01), P837.rainfallRate(LAT, LON, 0.01 - 1e-9)) < 1e-3);
    }

    @Test
    void p453ProbabilityLimits() {
        for (double p : new double[] {0.05, 0.1, 50, 99}) {
            assertTrue(finite(P453.mapWetTermRadioRefractivity(LAT, LON, p)), "p = " + p);
        }
        assertTrue(finite(P453.dn65(LAT, LON, 0.1)) && finite(P453.dn65(LAT, LON, 99.9)));
        assertThrows(IllegalArgumentException.class, () -> P453.dn65(LAT, LON, 99.95));
    }

    @Test
    void p618ProbabilityLimits() {
        double a1 = P618.rainAttenuation(20, LON, 20, 20, null, 0.9999, 30.0, 45, null);
        double a2 = P618.rainAttenuation(20, LON, 20, 20, null, 1.0001, 30.0, 45, null);
        assertTrue(rel(a1, a2) < 1e-3, "no jump at p = 1 %");
        double rare = P618.rainAttenuation(LAT, LON, 20, 35, null, 0.001, null, 45, null);
        double common = P618.rainAttenuation(LAT, LON, 20, 35, null, 5, null, 45, null);
        assertTrue(finite(rare) && finite(common) && rare > common);
        // outside the validity range of the method (0.001 to 5 %) a value is still computed
        assertTrue(finite(P618.rainAttenuation(LAT, LON, 20, 35, null, 10, null, 45, null)));
        assertTrue(finite(P618.rainAttenuation(LAT, LON, 20, 35, null, 0.0001, null, 45, null)));
    }

    // ---- elevation --------------------------------------------------------------------------------

    @Test
    void rainAttenuationFromTheHorizonToTheZenith() {
        double horizon = P618.rainAttenuation(LAT, LON, 20, 1e-3);
        double zenith = P618.rainAttenuation(LAT, LON, 20, 90);
        assertTrue(finite(horizon) && finite(zenith));
        assertTrue(horizon > 5 * zenith);
        assertTrue(zenith > 0);
    }

    @Test
    void gasAttenuationFromTheHorizonToTheZenith() {
        for (P676.Mode mode : P676.Mode.values()) {
            double low = P676.gaseousAttenuationSlantPath(20, 0.5, 7.5, 1013.25, 288.15, null, null, mode);
            double zenith = P676.gaseousAttenuationSlantPath(20, 90, 7.5, 1013.25, 288.15, null, null, mode);
            assertTrue(finite(low) && finite(zenith) && low > 10 * zenith, mode + ": " + low + " / " + zenith);
        }
    }

    @Test
    void scintillationAndCloudsAtLowElevation() {
        double low = P618.scintillationAttenuation(LAT, LON, 20, 1, 1, 1.2, 0.5, null, null, null, 1000);
        double zenith = P618.scintillationAttenuation(LAT, LON, 20, 90, 1, 1.2, 0.5, null, null, null, 1000);
        assertTrue(finite(low) && finite(zenith) && low > 10 * zenith);
        assertEquals(P840.cloudAttenuation(LAT, LON, 90, 20, 1, 1.0) / Math.sin(Math.toRadians(2)),
            P840.cloudAttenuation(LAT, LON, 2, 20, 1, 1.0), 1e-9);
    }

    @Test
    void inclinedPathAroundTheSwitchBetweenTheTwoFormulas() {
        // Annex 2 uses a cosecant law above 5 degrees and an exact integral below: the two differ by
        // a few percent where they meet
        double below = P676.gaseousAttenuationInclinedPath(20, 4.999999, 7.5, 1013.25, 288.15, 0, 8, P676.Mode.APPROX);
        double above = P676.gaseousAttenuationInclinedPath(20, 5.000001, 7.5, 1013.25, 288.15, 0, 8, P676.Mode.APPROX);
        assertTrue(rel(below, above) < 0.05, below + " vs " + above);
        assertEquals(P676.gaseousAttenuationInclinedPath(20, 90, 7.5, 1013.25, 288.15, 0, 8, P676.Mode.APPROX),
            P676.gaseousAttenuationInclinedPath(20, 89.999999, 7.5, 1013.25, 288.15, 0, 8, P676.Mode.APPROX), 1e-5);
        assertTrue(finite(P676.gaseousAttenuationInclinedPath(20, 0, 7.5, 1013.25, 288.15, 0, 8, P676.Mode.APPROX)));
    }

    @Test
    void crossPolarisationAtTheLimitsOfTheMethod() {
        assertTrue(finite(P618.rainCrossPolarizationDiscrimination(5, 20, 35, 0.01, 45)));
        double below = P618.rainCrossPolarizationDiscrimination(5, 20, 59.999, 0.01, 45);
        double above = P618.rainCrossPolarizationDiscrimination(5, 20, 60.001, 0.01, 45); // logs a warning
        assertTrue(rel(below, above) < 1e-3);
        assertTrue(finite(P618.rainCrossPolarizationDiscrimination(5, 20, 90, 0.01, 45)));
    }

    // ---- frequency --------------------------------------------------------------------------------

    @Test
    void frequencyLimits() {
        assertTrue(P838.rainSpecificAttenuation(100, 1, 0, 0) > 0);
        assertTrue(P838.rainSpecificAttenuation(100, 1000, 90, 90) > 0);
        assertTrue(P840.specificAttenuationCoefficient(1000, -20) > 0);
        assertTrue(P840.specificAttenuationCoefficient(1, 40) > 0);
        assertTrue(P676.gammaExact(1, 1013.25, 7.5, 288.15) > 0);
        assertTrue(P676.gammaExact(350, 1013.25, 7.5, 288.15) > 0);
        assertTrue(finite(P676.gaseousAttenuationSlantPath(350, 10, 7.5, 1013.25, 288.15, null, null,
            P676.Mode.EXACT)));
        assertTrue(finite(P618.rainAttenuation(LAT, LON, 1, 35)) && finite(P618.rainAttenuation(LAT, LON, 1000, 35)));
        // cross-polarisation: 4 to 55 GHz
        assertTrue(finite(P618.rainCrossPolarizationDiscrimination(5, 4, 35, 0.01, 45)));
        assertTrue(finite(P618.rainCrossPolarizationDiscrimination(5, 55, 35, 0.01, 45)));
        assertThrows(IllegalArgumentException.class,
            () -> P618.rainCrossPolarizationDiscrimination(5, 3.9999, 35, 0.01, 45));
        assertThrows(IllegalArgumentException.class,
            () -> P618.rainCrossPolarizationDiscrimination(5, 55.0001, 35, 0.01, 45));
        assertThrows(IllegalArgumentException.class, () -> P840.specificAttenuationCoefficient(1000.001, 0));
    }

    // ---- where a Recommendation switches formula ---------------------------------------------------

    @Test
    void rainAttenuationAroundLatitude36() {
        // above 25 degrees of elevation the correction beta is continuous in latitude
        double south = P618.rainAttenuation(35.999, LON, 20, 30, null, 0.1, 30.0, 45, null);
        double north = P618.rainAttenuation(36.001, LON, 20, 30, null, 0.1, 30.0, 45, null);
        assertTrue(rel(south, north) < 1e-3);
        // at 25 degrees or less P.618-13 adds a term that does not vanish at 36 degrees of latitude:
        // the Recommendation itself is discontinuous there
        double lowSouth = P618.rainAttenuation(35.999, LON, 20, 20, null, 0.1, 30.0, 45, null);
        double lowNorth = P618.rainAttenuation(36.001, LON, 20, 20, null, 0.1, 30.0, 45, null);
        assertTrue(lowSouth > 1.1 * lowNorth, lowSouth + " vs " + lowNorth);
        // and the elevation threshold of 25 degrees is continuous at latitude 36 and above
        assertTrue(rel(P618.rainAttenuation(40, LON, 20, 24.9999, null, 0.1, 30.0, 45, null),
            P618.rainAttenuation(40, LON, 20, 25.0001, null, 0.1, 30.0, 45, null)) < 1e-3);
    }

    @Test
    void zenithWaterVapourAroundTwentyGigahertz() {
        double below = P676.zenithWaterVapourAttenuation(LAT, LON, 1, 19.9999, 20.0, 1.0);
        double above = P676.zenithWaterVapourAttenuation(LAT, LON, 1, 20, 20.0, 1.0);
        assertTrue(rel(below, above) < 0.01);
    }

    @Test
    void standardAtmosphereAtTheEdgesOfItsLayers() {
        // 86 km: P.835 itself has a 0.08 K step between its two formulas
        assertTrue(Math.abs(P835.standardTemperature(85.9999) - P835.standardTemperature(86)) < 0.1);
        for (double h : new double[] {11, 20, 32, 47, 51, 71, 91}) {
            assertTrue(Math.abs(P835.standardTemperature(h - 1e-6) - P835.standardTemperature(h + 1e-6)) < 0.1,
                "h = " + h);
            assertTrue(rel(P835.standardPressure(h - 1e-6), P835.standardPressure(h + 1e-6)) < 1e-3, "h = " + h);
        }
        assertEquals(1e-62, P835.standardPressure(100.0001), 0); // above 100 km: floor value
        assertTrue(P835.standardPressure(100) > 1e-5);
    }

    // ---- degenerate inputs --------------------------------------------------------------------------

    @Test
    void noRainNoAttenuation() {
        assertEquals(0, P618.rainAttenuation(LAT, LON, 20, 35, null, 0.01, 0.0, 45, null), 0);
        assertEquals(0, P618.rainAttenuation(LAT, LON, 20, 35, null, 0.01, 30.0, 45, 0.0), 0);
        assertEquals(0, P838.rainSpecificAttenuation(0, 20, 35, 45), 0);
        assertTrue(P618.rainAttenuation(LAT, LON, 20, 35, null, 0.01, 1e-12, 45, null) < 1e-9);
    }

    @Test
    void stationAtOrAboveTheRainHeight() {
        double hr = P839.rainHeight(LAT, LON);
        double at = P618.rainAttenuation(LAT, LON, 20, 35, hr, 0.01, 30.0, 45, null);
        double above = P618.rainAttenuation(LAT, LON, 20, 35, hr + 2, 0.01, 30.0, 45, null);
        assertTrue(finite(at) && finite(above) && at >= 0 && above >= 0);
        assertTrue(at < 0.01 && above < 0.01, "there is no rain path: " + at + " " + above);
    }

    @Test
    void extremeRainfallRates() {
        double heavy = P618.rainAttenuation(LAT, LON, 20, 35, null, 0.01, 1000.0, 45, null);
        assertTrue(finite(heavy) && heavy > P618.rainAttenuation(LAT, LON, 20, 35, null, 0.01, 100.0, 45, null));
        assertTrue(finite(P838.rainSpecificAttenuation(1000, 100, 0, 45)));
    }

    @Test
    void scintillationWithVeryLargeAntennaVanishes() {
        // beyond the aperture-averaging limit (x >= 7) the Recommendation gives no scintillation
        assertEquals(0, P618.scintillationAttenuation(LAT, LON, 20, 35, 1, 50, 0.5, null, null, null, 1000), 0);
        assertTrue(P618.scintillationAttenuation(LAT, LON, 20, 35, 0.01, 1.2, 0.5, null, null, null, 1000)
            > P618.scintillationAttenuation(LAT, LON, 20, 35, 50, 1.2, 0.5, null, null, null, 1000));
        assertEquals(0, P618.scintillationAttenuation(LAT, LON, 20, 35, 99, 1.2, 0.5, null, null, null, 1000), 0);
    }

    @Test
    void thinAndColdAir() {
        // 1 hPa, no vapour, 220 K: a thin oxygen-only atmosphere still gives a small positive value
        double thin = P676.gammaExact(60, 1, 0.0, 220);
        assertTrue(finite(thin) && thin > 0 && thin < 0.01);
        for (double t : new double[] {200, 330}) {
            assertTrue(finite(P676.gammaExact(20, 1013.25, 7.5, t)), "T = " + t);
        }
        assertEquals(0, P676.gammaWExact(20, 1013.25, 0, 288.15), 0);
    }

    @Test
    void totalAttenuationAtTheEndsOfTheRange() {
        for (double p : new double[] {0.001, 50}) {
            assertTrue(finite(Itur.atmosphericAttenuationSlantPath(LAT, LON, 20, 35, p, 1.2).total()), "p = " + p);
        }
        assertTrue(finite(Itur.atmosphericAttenuationSlantPath(LAT, LON, 20, 90, 0.1, 1.2).total()));
        assertTrue(Itur.atmosphericAttenuationSlantPath(LAT, LON, 20, 0.1, 0.1, 1.2).total()
            > 20 * Itur.atmosphericAttenuationSlantPath(LAT, LON, 20, 90, 0.1, 1.2).total());
        assertTrue(Itur.atmosphericAttenuationSlantPath(-90, 0, 20, 35, 0.1, 1.2).total() >= 0
            || Double.isNaN(Itur.atmosphericAttenuationSlantPath(-90, 0, 20, 35, 0.1, 1.2).total()));
        for (double lat : new double[] {-89.5, 89.5}) {
            assertTrue(finite(Itur.atmosphericAttenuationSlantPath(lat, 10, 20, 35, 0.1, 1.2).total()), "lat " + lat);
        }
    }

    @Test
    void noVapourInTheCombinedModelStillGivesDryAir() {
        Itur.Attenuation a = Itur.atmosphericAttenuationSlantPath(LAT, LON, 20, 35, 0.1, 1.2,
            Itur.Options.builder().rhoGPerM3(0).build());
        assertTrue(a.gas() > 0 && finite(a.total()));
    }
}
