package org.itur.models;

import java.util.logging.Logger;

import org.itur.data.DataStore;

/**
 * Recommendation ITU-R P.676-11 (09/2016): attenuation by atmospheric gases.
 *
 * <p>Units: frequency in GHz, pressure in hPa, temperature in kelvin, water vapour density in g/m3,
 * heights in km, elevation angles in degrees, specific attenuation in dB/km.
 */
public final class P676 {

    public static final String RECOMMENDATION = "ITU-R P.676-11";

    private static final Logger LOG = Logger.getLogger(P676.class.getName());

    /** Calculation method for path attenuations. */
    public enum Mode {
        /** Approximate method of Annex 2 (valid up to 350 GHz). */
        APPROX,
        /** Line-by-line method of Annex 1. */
        EXACT
    }

    /** Specific attenuation split between dry air and water vapour. */
    public record Specific(double gamma0, double gammaw) {
    }

    /** Equivalent heights of dry air and water vapour, in km. */
    public record EquivalentHeights(double h0, double hw) {
    }

    // Spectroscopic data (Tables 1 and 2 of Annex 1), loaded once from the data files.
    private static final double[] F_OX;
    private static final double[] A1;
    private static final double[] A2;
    private static final double[] A3;
    private static final double[] A4;
    private static final double[] A5;
    private static final double[] A6;
    private static final double[] F_WV;
    private static final double[] B1;
    private static final double[] B2;
    private static final double[] B3;
    private static final double[] B4;
    private static final double[] B5;
    private static final double[] B6;
    private static final int[] APPROX_WV_ROWS = {0, 3, 4, 5, 7, 12, 20, 24, 34};

    static {
        double[][] ox = DataStore.loadCsv("676/v11_lines_oxygen.txt", 1);
        double[][] wv = DataStore.loadCsv("676/v11_lines_water_vapour.txt", 1);
        F_OX = column(ox, 0);
        A1 = column(ox, 1);
        A2 = column(ox, 2);
        A3 = column(ox, 3);
        A4 = column(ox, 4);
        A5 = column(ox, 5);
        A6 = column(ox, 6);
        F_WV = column(wv, 0);
        B1 = column(wv, 1);
        B2 = column(wv, 2);
        B3 = column(wv, 3);
        B4 = column(wv, 4);
        B5 = column(wv, 5);
        B6 = column(wv, 6);
    }

    private P676() {
    }

    private static double[] column(double[][] rows, int c) {
        double[] out = new double[rows.length];
        for (int i = 0; i < rows.length; i++) {
            out[i] = rows[i][c];
        }
        return out;
    }

    // ------------------------------------------------------------------ specific attenuation

    /** Specific attenuation of dry air by the line-by-line method (Eq. 1, 3-7 of Annex 1). */
    public static double gamma0Exact(double f, double p, double rho, double t) {
        double theta = 300.0 / t;
        double e = rho * t / 216.7;
        double sum = 0.0;
        for (int i = 0; i < F_OX.length; i++) {
            double fox = F_OX[i];
            double d = A3[i] * 1e-4 * (p * Math.pow(theta, 0.8 - A4[i]) + 1.1 * e * theta);
            d = Math.sqrt(d * d + 2.25e-6);
            double delta = (A5[i] + A6[i] * theta) * 1e-4 * (p + e) * Math.pow(theta, 0.8);
            double fi = f / fox * ((d - delta * (fox - f)) / ((fox - f) * (fox - f) + d * d)
                    + (d - delta * (fox + f)) / ((fox + f) * (fox + f) + d * d));
            double si = A1[i] * 1e-7 * p * theta * theta * theta * Math.exp(A2[i] * (1 - theta));
            sum += si * fi;
        }
        double d = 5.6e-4 * (p + e) * Math.pow(theta, 0.8);
        double nd = f * p * theta * theta * (6.14e-5 / (d * (1 + (f / d) * (f / d)))
                + 1.4e-12 * p * Math.pow(theta, 1.5) / (1 + 1.9e-5 * Math.pow(f, 1.5)));
        return 0.1820 * f * (sum + nd);
    }

    /** Specific attenuation of water vapour by the line-by-line method. */
    public static double gammawExact(double f, double p, double rho, double t) {
        double theta = 300.0 / t;
        double e = rho * t / 216.7;
        double sum = 0.0;
        for (int i = 0; i < F_WV.length; i++) {
            double fwv = F_WV[i];
            double d = B3[i] * 1e-4 * (p * Math.pow(theta, B4[i]) + B5[i] * e * Math.pow(theta, B6[i]));
            d = 0.535 * d + Math.sqrt(0.217 * d * d + 2.1316e-12 * fwv * fwv / theta);
            double fi = f / fwv * (d / ((fwv - f) * (fwv - f) + d * d) + d / ((fwv + f) * (fwv + f) + d * d));
            double si = B1[i] * 1e-1 * e * Math.pow(theta, 3.5) * Math.exp(B2[i] * (1 - theta));
            sum += si * fi;
        }
        return 0.1820 * f * sum;
    }

    /** Total gaseous specific attenuation by the line-by-line method, in dB/km. */
    public static double gammaExact(double f, double p, double rho, double t) {
        return gamma0Exact(f, p, rho, t) + gammawExact(f, p, rho, t);
    }

    /** Dry-air specific attenuation by the approximate method of Annex 2 (all oxygen lines, no line overlap). */
    public static double gamma0Approx(double f, double p, double rho, double t) {
        double theta = 300.0 / t;
        double e = rho * t / 216.7;
        double sum = 0.0;
        for (int i = 0; i < F_OX.length; i++) {
            double fox = F_OX[i];
            double d = A3[i] * 1e-4 * (p * Math.pow(theta, 0.8 - A4[i]) + 1.1 * e * theta);
            double delta = (A5[i] + A6[i] * theta) * 1e-4 * (p + e) * Math.pow(theta, 0.8);
            double fi = f / fox * ((d - delta * (fox - f)) / ((fox - f) * (fox - f) + d * d)
                    + (d - delta * (fox + f)) / ((fox + f) * (fox + f) + d * d));
            double si = A1[i] * 1e-7 * p * theta * theta * theta * Math.exp(A2[i] * (1 - theta));
            sum += si * fi;
        }
        double d = 5.6e-4 * (p + e) * Math.pow(theta, 0.8);
        double nd = f * p * theta * theta * (6.14e-5 / (d * (1 + (f / d) * (f / d)))
                + 1.4e-12 * p * Math.pow(theta, 1.5) / (1 + 1.9e-5 * Math.pow(f, 1.5)));
        return 0.1820 * f * (sum + nd);
    }

    /** Water-vapour specific attenuation by the approximate method of Annex 2 (reduced set of lines). */
    public static double gammawApprox(double f, double p, double rho, double t) {
        double theta = 300.0 / t;
        double e = rho * t / 216.7;
        double sum = 0.0;
        for (int row : APPROX_WV_ROWS) {
            double fwv = F_WV[row];
            double d = B3[row] * 1e-4 * (p * Math.pow(theta, B4[row]) + B5[row] * e * Math.pow(theta, B6[row]));
            double fi = f / fwv * (d / ((fwv - f) * (fwv - f) + d * d) + d / ((fwv + f) * (fwv + f) + d * d));
            double si = B1[row] * 1e-1 * e * Math.pow(theta, 3.5) * Math.exp(B2[row] * (1 - theta));
            sum += si * fi;
        }
        return 0.1820 * f * sum;
    }

    private static Specific approximation(double f, double el, double rho, double p, double t) {
        if (f > 350) {
            LOG.warning("The approximate method of ITU-R P.676-11 is only recommended below 350 GHz");
        }
        if (5 > el || (el % 90) < 5) {
            LOG.warning("The approximate method of ITU-R P.676-11 is only recommended for elevation angles "
                    + "between 5 and 90 degrees");
        }
        return new Specific(gamma0Approx(f, p, rho, t), gammawApprox(f, p, rho, t));
    }

    // ------------------------------------------------------------------ equivalent heights

    /** Equivalent heights for slant and inclined paths (Eq. 31-37 of Annex 2); {@code p} is the total pressure. */
    public static EquivalentHeights slantInclinedPathEquivalentHeight(double f, double p) {
        double rp = p / 1013.25;
        double t1 = 4.64 / (1 + 0.066 * Math.pow(rp, -2.3))
                * Math.exp(-Math.pow((f - 59.7) / (2.87 + 12.4 * Math.exp(-7.9 * rp)), 2));
        double t2 = (0.14 * Math.exp(2.12 * rp)) / ((f - 118.75) * (f - 118.75) + 0.031 * Math.exp(2.2 * rp));
        double t3 = 0.0114 / (1 + 0.14 * Math.pow(rp, -2.6)) * f
                * (-0.0247 + 0.0001 * f + 1.61e-6 * f * f)
                / (1 - 0.0169 * f + 4.1e-5 * f * f + 3.2e-7 * f * f * f);
        double h0 = 6.1 / (1 + 0.17 * Math.pow(rp, -1.1)) * (1 + t1 + t2 + t3);
        if (f < 70) {
            h0 = Math.min(h0, 10.7 * Math.pow(rp, 0.3));
        }
        double sigmaW = 1.013 / (1 + Math.exp(-8.6 * (rp - 0.57)));
        double hw = 1.66 * (1 + (1.39 * sigmaW) / ((f - 22.235) * (f - 22.235) + 2.56 * sigmaW)
                + (3.37 * sigmaW) / ((f - 183.31) * (f - 183.31) + 4.69 * sigmaW)
                + (1.58 * sigmaW) / ((f - 325.1) * (f - 325.1) + 2.89 * sigmaW));
        return new EquivalentHeights(h0, hw);
    }

    // ------------------------------------------------------------------ paths

    /**
     * Gaseous attenuation on a terrestrial path.
     *
     * @param r path length in km
     * @return attenuation in dB
     */
    public static double terrestrialPath(double r, double f, double el, double rho, double p, double t, Mode mode) {
        if (mode == Mode.APPROX) {
            Specific s = approximation(f, el, rho, p, t);
            return (s.gamma0() + s.gammaw()) * r;
        }
        return gammaExact(f, p, rho, t) * r;
    }

    /**
     * Gaseous attenuation on a slant path between the ground and space (never negative).
     *
     * @param vtOrNull integrated water vapour content in kg/m2, or {@code null} to use the specific attenuation
     * @param hOrNull  station altitude in km (needed together with {@code vtOrNull})
     * @return attenuation in dB
     */
    public static double slantPath(double f, double el, double rho, double p, double t,
            Double vtOrNull, Double hOrNull, Mode mode) {
        double a;
        if (mode == Mode.APPROX) {
            Specific s = approximation(f, el, rho, p, t);
            double e = rho * t / 216.7;
            EquivalentHeights eh = slantInclinedPathEquivalentHeight(f, p + e);
            double aw = (vtOrNull != null && hOrNull != null)
                    ? zenithWaterVapourAttenuation(f, vtOrNull, hOrNull)
                    : s.gammaw() * eh.hw();
            double a0 = s.gamma0() * eh.h0();
            a = (a0 + aw) / Math.sin(Math.toRadians(el));
        } else {
            a = slantPathExact(f, el, rho);
        }
        return a < 0 ? 0.0 : a;
    }

    private static double slantPathExact(double f, double el, double rho0) {
        final int n = 922;
        double[] hN = new double[n];
        double[] deltaH = new double[n];
        double[] tN = new double[n];
        double[] pN = new double[n];
        double[] rhoN = new double[n];
        double[] nIdx = new double[n];
        double denom = Math.exp(1.0 / 100.0) - 1.0;
        for (int i = 0; i < n; i++) {
            deltaH[i] = 0.0001 * Math.exp(i / 100.0); // Eq. 21
            hN[i] = 0.0001 * ((Math.exp(i / 100.0) - 1.0) / denom);
            tN[i] = P835.standardTemperature(hN[i]);
            pN[i] = P835.standardPressure(hN[i]);
            rhoN[i] = P835.standardWaterVapourDensity(hN[i], 2.0, rho0);
            double eN = rhoN[i] * tN[i] / 216.7;
            nIdx[i] = P453.radioRefractiveIndex(pN[i], eN, tN[i]);
        }

        double b = Math.PI / 2 - Math.toRadians(el);
        double agas = 0.0;
        for (int i = 0; i < n; i++) {
            double nRatio = nIdx[i] / nIdx[Math.min(i + 1, n - 1)];
            double r = 6371.0 + hN[i];
            double delta = deltaH[i];
            double cosB = Math.cos(b);
            double a = -r * cosB + 0.5 * Math.sqrt(4 * r * r * cosB * cosB + 8 * r * delta + 4 * delta * delta); // Eq. 17
            double arg = (-a * a - 2 * r * delta - delta * delta) / (2 * a * r + 2 * a * delta);
            double alpha = Math.PI - Math.acos(Math.min(Math.max(arg, -1.0), 1.0)); // Eq. 18
            double gamma = gammaExact(f, pN[i], rhoN[i], tN[i]);
            agas += a * gamma; // Eq. 20
            b = Math.asin(Math.sin(alpha) * nRatio); // Eq. 19
        }
        return agas;
    }

    /**
     * Gaseous attenuation on an inclined path between two stations at heights h1 and h2 (km, both below 10 km).
     */
    public static double inclinedPath(double f, double el, double rho, double p, double t,
            double h1, double h2, Mode mode) {
        if (h1 > 10 || h2 > 10) {
            throw new IllegalArgumentException("Both the transmitter and the receiver must be at an altitude of less "
                    + "than 10 km above sea level. Current altitude Tx: " + h1 + " km, Rx: " + h2 + " km");
        }
        double gamma0;
        double gammaw;
        if (mode == Mode.APPROX) {
            rho = rho * Math.exp(h1 / 2);
            Specific s = approximation(f, el, rho, p, t);
            gamma0 = s.gamma0();
            gammaw = s.gammaw();
        } else {
            gamma0 = gamma0Exact(f, p, rho, t);
            gammaw = 0.0;
        }
        double e = rho * t / 216.7;
        EquivalentHeights eh = slantInclinedPathEquivalentHeight(f, p + e);
        double h0 = eh.h0();
        double hw = eh.hw();

        if (5 < el && el < 90) {
            double h0p = h0 * (Math.exp(-h1 / h0) - Math.exp(-h2 / h0));
            double hwp = hw * (Math.exp(-h1 / hw) - Math.exp(-h2 / hw));
            return (gamma0 * h0p + gammaw * hwp) / Math.sin(Math.toRadians(el));
        }
        final double re = 8500.0;
        double el1 = el;
        double el2 = Math.toDegrees(Math.acos(((re + h1) / (re + h2)) * Math.cos(Math.toRadians(el1))));
        return gamma0 * Math.sqrt(h0) * (eq33(re, h1, h0, el1, xi(re, el1, h1, h0)) - eq33(re, h2, h0, el2, xi(re, el2, h2, h0)))
                + gammaw * Math.sqrt(hw) * (eq33(re, h1, hw, el1, xi(re, el1, h1, hw))
                        - eq33(re, h2, hw, el2, xi(re, el2, h2, hw)));
    }

    private static double bigF(double x) {
        return 1.0 / (0.661 * x + 0.339 * Math.sqrt(x * x + 5.51));
    }

    private static double xi(double re, double elDeg, double h, double hEq) {
        return Math.tan(Math.toRadians(elDeg)) * Math.sqrt((re + h) / hEq);
    }

    private static double eq33(double re, double hNum, double hDen, double elDeg, double x) {
        return Math.sqrt(re + hNum) * bigF(x) * Math.exp(-hNum / hDen) / Math.cos(Math.toRadians(elDeg));
    }

    /**
     * Water vapour attenuation at zenith (Annex 2, Section 2.3), from the integrated water vapour content
     * {@code vt} (kg/m2) and the station altitude {@code h} (km).
     */
    public static double zenithWaterVapourAttenuation(double f, double vt, double h) {
        final double fRef = 20.6;
        final double pRef = 815.0;
        double rhoRef = vt / 3.67;
        double tRef = 14 * Math.log(0.22 * vt / 3.67) + 3; // degrees Celsius

        double a = 0.2048 * Math.exp(-Math.pow((f - 22.43) / 3.097, 2))
                + 0.2326 * Math.exp(-Math.pow((f - 183.5) / 4.096, 2))
                + 0.2073 * Math.exp(-Math.pow((f - 325) / 3.651, 2)) - 0.113;
        double b = 8.741e4 * Math.exp(-0.587 * f) + 312.2 * Math.pow(f, -2.38) + 0.723;
        double hh = Math.min(h, 4.0);

        double term1 = 0.0176 * vt * gammawApprox(f, pRef, rhoRef, tRef + 273.15)
                / gammawApprox(fRef, pRef, rhoRef, tRef + 273.15);
        return f < 20 ? term1 : term1 * (a * Math.pow(hh, b) + 1);
    }

    /**
     * Water vapour attenuation at zenith, using the P.836 maps for the integrated water vapour content (and the
     * P.1511 topography for the altitude) when they are not supplied.
     */
    public static double zenithWaterVapourAttenuation(double lat, double lon, double p, double f,
            Double vtOrNull, Double hOrNull) {
        double h = hOrNull != null ? hOrNull : P1511.topographicAltitude(lat, lon);
        double vt = vtOrNull != null ? vtOrNull : P836.totalWaterVapourContent(lat, lon, p, h);
        return zenithWaterVapourAttenuation(f, vt, h);
    }
}
