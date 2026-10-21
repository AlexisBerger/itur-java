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
- P.676-13 is also checked against the official ITU tables (`P676OfficialTablesTest`): γ0/γw/γ (350 rows), Annex 2 A_gas (10 rows) and the Annex 1 sheet (28 GHz, 30°), all within 5e-6.
- The other recommendations were checked once against the ITU tables: all rows pass except P.837-7 rainfall rate for arbitrary `p` (up to 3.4e-4 relative, same as ITU-Rpy) and P.1510-1 temperature (≤ 1.3e-5 K, float32 storage).
- The maps are rounded to float32 on both sides so that the same inputs are compared; `ReferenceValuesTest` checks float64 values at 1e-5.

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

## License

MIT, with the ITU-Rpy copyright notice (see `LICENSE`).
