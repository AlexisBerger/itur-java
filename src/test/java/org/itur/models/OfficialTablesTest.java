package org.itur.models;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;
import org.itur.Itur;
import org.itur.models.OfficialTable.Row;
import org.junit.jupiter.api.Test;

/**
 * Checks the implementation against the validation tables published with the ITU-R
 * Recommendations (as distributed in ITU-Rpy's {@code test_data}, copied to
 * {@code src/test/resources/official}). P.676-13 has its own class,
 * {@link P676OfficialTablesTest}.
 *
 * <p>The default criterion is the one of ITU-Rpy's own test-suite: absolute error below 5e-6
 * (five decimal places). Two tables need a looser, documented tolerance, see
 * {@code docs/VALIDATION.md}: P.837-7 rainfall rate for {@code p != 0.01} (relative 5e-4, the
 * same difference as ITU-Rpy's) and P.1510-1 (the map is stored as float32: absolute 2e-5).
 */
class OfficialTablesTest {

    private static final double TOL = 5e-6;

    /** Compares one column of a table with the model; fails with the list of the offending rows. */
    private static int check(String resource, String column, double absTol, double relTol,
                             ToDoubleFunction<Row> model) throws Exception {
        List<Row> rows = OfficialTable.read(resource);
        List<String> bad = new ArrayList<>();
        int n = 0;
        for (Row row : rows) {
            double expected = row.get(column);
            if (Double.isNaN(expected)) {
                continue;
            }
            n++;
            double got = model.applyAsDouble(row);
            if (!(Math.abs(got - expected) <= absTol + relTol * Math.abs(expected))) {
                bad.add(row.text() + " -> " + got);
            }
        }
        assertTrue(n > 0, resource + ": no row compared");
        assertTrue(bad.isEmpty(), resource + " " + column + ": " + bad.size() + "/" + n + " rows differ: " + bad);
        return n;
    }

    private static int check(String resource, String column, ToDoubleFunction<Row> model) throws Exception {
        return check(resource, column, TOL, 0, model);
    }

    @Test
    void p453WetRefractivity() throws Exception {
        check("453/ITURP453-14_Nwet.csv", "Nwet",
            r -> P453.mapWetTermRadioRefractivity(r.get("lat"), r.get("lon"), r.get("p")));
    }

    @Test
    void p618RainAttenuation() throws Exception {
        int n = check("618/ITURP618-13_A_rain.csv", "A_rain", r -> P618.rainAttenuation(r.get("lat"), r.get("lon"),
            r.get("f"), r.get("el"), r.get("hs"), r.get("p"), r.get("R001"), r.get("tau"), null));
        assertTrue(n >= 60);
    }

    @Test
    void p618RainAttenuationProbability() throws Exception {
        check("618/ITURP618-13_A_rain.csv", "P_rain", r -> P618.rainAttenuationProbability(r.get("lat"),
            r.get("lon"), r.get("el"), r.get("hs"), r.get("Ls"), r.get("P0") * 100));
    }

    @Test
    void p618Scintillation() throws Exception {
        check("618/ITURP618-13_A_sci.csv", "A_scin", r -> P618.scintillationAttenuation(r.get("lat"), r.get("lon"),
            r.get("f"), r.get("el"), r.get("p"), r.get("D"), r.get("eta"), null, null, null, 1000));
    }

    @Test
    void p618CrossPolarizationDiscrimination() throws Exception {
        check("618/ITURP618-13_A_xpd.csv", "XPD", r -> P618.rainCrossPolarizationDiscrimination(r.get("Ap"),
            r.get("f"), r.get("el"), r.get("p"), r.get("tau")));
    }

    @Test
    void p836SurfaceWaterVapourDensity() throws Exception {
        check("836/ITURP836-6_surface_water_vapour_density_annual.csv", "rho", r -> P836.surfaceWaterVapourDensity(
            r.get("lat"), r.get("lon"), r.get("p"), r.get("alt")));
    }

    @Test
    void p836TotalWaterVapourContent() throws Exception {
        check("836/ITURP836-6_total_water_vapour_content_annual.csv", "V", r -> P836.totalWaterVapourContent(
            r.get("lat"), r.get("lon"), r.get("p"), r.get("alt")));
    }

    @Test
    void p837RainfallRateMap() throws Exception {
        check("837/ITURP837-7_rainfall_rate_R001.csv", "Rp",
            r -> P837.rainfallRate(r.get("lat"), r.get("lon"), r.get("p")));
    }

    @Test
    void p837RainfallRateOtherProbabilities() throws Exception {
        // Known difference, identical in ITU-Rpy: up to 3.4e-4 relative for p != 0.01
        check("837/ITURP837-7_rainfall_rate.csv", "Rp", 0, 5e-4,
            r -> P837.rainfallRate(r.get("lat"), r.get("lon"), r.get("p")));
    }

    @Test
    void p837RainfallProbability() throws Exception {
        check("837/ITURP837-7_rainfall_rate_probability.csv", "p",
            r -> P837.rainfallProbability(r.get("lat"), r.get("lon")));
    }

    @Test
    void p838RainSpecificAttenuation() throws Exception {
        String t = "838/ITURP838-3_rain_specific_attenuation.csv";
        check(t, "k", r -> P838.coefficients(r.get("f"), r.get("el"), r.get("tau")).k());
        check(t, "alpha", r -> P838.coefficients(r.get("f"), r.get("el"), r.get("tau")).alpha());
        check(t, "gamma_r", r -> P838.rainSpecificAttenuation(r.get("R"), r.get("f"), r.get("el"), r.get("tau")));
    }

    @Test
    void p839RainHeight() throws Exception {
        String t = "839/ITURP839-4_rain_height.csv";
        check(t, "h0", r -> P839.isotherm0(r.get("lat"), r.get("lon")));
        check(t, "hr", r -> P839.rainHeight(r.get("lat"), r.get("lon")));
    }

    @Test
    void p840ColumnarContentAndCloudAttenuation() throws Exception {
        check("840/ITURP840-8_columnar_content_reduced_liquid.csv", "Lred", r -> P840.columnarContentReducedLiquid(
            r.get("lat"), r.get("lon"), r.get("p")));
        check("840/ITURP840-8_cloud_attenuation.csv", "Ac", r -> P840.cloudAttenuation(r.get("lat"), r.get("lon"),
            r.get("el"), r.get("f"), r.get("p"), null));
    }

    @Test
    void p1510Temperature() throws Exception {
        // the map is stored as float32 (ITU-Rpy keeps float64): 1.3e-5 K at most
        check("1510/ITURP1510-1_temperature.csv", "T", 2e-5, 0,
            r -> P1510.surfaceMeanTemperature(r.get("lat"), r.get("lon")));
    }

    @Test
    void p1511TopographicAltitude() throws Exception {
        check("1511/ITURP1511-1_topographic_altitude.csv", "hs",
            r -> P1511.topographicAltitude(r.get("lat"), r.get("lon")));
    }

    /**
     * The P.618-13 total-attenuation table. Only the contributions that do not depend on the version
     * of P.676 are compared: rain and scintillation (all rows) and clouds (rows with {@code p >= 1 %},
     * the table uses the exceedance probability itself below 1 % where {@link Itur} uses
     * {@code max(1, p)}). Rain is compared at 5e-4 relative: the table gives no rainfall rate, so it
     * comes from the P.837-7 map, with the known difference (3.4e-4, see above). The table's gas column follows P.676-12 and therefore differs by up to
     * 1e-2 dB from P.676-13, and so does the total.
     */
    @Test
    void p618TotalAttenuationTableContributions() throws Exception {
        List<Row> rows = OfficialTable.read("618/ITURP618-13_A_total.csv");
        List<String> bad = new ArrayList<>();
        int clouds = 0;
        for (Row r : rows) {
            Itur.Attenuation a = Itur.atmosphericAttenuationSlantPath(r.get("lat"), r.get("lon"), r.get("f"),
                r.get("el"), r.get("p"), r.get("D"), Itur.Options.builder().hsKm(r.get("hs")).eta(r.get("eta"))
                    .tauDeg(r.get("tau")).build());
            if (Math.abs(a.rain() - r.get("A_rain")) > 5e-4 * r.get("A_rain")) {
                bad.add("rain " + r.text() + " -> " + a.rain());
            }
            if (Math.abs(a.scintillation() - r.get("A_scin")) > TOL) {
                bad.add("scintillation " + r.text() + " -> " + a.scintillation());
            }
            if (r.get("p") >= 1) {
                clouds++;
                if (Math.abs(a.cloud() - r.get("A_clouds")) > TOL) {
                    bad.add("clouds " + r.text() + " -> " + a.cloud());
                }
            }
        }
        assertTrue(rows.size() >= 60 && clouds > 0);
        assertTrue(bad.isEmpty(), bad.size() + " differences: " + bad);
    }
}
