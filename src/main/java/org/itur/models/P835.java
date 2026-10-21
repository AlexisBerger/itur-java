package org.itur.models;

/**
 * Recommendation ITU-R P.835-6 (12/2017), reference standard atmosphere (Section 1) and the exponential
 * water-vapour profile. It is a dependency of the exact slant-path gaseous attenuation of P.676 and of the
 * default surface pressure used by the combined attenuation method.
 */
public final class P835 {

    public static final String RECOMMENDATION = "ITU-R P.835-6";

    private P835() {
    }

    /** Geopotential height (km) from the geometric height h (km). */
    private static double geopotential(double h) {
        return 6356.766 * h / (6356.766 + h);
    }

    /** Reference standard atmosphere temperature in kelvin at height h (km). */
    public static double standardTemperature(double h) {
        double hp = geopotential(h);
        if (hp <= 11) {
            return 288.15 - 6.5 * hp;
        } else if (hp <= 20) {
            return 216.65;
        } else if (hp <= 32) {
            return 216.65 + (hp - 20);
        } else if (hp <= 47) {
            return 228.65 + 2.8 * (hp - 32);
        } else if (hp <= 51) {
            return 270.65;
        } else if (hp <= 71) {
            return 270.65 - 2.8 * (hp - 51);
        } else if (hp <= 84.852) {
            return 214.65 - 2.0 * (hp - 71);
        } else if (86 <= h && h <= 91) {
            return 186.8673;
        } else if (91 < h && h <= 100) {
            double t = (h - 91) / 19.9429;
            return 263.1905 - 76.3232 * Math.sqrt(1 - t * t);
        }
        return 195.08134;
    }

    /** Reference standard atmosphere dry pressure in hPa at height h (km). */
    public static double standardPressure(double h) {
        double hp = geopotential(h);
        if (hp <= 11) {
            return 1013.25 * Math.pow(288.15 / (288.15 - 6.5 * hp), -34.1632 / 6.5);
        } else if (hp <= 20) {
            return 226.3226 * Math.exp(-34.1632 * (hp - 11) / 216.65);
        } else if (hp <= 32) {
            return 54.74980 * Math.pow(216.65 / (216.65 + (hp - 20)), 34.1632);
        } else if (hp <= 47) {
            return 8.680422 * Math.pow(228.65 / (228.65 + 2.8 * (hp - 32)), 34.1632 / 2.8);
        } else if (hp <= 51) {
            return 1.109106 * Math.exp(-34.1632 * (hp - 47) / 270.65);
        } else if (hp <= 71) {
            return 0.6694167 * Math.pow(270.65 / (270.65 - 2.8 * (hp - 51)), -34.1632 / 2.8);
        } else if (hp <= 84.852) {
            return 0.03956649 * Math.pow(214.65 / (214.65 - 2.0 * (hp - 71)), -34.1632 / 2.0);
        } else if (86 <= h && h <= 100) {
            return Math.exp(95.571899 - 4.011801 * h + 6.424731e-2 * h * h - 4.789660e-4 * h * h * h
                    + 1.340543e-6 * h * h * h * h);
        }
        return 1e-62;
    }

    /** Water vapour density in g/m3 at height h (km), with scale height {@code h0} (km) and ground value rho0. */
    public static double standardWaterVapourDensity(double h, double h0, double rho0) {
        return rho0 * Math.exp(-h / h0);
    }

    /** Water vapour density with the default scale height of 2 km and 7.5 g/m3 at sea level. */
    public static double standardWaterVapourDensity(double h) {
        return standardWaterVapourDensity(h, 2.0, 7.5);
    }

    /** Water vapour partial pressure in hPa at height h (km). */
    public static double standardWaterVapourPressure(double h, double h0, double rho0) {
        return standardWaterVapourDensity(h, h0, rho0) * standardTemperature(h) / 216.7;
    }
}
