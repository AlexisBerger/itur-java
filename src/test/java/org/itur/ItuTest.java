package org.itur;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import org.itur.models.P676;

/** The combination of the contributions and the options of {@link Itur}. */
class ItuTest {

    private static final double LAT = 48.85;
    private static final double LON = 2.35;

    private static Itur.Attenuation run(Itur.Options o) {
        return Itur.atmosphericAttenuationSlantPath(LAT, LON, 20, 35, 0.1, 1.2, o);
    }

    @Test
    void totalCombinesTheContributions() {
        Itur.Attenuation a = run(Itur.Options.defaults());
        double expected = a.gas() + Math.sqrt((a.rain() + a.cloud()) * (a.rain() + a.cloud())
            + a.scintillation() * a.scintillation());
        assertEquals(expected, a.total(), 1e-12);
        assertTrue(a.total() >= a.gas() && a.total() >= a.rain() && a.total() < a.gas() + a.cloud() + a.rain()
            + a.scintillation());
    }

    @Test
    void defaultOverloadUsesTheDefaultOptions() {
        assertEquals(run(Itur.Options.defaults()).total(),
            Itur.atmosphericAttenuationSlantPath(LAT, LON, 20, 35, 0.1, 1.2).total(), 0);
    }

    @Test
    void eachContributionCanBeSwitchedOff() {
        Itur.Attenuation all = run(Itur.Options.defaults());
        Itur.Attenuation rainOnly = run(Itur.Options.builder().include(true, false, false, false).build());
        assertEquals(all.rain(), rainOnly.rain(), 1e-12);
        assertEquals(0, rainOnly.gas(), 0);
        assertEquals(0, rainOnly.cloud(), 0);
        assertEquals(0, rainOnly.scintillation(), 0);
        assertEquals(rainOnly.rain(), rainOnly.total(), 1e-12);
        Itur.Attenuation gasOnly = run(Itur.Options.builder().include(false, true, false, false).build());
        assertEquals(all.gas(), gasOnly.gas(), 1e-12);
        assertEquals(gasOnly.gas(), gasOnly.total(), 1e-12);
        Itur.Attenuation none = run(Itur.Options.builder().include(false, false, false, false).build());
        assertEquals(0, none.total(), 0);
    }

    @Test
    void explicitValuesOverrideTheMaps() {
        Itur.Attenuation base = run(Itur.Options.defaults());
        assertTrue(run(Itur.Options.builder().r001MmPerHour(80).build()).rain() > base.rain());
        assertTrue(run(Itur.Options.builder().r001MmPerHour(5).build()).rain() < base.rain());
        assertTrue(run(Itur.Options.builder().hsKm(2.0).build()).rain() < base.rain());
        assertTrue(run(Itur.Options.builder().rhoGPerM3(25).build()).gas() > base.gas());
        assertTrue(run(Itur.Options.builder().vtKgPerM2(60).build()).gas() > base.gas());
        assertTrue(run(Itur.Options.builder().tauDeg(0).build()).rain() != base.rain());
        assertTrue(run(Itur.Options.builder().lsKm(1.0).build()).rain() < base.rain());
        assertTrue(run(Itur.Options.builder().hPercent(90).tKelvin(300).pHPa(1000).build()).scintillation()
            != base.scintillation());
        assertTrue(run(Itur.Options.builder().eta(0.7).hLMeters(2000).build()).scintillation()
            != base.scintillation());
    }

    @Test
    void theRayTracedGasMethodCanBeSelected() {
        Itur.Attenuation approx = run(Itur.Options.defaults());
        Itur.Attenuation exact = run(Itur.Options.builder().gasMode(P676.Mode.EXACT).build());
        assertEquals(approx.rain(), exact.rain(), 0);
        assertTrue(exact.gas() != approx.gas());
        // the two methods use different water vapour inputs (P.836 content against surface density)
        assertEquals(approx.gas(), exact.gas(), 0.25 * approx.gas());
    }

    @Test
    void attenuationGrowsWithFrequencyAndRarityOfTheEvent() {
        assertTrue(Itur.atmosphericAttenuationSlantPath(LAT, LON, 30, 35, 0.1, 1.2).total()
            > Itur.atmosphericAttenuationSlantPath(LAT, LON, 12, 35, 0.1, 1.2).total());
        assertTrue(Itur.atmosphericAttenuationSlantPath(LAT, LON, 20, 35, 0.01, 1.2).total()
            > Itur.atmosphericAttenuationSlantPath(LAT, LON, 20, 35, 1, 1.2).total());
    }

    @Test
    void gasesAndCloudsAreNotDefinedBelowOnePercent() {
        Itur.Attenuation a = run(Itur.Options.defaults());
        Itur.Attenuation b = Itur.atmosphericAttenuationSlantPath(LAT, LON, 20, 35, 0.001, 1.2);
        assertEquals(a.gas(), b.gas(), 1e-12);
        assertEquals(a.cloud(), b.cloud(), 1e-12);
        assertTrue(b.rain() > a.rain());
    }
}
