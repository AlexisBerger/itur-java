package org.itur.models;

/**
 * Recommendation ITU-R P.838-3 (03/2005): specific attenuation model for rain for use in prediction methods.
 */
public final class P838 {

    public static final String RECOMMENDATION = "ITU-R P.838-3";

    /** Regression coefficients of {@code gamma_R = k * R^alpha}. */
    public record RainCoefficients(double k, double alpha) {
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

    private P838() {
    }

    private static double curve(double logF, double a, double b, double c) {
        double t = (logF - b) / c;
        return a * Math.exp(-(t * t));
    }

    private static double sum(double logF, double[] a, double[] b, double[] c) {
        double s = 0.0;
        for (int j = 0; j < a.length; j++) {
            s += curve(logF, a[j], b[j], c[j]);
        }
        return s;
    }

    /**
     * Coefficients k and alpha (Eq. 2-5 of the Recommendation).
     *
     * @param fGHz  frequency in GHz (1 to 1000)
     * @param elDeg path elevation angle in degrees
     * @param tauDeg polarisation tilt angle relative to the horizontal, in degrees (45 for circular)
     */
    public static RainCoefficients coefficients(double fGHz, double elDeg, double tauDeg) {
        double lf = Math.log10(fGHz);
        double kH = Math.pow(10.0, sum(lf, KH_A, KH_B, KH_C) + KH_M * lf + KH_CK);
        double kV = Math.pow(10.0, sum(lf, KV_A, KV_B, KV_C) + KV_M * lf + KV_CK);
        double aH = sum(lf, AH_A, AH_B, AH_C) + AH_M * lf + AH_CA;
        double aV = sum(lf, AV_A, AV_B, AV_C) + AV_M * lf + AV_CA;

        double cosEl = Math.cos(Math.toRadians(elDeg));
        double cos2Tau = Math.cos(Math.toRadians(2.0 * tauDeg));
        double k = (kH + kV + (kH - kV) * (cosEl * cosEl) * cos2Tau) / 2.0;
        double alpha = (kH * aH + kV * aV + (kH * aH - kV * aV) * (cosEl * cosEl) * cos2Tau) / (2.0 * k);
        return new RainCoefficients(k, alpha);
    }

    /**
     * Specific attenuation gamma_R = k R^alpha.
     *
     * @param rainRate rain rate in mm/h
     * @return specific attenuation in dB/km
     */
    public static double specificAttenuation(double rainRate, double fGHz, double elDeg, double tauDeg) {
        RainCoefficients c = coefficients(fGHz, elDeg, tauDeg);
        return c.k() * Math.pow(rainRate, c.alpha());
    }
}
