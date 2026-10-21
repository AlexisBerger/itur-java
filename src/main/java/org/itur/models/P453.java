package org.itur.models;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

import org.itur.grid.BilinearGrid;
import org.itur.util.Maths;

/**
 * Recommendation ITU-R P.453-13 (12/2017): the radio refractive index, its formula and refractivity data.
 */
public final class P453 {

    public static final String RECOMMENDATION = "ITU-R P.453-13";

    /** Hydrometeor type for the saturation vapour pressure. */
    public enum Hydrometeor { WATER, ICE }

    /** Percentages available for the wet-term refractivity map (P.453-13, annual). */
    public static final double[] NWET_PERCENTAGES = {
        0.1, 0.2, 0.3, 0.5, 1, 2, 3, 5, 10, 20, 30, 50, 60, 70, 80, 90, 95, 99};

    /** Percentages available for the DN65 / DN1 maps. */
    public static final double[] DN_PERCENTAGES = {
        0.1, 0.2, 0.5, 1, 2, 5, 10, 20, 30, 40, 50, 60, 70, 80, 90, 95, 98, 99, 99.5, 99.8, 99.9};

    private static final ConcurrentHashMap<String, BilinearGrid> CACHE = new ConcurrentHashMap<>();

    private P453() {
    }

    // ------------------------------------------------------------------ formulas

    /** Wet term of the radio refractivity (Eq. 6), with e in hPa and the temperature in degrees Celsius. */
    public static double wetTermRadioRefractivity(double e, double tCelsius) {
        double tk = tCelsius + 273.15;
        return 72.0 * e / tk + 3.75e5 * e / (tk * tk);
    }

    /** Dry term of the radio refractivity (Eq. 3), with the dry pressure in hPa and T in kelvin. */
    public static double dryTermRadioRefractivity(double pd, double tKelvin) {
        return 77.6 * pd / tKelvin;
    }

    /** Radio refractive index n (Eq. 1-2) from the dry pressure Pd (hPa), vapour pressure e (hPa) and T (K). */
    public static double radioRefractiveIndex(double pd, double e, double tKelvin) {
        double n = 77.6 * pd / tKelvin + 72.0 * e / tKelvin + 3.75e5 * e / (tKelvin * tKelvin);
        return 1.0 + n * 1e-6;
    }

    /** Saturation vapour pressure in hPa (Eq. 9-10), with T in degrees Celsius and the total pressure in hPa. */
    public static double saturationVapourPressure(double tCelsius, double pHpa, Hydrometeor type) {
        double ef;
        double a;
        double b;
        double c;
        double d;
        if (type == Hydrometeor.WATER) {
            ef = 1.0 + 1e-4 * (7.2 + pHpa * (0.0320 + 5.9e-6 * tCelsius * tCelsius));
            a = 6.1121;
            b = 18.678;
            c = 257.14;
            d = 234.5;
        } else {
            ef = 1.0 + 1e-4 * (2.2 + pHpa * (0.0383 + 6.4e-6 * tCelsius * tCelsius));
            a = 6.1115;
            b = 23.036;
            c = 279.82;
            d = 333.7;
        }
        return ef * a * Math.exp((b - tCelsius / d) * tCelsius / (tCelsius + c));
    }

    /** Water vapour pressure e in hPa (Eq. 8) from the relative humidity H in percent. */
    public static double waterVapourPressure(double tCelsius, double pHpa, double humidityPercent, Hydrometeor type) {
        return humidityPercent * saturationVapourPressure(tCelsius, pHpa, type) / 100.0;
    }

    public static double waterVapourPressure(double tCelsius, double pHpa, double humidityPercent) {
        return waterVapourPressure(tCelsius, pHpa, humidityPercent, Hydrometeor.WATER);
    }

    // ------------------------------------------------------------------ maps

    private static String nwetTag(double p) {
        return switch ((int) Math.round(p * 10)) {
            case 1 -> "01";
            case 2 -> "02";
            case 3 -> "03";
            case 5 -> "05";
            default -> Integer.toString((int) Math.round(p));
        };
    }

    private static BilinearGrid nwetGrid(double p) {
        return CACHE.computeIfAbsent("nwet" + p, k -> BilinearGrid.load(
                "453/v13_lat_n.npz", "453/v13_lon_n.npz", "453/v13_nwet_annual_" + nwetTag(p) + ".npz"));
    }

    private static boolean contains(double[] arr, double v) {
        return Arrays.stream(arr).anyMatch(x -> x == v);
    }

    private static BilinearGrid dnGrid(String prefix, double p) {
        if (!contains(DN_PERCENTAGES, p)) {
            throw new IllegalArgumentException("p = " + p + " is not available for " + prefix
                    + "; valid values: " + Arrays.toString(DN_PERCENTAGES));
        }
        return CACHE.computeIfAbsent(prefix + p, k -> {
            int whole = (int) Math.floor(p);
            long frac = Math.round((p % 1.0) * 100);
            String name = String.format("453/v12_%s_%02dd%02d_v1.npz", prefix, whole, frac);
            return BilinearGrid.load("453/v12_lat0d75.npz", "453/v12_lon0d75.npz", name);
        });
    }

    /** Difference of the refractivity gradient (DN65), exceeded for p% of the average year (dimensionless, N-units). */
    public static double dn65(double lat, double lon, double p) {
        return dnGrid("dn65m", p).at(lat, Maths.lon360(lon));
    }

    /** Refractivity gradient in the lowest 1 km of the atmosphere (DN1), exceeded for p% of the average year. */
    public static double dn1(double lat, double lon, double p) {
        return dnGrid("dn", p).at(lat, Maths.lon360(lon));
    }

    private static double nwetPoint(double lat, double lon, double p) {
        return nwetGrid(p).at(lat, Maths.lon180(lon));
    }

    /**
     * Wet term of the surface refractivity (N-units) exceeded for p% of the average year, obtained as in
     * ITU-Rpy by bilinear interpolation of the four surrounding grid points and, if p is not tabulated,
     * logarithmic interpolation between the two nearest tabulated percentages.
     */
    public static double mapWetTermRadioRefractivity(double lat, double lon, double p) {
        double lonN = Maths.lon180(Maths.lon360(lon));
        double[] avail = NWET_PERCENTAGES;
        boolean exact = contains(avail, p);
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

        double rr = Maths.floorDiv(-(lat - 90.0), 0.75);
        double cc = Maths.floorDiv(lonN + 180.0, 0.75);
        double[] lats = {90.0 - rr * 0.75, 90.0 - (rr + 1) * 0.75, 90.0 - rr * 0.75, 90.0 - (rr + 1) * 0.75};
        double[] lons = {cc * 0.75 - 180.0, cc * 0.75 - 180.0, (cc + 1) * 0.75 - 180.0, (cc + 1) * 0.75 - 180.0};
        double r = -(lat - 90.0) / 0.75;
        double c = (lonN + 180.0) / 0.75;

        double a = corners(lats, lons, pAbove, rr, cc, r, c);
        if (exact) {
            return a;
        }
        double b = corners(lats, lons, pBelow, rr, cc, r, c);
        return b + (a - b) * (Math.log(p) - Math.log(pBelow)) / (Math.log(pAbove) - Math.log(pBelow));
    }

    private static double corners(double[] lats, double[] lons, double p, double rr, double cc, double r, double c) {
        double n0 = nwetPoint(lats[0], lons[0], p);
        double n1 = nwetPoint(lats[1], lons[1], p);
        double n2 = nwetPoint(lats[2], lons[2], p);
        double n3 = nwetPoint(lats[3], lons[3], p);
        return n0 * ((rr + 1 - r) * (cc + 1 - c)) + n1 * ((r - rr) * (cc + 1 - c))
                + n2 * ((rr + 1 - r) * (c - cc)) + n3 * ((r - rr) * (c - cc));
    }
}
