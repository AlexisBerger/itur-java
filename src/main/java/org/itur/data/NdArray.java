package org.itur.data;

/**
 * A dense, row-major, two-dimensional array of doubles (1-D arrays are stored as a single row).
 *
 * @param rows number of rows
 * @param cols number of columns
 * @param data row-major values, length {@code rows * cols}
 */
public record NdArray(int rows, int cols, double[] data) {

    public NdArray {
        if (data.length != rows * cols) {
            throw new IllegalArgumentException("data length " + data.length + " != " + rows + "x" + cols);
        }
    }

    public double get(int row, int col) {
        return data[row * cols + col];
    }

    /** First column, i.e. {@code a[:, 0]}. */
    public double[] column(int col) {
        double[] out = new double[rows];
        for (int i = 0; i < rows; i++) {
            out[i] = data[i * cols + col];
        }
        return out;
    }

    /** One row, i.e. {@code a[row, :]}. */
    public double[] row(int row) {
        double[] out = new double[cols];
        System.arraycopy(data, row * cols, out, 0, cols);
        return out;
    }
}
