package org.itur.models;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.itur.grid.BicubicGrid;
import org.itur.grid.BilinearGrid;
import org.itur.util.Maths;

/**
 * Recommendation ITU-R P.836-6 (12/2017): water vapour, surface density and total columnar content.
 */
public final class P836 {

    public static final String RECOMMENDATION = "ITU-R P.836-6";

    /** Percentages for which the Recommendation provides maps. */
    public static final double[] PERCENTAGES = {
        0.1, 0.2, 0.3, 0.5, 1, 2, 3, 5, 10, 20, 30, 50, 60, 70, 80, 90, 95, 99};

    private static final ConcurrentHashMap<String, BilinearGrid> CACHE = new ConcurrentHashMap<>();
    private static final AtomicReference<BicubicGrid> TOPO = new AtomicReference<>();

    private P836() {
    }

    private static String tag(double p) {
        return switch ((int) Math.round(p * 10)) {
            case 1 -> "01";
            case 2 -> "02";
            case 3 -> "03";
            case 5 -> "05";
            default -> Integer.toString((int) Math.round(p));
        };
    }

    private static BilinearGrid grid(String kind, double p) {
        return CACHE.computeIfAbsent(kind + p, k -> BilinearGrid.load(
                "836/v6_lat.npz", "836/v6_lon.npz", "836/v6_" + kind + "_" + tag(p) + ".npz"));
    }

    private static double topo(double lat, double lon) {
        BicubicGrid g = TOPO.get();
        if (g == null) {
            g = BicubicGrid.load("836/v6_topolat.npz", "836/v6_topolon.npz", "836/v6_topo_0dot5.npz");
            TOPO.compareAndSet(null, g);
            g = TOPO.get();
        }
        return g.at(lat, lon);
    }

    /** Surface water vapour density in g/m3 exceeded for p% of the year, at the altitude of the terrain. */
    public static double surfaceWaterVapourDensity(double lat, double lon, double p) {
        return interpolate("rho", lat, lon, p, null);
    }

    /** Surface water vapour density in g/m3 exceeded for p% of the year, at altitude {@code altKm}. */
    public static double surfaceWaterVapourDensity(double lat, double lon, double p, double altKm) {
        return interpolate("rho", lat, lon, p, altKm);
    }

    /** Total columnar water vapour content in kg/m2 exceeded for p% of the year, at the terrain altitude. */
    public static double totalWaterVapourContent(double lat, double lon, double p) {
        return interpolate("v", lat, lon, p, null);
    }

    /** Total columnar water vapour content in kg/m2 exceeded for p% of the year, at altitude {@code altKm}. */
    public static double totalWaterVapourContent(double lat, double lon, double p, double altKm) {
        return interpolate("v", lat, lon, p, altKm);
    }

    private static double interpolate(String kind, double lat, double lon, double p, Double alt) {
        double lonN = Maths.lon360(lon);
        double[] avail = PERCENTAGES;
        boolean exact = Arrays.stream(avail).anyMatch(x -> x == p);
        double pBelow;
        double pAbove;
        if (exact) {
            pBelow = p;
            pAbove = p;
        } else {
            int idx = Maths.searchSortedRight(avail, p) - 1;
            idx = Math.min(Math.max(idx, 0), avail.length - 1);
            pBelow = avail[idx];
            pAbove = avail[Math.min(Math.max(idx + 1, 0), avail.length - 1)];
        }

        double rr = Maths.floorDiv(-(lat - 90.0), 1.125);
        double cc = Maths.floorDiv(lonN, 1.125);
        double[] lats = {90.0 - rr * 1.125, 90.0 - (rr + 1) * 1.125, 90.0 - rr * 1.125, 90.0 - (rr + 1) * 1.125};
        double[] lons = {
            Maths.mod(cc * 1.125, 360.0), Maths.mod(cc * 1.125, 360.0),
            Maths.mod((cc + 1) * 1.125, 360.0), Maths.mod((cc + 1) * 1.125, 360.0)};
        double r = -(lat - 90.0) / 1.125;
        double c = lonN / 1.125;

        double a = blend(kind, lats, lons, pAbove, alt, rr, cc, r, c);
        if (exact) {
            return a;
        }
        double b = blend(kind, lats, lons, pBelow, alt, rr, cc, r, c);
        return b + (a - b) * (Math.log(p) - Math.log(pBelow)) / (Math.log(pAbove) - Math.log(pBelow));
    }

    private static double blend(String kind, double[] lats, double[] lons, double p, Double alt,
            double rr, double cc, double r, double c) {
        double[] vals = new double[4];
        for (int i = 0; i < 4; i++) {
            double data = grid(kind, p).at(lats[i], lons[i]);
            double vsch = grid("vsch", p).at(lats[i], lons[i]);
            double altRes = topo(lats[i], lons[i]);
            double altitude = alt == null ? altRes : alt;
            vals[i] = data * Math.exp(-(altitude - altRes) * 1.0 / vsch);
        }
        return vals[0] * ((rr + 1 - r) * (cc + 1 - c)) + vals[1] * ((r - rr) * (cc + 1 - c))
                + vals[2] * ((rr + 1 - r) * (c - cc)) + vals[3] * ((r - rr) * (c - cc));
    }
}
