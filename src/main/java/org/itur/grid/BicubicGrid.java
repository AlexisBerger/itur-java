package org.itur.grid;

import org.itur.data.DataStore;
import org.itur.data.NdArray;
import org.itur.util.Maths;

/**
 * Bicubic (cubic convolution, a = -0.5) interpolation on a regular grid, as defined in Recommendation
 * ITU-R P.1144 and implemented by ITU-Rpy's {@code bicubic_2D_interpolator}. The grid carries one extra ring of
 * points around the area of validity (e.g. -90.5..90.5 / -0.5..360.5 for P.1511), which the 4x4 stencil needs.
 */
public final class BicubicGrid implements GridInterpolator {

    private final double[] latRow; // lat[1:-1]
    private final double[] lonRow; // lon[1:-1]
    private final double[] data;
    private final int nRows;
    private final int nCols;

    /** @param latAscending full ascending latitude axis; @param lonAscending full ascending longitude axis. */
    public BicubicGrid(double[] latAscending, double[] lonAscending, double[] values) {
        this.nRows = latAscending.length;
        this.nCols = lonAscending.length;
        if (values.length != nRows * nCols || nRows < 5 || nCols < 5) {
            throw new IllegalArgumentException("Grid shape mismatch");
        }
        this.latRow = java.util.Arrays.copyOfRange(latAscending, 1, nRows - 1);
        this.lonRow = java.util.Arrays.copyOfRange(lonAscending, 1, nCols - 1);
        this.data = values;
    }

    public static BicubicGrid load(String latFile, String lonFile, String dataFile) {
        NdArray la = DataStore.loadCachedArray(latFile);
        NdArray lo = DataStore.loadCachedArray(lonFile);
        NdArray va = DataStore.loadArray(dataFile);
        double[] latAxis = la.column(0);
        double[] lonAxis = lo.row(0);
        double[] vals = va.data();
        int rows = va.rows();
        int cols = va.cols();
        if (latAxis[0] > latAxis[rows - 1]) { // ITU files list latitudes from north to south
            double[] flipped = new double[vals.length];
            double[] newLat = new double[rows];
            for (int i = 0; i < rows; i++) {
                newLat[i] = latAxis[rows - 1 - i];
                System.arraycopy(vals, (rows - 1 - i) * cols, flipped, i * cols, cols);
            }
            latAxis = newLat;
            vals = flipped;
        }
        return new BicubicGrid(latAxis, lonAxis, vals);
    }

    private static double kernel(double d) {
        d = Math.abs(d);
        if (d <= 1.0) {
            return 1.5 * d * d * d - 2.5 * d * d + 1.0;
        } else if (d <= 2.0) {
            return -0.5 * d * d * d + 2.5 * d * d - 4.0 * d + 2.0;
        }
        return 0.0;
    }

    @Override
    public double at(double lat, double lon) {
        if (Double.isNaN(lat) || Double.isNaN(lon)) {
            return Double.NaN;
        }
        int latRight = Maths.searchSortedRight(latRow, lat);
        int latLeft = Maths.searchSortedLeft(latRow, lat);
        int r0 = Math.floorDiv((latRight - 1) + (latLeft - 1), 2);
        int c0 = Math.floorDiv((Maths.searchSortedRight(lonRow, lon) - 1) * 2, 2);

        double dLat = latRow[1] - latRow[0];
        double dLon = lonRow[1] - lonRow[0];
        double r = (lat - latRow[0]) / dLat + 1.0;
        double c = (lon - lonRow[0]) / dLon + 1.0;

        if (r0 < 0 || r0 + 3 >= nRows || c0 < 0 || c0 + 3 >= nCols) {
            return Double.NaN; // outside the padded grid
        }
        double k0 = kernel(c - c0);
        double k1 = kernel(c - (c0 + 1));
        double k2 = kernel(c - (c0 + 2));
        double k3 = kernel(c - (c0 + 3));
        double[] rowSums = new double[4];
        for (int a = 0; a < 4; a++) {
            int base = (r0 + a) * nCols + c0;
            rowSums[a] = data[base] * k0 + data[base + 1] * k1 + data[base + 2] * k2 + data[base + 3] * k3;
        }
        return rowSums[0] * kernel(r - r0) + rowSums[1] * kernel(r - (r0 + 1))
                + rowSums[2] * kernel(r - (r0 + 2)) + rowSums[3] * kernel(r - (r0 + 3));
    }
}
