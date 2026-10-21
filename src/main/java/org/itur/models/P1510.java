package org.itur.models;

import java.util.concurrent.ConcurrentHashMap;

import org.itur.grid.BilinearGrid;
import org.itur.util.Maths;

/**
 * Recommendation ITU-R P.1510-1 (06/2017): mean surface temperature.
 */
public final class P1510 {

    public static final String RECOMMENDATION = "ITU-R P.1510-1";

    private static final ConcurrentHashMap<String, BilinearGrid> CACHE = new ConcurrentHashMap<>();

    private P1510() {
    }

    private static BilinearGrid grid(String dataFile) {
        return CACHE.computeIfAbsent(dataFile, f -> BilinearGrid.load("1510/v1_lat.npz", "1510/v1_lon.npz", f));
    }

    /** Annual mean surface temperature in kelvin. */
    public static double surfaceMeanTemperature(double lat, double lon) {
        return grid("1510/v1_t_annual.npz").at(lat, Maths.lon180(Maths.lon360(lon)));
    }

    /** Monthly mean surface temperature in kelvin, {@code month} from 1 (January) to 12 (December). */
    public static double surfaceMonthMeanTemperature(double lat, double lon, int month) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("month must be between 1 and 12");
        }
        return grid(String.format("1510/v1_t_month%02d.npz", month)).at(lat, Maths.lon180(Maths.lon360(lon)));
    }
}
