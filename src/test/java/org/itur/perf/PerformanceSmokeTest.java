package org.itur.perf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * Keeps the benchmark from rotting: every benchmarked operation must run and return a finite
 * number, and the measurement loop must report a positive time. It asserts nothing about speed
 * (timings are too machine-dependent for a unit test); run {@link PerformanceBenchmark} for numbers.
 */
class PerformanceSmokeTest {

    @Test
    void everyOperationReturnsFiniteValues() {
        for (PerformanceBenchmark.Case c : PerformanceBenchmark.cases()) {
            for (int i = 0; i < 8; i++) {
                double v = c.op().run(i);
                assertTrue(Double.isFinite(v), c.name() + " returned " + v + " for point " + i);
            }
        }
    }

    @Test
    void measurementReportsPositiveTimes() {
        PerformanceBenchmark.Result r = PerformanceBenchmark.measure("p838",
            i -> PerformanceBenchmark.cases().get(0).op().run(i), 1_000_000L, 1_000_000L, 3);
        assertTrue(r.medianNs() > 0 && r.bestNs() > 0);
        assertTrue(r.bestNs() <= r.medianNs());
        assertEquals(1e9 / r.medianNs(), r.opsPerSecond(), 1e-6);
    }

    @Test
    void concurrentCallsGiveTheSameResultsAsSequentialCalls() {
        int n = 64;
        double[] sequential = new double[n];
        for (int i = 0; i < n; i++) {
            sequential[i] = PerformanceBenchmark.total(i, PerformanceBenchmark.DEFAULT_OPTIONS);
        }
        double[] parallel = new double[n];
        IntStream.range(0, n).parallel()
            .forEach(i -> parallel[i] = PerformanceBenchmark.total(i, PerformanceBenchmark.DEFAULT_OPTIONS));
        for (int i = 0; i < n; i++) {
            assertEquals(sequential[i], parallel[i], 0.0, "point " + i);
        }
    }
}
