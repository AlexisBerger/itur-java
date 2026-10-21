#!/usr/bin/env python3
"""Generate the golden test vectors (src/test/resources/golden/*.csv) with ITU-Rpy.

Run with ITU-Rpy (>= 0.4) importable, e.g. ``pip install ITU-Rpy`` then
    python3 tools/generate_golden.py src/test/resources/golden

The ITU-R datasets bundled in this Java library are stored as float32. To compare like with like,
the data arrays returned by ``itur.utils.load_data`` are rounded to float32 before use
(``ROUND_DATA_TO_FLOAT32``); latitude/longitude axes are left untouched. With pristine float64
data the results differ by ~1e-7 relative, so use a tolerance of 1e-5 in that case.
"""
import os, sys, warnings, math
warnings.filterwarnings('ignore')
import numpy as np

ROUND_DATA_TO_FLOAT32 = True

import itur.utils as _u
import itur.models.itu618 as m618, itur.models.itu676 as m676, itur.models.itu838 as m838
import itur.models.itu839 as m839, itur.models.itu453 as m453, itur.models.itu836 as m836
import itur.models.itu837 as m837, itur.models.itu840 as m840, itur.models.itu1510 as m1510
import itur.models.itu1511 as m1511, itur.models.itu835 as m835

if ROUND_DATA_TO_FLOAT32:
    _orig_load = _u.load_data
    def _load(path, is_text=False, **kw):
        d = _orig_load(path, is_text, **kw)
        base = os.path.basename(path)
        if path.endswith('.npz') and 'lat' not in base and 'lon' not in base:
            d = d.astype(np.float32).astype(np.float64)
        return d
    _u.load_data = _load
    for _m in (m618, m676, m838, m839, m453, m836, m837, m840, m1510, m1511, m835):
        if hasattr(_m, 'load_data'):
            _m.load_data = _load

# Versions covered by this library
m618.change_version(13); m676.change_version(11); m838.change_version(3); m839.change_version(4)
m453.change_version(13); m836.change_version(6); m837.change_version(7); m840.change_version(7)
m1510.change_version(1); m1511.change_version(1); m835.change_version(6)

def fv(x):
    """Quantity / ndarray / number -> float."""
    x = getattr(x, 'value', x)
    return float(np.asarray(x).reshape(-1)[0])

def fmt(v):
    v = float(v)
    if math.isnan(v): return 'NaN'
    if math.isinf(v): return 'Infinity' if v > 0 else '-Infinity'
    return repr(v)

class Golden:
    def __init__(self, outdir, name):
        os.makedirs(outdir, exist_ok=True)
        self.f = open(os.path.join(outdir, name + '.csv'), 'w')
        self.n = 0
    def add(self, fn, args, fcn):
        try:
            res = fcn()
        except Exception as e:                      # keep going; report
            print('  skipped', fn, args, type(e).__name__, str(e)[:80]); return
        self.f.write(fn + ',' + ','.join(fmt(a) for a in args) + ',' + fmt(res) + '\n'); self.n += 1
    def close(self):
        self.f.close(); print(f'  {self.f.name}: {self.n} cases')

# ----- sample locations ---------------------------------------------------------------------
CITIES = [(48.85, 2.35), (43.60, 1.44), (5.16, -52.65), (35.68, 139.69), (-33.87, 151.21),
          (-22.91, -43.17), (-1.29, 36.82), (64.15, -21.94), (-77.85, 166.67), (28.61, 77.21),
          (40.71, -74.0), (-34.6, -58.4), (1.35, 103.82), (55.75, 37.62), (-12.05, -77.04),
          (0.0, 0.0), (45.0, 90.0), (-30.0, 270.0), (89.0, 359.0), (-89.0, 1.0)]
rng = np.random.default_rng(20261021)
RANDOM = [(float(rng.uniform(-88, 88)), float(rng.uniform(-180, 360))) for _ in range(40)]
# boundary cases: poles, date line, 0/360 wrap, grid nodes and just off them
EDGE = [(90.0, 0.0), (-90.0, 0.0), (0.0, 180.0), (0.0, -180.0), (0.0, 360.0), (45.0, 359.99), (89.9999, 10.0),
        (-89.9999, 10.0), (30.0, 179.9999), (-45.0, -179.9999), (37.5, 1.125), (-60.0, 90.0), (12.0, -0.0001)]
POINTS = CITIES + RANDOM + EDGE

def gen_basic(outdir):
    print('basic models')
    g = Golden(outdir, 'p838')
    for f in (1, 2.5, 4, 7.3, 10, 12, 14.25, 20, 28, 30, 38.5, 45, 60, 77, 100, 250, 600, 1000):
        for el, tau in ((5, 0), (23, 45), (45, 90), (89, 30), (0, 45)):
            g.add('k', (f, el, tau), lambda: np.asarray(m838.rain_specific_attenuation_coefficients(f, el, tau)).reshape(-1)[0])
            g.add('alpha', (f, el, tau), lambda: np.asarray(m838.rain_specific_attenuation_coefficients(f, el, tau)).reshape(-1)[1])
        for R in (0.1, 1.0, 12.5, 42.0, 99.0, 150.0):
            g.add('gamma', (R, f, 35, 45), lambda: fv(m838.rain_specific_attenuation(R, f, 35, 45)))
    g.close()

    g = Golden(outdir, 'p835')
    for h in [-0.5, 0, 0.1, 0.5, 1, 2.2, 4, 8.848, 10, 11, 11.5, 15, 20, 25, 30, 32, 40, 47, 50, 51, 60, 71, 80, 84, 85, 86, 88, 91, 92, 95, 100, 120]:
        g.add('T', (h,), lambda: fv(m835.standard_temperature(h)))
        g.add('P', (h,), lambda: fv(m835.standard_pressure(h)))
        g.add('rho', (h,), lambda: fv(m835.standard_water_vapour_density(h)))
        g.add('rho2', (h, 3.0, 11.0), lambda: fv(m835.standard_water_vapour_density(h, 3.0, 11.0)))
        g.add('e', (h,), lambda: fv(m835.standard_water_vapour_pressure(h)))
    g.close()

    g = Golden(outdir, 'maps')
    for lat, lon in POINTS:
        g.add('p839.h0', (lat, lon), lambda: fv(m839.isoterm_0(lat, lon)))
        g.add('p839.hr', (lat, lon), lambda: fv(m839.rain_height(lat, lon)))
        g.add('p1510.T', (lat, lon), lambda: fv(m1510.surface_mean_temperature(lat, lon)))
        for m in (1, 4, 7, 12):
            g.add('p1510.Tm', (lat, lon, m), lambda: fv(m1510.surface_month_mean_temperature(lat, lon, m)))
        g.add('p1511.h', (lat, lon), lambda: fv(m1511.topographic_altitude(lat, lon)))
    g.close()

def gen_maps2(outdir):
    print('P.837 / P.836 / P.840 / P.453')
    g = Golden(outdir, 'p837')
    for lat, lon in POINTS:
        g.add('P0', (lat, lon), lambda: fv(m837.rainfall_probability(lat, lon)))
        for p in (0.01, 0.001, 0.1, 0.5, 1.0, 3.0, 5.0, 10.0):
            g.add('R', (lat, lon, p), lambda: fv(m837.rainfall_rate(lat, lon, p)))
    for lat, lon in CITIES[:10]:
        for R in (5.0, 20.0, 40.0):
            g.add('unavail', (lat, lon, R), lambda: fv(m837.unavailability_from_rainfall_rate(lat, lon, R)))
    g.close()

    g = Golden(outdir, 'p836')
    for lat, lon in POINTS:
        for p in (0.1, 1.0, 20.0, 50.0, 99.0, 0.05, 1.5, 7.0, 33.0, 99.5):
            g.add('rho', (lat, lon, p), lambda: fv(m836.surface_water_vapour_density(lat, lon, p)))
            g.add('V', (lat, lon, p), lambda: fv(m836.total_water_vapour_content(lat, lon, p)))
        for alt in (0.0, 0.5, 2.0):
            g.add('rhoAlt', (lat, lon, 3.0, alt), lambda: fv(m836.surface_water_vapour_density(lat, lon, 3.0, alt)))
            g.add('VAlt', (lat, lon, 3.0, alt), lambda: fv(m836.total_water_vapour_content(lat, lon, 3.0, alt)))
    g.close()

    g = Golden(outdir, 'p840')
    for lat, lon in POINTS:
        for p in (0.1, 1.0, 5.0, 50.0, 99.0, 0.05, 1.5, 7.0, 33.0, 98.5):
            g.add('Lred', (lat, lon, p), lambda: fv(m840.columnar_content_reduced_liquid(lat, lon, p)))
        for el, f, p in ((10, 12.0, 1.0), (35, 20.0, 0.1), (60, 30.0, 5.0), (90, 50.0, 2.0), (25, 100.0, 7.0)):
            g.add('A', (lat, lon, el, f, p), lambda: fv(m840.cloud_attenuation(lat, lon, el, f, p)))
        g.add('m', (lat, lon), lambda: fv(m840.lognormal_approximation_coefficient(lat, lon)[0]))
        g.add('sigma', (lat, lon), lambda: fv(m840.lognormal_approximation_coefficient(lat, lon)[1]))
        g.add('pclw', (lat, lon), lambda: fv(m840.lognormal_approximation_coefficient(lat, lon)[2]))
    for f in (1, 5, 12, 20, 30, 50, 90, 150, 300, 1000):
        for T in (-10, 0, 10, 20):
            g.add('Kl', (f, T), lambda: fv(m840.specific_attenuation_coefficients(f, T)))
    g.close()

    g = Golden(outdir, 'p453')
    for lat, lon in POINTS:
        for p in (50.0, 0.1, 5.0, 1.5, 99.0, 0.05, 77.0):
            g.add('Nwet', (lat, lon, p), lambda: fv(m453.map_wet_term_radio_refractivity(lat, lon, p)))
        for p in (1.0, 10.0, 50.0, 99.9, 0.1, 99.5):
            g.add('DN65', (lat, lon, p), lambda: fv(m453.DN65(lat, lon, p)))
            g.add('DN1', (lat, lon, p), lambda: fv(m453.DN1(lat, lon, p)))
    for e, T in ((5.0, -10.0), (12.0, 5.0), (20.0, 15.0), (30.0, 25.0), (45.0, 35.0)):
        g.add('Nwet_f', (e, T), lambda: fv(m453.wet_term_radio_refractivity(e, T)))
    for Pd, T in ((1013.25, 288.15), (900.0, 270.0), (500.0, 250.0)):
        g.add('Ndry', (Pd, T), lambda: fv(m453.dry_term_radio_refractivity(Pd, T)))
        g.add('n', (Pd, 10.0, T), lambda: fv(m453.radio_refractive_index(Pd, 10.0, T)))
    for T in (-30.0, -10.0, 0.0, 15.0, 30.0, 45.0):
        for P in (500.0, 1013.25):
            g.add('es_w', (T, P), lambda: fv(m453.saturation_vapour_pressure(T, P, 'water')))
            g.add('es_i', (T, P), lambda: fv(m453.saturation_vapour_pressure(T, P, 'ice')))
            g.add('e', (T, P, 60.0), lambda: fv(m453.water_vapour_pressure(T, P, 60.0)))
    g.close()

def gen_gas(outdir):
    print('P.676')
    g = Golden(outdir, 'p676')
    FREQ = (1, 5, 10, 12.5, 20, 22.235, 30, 50, 57, 60, 63, 100, 118.75, 183.31, 220, 300, 350)
    ATM = ((1013.25, 7.5, 288.15), (900.0, 15.0, 275.0), (700.0, 3.0, 260.0), (1000.0, 22.0, 300.0))
    for f in FREQ:
        for P, rho, T in ATM:
            g.add('g0e', (f, P, rho, T), lambda: fv(m676.gamma0_exact(f, P, rho, T)))
            g.add('gwe', (f, P, rho, T), lambda: fv(m676.gammaw_exact(f, P, rho, T)))
            g.add('ge', (f, P, rho, T), lambda: fv(m676.gamma_exact(f, P, rho, T)))
            g.add('g0a', (f, P, rho, T), lambda: fv(m676.gamma0_approx(f, P, rho, T)))
            g.add('gwa', (f, P, rho, T), lambda: fv(m676.gammaw_approx(f, P, rho, T)))
        for P in (1013.25, 850.0, 600.0):
            g.add('h0', (f, P), lambda: np.asarray(m676.slant_inclined_path_equivalent_height(f, P).value if hasattr(m676.slant_inclined_path_equivalent_height(f, P), 'value') else m676.slant_inclined_path_equivalent_height(f, P)).reshape(-1)[0])
            g.add('hw', (f, P), lambda: np.asarray(m676.slant_inclined_path_equivalent_height(f, P).value if hasattr(m676.slant_inclined_path_equivalent_height(f, P), 'value') else m676.slant_inclined_path_equivalent_height(f, P)).reshape(-1)[1])
        for el in (5, 12, 30, 60, 90):
            for P, rho, T in ATM[:3]:
                g.add('slant_a', (f, el, rho, P, T), lambda: fv(m676.gaseous_attenuation_slant_path(f, el, rho, P, T, mode='approx')))
                g.add('slant_a_vt', (f, el, rho, P, T, 20.0, 0.3), lambda: fv(m676.gaseous_attenuation_slant_path(f, el, rho, P, T, 20.0, 0.3, 'approx')))
        for (r, el) in ((10.0, 0), (3.5, 10)):
            for P, rho, T in ATM[:2]:
                g.add('terr_a', (r, f, el, rho, P, T), lambda: fv(m676.gaseous_attenuation_terrestrial_path(r, f, el, rho, P, T, 'approx')))
                g.add('terr_e', (r, f, el, rho, P, T), lambda: fv(m676.gaseous_attenuation_terrestrial_path(r, f, el, rho, P, T, 'exact')))
        for el, h1, h2 in ((30, 0.0, 8.0), (60, 1.0, 5.0), (3, 0.5, 9.0), (-2, 1.0, 6.0), (89.5, 0.0, 9.5), (5, 0.0, 3.0)):
            for P, rho, T in ATM[:2]:
                g.add('incl_a', (f, el, rho, P, T, h1, h2), lambda: fv(m676.gaseous_attenuation_inclined_path(f, el, rho, P, T, h1, h2, 'approx')))
                g.add('incl_e', (f, el, rho, P, T, h1, h2), lambda: fv(m676.gaseous_attenuation_inclined_path(f, el, rho, P, T, h1, h2, 'exact')))
    for f in (10, 20, 22.5, 30, 60, 183.3):
        for el in (10, 45, 90):
            for P, rho, T in ATM[:2]:
                g.add('slant_e', (f, el, rho, P, T), lambda: fv(m676.gaseous_attenuation_slant_path(f, el, rho, P, T, mode='exact')))
    for lat, lon in CITIES:
        for f in (12.0, 20.0, 30.0):
            g.add('zwv', (lat, lon, 1.0, f), lambda: fv(m676.zenit_water_vapour_attenuation(lat, lon, 1.0, f)))
            g.add('zwv_vt', (lat, lon, 1.0, f, 25.0, 0.5), lambda: fv(m676.zenit_water_vapour_attenuation(lat, lon, 1.0, f, 25.0, 0.5)))
    g.close()

def gen_618(outdir):
    print('P.618')
    g = Golden(outdir, 'p618')
    CASES = ((20.0, 35.0, 0.01, 45.0), (12.0, 10.0, 0.1, 0.0), (30.0, 50.0, 1.0, 90.0), (40.0, 4.0, 0.01, 45.0),
             (14.0, 28.0, 0.001, 30.0), (10.0, 20.0, 5.0, 45.0), (50.0, 80.0, 0.5, 45.0))
    for lat, lon in POINTS:
        for f, el, p, tau in CASES:
            g.add('rain', (lat, lon, f, el, p, tau), lambda: fv(m618.rain_attenuation(lat, lon, f, el, p=p, tau=tau)))
        g.add('rain_hs', (lat, lon, 20.0, 35.0, 0.3, 0.2, 45.0), lambda: fv(m618.rain_attenuation(lat, lon, 20.0, 35.0, hs=0.3, p=0.2, tau=45.0)))
        g.add('rain_r001', (lat, lon, 20.0, 35.0, 0.01, 60.0, 45.0), lambda: fv(m618.rain_attenuation(lat, lon, 20.0, 35.0, p=0.01, R001=60.0, tau=45.0)))
        g.add('rain_ls', (lat, lon, 20.0, 35.0, 0.01, 6.0, 45.0), lambda: fv(m618.rain_attenuation(lat, lon, 20.0, 35.0, p=0.01, Ls=6.0, tau=45.0)))
        for f, el, p, D, eta in ((12.0, 30.0, 1.0, 1.2, 0.5), (20.0, 10.0, 0.1, 3.0, 0.65), (30.0, 60.0, 0.01, 0.6, 0.5), (4.0, 45.0, 10.0, 8.0, 0.6)):
            g.add('scint', (lat, lon, f, el, p, D, eta), lambda: fv(m618.scintillation_attenuation(lat, lon, f, el, p, D, eta)))
            g.add('scint_sigma', (lat, lon, f, el, p, D, eta), lambda: fv(m618.scintillation_attenuation_sigma(lat, lon, f, el, p, D, eta)))
        g.add('scint_thp', (lat, lon, 20.0, 25.0, 0.5, 2.0, 0.5, 15.0, 70.0, 1000.0), lambda: fv(m618.scintillation_attenuation(lat, lon, 20.0, 25.0, 0.5, 2.0, 0.5, 15.0, 70.0, 1000.0)))
        g.add('scint_thp2', (lat, lon, 11.0, 40.0, 2.0, 1.0, 0.6, 28.0, 90.0, 1013.0, 800.0), lambda: fv(m618.scintillation_attenuation(lat, lon, 11.0, 40.0, 2.0, 1.0, 0.6, 28.0, 90.0, 1013.0, 800.0)))
    for Ap in (0.5, 3.0, 10.0, 25.0):
        for f in (4.0, 5.5, 6.0, 8.0, 9.0, 12.0, 20.0, 30.0, 36.0, 45.0, 55.0):
            for el, p, tau in ((30.0, 0.01, 45.0), (50.0, 0.001, 0.0), (20.0, 0.5, 90.0), (45.0, 2.0, 30.0), (10.0, 0.05, 45.0)):
                g.add('xpd', (Ap, f, el, p, tau), lambda: fv(m618.rain_cross_polarization_discrimination(Ap, f, el, p, tau)))
    g.close()

    # functions relying on numerical integration (scipy.integrate.quad in ITU-Rpy): looser tolerance
    g = Golden(outdir, 'p618q')
    for lat, lon in CITIES[:14]:
        for el in (20.0, 40.0):
            g.add('prob', (lat, lon, el), lambda: fv(m618.rain_attenuation_probability(lat, lon, el)))
    for lat, lon in CITIES[:6]:
        g.add('fit_sigma', (lat, lon, 20.0, 35.0, 5.0, 45.0), lambda: fv(m618.fit_rain_attenuation_to_lognormal(lat, lon, 20.0, 35.0, 0.1, 5.0, 45.0)[0]))
        g.add('fit_m', (lat, lon, 20.0, 35.0, 5.0, 45.0), lambda: fv(m618.fit_rain_attenuation_to_lognormal(lat, lon, 20.0, 35.0, 0.1, 5.0, 45.0)[1]))
    PAIRS = (((48.85, 2.35), (43.60, 1.44)), ((35.68, 139.69), (34.69, 135.5)), ((-22.91, -43.17), (-23.55, -46.63)),
             ((1.35, 103.82), (3.14, 101.69)), ((40.71, -74.0), (42.36, -71.06)))
    for (a, b) in PAIRS:
        g.add('divers', (a[0], a[1], 4.0, 35.0, b[0], b[1], 4.0, 35.0, 20.0, 45.0),
              lambda: fv(m618.site_diversity_rain_outage_probability(a[0], a[1], 4.0, 35.0, b[0], b[1], 4.0, 35.0, 20.0, 45.0, 0.1, 0.1)))
    g.close()

def gen_itur(outdir):
    print('itur.atmospheric_attenuation_slant_path')
    import itur
    g = Golden(outdir, 'itur')
    CASES = ((20.0, 35.0, 0.1, 1.2), (12.0, 20.0, 1.0, 2.4), (30.0, 50.0, 0.01, 0.6), (11.0, 8.0, 5.0, 3.0), (45.0, 70.0, 0.5, 0.6), (14.0, 28.0, 0.001, 1.8))
    for lat, lon in POINTS[:45]:
        for f, el, p, D in CASES:
            def contrib(k, mode='approx'):
                return lambda: fv(itur.atmospheric_attenuation_slant_path(lat, lon, f, el, p, D, mode=mode, return_contributions=True)[k])
            for name, k in (('gas', 0), ('cloud', 1), ('rain', 2), ('scint', 3), ('total', 4)):
                g.add('itur.' + name, (lat, lon, f, el, p, D), contrib(k))
    for lat, lon in CITIES[:6]:
        for f, el, p, D in CASES[:2]:
            g.add('itur.total_exact', (lat, lon, f, el, p, D), lambda: fv(itur.atmospheric_attenuation_slant_path(lat, lon, f, el, p, D, mode='exact')))
    g.close()

SECTIONS = {'basic': gen_basic, 'maps2': gen_maps2, 'gas': gen_gas, '618': gen_618, 'itur': gen_itur}

if __name__ == '__main__':
    out = sys.argv[1]
    which = sys.argv[2:] or list(SECTIONS)
    for w in which:
        SECTIONS[w](out)
