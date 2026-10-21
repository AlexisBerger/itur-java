package org.itur.grid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GridTest {

    @Test
    void bilinearReproducesNodesAndInterpolatesLinearly() {
        Grid g = Grid.load("839/v4_esa0height"); // 1.5 deg grid, lat -90..90, lon 0..360
        assertEquals(121, g.nlat());
        assertEquals(241, g.nlon());
        assertEquals(g.value(60, 100), g.bilinear(g.lat(60), g.lon(100)), 1e-12);
        double mid = g.bilinear(g.lat(60) + 0.75, g.lon(100));
        assertEquals((g.value(60, 100) + g.value(61, 100)) / 2, mid, 1e-12);
    }

    @Test
    void outsideTheGridIsNaN() {
        Grid g = Grid.load("839/v4_esa0height");
        assertTrue(Double.isNaN(g.bilinear(91, 10)));
        assertTrue(Double.isNaN(g.bilinear(10, -1)));
        assertTrue(Double.isNaN(g.bilinear(Double.NaN, 10)));
    }

    @Test
    void bicubicReproducesNodes() {
        Grid g = Grid.load("1511/v1_topo_0dot5"); // padded 0.5 deg grid
        int i = 200;
        int j = 300;
        assertEquals(g.value(i, j), g.bicubic(g.lat(i), g.lon(j)), 1e-6);
    }

    @Test
    void missingResourceIsReported() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> Grid.load("nope/none"));
    }
}
