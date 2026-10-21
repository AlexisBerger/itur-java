package org.itur.grid;

import org.itur.data.DataStore;
import org.itur.data.NdArray;
import org.itur.util.Maths;

/**
 * Bilinear interpolation on a regular latitude / longitude grid (Recommendation ITU-R P.1144), equivalent to
 * {@code scipy.interpolate.RegularGridInterpolator(method="linear", bounds_error=False)} as used by ITU-Rpy.
 * Points outside the grid give NaN.
 */
public final class BilinearGrid implements GridInterpolator {

    private final double[] lat;
    private final double[] lon;
    private final double[] values;
    private final int nLat;
    private final int nLon;

    /**
     * @param latAscending strictly ascending latitude axis
     * @param lonAscending strictly ascending longitude axis
     * @param values       row-major values, {@code values[i * lon.length + j]} at (lat[i], lon[j])
     */
    public BilinearGrid(double[] latAscending, double[] lonAscending, double[] values) {
        this.lat = latAscending;
        this.lon = lonAscending;
        this.values = values;
        this.nLat = latAscending.length;
        this.nLon = lonAscending.length;
        if (values.length != nLat * nLon) {
            throw new IllegalArgumentException("Grid shape mismatch");
        }
    }

    /** Builds the interpolator from ITU-style data files (latitude, longitude and value matrices). */
    public static BilinearGrid load(String latFile, String lonFile, String dataFile) {
        NdArray la = DataStore.loadCachedArray(latFile);
        NdArray lo = DataStore.loadCachedArray(lonFile);
        NdArray va = DataStore.loadArray(dataFile);
        return fromArrays(la, lo, va);
    }

    public static BilinearGrid fromArrays(NdArray latMatrix, NdArray lonMatrix, NdArray vals) {
        if (latMatrix.rows() != vals.rows() || lonMatrix.cols() != vals.cols()) {
            throw new IllegalArgumentException("Latitude/longitude matrices do not match the data shape");
        }
        double[] latAxis = latMatrix.column(0);
        double[] lonAxis = lonMatrix.row(0);
        double[] data = vals.data().clone();
        int rows = vals.rows();
        int cols = vals.cols();

        if (latAxis[0] > latAxis[rows - 1]) { // descending latitude: flip rows
            double[] flipped = new double[data.length];
            double[] newLat = new double[rows];
            for (int i = 0; i < rows; i++) {
                newLat[i] = latAxis[rows - 1 - i];
                System.arraycopy(data, (rows - 1 - i) * cols, flipped, i * cols, cols);
            }
            latAxis = newLat;
            data = flipped;
        }
        if (lonAxis[0] > lonAxis[cols - 1]) { // descending longitude: flip columns
            double[] flipped = new double[data.length];
            double[] newLon = new double[cols];
            for (int j = 0; j < cols; j++) {
                newLon[j] = lonAxis[cols - 1 - j];
            }
            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < cols; j++) {
                    flipped[i * cols + j] = data[i * cols + cols - 1 - j];
                }
            }
            lonAxis = newLon;
            data = flipped;
        }
        return new BilinearGrid(latAxis, lonAxis, data);
    }

    @Override
    public double at(double la, double lo) {
        if (!(la >= lat[0] && la <= lat[nLat - 1] && lo >= lon[0] && lo <= lon[nLon - 1])) {
            return Double.NaN;
        }
        int i = Math.min(Math.max(Maths.searchSortedLeft(lat, la) - 1, 0), nLat - 2);
        int j = Math.min(Math.max(Maths.searchSortedLeft(lon, lo) - 1, 0), nLon - 2);
        double ty = (la - lat[i]) / (lat[i + 1] - lat[i]);
        double tx = (lo - lon[j]) / (lon[j + 1] - lon[j]);
        double v00 = values[i * nLon + j];
        double v01 = values[i * nLon + j + 1];
        double v10 = values[(i + 1) * nLon + j];
        double v11 = values[(i + 1) * nLon + j + 1];
        return v00 * (1.0 - ty) * (1.0 - tx) + v01 * (1.0 - ty) * tx + v10 * ty * (1.0 - tx) + v11 * ty * tx;
    }
}
