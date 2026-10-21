package org.itur.models;

import java.util.logging.Logger;

import org.itur.util.Geodesy;
import org.itur.util.Maths;

/**
 * Recommendation ITU-R P.618-13 (12/2017): propagation data and prediction methods required for the design of
 * Earth-space telecommunication systems.
 *
 * <p>Units: latitude / longitude in degrees, frequency in GHz, elevation angle and polarisation tilt in degrees,
 * heights in km, percentages of time in %, attenuations in dB. Arguments that are optional in the Recommendation
 * (and are then taken from the P.1511, P.837, P.839 ... maps) are passed as {@code null}.
 */
public final class P618 {

    public static final String RECOMMENDATION = "ITU-R P.618-13";

    private static final Logger LOG = Logger.getLogger(P618.class.getName());
    private static final double RE = 8500.0; // effective Earth radius (km)

    private P618() {
    }

    // ------------------------------------------------------------------ rain attenuation

    /** Slant path length below the rain height, Eq. 1-2. */
    private static double slantPathLength(double el, double hr, double hs) {
        double sinEl = Math.sin(Math.toRadians(el));
        if (el >= 5) {
            return (hr - hs) / sinEl; // Eq. 1
        }
        return 2 * (hr - hs) / (Math.pow(sinEl * sinEl + 2 * (hr - hs) / RE, 0.5) + sinEl); // Eq. 2
    }

    /**
     * Rain attenuation exceeded for p% of an average year, without clipping negative values (steps 1-10).
     */
    private static double rainAttenuationRaw(double lat, double lon, double f, double el, Double hsIn, double p,
            Double r001In, double tau, Double lsIn) {
        if (p < 0.001 || p > 5) {
            LOG.warning("The rain attenuation method of ITU-R P.618-13 is only valid for 0.001% <= p <= 5%");
        }
        double lonN = Maths.lon360(lon);
        double hs = hsIn != null ? hsIn : P1511.topographicAltitude(lat, lonN);
        double hr = P839.rainHeight(lat, lonN); // step 1
        double ls = lsIn != null ? lsIn : slantPathLength(el, hr, hs); // step 2
        double lg = Math.abs(ls * Math.cos(Math.toRadians(el))); // step 3
        double r001 = r001In != null ? r001In : P837.rainfallRate(lat, lonN, 0.01) + Maths.EPSILON; // step 4
        double gammaR = P838.specificAttenuation(r001, f, el, tau); // step 5

        double rh = 1.0 / (1 + 0.78 * Math.sqrt(lg * gammaR / f) - 0.38 * (1 - Math.exp(-2 * lg))); // step 6
        double eta = Math.toDegrees(Math.atan2(hr - hs, lg * rh)); // step 7
        double deltaH = (hr - hs <= 0) ? Maths.EPSILON : (hr - hs);
        double sinEl = Math.sin(Math.toRadians(el));
        double lr = eta > el ? lg * rh / Math.cos(Math.toRadians(el)) : deltaH / sinEl;
        double xi = Math.abs(lat) < 36 ? 36 - Math.abs(lat) : 0.0;
        double v001 = 1.0 / (1 + Math.sqrt(sinEl)
                * (31 * (1 - Math.exp(-(el / (1 + xi)))) * Math.sqrt(lr * gammaR) / (f * f) - 0.45));

        double le = lr * v001; // step 8
        double a001 = gammaR * le; // step 9

        double beta; // step 10
        if (p >= 1) {
            beta = 0.0;
        } else if (Math.abs(lat) >= 36) {
            beta = 0.0;
        } else if (el > 25) {
            beta = -0.005 * (Math.abs(lat) - 36);
        } else {
            beta = -0.005 * (Math.abs(lat) - 36) + 1.8 - 4.25 * sinEl;
        }
        return a001 * Math.pow(p / 0.01,
                -(0.655 + 0.033 * Math.log(p) - 0.045 * Math.log(a001) - beta * (1 - p) * sinEl));
    }

    /**
     * Rain attenuation (dB) exceeded for p% of an average year (never negative).
     *
     * @param hs   altitude of the earth station above mean sea level (km), or {@code null} for the P.1511 value
     * @param p    percentage of an average year (0.001 to 5)
     * @param r001 rainfall rate exceeded for 0.01% of the year (mm/h), or {@code null} for the P.837 value
     * @param tau  polarisation tilt angle (degrees; 45 for circular polarisation)
     * @param ls   slant path length (km), or {@code null} to compute it from the rain height
     */
    public static double rainAttenuation(double lat, double lon, double f, double el, Double hs, double p,
            Double r001, double tau, Double ls) {
        double a = rainAttenuationRaw(lat, lon, f, el, hs, p, r001, tau, ls);
        return a < 0 ? 0.0 : a;
    }

    /** Rain attenuation with all optional inputs taken from the maps and circular polarisation. */
    public static double rainAttenuation(double lat, double lon, double f, double el, double p) {
        return rainAttenuation(lat, lon, f, el, null, p, null, 45.0, null);
    }

    /**
     * Probability of rain attenuation on the slant path, in percent (Section 2.2.1.1).
     *
     * @param p0 probability of rain at the station as a fraction (0 to 1), or {@code null} for the P.837 value
     */
    public static double rainAttenuationProbability(double lat, double lon, double el, Double hsIn, Double lsIn,
            Double p0In) {
        double lonN = Maths.lon360(lon);
        double hs = hsIn != null ? hsIn : P1511.topographicAltitude(lat, lonN);
        double p0 = p0In != null ? p0In : P837.rainfallProbability(lat, lonN) / 100.0;
        double alpha = Maths.normPpf(1 - p0);
        double hr = P839.rainHeight(lat, lonN);
        double ls = lsIn != null ? lsIn : slantPathLength(el, hr, hs);
        double d = ls * Math.cos(Math.toRadians(el));
        double rho = 0.59 * Math.exp(-Math.abs(d) / 31) + 0.41 * Math.exp(-Math.abs(d) / 800);
        double cB = Maths.bivariateNormalUpper(alpha, alpha, rho);
        double p = 1 - (1 - p0) * Math.pow((cB - p0 * p0) / (p0 * (1 - p0)), p0);
        return p * 100.0;
    }

    /** Parameters of the log-normal fit of rain attenuation: {@code ln A = sigma * Q^-1(P/Pk) + m}. */
    public record LognormalFit(double sigma, double m) {
    }

    /**
     * Log-normal fit of the rain attenuation statistics of a path (Annex of the site-diversity method).
     *
     * @param pk probability of rain (%), the log-normal distribution applies for P &lt; pk
     */
    public static LognormalFit fitRainAttenuationToLognormal(double lat, double lon, double f, double el,
            Double hs, double pk, double tau) {
        double[] pAll = {0.01, 0.02, 0.03, 0.05, 0.1, 0.2, 0.3, 0.5, 1, 2, 3, 5, 10};
        int n = 0;
        for (double v : pAll) {
            if (v < pk) {
                n++;
            }
        }
        double[] q = new double[n];
        double[] lnA = new double[n];
        int k = 0;
        for (double v : pAll) {
            if (v < pk) {
                double a = rainAttenuationRaw(lat, lon, f, el, hs, v, null, tau, null);
                q[k] = Maths.normPpf(1 - (v / pk));
                lnA[k] = Math.log(a);
                k++;
            }
        }
        // Least squares of lnA = m + sigma * Q
        double sq = 0;
        double sy = 0;
        for (int i = 0; i < n; i++) {
            sq += q[i];
            sy += lnA[i];
        }
        double qm = sq / n;
        double ym = sy / n;
        double sxx = 0;
        double sxy = 0;
        for (int i = 0; i < n; i++) {
            sxx += (q[i] - qm) * (q[i] - qm);
            sxy += (q[i] - qm) * (lnA[i] - ym);
        }
        double sigma = sxy / sxx;
        double m = ym - sigma * qm;
        return new LognormalFit(sigma, m);
    }

    /**
     * Joint probability (%) that the attenuation on the path to site 1 exceeds {@code a1} dB and on the path to
     * site 2 exceeds {@code a2} dB (site-diversity method).
     */
    public static double siteDiversityRainOutageProbability(double lat1, double lon1, double a1, double el1,
            double lat2, double lon2, double a2, double el2, double f, double tau, Double hs1, Double hs2) {
        double l1 = Maths.lon360(lon1);
        double l2 = Maths.lon360(lon2);
        double d = Geodesy.distanceKm(lat1, l1, lat2, l2);
        double rhoR = 0.7 * Math.exp(-d / 60) + 0.3 * Math.exp(-Math.pow(d / 700, 2));

        double p1 = P837.rainfallProbability(lat1, l1) / 100.0;
        double p2 = P837.rainfallProbability(lat2, l2) / 100.0;
        double r1 = Maths.normPpf(1 - p1);
        double r2 = Maths.normPpf(1 - p2);
        double pr = Maths.bivariateNormalUpper(r1, r2, rhoR);

        LognormalFit fit1 = fitRainAttenuationToLognormal(lat1, l1, f, el1, hs1, p1 * 100, tau);
        LognormalFit fit2 = fitRainAttenuationToLognormal(lat2, l2, f, el2, hs2, p2 * 100, tau);

        double rhoA = 0.94 * Math.exp(-d / 30) + 0.06 * Math.exp(-Math.pow(d / 500, 2));
        double lim1 = (Math.log(a1) - fit1.m()) / fit1.sigma();
        double lim2 = (Math.log(a2) - fit2.m()) / fit2.sigma();
        double pa = Maths.bivariateNormalUpper(lim1, lim2, rhoA);
        return 100 * pr * pa;
    }

    // ------------------------------------------------------------------ cross-polarisation

    /**
     * Cross-polarisation discrimination (dB) not exceeded for p% of the time (Section 4.1), valid for
     * 4 &lt;= f &lt;= 55 GHz and elevation angles below 60 degrees.
     *
     * @param ap rain attenuation exceeded for p% of the time (dB)
     */
    public static double rainCrossPolarizationDiscrimination(double ap, double f, double el, double p, double tau) {
        if (f < 4 || f > 55) {
            throw new IllegalArgumentException("The XPD method of ITU-R P.618-13 is only valid for 4-55 GHz");
        }
        if (el > 60) {
            LOG.warning("The XPD method of ITU-R P.618-13 is only valid for elevation angles below 60 degrees");
        }
        double fOrig = f;
        boolean scale = false;
        if (4 <= f && f < 6) {
            f = 6;
            scale = true;
        }

        double cf; // step 1
        if (6 <= f && f < 9) {
            cf = 60 * Math.log10(f) - 28.3;
        } else if (9 <= f && f < 36) {
            cf = 26 * Math.log10(f) + 4.1;
        } else {
            cf = 35.9 * Math.log10(f) - 11.3;
        }

        double v; // step 2
        if (6 <= f && f < 9) {
            v = 30.8 * Math.pow(f, -0.21);
        } else if (9 <= f && f < 20) {
            v = 12.8 * Math.pow(f, 0.19);
        } else if (20 <= f && f < 40) {
            v = 22.6;
        } else {
            v = 13.0 * Math.pow(f, 0.15);
        }
        double ca = v * Math.log10(ap);

        double cTau = -10 * Math.log10(1 - 0.484 * (1 + Math.cos(Math.toRadians(4 * tau)))); // step 3
        double cTheta = -40 * Math.log10(Math.cos(Math.toRadians(el))); // step 4

        double cSigma; // step 5
        if (p <= 0.001) {
            cSigma = 0.0053 * 15 * 15;
        } else if (p <= 0.01) {
            cSigma = 0.0053 * 10 * 10;
        } else if (p <= 0.1) {
            cSigma = 0.0053 * 5 * 5;
        } else {
            cSigma = 0.0;
        }

        double xpdRain = cf - ca + cTau + cTheta + cSigma; // step 6
        double cIce = xpdRain * (0.3 + 0.1 * Math.log10(p)) / 2; // step 7
        double xpdP = xpdRain - cIce; // step 8

        if (scale) {
            double s = Math.sqrt(1 - 0.484 * (1 + Math.cos(Math.toRadians(4 * tau))));
            xpdP = xpdP - 20 * Math.log10(fOrig * s / (f * s));
        }
        return xpdP;
    }

    // ------------------------------------------------------------------ scintillation

    /**
     * Standard deviation of the tropospheric scintillation signal amplitude (dB), Section 2.4.1 steps 1-7.
     *
     * @param p    percentage of time
     * @param d    antenna diameter (m)
     * @param eta  antenna efficiency (0.5 if unknown)
     * @param tC   average surface ambient temperature (degrees Celsius), or {@code null}
     * @param hPct average surface relative humidity (%), or {@code null}
     * @param pHpa average surface pressure (hPa), or {@code null}; when any of the three is {@code null} the
     *             P.453 map of the wet refractivity (50%) is used
     * @param hL   height of the turbulent layer (m), 1000 m by default
     */
    public static double scintillationAttenuationSigma(double lat, double lon, double f, double el, double p,
            double d, double eta, Double tC, Double hPct, Double pHpa, double hL) {
        double nWet;
        if (tC != null && hPct != null && pHpa != null) {
            double e = P453.waterVapourPressure(tC, pHpa, hPct); // steps 1-2
            nWet = P453.wetTermRadioRefractivity(e, tC);
        } else {
            nWet = P453.mapWetTermRadioRefractivity(lat, Maths.lon360(lon), 50);
        }
        double sigmaRef = 3.6e-3 + 1e-4 * nWet; // Eq. 43 [dB]
        double sinEl = Math.sin(Math.toRadians(el));
        double l = 2 * hL / (Math.sqrt(sinEl * sinEl + 2.35e-4) + sinEl); // Eq. 44 [m]
        double dEff = Math.sqrt(eta) * d; // Eq. 45 [m]
        double x = 1.22 * dEff * dEff * f / l;
        double g = x >= 7.0 ? 0.0
                : Math.sqrt(3.86 * Math.pow(x * x + 1, 11.0 / 12) * Math.sin(11.0 / 6 * Math.atan2(1, x))
                        - 7.08 * Math.pow(x, 5.0 / 6)); // Eq. 46
        return sigmaRef * Math.pow(f, 7.0 / 12) * g / Math.pow(sinEl, 1.2);
    }

    /** Tropospheric scintillation fade depth (dB) exceeded for p% of the time, for 0.01% &lt; p &lt; 50%. */
    public static double scintillationAttenuation(double lat, double lon, double f, double el, double p,
            double d, double eta, Double tC, Double hPct, Double pHpa, double hL) {
        double sigma = scintillationAttenuationSigma(lat, lon, f, el, p, d, eta, tC, hPct, pHpa, hL);
        double lp = Math.log10(p);
        double a = -0.061 * (lp * lp * lp) + 0.072 * (lp * lp) - 1.71 * lp + 3; // step 8
        double as = a * sigma; // Eq. 49
        return as < 0 ? 0.0 : as;
    }
}
