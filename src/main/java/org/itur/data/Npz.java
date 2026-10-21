package org.itur.data;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Minimal reader for NumPy {@code .npz} files holding a single array ({@code arr_0.npy}), which is the format
 * used by the ITU data files shipped with ITU-Rpy.
 */
public final class Npz {

    private static final Pattern DESCR = Pattern.compile("'descr'\\s*:\\s*'([^']*)'");
    private static final Pattern FORTRAN = Pattern.compile("'fortran_order'\\s*:\\s*(True|False)");
    private static final Pattern SHAPE = Pattern.compile("'shape'\\s*:\\s*\\(([^)]*)\\)");

    private Npz() {
    }

    /** Reads the first array of an {@code .npz} stream. */
    public static NdArray readNpz(InputStream in) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry = zip.getNextEntry();
            while (entry != null && !entry.getName().endsWith(".npy")) {
                entry = zip.getNextEntry();
            }
            if (entry == null) {
                throw new IOException("No .npy entry found in npz stream");
            }
            return readNpy(zip.readAllBytes());
        }
    }

    /** Parses a {@code .npy} byte block (format versions 1.x to 3.x) into a 2-D array of doubles. */
    public static NdArray readNpy(byte[] bytes) throws IOException {
        if (bytes.length < 10 || (bytes[0] & 0xFF) != 0x93 || bytes[1] != 'N' || bytes[2] != 'U'
                || bytes[3] != 'M' || bytes[4] != 'P' || bytes[5] != 'Y') {
            throw new IOException("Not a NumPy array (bad magic)");
        }
        int major = bytes[6] & 0xFF;
        int headerLen;
        int headerStart;
        if (major == 1) {
            headerLen = (bytes[8] & 0xFF) | ((bytes[9] & 0xFF) << 8);
            headerStart = 10;
        } else {
            headerLen = (bytes[8] & 0xFF) | ((bytes[9] & 0xFF) << 8) | ((bytes[10] & 0xFF) << 16)
                    | ((bytes[11] & 0xFF) << 24);
            headerStart = 12;
        }
        String header = new String(bytes, headerStart, headerLen, StandardCharsets.ISO_8859_1);

        Matcher md = DESCR.matcher(header);
        Matcher mf = FORTRAN.matcher(header);
        Matcher ms = SHAPE.matcher(header);
        if (!md.find() || !mf.find() || !ms.find()) {
            throw new IOException("Unsupported NumPy header: " + header);
        }
        String descr = md.group(1);
        boolean fortran = "True".equals(mf.group(1));

        String[] dims = ms.group(1).trim().isEmpty() ? new String[0] : ms.group(1).trim().split("\\s*,\\s*");
        int rows;
        int cols;
        if (dims.length == 0) {
            rows = 1;
            cols = 1;
        } else if (dims.length == 1) {
            rows = 1;
            cols = Integer.parseInt(dims[0]);
        } else if (dims.length == 2) {
            rows = Integer.parseInt(dims[0]);
            cols = Integer.parseInt(dims[1]);
        } else {
            throw new IOException("Only arrays with up to 2 dimensions are supported, got shape " + ms.group(1));
        }

        ByteOrder order = descr.startsWith(">") ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
        String type = descr.replaceAll("^[<>|=]", "");
        int count = rows * cols;
        ByteBuffer buf = ByteBuffer.wrap(bytes, headerStart + headerLen, bytes.length - headerStart - headerLen)
                .order(order);
        double[] flat = new double[count];
        switch (type) {
            case "f8" -> {
                for (int i = 0; i < count; i++) {
                    flat[i] = buf.getDouble();
                }
            }
            case "f4" -> {
                for (int i = 0; i < count; i++) {
                    flat[i] = buf.getFloat();
                }
            }
            case "i8" -> {
                for (int i = 0; i < count; i++) {
                    flat[i] = buf.getLong();
                }
            }
            case "i4" -> {
                for (int i = 0; i < count; i++) {
                    flat[i] = buf.getInt();
                }
            }
            case "i2" -> {
                for (int i = 0; i < count; i++) {
                    flat[i] = buf.getShort();
                }
            }
            case "u1", "b1" -> {
                for (int i = 0; i < count; i++) {
                    flat[i] = buf.get() & 0xFF;
                }
            }
            default -> throw new IOException("Unsupported NumPy dtype: " + descr);
        }

        if (fortran && rows > 1 && cols > 1) {
            double[] rowMajor = new double[count];
            for (int c = 0; c < cols; c++) {
                for (int r = 0; r < rows; r++) {
                    rowMajor[r * cols + c] = flat[c * rows + r];
                }
            }
            flat = rowMajor;
        }
        return new NdArray(rows, cols, flat);
    }

    /** Serialises a 2-D array as an uncompressed {@code .npz}; used by the tests only. */
    public static byte[] writeNpz(NdArray a) throws IOException {
        String header = "{'descr': '<f8', 'fortran_order': False, 'shape': (" + a.rows() + ", " + a.cols() + "), }";
        int total = 10 + header.length() + 1;
        int pad = (64 - total % 64) % 64;
        StringBuilder sb = new StringBuilder(header);
        sb.append(" ".repeat(pad)).append('\n');
        byte[] h = sb.toString().getBytes(StandardCharsets.ISO_8859_1);
        ByteBuffer npy = ByteBuffer.allocate(10 + h.length + a.data().length * 8).order(ByteOrder.LITTLE_ENDIAN);
        npy.put((byte) 0x93).put("NUMPY".getBytes(StandardCharsets.ISO_8859_1)).put((byte) 1).put((byte) 0);
        npy.putShort((short) h.length).put(h);
        for (double d : a.data()) {
            npy.putDouble(d);
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(bos)) {
            zos.putNextEntry(new ZipEntry("arr_0.npy"));
            zos.write(npy.array());
            zos.closeEntry();
        }
        return bos.toByteArray();
    }
}
