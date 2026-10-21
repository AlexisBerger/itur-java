package org.itur.models;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.itur.util.Check;

/**
 * Recommendation ITU-R P.676-13 (08/2022): attenuation by atmospheric gases. Provides the
 * line-by-line specific attenuation (Annex 1), the simplified slant-path method (Annex 2) and the
 * slant, inclined and terrestrial path attenuations.
 *
 * <p>Differences with P.676-11: the equivalent heights come from a tabulated oxygen height and an
 * analytical water-vapour height, the zenith water-vapour attenuation uses new coefficients, the
 * ray tracing evaluates the atmosphere at layer mid-points with the dry-air pressure, and the
 * explicit approximations of the specific attenuation were removed (the line-by-line values are
 * used instead).
 */
public final class P676 {

    private P676() {
    }

    /**
     * Calculation method: {@link #APPROX} (Annex 2, equivalent heights) or {@link #EXACT}
     * (Annex 1, ray tracing with the line-by-line specific attenuation).
     */
    public enum Mode {
        /** Annex 2: equivalent heights. */
        APPROX,
        /** Annex 1: line-by-line specific attenuation integrated by ray tracing. */
        EXACT
    }

    /**
     * Equivalent heights for oxygen and water vapour.
     *
     * @param h0 equivalent height of dry air (km)
     * @param hw equivalent height of water vapour (km)
     */
    public record Heights(double h0, double hw) {
    }

    // ---- spectroscopic data -------------------------------------------------------------------

    private static final double[][] OX = readTable("/itur/data/676/v13_lines_oxygen.txt");
    private static final double[][] WV = readTable("/itur/data/676/v13_lines_water_vapour.txt");
    /** Annex 2 Part 1 oxygen equivalent-height coefficients: f, a0, b0, c0, d0 (1 to 350 GHz). */
    private static final double[][] H0 = readTable("/itur/data/676/v13_h0_coefficients.txt");
    /** Annex 2 Table 4: water-vapour equivalent height, {@code (fi, ai, bi)} per line. */
    private static final double[][] HW_LINES = {
        {22.235080, 2.6846, 2.7649},
        {183.310087, 5.8905, 4.9219},
        {325.152888, 2.9810, 3.0748},
    };

    private static final System.Logger LOG = System.getLogger(P676.class.getName());
    private static final java.util.concurrent.atomic.AtomicBoolean WARNED_APPROX =
        new java.util.concurrent.atomic.AtomicBoolean();

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

    /**
     * Specific attenuation of dry air (dB/km), line-by-line (Annex 1).
     *
     * @param fGHz frequency (GHz)
     * @param pHPa total pressure (hPa)
     * @param rho water vapour density (g/m3)
     * @param tKelvin temperature (K)
     * @return specific attenuation of dry air (dB/km)
     */
    public static double gamma0Exact(double fGHz, double pHPa, double rho, double tKelvin) {
        Check.frequency(fGHz);
        Check.positive("total pressure (hPa)", pHPa);
        Check.nonNegative("water vapour density (g/m3)", rho);
        Check.kelvin(tKelvin);
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

    /**
     * Specific attenuation of water vapour (dB/km), line-by-line (Annex 1).
     *
     * @param fGHz frequency (GHz)
     * @param pHPa total pressure (hPa)
     * @param rho water vapour density (g/m3)
     * @param tKelvin temperature (K)
     * @return specific attenuation of water vapour (dB/km)
     */
    public static double gammaWExact(double fGHz, double pHPa, double rho, double tKelvin) {
        Check.frequency(fGHz);
        Check.positive("total pressure (hPa)", pHPa);
        Check.nonNegative("water vapour density (g/m3)", rho);
        Check.kelvin(tKelvin);
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

    /**
     * Total gaseous specific attenuation (dB/km), line-by-line: dry air plus water vapour.
     *
     * @param fGHz frequency (GHz)
     * @param pHPa total pressure (hPa)
     * @param rho water vapour density (g/m3)
     * @param tKelvin temperature (K)
     * @return total gaseous specific attenuation (dB/km)
     */
    public static double gammaExact(double fGHz, double pHPa, double rho, double tKelvin) {
        return gamma0Exact(fGHz, pHPa, rho, tKelvin) + gammaWExact(fGHz, pHPa, rho, tKelvin);
    }

    /**
     * P.676-13 has no explicit approximation of the dry-air specific attenuation: like ITU-Rpy,
     * this returns the <b>total</b> line-by-line {@link #gammaExact} (dry air + water vapour).
     *
     * @param fGHz frequency (GHz)
     * @param pHPa total pressure (hPa)
     * @param rho water vapour density (g/m3)
     * @param tKelvin temperature (K)
     * @return total gaseous specific attenuation (dB/km)
     * @deprecated P.676-13 has no approximation: use {@link #gamma0Exact}. A warning is logged the first
     *     time this is called.
     */
    @Deprecated(forRemoval = true)
    public static double gamma0Approx(double fGHz, double pHPa, double rho, double tKelvin) {
        warnApproxOnce();
        return gammaExact(fGHz, pHPa, rho, tKelvin);
    }

    /**
     * P.676-13 has no explicit approximation of the water-vapour specific attenuation: like
     * ITU-Rpy, this returns the <b>total</b> line-by-line {@link #gammaExact}.
     *
     * @param fGHz frequency (GHz)
     * @param pHPa total pressure (hPa)
     * @param rho water vapour density (g/m3)
     * @param tKelvin temperature (K)
     * @return total gaseous specific attenuation (dB/km)
     * @deprecated P.676-13 has no approximation: use {@link #gammaWExact}. A warning is logged the first
     *     time this is called.
     */
    @Deprecated(forRemoval = true)
    public static double gammaWApprox(double fGHz, double pHPa, double rho, double tKelvin) {
        warnApproxOnce();
        return gammaExact(fGHz, pHPa, rho, tKelvin);
    }

    private static void warnApproxOnce() {
        if (WARNED_APPROX.compareAndSet(false, true)) {
            LOG.log(System.Logger.Level.WARNING, "ITU-R P.676-13 has no approximation of gamma_0 and gamma_w; "
                + "gamma0Approx and gammaWApprox return the line-by-line total specific attenuation");
        }
    }

    // ---- equivalent heights, zenith water vapour -------------------------------------------------

    /** {@code numpy.interp}: linear interpolation, clamped to the end values outside the table. */
    private static double interp(double x, int col) {
        int n = H0.length;
        if (x <= H0[0][0]) {
            return H0[0][col];
        }
        if (x >= H0[n - 1][0]) {
            return H0[n - 1][col];
        }
        int lo = 0;
        int hi = n - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (H0[mid][0] <= x) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        double x0 = H0[lo][0];
        double x1 = H0[hi][0];
        double slope = (H0[hi][col] - H0[lo][col]) / (x1 - x0);
        return slope * (x - x0) + H0[lo][col];
    }

    /**
     * Equivalent heights for oxygen and water vapour (Annex 2, Eqs. 31 and 37).
     *
     * <p>{@code h0 = a0(f) + b0(f) T + c0(f) Ps + d0(f) rho} with the coefficients interpolated
     * linearly in the tabulated frequencies and {@code Ps = P + e} the total pressure;
     * {@code hw = A f + B + sum_i ai / ((f - fi)^2 + bi)}.
     *
     * @param fGHz frequency (GHz)
     * @param pHPa dry-air pressure {@code P} (hPa)
     * @param rho water vapour density (g/m3)
     * @param tKelvin temperature (K)
     * @return the equivalent heights {@code h0} (dry air) and {@code hw} (water vapour), in km
     */
    public static Heights slantInclinedPathEquivalentHeight(double fGHz, double pHPa, double rho,
                                                            double tKelvin) {
        Check.frequency(fGHz);
        Check.positive("total pressure (hPa)", pHPa);
        Check.nonNegative("water vapour density (g/m3)", rho);
        Check.kelvin(tKelvin);
        double e = rho * tKelvin / 216.7;
        double ps = pHPa + e;
        double h0 = interp(fGHz, 1) + interp(fGHz, 2) * tKelvin + interp(fGHz, 3) * ps + interp(fGHz, 4) * rho;
        double hw = 5.6585e-5 * fGHz + 1.8348;
        for (double[] l : HW_LINES) {
            hw += l[1] / ((fGHz - l[0]) * (fGHz - l[0]) + l[2]);
        }
        return new Heights(h0, hw);
    }

    /**
     * Zenith attenuation due to water vapour (dB), Annex 2 Section 2.3.
     *
     * @param latDeg latitude (degrees, -90 to 90)
     * @param lonDeg longitude (degrees, -180 to 180 or 0 to 360)
     * @param p percentage of time (used only to look up the water vapour content)
     * @param fGHz frequency (GHz)
     * @param vtKgPerM2 integrated water vapour content (kg/m2), or {@code null} to take it from P.836
     * @param hKm station altitude (km), or {@code null} to take it from P.1511
     * @return zenith attenuation due to water vapour (dB)
     */
    public static double zenithWaterVapourAttenuation(double latDeg, double lonDeg, double p, double fGHz,
                                                     Double vtKgPerM2, Double hKm) {
        Check.position(latDeg, lonDeg);
        Check.probability(p);
        Check.frequency(fGHz);
        Check.optionalPositive("water vapour content (kg/m2)", vtKgPerM2);
        Check.optionalFinite("station altitude (km)", hKm);
        double h = hKm != null ? hKm : P1511.topographicAltitude(latDeg, lonDeg);
        double vt = vtKgPerM2 != null ? vtKgPerM2 : P836.totalWaterVapourContent(latDeg, lonDeg, p, h);
        return zenithFromContent(fGHz, vt, h);
    }

    /** Annex 2 zenith water vapour attenuation from an integrated water vapour content (arguments checked). */
    private static double zenithFromContent(double f, double vt, double hKm) {
        final double fRef = 20.6;
        final double pRef = 845;
        if (Double.isNaN(vt)) {
            return Double.NaN; // location outside the P.836 map
        }
        double rhoRef = vt / 2.38;
        double tRef = 14 * Math.log(0.22 * vt / 2.38) + 3;
        double a = 0.2048 * Math.exp(-Math.pow((f - 22.43) / 3.097, 2))
            + 0.2326 * Math.exp(-Math.pow((f - 183.5) / 4.096, 2))
            + 0.2073 * Math.exp(-Math.pow((f - 325) / 3.651, 2)) - 0.1113;
        double b = 8.741e4 * Math.exp(-0.587 * f) + 312.2 * Math.pow(f, -2.38) + 0.723;
        double h = Math.max(0, Math.min(hKm, 4));
        double term1 = 0.0176 * vt * gammaWExact(f, pRef, rhoRef, tRef + 273.15)
            / gammaWExact(fRef, pRef, rhoRef, tRef + 273.15);
        return f < 20 ? term1 : term1 * (a * Math.pow(h, b) + 1);
    }

    // ---- path attenuation -------------------------------------------------------------------------

    /**
     * Attenuation (dB) on a terrestrial path of {@code rKm} km: the line-by-line specific attenuation
     * times the path length.
     *
     * @param rKm path length (km)
     * @param fGHz frequency (GHz)
     * @param rho water vapour density (g/m3)
     * @param pHPa total pressure (hPa)
     * @param tKelvin temperature (K)
     * @return gaseous attenuation of the path (dB)
     */
    public static double gaseousAttenuationTerrestrialPath(double rKm, double fGHz, double rho, double pHPa,
                                                           double tKelvin) {
        Check.nonNegative("path length (km)", rKm);
        return gammaExact(fGHz, pHPa, rho, tKelvin) * rKm;
    }

    /**
     * Attenuation (dB) on a terrestrial path of {@code rKm} km: the line-by-line specific attenuation
     * times the path length (P.676-13 has no separate approximation, so both modes are identical).
     *
     * @param rKm path length (km)
     * @param fGHz frequency (GHz)
     * @param elDeg elevation angle (degrees); not used, kept for compatibility with ITU-Rpy
     * @param rho water vapour density (g/m3)
     * @param pHPa total pressure (hPa)
     * @param tKelvin temperature (K)
     * @param mode calculation method (no effect for terrestrial paths)
     * @return gaseous attenuation of the path (dB)
     * @deprecated the elevation angle and the mode are not used: call
     *     {@link #gaseousAttenuationTerrestrialPath(double, double, double, double, double)}
     */
    @Deprecated
    public static double gaseousAttenuationTerrestrialPath(double rKm, double fGHz, double elDeg, double rho,
                                                           double pHPa, double tKelvin, Mode mode) {
        java.util.Objects.requireNonNull(mode, "mode");
        return gaseousAttenuationTerrestrialPath(rKm, fGHz, rho, pHPa, tKelvin);
    }

    /**
     * Gaseous attenuation (dB) of a slant path through the whole atmosphere; negative values are
     * clipped to 0.
     *
     * @param fGHz frequency (GHz)
     * @param elDeg elevation angle (degrees)
     * @param rho surface water vapour density (g/m3)
     * @param pHPa surface pressure (hPa)
     * @param tKelvin surface temperature (K)
     * @param vtKgPerM2 integrated water vapour content (kg/m2) or {@code null} (APPROX mode only)
     * @param hKm station altitude (km) or {@code null} (APPROX mode only)
     * @param mode {@link Mode#APPROX} (Annex 2) or {@link Mode#EXACT} (Annex 1 ray tracing)
     * @return gaseous attenuation of the slant path (dB)
     */
    public static double gaseousAttenuationSlantPath(double fGHz, double elDeg, double rho, double pHPa,
                                                     double tKelvin, Double vtKgPerM2, Double hKm, Mode mode) {
        Check.slantElevation(elDeg);
        Check.frequency(fGHz);
        Check.positive("total pressure (hPa)", pHPa);
        Check.nonNegative("water vapour density (g/m3)", rho);
        Check.kelvin(tKelvin);
        Check.optionalPositive("water vapour content (kg/m2)", vtKgPerM2);
        Check.optionalFinite("station altitude (km)", hKm);
        java.util.Objects.requireNonNull(mode, "mode");
        double val;
        if (mode == Mode.APPROX) {
            double g0 = gamma0Exact(fGHz, pHPa, rho, tKelvin);
            double gw = gammaWExact(fGHz, pHPa, rho, tKelvin);
            Heights hh = slantInclinedPathEquivalentHeight(fGHz, pHPa, rho, tKelvin);
            double aw = (vtKgPerM2 != null && hKm != null)
                ? zenithFromContent(fGHz, vtKgPerM2, hKm)
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
        double[] hMid = new double[n];
        double[] delta = new double[n];
        double[] temp = new double[n];
        double[] press = new double[n];
        double[] rho = new double[n];
        double[] refr = new double[n];
        for (int i = 0; i < n; i++) {
            delta[i] = 0.0001 * Math.exp(i / 100.0);
            h[i] = 0.0001 * ((Math.exp(i / 100.0) - 1.0) / denom);
            // the atmosphere is evaluated at the layer mid-point (P.676-13 Annex 1)
            hMid[i] = h[i] + delta[i] / 2;
            temp[i] = P835.standardTemperature(hMid[i]);
            press[i] = P835.standardPressure(hMid[i]);
            rho[i] = P835.standardWaterVapourDensity(hMid[i], 2.0, rho0);
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
            // Eq. 18b
            double alpha = Math.asin(Math.max(-1, Math.min(1, r / (r + delta[i]) * Math.sin(b))));
            // the specific attenuation takes the dry-air pressure (total pressure minus e)
            double pDry = press[i] - rho[i] * temp[i] / 216.7;
            total += a * gammaExact(fGHz, pDry, rho[i], temp[i]);
            b = Math.asin(Math.sin(alpha) * nRatio);
        }
        return total;
    }

    /**
     * Gaseous attenuation (dB) on an inclined path between two stations at altitudes {@code h1Km}
     * and {@code h2Km} (both below 10 km).
     *
     * @param fGHz frequency (GHz)
     * @param elDeg elevation angle (degrees)
     * @param rho surface water vapour density (g/m3)
     * @param pHPa surface pressure (hPa)
     * @param tKelvin surface temperature (K)
     * @param h1Km altitude of the first station (km)
     * @param h2Km altitude of the second station (km)
     * @param mode calculation method
     * @return gaseous attenuation of the inclined path (dB)
     * @throws IllegalArgumentException if either altitude is above 10 km
     */
    public static double gaseousAttenuationInclinedPath(double fGHz, double elDeg, double rho, double pHPa,
                                                        double tKelvin, double h1Km, double h2Km, Mode mode) {
        Check.range("elevation angle (degrees)", elDeg, -90, 90);
        Check.frequency(fGHz);
        Check.positive("total pressure (hPa)", pHPa);
        Check.nonNegative("water vapour density (g/m3)", rho);
        Check.kelvin(tKelvin);
        Check.finite("altitude of the first station (km)", h1Km);
        Check.finite("altitude of the second station (km)", h2Km);
        java.util.Objects.requireNonNull(mode, "mode");
        if (h1Km > 10 || h2Km > 10) {
            throw new IllegalArgumentException(String.format(
                "Both stations must be at an altitude of less than 10 km. Tx: %.2f km, Rx: %.2f km", h1Km, h2Km));
        }
        double g0;
        double gw;
        double rhoUsed = rho;
        if (mode == Mode.APPROX) {
            rhoUsed = rho * Math.exp(h1Km / 2);
            g0 = gamma0Exact(fGHz, pHPa, rhoUsed, tKelvin);
            gw = gammaWExact(fGHz, pHPa, rhoUsed, tKelvin);
        } else {
            g0 = gamma0Exact(fGHz, pHPa, rho, tKelvin);
            gw = 0;
        }
        Heights hh = slantInclinedPathEquivalentHeight(fGHz, pHPa, rhoUsed, tKelvin);
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
