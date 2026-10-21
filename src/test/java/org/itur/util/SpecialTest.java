package org.itur.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SpecialTest {

    @Test
    void erfcMatchesKnownValues() {
        assertEquals(1.0, Special.erfc(0), 1e-15);
        assertEquals(0.15729920705028513, Special.erfc(1), 1e-15);
        assertEquals(0.004677734981047266, Special.erfc(2), 1e-14 * 0.004677734981047266);
        assertEquals(2.209049699858544e-05, Special.erfc(3), 1e-14 * 2.209049699858544e-05);
        assertEquals(2 - 0.15729920705028513, Special.erfc(-1), 1e-15);
        assertEquals(0.0, Special.erfc(40), 0.0);
    }

    @Test
    void normalDistribution() {
        assertEquals(0.5, Special.normSf(0), 1e-16);
        assertEquals(0.025, Special.normSf(1.959963984540054), 1e-15);
        assertEquals(1.959963984540054, Special.normPpf(0.975), 1e-13);
        assertEquals(-1.2815515655446004, Special.normPpf(0.1), 1e-13);
        assertTrue(Double.isNaN(Special.normPpf(1.5)));
        assertEquals(Double.POSITIVE_INFINITY, Special.normPpf(1.0));
        // round trip in the tails
        for (double p : new double[] {1e-12, 1e-6, 0.01, 0.3, 0.7, 0.99, 1 - 1e-9}) {
            assertEquals(p, Special.normCdf(Special.normPpf(p)), 1e-12 * Math.max(p, 1 - p) + 1e-15);
        }
    }

    @Test
    void bivariateNormalTail() {
        // independent variables: product of the marginal tails
        assertEquals(Special.normSf(0.7) * Special.normSf(1.3), Special.bivariateNormalSf(0.7, 1.3, 0.0), 1e-14);
        // perfectly correlated limit approaches the marginal tail
        assertEquals(Special.normSf(1.0), Special.bivariateNormalSf(1.0, 1.0, 0.999999999), 1e-5);
        // orthant probability for h = k = 0 is 1/4 + asin(rho)/(2 pi)
        assertEquals(0.25 + Math.asin(0.6) / (2 * Math.PI), Special.bivariateNormalSf(0, 0, 0.6), 1e-14);
    }

    @Test
    void numpyCompatibleArithmetic() {
        assertEquals(-1.0, Special.floorDiv(-0.5, 1.125));
        assertEquals(2.0, Special.floorDiv(2.5, 1.125));
        assertEquals(0.0, Special.floorDiv(1.0, 1.125));
        assertEquals(350.0, Special.mod(-10, 360), 0.0);
        assertEquals(10.0, Special.mod(370, 360), 0.0);
        assertEquals(0.0, Special.mod(360, 360), 0.0);
    }

    @Test
    void bisectFindsRootsLikeScipy() {
        assertEquals(Math.sqrt(2), Special.bisect(x -> x * x - 2, 0, 2, 1e-12), 1e-11);
        assertThrows(IllegalArgumentException.class, () -> Special.bisect(x -> x * x + 1, 0, 2, 1e-12));
    }
}
