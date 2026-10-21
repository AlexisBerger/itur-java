package org.itur.perf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.DoubleAdder;
import org.itur.Itur;
import org.itur.models.P1510;
import org.itur.models.P1511;
import org.itur.models.P453;
import org.itur.models.P618;
import org.itur.models.P676;
import org.itur.models.P836;
import org.itur.models.P837;
import org.itur.models.P838;
import org.itur.models.P839;
import org.itur.models.P840;

/**
 * Dependency-free micro-benchmark of the public API (no JMH, so it runs anywhere the tests run).
 *
 * <p>Run it from the build output:
 * <pre>
 *   java -Xmx2g -cp target/classes:target/test-classes org.itur.perf.PerformanceBenchmark [--quick] [--markdown]
 *   java -cp target/classes:target/test-classes org.itur.perf.PerformanceBenchmark --dump-inputs points.csv
 * </pre>
 *
 * <p>{@code --dump-inputs} writes the 1,024 input points (lat, lon, f, el, ap) used by the
 * benchmark so that {@code tools/benchmark_itur_rpy.py} can time ITU-Rpy on the same inputs.
 *
 * <p>It reports, in this order:
 * <ol>
 *   <li><b>First call</b> of each operation in a fresh JVM (class loading plus reading the map it
 *       needs from the class path). Maps shared with an earlier operation are already loaded, so the
 *       order matters and is the one printed.</li>
 *   <li><b>Warm throughput</b> of each operation, single thread: warm-up, then several timed
 *       rounds; the median is reported. Inputs cycle through 1,024 fixed pseudo-random points so the
 *       JIT cannot fold the computation.</li>
 *   <li><b>Thread scaling</b> of the combined total-attenuation computation.</li>
 *   <li><b>Memory</b>: heap in use after everything is loaded.</li>
 * </ol>
 * These are indicative numbers, not a substitute for JMH: they depend on the JVM, the CPU and the
 * load of the machine.
 */
public final class PerformanceBenchmark {

    private static final int POINTS = 1024;
    private static final int MASK = POINTS - 1;

    /** One benchmarked operation: computes a result for the i-th input point. */
    @FunctionalInterface
    interface Op {
        double run(int i);
    }

    /** A named operation. */
    record Case(String name, Op op) {
    }

    /** Warm result of one operation. */
    public record Result(String name, double medianNs, double bestNs) {
        public double opsPerSecond() {
            return 1e9 / medianNs;
        }
    }

    static final double[] LAT = new double[POINTS];
    static final double[] LON = new double[POINTS];
    static final double[] FREQ = new double[POINTS];
    static final double[] ELEV = new double[POINTS];
    static final double[] AP = new double[POINTS];

    static {
        Random rnd = new Random(20261021L);
        for (int i = 0; i < POINTS; i++) {
            LAT[i] = -60 + 130 * rnd.nextDouble();
            LON[i] = -180 + 360 * rnd.nextDouble();
            FREQ[i] = 10 + 30 * rnd.nextDouble();
            ELEV[i] = 10 + 45 * rnd.nextDouble();
            AP[i] = 1 + 10 * rnd.nextDouble();
        }
    }

    /** Defeats dead-code elimination. */
    static volatile double sink;

    static final Itur.Options EXACT_GAS = Itur.Options.builder().gasMode(P676.Mode.EXACT).build();
    static final Itur.Options DEFAULT_OPTIONS = Itur.Options.defaults();

    /** The operations, in the order of the first-call measurement. */
    static List<Case> cases() {
        List<Case> c = new ArrayList<>();
        c.add(new Case("P.838 rain specific attenuation",
            i -> P838.rainSpecificAttenuation(20 + AP[i], FREQ[i], ELEV[i], 45)));
        c.add(new Case("P.676 gamma (line by line, 79 lines)",
            i -> P676.gammaExact(FREQ[i], 1013.25, 7.5 + AP[i], 288.15)));
        c.add(new Case("P.676 slant path, Annex 2",
            i -> P676.gaseousAttenuationSlantPath(FREQ[i], ELEV[i], 7.5 + AP[i], 1013.25, 288.15, null, null,
                P676.Mode.APPROX)));
        c.add(new Case("P.676 slant path, Annex 1 ray tracing (922 layers)",
            i -> P676.gaseousAttenuationSlantPath(FREQ[i], ELEV[i], 7.5 + AP[i], 1013.25, 288.15, null, null,
                P676.Mode.EXACT)));
        c.add(new Case("P.1511 topographic altitude (bicubic)", i -> P1511.topographicAltitude(LAT[i], LON[i])));
        c.add(new Case("P.1510 mean surface temperature", i -> P1510.surfaceMeanTemperature(LAT[i], LON[i])));
        c.add(new Case("P.839 rain height", i -> P839.rainHeight(LAT[i], LON[i])));
        c.add(new Case("P.453 wet refractivity map (p = 50 %)",
            i -> P453.mapWetTermRadioRefractivity(LAT[i], LON[i], 50)));
        c.add(new Case("P.836 total water vapour content",
            i -> P836.totalWaterVapourContent(LAT[i], LON[i], 1.0, null)));
        c.add(new Case("P.837 rainfall rate, p = 0.01 % (map)", i -> P837.rainfallRate(LAT[i], LON[i], 0.01)));
        c.add(new Case("P.837 rainfall rate, p = 1 % (monthly, bisection)",
            i -> P837.rainfallRate(LAT[i], LON[i], 1.0)));
        c.add(new Case("P.840 cloud attenuation",
            i -> P840.cloudAttenuation(LAT[i], LON[i], ELEV[i], FREQ[i], 1.0, null)));
        c.add(new Case("P.618 rain attenuation (0.01 %)",
            i -> P618.rainAttenuation(LAT[i], LON[i], FREQ[i], ELEV[i])));
        c.add(new Case("P.618 scintillation",
            i -> P618.scintillationAttenuation(LAT[i], LON[i], FREQ[i], ELEV[i], 1.0, 1.2, 0.5, null, null, null,
                1000.0)));
        c.add(new Case("P.618 rain XPD",
            i -> P618.rainCrossPolarizationDiscrimination(AP[i], FREQ[i], ELEV[i], 0.01, 45)));
        c.add(new Case("Itur total attenuation (gas: Annex 2)", i -> total(i, DEFAULT_OPTIONS)));
        c.add(new Case("Itur total attenuation (gas: Annex 1)", i -> total(i, EXACT_GAS)));
        return c;
    }

    static double total(int i, Itur.Options options) {
        return Itur.atmosphericAttenuationSlantPath(LAT[i], LON[i], FREQ[i], ELEV[i], 0.1, 1.2, options).total();
    }

    /** Runs {@code op} repeatedly for about {@code budgetNanos}; returns the mean time per call in ns. */
    static double run(Op op, long budgetNanos) {
        long start = System.nanoTime();
        long calls = 0;
        double acc = 0;
        int i = 0;
        long now;
        do {
            for (int k = 0; k < 8; k++) {
                acc += op.run(i++ & MASK);
            }
            calls += 8;
            now = System.nanoTime();
        } while (now - start < budgetNanos);
        sink = acc;
        return (double) (now - start) / calls;
    }

    /** Warm-up, then {@code rounds} timed rounds; returns the median and the best round. */
    public static Result measure(String name, Op op, long warmupNanos, long roundNanos, int rounds) {
        run(op, warmupNanos);
        double[] ns = new double[rounds];
        for (int r = 0; r < rounds; r++) {
            ns[r] = run(op, roundNanos);
        }
        Arrays.sort(ns);
        return new Result(name, ns[rounds / 2], ns[0]);
    }

    /** Aggregate calls per second of {@code threads} threads running the total attenuation. */
    static double throughput(int threads, long budgetNanos) throws InterruptedException {
        DoubleAdder calls = new DoubleAdder();
        List<Thread> ts = new ArrayList<>();
        long deadline = System.nanoTime() + budgetNanos;
        for (int t = 0; t < threads; t++) {
            final int offset = t * 97;
            Thread th = new Thread(() -> {
                long n = 0;
                double acc = 0;
                int i = offset;
                while (System.nanoTime() < deadline) {
                    for (int k = 0; k < 8; k++) {
                        acc += total(i++ & MASK, DEFAULT_OPTIONS);
                    }
                    n += 8;
                }
                sink = acc;
                calls.add(n);
            });
            ts.add(th);
        }
        long start = System.nanoTime();
        ts.forEach(Thread::start);
        for (Thread th : ts) {
            th.join();
        }
        return calls.sum() / ((System.nanoTime() - start) / 1e9);
    }

    /** Writes the benchmark input points as CSV (header: lat,lon,f,el,ap). */
    static void dumpInputs(java.nio.file.Path file) throws java.io.IOException {
        StringBuilder sb = new StringBuilder("lat,lon,f,el,ap\n");
        for (int i = 0; i < POINTS; i++) {
            sb.append(LAT[i]).append(',').append(LON[i]).append(',').append(FREQ[i]).append(',')
                .append(ELEV[i]).append(',').append(AP[i]).append('\n');
        }
        java.nio.file.Files.writeString(file, sb.toString());
    }

    static String human(double ns) {
        if (ns >= 1e6) {
            return String.format("%.2f ms", ns / 1e6);
        }
        if (ns >= 1e3) {
            return String.format("%.2f us", ns / 1e3);
        }
        return String.format("%.0f ns", ns);
    }

    public static void main(String[] args) throws Exception {
        List<String> a = Arrays.asList(args);
        boolean quick = a.contains("--quick");
        boolean md = a.contains("--markdown");
        int dump = a.indexOf("--dump-inputs");
        if (dump >= 0) {
            dumpInputs(java.nio.file.Path.of(a.get(dump + 1)));
            return;
        }
        long warm = quick ? 100_000_000L : 1_000_000_000L;
        long round = quick ? 50_000_000L : 500_000_000L;
        int rounds = quick ? 3 : 5;

        Runtime rt = Runtime.getRuntime();
        System.out.printf("%s, %d CPU, max heap %d MB%n", System.getProperty("java.vm.name") + " "
            + System.getProperty("java.version"), rt.availableProcessors(), rt.maxMemory() >> 20);

        List<Case> cases = cases();

        // 1. first call, in a fresh JVM
        System.out.println("\nFirst call (class loading + map loading)");
        for (Case c : cases) {
            long t0 = System.nanoTime();
            sink = c.op().run(0);
            double ms = (System.nanoTime() - t0) / 1e6;
            System.out.printf(md ? "| %s | %.1f ms |%n" : "  %-58s %8.1f ms%n", c.name(), ms);
        }

        // 2. warm throughput
        System.out.println("\nWarm, single thread (median of " + rounds + " rounds)");
        if (md) {
            System.out.println("| Operation | Time per call | Calls per second |\n|---|---:|---:|");
        }
        for (Case c : cases) {
            Result r = measure(c.name(), c.op(), warm, round, rounds);
            System.out.printf(md ? "| %s | %s | %s |%n" : "  %-58s %10s %14s/s%n", r.name(), human(r.medianNs()),
                String.format("%,.0f", r.opsPerSecond()));
        }

        // 3. threads
        System.out.println("\nThread scaling, Itur total attenuation (Annex 2 gas)");
        int max = rt.availableProcessors();
        double base = 0;
        for (int t = 1; t <= max; t *= 2) {
            double ops = throughput(t, round * 2);
            if (t == 1) {
                base = ops;
            }
            System.out.printf(md ? "| %d | %,.0f | %.2fx |%n" : "  %2d thread(s) %,14.0f calls/s  (x%.2f)%n", t, ops,
                ops / base);
            if (t * 2 > max && t != max) {
                t = max / 2;
            }
        }

        // 4. memory
        System.gc();
        System.out.printf("%nHeap in use with all maps loaded: %d MB%n", (rt.totalMemory() - rt.freeMemory()) >> 20);
    }
}
