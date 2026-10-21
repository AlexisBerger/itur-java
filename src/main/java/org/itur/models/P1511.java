package org.itur.models;

import org.itur.grid.Grid;
import org.itur.util.Special;

/**
 * Recommendation ITU-R P.1511-1 (07/2015): topography for Earth-space propagation modelling.
 * 0.5 deg grid with bicubic interpolation.
 */
public final class P1511 {

    private P1511() {
    }

    /** Topographic height above mean sea level (km), never below 1e-9 (as in ITU-Rpy). */
    public static double topographicAltitude(double latDeg, double lonDeg) {
        double lon = Special.mod(lonDeg, 360);
        double val = Grid.load("1511/v1_topo_0dot5").bicubic(latDeg, lon);
        return Math.max(val, 1e-9);
    }
}
