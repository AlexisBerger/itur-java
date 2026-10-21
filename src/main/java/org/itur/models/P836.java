package org.itur.models;

import org.itur.grid.Grid;
import org.itur.util.Check;
import org.itur.util.Special;

/**
 * Recommendation ITU-R P.836-6 (12/2017): water vapour, surface density and total columnar content.
 * Data are given on a 1.125 deg grid for 18 exceedance probabilities; values for other
 * probabilities are interpolated in log(p), and values for a station altitude different from
 * the topographic one are scaled with the vapour scale height (Eqs. of Annex 1).
 */
public final class P836 {

    private P836() {
    }

    private static final double[] AVAILABLE_P = {0.1, 0.2, 0.3, 0.5, 1, 2, 3, 5, 10, 20, 30, 50, 60, 70, 80, 90, 95, 99};
    private static final String[] SUFFIX = {"01", "02", "03", "05", "1", "2", "3", "5", "10", "20", "30", "50", "60",
        "70", "80", "90", "95", "99"};

    private static final double STEP = 1.125;

    private static int indexOf(double p) {
        for (int i = 0; i < AVAILABLE_P.length; i++) {
            if (AVAILABLE_P[i] == p) {
                return i;
            }
        }
        return -1;
    }

    /** {@code numpy.searchsorted(AVAILABLE_P, p, side='right')}. */
    static int searchSortedRight(double[] sorted, double p) {
        int lo = 0;
        int hi = sorted.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (sorted[mid] <= p) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    private static double corners(String kind, int pIdx, double[] lats, double[] lons, double[] alt, boolean altGiven,
                                  double R, double C, double r, double c) {
        double[] d = new double[4];
        for (int i = 0; i < 4; i++) {
            double data = Grid.load("836/v6_" + kind + "_" + SUFFIX[pIdx]).bilinear(lats[i], lons[i]);
            double vsch = Grid.load("836/v6_vsch_" + SUFFIX[pIdx]).bilinear(lats[i], lons[i]);
            double altRes = Grid.load("836/v6_topo_0dot5").bicubic(lats[i], lons[i]);
            double a = altGiven ? alt[0] : altRes;
            d[i] = data * Math.exp(-(a - altRes) * 1.0 / vsch);
        }
        return d[0] * ((R + 1 - r) * (C + 1 - c)) + d[1] * ((r - R) * (C + 1 - c))
            + d[2] * ((R + 1 - r) * (c - C)) + d[3] * ((r - R) * (c - C));
    }

    private static double interpolate(String kind, double latDeg, double lonDeg, double p, Double altKm) {
        double lon = Special.mod(lonDeg, 360);
        int exact = indexOf(p);
        int below;
        int above;
        if (exact >= 0) {
            below = above = exact;
        } else {
            int idx = searchSortedRight(AVAILABLE_P, p) - 1;
            idx = Math.max(0, Math.min(AVAILABLE_P.length - 1, idx));
            below = idx;
            above = Math.max(0, Math.min(AVAILABLE_P.length - 1, idx + 1));
        }
        double R = Special.floorDiv(-(latDeg - 90), STEP);
        double C = Special.floorDiv(lon, STEP);
        double[] lats = {90 - R * STEP, 90 - (R + 1) * STEP, 90 - R * STEP, 90 - (R + 1) * STEP};
        double[] lons = {
            Special.mod(C * STEP, 360), Special.mod(C * STEP, 360),
            Special.mod((C + 1) * STEP, 360), Special.mod((C + 1) * STEP, 360)};
        double r = -(latDeg - 90) / STEP;
        double c = lon / STEP;
        boolean altGiven = altKm != null;
        double[] alt = altGiven ? new double[] {altKm} : null;

        double dataA = corners(kind, above, lats, lons, alt, altGiven, R, C, r, c);
        if (exact >= 0) {
            return dataA;
        }
        double dataB = corners(kind, below, lats, lons, alt, altGiven, R, C, r, c);
        double p0 = AVAILABLE_P[below];
        double p1 = AVAILABLE_P[above];
        return dataB + (dataA - dataB) * (Math.log(p) - Math.log(p0)) / (Math.log(p1) - Math.log(p0));
    }

    /**
     * Surface water vapour density exceeded for {@code p} % of an average year (P.836-6).
     *
     * @param latDeg latitude (degrees, -90 to 90)
     * @param lonDeg longitude (degrees, -180 to 180 or 0 to 360)
     * @param p percentage of an average year the value is exceeded (%), 0.1 to 99
     * @param altKm altitude of the station (km), or {@code null} to use the topographic altitude
     * @return surface water vapour density (g/m3)
     */
    public static double surfaceWaterVapourDensity(double latDeg, double lonDeg, double p, Double altKm) {
        Check.position(latDeg, lonDeg);
        Check.probability(p);
        Check.optionalFinite("altitude (km)", altKm);
        return interpolate("rho", latDeg, lonDeg, p, altKm);
    }

    /**
     * Total columnar water vapour content exceeded for {@code p} % of an average year (P.836-6).
     *
     * @param latDeg latitude (degrees, -90 to 90)
     * @param lonDeg longitude (degrees, -180 to 180 or 0 to 360)
     * @param p percentage of an average year the value is exceeded (%), 0.1 to 99
     * @param altKm altitude of the station (km), or {@code null} to use the topographic altitude
     * @return total water vapour content (kg/m2)
     */
    public static double totalWaterVapourContent(double latDeg, double lonDeg, double p, Double altKm) {
        Check.position(latDeg, lonDeg);
        Check.probability(p);
        Check.optionalFinite("altitude (km)", altKm);
        return interpolate("v", latDeg, lonDeg, p, altKm);
    }
}
