#!/usr/bin/env python3
"""Time ITU-Rpy on the same operations and inputs as org.itur.perf.PerformanceBenchmark.

Usage:
    java -cp target/classes:target/test-classes org.itur.perf.PerformanceBenchmark --dump-inputs points.csv
    python3 tools/benchmark_itur_rpy.py points.csv [--quick] [--markdown]

Needs ITU-Rpy (>= 0.4) importable, with its dependencies (numpy, scipy, astropy, pyproj).

Two figures per operation:
  * scalar:     one Python call per point (what a Java caller of the scalar API does);
  * vectorized: one call on the first 128 points as numpy arrays, divided by 128 (ITU-Rpy's
                intended use). Reported as "n/a" when the function does not accept arrays.
The first-call time (data loading, interpolator construction) is measured first, in the same order
as the Java benchmark. Operation names match the Java benchmark so the tables can be merged.
"""
import sys, time, warnings
warnings.filterwarnings('ignore')
import numpy as np
import itur
import itur.models.itu618 as m618, itur.models.itu676 as m676, itur.models.itu838 as m838
import itur.models.itu839 as m839, itur.models.itu453 as m453, itur.models.itu836 as m836
import itur.models.itu837 as m837, itur.models.itu840 as m840, itur.models.itu1510 as m1510
import itur.models.itu1511 as m1511, itur.models.itu835 as m835

# Versions implemented by the Java library
m618.change_version(13); m676.change_version(13); m838.change_version(3); m839.change_version(4)
m453.change_version(13); m836.change_version(6); m837.change_version(7); m840.change_version(7)
m1510.change_version(1); m1511.change_version(1); m835.change_version(6)

args = sys.argv[1:]
quick = '--quick' in args
md = '--markdown' in args
files = [a for a in args if not a.startswith('--')]
data = np.genfromtxt(files[0], delimiter=',', names=True)
N = len(data)
LAT, LON, F, EL, AP = (data[k] for k in ('lat', 'lon', 'f', 'el', 'ap'))
LATl, LONl, Fl, ELl, APl = (a.tolist() for a in (LAT, LON, F, EL, AP))
MASK = N - 1
# The vectorized call uses the first VN points only: the ray-traced operations are np.vectorize
# loops that need seconds per call on 1,024 points.
VN = 128
LAT, LON, F, EL, AP = (a[:VN] for a in (LAT, LON, F, EL, AP))

# (name, scalar(i), vectorized() or None)
CASES = [
    ('P.838 rain specific attenuation',
     lambda i: m838.rain_specific_attenuation(20 + APl[i], Fl[i], ELl[i], 45),
     lambda: m838.rain_specific_attenuation(20 + AP, F, EL, 45)),
    ('P.676 gamma (line by line, 79 lines)',
     lambda i: m676.gamma_exact(Fl[i], 1013.25, 7.5 + APl[i], 288.15),
     lambda: m676.gamma_exact(F, 1013.25, 7.5 + AP, 288.15)),
    ('P.676 slant path, Annex 2',
     lambda i: m676.gaseous_attenuation_slant_path(Fl[i], ELl[i], 7.5 + APl[i], 1013.25, 288.15, mode='approx'),
     lambda: m676.gaseous_attenuation_slant_path(F, EL, 7.5 + AP, 1013.25, 288.15, mode='approx')),
    ('P.676 slant path, Annex 1 ray tracing (922 layers)',
     lambda i: m676.gaseous_attenuation_slant_path(Fl[i], ELl[i], 7.5 + APl[i], 1013.25, 288.15, mode='exact'),
     lambda: m676.gaseous_attenuation_slant_path(F, EL, 7.5 + AP, 1013.25, 288.15, mode='exact')),
    ('P.1511 topographic altitude (bicubic)',
     lambda i: m1511.topographic_altitude(LATl[i], LONl[i]),
     lambda: m1511.topographic_altitude(LAT, LON)),
    ('P.1510 mean surface temperature',
     lambda i: m1510.surface_mean_temperature(LATl[i], LONl[i]),
     lambda: m1510.surface_mean_temperature(LAT, LON)),
    ('P.839 rain height',
     lambda i: m839.rain_height(LATl[i], LONl[i]),
     lambda: m839.rain_height(LAT, LON)),
    ('P.453 wet refractivity map (p = 50 %)',
     lambda i: m453.map_wet_term_radio_refractivity(LATl[i], LONl[i], 50),
     lambda: m453.map_wet_term_radio_refractivity(LAT, LON, 50)),
    ('P.836 total water vapour content',
     lambda i: m836.total_water_vapour_content(LATl[i], LONl[i], 1.0),
     lambda: m836.total_water_vapour_content(LAT, LON, 1.0)),
    ('P.837 rainfall rate, p = 0.01 % (map)',
     lambda i: m837.rainfall_rate(LATl[i], LONl[i], 0.01),
     lambda: m837.rainfall_rate(LAT, LON, 0.01)),
    ('P.837 rainfall rate, p = 1 % (monthly, bisection)',
     lambda i: m837.rainfall_rate(LATl[i], LONl[i], 1.0),
     lambda: m837.rainfall_rate(LAT, LON, 1.0)),
    ('P.840 cloud attenuation',
     lambda i: m840.cloud_attenuation(LATl[i], LONl[i], ELl[i], Fl[i], 1.0),
     lambda: m840.cloud_attenuation(LAT, LON, EL, F, 1.0)),
    ('P.618 rain attenuation (0.01 %)',
     lambda i: m618.rain_attenuation(LATl[i], LONl[i], Fl[i], ELl[i]),
     lambda: m618.rain_attenuation(LAT, LON, F, EL)),
    ('P.618 scintillation',
     lambda i: m618.scintillation_attenuation(LATl[i], LONl[i], Fl[i], ELl[i], 1.0, 1.2, 0.5),
     lambda: m618.scintillation_attenuation(LAT, LON, F, EL, 1.0, 1.2, 0.5)),
    ('P.618 rain XPD',
     lambda i: m618.rain_cross_polarization_discrimination(APl[i], Fl[i], ELl[i], 0.01, 45),
     lambda: m618.rain_cross_polarization_discrimination(AP, F, EL, 0.01, 45)),
    ('Itur total attenuation (gas: Annex 2)',
     lambda i: itur.atmospheric_attenuation_slant_path(LATl[i], LONl[i], Fl[i], ELl[i], 0.1, 1.2, mode='approx'),
     lambda: itur.atmospheric_attenuation_slant_path(LAT, LON, F, EL, 0.1, 1.2, mode='approx')),
    ('Itur total attenuation (gas: Annex 1)',
     lambda i: itur.atmospheric_attenuation_slant_path(LATl[i], LONl[i], Fl[i], ELl[i], 0.1, 1.2, mode='exact'),
     lambda: itur.atmospheric_attenuation_slant_path(LAT, LON, F, EL, 0.1, 1.2, mode='exact')),
]

WARM = 0.2 if quick else 1.0
ROUND = 0.1 if quick else 0.5
ROUNDS = 3 if quick else 5


def timed_calls(fn, budget, per_call_points=1):
    """Run fn(i) repeatedly for ~budget seconds; return mean seconds per point."""
    start = time.perf_counter()
    n = 0
    i = 0
    while True:
        fn(i & MASK)
        i += 1
        n += 1
        now = time.perf_counter()
        if now - start >= budget:
            return (now - start) / (n * per_call_points)


def measure(fn, per_call_points=1):
    timed_calls(fn, WARM, per_call_points)
    r = sorted(timed_calls(fn, ROUND, per_call_points) for _ in range(ROUNDS))
    return r[len(r) // 2]


def human(s):
    ns = s * 1e9
    if ns >= 1e6: return '%.2f ms' % (ns / 1e6)
    if ns >= 1e3: return '%.2f us' % (ns / 1e3)
    return '%.0f ns' % ns


import platform
print('Python %s, numpy %s, ITU-Rpy %s' % (platform.python_version(), np.__version__, getattr(itur, '__version__', '?')))

print('\nFirst call (data loading + interpolator construction)')
for name, sc, ve in CASES:
    t0 = time.perf_counter()
    sc(0)
    ms = (time.perf_counter() - t0) * 1e3
    print('| %s | %.1f ms |' % (name, ms) if md else '  %-58s %8.1f ms' % (name, ms), flush=True)

print('\nWarm (median of %d rounds; vectorized calls use %d points)' % (ROUNDS, VN), flush=True)
if md:
    print('| Operation | scalar: time per call | scalar: calls per second | vectorized: time per point | vectorized: points per second |\n|---|---:|---:|---:|---:|')
for name, sc, ve in CASES:
    s = measure(sc)
    try:
        v = measure(lambda _i: ve(), VN)
        vs, vo = human(v), '{:,.0f}'.format(1 / v)
    except Exception as e:  # function does not accept arrays
        vs, vo = 'n/a', 'n/a'
    if md:
        print('| %s | %s | %s | %s | %s |' % (name, human(s), '{:,.0f}'.format(1 / s), vs, vo), flush=True)
    else:
        print('  %-58s %10s %12s/s   vectorized %10s %12s/s' % (name, human(s), '{:,.0f}'.format(1 / s), vs, vo), flush=True)
