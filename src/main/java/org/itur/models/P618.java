package org.itur.models;

import org.itur.util.Geodesy;
import org.itur.util.Special;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;

/**
 * Recommendation ITU-R P.618-13 (12/2017): propagation data and prediction methods required for the
 * design of Earth-space telecommunication systems (rain attenuation, rain cross-polarization
 * discrimination, scintillation, site diversity).
 */
public final class P618 {

    private P618() {
    }

    private static final Logger LOG = System.getLogger(P618.class.getName());
    private static final double EPSILON = 1e-9;
    private static final double RE = 8500; // effective Earth radius (km)

    /** Log-normal fit of the rain attenuation: {@code ln A ~ N(m, sigma)}. */
    public record LognormalFit(double sigmaLnA, double mLnA) {
    }

    // ---- rain attenuation ------------------------------------------------------------------

    private static double slantPathLength(double elDeg, double hr, double hs) {
        double sinEl = Math.sin(Math.toRadians(elDeg));
        if (elDeg >= 5) {
            return (hr - hs) / sinEl;
        }
        return 2 * (hr - hs) / (Math.sqrt(sinEl * sinEl + 2 * (hr - hs) / RE) + sinEl);
    }

    /** Rain attenuation exceeded for p % of an average year, without the final clipping to >= 0. */
    private static double rainAttenuationRaw(double lat, double lon, double f, double el, Double hsKm, double p,
                                             Double r001, double tau, Double lsKm) {
        if (p < 0.001 || p > 5) {
            LOG.log(Level.WARNING, "P.618-13 rain attenuation is only valid for 0.001% <= p <= 5%");
        }
        double hs = hsKm != null ? hsKm : P1511.topographicAltitude(lat, lon);
        double hr = P839.rainHeight(lat, lon);
        double ls = lsKm != null ? lsKm : slantPathLength(el, hr, hs);
        double lg = Math.abs(ls * Math.cos(Math.toRadians(el)));
        double rain = r001 != null ? r001 : P837.rainfallRate(lat, lon, 0.01) + EPSILON;
        double gammaR = P838.rainSpecificAttenuation(rain, f, el, tau);

        double rr001 = 1.0 / (1 + 0.78 * Math.sqrt(lg * gammaR / f) - 0.38 * (1 - Math.exp(-2 * lg)));
        double eta = Math.toDegrees(Math.atan2(hr - hs, lg * rr001));
        double deltaH = (hr - hs <= 0) ? EPSILON : (hr - hs);
        double lr = eta > el ? lg * rr001 / Math.cos(Math.toRadians(el)) : deltaH / Math.sin(Math.toRadians(el));
        double absLat = Math.abs(lat);
        double xi = absLat < 36 ? 36 - absLat : 0;
        double sinEl = Math.sin(Math.toRadians(el));
        double v001 = 1.0 / (1 + Math.sqrt(sinEl)
            * (31 * (1 - Math.exp(-(el / (1 + xi)))) * Math.sqrt(lr * gammaR) / (f * f) - 0.45));
        double le = lr * v001;
        double a001 = gammaR * le;

        double beta;
        if (p >= 1) {
            beta = 0;
        } else if (absLat >= 36) {
            beta = 0;
        } else if (absLat < 36 && el > 25) {
            beta = -0.005 * (absLat - 36);
        } else {
            beta = -0.005 * (absLat - 36) + 1.8 - 4.25 * sinEl;
        }
        return a001 * Math.pow(p / 0.01,
            -(0.655 + 0.033 * Math.log(p) - 0.045 * Math.log(a001) - beta * (1 - p) * sinEl));
    }

    /**
     * Rain attenuation (dB) exceeded for {@code p} % of an average year (Section 2.2.1.1).
     *
     * @param hsKm station altitude above mean sea level (km), or {@code null} to use P.1511
     * @param p percentage of time (0.001 to 5)
     * @param r001 rainfall rate exceeded for 0.01 % (mm/h), or {@code null} to use P.837
     * @param tauDeg polarization tilt angle (45 for circular)
     * @param lsKm slant path length (km), or {@code null} to compute it from the rain height
     */
    public static double rainAttenuation(double latDeg, double lonDeg, double fGHz, double elDeg, Double hsKm,
                                         double p, Double r001, double tauDeg, Double lsKm) {
        double lon = Special.mod(lonDeg, 360);
        double a = rainAttenuationRaw(latDeg, lon, fGHz, elDeg, hsKm, p, r001, tauDeg, lsKm);
        return a < 0 ? 0 : a;
    }

    /** {@link #rainAttenuation} with default arguments ({@code p = 0.01 %}, circular polarization). */
    public static double rainAttenuation(double latDeg, double lonDeg, double fGHz, double elDeg) {
        return rainAttenuation(latDeg, lonDeg, fGHz, elDeg, null, 0.01, null, 45, null);
    }

    /**
     * Probability of rain attenuation (%) on the slant path (Section 2.2.1.2).
     *
     * @param p0Percent probability of rain at the station (%), or {@code null} to use P.837
     */
    public static double rainAttenuationProbability(double latDeg, double lonDeg, double elDeg, Double hsKm,
                                                    Double lsKm, Double p0Percent) {
        double lon = Special.mod(lonDeg, 360);
        double hs = hsKm != null ? hsKm : P1511.topographicAltitude(latDeg, lon);
        double p0 = (p0Percent != null ? p0Percent : P837.rainfallProbability(latDeg, lon)) / 100.0;
        double alpha = Special.normPpf(1 - p0);
        double hr = P839.rainHeight(latDeg, lon);
        double ls = lsKm != null ? lsKm : slantPathLength(elDeg, hr, hs);
        double d = ls * Math.cos(Math.toRadians(elDeg));
        double rho = 0.59 * Math.exp(-Math.abs(d) / 31) + 0.41 * Math.exp(-Math.abs(d) / 800);
        double cB = Special.bivariateNormalSf(alpha, alpha, rho);
        double p = 1 - (1 - p0) * Math.pow((cB - p0 * p0) / (p0 * (1 - p0)), p0);
        return p * 100;
    }

    /**
     * Fits {@code ln A_rain} to a log-normal distribution (Section 2.2.1.3 / Annex), by least
     * squares over the standard exceedance probabilities lower than {@code pkPercent}.
     */
    public static LognormalFit fitRainAttenuationToLognormal(double latDeg, double lonDeg, double fGHz,
                                                              double elDeg, Double hsKm, double pkPercent,
                                                              double tauDeg) {
        double lon = Special.mod(lonDeg, 360);
        double[] pi = {0.01, 0.02, 0.03, 0.05, 0.1, 0.2, 0.3, 0.5, 1, 2, 3, 5, 10};
        int n = 0;
        for (double p : pi) {
            if (p < pkPercent) {
                n++;
            }
        }
        if (n < 2) {
            throw new IllegalArgumentException("At least two exceedance probabilities below P_k are required");
        }
        double[] q = new double[n];
        double[] lnA = new double[n];
        int k = 0;
        for (double p : pi) {
            if (p < pkPercent) {
                double a = rainAttenuationRaw(latDeg, lon, fGHz, elDeg, hsKm, p, null, tauDeg, null);
                q[k] = Special.normPpf(1 - p / pkPercent);
                lnA[k] = Math.log(a);
                k++;
            }
        }
        // ordinary least squares  lnA = m + sigma * Q  (centred form, numerically stable)
        double qm = 0;
        double ym = 0;
        for (int i = 0; i < n; i++) {
            qm += q[i];
            ym += lnA[i];
        }
        qm /= n;
        ym /= n;
        double sxy = 0;
        double sxx = 0;
        for (int i = 0; i < n; i++) {
            sxy += (q[i] - qm) * (lnA[i] - ym);
            sxx += (q[i] - qm) * (q[i] - qm);
        }
        double sigma = sxy / sxx;
        double m = ym - sigma * qm;
        return new LognormalFit(sigma, m);
    }

    /**
     * Probability (%) that the rain attenuation exceeds both margins at two sites simultaneously
     * (site diversity, Section 2.2.4).
     */
    public static double siteDiversityRainOutageProbability(double lat1, double lon1, double a1Db, double el1,
                                                            double lat2, double lon2, double a2Db, double el2,
                                                            double fGHz, double tauDeg, Double hs1, Double hs2) {
        double l1 = Special.mod(lon1, 360);
        double l2 = Special.mod(lon2, 360);
        double d = Geodesy.distanceKm(lat1, l1, lat2, l2);
        double rhoR = 0.7 * Math.exp(-d / 60) + 0.3 * Math.exp(-Math.pow(d / 700, 2));
        double p1 = P837.rainfallProbability(lat1, l1) / 100.0;
        double p2 = P837.rainfallProbability(lat2, l2) / 100.0;
        double r1 = Special.normPpf(1 - p1);
        double r2 = Special.normPpf(1 - p2);
        double pr = Special.bivariateNormalSf(r1, r2, rhoR);
        LognormalFit fit1 = fitRainAttenuationToLognormal(lat1, l1, fGHz, el1, hs1, p1 * 100, tauDeg);
        LognormalFit fit2 = fitRainAttenuationToLognormal(lat2, l2, fGHz, el2, hs2, p2 * 100, tauDeg);
        double rhoA = 0.94 * Math.exp(-d / 30) + 0.06 * Math.exp(-Math.pow(d / 500, 2));
        double lim1 = (Math.log(a1Db) - fit1.mLnA()) / fit1.sigmaLnA();
        double lim2 = (Math.log(a2Db) - fit2.mLnA()) / fit2.sigmaLnA();
        double pa = Special.bivariateNormalSf(lim1, lim2, rhoA);
        return 100 * pr * pa;
    }

    // ---- cross-polarization ------------------------------------------------------------------

    /**
     * Cross-polarization discrimination XPD_p (dB) not exceeded for p % of the time, from rain
     * attenuation {@code apDb} exceeded for the same percentage (Section 4.1).
     *
     * @param fGHz frequency (GHz), 4 to 55
     * @param elDeg elevation angle (degrees)
     * @param p percentage of time
     * @param tauDeg polarization tilt angle (degrees)
     */
    public static double rainCrossPolarizationDiscrimination(double apDb, double fGHz, double elDeg, double p,
                                                             double tauDeg) {
        if (fGHz < 4 || fGHz > 55) {
            throw new IllegalArgumentException("The cross-polarization method is valid for 4 GHz <= f <= 55 GHz");
        }
        if (elDeg > 60) {
            LOG.log(Level.WARNING, "P.618-13 XPD is only valid for elevation angles below 60 degrees");
        }
        double f = fGHz;
        boolean scale = false;
        double fOrig = f;
        if (4 <= f && f < 6) {
            f = 6;
            scale = true;
        }
        double cf;
        if (6 <= f && f < 9) {
            cf = 60 * Math.log10(f) - 28.3;
        } else if (9 <= f && f < 36) {
            cf = 26 * Math.log10(f) + 4.1;
        } else {
            cf = 35.9 * Math.log10(f) - 11.3;
        }
        double v;
        if (6 <= f && f < 9) {
            v = 30.8 * Math.pow(f, -0.21);
        } else if (9 <= f && f < 20) {
            v = 12.8 * Math.pow(f, 0.19);
        } else if (20 <= f && f < 40) {
            v = 22.6;
        } else {
            v = 13.0 * Math.pow(f, 0.15);
        }
        double ca = v * Math.log10(apDb);
        double cTau = -10 * Math.log10(1 - 0.484 * (1 + Math.cos(Math.toRadians(4 * tauDeg))));
        double cTheta = -40 * Math.log10(Math.cos(Math.toRadians(elDeg)));
        double cSigma;
        if (p <= 0.001) {
            cSigma = 0.0053 * 15 * 15;
        } else if (p <= 0.01) {
            cSigma = 0.0053 * 10 * 10;
        } else if (p <= 0.1) {
            cSigma = 0.0053 * 5 * 5;
        } else {
            cSigma = 0;
        }
        double xpdRain = cf - ca + cTau + cTheta + cSigma;
        double cIce = xpdRain * (0.3 + 0.1 * Math.log10(p)) / 2;
        double xpd = xpdRain - cIce;
        if (scale) {
            double s = Math.sqrt(1 - 0.484 * (1 + Math.cos(Math.toRadians(4 * tauDeg))));
            xpd = xpd - 20 * Math.log10(fOrig * s / (f * s));
        }
        return xpd;
    }

    // ---- scintillation -----------------------------------------------------------------------

    /**
     * Standard deviation of the signal amplitude (dB) due to tropospheric scintillation
     * (Section 2.4.1).
     *
     * @param dMeters antenna diameter (m)
     * @param eta antenna efficiency (0.5 is a good default)
     * @param tCelsius average surface temperature (deg C), or {@code null}
     * @param hPercent average surface relative humidity (%), or {@code null}
     * @param pHPa average surface pressure (hPa), or {@code null}; when any of these three is
     *     {@code null} the wet refractivity is taken from the P.453 map
     * @param hLMeters height of the turbulent layer (m), 1000 by default
     */
    public static double scintillationAttenuationSigma(double latDeg, double lonDeg, double fGHz, double elDeg,
                                                       double dMeters, double eta, Double tCelsius, Double hPercent,
                                                       Double pHPa, double hLMeters) {
        double nWet;
        if (tCelsius != null && hPercent != null && pHPa != null) {
            double e = P453.waterVapourPressure(tCelsius, pHPa, hPercent);
            nWet = P453.wetTermRadioRefractivity(e, tCelsius);
        } else {
            nWet = P453.mapWetTermRadioRefractivity(latDeg, Special.mod(lonDeg, 360), 50);
        }
        double sigmaRef = 0.0036 + 0.0001 * nWet;
        double sinEl = Math.sin(Math.toRadians(elDeg));
        double len = 2 * hLMeters / (Math.sqrt(sinEl * sinEl + 0.000235) + sinEl);
        double dEff = Math.sqrt(eta) * dMeters;
        double x = 1.22 * dEff * dEff * fGHz / len;
        double g = x >= 7.0 ? 0
            : Math.sqrt(3.86 * Math.pow(x * x + 1, 11.0 / 12) * Math.sin(11.0 / 6 * Math.atan2(1, x))
                - 7.08 * Math.pow(x, 5.0 / 6));
        return sigmaRef * Math.pow(fGHz, 7.0 / 12) * g / Math.pow(sinEl, 1.2);
    }

    /**
     * Tropospheric scintillation fade depth (dB) exceeded for {@code p} % of the time (Section 2.4.1);
     * negative results are clipped to 0. See {@link #scintillationAttenuationSigma} for the arguments.
     */
    public static double scintillationAttenuation(double latDeg, double lonDeg, double fGHz, double elDeg, double p,
                                                  double dMeters, double eta, Double tCelsius, Double hPercent,
                                                  Double pHPa, double hLMeters) {
        double sigma = scintillationAttenuationSigma(latDeg, lonDeg, fGHz, elDeg, dMeters, eta, tCelsius, hPercent,
            pHPa, hLMeters);
        double lp = Math.log10(p);
        double a = -0.061 * lp * lp * lp + 0.072 * lp * lp - 1.71 * lp + 3;
        double as = a * sigma;
        return as < 0 ? 0 : as;
    }
}
