package org.itur;

import java.util.function.DoubleUnaryOperator;

/**
 * Numerical helpers that the Java standard library does not provide and that the ITU methods
 * need: complementary error function, standard normal survival function / quantile, the
 * bisection root finder used by ITU-Rpy, bivariate normal tail probability, and numpy-compatible
 * floor division / modulo.
 */
public final class Special {

    private Special() {
    }

    private static final double SQRT_PI = Math.sqrt(Math.PI);
    private static final double SQRT2 = Math.sqrt(2.0);

    // ------------------------------------------------------------------------------------
    // numpy-compatible arithmetic
    // ------------------------------------------------------------------------------------

    /** {@code numpy.floor_divide(a, b)} for floating point numbers. */
    public static double floorDiv(double a, double b) {
        double mod = a % b;
        double div = (a - mod) / b;
        if (mod != 0 && ((b < 0) != (mod < 0))) {
            div -= 1.0;
        }
        if (div != 0) {
            double fd = Math.floor(div);
            if (div - fd > 0.5) {
                fd += 1.0;
            }
            return fd;
        }
        return Math.copySign(0.0, a / b);
    }

    /** {@code numpy.mod(a, b)} for floating point numbers (result has the sign of {@code b}). */
    public static double mod(double a, double b) {
        double mod = a % b;
        if (mod != 0) {
            if ((b < 0) != (mod < 0)) {
                mod += b;
            }
        } else {
            mod = Math.copySign(0.0, b);
        }
        return mod;
    }

    // ------------------------------------------------------------------------------------
    // erfc and friends
    // ------------------------------------------------------------------------------------

    /** Complementary error function, accurate to about 1e-15 (relative). */
    public static double erfc(double x) {
        if (Double.isNaN(x)) {
            return Double.NaN;
        }
        if (x < 0) {
            return 2.0 - erfc(-x);
        }
        if (x < 1.0) {
            return 1.0 - erfSeries(x);
        }
        if (x > 27.3) {
            return 0.0;
        }
        return erfcContinuedFraction(x);
    }

    /** erf(x) for 0 <= x < ~1.5 via its Maclaurin series (alternating, no cancellation issue here). */
    private static double erfSeries(double x) {
        double x2 = x * x;
        double term = x;
        double sum = x;
        for (int n = 1; n < 200; n++) {
            term *= -x2 / n;
            double add = term / (2 * n + 1);
            sum += add;
            if (Math.abs(add) < 1e-17 * Math.abs(sum)) {
                break;
            }
        }
        return 2.0 / SQRT_PI * sum;
    }

    /** erfc(x) for x >= 1 by the Laplace continued fraction (modified Lentz algorithm). */
    private static double erfcContinuedFraction(double x) {
        // erfc(x) = exp(-x^2)/sqrt(pi) * 1/(x + (1/2)/(x + 1/(x + (3/2)/(x + 2/(x + ...)))))
        final double tiny = 1e-300;
        double f = x;
        double c = x;
        double d = 0.0;
        for (int k = 1; k < 5000; k++) {
            double a = k / 2.0;
            d = x + a * d;
            d = (d == 0) ? tiny : d;
            c = x + a / c;
            c = (c == 0) ? tiny : c;
            d = 1.0 / d;
            double delta = c * d;
            f *= delta;
            if (Math.abs(delta - 1.0) < 1e-16) {
                break;
            }
        }
        return Math.exp(-x * x) / (SQRT_PI * f);
    }

    /** Standard normal survival function {@code Q(x) = P(X > x)} ({@code scipy.stats.norm.sf}). */
    public static double normSf(double x) {
        return 0.5 * erfc(x / SQRT2);
    }

    /** Standard normal CDF ({@code scipy.stats.norm.cdf}). */
    public static double normCdf(double x) {
        return 0.5 * erfc(-x / SQRT2);
    }

    /** Standard normal quantile function ({@code scipy.stats.norm.ppf}). */
    public static double normPpf(double p) {
        if (Double.isNaN(p) || p < 0 || p > 1) {
            return Double.NaN;
        }
        if (p == 0) {
            return Double.NEGATIVE_INFINITY;
        }
        if (p == 1) {
            return Double.POSITIVE_INFINITY;
        }
        // Acklam's rational approximation (rel. error 1.15e-9) ...
        final double[] a = {-3.969683028665376e+01, 2.209460984245205e+02, -2.759285104469687e+02,
            1.383577518672690e+02, -3.066479806614716e+01, 2.506628277459239e+00};
        final double[] b = {-5.447609879822406e+01, 1.615858368580409e+02, -1.556989798598866e+02,
            6.680131188771972e+01, -1.328068155288572e+01};
        final double[] c = {-7.784894002430293e-03, -3.223964580411365e-01, -2.400758277161838e+00,
            -2.549732539343734e+00, 4.374664141464968e+00, 2.938163982698783e+00};
        final double[] d = {7.784695709041462e-03, 3.224671290700398e-01, 2.445134137142996e+00,
            3.754408661907416e+00};
        final double pLow = 0.02425;
        double x;
        if (p < pLow) {
            double q = Math.sqrt(-2 * Math.log(p));
            x = (((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5])
                / ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1);
        } else if (p <= 1 - pLow) {
            double q = p - 0.5;
            double r = q * q;
            x = (((((a[0] * r + a[1]) * r + a[2]) * r + a[3]) * r + a[4]) * r + a[5]) * q
                / (((((b[0] * r + b[1]) * r + b[2]) * r + b[3]) * r + b[4]) * r + 1);
        } else {
            double q = Math.sqrt(-2 * Math.log(1 - p));
            x = -(((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5])
                / ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1);
        }
        // ... refined with Halley steps down to machine precision.
        for (int i = 0; i < 3; i++) {
            // e = Phi(x) - p, evaluated through the smaller tail for accuracy
            double e = (x < 0) ? normCdf(x) - p : (1 - p) - normSf(x);
            double u = e * Math.sqrt(2 * Math.PI) * Math.exp(x * x / 2);
            x = x - u / (1 + x * u / 2);
        }
        return x;
    }

    // ------------------------------------------------------------------------------------
    // Root finding
    // ------------------------------------------------------------------------------------

    /**
     * Bisection root finder, a literal port of {@code scipy.optimize.bisect}
     * ({@code rtol = 4 * eps}, {@code maxiter = 100}).
     */
    public static double bisect(DoubleUnaryOperator f, double a, double b, double xtol) {
        return bisect(f, a, b, xtol, 100);
    }

    /** Same as {@link #bisect(DoubleUnaryOperator, double, double, double)} with a custom iteration limit. */
    public static double bisect(DoubleUnaryOperator f, double a, double b, double xtol, int maxiter) {
        final double rtol = 4 * Math.ulp(1.0);
        double tol = xtol + rtol * (Math.abs(a) + Math.abs(b));
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
        double dm = b - a;
        for (int i = 0; i < maxiter; i++) {
            dm *= 0.5;
            double xm = a + dm;
            double fm = f.applyAsDouble(xm);
            if (fm * fa >= 0) {
                a = xm;
            }
            if (fm == 0 || Math.abs(dm) < tol) {
                return xm;
            }
        }
        throw new IllegalStateException("bisect failed to converge");
    }

    // ------------------------------------------------------------------------------------
    // Bivariate normal
    // ------------------------------------------------------------------------------------

    private static final double[] GL_X = new double[64];
    private static final double[] GL_W = new double[64];

    static {
        // 64-point Gauss-Legendre nodes/weights on [-1, 1] by Newton iteration on P_n.
        int n = 64;
        for (int i = 0; i < n / 2; i++) {
            double z = Math.cos(Math.PI * (i + 0.75) / (n + 0.5));
            double pp = 0;
            for (int it = 0; it < 100; it++) {
                double p1 = 1.0;
                double p2 = 0.0;
                for (int j = 0; j < n; j++) {
                    double p3 = p2;
                    p2 = p1;
                    p1 = ((2.0 * j + 1.0) * z * p2 - j * p3) / (j + 1);
                }
                pp = n * (z * p1 - p2) / (z * z - 1.0);
                double z1 = z;
                z = z1 - p1 / pp;
                if (Math.abs(z - z1) < 1e-16) {
                    break;
                }
            }
            GL_X[i] = -z;
            GL_X[n - 1 - i] = z;
            GL_W[i] = 2.0 / ((1.0 - z * z) * pp * pp);
            GL_W[n - 1 - i] = GL_W[i];
        }
    }

    /**
     * Upper-orthant probability {@code P(X > h, Y > k)} of a standard bivariate normal with
     * correlation {@code rho}. This is the quantity ITU-Rpy evaluates with nested adaptive
     * quadrature; here it is computed with Plackett's identity
     * <pre>
     *   Q(h,k;rho) = Q(h) Q(k) + 1/(2 pi) * Integral_0^asin(rho) exp(-(h^2 + k^2 - 2 h k sin t) / (2 cos^2 t)) dt
     * </pre>
     * integrated with a composite 64-point Gauss-Legendre rule.
     */
    public static double bivariateNormalSf(double h, double k, double rho) {
        double tMax = Math.asin(rho);
        int panels = Math.max(1, (int) Math.ceil(Math.abs(tMax) / 0.4));
        double sum = 0;
        double width = tMax / panels;
        for (int p = 0; p < panels; p++) {
            double a = p * width;
            double half = width / 2;
            double mid = a + half;
            double panelSum = 0;
            for (int i = 0; i < GL_X.length; i++) {
                double t = mid + half * GL_X[i];
                double cos = Math.cos(t);
                double arg = -(h * h + k * k - 2 * h * k * Math.sin(t)) / (2 * cos * cos);
                panelSum += GL_W[i] * Math.exp(arg);
            }
            sum += panelSum * half;
        }
        return normSf(h) * normSf(k) + sum / (2 * Math.PI);
    }
}
