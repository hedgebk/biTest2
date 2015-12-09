package bthdg.tres.ind;

import bthdg.ChartAxe;
import bthdg.exch.Direction;
import bthdg.tres.ChartPoint;
import bthdg.tres.TresExchData;
import bthdg.tres.alg.Interpolator;
import bthdg.tres.alg.TresAlgo;
import bthdg.util.Utils;
import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;

import java.awt.*;
import java.util.Map;

public class VelocityIndicator extends TresIndicator {
    private final VelocityTracker m_velocityTracker;

    public VelocityIndicator(TresAlgo algo, String name, long frameSizeMillis, double peakTolerance) {
        super(name, peakTolerance, algo);
        m_velocityTracker = new VelocityTracker(frameSizeMillis);
    }

    @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) { return null; }
    @Override public Color getColor() { return Color.orange; }
    @Override protected void preDraw(Graphics g, ChartAxe xTimeAxe, ChartAxe yAxe) { drawZeroHLine(g, xTimeAxe, yAxe); }

    @Override public void addBar(ChartPoint chartPoint) {
        if (chartPoint != null) {
            long millis = chartPoint.m_millis;
            double value = chartPoint.m_value;
            m_velocityTracker.add(millis, value);
            double velocity = m_velocityTracker.getValue();
            ChartPoint smoochPoint = new ChartPoint(millis, velocity);
            super.addBar(smoochPoint);
        }
    }


    // ===============================================================================================
    private static class VelocityTracker {
        private final Utils.SlidingValuesFrame m_small;
        private final Utils.SlidingValuesFrame m_big;

        private final Interpolator m_interpolator = new Interpolator();

        public VelocityTracker(long frameSizeMillis) {
            m_small = new Utils.SlidingValuesFrame(frameSizeMillis);
            m_big = new Utils.SlidingValuesFrame(frameSizeMillis * 2);
        }

        public double getValue() {
            if (m_big.m_full) {
                Map.Entry<Long, Double> oldest = m_big.m_map.firstEntry();
                Long x1 = oldest.getKey();
                Double y1 = oldest.getValue();

                Map.Entry<Long, Double> mid = m_small.m_map.firstEntry();
                Long x2 = mid.getKey();
                Double y2 = mid.getValue();

                Map.Entry<Long, Double> newest = m_small.m_map.lastEntry();
                Long x3 = newest.getKey();
                Double y3 = newest.getValue();

                if ((x1 < x2) && (x2 < x3)) {
                    PolynomialSplineFunction polynomialFunc = m_interpolator.interpolate(x1, y1, x2, y2, x3, y3);
                    PolynomialFunction[] polynomials = polynomialFunc.getPolynomials();
                    PolynomialFunction polynomial = polynomials[1];

//                System.out.println(" polynomial=" + polynomial);
                    UnivariateFunction derivative = polynomial.derivative();
//                System.out.println("  derivative=" + derivative);

//                double polynomialValue = polynomial.value(x3);
//                System.out.println("   polynomialValue=" + polynomialValue);
//                double polynomialValue2 = polynomial.value(x3-x2);
//                System.out.println("    polynomialValue2=" + polynomialValue2);

//                double derivativeValue = derivative.value(x3);
//                System.out.println("   derivativeValue=" + derivativeValue);
                    double derivativeValue2 = derivative.value(x3 - x2);
//                System.out.println("    derivativeValue2=" + derivativeValue2);
//                System.out.println("    done");

                    return derivativeValue2;
                }
            }

//            if (m_small.m_map.size() > 1) {
//                Map.Entry<Long, Double> oldest = m_small.m_map.firstEntry();
//                Map.Entry<Long, Double> last = m_small.m_map.lastEntry();
//                double diff = last.getValue() - oldest.getValue();
//                if (!m_small.m_full) {
//                    long timeDiff = last.getKey() - oldest.getKey();
//                    double fullRatio = ((double) timeDiff) / m_small.m_frameSizeMillis;
//                    diff *= fullRatio;
//                }
//                return diff;
//            }
            return 0; // parked
        }

        public void add(long millis, double value) {
            m_small.justAdd(millis, value);
            m_big.justAdd(millis, value);
        }
    }

    // ==========================================================================================================
    public static class VelocityRateCalculator {
        private final TresIndicator m_indicator;
        private Direction m_lastDirection;
        private double m_maxAbsVelocity;
        public double m_velocityRate;

        public VelocityRateCalculator(TresIndicator indicator) {
            m_indicator = indicator;
        }

        public ChartPoint update(ChartPoint velocityPoint) {
            if (velocityPoint != null) {
                double velocity = velocityPoint.m_value;
                Direction direction = m_indicator.m_peakWatcher.m_avgPeakCalculator.m_direction;
                if ((direction != null) && ((direction.isForward() && (velocity < 0)) || (direction.isBackward() && (velocity > 0)))) {
                    velocity = 0;
                }
                if (m_lastDirection != direction) { // direction changed
                    double absVelocity = Math.abs(velocity);
                    m_maxAbsVelocity = absVelocity;
                    m_velocityRate = (velocity == 0) ? 0 : 1;
                } else { // the same direction
                    double absVelocity = Math.abs(velocity);
                    m_maxAbsVelocity = Math.max(m_maxAbsVelocity, absVelocity);
                    double velocityRate = (m_maxAbsVelocity == 0) ? 0 : Math.max(0, Math.min(1, 2 * absVelocity / m_maxAbsVelocity));
                    m_velocityRate = velocityRate;
                }
                ChartPoint chartPoint = new ChartPoint(velocityPoint.m_millis, m_velocityRate);
                m_lastDirection = direction;
                return chartPoint;
            }
            return null;
        }
    }
}
