package org.itur.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import org.junit.jupiter.api.Test;

/**
 * Checks the P.676-13 implementation against the validation tables published with the ITU-R
 * recommendation (as distributed in ITU-Rpy's {@code test_data}). The criterion is the one of
 * ITU-Rpy's own test-suite: absolute error below 5e-6 (five decimal places).
 */
class P676OfficialTablesTest {

    private static final double TOL = 5e-6;

    private static int check(String resource, String column, ToDoubleFunction<Map<String, Double>> model)
            throws Exception {
        int rows = 0;
        try (InputStream in = P676OfficialTablesTest.class.getResourceAsStream(resource);
             BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.ISO_8859_1))) {
            String[] head = r.readLine().split(",");
            r.readLine(); // units
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] t = line.split(",");
                Map<String, Double> row = new HashMap<>();
                for (int i = 0; i < head.length; i++) {
                    row.put(head[i].trim(), Double.parseDouble(t[i].trim()));
                }
                assertEquals(row.get(column).doubleValue(), model.applyAsDouble(row), TOL, resource + " row " + line);
                rows++;
            }
        }
        return rows;
    }

    @Test
    void specificAttenuationTable() throws Exception {
        String res = "/official/676/ITURP676-13_gamma.csv";
        int n = check(res, "gamma0", m -> P676.gamma0Exact(m.get("f"), m.get("P"), m.get("rho"), m.get("T")));
        check(res, "gammaw", m -> P676.gammaWExact(m.get("f"), m.get("P"), m.get("rho"), m.get("T")));
        check(res, "gamma", m -> P676.gammaExact(m.get("f"), m.get("P"), m.get("rho"), m.get("T")));
        assertTrue(n > 300);
    }

    @Test
    void slantPathAnnex2Table() throws Exception {
        int n = check("/official/676/ITURP676-13_A_gas.csv", "A_gas",
            m -> P676.gaseousAttenuationSlantPath(m.get("f"), m.get("el"), m.get("rho"), m.get("P"), m.get("T"),
                null, null, P676.Mode.APPROX));
        assertEquals(10, n);
    }

    @Test
    void slantPathAnnex1Sheet() {
        // "A_Gas_A1_2.2.1a" sheet: f = 28 GHz, el = 30 deg, rho = 7.5, P = 1013.25 (dry), T = 288.15
        assertEquals(0.47081173472870474,
            P676.gaseousAttenuationSlantPath(28, 30, 7.5, 1013.25, 288.15, null, null, P676.Mode.EXACT), TOL);
    }
}
