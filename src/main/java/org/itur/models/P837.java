package org.itur.models;

import org.itur.grid.Grid;
import org.itur.util.Check;
import org.itur.util.Special;

/**
 * Recommendation ITU-R P.837-7 (06/2017): characteristics of precipitation for propagation
 * modelling. Rainfall rate exceeded for 0.01 % of an average year comes from a 0.125 deg map;
 * other percentages are derived from monthly mean total rainfall and surface temperature maps.
 */
public final class P837 {

    private P837() {
    }

    private static final double[] N = {31, 28.25, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};

    private static double lonFor(double lonDeg) {
        return lonDeg > 180 ? lonDeg - 360 : lonDeg;
    }

    private static double r001(double lat, double lon) {
        return Grid.load("837/v7_r001").bilinear(lat, lonFor(lon));
    }

    private static double monthlyTotalRainfall(double lat, double lon, int month) {
        return Grid.load(String.format("837/v7_mt_month%02d", month)).bilinear(lat, lonFor(lon));
    }

    /** Per-month intermediate quantities of Steps 2-7: probability of rain and rain-rate scale. */
    private record Monthly(double[] p0, double[] r, double annualP0) {
    }

    private static Monthly monthly(double lat, double lon) {
        double[] p0 = new double[12];
        double[] r = new double[12];
        for (int m = 0; m < 12; m++) {
            double tii = P1510.surfaceMonthMeanTemperature(lat, lon, m + 1) - 273.15;
            double mt = monthlyTotalRainfall(lat, lon, m + 1);
            double rii = tii >= 0 ? 0.5874 * Math.exp(0.0883 * tii) : 0.5874;
            double p0ii = 100 * mt / (24 * N[m] * rii);
            if (p0ii > 70) {
                rii = 100 / 70.0 * mt / (24 * N[m]);
                p0ii = 70;
            }
            p0[m] = p0ii;
            r[m] = rii;
        }
        double sum = 0;
        for (int m = 0; m < 12; m++) {
            sum += N[m] * p0[m];
        }
        return new Monthly(p0, r, sum / 365.25);
    }

    /**
     * Probability of rain in an average year, P0 (P.837-7).
     *
     * @param latDeg latitude (degrees, -90 to 90)
     * @param lonDeg longitude (degrees, -180 to 180 or 0 to 360)
     * @return probability of rain (%)
     */
    public static double rainfallProbability(double latDeg, double lonDeg) {
        Check.position(latDeg, lonDeg);
        return monthly(latDeg, lonDeg).annualP0();
    }

    /**
     * Rainfall rate exceeded for {@code p} % of an average year (P.837-7). For {@code p = 0.01} the
     * tabulated R0.01 map is used; otherwise the value is found by bisection from the monthly statistics.
     * Returns 0 when {@code p} is larger than the probability of rain.
     *
     * @param latDeg latitude (degrees, -90 to 90)
     * @param lonDeg longitude (degrees, -180 to 180 or 0 to 360)
     * @param p percentage of an average year the rate is exceeded (%)
     * @return rainfall rate (mm/h)
     */
    public static double rainfallRate(double latDeg, double lonDeg, double p) {
        Check.position(latDeg, lonDeg);
        Check.probability(p);
        if (p == 0.01) {
            return r001(latDeg, lonDeg);
        }
        Monthly mo = monthly(latDeg, lonDeg);
        if (p > mo.annualP0()) {
            return 0;
        }
        return Special.bisect(rRef -> {
            double sum = 0;
            for (int m = 0; m < 12; m++) {
                double pr = mo.p0()[m] * Special.normSf((Math.log(rRef) + 0.7938 - Math.log(mo.r()[m])) / 1.26);
                sum += N[m] * pr;
            }
            double prGeR = sum / 365.25;
            return 100 * (prGeR / p - 1);
        }, 1e-10, 1000, 1e-5);
    }

    /**
     * Percentage of an average year during which a rainfall rate is exceeded (inverse of
     * {@link #rainfallRate}).
     *
     * @param latDeg latitude (degrees, -90 to 90)
     * @param lonDeg longitude (degrees, -180 to 180 or 0 to 360)
     * @param rMmPerHour rainfall rate threshold (mm/h)
     * @return percentage of time the rate is exceeded (%)
     * @throws IllegalArgumentException if the rate is larger than the rate exceeded for 1e-5 % of the time
     */
    public static double unavailabilityFromRainfallRate(double latDeg, double lonDeg, double rMmPerHour) {
        Check.position(latDeg, lonDeg);
        Check.nonNegative("rainfall rate (mm/h)", rMmPerHour);
        try {
            return Special.bisect(x -> rainfallRate(latDeg, lonDeg, x) - rMmPerHour - 1e-6, 1e-5, 100, 2e-12, 50);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("rainfall rate " + rMmPerHour + " mm/h is outside the range the model"
                + " gives for exceedance probabilities from 1e-5 % to 100 % at this location", e);
        }
    }
}
