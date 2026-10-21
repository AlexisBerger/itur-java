package org.itur.models;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

import org.itur.grid.BilinearGrid;
import org.itur.util.Maths;

/**
 * Recommendation ITU-R P.840-7 (12/2017): attenuation due to clouds and fog.
 */
public final class P840 {

    public static final String RECOMMENDATION = "ITU-R P.840-7";

    /** Percentages for which the Recommendation provides maps of reduced cloud liquid water content. */
    public static final double[] PERCENTAGES = {
        0.1, 0.2, 0.3, 0.5, 1.0, 2.0, 3.0, 5.0, 10.0, 20.0, 30.0, 50.0, 60.0, 70.0, 80.0, 90.0, 95.0, 99.0};

    /** Log-normal approximation parameters of the columnar cloud liquid content. */
    public record Lognormal(double m, double sigma, double pclw) {
    }

    private static final ConcurrentHashMap<String, BilinearGrid> CACHE = new ConcurrentHashMap<>();

    private P840() {
    }

    private static BilinearGrid grid(String file) {
        return CACHE.computeIfAbsent(file, f -> BilinearGrid.load("840/v7_lat.npz", "840/v7_lon.npz", f));
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

    /**
     * Cloud liquid water specific attenuation coefficient K_l in (dB/km)/(g/m3).
     *
     * @param fGHz     frequency in GHz (up to 1000)
     * @param tCelsius cloud liquid water temperature in degrees Celsius (0 degC in the Recommendation's method)
     */
    public static double specificAttenuationCoefficient(double fGHz, double tCelsius) {
        if (fGHz > 1000) {
            throw new IllegalArgumentException("Frequency must be introduced in GHz and the maximum range is 1000 GHz");
        }
        double theta = 300.0 / (tCelsius + 273.15); // Eq. 9
        double e0 = 77.66 + 103.3 * (theta - 1); // Eq. 6
        double e1 = 0.0671 * e0; // Eq. 7
        double e2 = 3.52; // Eq. 8
        double fp = 20.20 - 146 * (theta - 1) + 316.0 * (theta - 1) * (theta - 1); // Eq. 10
        double fs = 39.8 * fp; // Eq. 11
        double fpr = fGHz / fp;
        double fsr = fGHz / fs;
        double epsP = (e0 - e1) / (1 + fpr * fpr) + (e1 - e2) / (1 + fsr * fsr) + e2; // Eq. 5
        double epsPP = fGHz * (e0 - e1) / (fp * (1 + fpr * fpr)) + fGHz * (e1 - e2) / (fs * (1 + fsr * fsr)); // Eq. 4
        double eta = (2 + epsP) / epsPP; // Eq. 3
        return (0.819 * fGHz) / (epsPP * (1 + eta * eta)); // Eq. 2
    }

    /** Total columnar content of reduced cloud liquid water in kg/m2 exceeded for p% of the average year. */
    public static double columnarContentReducedLiquid(double lat, double lon, double p) {
        double lonN = Maths.lon360(lon);
        double[] avail = PERCENTAGES;
        boolean exact = Arrays.stream(avail).anyMatch(x -> x == p);
        if (exact) {
            return lred(lat, lonN, p);
        }
        int idx = Maths.searchSortedRight(avail, p) - 1;
        idx = Math.min(Math.max(idx, 0), avail.length);
        if (idx + 1 >= avail.length) {
            throw new IllegalArgumentException("p = " + p + " is outside the range supported by the data (0.1 - 99)");
        }
        double pBelow = avail[idx];
        double pAbove = avail[idx + 1];
        double a = lred(lat, lonN, pAbove);
        double b = lred(lat, lonN, pBelow);
        return b + (a - b) * (Math.log(p) - Math.log(pBelow)) / (Math.log(pAbove) - Math.log(pBelow));
    }

    private static double lred(double lat, double lon360, double p) {
        return grid("840/v7_lred_" + tag(p) + ".npz").at(lat, lon360);
    }

    /**
     * Cloud attenuation in dB exceeded for p% of the average year (never negative).
     *
     * @param lredOrNull total columnar content of reduced cloud liquid water in kg/m2, or {@code null} to take it
     *                   from the Recommendation's maps
     */
    public static double cloudAttenuation(double lat, double lon, double elDeg, double fGHz, double p,
            Double lredOrNull) {
        double kl = specificAttenuationCoefficient(fGHz, 0.0);
        double lred = lredOrNull != null ? lredOrNull : columnarContentReducedLiquid(lat, lon, p);
        double a = lred * kl / Math.sin(Math.toRadians(elDeg));
        return a < 0 ? 0.0 : a;
    }

    public static double cloudAttenuation(double lat, double lon, double elDeg, double fGHz, double p) {
        return cloudAttenuation(lat, lon, elDeg, fGHz, p, null);
    }

    /** Parameters (m, sigma, P_clw) of the log-normal model of the columnar cloud liquid water content. */
    public static Lognormal lognormalApproximationCoefficient(double lat, double lon) {
        double lonN = Maths.lon360(lon);
        return new Lognormal(
                grid("840/v7_m.npz").at(lat, lonN),
                grid("840/v7_sigma.npz").at(lat, lonN),
                grid("840/v7_pclw.npz").at(lat, lonN));
    }
}
