package org.itur.models;

import java.util.concurrent.atomic.AtomicReference;

import org.itur.grid.BicubicGrid;
import org.itur.util.Maths;

/**
 * Recommendation ITU-R P.1511-1 (07/2015): topography for Earth-space propagation modelling.
 */
public final class P1511 {

    public static final String RECOMMENDATION = "ITU-R P.1511-1";

    private static final AtomicReference<BicubicGrid> TOPO = new AtomicReference<>();

    private P1511() {
    }

    private static BicubicGrid grid() {
        BicubicGrid g = TOPO.get();
        if (g == null) {
            g = BicubicGrid.load("1511/v1_lat.npz", "1511/v1_lon.npz", "1511/v1_topo_0dot5.npz");
            TOPO.compareAndSet(null, g);
            g = TOPO.get();
        }
        return g;
    }

    /** Topographic height above mean sea level, in km (never below 1e-9 km, as in ITU-Rpy). */
    public static double topographicAltitude(double lat, double lon) {
        return Math.max(grid().at(lat, Maths.lon360(lon)), 1e-9);
    }
}
