package org.itur.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GeodesyTest {

    @Test
    void sameLocationIsZero() {
        assertEquals(0, Geodesy.distanceKm(10, 20, 10, 20), 1e-9);
    }

    @Test
    void oneDegreeOfLongitudeAtTheEquator() {
        // a * pi / 180 on the WGS-84 ellipsoid
        assertEquals(111.3195, Geodesy.distanceKm(0, 0, 0, 1), 1e-3);
    }

    @Test
    void halfAMeridian() {
        // pole to pole on the WGS-84 ellipsoid
        assertEquals(20003.93, Geodesy.distanceKm(90, 0, -90, 0), 0.01);
    }

    @Test
    void symmetricAndTriangleInequality() {
        double ab = Geodesy.distanceKm(48.85, 2.35, 40.71, -74.0);
        assertEquals(ab, Geodesy.distanceKm(40.71, -74.0, 48.85, 2.35), 1e-6);
        assertEquals(5853, ab, 5); // Paris - New York
        double ac = Geodesy.distanceKm(48.85, 2.35, 51.5, -0.12);
        double cb = Geodesy.distanceKm(51.5, -0.12, 40.71, -74.0);
        assertTrue(ab <= ac + cb);
    }

    @Test
    void longitudeConventionDoesNotMatter() {
        assertEquals(Geodesy.distanceKm(10, 170, 10, -170), Geodesy.distanceKm(10, 170, 10, 190), 1e-6);
    }

    @Test
    void nearlyAntipodalPoints() {
        // Vincenty's iteration fails there: the result must still be close to half the circumference
        assertEquals(20004, Geodesy.distanceKm(0, 0, 0, 180), 100);
        assertEquals(20004, Geodesy.distanceKm(0, 0, 0, 179.9), 100);
        assertEquals(20004, Geodesy.distanceKm(10, 20, -10, -160), 100);
        double prev = 0;
        for (double lon = 170; lon <= 180; lon += 0.5) {
            double d = Geodesy.distanceKm(0, 0, 0, lon);
            assertTrue(d > prev, "monotonic at " + lon);
            prev = d;
        }
    }

    @Test
    void poles() {
        assertEquals(0, Geodesy.distanceKm(90, 10, 90, -170), 1e-6);
        assertEquals(10001.97, Geodesy.distanceKm(0, 33, 90, 77), 0.01); // equator to pole
    }
}
