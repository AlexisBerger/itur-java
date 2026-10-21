package org.itur;

/**
 * Total atmospheric attenuation of an Earth-space path, combining gases (P.676-11), clouds (P.840-7),
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

    /** Components of the total attenuation, all in dB. */
    public record Attenuation(double gas, double cloud, double rain, double scintillation, double total) {
    }

    /** Optional inputs of {@link #atmosphericAttenuationSlantPath}; {@code null} means "derive from the maps". */
    public record Options(Double hsKm, Double rhoGPerM3, Double r001MmPerHour, Double tKelvin, Double hPercent,
                          Double pHPa, Double lsKm, Double vtKgPerM2, double eta, double hLMeters, double tauDeg,
                          P676.Mode gasMode, boolean rain, boolean gas, boolean scintillation, boolean clouds) {

        /** Default options: everything derived from the maps, all contributions included. */
        public static Options defaults() {
            return builder().build();
        }

        /** Builder with the defaults of {@link #defaults()}; set only what differs. */
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

            /** Station altitude above mean sea level (km). Default: P.1511 topography. */
            public Builder hsKm(double v) {
                hsKm = v;
                return this;
            }

            /** Surface water vapour density (g/m3). Default: P.836. */
            public Builder rhoGPerM3(double v) {
                rhoGPerM3 = v;
                return this;
            }

            /** Rainfall rate exceeded for 0.01 % of the time (mm/h). Default: P.837. */
            public Builder r001MmPerHour(double v) {
                r001MmPerHour = v;
                return this;
            }

            /** Surface temperature (K). Default: P.1510 annual mean. */
            public Builder tKelvin(double v) {
                tKelvin = v;
                return this;
            }

            /** Surface relative humidity (%), used with temperature and pressure for scintillation. */
            public Builder hPercent(double v) {
                hPercent = v;
                return this;
            }

            /** Surface pressure (hPa). Default: P.835 standard atmosphere at the station altitude. */
            public Builder pHPa(double v) {
                pHPa = v;
                return this;
            }

            /** Slant path length through rain (km). Default: from the P.839 rain height. */
            public Builder lsKm(double v) {
                lsKm = v;
                return this;
            }

            /** Integrated water vapour content (kg/m2). Default: P.836. */
            public Builder vtKgPerM2(double v) {
                vtKgPerM2 = v;
                return this;
            }

            /** Antenna efficiency (default 0.5). */
            public Builder eta(double v) {
                eta = v;
                return this;
            }

            /** Height of the turbulent layer (m, default 1000). */
            public Builder hLMeters(double v) {
                hLMeters = v;
                return this;
            }

            /** Polarization tilt angle (degrees, default 45 = circular). */
            public Builder tauDeg(double v) {
                tauDeg = v;
                return this;
            }

            /** Gas attenuation method (default {@link P676.Mode#APPROX}). */
            public Builder gasMode(P676.Mode v) {
                gasMode = v;
                return this;
            }

            /** Include / exclude the individual contributions (all included by default). */
            public Builder include(boolean rain, boolean gas, boolean clouds, boolean scintillation) {
                this.rain = rain;
                this.gas = gas;
                this.clouds = clouds;
                this.scintillation = scintillation;
                return this;
            }

            public Options build() {
                return new Options(hsKm, rhoGPerM3, r001MmPerHour, tKelvin, hPercent, pHPa, lsKm, vtKgPerM2, eta,
                    hLMeters, tauDeg, gasMode, rain, gas, scintillation, clouds);
            }
        }
    }

    /**
     * @param p percentage of time the attenuation is exceeded (0.001 to 50)
     * @param dMeters earth-station antenna diameter (m)
     */
    public static Attenuation atmosphericAttenuationSlantPath(double latDeg, double lonDeg, double fGHz,
                                                              double elDeg, double p, double dMeters,
                                                              Options o) {
        double pCg = Math.max(1, p);
        double hs = o.hsKm() != null ? o.hsKm() : P1511.topographicAltitude(latDeg, lonDeg);
        double t = o.tKelvin() != null ? o.tKelvin() : P1510.surfaceMeanTemperature(latDeg, lonDeg);
        double pressure = o.pHPa() != null ? o.pHPa() : P835.standardPressure(hs);
        double vt = o.vtKgPerM2() != null ? o.vtKgPerM2() : P836.totalWaterVapourContent(latDeg, lonDeg, pCg, hs);
        double rho = o.rhoGPerM3() != null ? o.rhoGPerM3()
            : P836.surfaceWaterVapourDensity(latDeg, lonDeg, pCg, hs);

        double ar = o.rain() ? P618.rainAttenuation(latDeg, lonDeg, fGHz, elDeg, hs, p, o.r001MmPerHour(),
            o.tauDeg(), o.lsKm()) : 0;
        double ag = o.gas() ? P676.gaseousAttenuationSlantPath(fGHz, elDeg, rho, pressure, t, vt, hs, o.gasMode()) : 0;
        double ac = o.clouds() ? P840.cloudAttenuation(latDeg, lonDeg, elDeg, fGHz, pCg, null) : 0;
        double as = o.scintillation()
            ? P618.scintillationAttenuation(latDeg, lonDeg, fGHz, elDeg, p, dMeters, o.eta(), t - 273.15,
                o.hPercent(), pressure, o.hLMeters())
            : 0;
        double total = ag + Math.sqrt((ar + ac) * (ar + ac) + as * as);
        return new Attenuation(ag, ac, ar, as, total);
    }

    /** Total attenuation with default options. */
    public static Attenuation atmosphericAttenuationSlantPath(double latDeg, double lonDeg, double fGHz,
                                                              double elDeg, double p, double dMeters) {
        return atmosphericAttenuationSlantPath(latDeg, lonDeg, fGHz, elDeg, p, dMeters, Options.defaults());
    }
}
