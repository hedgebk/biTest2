package bthdg.tres.alg;

import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;

public class Interpolator {
    private final SplineInterpolator m_spline = new SplineInterpolator();
    private final double m_x[] = new double[3];
    private final double m_y[] = new double[3];

    public PolynomialSplineFunction interpolate(Long x1, Double y1, Long x2, Double y2, Long x3, Double y3) {
        m_x[0] = x1;
        m_y[0] = y1;
//                System.out.println(" x1=" + x1 + " y1=" + y1);
        m_x[1] = x2;
        m_y[1] = y2;
//                System.out.println(" x2=" + x2 + " y2=" + y2);
        m_x[2] = x3;
        m_y[2] = y3;
//                System.out.println(" x3=" + x3 + " y3=" + y3);

        PolynomialSplineFunction f = m_spline.interpolate(m_x, m_y);
        return f;
    }
}
