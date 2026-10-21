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

    /**
     * Topographic height of the ground above mean sea level (P.1511-1), by bicubic interpolation
     * of the 0.5 degree map. Never below 1e-9 km, as in ITU-Rpy.
     *
     * @param latDeg latitude (degrees, -90 to 90)
     * @param lonDeg longitude (degrees, -180 to 180 or 0 to 360)
     * @return topographic altitude above mean sea level (km)
     */
    public static double topographicAltitude(double latDeg, double lonDeg) {
        double lon = Special.mod(lonDeg, 360);
        double val = Grid.load("1511/v1_topo_0dot5").bicubic(latDeg, lon);
        return Math.max(val, 1e-9);
    }
}
