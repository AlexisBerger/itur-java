package org.itur.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** P.453 formulas: definitions and physical consistency. */
class P453Test {

    @Test
    void saturationPressureOfWaterAtKnownTemperatures() {
        // about 6.11 hPa at 0 degC and 1013 hPa at 100 degC
        assertEquals(6.136, P453.saturationVapourPressure(0, 1013.25, P453.Hydrometeor.WATER), 0.01);
        assertEquals(1023.1, P453.saturationVapourPressure(100, 1013.25, P453.Hydrometeor.WATER), 0.5);
    }

    @Test
    void waterAndIceCoincideAtTheFreezingPointAndDivergeBelow() {
        double w0 = P453.saturationVapourPressure(0, 1013.25, P453.Hydrometeor.WATER);
        double i0 = P453.saturationVapourPressure(0, 1013.25, P453.Hydrometeor.ICE);
        assertEquals(w0, i0, 1e-3);
        double w = P453.saturationVapourPressure(-30, 1013.25, P453.Hydrometeor.WATER);
        double i = P453.saturationVapourPressure(-30, 1013.25, P453.Hydrometeor.ICE);
        assertTrue(w > i, "supercooled water has the higher vapour pressure");
    }

    @Test
    void saturationPressureIncreasesWithTemperature() {
        double prev = 0;
        for (double t = -40; t <= 50; t += 5) {
            double e = P453.saturationVapourPressure(t, 1013.25, P453.Hydrometeor.WATER);
            assertTrue(e > prev);
            prev = e;
        }
    }

    @Test
    void vapourPressureIsTheHumidityFractionOfSaturation() {
        double es = P453.saturationVapourPressure(15, 1013.25, P453.Hydrometeor.WATER);
        assertEquals(0.6 * es, P453.waterVapourPressure(15, 1013.25, 60), 1e-12);
        assertEquals(P453.waterVapourPressure(15, 1013.25, 60),
            P453.waterVapourPressure(15, 1013.25, 60, P453.Hydrometeor.WATER), 0);
        assertEquals(0, P453.waterVapourPressure(15, 1013.25, 0), 0);
        assertEquals(es, P453.waterVapourPressure(15, 1013.25, 100), 1e-12);
    }

    @Test
    void refractivityTermsAndIndex() {
        double e = 10;
        double pd = 1003.25;
        double t = 288.15;
        double ndry = P453.dryTermRadioRefractivity(pd, t);
        double nwet = P453.wetTermRadioRefractivity(e, t - 273.15);
        assertEquals(77.6 * pd / t, ndry, 1e-12);
        assertEquals(72 * e / t + 3.75e5 * e / (t * t), nwet, 1e-9);
        assertEquals(1 + (ndry + nwet) * 1e-6, P453.radioRefractiveIndex(pd, e, t), 1e-12);
        // typical sea-level value of the refractivity: 300 to 330 N-units
        assertTrue(ndry + nwet > 300 && ndry + nwet < 330);
    }

    @Test
    void wetTermIsZeroWithoutVapour() {
        assertEquals(0, P453.wetTermRadioRefractivity(0, 15), 0);
    }

    @Test
    void mapsDecreaseWithTheExceedanceProbability() {
        double a = P453.mapWetTermRadioRefractivity(48.85, 2.35, 1);
        double b = P453.mapWetTermRadioRefractivity(48.85, 2.35, 50);
        double c = P453.mapWetTermRadioRefractivity(48.85, 2.35, 99);
        assertTrue(a > b && b > c, a + " " + b + " " + c);
    }

    @Test
    void gradientMapsAcceptOnlyTabulatedProbabilities() {
        double v = P453.dn65(48.85, 2.35, 1);
        assertTrue(v < 0, "the gradient in the first 65 m is negative in Paris");
        assertTrue(P453.dn1(48.85, 2.35, 1) < 0);
        assertThrows(IllegalArgumentException.class, () -> P453.dn1(48.85, 2.35, 3));
    }
}
