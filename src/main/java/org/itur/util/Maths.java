package org.itur.util;

import java.util.function.DoubleUnaryOperator;

/**
 * Numerical helpers that reproduce the behaviour of the NumPy / SciPy primitives used by ITU-Rpy.
 */
public final class Maths {

    /** Small quantity used to avoid log(0) and division by zero (same value as ITU-Rpy). */
    public static final double EPSILON = 1e-9;

    private static final double SQRT2 = Math.sqrt(2.0);
    private static final double SQRT_PI = Math.sqrt(Math.PI);
    private static final double SQRT_2PI = Math.sqrt(2.0 * Math.PI);

    private Maths() {
    }

    // ------------------------------------------------------------------ NumPy semantics

    /** {@code numpy.mod} for floats: the result has the sign of the divisor. */
    public static double mod(double a, double b) {
        double m = a % b;
        if (m != 0.0) {
            if ((b < 0) != (m < 0)) {
                m += b;
            }
        } else {
            m = Math.copySign(0.0, b);
        }
        return m;
    }

    /** {@code numpy.floor_divide} for floats (same algorithm as CPython's float floor division). */
    public static double floorDiv(double a, double b) {
        double mod = a % b;
        double div = (a - mod) / b;
        if (mod != 0.0 && ((b < 0) != (mod < 0))) {
            div -= 1.0;
        }
        double floordiv;
        if (div != 0.0) {
            floordiv = Math.floor(div);
            if (div - floordiv > 0.5) {
                floordiv += 1.0;
            }
        } else {
            floordiv = Math.copySign(0.0, a / b);
        }
        return floordiv;
    }

    /** Normalises a longitude to [0, 360), as done by every public ITU-Rpy function. */
    public static double lon360(double lon) {
        return mod(lon, 360.0);
    }

    /** Converts a longitude in [0, 360) to the [-180, 180] encoding used by some data sets. */
    public static double lon180(double lon360) {
        return lon360 > 180.0 ? lon360 - 360.0 : lon360;
    }

    /** {@code numpy.searchsorted(a, v, side='left')} on an ascending array. */
    public static int searchSortedLeft(double[] a, double v) {
        int lo = 0;
        int hi = a.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (a[mid] < v) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    /** {@code numpy.searchsorted(a, v, side='right')} on an ascending array. */
    public static int searchSortedRight(double[] a, double v) {
        int lo = 0;
        int hi = a.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (a[mid] <= v) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    public static double deg2rad(double deg) {
        return Math.toRadians(deg);
    }

    public static double rad2deg(double rad) {
        return Math.toDegrees(rad);
    }

    // ------------------------------------------------------------------ Normal distribution

    /** Complementary error function, accurate to about 1e-15 (relative) over the whole real line. */
    public static double erfc(double x) {
        if (Double.isNaN(x)) {
            return Double.NaN;
        }
        if (x < 0) {
            return 2.0 - erfc(-x);
        }
        if (x < 1.5) {
            return 1.0 - erfSeries(x);
        }
        if (x > 27.0) {
            return 0.0;
        }
        return erfcContinuedFraction(x);
    }

    private static double erfSeries(double x) {
        // erf(x) = 2/sqrt(pi) * exp(-x^2) * sum_{n>=0} 2^n x^(2n+1) / (2n+1)!!   (all terms positive)
        double x2 = x * x;
        double term = x;
        double sum = x;
        for (int n = 1; n < 400; n++) {
            term *= 2.0 * x2 / (2 * n + 1);
            sum += term;
            if (term < 1e-18 * sum) {
                break;
            }
        }
        return 2.0 / SQRT_PI * Math.exp(-x2) * sum;
    }

    private static double erfcContinuedFraction(double x) {
        // erfc(x) = exp(-x^2)/sqrt(pi) / (x + (1/2)/(x + (2/2)/(x + (3/2)/(x + ...))))
        double f = x;
        for (int k = 300; k >= 1; k--) {
            f = x + (k / 2.0) / f;
        }
        return Math.exp(-x * x) / (SQRT_PI * f);
    }

    /** Standard normal cumulative distribution function. */
    public static double normCdf(double x) {
        return 0.5 * erfc(-x / SQRT2);
    }

    /** Standard normal survival function, {@code scipy.stats.norm.sf}. */
    public static double normSf(double x) {
        return 0.5 * erfc(x / SQRT2);
    }

    /** Inverse of the standard normal CDF, {@code scipy.stats.norm.ppf}. */
    public static double normPpf(double q) {
        if (Double.isNaN(q) || q < 0.0 || q > 1.0) {
            return Double.NaN;
        }
        if (q == 0.0) {
            return Double.NEGATIVE_INFINITY;
        }
        if (q == 1.0) {
            return Double.POSITIVE_INFINITY;
        }
        if (q == 0.5) {
            return 0.0;
        }
        if (q > 0.5) {
            return -normPpf(1.0 - q); // 1 - q is exact for q in [0.5, 1]
        }
        // Initial guess: Abramowitz & Stegun 26.2.23 (|error| < 4.5e-4), refined by safeguarded Newton.
        double t = Math.sqrt(-2.0 * Math.log(q));
        double x = -(t - (2.515517 + 0.802853 * t + 0.010328 * t * t)
                / (1.0 + 1.432788 * t + 0.189269 * t * t + 0.001308 * t * t * t));
        double lo = -40.0;
        double hi = 0.0;
        for (int i = 0; i < 200; i++) {
            double fx = normCdf(x) - q;
            if (fx > 0) {
                hi = x;
            } else {
                lo = x;
            }
            double pdf = Math.exp(-0.5 * x * x) / SQRT_2PI;
            double next = pdf > 0 ? x - fx / pdf : Double.NaN;
            if (!(next > lo && next < hi)) {
                next = 0.5 * (lo + hi);
            }
            if (Math.abs(next - x) <= 1e-16 * Math.abs(next)) {
                return next;
            }
            x = next;
        }
        return x;
    }

    // ------------------------------------------------------------------ Bivariate normal

    private static final double[] GL_NODES = new double[64];
    private static final double[] GL_WEIGHTS = new double[64];

    static {
        int n = GL_NODES.length;
        for (int i = 0; i < n; i++) {
            double z = Math.cos(Math.PI * (i + 0.75) / (n + 0.5));
            double pp = 1.0;
            for (int iter = 0; iter < 100; iter++) {
                double p1 = 1.0;
                double p2 = 0.0;
                for (int j = 0; j < n; j++) {
                    double p3 = p2;
                    p2 = p1;
                    p1 = ((2.0 * j + 1.0) * z * p2 - j * p3) / (j + 1.0);
                }
                pp = n * (z * p1 - p2) / (z * z - 1.0);
                double z1 = z;
                z = z1 - p1 / pp;
                if (Math.abs(z - z1) < 1e-15) {
                    break;
                }
            }
            GL_NODES[i] = z;
            GL_WEIGHTS[i] = 2.0 / ((1.0 - z * z) * pp * pp);
        }
    }

    private static double gaussLegendre(DoubleUnaryOperator f, double a, double b, int panels) {
        double total = 0.0;
        double width = (b - a) / panels;
        for (int p = 0; p < panels; p++) {
            double lo = a + p * width;
            double mid = lo + 0.5 * width;
            double half = 0.5 * width;
            double s = 0.0;
            for (int i = 0; i < GL_NODES.length; i++) {
                s += GL_WEIGHTS[i] * f.applyAsDouble(mid + half * GL_NODES[i]);
            }
            total += s * half;
        }
        return total;
    }

    /**
     * Upper-tail probability of a standard bivariate normal distribution,
     * {@code P(X > h, Y > k)} with correlation {@code rho}. This is the quantity ITU-Rpy obtains with a nested
     * {@code scipy.integrate.quad}; here it is computed with Plackett's identity
     * (one smooth 1-D integral, Gauss-Legendre) which is accurate to about 1e-14.
     */
    public static double bivariateNormalUpper(double h, double k, double rho) {
        if (Double.isNaN(h) || Double.isNaN(k) || Double.isNaN(rho)) {
            return Double.NaN;
        }
        if (rho >= 1.0) {
            return normSf(Math.max(h, k));
        }
        if (rho <= -1.0) {
            return Math.max(0.0, normCdf(-h) - normCdf(k));
        }
        double product = normSf(h) * normSf(k);
        if (rho == 0.0) {
            return product;
        }
        double asr = Math.asin(rho);
        double dh = h - k;
        double hk = h * k;
        double integral = gaussLegendre(theta -> {
            double s = Math.sin(theta);
            double c2 = 1.0 - s * s;
            if (c2 <= 0.0) {
                return 0.0;
            }
            double expo = dh * dh / (2.0 * c2) + hk / (1.0 + s);
            return Math.exp(-expo);
        }, 0.0, asr, 8);
        return product + integral / (2.0 * Math.PI);
    }

    // ------------------------------------------------------------------ Root finding

    /**
     * Bisection with the semantics of {@code scipy.optimize.bisect}.
     *
     * @throws IllegalArgumentException if f(a) and f(b) have the same sign
     * @throws ArithmeticException      if it does not converge in {@code maxIter} iterations
     */
    public static double bisect(DoubleUnaryOperator f, double a, double b, double xtol, double rtol, int maxIter) {
        double fa = f.applyAsDouble(a);
        double fb = f.applyAsDouble(b);
        if (fa * fb > 0) {
            throw new IllegalArgumentException("f(a) and f(b) must have different signs");
        }
        if (fa == 0) {
            return a;
        }
        if (fb == 0) {
            return b;
        }
        double xa = a;
        double dm = b - a;
        for (int i = 0; i < maxIter; i++) {
            dm *= 0.5;
            double xm = xa + dm;
            double fm = f.applyAsDouble(xm);
            if (fm * fa >= 0) {
                xa = xm;
            }
            if (fm == 0 || Math.abs(dm) < xtol + rtol * Math.abs(xm)) {
                return xm;
            }
        }
        throw new ArithmeticException("bisect failed to converge in " + maxIter + " iterations");
    }
}
