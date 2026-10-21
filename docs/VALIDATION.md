# Validation

This page describes how the library is validated and what the results are. The reference
implementation is [ITU-Rpy](https://github.com/inigodelportillo/ITU-Rpy) 0.4.0; the official
validation tables of ITU-R Study Group 3 (as distributed in ITU-Rpy's `test/test_data`) are used as
an independent check.

## Strategy

Three layers, from the strictest to the most independent:

1. **Unit tests** check the numerical building blocks (`SpecialTest`, `GridTest`, `GeodesyTest`),
   each recommendation on its own (`P835Test`, `P838Test`, `P453Test`, `P836Test`, `P837Test`,
   `P840Test`, `P676Test`, `P618Test`, `MapModelsTest` for P.839/P.1510/P.1511) and the combination
   (`ItuTest`). They use values that do not come from ITU-Rpy where possible (the table of P.838-3,
   the US Standard Atmosphere, the WGS-84 ellipsoid) and otherwise physical invariants:
   monotonic behaviour, limiting cases, identities between functions, longitude conventions.
   `ReferenceValuesTest` adds a handful of end-to-end values computed with ITU-Rpy on its original
   float64 data, and the rejection of invalid inputs.
2. **Golden vectors** compare every public function with ITU-Rpy on a large, fixed set of inputs.
3. **Official ITU tables** compare the results with the numbers published by the ITU for each
   recommendation.

### Golden vectors

`tools/generate_golden.py` runs ITU-Rpy and writes `src/test/resources/golden/*.csv`, one line per
call: `function,arguments...,expected`. `GoldenTest` recomputes each line in Java and compares.

- **Inputs.** 20 cities, 40 random points (fixed seed) and 13 boundary points (poles, ±180°, 360°,
  points just inside the grid edges), combined with grids of frequencies, elevations, probabilities
  and atmospheres chosen per recommendation.
- **Same data on both sides.** The maps are stored as float32 in this library, so the generator
  rounds ITU-Rpy's data arrays to float32 before computing. Both implementations therefore see
  identical inputs, and the comparison measures the algorithms rather than the storage format.
  With pristine float64 data the differences are about 1e-7 relative.
- **Versions.** ITU-Rpy is switched to the versions implemented here (P.618-13, P.676-13, P.837-7,
  P.838-3, P.839-4, P.840-7, P.453-13, P.836-6, P.1510-1, P.1511-1, P.835-6).
- **Tolerance.** Relative 1e-9 by default (`-Ditur.tolerance=...` to change it), with an absolute
  floor for values near zero. Files computed by ITU-Rpy with adaptive numerical integration
  (`p618q`: lognormal fit and site diversity) use at least 2e-5.
- **Boundary behaviour is part of the contract.** ITU-Rpy returns `NaN` in a few places (for
  example latitude -90° in the P.836 and P.453 maps) and wraps longitudes in specific ways; the
  boundary points make sure the Java code reproduces these cases rather than only the easy ones.

### Official ITU tables

The tables are small spreadsheets published with each recommendation (inputs and the ITU's own
expected outputs). The pass criterion is the one used by ITU-Rpy's test suite: absolute error
below 5e-6 (five decimal places).

- `P676OfficialTablesTest` runs the P.676-13 tables as part of `mvn test`.
- `OfficialTablesTest` runs the tables of all the other recommendations (P.453, P.618, P.836, P.837,
  P.838, P.839, P.840, P.1510, P.1511) as part of `mvn test`. The tables are in
  `src/test/resources/official/<recommendation>`; `OfficialTable` reads them. Two tables use a
  documented, looser tolerance (see "Known differences"): P.837-7 for p ≠ 0.01 % (relative 5e-4)
  and P.1510-1 (absolute 2e-5).
- The P.618-13 total-attenuation table is used for its rain and scintillation columns (all rows)
  and its cloud column (rows with p ≥ 1 %); see "What is not validated".

## Results

### Golden vectors (11,143 cases)

| File | Cases | Worst relative error |
|---|---:|---:|
| `maps.csv` (P.839, P.1510, P.1511 maps) | 584 | 2.4e-14 |
| `p453.csv` | 1,434 | 0 |
| `p618.csv` | 1,680 | 1.6e-14 |
| `p618q.csv` (lognormal fit, diversity) | 45 | 2.4e-7 |
| `p676.csv` | 1,652 | 4.2e-12 |
| `p835.csv` | 160 | 9.9e-14 |
| `p836.csv` | 1,898 | 2.4e-16 |
| `p837.csv` | 686 | 6.5e-14 |
| `p838.csv` | 288 | 1.8e-15 |
| `p840.csv` | 1,354 | 3.9e-16 |
| `itur.csv` (total attenuation) | 1,362 | 3.3e-13 |

No case fails. The only values above 1e-11 are the P.618 quantile functions, where ITU-Rpy itself
relies on numerical integration.

### Official ITU tables

| Recommendation | Quantity | Rows | Rows within 5e-6 | Largest absolute error |
|---|---|---:|---:|---:|
| P.618-13 | rain attenuation | 64 | 64 | 1.7e-6 |
| P.618-13 | probability of rain attenuation | 64 | 64 | 6.1e-8 |
| P.618-13 | scintillation | 64 | 64 | 1.6e-8 |
| P.618-13 | rain cross-polarization discrimination | 64 | 64 | 1.4e-8 |
| P.676-13 | γ0, γw, γ (line by line) | 3 × 350 | 3 × 350 | 5.2e-14 |
| P.676-13 | slant path, Annex 2 | 10 | 10 | 8.6e-11 |
| P.676-13 | slant path, Annex 1 (28 GHz, 30°) | 1 | 1 | 2.0e-6 |
| P.836-6 | surface water vapour density | 32 | 32 | 6.3e-7 |
| P.836-6 | total water vapour content | 32 | 32 | 2.2e-6 |
| P.837-7 | R0.01 map and rainfall probability | 16 | 16 | 1.6e-6 |
| P.837-7 | rainfall rate for any p | 40 | 12 | 2.2e-2 |
| P.838-3 | k, α, γR | 3 × 64 | 3 × 64 | 4.9e-9 |
| P.839-4 | h0, hR | 2 × 8 | 2 × 8 | 1.2e-7 |
| P.840 | Lred, cloud attenuation (tables of P.840-8) | 2 × 64 | 2 × 64 | 5.7e-8 |
| P.453 | wet term Nwet (tables of P.453-14) | 8 | 8 | 2.2e-6 |
| P.1510-1 | mean surface temperature | 64 | 40 | 1.3e-5 K |
| P.1511-1 | topographic altitude | 8 | 8 | 7.1e-9 |

## Known differences from the official tables

- **P.837-7, rainfall rate for an arbitrary p.** 28 of 40 rows differ, by up to 0.022 mm/h
  (3.4e-4 relative; for example 63.597 instead of 63.619 mm/h at p = 0.01 % in New Delhi). ITU-Rpy
  returns exactly the same values as this library on these rows, so the difference comes from
  ITU-Rpy's method (it reads the R0.01 map at p = 0.01 % and solves monthly statistics by
  bisection otherwise), which was ported faithfully; the exact cause has not been traced further.
  The R0.01 map lookup and the rainfall probability match their tables.
- **P.1510-1, temperature.** 24 of 64 rows differ by at most 1.3e-5 K, which is the resolution of
  the float32 storage of the map at 300 K.
- **P.676-13, Annex 1 sheet.** The difference of 2.0e-6 dB with the ITU sheet is also present in
  ITU-Rpy (0.47081372 dB against 0.47081173 dB in the sheet), and is within the 5e-6 criterion.
- **Tables from other versions.** The P.840 and P.453 tables available correspond to P.840-8 and
  P.453-14, while this library implements P.840-7 and P.453-13. The values agree on the tested
  points, but they do not prove conformance of the older versions on their own.

## What is not validated

- **Total attenuation and gas column against an ITU table.** The P.618-13 total-attenuation table
  includes gases computed with P.676-12, which differs from the P.676-13 implemented here (by up
  to about 1e-2 dB), so its gas and total columns cannot be used. Its cloud column uses the
  exceedance probability itself below 1 %, where this library uses max(1, p) as P.618 prescribes
  for gases and clouds, so only the rows with p ≥ 1 % are compared.
- **Site diversity** has no table in the available data; it is compared with ITU-Rpy only.
- **Maven and Java 25.** The development environment had no access to Maven Central and only a
  Java 21 compiler: the code was compiled with `javac 21 --release 21`, and the JUnit tests were
  run with a small stand-in runner. Run `mvn test` with JDK 25 to complete the verification.
- **Independence.** ITU-Rpy is itself a port of the recommendations; where it deviates from the
  text, this library does too. The official tables are the independent check, but they cover only
  a few points per recommendation.

## Reproducing

```
# unit tests, golden vectors and the official tables
mvn test

# different golden tolerance
mvn test -Ditur.tolerance=1e-12

# regenerate the golden vectors (needs ITU-Rpy importable; see the script header)
python3 tools/generate_golden.py src/test/resources/golden
```
