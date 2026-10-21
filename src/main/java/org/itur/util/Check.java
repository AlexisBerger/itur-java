package org.itur.util;

/**
 * Argument checks shared by the models. Every public method of the library rejects an argument that
 * has no physical meaning (not-a-number, a negative frequency, a latitude above 90 degrees, a
 * probability outside 0 to 100 %, ...) with an {@link IllegalArgumentException} whose message names
 * the argument and the accepted range. Arguments that are merely outside the validity range of a
 * Recommendation (for example a probability below 0.001 % in P.618) are accepted, as in ITU-Rpy,
 * and a warning is logged where the Recommendation states a limit.
 *
 * <p>This class is an implementation helper, not part of the stable API of the library.
 */
public final class Check {

    private Check() {
    }

    private static IllegalArgumentException fail(String name, String requirement, double value) {
        return new IllegalArgumentException(name + " must be " + requirement + ", got " + value);
    }

    /**
     * Checks that a value lies in a closed interval (a NaN never does).
     *
     * @param name argument name used in the message
     * @param v value to check
     * @param min lower bound
     * @param max upper bound
     * @return {@code v}
     * @throws IllegalArgumentException if {@code v} is NaN or outside {@code [min, max]}
     */
    public static double range(String name, double v, double min, double max) {
        if (!(v >= min && v <= max)) {
            throw fail(name, "within [" + min + ", " + max + "]", v);
        }
        return v;
    }

    /**
     * Checks that a value is a finite number.
     *
     * @param name argument name used in the message
     * @param v value to check
     * @return {@code v}
     * @throws IllegalArgumentException if {@code v} is NaN or infinite
     */
    public static double finite(String name, double v) {
        if (!Double.isFinite(v)) {
            throw fail(name, "a finite number", v);
        }
        return v;
    }

    /**
     * Checks that a value is strictly positive and finite.
     *
     * @param name argument name used in the message
     * @param v value to check
     * @return {@code v}
     * @throws IllegalArgumentException if {@code v} is not a finite number greater than 0
     */
    public static double positive(String name, double v) {
        if (!(v > 0 && v < Double.POSITIVE_INFINITY)) {
            throw fail(name, "a finite number greater than 0", v);
        }
        return v;
    }

    /**
     * Checks that a value is zero or positive and finite.
     *
     * @param name argument name used in the message
     * @param v value to check
     * @return {@code v}
     * @throws IllegalArgumentException if {@code v} is not a finite number greater than or equal to 0
     */
    public static double nonNegative(String name, double v) {
        if (!(v >= 0 && v < Double.POSITIVE_INFINITY)) {
            throw fail(name, "a finite number greater than or equal to 0", v);
        }
        return v;
    }

    /**
     * Checks a latitude.
     *
     * @param v latitude (degrees)
     * @throws IllegalArgumentException if {@code v} is not within -90 to 90 degrees
     */
    public static void latitude(double v) {
        range("latitude", v, -90, 90);
    }

    /**
     * Checks a longitude: any finite value is accepted, since longitudes are wrapped.
     *
     * @param v longitude (degrees)
     * @throws IllegalArgumentException if {@code v} is NaN or infinite
     */
    public static void longitude(double v) {
        finite("longitude", v);
    }

    /**
     * Checks a geographic position.
     *
     * @param lat latitude (degrees), -90 to 90
     * @param lon longitude (degrees), any finite value
     * @throws IllegalArgumentException if either coordinate is invalid
     */
    public static void position(double lat, double lon) {
        latitude(lat);
        longitude(lon);
    }

    /**
     * Checks a frequency.
     *
     * @param fGHz frequency (GHz)
     * @throws IllegalArgumentException if {@code fGHz} is not a finite number greater than 0
     */
    public static void frequency(double fGHz) {
        positive("frequency (GHz)", fGHz);
    }

    /**
     * Checks the elevation angle of a slant path, which must point above the horizon.
     *
     * @param elDeg elevation angle (degrees)
     * @throws IllegalArgumentException if {@code elDeg} is not within 0 (excluded) to 90 degrees
     */
    public static void slantElevation(double elDeg) {
        if (!(elDeg > 0 && elDeg <= 90)) {
            throw fail("elevation angle (degrees)", "within ]0, 90]", elDeg);
        }
    }

    /**
     * Checks a percentage of time.
     *
     * @param p percentage of an average year (%)
     * @throws IllegalArgumentException if {@code p} is not within 0 (excluded) to 100
     */
    public static void probability(double p) {
        if (!(p > 0 && p <= 100)) {
            throw fail("percentage of time p (%)", "within ]0, 100]", p);
        }
    }

    /**
     * Checks a relative humidity.
     *
     * @param hPercent relative humidity (%)
     * @throws IllegalArgumentException if {@code hPercent} is not within 0 to 100
     */
    public static void humidity(double hPercent) {
        range("relative humidity (%)", hPercent, 0, 100);
    }

    /**
     * Checks a temperature expressed in kelvin.
     *
     * @param tKelvin temperature (K)
     * @throws IllegalArgumentException if {@code tKelvin} is not a finite number greater than 0
     */
    public static void kelvin(double tKelvin) {
        positive("temperature (K)", tKelvin);
    }

    /**
     * Checks a temperature expressed in degrees Celsius.
     *
     * @param tCelsius temperature (deg C)
     * @throws IllegalArgumentException if {@code tCelsius} is not a finite number above absolute zero
     */
    public static void celsius(double tCelsius) {
        if (!(tCelsius > -273.15 && tCelsius < Double.POSITIVE_INFINITY)) {
            throw fail("temperature (deg C)", "a finite number above -273.15", tCelsius);
        }
    }

    /**
     * Checks an optional value: {@code null} means "derive it from the maps" and is accepted.
     *
     * @param name argument name used in the message
     * @param v value, or {@code null}
     * @throws IllegalArgumentException if {@code v} is not null and not a finite number
     */
    public static void optionalFinite(String name, Double v) {
        if (v != null) {
            finite(name, v);
        }
    }

    /**
     * Checks an optional value that must be positive when given.
     *
     * @param name argument name used in the message
     * @param v value, or {@code null}
     * @throws IllegalArgumentException if {@code v} is not null and not a finite number greater than 0
     */
    public static void optionalPositive(String name, Double v) {
        if (v != null) {
            positive(name, v);
        }
    }

    /**
     * Checks an optional value that must not be negative when given.
     *
     * @param name argument name used in the message
     * @param v value, or {@code null}
     * @throws IllegalArgumentException if {@code v} is not null and not a finite number {@code >= 0}
     */
    public static void optionalNonNegative(String name, Double v) {
        if (v != null) {
            nonNegative(name, v);
        }
    }
}
