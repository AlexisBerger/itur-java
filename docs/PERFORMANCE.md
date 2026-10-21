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

`tools/benchmark_itur_rpy.py` times ITU-Rpy on the same operations and the same 1,024 inputs (see
[Comparison with ITU-Rpy](#comparison-with-itu-rpy)).

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

## Comparison with ITU-Rpy

The library is a port of ITU-Rpy, so ITU-Rpy is the natural baseline. `tools/benchmark_itur_rpy.py`
runs the same operations on the same inputs (the benchmark writes them with `--dump-inputs`):

```
java -cp target/classes:target/test-classes org.itur.perf.PerformanceBenchmark --dump-inputs points.csv
python3 tools/benchmark_itur_rpy.py points.csv --markdown
```

It reports two figures for ITU-Rpy:

- **scalar**: one Python call per point, which is what a caller of this library's scalar API does;
- **vectorized**: one call on 128 numpy-array points, divided by 128 (how ITU-Rpy is meant to be
  used). Only 128 points are used because the ray-traced operations are `np.vectorize` loops that
  need seconds per call on 1,024 points. ITU-Rpy 0.4.0 raised an error for P.838 with array
  arguments in this setup, so that cell is `n/a`.

**Read these caveats first.**

- ITU-Rpy normally needs `astropy` and `pyproj`, which could not be installed in the sandbox (no
  access to the package index). They were replaced by small stand-ins: a minimal units class and a
  geodesic-distance function. Real `astropy` quantities add overhead to every call, so **real
  ITU-Rpy is slower than the figures below, not faster**.
- Python 3.13.16, numpy 2.5.3, scipy 1.18.1, ITU-Rpy 0.4.0, on the same machine as the Java
  figures (OpenJDK 21, not 25). The Java column is the full run reported above. The Python figures
  come from a single run (median of 5 rounds), so the run-to-run variation was not measured.
- The workloads are equivalent, not byte-identical: ITU-Rpy's total attenuation, for example,
  goes through its own defaults and input handling.
- ITU-Rpy ran on its original float64 data; this library on float32 maps (the differences are
  about 1e-7 relative, see [VALIDATION.md](VALIDATION.md)).

### Warm

| Operation | Java | ITU-Rpy, scalar calls | Java faster by | ITU-Rpy, vectorized (per point) | Java faster by |
|---|---:|---:|---:|---:|---:|
| P.838 rain specific attenuation | 203 ns | 47.74 µs | 235x | n/a (error) | n/a |
| P.676 gamma (line by line, 79 lines) | 4.41 µs | 127.09 µs | 29x | 51.41 µs | 12x |
| P.676 slant path, Annex 2 | 4.92 µs | 261.17 µs | 53x | 80.09 µs | 16x |
| P.676 slant path, Annex 1 ray tracing (922 layers) | 4.02 ms | 127.42 ms | 32x | 63.75 ms | 16x |
| P.1511 topographic altitude (bicubic) | 295 ns | 491.63 µs | 1667x | 4.96 µs | 17x |
| P.1510 mean surface temperature | 32 ns | 44.46 µs | 1389x | 473 ns | 15x |
| P.839 rain height | 34 ns | 38.74 µs | 1139x | 343 ns | 10x |
| P.453 wet refractivity map (p = 50 %) | 121 ns | 114.10 µs | 943x | 1.12 µs | 9.3x |
| P.836 total water vapour content | 1.86 µs | 763.63 µs | 411x | 10.13 µs | 5.4x |
| P.837 rainfall rate, p = 0.01 % (map) | 28 ns | 78.09 µs | 2789x | 666 ns | 24x |
| P.837 rainfall rate, p = 1 % (monthly, bisection) | 140.68 µs | 4.31 ms | 31x | 2.62 ms | 19x |
| P.840 cloud attenuation | 89 ns | 131.83 µs | 1481x | 4.79 µs | 54x |
| P.618 rain attenuation (0.01 %) | 788 ns | 936.31 µs | 1188x | 1.03 ms | 1307x |
| P.618 scintillation | 293 ns | 214.48 µs | 732x | 274.69 µs | 938x |
| P.618 rain XPD | 85 ns | 39.26 µs | 462x | 4.84 µs | 57x |
| Itur total attenuation (gas: Annex 2) | 13.72 µs | 4.35 ms | 317x | 887.72 µs | 65x |
| Itur total attenuation (gas: Annex 1) | 4.54 ms | 125.60 ms | 28x | 60.24 ms | 13x |

### First call

| Operation | Java | ITU-Rpy |
|---|---:|---:|
| P.838 rain specific attenuation | 0.7 ms | 0.3 ms |
| P.676 gamma (line by line, 79 lines) | 32.5 ms | 0.4 ms |
| P.676 slant path, Annex 2 | 0.5 ms | 0.4 ms |
| P.676 slant path, Annex 1 ray tracing (922 layers) | 17.5 ms | 127.5 ms |
| P.1511 topographic altitude (bicubic) | 15.8 ms | 20.2 ms |
| P.1510 mean surface temperature | 5.2 ms | 9.4 ms |
| P.839 rain height | 2.0 ms | 3.0 ms |
| P.453 wet refractivity map (p = 50 %) | 7.3 ms | 110.7 ms |
| P.836 total water vapour content | 10.0 ms | 211.8 ms |
| P.837 rainfall rate, p = 0.01 % (map) | 146.1 ms | 321.5 ms |
| P.837 rainfall rate, p = 1 % (monthly, bisection) | 516.1 ms | 920.1 ms |
| P.840 cloud attenuation | 2.8 ms | 73.7 ms |
| P.618 rain attenuation (0.01 %) | 1.0 ms | 1.2 ms |
| P.618 scintillation | 0.0 ms | 0.3 ms |
| P.618 rain XPD | 0.0 ms | 0.1 ms |
| Itur total attenuation (gas: Annex 2) | 2.9 ms | 104.9 ms |
| Itur total attenuation (gas: Annex 1) | 7.0 ms | 156.1 ms |

### What the comparison shows

- **Scalar calls: Java is 28x to about 2,800x faster** on every operation. Most of the gap on the
  cheap operations is the per-call overhead of the Python stack (array conversion, unit handling,
  interpolator set-up), not the arithmetic itself; the closest ratios are on the operations that
  are dominated by arithmetic (the line-by-line sum, the ray tracing and the bisection, 28x to 32x).
- **Vectorized calls narrow the gap but do not close it: Java is 5x to 65x faster** per point
  on the operations that accept arrays, with two exceptions where ITU-Rpy's "vectorized" call is
  not faster than its scalar one (P.618 rain attenuation and scintillation, about 1,000x in Java's
  favour). The smallest ratio is P.836 (5.4x). With larger arrays the vectorized figures could
  improve further; only 128 points were measured.
- **Warm Java is never slower than ITU-Rpy** on this set.
- **First call: Java is not always faster.** Three operations are slower on their first call in
  Java: the line-by-line specific attenuation (32.5 ms against 0.4 ms, which is class loading and
  JIT warm-up since the tables are tiny), P.838 (0.7 ms against 0.3 ms) and the Annex 2 slant path
  (0.5 ms against 0.4 ms). Everything else loads faster in Java, by up to about 36x for the total
  attenuation (2.9 ms against 104.9 ms) and 15x to 26x for the maps ITU-Rpy has to turn into
  interpolators (P.453, P.836, P.840).
- This is a speed comparison only. It says nothing about numerical agreement, which is
  covered in [VALIDATION.md](VALIDATION.md).

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

- The ITU-Rpy figures use stand-ins for `astropy` and `pyproj` and come from a single run (see
  the caveats in the comparison).
- One machine, one JVM, a shared cloud CPU: use the numbers to compare operations with each other,
  not as a guarantee. Repeat the run on your hardware.
- A hand-written harness, not JMH: it does not control for every JIT or GC effect. Differences
  below about 15 % between runs are noise.
- Inputs are mid-latitude points; polar points or unusual frequencies were not benchmarked
  separately.
