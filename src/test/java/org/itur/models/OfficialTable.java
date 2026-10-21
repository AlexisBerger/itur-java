package org.itur.models;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One validation table published with an ITU-R Recommendation (as distributed in ITU-Rpy's
 * {@code test_data}): a header line with the column names, a line of units, then one row per case.
 * Empty cells are read as {@code NaN}.
 */
final class OfficialTable {

    /** One row of the table. */
    record Row(Map<String, Double> values, String text) {
        double get(String column) {
            Double v = values.get(column);
            if (v == null) {
                throw new IllegalArgumentException("no column " + column);
            }
            return v;
        }

        /** A value that may be absent: {@code null} when the cell is empty. */
        Double opt(String column) {
            double v = get(column);
            return Double.isNaN(v) ? null : v;
        }
    }

    private OfficialTable() {
    }

    /** Reads {@code /official/<resource>} from the test classpath. */
    static List<Row> read(String resource) throws IOException {
        List<Row> rows = new ArrayList<>();
        try (InputStream in = OfficialTable.class.getResourceAsStream("/official/" + resource)) {
            if (in == null) {
                throw new IOException("missing test resource " + resource);
            }
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.ISO_8859_1));
            String[] head = r.readLine().split(",", -1);
            r.readLine(); // units
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] t = line.split(",", -1);
                Map<String, Double> m = new HashMap<>();
                for (int i = 0; i < head.length && i < t.length; i++) {
                    String s = t[i].trim();
                    m.put(head[i].trim(), s.isEmpty() ? Double.NaN : Double.parseDouble(s));
                }
                rows.add(new Row(m, line));
            }
        }
        return rows;
    }
}
