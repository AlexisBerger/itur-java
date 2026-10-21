package org.itur.util;

/**
 * Geodesic distance on the WGS84 ellipsoid (Vincenty's inverse formula, sub-millimetre accuracy).
 * Vincenty's iteration does not converge for nearly antipodal points (more than about 179.4 degrees
 * of longitude apart near the equator); the great-circle distance on a sphere of mean radius is returned
 * then, within 0.5 % of the geodesic, which is of no consequence for the site separations of a
 * propagation study.
 */
public final class Geodesy {

    private Geodesy() {
    }

    private static final double A = 6378137.0;
    private static final double F = 1 / 298.257223563;
    private static final double B = A * (1 - F);
    private static final double MEAN_RADIUS_KM = 6371.0088;

    /**
     * Distance in km between two points given in degrees, on the WGS-84 ellipsoid (Vincenty's
     * inverse formula).
     *
     * @param lat1 latitude of the first point (degrees)
     * @param lon1 longitude of the first point (degrees)
     * @param lat2 latitude of the second point (degrees)
     * @param lon2 longitude of the second point (degrees)
     * @return geodesic distance (km)
     */
    public static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double l = Math.IEEEremainder(Math.toRadians(lon2 - lon1), 2 * Math.PI); // -pi..pi
        double u1 = Math.atan((1 - F) * Math.tan(Math.toRadians(lat1)));
        double u2 = Math.atan((1 - F) * Math.tan(Math.toRadians(lat2)));
        double sU1 = Math.sin(u1);
        double cU1 = Math.cos(u1);
        double sU2 = Math.sin(u2);
        double cU2 = Math.cos(u2);
        double lam = l;
        double ss = 0;
        double cs = 0;
        double sigma = 0;
        double c2a = 0;
        double c2sm = 0;
        boolean converged = false;
        for (int i = 0; i < 200; i++) {
            double sl = Math.sin(lam);
            double cl = Math.cos(lam);
            double t1 = cU2 * sl;
            double t2 = cU1 * sU2 - sU1 * cU2 * cl;
            ss = Math.sqrt(t1 * t1 + t2 * t2);
            if (ss == 0) {
                return 0.0;
            }
            cs = sU1 * sU2 + cU1 * cU2 * cl;
            sigma = Math.atan2(ss, cs);
            double sa = cU1 * cU2 * sl / ss;
            c2a = 1 - sa * sa;
            c2sm = c2a != 0 ? cs - 2 * sU1 * sU2 / c2a : 0.0;
            double c = F / 16 * c2a * (4 + F * (4 - 3 * c2a));
            double lamNew = l + (1 - c) * F * sa
                * (sigma + c * ss * (c2sm + c * cs * (-1 + 2 * c2sm * c2sm)));
            boolean done = Math.abs(lamNew - lam) < 1e-12;
            lam = lamNew;
            if (done) {
                converged = true;
                break;
            }
        }
        if (!converged) {
            return greatCircleKm(lat1, lon1, lat2, lon2);
        }
        double u2sq = c2a * (A * A - B * B) / (B * B);
        double aa = 1 + u2sq / 16384 * (4096 + u2sq * (-768 + u2sq * (320 - 175 * u2sq)));
        double bb = u2sq / 1024 * (256 + u2sq * (-128 + u2sq * (74 - 47 * u2sq)));
        double ds = bb * ss * (c2sm + bb / 4 * (cs * (-1 + 2 * c2sm * c2sm)
            - bb / 6 * c2sm * (-3 + 4 * ss * ss) * (-3 + 4 * c2sm * c2sm)));
        return B * aa * (sigma - ds) / 1e3;
    }

    /** Haversine distance on a sphere of mean radius, used when Vincenty's iteration fails. */
    private static double greatCircleKm(double lat1, double lon1, double lat2, double lon2) {
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dp = p2 - p1;
        double dl = Math.toRadians(lon2 - lon1);
        double h = Math.sin(dp / 2) * Math.sin(dp / 2)
            + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2 * MEAN_RADIUS_KM * Math.asin(Math.min(1, Math.sqrt(h)));
    }
}
