package org.itur;

/**
 * Recommendation ITU-R P.453-13 (12/2017): the radio refractive index, its formula and
 * refractivity data. Includes the wet-term map (0.75 deg grid, 18 probabilities) and the DN65 / DN1
 * refractivity-gradient maps (0.75 deg grid, 21 probabilities).
 */
public final class P453 {

    private P453() {
    }

    private static final double[] P_WET = {0.1, 0.2, 0.3, 0.5, 1, 2, 3, 5, 10, 20, 30, 50, 60, 70, 80, 90, 95, 99};
    private static final String[] SUFFIX_WET = {"01", "02", "03", "05", "1", "2", "3", "5", "10", "20", "30", "50",
        "60", "70", "80", "90", "95", "99"};
    private static final double[] P_DN = {0.1, 0.2, 0.5, 1, 2, 5, 10, 20, 30, 40, 50, 60, 70, 80, 90, 95, 98, 99, 99.5,
        99.8, 99.9};

    /** Type of hydrometeor for the saturation vapour pressure. */
    public enum Hydrometeor {
        WATER, ICE
    }

    // ---- formulas ----------------------------------------------------------------------------

    /**
     * Wet term of the radio refractivity (N-units), Eq. 6: {@code 72 e / T + 3.75e5 e / T^2}
     * with the temperature expressed in deg C (converted to kelvin internally, as in the
     * ITU-Rpy implementation used by P.618 and P.676).
     *
     * @param eHPa water vapour partial pressure (hPa)
     * @param tCelsius temperature (deg C)
     */
    public static double wetTermRadioRefractivity(double eHPa, double tCelsius) {
        double tk = tCelsius + 273.15;
        return 72 * eHPa / tk + 3.75e5 * eHPa / (tk * tk);
    }

    /**
     * Dry term of the radio refractivity (N-units), Eq. 3.
     *
     * @param pdHPa dry air pressure (hPa)
     * @param tKelvin temperature (K)
     */
    public static double dryTermRadioRefractivity(double pdHPa, double tKelvin) {
        return 77.6 * pdHPa / tKelvin;
    }

    /**
     * Radio refractive index n, Eqs. 1-2.
     *
     * @param pdHPa dry air pressure (hPa)
     * @param eHPa water vapour partial pressure (hPa)
     * @param tKelvin temperature (K)
     */
    public static double radioRefractiveIndex(double pdHPa, double eHPa, double tKelvin) {
        double n = 77.6 * pdHPa / tKelvin + 72 * eHPa / tKelvin + 3.75e5 * eHPa / (tKelvin * tKelvin);
        return 1 + n * 1e-6;
    }

    /**
     * Saturation vapour pressure (hPa), Eqs. 9-10 (with the enhancement factor).
     *
     * @param tCelsius temperature (deg C)
     * @param pHPa total pressure (hPa)
     */
    public static double saturationVapourPressure(double tCelsius, double pHPa, Hydrometeor type) {
        double t = tCelsius;
        double ef;
        double a;
        double b;
        double c;
        double d;
        if (type == Hydrometeor.WATER) {
            ef = 1 + 1e-4 * (7.2 + pHPa * (0.0320 + 5.9e-6 * t * t));
            a = 6.1121;
            b = 18.678;
            c = 257.14;
            d = 234.5;
        } else {
            ef = 1 + 1e-4 * (2.2 + pHPa * (0.0383 + 6.4e-6 * t * t));
            a = 6.1115;
            b = 23.036;
            c = 279.82;
            d = 333.7;
        }
        return ef * a * Math.exp((b - t / d) * t / (t + c));
    }

    /**
     * Water vapour pressure (hPa), Eq. 8: {@code H * e_s / 100}.
     *
     * @param tCelsius temperature (deg C)
     * @param pHPa total pressure (hPa)
     * @param hPercent relative humidity (%)
     */
    public static double waterVapourPressure(double tCelsius, double pHPa, double hPercent, Hydrometeor type) {
        return hPercent * saturationVapourPressure(tCelsius, pHPa, type) / 100;
    }

    /** Water vapour pressure over water (hPa). */
    public static double waterVapourPressure(double tCelsius, double pHPa, double hPercent) {
        return waterVapourPressure(tCelsius, pHPa, hPercent, Hydrometeor.WATER);
    }

    // ---- maps --------------------------------------------------------------------------------

    private static int indexOf(double[] list, double p) {
        for (int i = 0; i < list.length; i++) {
            if (list[i] == p) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Wet term of the surface radio refractivity (N-units) exceeded for {@code p} % of an average
     * year, from the 0.75 deg map (log-interpolated between the tabulated probabilities).
     */
    public static double mapWetTermRadioRefractivity(double latDeg, double lonDeg, double p) {
        double lon = Special.mod(lonDeg, 360);
        if (lon > 180) {
            lon -= 360;
        }
        int exact = indexOf(P_WET, p);
        int below;
        int above;
        if (exact >= 0) {
            below = above = exact;
        } else {
            int idx = P836.searchSortedRight(P_WET, p) - 1;
            idx = Math.max(0, Math.min(P_WET.length - 1, idx));
            below = idx;
            above = Math.max(0, Math.min(P_WET.length - 1, idx + 1));
        }
        final double step = 0.75;
        double R = Special.floorDiv(-(latDeg - 90), step);
        double C = Special.floorDiv(lon + 180, step);
        double[] lats = {90 - R * step, 90 - (R + 1) * step, 90 - R * step, 90 - (R + 1) * step};
        double[] lons = {C * step - 180, C * step - 180, (C + 1) * step - 180, (C + 1) * step - 180};
        double r = -(latDeg - 90) / step;
        double c = (lon + 180) / step;

        double a = cornerMix(SUFFIX_WET[above], lats, lons, R, C, r, c);
        if (exact >= 0) {
            return a;
        }
        double b = cornerMix(SUFFIX_WET[below], lats, lons, R, C, r, c);
        return b + (a - b) * (Math.log(p) - Math.log(P_WET[below])) / (Math.log(P_WET[above]) - Math.log(P_WET[below]));
    }

    private static double cornerMix(String suffix, double[] lats, double[] lons, double R, double C, double r,
                                    double c) {
        Grid g = Grid.load("453/v13_nwet_annual_" + suffix);
        // ITU-Rpy maps corner longitudes > 180 back to [-180, 180] before interpolating
        double n0 = g.bilinear(lats[0], wrap180(lons[0]));
        double n1 = g.bilinear(lats[1], wrap180(lons[1]));
        double n2 = g.bilinear(lats[2], wrap180(lons[2]));
        double n3 = g.bilinear(lats[3], wrap180(lons[3]));
        return n0 * ((R + 1 - r) * (C + 1 - c)) + n1 * ((r - R) * (C + 1 - c)) + n2 * ((R + 1 - r) * (c - C))
            + n3 * ((r - R) * (c - C));
    }

    private static double wrap180(double lon) {
        return lon > 180 ? lon - 360 : lon;
    }

    private static double dnMap(String stem, double latDeg, double lonDeg, double p) {
        int idx = indexOf(P_DN, p);
        if (idx < 0) {
            throw new IllegalArgumentException("p = " + p + " is not one of the tabulated probabilities");
        }
        int ip = (int) Math.floor(p);
        int fp = (int) Math.round((p % 1.0) * 100);
        String name = String.format("453/v12_%s_%02dd%02d_v1", stem, ip, fp);
        return Grid.load(name).bilinear(latDeg, Special.mod(lonDeg, 360));
    }

    /**
     * Vertical gradient of radio refractivity in the lowest 65 m (N-units/km) exceeded for
     * {@code p} % of an average year.
     *
     * @param p one of 0.1, 0.2, 0.5, 1, 2, 5, 10, 20, 30, 40, 50, 60, 70, 80, 90, 95, 98, 99, 99.5, 99.8, 99.9
     */
    public static double dn65(double latDeg, double lonDeg, double p) {
        return dnMap("dn65m", latDeg, lonDeg, p);
    }

    /**
     * Vertical gradient of radio refractivity over the first km of the atmosphere (N-units/km)
     * exceeded for {@code p} % of an average year (same probability set as {@link #dn65}).
     */
    public static double dn1(double latDeg, double lonDeg, double p) {
        return dnMap("dn", latDeg, lonDeg, p);
    }
}
