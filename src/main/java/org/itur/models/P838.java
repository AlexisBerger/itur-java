package org.itur.models;

import org.itur.util.Check;

/**
 * Recommendation ITU-R P.838-3 (03/2005): specific attenuation model for rain for use in
 * prediction methods.
 */
public final class P838 {

    private P838() {
    }

    /**
     * Coefficients {@code k} and {@code alpha} of the power law {@code gamma_R = k * R^alpha}.
     *
     * @param k coefficient {@code k}
     * @param alpha exponent {@code alpha}
     */
    public record Coefficients(double k, double alpha) {
    }

    private static final double[] KH_A = {-5.33980, -0.35351, -0.23789, -0.94158};
    private static final double[] KH_B = {-0.10008, 1.2697, 0.86036, 0.64552};
    private static final double[] KH_C = {1.13098, 0.454, 0.15354, 0.16817};
    private static final double KH_M = -0.18961;
    private static final double KH_CK = 0.71147;

    private static final double[] KV_A = {-3.80595, -3.44965, -0.39902, 0.50167};
    private static final double[] KV_B = {0.56934, -0.22911, 0.73042, 1.07319};
    private static final double[] KV_C = {0.81061, 0.51059, 0.11899, 0.27195};
    private static final double KV_M = -0.16398;
    private static final double KV_CK = 0.63297;

    private static final double[] AH_A = {-0.14318, 0.29591, 0.32177, -5.37610, 16.1721};
    private static final double[] AH_B = {1.82442, 0.77564, 0.63773, -0.96230, -3.29980};
    private static final double[] AH_C = {-0.55187, 0.19822, 0.13164, 1.47828, 3.4399};
    private static final double AH_M = 0.67849;
    private static final double AH_CA = -1.95537;

    private static final double[] AV_A = {-0.07771, 0.56727, -0.20238, -48.2991, 48.5833};
    private static final double[] AV_B = {2.3384, 0.95545, 1.1452, 0.791669, 0.791459};
    private static final double[] AV_C = {-0.76284, 0.54039, 0.26809, 0.116226, 0.116479};
    private static final double AV_M = -0.053739;
    private static final double AV_CA = 0.83433;

    private static double sumGauss(double[] a, double[] b, double[] c, double logF) {
        double s = 0;
        for (int j = 0; j < a.length; j++) {
            double z = (logF - b[j]) / c[j];
            s += a[j] * Math.exp(-(z * z));
        }
        return s;
    }

    /**
     * Coefficients k and alpha (Eqs. 2-5 of the Recommendation).
     *
     * @param fGHz frequency (GHz), 1 to 1000
     * @param elDeg path elevation angle (degrees)
     * @param tauDeg polarization tilt angle relative to the horizontal (degrees); 45 for circular
     * @return the coefficients {@code k} and {@code alpha}
     */
    public static Coefficients coefficients(double fGHz, double elDeg, double tauDeg) {
        Check.range("frequency (GHz)", fGHz, 1, 1000);
        Check.range("elevation angle (degrees)", elDeg, -90, 90);
        Check.finite("polarization tilt angle (degrees)", tauDeg);
        double lf = Math.log10(fGHz);
        double kh = Math.pow(10, sumGauss(KH_A, KH_B, KH_C, lf) + KH_M * lf + KH_CK);
        double kv = Math.pow(10, sumGauss(KV_A, KV_B, KV_C, lf) + KV_M * lf + KV_CK);
        double ah = sumGauss(AH_A, AH_B, AH_C, lf) + AH_M * lf + AH_CA;
        double av = sumGauss(AV_A, AV_B, AV_C, lf) + AV_M * lf + AV_CA;
        double cosEl = Math.cos(Math.toRadians(elDeg));
        double cos2El = cosEl * cosEl;
        double cos2Tau = Math.cos(Math.toRadians(2 * tauDeg));
        double k = (kh + kv + (kh - kv) * cos2El * cos2Tau) / 2.0;
        double alpha = (kh * ah + kv * av + (kh * ah - kv * av) * cos2El * cos2Tau) / (2.0 * k);
        return new Coefficients(k, alpha);
    }

    /**
     * Specific attenuation {@code gamma_R = k R^alpha} (dB/km).
     *
     * @param rMmPerHour rainfall rate (mm/h)
     * @param fGHz frequency (GHz)
     * @param elDeg elevation angle (degrees)
     * @param tauDeg polarization tilt angle (degrees)
     * @return specific attenuation due to rain (dB/km)
     */
    public static double rainSpecificAttenuation(double rMmPerHour, double fGHz, double elDeg, double tauDeg) {
        Check.nonNegative("rainfall rate (mm/h)", rMmPerHour);
        Coefficients c = coefficients(fGHz, elDeg, tauDeg);
        return c.k() * Math.pow(rMmPerHour, c.alpha());
    }
}
