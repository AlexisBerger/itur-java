package org.itur.models;

import org.itur.grid.Grid;
import org.itur.util.Special;

/**
 * Recommendation ITU-R P.1510-1 (06/2017): mean surface temperature (2 m above ground),
 * 0.75 deg grid.
 */
public final class P1510 {

    private P1510() {
    }

    private static double lonFor(double lonDeg) {
        double lon = Special.mod(lonDeg, 360);
        return lon > 180 ? lon - 360 : lon;
    }

    /** Annual mean surface temperature (K). */
    public static double surfaceMeanTemperature(double latDeg, double lonDeg) {
        return Grid.load("1510/v1_t_annual").bilinear(latDeg, lonFor(lonDeg));
    }

    /**
     * Monthly mean surface temperature (K).
     *
     * @param month 1 (January) to 12 (December)
     */
    public static double surfaceMonthMeanTemperature(double latDeg, double lonDeg, int month) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("month must be in 1..12");
        }
        return Grid.load(String.format("1510/v1_t_month%02d", month)).bilinear(latDeg, lonFor(lonDeg));
    }
}
