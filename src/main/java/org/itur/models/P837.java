package org.itur.models;

import java.util.concurrent.ConcurrentHashMap;

import org.itur.grid.BilinearGrid;
import org.itur.util.Maths;

/**
 * Recommendation ITU-R P.837-7 (06/2017): characteristics of precipitation for propagation modelling.
 */
public final class P837 {

    public static final String RECOMMENDATION = "ITU-R P.837-7";

    private static final double[] DAYS = {31, 28.25, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
    private static final ConcurrentHashMap<String, BilinearGrid> CACHE = new ConcurrentHashMap<>();

    private P837() {
    }

    private static BilinearGrid r001Grid() {
        return CACHE.computeIfAbsent("r001", k -> BilinearGrid.load(
                "837/v7_lat_r001.npz", "837/v7_lon_r001.npz", "837/v7_r001.npz"));
    }

    private static BilinearGrid mtGrid(int month) {
        return CACHE.computeIfAbsent("mt" + month, k -> BilinearGrid.load(
                "837/v7_lat_mt.npz", "837/v7_lon_mt.npz", String.format("837/v7_mt_month%02d.npz", month)));
    }

    /** Monthly rain statistics shared by the probability and rainfall-rate computations. */
    private record Monthly(double[] p0, double[] r, double p0Annual) {
    }

    private static Monthly monthly(double lat, double lon) {
        double lonN = Maths.lon360(lon);
        double[] p0 = new double[12];
        double[] rii = new double[12];
        for (int i = 0; i < 12; i++) {
            int m = i + 1;
            double t = P1510.surfaceMonthMeanTemperature(lat, lonN, m);
            double mt = mtGrid(m).at(lat, Maths.lon180(lonN));
            double tii = t - 273.15;
            double r = tii >= 0 ? 0.5874 * Math.exp(0.0883 * tii) : 0.5874;
            double p = 100.0 * mt / (24.0 * DAYS[i] * r);
            if (p > 70) {
                r = 100.0 / 70.0 * mt / (24.0 * DAYS[i]);
                p = 70;
            }
            p0[i] = p;
            rii[i] = r;
        }
        double sum = 0.0;
        for (int i = 0; i < 12; i++) {
            sum += DAYS[i] * p0[i];
        }
        return new Monthly(p0, rii, sum / 365.25);
    }

    /** Annual probability of rain at the location, in percent. */
    public static double rainfallProbability(double lat, double lon) {
        return monthly(lat, lon).p0Annual();
    }

    /** Rainfall rate in mm/h exceeded for p% of an average year. */
    public static double rainfallRate(double lat, double lon, double p) {
        if (p == 0.01) {
            return r001Grid().at(lat, Maths.lon180(Maths.lon360(lon)));
        }
        Monthly m = monthly(lat, lon);
        if (p > m.p0Annual()) {
            return 0.0;
        }
        return Maths.bisect(rRef -> {
            double sum = 0.0;
            for (int i = 0; i < 12; i++) {
                double pr = m.p0()[i] * Maths.normSf((Math.log(rRef) + 0.7938 - Math.log(m.r()[i])) / 1.26);
                sum += DAYS[i] * pr;
            }
            double pExceed = sum / 365.25;
            return 100.0 * (pExceed / p - 1.0);
        }, 1e-10, 1000.0, 1e-5, 8.881784197001252e-16, 100);
    }

    /** Percentage of an average year during which the rainfall rate {@code rate} (mm/h) is exceeded. */
    public static double unavailabilityFromRainfallRate(double lat, double lon, double rate) {
        return Maths.bisect(x -> rainfallRate(lat, lon, x) - rate - 1e-6, 1e-5, 100.0, 1e-5, 8.881784197001252e-16, 50);
    }
}
