package org.itur.util;

/**
 * Geodesic distance on the WGS-84 ellipsoid (Vincenty's inverse formula), used for the site-diversity method.
 * ITU-Rpy obtains it with {@code pyproj.Geod.inv}; both agree to well below a millimetre except for
 * nearly antipodal points.
 */
public final class Geodesy {

    private static final double A = 6378137.0;
    private static final double F = 1.0 / 298.257223563;
    private static final double B = (1.0 - F) * A;

    private Geodesy() {
    }

    /** Distance in km between two points given in degrees. */
    public static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double l = Math.toRadians(lon2 - lon1);
        double u1 = Math.atan((1 - F) * Math.tan(phi1));
        double u2 = Math.atan((1 - F) * Math.tan(phi2));
        double sinU1 = Math.sin(u1);
        double cosU1 = Math.cos(u1);
        double sinU2 = Math.sin(u2);
        double cosU2 = Math.cos(u2);

        double lambda = l;
        double sinSigma = 0;
        double cosSigma = 0;
        double sigma = 0;
        double cos2Alpha = 0;
        double cos2SigmaM = 0;
        for (int i = 0; i < 200; i++) {
            double sinL = Math.sin(lambda);
            double cosL = Math.cos(lambda);
            double t1 = cosU2 * sinL;
            double t2 = cosU1 * sinU2 - sinU1 * cosU2 * cosL;
            sinSigma = Math.sqrt(t1 * t1 + t2 * t2);
            if (sinSigma == 0) {
                return 0.0; // coincident points
            }
            cosSigma = sinU1 * sinU2 + cosU1 * cosU2 * cosL;
            sigma = Math.atan2(sinSigma, cosSigma);
            double sinAlpha = cosU1 * cosU2 * sinL / sinSigma;
            cos2Alpha = 1 - sinAlpha * sinAlpha;
            cos2SigmaM = cos2Alpha != 0 ? cosSigma - 2 * sinU1 * sinU2 / cos2Alpha : 0.0;
            double c = F / 16 * cos2Alpha * (4 + F * (4 - 3 * cos2Alpha));
            double next = l + (1 - c) * F * sinAlpha
                    * (sigma + c * sinSigma * (cosSigma_(cosSigma, cos2SigmaM, c)));
            if (Math.abs(next - lambda) < 1e-13) {
                lambda = next;
                break;
            }
            lambda = next;
        }
        double u2sq = cos2Alpha * (A * A - B * B) / (B * B);
        double capA = 1 + u2sq / 16384 * (4096 + u2sq * (-768 + u2sq * (320 - 175 * u2sq)));
        double capB = u2sq / 1024 * (256 + u2sq * (-128 + u2sq * (74 - 47 * u2sq)));
        double deltaSigma = capB * sinSigma * (cos2SigmaM + capB / 4 * (cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM)
                - capB / 6 * cos2SigmaM * (-3 + 4 * sinSigma * sinSigma) * (-3 + 4 * cos2SigmaM * cos2SigmaM)));
        return B * capA * (sigma - deltaSigma) / 1000.0;
    }

    private static double cosSigma_(double cosSigma, double cos2SigmaM, double c) {
        return cos2SigmaM + c * cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM);
    }
}
