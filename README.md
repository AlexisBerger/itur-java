# itur-java

Java implementation (Java 25 target, no runtime dependencies) of ITU-R radiowave propagation models, ported from [ITU-Rpy 0.4.0](https://github.com/inigodelportillo/ITU-Rpy) and validated against it and against the ITU validation tables.

## Recommendations

| Rec. | Subject | Class |
|---|---|---|
| P.618-13 | Rain attenuation, scintillation, XPD, site diversity | `P618` |
| P.676-13 | Gaseous attenuation (line by line, Annex 2 equivalent heights, ray-traced exact slant path) | `P676` |
| P.837-7 | Rainfall rate | `P837` |
| P.838-3 | Rain specific attenuation | `P838` |
| P.839-4 | Rain height / 0 °C isotherm | `P839` |
| P.840-7 | Clouds (reduced liquid water content, coefficient, attenuation) | `P840` |
| P.453-13 | Radio refractive index | `P453` |
| P.836-6 | Water vapour (density, total content) | `P836` |
| P.1510-1 | Mean surface temperature | `P1510` |
| P.1511-1 | Topographic altitude (bicubic) | `P1511` |
| (P.835) | Standard atmosphere (dependency of P.676/P.836) | `P835` |

> **Versions**: ITU-Rpy already has later versions of P.618 (-14), P.840 (-9) and P.1511 (-2). The requested versions are implemented as they are; upgrading is possible (the data is available).

## Packages

| Package | Contents |
|---|---|
| `org.itur` | `Itur`: combined computation (gases + clouds + rain + scintillation) |
| `org.itur.models` | one class per recommendation (`P618`, `P676`, `P837`, …) |
| `org.itur.grid` | `Grid`: lat/lon maps, bilinear and bicubic interpolation |
| `org.itur.util` | `Special` (special functions, bisection), `Geodesy` |

`Itur.atmosphericAttenuationSlantPath(...)` combines gases, clouds, rain and scintillation (`A = Ag + sqrt((Ar+Ac)² + As²)`).

## Documentation

One page per recommendation, with equations, API, worked examples and figures computed from this library: [docs/recommendations/](docs/recommendations/README.md). Also [validation](docs/VALIDATION.md) and [performance](docs/PERFORMANCE.md).

| Pages | |
|---|---|
| [P.618](docs/recommendations/P618.md) · [P.676](docs/recommendations/P676.md) · [P.837](docs/recommendations/P837.md) · [P.838](docs/recommendations/P838.md) · [P.839](docs/recommendations/P839.md) · [P.840](docs/recommendations/P840.md) | [P.453](docs/recommendations/P453.md) · [P.836](docs/recommendations/P836.md) · [P.1510](docs/recommendations/P1510.md) · [P.1511](docs/recommendations/P1511.md) · [P.835](docs/recommendations/P835.md) · [Combined](docs/recommendations/ITUR.md) |

## Quick start

```java
import org.itur.Itur;

var a = Itur.atmosphericAttenuationSlantPath(48.85, 2.35, 20.0, 35.0, 0.1, 1.2, Itur.Options.defaults());
System.out.println(a.total() + " dB");
```

Runnable example: `java -cp target/classes examples/LinkBudgetExample.java`
(Paris, 20 GHz, 35°, p = 0.1 %, 1.2 m dish: gases 0.95; clouds 0.78; rain 4.14; scintillation 0.45; total 5.89 dB).

Conventions: latitude/longitude in degrees, frequency in GHz, heights in km, probabilities `p` in %, scalar API, nullable `Double` = optional value (derived from the ITU maps when `null`).

## Data

The ITU maps are converted from `.npz` to `.grid` (gzip, float32, big-endian) in `src/main/resources/itur/data` (~80 MB).
- `tools/convert_data.py`: regenerates the `.grid` files from ITU-Rpy.
- `tools/generate_golden.py`: regenerates the reference vectors (`src/test/resources/golden/*.csv`) from ITU-Rpy.

The ITU data is subject to the ITU's terms of use.

## Validation

See [docs/VALIDATION.md](docs/VALIDATION.md) for the strategy, the full results and the known differences. In short:

- 11,143 golden vectors (cities, random points, boundary points: poles, ±180°, 360°) compared with ITU-Rpy at a relative tolerance of 1e-9: all pass, worst error ≤ 1e-11 except the P.618 quantile functions (≤ 2.4e-7, tolerance 2e-5).
- All the recommendations are checked against the official ITU tables as part of `mvn test` (`P676OfficialTablesTest`, `OfficialTablesTest`): within 5e-6, except P.837-7 for p ≠ 0.01 % and P.1510-1 (documented tolerances).
- The maps are rounded to float32 on both sides so that the same inputs are compared; `ReferenceValuesTest` checks float64 values at 1e-5.

## Performance

A typical total-attenuation computation takes about 13 µs per call and per core (about 4 ms with the ray-traced Annex 1 gas method); the first call in a JVM loads the maps and takes a few milliseconds up to about half a second. On the same inputs and machine, scalar calls were 28x to about 2,800x faster than ITU-Rpy's, and 5x to 65x faster than its vectorized calls where it accepts arrays (ITU-Rpy ran with stand-ins for astropy, so real ITU-Rpy is slower still). See [docs/PERFORMANCE.md](docs/PERFORMANCE.md) for the benchmark, the full results, the caveats and tuning notes.

## Known differences (inherited from ITU-Rpy)

- `P0` of P.618 is in %.
- P.453 wet term: temperature in °C.
- `NaN` at the grid limits (e.g. lat = -90° for P.836/P.453), as in ITU-Rpy.
- P.840: error for p > 99 %; P.840-7 has no lognormal attenuation.
- P.835: reference standard atmosphere only.
- P.676-13: no explicit approximation of γ0/γw; `gamma0Approx`/`gammaWApprox` (deprecated) return the total line-by-line γ, as ITU-Rpy does; the `APPROX`/`EXACT` modes of the terrestrial path are therefore identical.

## Build

```
mvn test
```

Not built with Maven/Java 25 in the development environment (network blocked): validated with javac 21, a hand-assembled jar and a small JUnit stand-in runner. A first `mvn test` on your side is the final check.

Regenerate the documentation figures: `java -cp target/classes tools/DocFigureData.java build/doc-data && python3 tools/plot_doc_figures.py build/doc-data docs/img` (needs matplotlib, pandas).

Coverage: `mvn verify` writes the JaCoCo report to `target/site/jacoco/index.html`.

Benchmark (not run by `mvn test`): `java -Xmx2g -cp target/classes:target/test-classes org.itur.perf.PerformanceBenchmark [--quick] [--markdown]`; ITU-Rpy comparison: `tools/benchmark_itur_rpy.py`.

## License

MIT, with the ITU-Rpy copyright notice (see `LICENSE`).
