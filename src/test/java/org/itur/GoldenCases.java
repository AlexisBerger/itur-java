package org.itur;

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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates the golden test vectors produced by {@code tools/generate_golden.py} (ITU-Rpy) against
 * this library. Plain Java (no test framework) so that it can be reused by JUnit and from a main().
 */
final class GoldenCases {

    private GoldenCases() {
    }

    /** Result of checking one CSV file. */
    record Report(String file, int cases, List<String> failures, double worstRel, String worstCase) {
    }

    static Report check(Path csv, double relTol, double absTol) throws IOException {
        String file = csv.getFileName().toString().replace(".csv", "");
        List<String> failures = new ArrayList<>();
        int n = 0;
        double worst = 0;
        String worstCase = "";
        for (String line : Files.readAllLines(csv)) {
            if (line.isBlank()) {
                continue;
            }
            String[] t = line.split(",");
            String fn = t[0];
            double[] args = new double[t.length - 2];
            for (int i = 0; i < args.length; i++) {
                args[i] = Double.parseDouble(t[i + 1]);
            }
            double expected = Double.parseDouble(t[t.length - 1]);
            double got;
            try {
                got = eval(file, fn, args);
            } catch (RuntimeException e) {
                failures.add(line + "  -> " + e);
                n++;
                continue;
            }
            n++;
            double tol = relTol;
            if (!close(got, expected, tol, absTol)) {
                failures.add(line + "  -> got " + got);
            }
            double rel = relErr(got, expected);
            if (rel > worst) {
                worst = rel;
                worstCase = line + " -> " + got;
            }
        }
        return new Report(file, n, failures, worst, worstCase);
    }

    private static double relErr(double got, double expected) {
        if (Double.isNaN(got) && Double.isNaN(expected)) {
            return 0;
        }
        if (Double.isInfinite(expected) && got == expected) {
            return 0;
        }
        if (Double.isNaN(got) || Double.isNaN(expected) || Double.isInfinite(got) || Double.isInfinite(expected)) {
            return Double.POSITIVE_INFINITY;
        }
        double d = Math.abs(got - expected);
        return d == 0 ? 0 : d / Math.max(Math.abs(expected), 1e-12);
    }

    static boolean close(double got, double expected, double relTol, double absTol) {
        if (Double.isNaN(got) || Double.isNaN(expected)) {
            return Double.isNaN(got) && Double.isNaN(expected);
        }
        if (Double.isInfinite(got) || Double.isInfinite(expected)) {
            return got == expected;
        }
        return Math.abs(got - expected) <= absTol + relTol * Math.abs(expected);
    }

    // ---------------------------------------------------------------------------------------
    @SuppressWarnings("deprecation")
    static double eval(String file, String fn, double[] a) {
        return switch (file) {
            case "p838" -> switch (fn) {
                case "k" -> P838.coefficients(a[0], a[1], a[2]).k();
                case "alpha" -> P838.coefficients(a[0], a[1], a[2]).alpha();
                case "gamma" -> P838.rainSpecificAttenuation(a[0], a[1], a[2], a[3]);
                default -> unknown(file, fn);
            };
            case "p835" -> switch (fn) {
                case "T" -> P835.standardTemperature(a[0]);
                case "P" -> P835.standardPressure(a[0]);
                case "rho" -> P835.standardWaterVapourDensity(a[0]);
                case "rho2" -> P835.standardWaterVapourDensity(a[0], a[1], a[2]);
                case "e" -> P835.standardWaterVapourPressure(a[0], 2.0, 7.5);
                default -> unknown(file, fn);
            };
            case "maps" -> switch (fn) {
                case "p839.h0" -> P839.isotherm0(a[0], a[1]);
                case "p839.hr" -> P839.rainHeight(a[0], a[1]);
                case "p1510.T" -> P1510.surfaceMeanTemperature(a[0], a[1]);
                case "p1510.Tm" -> P1510.surfaceMonthMeanTemperature(a[0], a[1], (int) a[2]);
                case "p1511.h" -> P1511.topographicAltitude(a[0], a[1]);
                default -> unknown(file, fn);
            };
            case "p837" -> switch (fn) {
                case "P0" -> P837.rainfallProbability(a[0], a[1]);
                case "R" -> P837.rainfallRate(a[0], a[1], a[2]);
                case "unavail" -> P837.unavailabilityFromRainfallRate(a[0], a[1], a[2]);
                default -> unknown(file, fn);
            };
            case "p836" -> switch (fn) {
                case "rho" -> P836.surfaceWaterVapourDensity(a[0], a[1], a[2], null);
                case "V" -> P836.totalWaterVapourContent(a[0], a[1], a[2], null);
                case "rhoAlt" -> P836.surfaceWaterVapourDensity(a[0], a[1], a[2], a[3]);
                case "VAlt" -> P836.totalWaterVapourContent(a[0], a[1], a[2], a[3]);
                default -> unknown(file, fn);
            };
            case "p840" -> switch (fn) {
                case "Lred" -> P840.columnarContentReducedLiquid(a[0], a[1], a[2]);
                case "A" -> P840.cloudAttenuation(a[0], a[1], a[2], a[3], a[4], null);
                case "m" -> P840.lognormalApproximationCoefficients(a[0], a[1]).m();
                case "sigma" -> P840.lognormalApproximationCoefficients(a[0], a[1]).sigma();
                case "pclw" -> P840.lognormalApproximationCoefficients(a[0], a[1]).pclw();
                case "Kl" -> P840.specificAttenuationCoefficient(a[0], a[1]);
                default -> unknown(file, fn);
            };
            case "p453" -> switch (fn) {
                case "Nwet" -> P453.mapWetTermRadioRefractivity(a[0], a[1], a[2]);
                case "DN65" -> P453.dn65(a[0], a[1], a[2]);
                case "DN1" -> P453.dn1(a[0], a[1], a[2]);
                case "Nwet_f" -> P453.wetTermRadioRefractivity(a[0], a[1]);
                case "Ndry" -> P453.dryTermRadioRefractivity(a[0], a[1]);
                case "n" -> P453.radioRefractiveIndex(a[0], a[1], a[2]);
                case "es_w" -> P453.saturationVapourPressure(a[0], a[1], P453.Hydrometeor.WATER);
                case "es_i" -> P453.saturationVapourPressure(a[0], a[1], P453.Hydrometeor.ICE);
                case "e" -> P453.waterVapourPressure(a[0], a[1], a[2]);
                default -> unknown(file, fn);
            };
            case "p676" -> switch (fn) {
                case "g0e" -> P676.gamma0Exact(a[0], a[1], a[2], a[3]);
                case "gwe" -> P676.gammaWExact(a[0], a[1], a[2], a[3]);
                case "ge" -> P676.gammaExact(a[0], a[1], a[2], a[3]);
                case "g0a" -> P676.gamma0Approx(a[0], a[1], a[2], a[3]);
                case "gwa" -> P676.gammaWApprox(a[0], a[1], a[2], a[3]);
                case "h0" -> P676.slantInclinedPathEquivalentHeight(a[0], a[1], a[2], a[3]).h0();
                case "hw" -> P676.slantInclinedPathEquivalentHeight(a[0], a[1], a[2], a[3]).hw();
                case "slant_a" -> P676.gaseousAttenuationSlantPath(a[0], a[1], a[2], a[3], a[4], null, null, P676.Mode.APPROX);
                case "slant_a_vt" -> P676.gaseousAttenuationSlantPath(a[0], a[1], a[2], a[3], a[4], a[5], a[6], P676.Mode.APPROX);
                case "slant_e" -> P676.gaseousAttenuationSlantPath(a[0], a[1], a[2], a[3], a[4], null, null, P676.Mode.EXACT);
                case "terr_a" -> P676.gaseousAttenuationTerrestrialPath(a[0], a[1], a[2], a[3], a[4], a[5], P676.Mode.APPROX);
                case "terr_e" -> P676.gaseousAttenuationTerrestrialPath(a[0], a[1], a[2], a[3], a[4], a[5], P676.Mode.EXACT);
                case "incl_a" -> P676.gaseousAttenuationInclinedPath(a[0], a[1], a[2], a[3], a[4], a[5], a[6], P676.Mode.APPROX);
                case "incl_e" -> P676.gaseousAttenuationInclinedPath(a[0], a[1], a[2], a[3], a[4], a[5], a[6], P676.Mode.EXACT);
                case "zwv" -> P676.zenithWaterVapourAttenuation(a[0], a[1], a[2], a[3], null, null);
                case "zwv_vt" -> P676.zenithWaterVapourAttenuation(a[0], a[1], a[2], a[3], a[4], a[5]);
                default -> unknown(file, fn);
            };
            case "p618" -> switch (fn) {
                case "rain" -> P618.rainAttenuation(a[0], a[1], a[2], a[3], null, a[4], null, a[5], null);
                case "rain_hs" -> P618.rainAttenuation(a[0], a[1], a[2], a[3], a[4], a[5], null, a[6], null);
                case "rain_r001" -> P618.rainAttenuation(a[0], a[1], a[2], a[3], null, a[4], a[5], a[6], null);
                case "rain_ls" -> P618.rainAttenuation(a[0], a[1], a[2], a[3], null, a[4], null, a[6], a[5]);
                case "scint" -> P618.scintillationAttenuation(a[0], a[1], a[2], a[3], a[4], a[5], a[6], null, null, null, 1000.0);
                case "scint_sigma" -> P618.scintillationAttenuationSigma(a[0], a[1], a[2], a[3], a[5], a[6], null, null, null, 1000.0);
                case "scint_thp" -> P618.scintillationAttenuation(a[0], a[1], a[2], a[3], a[4], a[5], a[6], a[7], a[8], a[9], 1000.0);
                case "scint_thp2" -> P618.scintillationAttenuation(a[0], a[1], a[2], a[3], a[4], a[5], a[6], a[7], a[8], a[9], a[10]);
                case "xpd" -> P618.rainCrossPolarizationDiscrimination(a[0], a[1], a[2], a[3], a[4]);
                default -> unknown(file, fn);
            };
            case "p618q" -> switch (fn) {
                case "prob" -> P618.rainAttenuationProbability(a[0], a[1], a[2], null, null, null);
                case "fit_sigma" -> P618.fitRainAttenuationToLognormal(a[0], a[1], a[2], a[3], 0.1, a[4], a[5]).sigmaLnA();
                case "fit_m" -> P618.fitRainAttenuationToLognormal(a[0], a[1], a[2], a[3], 0.1, a[4], a[5]).mLnA();
                case "divers" -> P618.siteDiversityRainOutageProbability(a[0], a[1], a[2], a[3], a[4], a[5], a[6], a[7],
                    a[8], a[9], 0.1, 0.1);
                default -> unknown(file, fn);
            };
            case "itur" -> {
                P676.Mode mode = fn.equals("itur.total_exact") ? P676.Mode.EXACT : P676.Mode.APPROX;
                Itur.Options o = new Itur.Options(null, null, null, null, null, null, null, null, 0.5, 1000.0, 45.0,
                    mode, true, true, true, true);
                Itur.Attenuation r = Itur.atmosphericAttenuationSlantPath(a[0], a[1], a[2], a[3], a[4], a[5], o);
                yield switch (fn) {
                    case "itur.gas" -> r.gas();
                    case "itur.cloud" -> r.cloud();
                    case "itur.rain" -> r.rain();
                    case "itur.scint" -> r.scintillation();
                    case "itur.total", "itur.total_exact" -> r.total();
                    default -> unknown(file, fn);
                };
            }
            default -> unknown(file, fn);
        };
    }

    private static double unknown(String file, String fn) {
        throw new IllegalArgumentException("No evaluator for " + file + "/" + fn);
    }

    /** Command line runner: {@code GoldenCases <golden-dir> [relTol]}. */
    public static void main(String[] args) throws IOException {
        Path dir = Path.of(args[0]);
        double tol = args.length > 1 ? Double.parseDouble(args[1]) : 1e-9;
        boolean ok = true;
        try (var files = Files.list(dir)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".csv")).sorted().toList()) {
                String nm = p.getFileName().toString();
                double t = nm.startsWith("p618q") ? Math.max(tol, 2e-5) : tol;
                Report r = check(p, t, 1e-12);
                System.out.printf("%-12s %5d cases, %4d failures, worst rel err %.2e%n", r.file(), r.cases(),
                    r.failures().size(), r.worstRel());
                r.failures().stream().limit(6).forEach(f -> System.out.println("    FAIL " + f));
                if (!r.failures().isEmpty()) {
                    System.out.println("    worst: " + r.worstCase());
                    ok = false;
                }
            }
        }
        System.exit(ok ? 0 : 1);
    }
}
