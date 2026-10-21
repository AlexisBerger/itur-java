package org.itur;

/**
 * Recommendation ITU-R P.835-6 (12/2017), Annex 1: reference standard atmosphere
 * (temperature, pressure and water-vapour profiles as a function of geometric height).
 *
 * <p>Only the global reference atmosphere is provided; it is the part needed by P.676.
 */
public final class P835 {

    private P835() {
    }

    private static double geopotential(double hKm) {
        return 6356.766 * hKm / (6356.766 + hKm);
    }

    /** Standard temperature (K) at geometric height {@code hKm} (km). */
    public static double standardTemperature(double hKm) {
        double hp = geopotential(hKm);
        if (hp <= 11) {
            return 288.15 - 6.5 * hp;
        } else if (11 < hp && hp <= 20) {
            return 216.65;
        } else if (20 < hp && hp <= 32) {
            return 216.65 + (hp - 20);
        } else if (32 < hp && hp <= 47) {
            return 228.65 + 2.8 * (hp - 32);
        } else if (47 < hp && hp <= 51) {
            return 270.65;
        } else if (51 < hp && hp <= 71) {
            return 270.65 - 2.8 * (hp - 51);
        } else if (71 < hp && hp <= 84.852) {
            return 214.65 - 2.0 * (hp - 71);
        } else if (86 <= hKm && hKm <= 91) {
            return 186.8673;
        } else if (91 < hKm && hKm <= 100) {
            double z = (hKm - 91) / 19.9429;
            return 263.1905 - 76.3232 * Math.sqrt(1 - z * z);
        }
        return 195.08134;
    }

    /** Standard total pressure (hPa) at geometric height {@code hKm} (km). */
    public static double standardPressure(double hKm) {
        double hp = geopotential(hKm);
        if (hp <= 11) {
            return 1013.25 * Math.pow(288.15 / (288.15 - 6.5 * hp), -34.1632 / 6.5);
        } else if (11 < hp && hp <= 20) {
            return 226.3226 * Math.exp(-34.1632 * (hp - 11) / 216.65);
        } else if (20 < hp && hp <= 32) {
            return 54.7498 * Math.pow(216.65 / (216.65 + (hp - 20)), 34.1632);
        } else if (32 < hp && hp <= 47) {
            return 8.680422 * Math.pow(228.65 / (228.65 + 2.8 * (hp - 32)), 34.1632 / 2.8);
        } else if (47 < hp && hp <= 51) {
            return 1.109106 * Math.exp(-34.1632 * (hp - 47) / 270.65);
        } else if (51 < hp && hp <= 71) {
            return 0.6694167 * Math.pow(270.65 / (270.65 - 2.8 * (hp - 51)), -34.1632 / 2.8);
        } else if (71 < hp && hp <= 84.852) {
            return 0.03956649 * Math.pow(214.65 / (214.65 - 2.0 * (hp - 71)), -34.1632 / 2.0);
        } else if (86 <= hKm && hKm <= 100) {
            double h = hKm;
            return Math.exp(95.571899 - 4.011801 * h + 0.06424731 * h * h - 0.000478966 * h * h * h
                + 1.340543e-06 * h * h * h * h);
        }
        return 1e-62;
    }

    /** Standard water vapour density (g/m3) with scale height 2 km and ground value 7.5 g/m3. */
    public static double standardWaterVapourDensity(double hKm) {
        return standardWaterVapourDensity(hKm, 2.0, 7.5);
    }

    /**
     * Standard water vapour density (g/m3): {@code rho0 * exp(-h / h0)}.
     *
     * @param hKm height (km)
     * @param h0Km scale height (km)
     * @param rho0 density at sea level (g/m3)
     */
    public static double standardWaterVapourDensity(double hKm, double h0Km, double rho0) {
        return rho0 * Math.exp(-hKm / h0Km);
    }

    /** Standard water vapour partial pressure (hPa). */
    public static double standardWaterVapourPressure(double hKm, double h0Km, double rho0) {
        double rho = standardWaterVapourDensity(hKm, h0Km, rho0);
        return rho * standardTemperature(hKm) / 216.7;
    }
}
