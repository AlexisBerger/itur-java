package org.itur;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class ApiTest {
    private static final Itur.Link LINK = new Itur.Link(48.85, 2.35, 20, 35, 1.2);

    @Test
    void linkFormMatchesTheLongForm() {
        Itur.Attenuation a = Itur.atmosphericAttenuationSlantPath(48.85, 2.35, 20, 35, 1, 1.2);
        Itur.Attenuation b = Itur.atmosphericAttenuationSlantPath(LINK, 1);
        assertTrue(a.equals(b));
    }

    @Test
    void curveIsOrderedAndDecreasingWithTimePercentage() {
        List<Itur.Attenuation> c = Itur.attenuationCurve(LINK, new double[] {0.01, 0.1, 1}, Itur.Options.defaults());
        assertEquals(3, c.size());
        assertTrue(c.get(0).rain() > c.get(1).rain() && c.get(1).rain() > c.get(2).rain());
        assertTrue(Itur.atmosphericAttenuationSlantPath(LINK, 0.1).equals(c.get(1)));
        assertThrows(UnsupportedOperationException.class, () -> c.add(c.get(0)));
    }

    @Test
    void batchKeepsTheLinkOrder() {
        Itur.Link other = new Itur.Link(0, 0, 12, 60, 0.6);
        List<Itur.Attenuation> r = Itur.attenuationBatch(List.of(LINK, other), 1, Itur.Options.defaults());
        assertTrue(Itur.atmosphericAttenuationSlantPath(LINK, 1).equals(r.get(0)));
        assertTrue(Itur.atmosphericAttenuationSlantPath(other, 1).equals(r.get(1)));
    }

    @Test
    void invalidInputsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Itur.Link(91, 0, 20, 35, 1));
        assertThrows(IllegalArgumentException.class, () -> new Itur.Link(0, 0, 20, 0, 1));
        assertThrows(NullPointerException.class, () -> Itur.attenuationBatch(null, 1, Itur.Options.defaults()));
        assertThrows(IllegalArgumentException.class,
            () -> Itur.attenuationCurve(LINK, new double[] {0.01, 0}, Itur.Options.defaults()));
    }

    @Test
    void linkBuilderMatchesTheConstructor() {
        Itur.Link b = Itur.Link.builder().latitude(48.85).longitude(2.35).frequencyGHz(20).elevationDeg(35)
            .antennaDiameterM(1.2).build();
        assertTrue(LINK.equals(b));
    }

    @Test
    void linkBuilderRejectsMissingOrInvalidValues() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> Itur.Link.builder().latitude(48.85).longitude(2.35).elevationDeg(35).antennaDiameterM(1.2).build());
        assertTrue(e.getMessage().toLowerCase().contains("freq"), e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> Itur.Link.builder().build());
        assertThrows(IllegalArgumentException.class, () -> Itur.Link.builder().latitude(48.85).longitude(2.35)
            .frequencyGHz(20).elevationDeg(35).antennaDiameterM(-1).build());
    }
}
