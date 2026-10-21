package org.itur.models;

import java.util.concurrent.atomic.AtomicReference;

import org.itur.grid.BilinearGrid;
import org.itur.util.Maths;

/**
 * Recommendation ITU-R P.839-4 (09/2013): rain height model for prediction methods.
 */
public final class P839 {

    public static final String RECOMMENDATION = "ITU-R P.839-4";

    private static final AtomicReference<BilinearGrid> ISOTHERM = new AtomicReference<>();

    private P839() {
    }

    private static BilinearGrid grid() {
        BilinearGrid g = ISOTHERM.get();
        if (g == null) {
            g = BilinearGrid.load("839/v4_esalat.npz", "839/v4_esalon.npz", "839/v4_esa0height.npz");
            ISOTHERM.compareAndSet(null, g);
            g = ISOTHERM.get();
        }
        return g;
    }

    /** Mean annual 0 degC isotherm height above mean sea level, in km. */
    public static double isotherm0(double lat, double lon) {
        return grid().at(lat, Maths.lon360(lon));
    }

    /** Mean annual rain height above mean sea level, h_R = h_0 + 0.36 km. */
    public static double rainHeight(double lat, double lon) {
        return isotherm0(lat, lon) + 0.36;
    }
}
