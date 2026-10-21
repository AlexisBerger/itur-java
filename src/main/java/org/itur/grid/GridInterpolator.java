package org.itur.grid;

/** A function of (latitude, longitude) defined from tabulated ITU data. */
@FunctionalInterface
public interface GridInterpolator {

    /**
     * @param lat latitude in degrees
     * @param lon longitude, in the same encoding as the underlying data set
     * @return the interpolated value, or NaN outside the data domain
     */
    double at(double lat, double lon);
}
