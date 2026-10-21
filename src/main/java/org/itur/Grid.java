package org.itur;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;

/**
 * Regular latitude/longitude grid of a geophysical quantity, with the two interpolation methods
 * recommended by ITU-R P.1144 (bilinear and bicubic).
 *
 * <p>The grid is stored with <b>ascending</b> latitude and longitude:
 * {@code lat(i) = lat0 + i * dlat}, {@code lon(j) = lon0 + j * dlon}. The values are kept as
 * {@code float} to keep the memory footprint of the large ITU maps reasonable.
 *
 * <p>Grids are loaded lazily from the class path ({@code /itur/data/<name>.grid}) and cached.
 * The longitude convention (0..360 or -180..180) is the one of the original ITU data set; callers
 * are responsible for mapping their longitude into the range of the grid they use, exactly like
 * the individual recommendations do.
 */
public final class Grid {

    private static final int MAGIC = 0x49545547; // "ITUG"
    private static final Map<String, Grid> CACHE = new ConcurrentHashMap<>();

    private final int nlat;
    private final int nlon;
    private final double lat0;
    private final double dlat;
    private final double lon0;
    private final double dlon;
    private final float[] values;

    Grid(int nlat, int nlon, double lat0, double dlat, double lon0, double dlon, float[] values) {
        if (values.length != nlat * nlon) {
            throw new IllegalArgumentException("grid size mismatch");
        }
        this.nlat = nlat;
        this.nlon = nlon;
        this.lat0 = lat0;
        this.dlat = dlat;
        this.lon0 = lon0;
        this.dlon = dlon;
        this.values = values;
    }

    /** Loads (or returns the cached) grid stored in the resource {@code itur/data/<name>.grid}. */
    public static Grid load(String name) {
        return CACHE.computeIfAbsent(name, Grid::read);
    }

    private static Grid read(String name) {
        String path = "/itur/data/" + name + ".grid";
        try (InputStream raw = Grid.class.getResourceAsStream(path)) {
            if (raw == null) {
                throw new IllegalStateException("ITU data resource not found: " + path);
            }
            byte[] bytes;
            try (GZIPInputStream in = new GZIPInputStream(raw, 1 << 16)) {
                bytes = in.readAllBytes();
            }
            DataInputStream header = new DataInputStream(new java.io.ByteArrayInputStream(bytes));
            if (header.readInt() != MAGIC) {
                throw new IllegalStateException("Bad grid file: " + path);
            }
            int nlat = header.readInt();
            int nlon = header.readInt();
            double lat0 = header.readDouble();
            double dlat = header.readDouble();
            double lon0 = header.readDouble();
            double dlon = header.readDouble();
            float[] v = new float[nlat * nlon];
            ByteBuffer.wrap(bytes, 12 + 32, v.length * 4).asFloatBuffer().get(v);
            return new Grid(nlat, nlon, lat0, dlat, lon0, dlon, v);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read ITU data resource " + path, e);
        }
    }

    public int nlat() {
        return nlat;
    }

    public int nlon() {
        return nlon;
    }

    public double lat(int i) {
        return lat0 + i * dlat;
    }

    public double lon(int j) {
        return lon0 + j * dlon;
    }

    public double value(int i, int j) {
        return values[i * nlon + j];
    }

    /**
     * Bilinear interpolation (same behaviour as {@code scipy.interpolate.RegularGridInterpolator}
     * with {@code method="linear"}, {@code bounds_error=False}): points outside the grid, or NaN
     * coordinates, give {@code NaN}.
     */
    public double bilinear(double lat, double lon) {
        double latMax = lat0 + (nlat - 1) * dlat;
        double lonMax = lon0 + (nlon - 1) * dlon;
        if (!(lat >= lat0 && lat <= latMax && lon >= lon0 && lon <= lonMax)) {
            return Double.NaN;
        }
        int i = (int) Math.floor((lat - lat0) / dlat);
        int j = (int) Math.floor((lon - lon0) / dlon);
        i = Math.max(0, Math.min(nlat - 2, i));
        j = Math.max(0, Math.min(nlon - 2, j));
        double ty = (lat - (lat0 + i * dlat)) / dlat;
        double tx = (lon - (lon0 + j * dlon)) / dlon;
        double v00 = values[i * nlon + j];
        double v01 = values[i * nlon + j + 1];
        double v10 = values[(i + 1) * nlon + j];
        double v11 = values[(i + 1) * nlon + j + 1];
        return v00 * (1 - ty) * (1 - tx) + v01 * (1 - ty) * tx + v10 * ty * (1 - tx) + v11 * ty * tx;
    }

    /**
     * Bicubic (Keys / Catmull-Rom, a = -0.5) interpolation as specified in ITU-R P.1144 and
     * implemented by ITU-Rpy. The grid must be padded by one cell on each side of the area of
     * interest (as the P.1511 and P.836 topography grids are): the interpolation point must lie
     * inside the inner area.
     */
    public double bicubic(double lat, double lon) {
        int innerLat = nlat - 2;
        int innerLon = nlon - 2;
        double latRow0 = lat0 + dlat;
        double lonRow0 = lon0 + dlon;
        // numpy.searchsorted(row, x, side) on the inner (regular) rows / columns
        int latRight = searchSorted(lat, latRow0, dlat, innerLat, true);
        int latLeft = searchSorted(lat, latRow0, dlat, innerLat, false);
        int lonRight = searchSorted(lon, lonRow0, dlon, innerLon, true);
        int r0 = Math.floorDiv((latRight - 1) + (latLeft - 1), 2);
        int c0 = Math.floorDiv((lonRight - 1) + (lonRight - 1), 2);
        double r = (lat - latRow0) / dlat + 1;
        double c = (lon - lonRow0) / dlon + 1;

        double[] kc = new double[4];
        double[] kr = new double[4];
        for (int k = 0; k < 4; k++) {
            kc[k] = keys(c - (c0 + k));
            kr[k] = keys(r - (r0 + k));
        }
        double result = 0;
        for (int a = 0; a < 4; a++) {
            int row = wrap(r0 + a, nlat);
            if (row < 0) {
                return Double.NaN;
            }
            double acc = 0;
            for (int b = 0; b < 4; b++) {
                int col = wrap(c0 + b, nlon);
                if (col < 0) {
                    return Double.NaN;
                }
                acc += values[row * nlon + col] * kc[b];
            }
            result += acc * kr[a];
        }
        return result;
    }

    /** Index with numpy semantics for negative values (wrap-around); -1 when out of range. */
    private static int wrap(int idx, int n) {
        if (idx < -n || idx >= n) {
            return -1;
        }
        return idx < 0 ? idx + n : idx;
    }

    private static double keys(double d) {
        d = Math.abs(d);
        if (d >= 0 && d <= 1) {
            return 1.5 * d * d * d - 2.5 * d * d + 1;
        }
        if (d >= 1 && d <= 2) {
            return -0.5 * d * d * d + 2.5 * d * d - 4 * d + 2;
        }
        return 0;
    }

    /** {@code numpy.searchsorted} on the arithmetic sequence {@code first + k*step}, k in [0, n). */
    private static int searchSorted(double x, double first, double step, int n, boolean right) {
        int lo = 0;
        int hi = n;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            double v = first + mid * step;
            boolean goRight = right ? (v <= x) : (v < x);
            if (goRight) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }
}
