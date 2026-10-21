# Performance

This page describes the benchmark shipped with the library, the results measured on one machine,
and what they mean in practice.

## The benchmark

`org.itur.perf.PerformanceBenchmark` (in `src/test/java`) is a small, dependency-free
micro-benchmark of the public API. It is deliberately not JMH-based, so it runs wherever the tests
run, without extra dependencies. It is **not** executed by `mvn test`.

```
mvn test-compile
java -Xmx2g -cp target/classes:target/test-classes org.itur.perf.PerformanceBenchmark
```

Options: `--quick` (short rounds, for a smoke run only; the numbers are less stable) and
`--markdown` (prints the tables used below).

It measures, in this order:

1. **First call** of each operation in a fresh JVM: class loading plus reading the map from the
   class path. A map shared with an earlier operation is already loaded, so the order matters (it
   is the order of the tables below).
2. **Warm throughput** of each operation on one thread: 1 s of warm-up, then 5 timed rounds of
   0.5 s; the median round is reported. Inputs cycle through 1,024 fixed pseudo-random points
   (latitude -60° to 70°, any longitude, 10–40 GHz, 10–55° elevation), so results are not constant
   and the JIT cannot fold the work.
3. **Thread scaling** of the total attenuation, from 1 thread up to the number of CPUs.
4. **Heap in use** after a `System.gc()` once all maps are loaded.

`PerformanceSmokeTest` runs with `mvn test`. It checks that every benchmarked operation returns a
finite number, that the measurement loop works, and that concurrent calls return bit-identical
results to sequential calls. It asserts nothing about speed, because timings depend too much on the
machine to be a reliable unit test.

## Results

Measured in a cloud sandbox: 2 vCPU Intel Xeon @ 2.1 GHz, 8 GB RAM, OpenJDK 21.0.12, `-Xmx2g`
(the code targets Java 25, which was not available there). Figures are from one full run; a second
full run agreed within about 15 % on the warm throughput, and by up to a factor of two on the
first-call times, which depend on disk cache and JIT timing.

### First call (fresh JVM)

| Operation | First call |
|---|---:|
| P.838 rain specific attenuation | 0.7 ms |
| P.676 gamma (line by line, 79 lines) | 32.5 ms |
| P.676 slant path, Annex 2 | 0.5 ms |
| P.676 slant path, Annex 1 ray tracing (922 layers) | 17.5 ms |
| P.1511 topographic altitude (bicubic) | 15.8 ms |
| P.1510 mean surface temperature | 5.2 ms |
| P.839 rain height | 2.0 ms |
| P.453 wet refractivity map (p = 50 %) | 7.3 ms |
| P.836 total water vapour content | 10.0 ms |
| P.837 rainfall rate, p = 0.01 % (map) | 146.1 ms |
| P.837 rainfall rate, p = 1 % (monthly, bisection) | 516.1 ms |
| P.840 cloud attenuation | 2.8 ms |
| P.618 rain attenuation (0.01 %) | 1.0 ms |
| P.618 scintillation | 0.0 ms |
| P.618 rain XPD | 0.0 ms |
| Itur total attenuation (gas: Annex 2) | 2.9 ms |
| Itur total attenuation (gas: Annex 1) | 7.0 ms |

### Warm, single thread

| Operation | Time per call | Calls per second |
|---|---:|---:|
| P.838 rain specific attenuation | 203 ns | 4,929,846 |
| P.676 gamma (line by line, 79 lines) | 4.41 µs | 226,846 |
| P.676 slant path, Annex 2 | 4.92 µs | 203,456 |
| P.676 slant path, Annex 1 ray tracing (922 layers) | 4.02 ms | 249 |
| P.1511 topographic altitude (bicubic) | 295 ns | 3,386,658 |
| P.1510 mean surface temperature | 32 ns | 30,788,072 |
| P.839 rain height | 34 ns | 29,666,318 |
| P.453 wet refractivity map (p = 50 %) | 121 ns | 8,243,786 |
| P.836 total water vapour content | 1.86 µs | 537,374 |
| P.837 rainfall rate, p = 0.01 % (map) | 28 ns | 36,098,603 |
| P.837 rainfall rate, p = 1 % (monthly, bisection) | 140.68 µs | 7,108 |
| P.840 cloud attenuation | 89 ns | 11,277,700 |
| P.618 rain attenuation (0.01 %) | 788 ns | 1,269,190 |
| P.618 scintillation | 293 ns | 3,411,669 |
| P.618 rain XPD | 85 ns | 11,761,627 |
| Itur total attenuation (gas: Annex 2) | 13.72 µs | 72,904 |
| Itur total attenuation (gas: Annex 1) | 4.54 ms | 220 |

### Thread scaling (Itur total attenuation, Annex 2 gas)

| Threads | Calls per second | Speed-up |
|---:|---:|---:|
| 1 | 61,992 | 1.00x |
| 2 | 143,347 | 2.31x |

The machine has only 2 vCPUs, so this shows that nothing in the library serialises the threads
(the speed-up is about 2x; the value above 2 is measurement noise from the single-thread
baseline, not a real super-linear effect). It says nothing about scaling beyond 2 cores.

### Memory

77 MB of heap in use with every map loaded (maps are kept as `float` arrays).

## What the numbers mean

- **A typical total attenuation costs about 14 µs per core** (roughly 70,000 per second per core)
  with the Annex 2 gas method. The largest parts are the line-by-line specific attenuation
  (about 5 µs) and the P.836 map look-ups (1.9 µs for the total content, measured on its own; the
  total needs two such look-ups); rain is below 1 µs.
- **The Annex 1 ray-traced gas method is about 300 times slower** (about 4 ms). The cost is
  almost exactly 922 layers × the cost of one line-by-line specific attenuation
  (922 × 4.41 µs ≈ 4.07 ms against 4.02 ms measured). If you need many exact slant paths, the
  specific attenuation of each layer is the place to optimise (see below).
- **P.837 for a probability other than 0.01 %** is 140 µs, because the monthly statistics are
  solved by bisection. At p = 0.01 % it reads the R0.01 map and costs 28 ns.
- **The first call is the expensive one.** Reading and decompressing a map takes 5–20 ms, and
  the 24 monthly P.837 maps take about half a second. A service that must answer its first request
  quickly should make one dummy call per recommendation it uses at start-up.
- **Thread safety.** The computations are static and stateless; the only shared state is the map
  cache, a `ConcurrentHashMap`, so concurrent callers are fine (checked by
  `PerformanceSmokeTest`).

## Tuning notes (not implemented)

These are ideas the measurements point to; none of them is done in the library.

- Annex 1 gas path: each layer evaluates the 79 lines with `Math.pow` and `Math.exp` calls. Terms
  that depend only on the layer (powers of `theta`) or only on the line could be computed once
  instead of once per line and layer, and the layer profiles could be cached per surface water
  vapour density.
- Bulk workloads (many points) could parallelise over points with a parallel stream; the library
  has no shared mutable state to protect.
- The maps could be read in advance on a background thread to remove the first-call latency.

## Limits of these measurements

- One machine, one JVM, a shared cloud CPU: use the numbers to compare operations with each other,
  not as a guarantee. Repeat the run on your hardware.
- A hand-written harness, not JMH: it does not control for every JIT or GC effect. Differences
  below about 15 % between runs are noise.
- Inputs are mid-latitude points; polar points or unusual frequencies were not benchmarked
  separately.
