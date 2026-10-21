import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.DoubleUnaryOperator;
import org.itur.Itur;
import org.itur.grid.Grid;
import org.itur.models.P1510;
import org.itur.models.P1511;
import org.itur.models.P453;
import org.itur.models.P618;
import org.itur.models.P676;
import org.itur.models.P835;
import org.itur.models.P836;
import org.itur.models.P837;
import org.itur.models.P838;
import org.itur.models.P839;
import org.itur.models.P840;
import org.itur.util.Geodesy;

/**
 * Writes the data behind the figures and the worked examples of docs/recommendations, computed with
 * this library. Run it from the repository root after "mvn compile":
 *
 * <pre>
 *   java -cp target/classes tools/DocFigureData.java build/doc-data
 * </pre>
 *
 * Then: python3 tools/plot_doc_figures.py build/doc-data docs/img
 */
public class DocFigureData {

    static final String[] CITY = {"Paris", "Kourou", "Singapore", "Dubai"};
    static final double[][] POS = {{48.85, 2.35}, {5.16, -52.65}, {1.35, 103.82}, {25.20, 55.27}};
    static Path out;

    public static void main(String[] args) throws IOException {
        Locale.setDefault(Locale.ROOT);
        out = Path.of(args[0]);
        Files.createDirectories(out);
        maps();
        lines();
        examples();
    }

    static double safe(java.util.function.DoubleSupplier s) {
        try {
            return s.getAsDouble();
        } catch (RuntimeException e) {
            return Double.NaN;
        }
    }

    // ---- maps --------------------------------------------------------------------------------

    interface Map2 {
        double at(double lat, double lon);
    }

    static void map(String name, double latStep, double lonStep, Map2 f) throws IOException {
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out.resolve("map_" + name + ".csv")))) {
            StringBuilder h = new StringBuilder("lat");
            for (double lon = -180; lon <= 180 + 1e-9; lon += lonStep) {
                h.append(',').append(lon);
            }
            w.println(h);
            for (double lat = 85; lat >= -85 - 1e-9; lat -= latStep) {
                StringBuilder sb = new StringBuilder().append(lat);
                for (double lon = -180; lon <= 180 + 1e-9; lon += lonStep) {
                    double lt = lat;
                    double ln = lon;
                    sb.append(',').append(safe(() -> f.at(lt, ln)));
                }
                w.println(sb);
            }
        }
    }

    static void maps() throws IOException {
        map("topo", 1.0, 1.0, P1511::topographicAltitude);
        map("r001", 1.5, 1.5, (a, o) -> P837.rainfallRate(a, o, 0.01));
        map("hr", 1.5, 1.5, P839::rainHeight);
        map("lred1", 1.5, 1.5, (a, o) -> P840.columnarContentReducedLiquid(a, o, 1.0));
        map("nwet50", 1.5, 1.5, (a, o) -> P453.mapWetTermRadioRefractivity(a, o, 50));
        map("rho1", 1.5, 1.5, (a, o) -> P836.surfaceWaterVapourDensity(a, o, 1.0, null));
        map("v1", 1.5, 1.5, (a, o) -> P836.totalWaterVapourContent(a, o, 1.0, null));
        map("tmean", 1.5, 1.5, P1510::surfaceMeanTemperature);
    }

    // ---- line data ----------------------------------------------------------------------------

    interface Row {
        String row(double x);
    }

    static void table(String name, String header, double from, double to, double step, boolean logStep, Row r)
            throws IOException {
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out.resolve(name + ".csv")))) {
            w.println(header);
            if (logStep) {
                int n = (int) step;
                for (int i = 0; i < n; i++) {
                    double x = from * Math.pow(to / from, (double) i / (n - 1));
                    w.println(r.row(x));
                }
            } else {
                for (double x = from; x <= to + 1e-9; x += step) {
                    w.println(r.row(x));
                }
            }
        }
    }

    static void lines() throws IOException {
        // P.838
        table("p838_coeffs", "f,kH,kV,aH,aV", 1, 1000, 121, true, f -> {
            P838.Coefficients h = P838.coefficients(f, 0, 0);
            P838.Coefficients v = P838.coefficients(f, 0, 90);
            return f + "," + h.k() + "," + v.k() + "," + h.alpha() + "," + v.alpha();
        });
        table("p838_gamma", "f,r1,r10,r50,r100", 1, 1000, 121, true, f -> f + ","
            + P838.rainSpecificAttenuation(1, f, 0, 45) + "," + P838.rainSpecificAttenuation(10, f, 0, 45) + ","
            + P838.rainSpecificAttenuation(50, f, 0, 45) + "," + P838.rainSpecificAttenuation(100, f, 0, 45));
        // P.676
        table("p676_gamma", "f,gamma0,gammaw", 1, 350, 0.25, false, f -> f + "," + P676.gamma0Exact(f, 1013.25, 7.5, 288.15)
            + "," + P676.gammaWExact(f, 1013.25, 7.5, 288.15));
        table("p676_slant", "el,annex2,annex1", 5, 90, 1, false, el -> el + ","
            + P676.gaseousAttenuationSlantPath(20, el, 7.5, 1013.25, 288.15, null, null, P676.Mode.APPROX) + ","
            + P676.gaseousAttenuationSlantPath(20, el, 7.5, 1013.25, 288.15, null, null, P676.Mode.EXACT));
        table("p676_heights", "f,h0,hw", 1, 350, 1, false, f -> {
            P676.Heights h = P676.slantInclinedPathEquivalentHeight(f, 1013.25, 7.5, 288.15);
            return f + "," + h.h0() + "," + h.hw();
        });
        // P.840
        table("p840_kl", "f,tm8,t0,t10,t20", 1, 200, 1, false, f -> f + "," + P840.specificAttenuationCoefficient(f, -8)
            + "," + P840.specificAttenuationCoefficient(f, 0) + "," + P840.specificAttenuationCoefficient(f, 10) + ","
            + P840.specificAttenuationCoefficient(f, 20));
        // P.453
        table("p453_es", "t,water,ice", -40, 40, 1, false, t -> t + "," + P453.saturationVapourPressure(t, 1013.25,
            P453.Hydrometeor.WATER) + "," + (t <= 0 ? P453.saturationVapourPressure(t, 1013.25, P453.Hydrometeor.ICE)
            : Double.NaN));
        // P.835
        table("p835_profile", "h,t,p,rho", 0, 100, 0.5, false, h -> h + "," + P835.standardTemperature(h) + ","
            + P835.standardPressure(h) + "," + P835.standardWaterVapourDensity(h));
        // per-city tables
        double[] pList = {0.01, 0.02, 0.03, 0.05, 0.1, 0.2, 0.3, 0.5, 1, 2, 3, 5};
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out.resolve("p837_rate.csv")))) {
            w.println("city,p,rate");
            for (int c = 0; c < CITY.length; c++) {
                for (double p : pList) {
                    w.println(CITY[c] + "," + p + "," + P837.rainfallRate(POS[c][0], POS[c][1], p));
                }
            }
        }
        double[] pv = {0.1, 0.2, 0.3, 0.5, 1, 2, 3, 5, 10, 20, 30, 50, 60, 70, 80, 90, 95, 99};
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out.resolve("p836_p.csv")))) {
            w.println("city,p,rho,v");
            for (int c = 0; c < CITY.length; c++) {
                for (double p : pv) {
                    w.println(CITY[c] + "," + p + "," + P836.surfaceWaterVapourDensity(POS[c][0], POS[c][1], p, null)
                        + "," + P836.totalWaterVapourContent(POS[c][0], POS[c][1], p, null));
                }
            }
        }
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out.resolve("p1510_months.csv")))) {
            w.println("city,month,t");
            for (int c = 0; c < CITY.length; c++) {
                for (int m = 1; m <= 12; m++) {
                    w.println(CITY[c] + "," + m + "," + P1510.surfaceMonthMeanTemperature(POS[c][0], POS[c][1], m));
                }
            }
        }
        // P.618
        double[] pr = {0.001, 0.002, 0.003, 0.005, 0.01, 0.02, 0.03, 0.05, 0.1, 0.2, 0.3, 0.5, 1, 2, 3, 5};
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out.resolve("p618_p.csv")))) {
            w.println("city,p,a");
            for (int c = 0; c < CITY.length; c++) {
                for (double p : pr) {
                    w.println(CITY[c] + "," + p + "," + P618.rainAttenuation(POS[c][0], POS[c][1], 20, 35, null, p, null,
                        45, null));
                }
            }
        }
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out.resolve("p618_f.csv")))) {
            w.println("city,f,a");
            for (int c = 0; c < CITY.length; c++) {
                for (int f = 4; f <= 50; f++) {
                    w.println(CITY[c] + "," + f + "," + P618.rainAttenuation(POS[c][0], POS[c][1], f, 35, null, 0.01,
                        null, 45, null));
                }
            }
        }
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out.resolve("p618_xpd.csv")))) {
            w.println("f,ap,xpd");
            for (int f : new int[] {12, 20, 30}) {
                for (double ap = 1; ap <= 20 + 1e-9; ap += 0.5) {
                    w.println(f + "," + ap + "," + P618.rainCrossPolarizationDiscrimination(ap, f, 35, 0.01, 45));
                }
            }
        }
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out.resolve("p618_scint.csv")))) {
            w.println("f,el,a");
            for (int f : new int[] {12, 20, 30}) {
                for (int el = 5; el <= 90; el++) {
                    w.println(f + "," + el + "," + P618.scintillationAttenuation(48.85, 2.35, f, el, 1.0, 1.2, 0.5,
                        null, null, null, 1000));
                }
            }
        }
        // combined
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out.resolve("itur_f.csv")))) {
            w.println("f,gas,cloud,rain,scint,total");
            for (int f = 4; f <= 40; f++) {
                Itur.Attenuation a = Itur.atmosphericAttenuationSlantPath(48.85, 2.35, f, 35, 0.1, 1.2);
                w.println(f + "," + a.gas() + "," + a.cloud() + "," + a.rain() + "," + a.scintillation() + ","
                    + a.total());
            }
        }
    }

    // ---- worked examples ----------------------------------------------------------------------

    static PrintWriter ex;

    static void put(String key, double v, String fmt) {
        ex.println(key + "=" + String.format(Locale.ROOT, fmt, v));
    }

    static void examples() throws IOException {
        ex = new PrintWriter(Files.newBufferedWriter(out.resolve("examples.txt")));
        double lat = 48.85;
        double lon = 2.35;
        // P.838
        P838.Coefficients c = P838.coefficients(20, 35, 45);
        put("p838.k", c.k(), "%.4f");
        put("p838.alpha", c.alpha(), "%.4f");
        put("p838.gamma25", P838.rainSpecificAttenuation(25, 20, 35, 45), "%.2f");
        put("p838.gamma25_30", P838.rainSpecificAttenuation(25, 30, 35, 45), "%.2f");
        // P.839
        put("p839.h0", P839.isotherm0(lat, lon), "%.2f");
        put("p839.hr", P839.rainHeight(lat, lon), "%.2f");
        put("p839.kourou_hr", P839.rainHeight(5.16, -52.65), "%.2f");
        put("p839.oslo_hr", P839.rainHeight(59.91, 10.75), "%.2f");
        // P.837
        put("p837.r001", P837.rainfallRate(lat, lon, 0.01), "%.1f");
        put("p837.r1", P837.rainfallRate(lat, lon, 1.0), "%.2f");
        put("p837.p0", P837.rainfallProbability(lat, lon), "%.2f");
        put("p837.kourou_r001", P837.rainfallRate(5.16, -52.65, 0.01), "%.1f");
        put("p837.kourou_p0", P837.rainfallProbability(5.16, -52.65), "%.2f");
        put("p837.unav", P837.unavailabilityFromRainfallRate(lat, lon, 20), "%.4f");
        // P.840
        put("p840.lred1", P840.columnarContentReducedLiquid(lat, lon, 1.0), "%.2f");
        put("p840.lred01", P840.columnarContentReducedLiquid(lat, lon, 0.1), "%.2f");
        put("p840.kl20", P840.specificAttenuationCoefficient(20, 0), "%.3f");
        put("p840.cloud", P840.cloudAttenuation(lat, lon, 35, 20, 1.0, null), "%.2f");
        put("p840.kl20_20c", P840.specificAttenuationCoefficient(20, 20), "%.3f");
        // P.453
        put("p453.nwet50", P453.mapWetTermRadioRefractivity(lat, lon, 50), "%.1f");
        put("p453.nwet01", P453.mapWetTermRadioRefractivity(lat, lon, 1), "%.1f");
        double es = P453.saturationVapourPressure(15, 1013.25, P453.Hydrometeor.WATER);
        put("p453.es15", es, "%.3f");
        double e = P453.waterVapourPressure(15, 1013.25, 60);
        put("p453.e", e, "%.3f");
        put("p453.nwet_formula", P453.wetTermRadioRefractivity(e, 15), "%.2f");
        put("p453.ndry", P453.dryTermRadioRefractivity(1013.25 - e, 288.15), "%.2f");
        put("p453.n", P453.radioRefractiveIndex(1013.25 - e, e, 288.15), "%.7f");
        put("p453.dn65", P453.dn65(lat, lon, 1.0), "%.1f");
        // P.836
        put("p836.rho1", P836.surfaceWaterVapourDensity(lat, lon, 1.0, null), "%.2f");
        put("p836.v1", P836.totalWaterVapourContent(lat, lon, 1.0, null), "%.2f");
        put("p836.v1_alt1", P836.totalWaterVapourContent(lat, lon, 1.0, 1.0), "%.2f");
        put("p836.v50", P836.totalWaterVapourContent(lat, lon, 50, null), "%.2f");
        put("p836.v1_p07", P836.totalWaterVapourContent(lat, lon, 0.7, null), "%.2f");
        // P.1510
        put("p1510.t", P1510.surfaceMeanTemperature(lat, lon), "%.2f");
        put("p1510.tjan", P1510.surfaceMonthMeanTemperature(lat, lon, 1), "%.2f");
        put("p1510.tjul", P1510.surfaceMonthMeanTemperature(lat, lon, 7), "%.2f");
        // P.1511
        put("p1511.paris", P1511.topographicAltitude(lat, lon), "%.3f");
        put("p1511.denver", P1511.topographicAltitude(39.74, -104.99), "%.3f");
        put("p1511.sea", P1511.topographicAltitude(45.0, -30.0), "%.9f");
        // P.835
        for (int h : new int[] {0, 5, 11, 20}) {
            put("p835.t" + h, P835.standardTemperature(h), "%.2f");
            put("p835.p" + h, P835.standardPressure(h), "%.3f");
            put("p835.rho" + h, P835.standardWaterVapourDensity(h), "%.3f");
        }
        // P.676
        put("p676.g0_20", P676.gamma0Exact(20, 1013.25, 7.5, 288.15), "%.5f");
        put("p676.gw_20", P676.gammaWExact(20, 1013.25, 7.5, 288.15), "%.5f");
        put("p676.g_60", P676.gammaExact(60, 1013.25, 7.5, 288.15), "%.2f");
        put("p676.g_22", P676.gammaExact(22.235, 1013.25, 7.5, 288.15), "%.4f");
        P676.Heights hh = P676.slantInclinedPathEquivalentHeight(20, 1013.25, 7.5, 288.15);
        put("p676.h0", hh.h0(), "%.2f");
        put("p676.hw", hh.hw(), "%.2f");
        put("p676.slant2", P676.gaseousAttenuationSlantPath(20, 35, 7.5, 1013.25, 288.15, null, null,
            P676.Mode.APPROX), "%.3f");
        put("p676.slant1", P676.gaseousAttenuationSlantPath(20, 35, 7.5, 1013.25, 288.15, null, null,
            P676.Mode.EXACT), "%.3f");
        put("p676.slant2_5", P676.gaseousAttenuationSlantPath(20, 5, 7.5, 1013.25, 288.15, null, null,
            P676.Mode.APPROX), "%.3f");
        put("p676.slant1_5", P676.gaseousAttenuationSlantPath(20, 5, 7.5, 1013.25, 288.15, null, null,
            P676.Mode.EXACT), "%.3f");
        put("p676.zenith_w", P676.zenithWaterVapourAttenuation(lat, lon, 1.0, 30, null, null), "%.3f");
        put("p676.terr", P676.gaseousAttenuationTerrestrialPath(10, 20, 0, 7.5, 1013.25, 288.15, P676.Mode.EXACT),
            "%.3f");
        put("p676.incl", P676.gaseousAttenuationInclinedPath(20, 30, 7.5, 1013.25, 288.15, 0, 8, P676.Mode.APPROX),
            "%.3f");
        // P.618
        put("p618.hs", P1511.topographicAltitude(lat, lon), "%.3f");
        put("p618.hr", P839.rainHeight(lat, lon), "%.2f");
        put("p618.a001", P618.rainAttenuation(lat, lon, 20, 35), "%.2f");
        put("p618.a01", P618.rainAttenuation(lat, lon, 20, 35, null, 0.1, null, 45, null), "%.2f");
        put("p618.a1", P618.rainAttenuation(lat, lon, 20, 35, null, 1.0, null, 45, null), "%.2f");
        put("p618.a001_kourou", P618.rainAttenuation(5.16, -52.65, 20, 35), "%.2f");
        put("p618.a001_r50", P618.rainAttenuation(lat, lon, 20, 35, null, 0.01, 50.0, 45, null), "%.2f");
        put("p618.prob", P618.rainAttenuationProbability(lat, lon, 35, null, null, null), "%.3f");
        put("p618.xpd", P618.rainCrossPolarizationDiscrimination(5, 20, 35, 0.01, 45), "%.1f");
        put("p618.sigma", P618.scintillationAttenuationSigma(lat, lon, 20, 35, 1.2, 0.5, null, null, null, 1000), "%.3f");
        put("p618.scint", P618.scintillationAttenuation(lat, lon, 20, 35, 1.0, 1.2, 0.5, null, null, null, 1000), "%.2f");
        P618.LognormalFit fit = P618.fitRainAttenuationToLognormal(lat, lon, 20, 35, null,
            P837.rainfallProbability(lat, lon), 45);
        put("p618.fit_m", fit.mLnA(), "%.3f");
        put("p618.fit_sigma", fit.sigmaLnA(), "%.3f");
        double dist = Geodesy.distanceKm(48.85, 2.35, 49.05, 2.10);
        put("p618.dist", dist, "%.1f");
        put("p618.diversity", P618.siteDiversityRainOutageProbability(48.85, 2.35, 3, 35, 49.05, 2.10, 3, 35, 20, 45,
            null, null), "%.5f");
        put("p618.diversity_far", P618.siteDiversityRainOutageProbability(48.85, 2.35, 3, 35, 43.30, 5.37, 3, 35, 20,
            45, null, null), "%.5f");
        put("p618.dist_far", Geodesy.distanceKm(48.85, 2.35, 43.30, 5.37), "%.0f");
        // combined
        Itur.Attenuation a = Itur.atmosphericAttenuationSlantPath(lat, lon, 20, 35, 0.1, 1.2);
        put("itur.gas", a.gas(), "%.2f");
        put("itur.cloud", a.cloud(), "%.2f");
        put("itur.rain", a.rain(), "%.2f");
        put("itur.scint", a.scintillation(), "%.2f");
        put("itur.total", a.total(), "%.2f");
        put("itur.sum", a.gas() + a.cloud() + a.rain() + a.scintillation(), "%.2f");
        ex.close();
    }
}
