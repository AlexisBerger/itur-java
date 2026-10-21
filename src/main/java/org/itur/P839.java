package org.itur;

/**
 * Recommendation ITU-R P.839-4 (09/2013): rain height model for prediction methods.
 * Uses the 0 deg C isotherm height map on a 1.5 deg grid.
 */
public final class P839 {

    private P839() {
    }

    /** Annual mean 0 deg C isotherm height above mean sea level (km). */
    public static double isotherm0(double latDeg, double lonDeg) {
        double lon = Special.mod(lonDeg, 360);
        return Grid.load("839/v4_esa0height").bilinear(latDeg, lon);
    }

    /** Mean annual rain height above mean sea level, {@code h_R = h_0 + 0.36} km. */
    public static double rainHeight(double latDeg, double lonDeg) {
        return isotherm0(latDeg, lonDeg) + 0.36;
    }
}
