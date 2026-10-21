package org.itur.data;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Locates and loads the ITU data files.
 *
 * <p>The files use exactly the layout of ITU-Rpy's {@code itur/data} folder ({@code 453/v13_lat_n.npz},
 * {@code 836/v6_v_1.npz}, ...). They are looked up, in order, in:
 * <ol>
 *   <li>the directory given to {@link #setDirectory(Path)};</li>
 *   <li>the {@code itur.data.dir} system property;</li>
 *   <li>the {@code ITUR_DATA_DIR} environment variable;</li>
 *   <li>the class path, under {@code /itur/data/}.</li>
 * </ol>
 */
public final class DataStore {

    private static final String CLASSPATH_ROOT = "/itur/data/";
    private static volatile Path directory;
    private static final ConcurrentHashMap<String, NdArray> AXIS_CACHE = new ConcurrentHashMap<>();

    private DataStore() {
    }

    /** Sets the folder holding the data files (highest priority). Pass {@code null} to reset. */
    public static void setDirectory(Path dir) {
        directory = dir;
        AXIS_CACHE.clear();
    }

    private static Path configuredDirectory() {
        Path d = directory;
        if (d != null) {
            return d;
        }
        String prop = System.getProperty("itur.data.dir");
        if (prop != null && !prop.isBlank()) {
            return Path.of(prop);
        }
        String env = System.getenv("ITUR_DATA_DIR");
        if (env != null && !env.isBlank()) {
            return Path.of(env);
        }
        return null;
    }

    private static InputStream open(String relativePath) throws IOException {
        Path dir = configuredDirectory();
        if (dir != null) {
            Path file = dir.resolve(relativePath);
            if (Files.isRegularFile(file)) {
                return Files.newInputStream(file);
            }
        }
        InputStream in = DataStore.class.getResourceAsStream(CLASSPATH_ROOT + relativePath);
        if (in == null) {
            throw new DataNotFoundException(relativePath, dir);
        }
        return in;
    }

    /** Whether a data file can be found. */
    public static boolean exists(String relativePath) {
        try (InputStream in = open(relativePath)) {
            return in != null;
        } catch (DataNotFoundException e) {
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    /** Loads an {@code .npz} data file. */
    public static NdArray loadArray(String relativePath) {
        try (InputStream in = open(relativePath)) {
            return Npz.readNpz(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + relativePath, e);
        }
    }

    /** Loads a small, frequently reused file (latitude / longitude axes) and caches it. */
    public static NdArray loadCachedArray(String relativePath) {
        return AXIS_CACHE.computeIfAbsent(relativePath, DataStore::loadArray);
    }

    /** Loads a comma-separated numeric text file, skipping {@code skipHeader} lines. */
    public static double[][] loadCsv(String relativePath, int skipHeader) {
        List<double[]> rows = new ArrayList<>();
        try (InputStream in = open(relativePath)) {
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            String[] lines = text.split("\\R");
            for (int i = skipHeader; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty()) {
                    continue;
                }
                String[] parts = line.split(",");
                double[] row = new double[parts.length];
                for (int j = 0; j < parts.length; j++) {
                    row[j] = Double.parseDouble(parts[j].trim());
                }
                rows.add(row);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + relativePath, e);
        }
        return rows.toArray(new double[0][]);
    }

    /** Thrown when a required data file is missing. */
    public static final class DataNotFoundException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public DataNotFoundException(String file, Path dir) {
            super("ITU data file '" + file + "' not found"
                    + (dir != null ? " in " + dir : "")
                    + ". Copy ITU-Rpy's 'itur/data' folders (453, 836, ...) to a directory and point to it with "
                    + "DataStore.setDirectory(...), -Ditur.data.dir=... or the ITUR_DATA_DIR environment variable.");
        }
    }
}
