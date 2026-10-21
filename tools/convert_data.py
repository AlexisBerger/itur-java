#!/usr/bin/env python3
"""Convert the ITU-Rpy ``itur/data`` (.npz / .txt) files used by this library into the
compact ``.grid`` format read by ``org.itur.grid.Grid``.

Usage:  python3 tools/convert_data.py <path-to-ITU-Rpy>/itur/data  src/main/resources/itur/data

Grid file layout (all big-endian, whole file GZIP-compressed):
    int32   magic   0x49545547 ("ITUG")
    int32   nlat, nlon
    float64 lat0, dlat      latitude  of row i    = lat0 + i * dlat   (always ascending)
    float64 lon0, dlon      longitude of column j = lon0 + j * dlon
    float32 values[nlat * nlon]  row-major, row 0 = lat0

Only the datasets needed by the recommended versions are converted:
P.453-13, P.836-6, P.837-7, P.839-4, P.840-7, P.1510-1, P.1511-1 (+ P.676-13 line tables).
"""
import gzip, os, shutil, struct, sys
import numpy as np

MAGIC = 0x49545547

def load(src, rel):
    return np.load(os.path.join(src, rel))['arr_0']

def write_grid(dst, rel_out, lat, lon, val):
    lat = np.asarray(lat, float); lon = np.asarray(lon, float); val = np.asarray(val, float)
    assert lat.shape == lon.shape == val.shape, (rel_out, lat.shape, lon.shape, val.shape)
    la, lo = lat[:, 0], lon[0, :]
    # all rows of lon must be identical and all columns of lat identical (regular grid)
    assert np.allclose(lon, lo[None, :], atol=1e-9) and np.allclose(lat, la[:, None], atol=1e-9), rel_out
    if la[0] > la[-1]:                       # make latitude ascending
        la = la[::-1]; val = val[::-1, :]
    nlat, nlon = val.shape
    dlat = (la[-1] - la[0]) / (nlat - 1); dlon = (lo[-1] - lo[0]) / (nlon - 1)
    assert np.allclose(la, la[0] + dlat * np.arange(nlat), atol=1e-9), rel_out
    assert np.allclose(lo, lo[0] + dlon * np.arange(nlon), atol=1e-9), rel_out
    out = os.path.join(dst, rel_out + '.grid')
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with gzip.open(out, 'wb', compresslevel=9) as f:
        f.write(struct.pack('>iii', MAGIC, nlat, nlon))
        f.write(struct.pack('>dddd', float(la[0]), float(dlat), float(lo[0]), float(dlon)))
        f.write(val.astype('>f4').tobytes())
    return os.path.getsize(out)

def fmt(p):  # p -> file suffix, exactly as ITU-Rpy: str(p).replace('.', '')
    return str(p).replace('.', '')

def main(src, dst):
    sizes = {}
    def add(key, n): sizes[key] = sizes.get(key, 0) + n
    P18 = [0.1, 0.2, 0.3, 0.5, 1, 2, 3, 5, 10, 20, 30, 50, 60, 70, 80, 90, 95, 99]
    P21 = [0.1, 0.2, 0.5, 1, 2, 5, 10, 20, 30, 40, 50, 60, 70, 80, 90, 95, 98, 99, 99.5, 99.8, 99.9]

    # P.839-4, P.1511-1, P.1510-1 --------------------------------------------------
    add('839', write_grid(dst, '839/v4_esa0height', load(src, '839/v4_esalat.npz'), load(src, '839/v4_esalon.npz'), load(src, '839/v4_esa0height.npz')))
    add('1511', write_grid(dst, '1511/v1_topo_0dot5', load(src, '1511/v1_lat.npz'), load(src, '1511/v1_lon.npz'), load(src, '1511/v1_topo_0dot5.npz')))
    la, lo = load(src, '1510/v1_lat.npz'), load(src, '1510/v1_lon.npz')
    add('1510', write_grid(dst, '1510/v1_t_annual', la, lo, load(src, '1510/v1_t_annual.npz')))
    for m in range(1, 13):
        add('1510', write_grid(dst, f'1510/v1_t_month{m:02d}', la, lo, load(src, f'1510/v1_t_month{m:02d}.npz')))

    # P.837-7 ----------------------------------------------------------------------
    add('837', write_grid(dst, '837/v7_r001', load(src, '837/v7_lat_r001.npz'), load(src, '837/v7_lon_r001.npz'), load(src, '837/v7_r001.npz')))
    la, lo = load(src, '837/v7_lat_mt.npz'), load(src, '837/v7_lon_mt.npz')
    for m in range(1, 13):
        add('837', write_grid(dst, f'837/v7_mt_month{m:02d}', la, lo, load(src, f'837/v7_mt_month{m:02d}.npz')))

    # P.836-6 ----------------------------------------------------------------------
    la, lo = load(src, '836/v6_lat.npz'), load(src, '836/v6_lon.npz')
    for kind in ('v', 'vsch', 'rho'):
        for p in P18:
            add('836', write_grid(dst, f'836/v6_{kind}_{fmt(p)}', la, lo, load(src, f'836/v6_{kind}_{fmt(p)}.npz')))
    add('836', write_grid(dst, '836/v6_topo_0dot5', load(src, '836/v6_topolat.npz'), load(src, '836/v6_topolon.npz'), load(src, '836/v6_topo_0dot5.npz')))

    # P.840-7 ----------------------------------------------------------------------
    la, lo = load(src, '840/v7_lat.npz'), load(src, '840/v7_lon.npz')
    for p in P18:
        add('840', write_grid(dst, f'840/v7_lred_{fmt(p)}', la, lo, load(src, f'840/v7_lred_{fmt(p)}.npz')))
    for n in ('m', 'sigma', 'pclw'):
        add('840', write_grid(dst, f'840/v7_{n}', la, lo, load(src, f'840/v7_{n}.npz')))

    # P.453-13 (N_wet map) + DN65 / DN1 maps (data files of P.453-12 are re-used by -13)
    la, lo = load(src, '453/v13_lat_n.npz'), load(src, '453/v13_lon_n.npz')
    for p in P18:
        add('453', write_grid(dst, f'453/v13_nwet_annual_{fmt(p)}', la, lo, load(src, f'453/v13_nwet_annual_{fmt(p)}.npz')))
    la, lo = load(src, '453/v12_lat0d75.npz'), load(src, '453/v12_lon0d75.npz')
    for p in P21:
        ip, fp = int(p // 1), round((p % 1.0) * 100)
        for kind, stem in (('dn65m', 'v12_dn65m_%02dd%02d_v1'), ('dn', 'v12_dn_%02dd%02d_v1')):
            name = stem % (ip, fp)
            add('453', write_grid(dst, f'453/{name}', la, lo, load(src, f'453/{name}.npz')))

    # P.676-13 line tables and oxygen equivalent-height coefficients (plain text) -------------------------------------------
    os.makedirs(os.path.join(dst, '676'), exist_ok=True)
    for n in ('v13_lines_oxygen.txt', 'v13_lines_water_vapour.txt', 'v13_h0_coefficients.txt'):
        shutil.copyfile(os.path.join(src, '676', n), os.path.join(dst, '676', n))

    total = 0
    for k, v in sorted(sizes.items()):
        print(f'P.{k:5s} {v/1e6:8.2f} MB'); total += v
    print(f'total   {total/1e6:8.2f} MB')

if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
