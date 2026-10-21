package org.itur;

import org.itur.models.P1510;
import org.itur.models.P1511;
import org.itur.models.P618;
import org.itur.models.P676;
import org.itur.models.P835;
import org.itur.models.P836;
import org.itur.models.P840;
import org.itur.util.Check;

/**
 * Total atmospheric attenuation of an Earth-space path, combining gases (P.676-13), clouds (P.840-7),
 * rain (P.618-13) and scintillation (P.618-13) as recommended by P.618-13 Section 2.5:
 * {@code A = A_gas + sqrt((A_rain + A_cloud)^2 + A_scint^2)}.
 *
 * <p>Missing inputs are derived from the ITU maps: station altitude (P.1511-1), mean surface
 * temperature (P.1510-1), surface pressure (P.835-6 standard atmosphere), integrated water vapour
 * (P.836-6), surface water vapour density (P.836-6), rainfall rate (P.837-7), rain height (P.839-4).
 */
public final class Itur {

    private Itur() {
    }

    /**
     * Components of the total attenuation, all in dB.
     *
     * @param gas gaseous attenuation (P.676)
     * @param cloud cloud attenuation (P.840)
     * @param rain rain attenuation (P.618)
     * @param scintillation tropospheric scintillation fade depth (P.618)
     * @param total combined attenuation, {@code gas + sqrt((rain + cloud)^2 + scintillation^2)}
     */
    public record Attenuation(double gas, double cloud, double rain, double scintillation, double total) {
    }

    /**
     * Optional inputs of {@link #atmosphericAttenuationSlantPath}; {@code null} means "derive from the maps".
     *
     * @param hsKm station altitude above mean sea level (km)
     * @param rhoGPerM3 surface water vapour density (g/m3)
     * @param r001MmPerHour rainfall rate exceeded for 0.01 % of the time (mm/h)
     * @param tKelvin surface temperature (K)
     * @param hPercent surface relative humidity (%)
     * @param pHPa surface pressure (hPa)
     * @param lsKm slant path length through rain (km)
     * @param vtKgPerM2 integrated water vapour content (kg/m2)
     * @param eta antenna efficiency
     * @param hLMeters height of the turbulent layer (m)
     * @param tauDeg polarization tilt angle (degrees)
     * @param gasMode gas attenuation method
     * @param rain whether rain attenuation is included
     * @param gas whether gaseous attenuation is included
     * @param scintillation whether scintillation is included
     * @param clouds whether cloud attenuation is included
     */
    public record Options(Double hsKm, Double rhoGPerM3, Double r001MmPerHour, Double tKelvin, Double hPercent,
                          Double pHPa, Double lsKm, Double vtKgPerM2, double eta, double hLMeters, double tauDeg,
                          P676.Mode gasMode, boolean rain, boolean gas, boolean scintillation, boolean clouds) {

        /** Checks the values that were given; {@code null} stays "derive from the maps". */
        public Options {
            Check.optionalFinite("station altitude (km)", hsKm);
            Check.optionalNonNegative("water vapour density (g/m3)", rhoGPerM3);
            Check.optionalNonNegative("rainfall rate R0.01 (mm/h)", r001MmPerHour);
            if (tKelvin != null) {
                Check.kelvin(tKelvin);
            }
            if (hPercent != null) {
                Check.humidity(hPercent);
            }
            Check.optionalPositive("pressure (hPa)", pHPa);
            Check.optionalNonNegative("slant path length (km)", lsKm);
            Check.optionalPositive("water vapour content (kg/m2)", vtKgPerM2);
            Check.range("antenna efficiency", eta, Math.nextUp(0.0), 1);
            Check.positive("height of the turbulent layer (m)", hLMeters);
            Check.finite("polarization tilt angle (degrees)", tauDeg);
            java.util.Objects.requireNonNull(gasMode, "gasMode");
        }

        /**
         * Default options: everything derived from the maps, all contributions included.
         *
         * @return the default options
         */
        public static Options defaults() {
            return builder().build();
        }

        /**
         * Builder with the defaults of {@link #defaults()}; set only what differs.
         *
         * @return a new builder
         */
        public static Builder builder() {
            return new Builder();
        }

        /** Mutable builder for {@link Options}. */
        public static final class Builder {
            private Double hsKm;
            private Double rhoGPerM3;
            private Double r001MmPerHour;
            private Double tKelvin;
            private Double hPercent;
            private Double pHPa;
            private Double lsKm;
            private Double vtKgPerM2;
            private double eta = 0.5;
            private double hLMeters = 1000.0;
            private double tauDeg = 45.0;
            private P676.Mode gasMode = P676.Mode.APPROX;
            private boolean rain = true;
            private boolean gas = true;
            private boolean scintillation = true;
            private boolean clouds = true;

            private Builder() {
            }

            /**
             * Station altitude above mean sea level (km). Default: P.1511 topography.
             *
             * @param v station altitude (km)
             * @return this builder
             */
            public Builder hsKm(double v) {
                hsKm = v;
                return this;
            }

            /**
             * Surface water vapour density (g/m3). Default: P.836.
             *
             * @param v water vapour density (g/m3)
             * @return this builder
             */
            public Builder rhoGPerM3(double v) {
                rhoGPerM3 = v;
                return this;
            }

            /**
             * Rainfall rate exceeded for 0.01 % of the time (mm/h). Default: P.837.
             *
             * @param v rainfall rate (mm/h)
             * @return this builder
             */
            public Builder r001MmPerHour(double v) {
                r001MmPerHour = v;
                return this;
            }

            /**
             * Surface temperature (K). Default: P.1510 annual mean.
             *
             * @param v temperature (K)
             * @return this builder
             */
            public Builder tKelvin(double v) {
                tKelvin = v;
                return this;
            }

            /**
             * Surface relative humidity (%), used with temperature and pressure for scintillation.
             *
             * @param v relative humidity (%)
             * @return this builder
             */
            public Builder hPercent(double v) {
                hPercent = v;
                return this;
            }

            /**
             * Surface pressure (hPa). Default: P.835 standard atmosphere at the station altitude.
             *
             * @param v pressure (hPa)
             * @return this builder
             */
            public Builder pHPa(double v) {
                pHPa = v;
                return this;
            }

            /**
             * Slant path length through rain (km). Default: from the P.839 rain height.
             *
             * @param v slant path length (km)
             * @return this builder
             */
            public Builder lsKm(double v) {
                lsKm = v;
                return this;
            }

            /**
             * Integrated water vapour content (kg/m2). Default: P.836.
             *
             * @param v water vapour content (kg/m2)
             * @return this builder
             */
            public Builder vtKgPerM2(double v) {
                vtKgPerM2 = v;
                return this;
            }

            /**
             * Antenna efficiency (default 0.5).
             *
             * @param v antenna efficiency (0 to 1)
             * @return this builder
             */
            public Builder eta(double v) {
                eta = v;
                return this;
            }

            /**
             * Height of the turbulent layer (m, default 1000).
             *
             * @param v height (m)
             * @return this builder
             */
            public Builder hLMeters(double v) {
                hLMeters = v;
                return this;
            }

            /**
             * Polarization tilt angle (degrees, default 45 = circular).
             *
             * @param v tilt angle relative to the horizontal (degrees)
             * @return this builder
             */
            public Builder tauDeg(double v) {
                tauDeg = v;
                return this;
            }

            /**
             * Gas attenuation method (default {@link P676.Mode#APPROX}).
             *
             * @param v gas attenuation method
             * @return this builder
             */
            public Builder gasMode(P676.Mode v) {
                gasMode = v;
                return this;
            }

            /**
             * Include / exclude the individual contributions (all included by default).
             *
             * @param rain include rain attenuation
             * @param gas include gaseous attenuation
             * @param clouds include cloud attenuation
             * @param scintillation include scintillation
             * @return this builder
             */
            public Builder include(boolean rain, boolean gas, boolean clouds, boolean scintillation) {
                this.rain = rain;
                this.gas = gas;
                this.clouds = clouds;
                this.scintillation = scintillation;
                return this;
            }

            /**
             * Builds the options.
             *
             * @return the immutable options
             */
            public Options build() {
                return new Options(hsKm, rhoGPerM3, r001MmPerHour, tKelvin, hPercent, pHPa, lsKm, vtKgPerM2, eta,
                    hLMeters, tauDeg, gasMode, rain, gas, scintillation, clouds);
            }
        }
    }

    /**
     * Total attenuation of an Earth-space path, {@code A = Ag + sqrt((Ar + Ac)^2 + As^2)}. Contributions that
     * need the climate maps use {@code max(1, p)}, since gases and clouds are not defined below 1 %.
     *
     * @param latDeg latitude of the station (degrees)
     * @param lonDeg longitude of the station (degrees)
     * @param fGHz frequency (GHz)
     * @param elDeg elevation angle (degrees)
     * @param p percentage of time the attenuation is exceeded (0.001 to 50)
     * @param dMeters earth-station antenna diameter (m)
     * @param o optional inputs; {@code null} fields are derived from the maps
     * @return the components and the total attenuation (dB)
     */
    public static Attenuation atmosphericAttenuationSlantPath(double latDeg, double lonDeg, double fGHz,
                                                              double elDeg, double p, double dMeters,
                                                              Options o) {
        Check.position(latDeg, lonDeg);
        Check.frequency(fGHz);
        Check.slantElevation(elDeg);
        Check.probability(p);
        Check.positive("antenna diameter (m)", dMeters);
        java.util.Objects.requireNonNull(o, "options");
        double pCg = Math.max(1, p);
        double hs = o.hsKm() != null ? o.hsKm() : P1511.topographicAltitude(latDeg, lonDeg);
        double t = o.tKelvin() != null ? o.tKelvin() : P1510.surfaceMeanTemperature(latDeg, lonDeg);
        double pressure = o.pHPa() != null ? o.pHPa() : P835.standardPressure(hs);
        double vt = o.vtKgPerM2() != null ? o.vtKgPerM2() : P836.totalWaterVapourContent(latDeg, lonDeg, pCg, hs);
        double rho = o.rhoGPerM3() != null ? o.rhoGPerM3()
            : P836.surfaceWaterVapourDensity(latDeg, lonDeg, pCg, hs);

        double ar = o.rain() ? P618.rainAttenuation(latDeg, lonDeg, fGHz, elDeg, hs, p, o.r001MmPerHour(),
            o.tauDeg(), o.lsKm()) : 0;
        // a location outside a map gives NaN, as in ITU-Rpy (the models reject NaN arguments)
        boolean gasInputsMissing = Double.isNaN(rho) || Double.isNaN(pressure) || Double.isNaN(t)
            || (o.gasMode() == P676.Mode.APPROX && (Double.isNaN(vt) || Double.isNaN(hs)));
        double ag = !o.gas() ? 0 : gasInputsMissing ? Double.NaN
            : P676.gaseousAttenuationSlantPath(fGHz, elDeg, rho, pressure, t, vt, hs, o.gasMode());
        double ac = o.clouds() ? P840.cloudAttenuation(latDeg, lonDeg, elDeg, fGHz, pCg, null) : 0;
        double as = o.scintillation()
            ? P618.scintillationAttenuation(latDeg, lonDeg, fGHz, elDeg, p, dMeters, o.eta(), t - 273.15,
                o.hPercent(), pressure, o.hLMeters())
            : 0;
        double total = ag + Math.sqrt((ar + ac) * (ar + ac) + as * as);
        return new Attenuation(ag, ac, ar, as, total);
    }

    /**
     * Total attenuation with default options.
     *
     * @param latDeg latitude of the station (degrees)
     * @param lonDeg longitude of the station (degrees)
     * @param fGHz frequency (GHz)
     * @param elDeg elevation angle (degrees)
     * @param p percentage of time the attenuation is exceeded (0.001 to 50)
     * @param dMeters earth-station antenna diameter (m)
     * @return the components and the total attenuation (dB)
     */
    public static Attenuation atmosphericAttenuationSlantPath(double latDeg, double lonDeg, double fGHz,
                                                              double elDeg, double p, double dMeters) {
        return atmosphericAttenuationSlantPath(latDeg, lonDeg, fGHz, elDeg, p, dMeters, Options.defaults());
    }
}
