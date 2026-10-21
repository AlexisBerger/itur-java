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
import org.itur.util.Check;
import org.junit.jupiter.api.Test;

/** Every public entry point rejects meaningless arguments with a message that names the argument. */
class InputValidationTest {

    private static final double LAT = 48.85;
    private static final double LON = 2.35;
    private static final double NAN = Double.NaN;

    /** A call that is expected to fail. */
    @FunctionalInterface
    private interface Call {
        void run() throws Throwable;
    }

    private static void rejects(String argument, Call call) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, call::run);
        assertTrue(e.getMessage() != null && e.getMessage().contains(argument),
            "message \"" + e.getMessage() + "\" should mention \"" + argument + "\"");
    }

    @Test
    void checkHelpers() {
        assertEquals(3, Check.range("x", 3, 0, 5), 0);
        rejects("x", () -> Check.range("x", NAN, 0, 5));
        rejects("x", () -> Check.range("x", 6, 0, 5));
        rejects("x", () -> Check.finite("x", Double.POSITIVE_INFINITY));
        rejects("x", () -> Check.positive("x", 0));
        rejects("x", () -> Check.nonNegative("x", -1e-12));
        Check.nonNegative("x", 0);
        Check.optionalPositive("x", null);
        rejects("x", () -> Check.optionalPositive("x", -1.0));
        Check.optionalFinite("x", null);
        Check.optionalNonNegative("x", null);
    }

    @Test
    void positions() {
        rejects("latitude", () -> P839.rainHeight(90.5, LON));
        rejects("latitude", () -> P1510.surfaceMeanTemperature(-91, LON));
        rejects("latitude", () -> P1511.topographicAltitude(NAN, LON));
        rejects("longitude", () -> P839.rainHeight(LAT, NAN));
        rejects("longitude", () -> P837.rainfallProbability(LAT, Double.POSITIVE_INFINITY));
        rejects("latitude", () -> P836.totalWaterVapourContent(95, LON, 1, null));
        rejects("latitude", () -> P840.lognormalApproximationCoefficients(-100, LON));
        rejects("latitude", () -> P453.dn65(100, LON, 1));
        rejects("latitude", () -> P618.rainAttenuation(95, LON, 20, 35));
        rejects("latitude", () -> Itur.atmosphericAttenuationSlantPath(95, LON, 20, 35, 0.1, 1.2));
    }

    @Test
    void frequencies() {
        rejects("frequency", () -> P618.rainAttenuation(LAT, LON, 0, 35));
        rejects("frequency", () -> P618.rainAttenuation(LAT, LON, -1, 35));
        rejects("frequency", () -> P618.rainAttenuation(LAT, LON, NAN, 35));
        rejects("frequency", () -> P676.gammaExact(0, 1013, 7.5, 288));
        rejects("frequency", () -> P676.gamma0Exact(-5, 1013, 7.5, 288));
        rejects("frequency", () -> P840.specificAttenuationCoefficient(0, 0));
        rejects("frequency", () -> P838.coefficients(0.5, 35, 45)); // valid from 1 GHz
        rejects("frequency", () -> P838.coefficients(1500, 35, 45)); // up to 1000 GHz
        rejects("frequency", () -> Itur.atmosphericAttenuationSlantPath(LAT, LON, 0, 35, 0.1, 1.2));
        rejects("frequency", () -> P618.scintillationAttenuation(LAT, LON, 0, 35, 1, 1.2, 0.5, null, null, null, 1000));
    }

    @Test
    void elevationAngles() {
        rejects("elevation", () -> P618.rainAttenuation(LAT, LON, 20, 0));
        rejects("elevation", () -> P618.rainAttenuation(LAT, LON, 20, -5));
        rejects("elevation", () -> P618.rainAttenuation(LAT, LON, 20, 91));
        rejects("elevation", () -> P676.gaseousAttenuationSlantPath(20, 0, 7.5, 1013, 288, null, null,
            P676.Mode.APPROX));
        rejects("elevation", () -> P840.cloudAttenuation(LAT, LON, 0, 20, 1, null));
        rejects("elevation", () -> P838.coefficients(20, 120, 45));
        rejects("elevation", () -> P676.gaseousAttenuationInclinedPath(20, 100, 7.5, 1013, 288, 0, 5,
            P676.Mode.APPROX));
        rejects("elevation", () -> Itur.atmosphericAttenuationSlantPath(LAT, LON, 20, 0, 0.1, 1.2));
        rejects("elevation", () -> P618.scintillationAttenuationSigma(LAT, LON, 20, 0, 1.2, 0.5, null, null, null,
            1000));
        rejects("elevation", () -> P618.rainCrossPolarizationDiscrimination(5, 20, 0, 0.01, 45));
    }

    @Test
    void probabilities() {
        rejects("percentage", () -> P618.rainAttenuation(LAT, LON, 20, 35, null, 0.0, null, 45, null));
        rejects("percentage", () -> P618.rainAttenuation(LAT, LON, 20, 35, null, -1.0, null, 45, null));
        rejects("percentage", () -> P618.rainAttenuation(LAT, LON, 20, 35, null, 101.0, null, 45, null));
        rejects("percentage", () -> P837.rainfallRate(LAT, LON, 0));
        rejects("percentage", () -> P837.rainfallRate(LAT, LON, -3));
        rejects("percentage", () -> P836.totalWaterVapourContent(LAT, LON, 100.5, null));
        rejects("percentage", () -> P836.surfaceWaterVapourDensity(LAT, LON, NAN, null));
        rejects("percentage", () -> P840.columnarContentReducedLiquid(LAT, LON, 0));
        rejects("percentage", () -> P453.mapWetTermRadioRefractivity(LAT, LON, 0));
        rejects("percentage", () -> Itur.atmosphericAttenuationSlantPath(LAT, LON, 20, 35, 0, 1.2));
        rejects("percentage", () -> Itur.atmosphericAttenuationSlantPath(LAT, LON, 20, 35, 150, 1.2));
        rejects("percentage", () -> P618.rainCrossPolarizationDiscrimination(5, 20, 35, 0, 45));
        rejects("percentage", () -> P618.scintillationAttenuation(LAT, LON, 20, 35, 0, 1.2, 0.5, null, null, null,
            1000));
        rejects("P0", () -> P618.rainAttenuationProbability(LAT, LON, 35, null, null, 120.0));
        rejects("pk", () -> P618.fitRainAttenuationToLognormal(LAT, LON, 20, 35, null, 0, 45));
    }

    @Test
    void physicalQuantities() {
        rejects("rainfall rate", () -> P838.rainSpecificAttenuation(-1, 20, 35, 45));
        rejects("rainfall rate", () -> P837.unavailabilityFromRainfallRate(LAT, LON, -5));
        rejects("rainfall rate", () -> P618.rainAttenuation(LAT, LON, 20, 35, null, 0.01, -3.0, 45, null));
        rejects("pressure", () -> P676.gammaExact(20, 0, 7.5, 288));
        rejects("water vapour density", () -> P676.gammaExact(20, 1013, -1, 288));
        rejects("temperature", () -> P676.gammaExact(20, 1013, 7.5, 0));
        rejects("temperature", () -> P453.dryTermRadioRefractivity(1000, -5));
        rejects("temperature", () -> P453.wetTermRadioRefractivity(10, -300));
        rejects("humidity", () -> P453.waterVapourPressure(15, 1013, 120));
        rejects("humidity", () -> P453.waterVapourPressure(15, 1013, -1));
        rejects("pressure", () -> P453.saturationVapourPressure(15, 0, P453.Hydrometeor.WATER));
        rejects("height", () -> P835.standardPressure(NAN));
        rejects("height", () -> P835.standardTemperature(Double.POSITIVE_INFINITY));
        rejects("scale height", () -> P835.standardWaterVapourDensity(1, 0, 7.5));
        rejects("path length", () -> P676.gaseousAttenuationTerrestrialPath(-1, 20, 0, 7.5, 1013, 288,
            P676.Mode.APPROX));
        rejects("water vapour content", () -> P676.zenithWaterVapourAttenuation(LAT, LON, 1, 20, 0.0, null));
        rejects("antenna diameter", () -> P618.scintillationAttenuationSigma(LAT, LON, 20, 35, 0, 0.5, null, null,
            null, 1000));
        rejects("antenna efficiency", () -> P618.scintillationAttenuationSigma(LAT, LON, 20, 35, 1.2, 1.5, null,
            null, null, 1000));
        rejects("antenna efficiency", () -> P618.scintillationAttenuationSigma(LAT, LON, 20, 35, 1.2, 0, null, null,
            null, 1000));
        rejects("turbulent layer", () -> P618.scintillationAttenuationSigma(LAT, LON, 20, 35, 1.2, 0.5, null, null,
            null, -1));
        rejects("margin", () -> P618.siteDiversityRainOutageProbability(LAT, LON, 0, 35, 43.3, 5.37, 3, 35, 20, 45,
            null, null));
        rejects("Ap", () -> P618.rainCrossPolarizationDiscrimination(0, 20, 35, 0.01, 45));
        rejects("antenna diameter", () -> Itur.atmosphericAttenuationSlantPath(LAT, LON, 20, 35, 0.1, 0));
    }

    @Test
    void anUnreachableRainfallRateIsReportedInTheTermsOfTheCaller() {
        rejects("rainfall rate 1000.0 mm/h", () -> P837.unavailabilityFromRainfallRate(LAT, LON, 1000));
    }

    @Test
    void optionalValuesAreCheckedWhenGiven() {
        rejects("altitude", () -> P836.totalWaterVapourContent(LAT, LON, 1, NAN));
        rejects("slant path length", () -> P618.rainAttenuation(LAT, LON, 20, 35, null, 0.01, null, 45, -1.0));
        rejects("altitude", () -> P618.rainAttenuation(LAT, LON, 20, 35, NAN, 0.01, null, 45, null));
        rejects("reduced liquid", () -> P840.cloudAttenuation(LAT, LON, 35, 20, 1, -1.0));
        rejects("water vapour content", () -> P676.gaseousAttenuationSlantPath(20, 35, 7.5, 1013, 288, -4.0, 0.0,
            P676.Mode.APPROX));
    }

    @Test
    void optionsOfTheCombinedModelAreChecked() {
        rejects("efficiency", () -> Itur.Options.builder().eta(0).build());
        rejects("efficiency", () -> Itur.Options.builder().eta(1.2).build());
        rejects("humidity", () -> Itur.Options.builder().hPercent(130).build());
        rejects("temperature", () -> Itur.Options.builder().tKelvin(-1).build());
        rejects("pressure", () -> Itur.Options.builder().pHPa(0).build());
        rejects("rainfall rate", () -> Itur.Options.builder().r001MmPerHour(-2).build());
        rejects("water vapour density", () -> Itur.Options.builder().rhoGPerM3(-2).build());
        rejects("water vapour content", () -> Itur.Options.builder().vtKgPerM2(0).build());
        rejects("turbulent layer", () -> Itur.Options.builder().hLMeters(0).build());
        rejects("slant path", () -> Itur.Options.builder().lsKm(-1).build());
        assertThrows(NullPointerException.class, () -> Itur.Options.builder().gasMode(null).build());
        assertThrows(NullPointerException.class,
            () -> Itur.atmosphericAttenuationSlantPath(LAT, LON, 20, 35, 0.1, 1.2, null));
        assertThrows(NullPointerException.class, () -> P676.gaseousAttenuationSlantPath(20, 35, 7.5, 1013, 288, null,
            null, null));
    }

    @Test
    void validArgumentsAtTheLimitsAreAccepted() {
        for (double lat : new double[] {-90, 90}) {
            P839.rainHeight(lat, 0); // NaN at the poles of some maps, but not an error
            P1510.surfaceMeanTemperature(lat, 360);
            P837.rainfallProbability(lat, -180);
        }
        assertTrue(P618.rainAttenuation(LAT, LON, 20, 90) > 0);
        assertTrue(P618.rainAttenuation(LAT, LON, 20, 1e-3) > 0);
        assertTrue(P837.rainfallRate(LAT, LON, 100) == 0);
        assertTrue(P838.coefficients(1, 0, 0).k() > 0 && P838.coefficients(1000, 0, 0).k() > 0);
        assertTrue(P838.rainSpecificAttenuation(0, 20, 35, 45) == 0);
        assertTrue(P453.waterVapourPressure(15, 1013, 0) == 0);
        assertTrue(P453.waterVapourPressure(15, 1013, 100) > 0);
        assertTrue(Double.isFinite(P835.standardPressure(-0.5)));
    }

    @Test
    void locationsOutsideAMapGiveNaNInsteadOfAnError() {
        // inside the valid ranges, a point that a map does not cover is NaN, as in ITU-Rpy
        Itur.Attenuation a = Itur.atmosphericAttenuationSlantPath(89, 359, 20, 35, 0.1, 1.2);
        assertTrue(Double.isNaN(a.gas()) && Double.isNaN(a.total()));
        assertTrue(Double.isNaN(P676.zenithWaterVapourAttenuation(89, 359, 1, 20, null, null)));
    }
}
