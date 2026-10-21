package org.itur;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * End-to-end values computed with ITU-Rpy 0.4.0 on the pristine float64 map data (Paris,
 * 48.85 N 2.35 E, 20 GHz, 35 deg elevation). The maps of this library are stored as float32, hence
 * the relative tolerance of 1e-5 (observed differences are ~1e-8).
 */
class ReferenceValuesTest {

    private static final double LAT = 48.85;
    private static final double LON = 2.35;
    private static final double TOL = 1e-5;

    private static void close(double expected, double actual) {
        assertEquals(expected, actual, TOL * Math.abs(expected));
    }

    @Test
    void mapsAndRain() {
        close(0.08503790725000011, P1511.topographicAltitude(LAT, LON));
        close(2.5886155555555557, P839.rainHeight(LAT, LON));
        close(284.0319555555556, P1510.surfaceMeanTemperature(LAT, LON));
        close(27.3564, P837.rainfallRate(LAT, LON, 0.01));
        close(1.7800703645750722, P837.rainfallRate(LAT, LON, 1.0));
        close(35.175423604938274, P836.totalWaterVapourContent(LAT, LON, 1.0, null));
    }

    @Test
    void rainAttenuationP618() {
        close(12.113666440995972, P618.rainAttenuation(LAT, LON, 20.0, 35.0));
        // longitude may be given in any convention
        close(12.113666440995972, P618.rainAttenuation(LAT, LON + 360, 20.0, 35.0));
        close(46.392279663042196, P618.rainAttenuation(5.16, -52.65, 20.0, 35.0));
    }

    @Test
    void totalAttenuationAndItsParts() {
        Itur.Attenuation a = Itur.atmosphericAttenuationSlantPath(LAT, LON, 20.0, 35.0, 0.1, 1.2);
        close(0.9436494914071086, a.gas());
        close(0.7798277557541271, a.cloud());
        close(4.135289905021819, a.rain());
        close(0.45405626235634355, a.scintillation());
        close(5.879695349040181, a.total());
        assertEquals(a.gas() + Math.sqrt(Math.pow(a.rain() + a.cloud(), 2) + a.scintillation() * a.scintillation()),
            a.total(), 1e-12);
    }

    @Test
    void optionsAreHonoured() {
        Itur.Attenuation onlyGas = Itur.atmosphericAttenuationSlantPath(LAT, LON, 20.0, 35.0, 0.1, 1.2,
            Itur.Options.builder().include(false, true, false, false).build());
        assertEquals(0.0, onlyGas.rain());
        assertEquals(onlyGas.gas(), onlyGas.total(), 1e-12);
        Itur.Attenuation heavyRain = Itur.atmosphericAttenuationSlantPath(LAT, LON, 20.0, 35.0, 0.1, 1.2,
            Itur.Options.builder().r001MmPerHour(80).build());
        assertTrue(heavyRain.rain() > 4.2);
    }

    @Test
    void gasesAndCrossPolarization() {
        close(0.4400936637901691, P676.gaseousAttenuationSlantPath(20.0, 35.0, 7.5, 1013.25, 288.15, null, null,
            P676.Mode.EXACT));
        close(24.819203161661413, P618.rainCrossPolarizationDiscrimination(5.0, 20.0, 35.0, 0.01, 45.0));
    }

    @Test
    void invalidInputs() {
        assertThrows(IllegalArgumentException.class, () -> P453.dn65(LAT, LON, 3.0)); // not a tabulated probability
        assertThrows(IllegalArgumentException.class, () -> P840.columnarContentReducedLiquid(LAT, LON, 99.5));
        assertThrows(IllegalArgumentException.class, () -> P618.rainCrossPolarizationDiscrimination(5, 70, 35, 0.01, 45));
        assertThrows(IllegalArgumentException.class, () -> P1510.surfaceMonthMeanTemperature(LAT, LON, 13));
        assertThrows(IllegalArgumentException.class,
            () -> P676.gaseousAttenuationInclinedPath(20, 30, 7.5, 1013, 288, 0, 12, P676.Mode.APPROX));
    }
}
