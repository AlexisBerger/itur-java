package org.itur;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Compares the library with ITU-Rpy on the vectors in {@code src/test/resources/golden}
 * (see {@code tools/generate_golden.py}); one dynamic test per CSV file.
 *
 * <p>The relative tolerance defaults to 1e-9 (the vectors were produced from the same float32 map
 * data) and can be overridden with {@code -Ditur.tolerance=...}, e.g. 1e-5 for vectors generated
 * from the pristine float64 ITU-Rpy data.
 */
class GoldenTest {

    private static final double TOLERANCE = Double.parseDouble(System.getProperty("itur.tolerance", "1e-9"));
    /** Files computed by ITU-Rpy with adaptive numerical integration (scipy.integrate.quad). */
    private static final double QUAD_TOLERANCE = Math.max(TOLERANCE, 2e-5);

    @TestFactory
    Stream<DynamicTest> goldenVectors() throws IOException, URISyntaxException {
        Path dir = Path.of(GoldenTest.class.getResource("/golden").toURI());
        List<Path> files;
        try (Stream<Path> s = Files.list(dir)) {
            files = s.filter(p -> p.toString().endsWith(".csv")).sorted().toList();
        }
        return files.stream().map(p -> DynamicTest.dynamicTest(p.getFileName().toString(), () -> {
            double tol = p.getFileName().toString().startsWith("p618q") ? QUAD_TOLERANCE : TOLERANCE;
            GoldenCases.Report r = GoldenCases.check(p, tol, 1e-12);
            assertTrue(r.cases() > 0, "no vectors in " + p);
            assertTrue(r.failures().isEmpty(), r.failures().size() + " of " + r.cases() + " vectors differ from "
                + "ITU-Rpy in " + p.getFileName() + ", e.g.: "
                + r.failures().stream().limit(5).toList());
        }));
    }
}
