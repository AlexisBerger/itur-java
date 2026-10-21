package org.itur;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Recommendation ITU-R P.676-11 (09/2016): attenuation by atmospheric gases. Provides the
 * line-by-line specific attenuation (Annex 1), the approximate method (Annex 2) and the slant,
 * inclined and terrestrial path attenuations.
 */
public final class P676 {

    private P676() {
    }

    /** Calculation method: {@link #APPROX} (Annex 2) or {@link #EXACT} (Annex 1, line by line). */
    public enum Mode {
        APPROX, EXACT
    }

    /** Equivalent heights (km) for oxygen ({@code h0}) and water vapour ({@code hw}). */
    public record Heights(double h0, double hw) {
    }

    // ---- spectroscopic data -------------------------------------------------------------------

    private static final double[][] OX = readTable("/itur/data/676/v11_lines_oxygen.txt");
    private static final double[][] WV = readTable("/itur/data/676/v11_lines_water_vapour.txt");
    /** Water vapour lines kept by the approximate model (rows marked with an asterisk in Table 2). */
    private static final int[] APPROX_ROWS = {0, 3, 4, 5, 7, 12, 20, 24, 34};

    private static double[][] readTable(String resource) {
        try (InputStream in = P676.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource " + resource);
            }
            List<double[]> rows = new ArrayList<>();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line = r.readLine(); // header
                while ((line = r.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    String[] t = line.split(",");
                    double[] row = new double[t.length];
                    for (int i = 0; i < t.length; i++) {
                        row[i] = Double.parseDouble(t[i].trim());
                    }
                    rows.add(row);
                }
            }
            return rows.toArray(new double[0][]);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ---- specific attenuation ------------------------------------------------------------------

    /** Specific attenuation of dry air (dB/km), line-by-line (Annex 1). */
    public static double gamma0Exact(double fGHz, double pHPa, double rho, double tKelvin) {
        double theta = 300 / tKelvin;
        double e = rho * tKelvin / 216.7;
        double sum = 0;
        for (double[] l : OX) {
            double fOx = l[0];
            double dF = l[3] * 1e-4 * (pHPa * Math.pow(theta, 0.8 - l[4]) + 1.1 * e * theta);
            dF = Math.sqrt(dF * dF + 2.25e-6);
            double delta = (l[5] + l[6] * theta) * 1e-4 * (pHPa + e) * Math.pow(theta, 0.8);
            double fi = fGHz / fOx * ((dF - delta * (fOx - fGHz)) / ((fOx - fGHz) * (fOx - fGHz) + dF * dF)
                + (dF - delta * (fOx + fGHz)) / ((fOx + fGHz) * (fOx + fGHz) + dF * dF));
            double si = l[1] * 1e-7 * pHPa * Math.pow(theta, 3) * Math.exp(l[2] * (1 - theta));
            sum += si * fi;
        }
        double d = 5.6e-4 * (pHPa + e) * Math.pow(theta, 0.8);
        double nd = fGHz * pHPa * theta * theta * (6.14e-5 / (d * (1 + Math.pow(fGHz / d, 2)))
            + 1.4e-12 * pHPa * Math.pow(theta, 1.5) / (1 + 1.9e-5 * Math.pow(fGHz, 1.5)));
        return 0.1820 * fGHz * (sum + nd);
    }

    /** Specific attenuation of water vapour (dB/km), line-by-line (Annex 1). */
    public static double gammaWExact(double fGHz, double pHPa, double rho, double tKelvin) {
        double theta = 300 / tKelvin;
        double e = rho * tKelvin / 216.7;
        double sum = 0;
        for (double[] l : WV) {
            double fWv = l[0];
            double dF = l[3] * 1e-4 * (pHPa * Math.pow(theta, l[4]) + l[5] * e * Math.pow(theta, l[6]));
            dF = 0.535 * dF + Math.sqrt(0.217 * dF * dF + 2.1316e-12 * fWv * fWv / theta);
            double fi = fGHz / fWv * (dF / ((fWv - fGHz) * (fWv - fGHz) + dF * dF)
                + dF / ((fWv + fGHz) * (fWv + fGHz) + dF * dF));
            double si = l[1] * 1e-1 * e * Math.pow(theta, 3.5) * Math.exp(l[2] * (1 - theta));
            sum += si * fi;
        }
        return 0.1820 * fGHz * sum;
    }

    /** Total gaseous specific attenuation (dB/km), line-by-line. */
    public static double gammaExact(double fGHz, double pHPa, double rho, double tKelvin) {
        return gamma0Exact(fGHz, pHPa, rho, tKelvin) + gammaWExact(fGHz, pHPa, rho, tKelvin);
    }

    /** Specific attenuation of dry air (dB/km), approximate method (Annex 2). */
    public static double gamma0Approx(double fGHz, double pHPa, double rho, double tKelvin) {
        double theta = 300 / tKelvin;
        double e = rho * tKelvin / 216.7;
        double sum = 0;
        for (double[] l : OX) {
            double fOx = l[0];
            double dF = l[3] * 1e-4 * (pHPa * Math.pow(theta, 0.8 - l[4]) + 1.1 * e * theta);
            double delta = (l[5] + l[6] * theta) * 1e-4 * (pHPa + e) * Math.pow(theta, 0.8);
            double fi = fGHz / fOx * ((dF - delta * (fOx - fGHz)) / ((fOx - fGHz) * (fOx - fGHz) + dF * dF)
                + (dF - delta * (fOx + fGHz)) / ((fOx + fGHz) * (fOx + fGHz) + dF * dF));
            double si = l[1] * 1e-7 * pHPa * Math.pow(theta, 3) * Math.exp(l[2] * (1 - theta));
            sum += si * fi;
        }
        double d = 0.00056 * (pHPa + e) * Math.pow(theta, 0.8);
        double nd = fGHz * pHPa * theta * theta * (6.14e-5 / (d * (1 + Math.pow(fGHz / d, 2)))
            + 1.4e-12 * pHPa * Math.pow(theta, 1.5) / (1 + 1.9e-5 * Math.pow(fGHz, 1.5)));
        return 0.182 * fGHz * (sum + nd);
    }

    /** Specific attenuation of water vapour (dB/km), approximate method (Annex 2). */
    public static double gammaWApprox(double fGHz, double pHPa, double rho, double tKelvin) {
        double theta = 300 / tKelvin;
        double e = rho * tKelvin / 216.7;
        double sum = 0;
        for (int row : APPROX_ROWS) {
            double[] l = WV[row];
            double fWv = l[0];
            double dF = l[3] * 0.0001 * (pHPa * Math.pow(theta, l[4]) + l[5] * e * Math.pow(theta, l[6]));
            double fi = fGHz / fWv * (dF / ((fWv - fGHz) * (fWv - fGHz) + dF * dF)
                + dF / ((fWv + fGHz) * (fWv + fGHz) + dF * dF));
            double si = l[1] * 0.1 * e * Math.pow(theta, 3.5) * Math.exp(l[2] * (1 - theta));
            sum += si * fi;
        }
        return 0.182 * fGHz * sum;
    }

    // ---- equivalent heights, zenith water vapour -------------------------------------------------

    /**
     * Equivalent heights for oxygen and water vapour (Annex 2, Eqs. 30-31).
     *
     * @param pHPa total (barometric) pressure plus water vapour pressure, {@code P + e} (hPa)
     */
    public static Heights slantInclinedPathEquivalentHeight(double fGHz, double pHPa) {
        double f = fGHz;
        double rp = pHPa / 1013.25;
        double t1 = 4.64 / (1 + 0.066 * Math.pow(rp, -2.3))
            * Math.exp(-Math.pow((f - 59.7) / (2.87 + 12.4 * Math.exp(-7.9 * rp)), 2));
        double t2 = 0.14 * Math.exp(2.12 * rp) / (Math.pow(f - 118.75, 2) + 0.031 * Math.exp(2.2 * rp));
        double t3 = 0.0114 / (1 + 0.14 * Math.pow(rp, -2.6)) * f * (-0.0247 + 0.0001 * f + 1.61e-6 * f * f)
            / (1 - 0.0169 * f + 4.1e-5 * f * f + 3.2e-7 * f * f * f);
        double h0 = 6.1 / (1 + 0.17 * Math.pow(rp, -1.1)) * (1 + t1 + t2 + t3);
        if (f < 70) {
            h0 = Math.min(h0, 10.7 * Math.pow(rp, 0.3));
        }
        double sigmaW = 1.013 / (1 + Math.exp(-8.6 * (rp - 0.57)));
        double hw = 1.66 * (1 + 1.39 * sigmaW / (Math.pow(f - 22.235, 2) + 2.56 * sigmaW)
            + 3.37 * sigmaW / (Math.pow(f - 183.31, 2) + 4.69 * sigmaW)
            + 1.58 * sigmaW / (Math.pow(f - 325.1, 2) + 2.89 * sigmaW));
        return new Heights(h0, hw);
    }

    /**
     * Zenith attenuation due to water vapour (dB), Section 2.3 of the Recommendation.
     *
     * @param p percentage of time (used only to look up the water vapour content)
     * @param vtKgPerM2 integrated water vapour content (kg/m2), or {@code null} to take it from P.836
     * @param hKm station altitude (km), or {@code null} to take it from P.1511
     */
    public static double zenithWaterVapourAttenuation(double latDeg, double lonDeg, double p, double fGHz,
                                                     Double vtKgPerM2, Double hKm) {
        final double fRef = 20.6;
        final double pRef = 815;
        double h = hKm != null ? hKm : P1511.topographicAltitude(latDeg, lonDeg);
        double vt = vtKgPerM2 != null ? vtKgPerM2 : P836.totalWaterVapourContent(latDeg, lonDeg, p, h);
        double rhoRef = vt / 3.67;
        double tRef = 14 * Math.log(0.22 * vt / 3.67) + 3;
        double f = fGHz;
        double a = 0.2048 * Math.exp(-Math.pow((f - 22.43) / 3.097, 2))
            + 0.2326 * Math.exp(-Math.pow((f - 183.5) / 4.096, 2))
            + 0.2073 * Math.exp(-Math.pow((f - 325) / 3.651, 2)) - 0.113;
        double b = 87410.0 * Math.exp(-0.587 * f) + 312.2 * Math.pow(f, -2.38) + 0.723;
        h = Math.min(h, 4);
        double term1 = 0.0176 * vt * gammaWApprox(f, pRef, rhoRef, tRef + 273.15)
            / gammaWApprox(fRef, pRef, rhoRef, tRef + 273.15);
        return f < 20 ? term1 : term1 * (a * Math.pow(h, b) + 1);
    }

    // ---- path attenuation -------------------------------------------------------------------------

    /** Attenuation (dB) on a terrestrial path of {@code rKm} km. */
    public static double gaseousAttenuationTerrestrialPath(double rKm, double fGHz, double elDeg, double rho,
                                                           double pHPa, double tKelvin, Mode mode) {
        if (mode == Mode.APPROX) {
            return (gamma0Approx(fGHz, pHPa, rho, tKelvin) + gammaWApprox(fGHz, pHPa, rho, tKelvin)) * rKm;
        }
        return gammaExact(fGHz, pHPa, rho, tKelvin) * rKm;
    }

    /**
     * Gaseous attenuation (dB) of a slant path through the whole atmosphere; negative values are
     * clipped to 0.
     *
     * @param rho surface water vapour density (g/m3)
     * @param pHPa surface pressure (hPa)
     * @param tKelvin surface temperature (K)
     * @param vtKgPerM2 integrated water vapour content (kg/m2) or {@code null} (APPROX mode only)
     * @param hKm station altitude (km) or {@code null} (APPROX mode only)
     */
    public static double gaseousAttenuationSlantPath(double fGHz, double elDeg, double rho, double pHPa,
                                                     double tKelvin, Double vtKgPerM2, Double hKm, Mode mode) {
        double val;
        if (mode == Mode.APPROX) {
            double g0 = gamma0Approx(fGHz, pHPa, rho, tKelvin);
            double gw = gammaWApprox(fGHz, pHPa, rho, tKelvin);
            double e = rho * tKelvin / 216.7;
            Heights hh = slantInclinedPathEquivalentHeight(fGHz, pHPa + e);
            double aw = (vtKgPerM2 != null && hKm != null)
                ? zenithWaterVapourAttenuation(Double.NaN, Double.NaN, Double.NaN, fGHz, vtKgPerM2, hKm)
                : gw * hh.hw();
            double a0 = g0 * hh.h0();
            val = (a0 + aw) / Math.sin(Math.toRadians(elDeg));
        } else {
            val = rayTracedAttenuation(fGHz, elDeg, rho);
        }
        return val < 0 ? 0 : val;
    }

    /** Annex 1 ray tracing through 922 exponentially thickening layers of the reference atmosphere. */
    private static double rayTracedAttenuation(double fGHz, double elDeg, double rho0) {
        final int n = 922;
        double denom = Math.exp(1.0 / 100.0) - 1.0;
        double[] h = new double[n];
        double[] delta = new double[n];
        double[] temp = new double[n];
        double[] press = new double[n];
        double[] rho = new double[n];
        double[] refr = new double[n];
        for (int i = 0; i < n; i++) {
            delta[i] = 0.0001 * Math.exp(i / 100.0);
            h[i] = 0.0001 * ((Math.exp(i / 100.0) - 1.0) / denom);
            temp[i] = P835.standardTemperature(h[i]);
            press[i] = P835.standardPressure(h[i]);
            rho[i] = P835.standardWaterVapourDensity(h[i], 2.0, rho0);
            double e = rho[i] * temp[i] / 216.7;
            refr[i] = P453.radioRefractiveIndex(press[i], e, temp[i]);
        }
        double b = Math.PI / 2 - Math.toRadians(elDeg);
        double total = 0;
        for (int i = 0; i < n; i++) {
            double nRatio = refr[i] / refr[Math.min(i + 1, n - 1)];
            double r = 6371 + h[i];
            double cosB = Math.cos(b);
            double x = r * cosB;
            double s = 0.5 * Math.sqrt(4 * r * r * cosB * cosB + 8 * r * delta[i] + 4 * delta[i] * delta[i]);
            // a = s - x. For x > 0 this subtracts two nearly equal numbers (~4500 km each, difference
            // ~1e-4 km), losing ~8 digits; the algebraically identical (s^2 - x^2)/(s + x) does not.
            double a = x > 0 ? (2 * r * delta[i] + delta[i] * delta[i]) / (x + s) : s - x;
            double arg = (-a * a - 2 * r * delta[i] - delta[i] * delta[i]) / (2 * a * r + 2 * a * delta[i]);
            arg = Math.max(-1, Math.min(1, arg));
            double alpha = Math.PI - Math.acos(arg);
            total += a * gammaExact(fGHz, press[i], rho[i], temp[i]);
            b = Math.asin(Math.sin(alpha) * nRatio);
        }
        return total;
    }

    /**
     * Gaseous attenuation (dB) on an inclined path between two stations at altitudes {@code h1Km}
     * and {@code h2Km} (both below 10 km).
     */
    public static double gaseousAttenuationInclinedPath(double fGHz, double elDeg, double rho, double pHPa,
                                                        double tKelvin, double h1Km, double h2Km, Mode mode) {
        if (h1Km > 10 || h2Km > 10) {
            throw new IllegalArgumentException(String.format(
                "Both stations must be at an altitude of less than 10 km. Tx: %.2f km, Rx: %.2f km", h1Km, h2Km));
        }
        double g0;
        double gw;
        double rhoUsed = rho;
        if (mode == Mode.APPROX) {
            rhoUsed = rho * Math.exp(h1Km / 2);
            g0 = gamma0Approx(fGHz, pHPa, rhoUsed, tKelvin);
            gw = gammaWApprox(fGHz, pHPa, rhoUsed, tKelvin);
        } else {
            g0 = gamma0Exact(fGHz, pHPa, rho, tKelvin);
            gw = 0;
        }
        double e = rhoUsed * tKelvin / 216.7;
        Heights hh = slantInclinedPathEquivalentHeight(fGHz, pHPa + e);
        double h0 = hh.h0();
        double hw = hh.hw();
        if (5 < elDeg && elDeg < 90) {
            double h0p = h0 * (Math.exp(-h1Km / h0) - Math.exp(-h2Km / h0));
            double hwp = hw * (Math.exp(-h1Km / hw) - Math.exp(-h2Km / hw));
            return (g0 * h0p + gw * hwp) / Math.sin(Math.toRadians(elDeg));
        }
        final double re = 8500;
        double el1 = elDeg;
        double el2 = Math.toDegrees(Math.acos((re + h1Km) / (re + h2Km) * Math.cos(Math.toRadians(el1))));
        return g0 * Math.sqrt(h0) * (eq33(re, h1Km, h0, el1, xi(re, el1, h1Km, h0))
                - eq33(re, h2Km, h0, el2, xi(re, el2, h2Km, h0)))
            + gw * Math.sqrt(hw) * (eq33(re, h1Km, hw, el1, xi(re, el1, h1Km, hw))
                - eq33(re, h2Km, hw, el2, xi(re, el2, h2Km, hw)));
    }

    private static double bigF(double x) {
        return 1 / (0.661 * x + 0.339 * Math.sqrt(x * x + 5.51));
    }

    private static double xi(double re, double elDeg, double hKm, double hEq) {
        return Math.tan(Math.toRadians(elDeg)) * Math.sqrt((re + hKm) / hEq);
    }

    private static double eq33(double re, double hNum, double hDen, double elDeg, double x) {
        return Math.sqrt(re + hNum) * bigF(x) * Math.exp(-hNum / hDen) / Math.cos(Math.toRadians(elDeg));
    }
}
