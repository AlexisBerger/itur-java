package org.itur.models;

import org.itur.grid.Grid;
import org.itur.util.Special;

/**
 * Recommendation ITU-R P.840-7 (12/2017): attenuation due to clouds and fog.
 */
public final class P840 {

    private P840() {
    }

    private static final double[] AVAILABLE_P = {0.1, 0.2, 0.3, 0.5, 1.0, 2.0, 3.0, 5.0, 10.0, 20.0, 30.0, 50.0, 60.0,
        70.0, 80.0, 90.0, 95.0, 99.0};
    private static final String[] SUFFIX = {"01", "02", "03", "05", "1", "2", "3", "5", "10", "20", "30", "50", "60",
        "70", "80", "90", "95", "99"};

    /**
     * Parameters of the log-normal approximation of the columnar liquid water content.
     *
     * @param m mean of {@code ln L}
     * @param sigma standard deviation of {@code ln L}
     * @param pclw probability of cloud liquid water (%)
     */
    public record LognormalCoefficients(double m, double sigma, double pclw) {
    }

    private static double lred(int pIdx, double lat, double lon) {
        return Grid.load("840/v7_lred_" + SUFFIX[pIdx]).bilinear(lat, lon);
    }

    /**
     * Total columnar content of reduced cloud liquid water (kg/m2) exceeded for {@code p} % of an
     * average year; log-interpolated between the tabulated probabilities (0.1 .. 99 %).
     *
     * @param latDeg latitude (degrees, -90 to 90)
     * @param lonDeg longitude (degrees, -180 to 180 or 0 to 360)
     * @param p percentage of an average year the value is exceeded (%)
     * @return reduced columnar liquid water content (kg/m2)
     * @throws IllegalArgumentException if {@code p} is greater than 99
     */
    public static double columnarContentReducedLiquid(double latDeg, double lonDeg, double p) {
        double lon = Special.mod(lonDeg, 360);
        int exact = -1;
        for (int i = 0; i < AVAILABLE_P.length; i++) {
            if (AVAILABLE_P[i] == p) {
                exact = i;
            }
        }
        if (exact >= 0) {
            return lred(exact, latDeg, lon);
        }
        int idx = P836.searchSortedRight(AVAILABLE_P, p) - 1;
        idx = Math.max(0, Math.min(AVAILABLE_P.length, idx));
        if (idx + 1 >= AVAILABLE_P.length) {
            throw new IllegalArgumentException("p = " + p + " is out of the supported range (<= 99 %)");
        }
        double pBelow = AVAILABLE_P[idx];
        double pAbove = AVAILABLE_P[idx + 1];
        double a = lred(idx + 1, latDeg, lon);
        double b = lred(idx, latDeg, lon);
        return b + (a - b) * (Math.log(p) - Math.log(pBelow)) / (Math.log(pAbove) - Math.log(pBelow));
    }

    /**
     * Cloud liquid water specific attenuation coefficient K_l ((dB/km)/(g/m3)), Eqs. 2-11.
     *
     * @param fGHz frequency (GHz), up to 1000
     * @param tCelsius cloud liquid water temperature (deg C)
     * @return specific attenuation coefficient K_l ((dB/km)/(g/m3))
     */
    public static double specificAttenuationCoefficient(double fGHz, double tCelsius) {
        if (fGHz > 1000) {
            throw new IllegalArgumentException("Frequency must be given in GHz, maximum 1000 GHz");
        }
        double theta = 300.0 / (tCelsius + 273.15);
        double eps0 = 77.66 + 103.3 * (theta - 1);
        double eps1 = 0.0671 * eps0;
        double eps2 = 3.52;
        double fp = 20.20 - 146 * (theta - 1) + 316.0 * (theta - 1) * (theta - 1);
        double fs = 39.8 * fp;
        double f = fGHz;
        double epsP = (eps0 - eps1) / (1 + Math.pow(f / fp, 2)) + (eps1 - eps2) / (1 + Math.pow(f / fs, 2)) + eps2;
        double epsPP = f * (eps0 - eps1) / (fp * (1 + Math.pow(f / fp, 2)))
            + f * (eps1 - eps2) / (fs * (1 + Math.pow(f / fs, 2)));
        double eta = (2 + epsP) / epsPP;
        return (0.819 * f) / (epsPP * (1 + eta * eta));
    }

    /**
     * Cloud attenuation (dB) exceeded for {@code p} % of the time, {@code A = Lred * K_l / sin(el)},
     * with K_l evaluated at 0 deg C. Negative results are clipped to 0.
     *
     * @param latDeg latitude (degrees, -90 to 90)
     * @param lonDeg longitude (degrees, -180 to 180 or 0 to 360)
     * @param elDeg elevation angle (degrees)
     * @param fGHz frequency (GHz)
     * @param p percentage of an average year the value is exceeded (%)
     * @param lredKgPerM2 columnar content of reduced liquid water (kg/m2), or {@code null} to take
     *     it from the maps
     * @return cloud attenuation (dB)
     */
    public static double cloudAttenuation(double latDeg, double lonDeg, double elDeg, double fGHz, double p,
                                          Double lredKgPerM2) {
        double kl = specificAttenuationCoefficient(fGHz, 0);
        double lred = lredKgPerM2 != null ? lredKgPerM2 : columnarContentReducedLiquid(latDeg, lonDeg, p);
        double a = lred * kl / Math.sin(Math.toRadians(elDeg));
        return a < 0 ? 0 : a;
    }

    /**
     * Log-normal approximation coefficients (m, sigma, P_clw) at a location.
     *
     * @param latDeg latitude (degrees, -90 to 90)
     * @param lonDeg longitude (degrees, -180 to 180 or 0 to 360)
     * @return the coefficients of the log-normal approximation
     */
    public static LognormalCoefficients lognormalApproximationCoefficients(double latDeg, double lonDeg) {
        double lon = Special.mod(lonDeg, 360);
        return new LognormalCoefficients(
            Grid.load("840/v7_m").bilinear(latDeg, lon),
            Grid.load("840/v7_sigma").bilinear(latDeg, lon),
            Grid.load("840/v7_pclw").bilinear(latDeg, lon));
    }
}
