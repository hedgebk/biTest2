package bthdg.tres.alg;

import bthdg.BaseChartPaint;
import bthdg.ChartAxe;
import bthdg.tres.ChartPoint;
import bthdg.tres.TresExchData;
import bthdg.tres.ind.CoppockIndicator;
import bthdg.tres.ind.TresIndicator;
import bthdg.util.Utils;
import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;

import java.awt.*;
import java.util.Map;

public class CoppockVelocityAlgo extends CoppockAlgo {
    private static final long RATIO = 2;

    private final SmoochedIndicator m_smoochedIndicator;
    private final VelocityIndicator m_velocityIndicator;
    private final CursorPainter m_painter;

    public CoppockVelocityAlgo(TresExchData tresExchData) {
        super(tresExchData);
        m_painter = new CursorPainter(m_coppockIndicator);

        final long barSizeMillis = tresExchData.m_tres.m_barSizeMillis;

        m_smoochedIndicator = new SmoochedIndicator(this, RATIO * barSizeMillis) {
            private CursorPainter m_painter = new CursorPainter(this);

            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                ChartPoint lastPoint = getLastPoint();
                if (lastPoint != null) {
                    m_velocityIndicator.addBar(lastPoint);
                }
            }

            @Override public void paint(Graphics g, ChartAxe xTimeAxe, ChartAxe yPriceAxe, Point cursorPoint) {
                super.paint(g, xTimeAxe, yPriceAxe, cursorPoint);
                g.setColor(Color.RED);
                m_painter.paint(g, xTimeAxe, yPriceAxe, cursorPoint);
            }
        };
        m_indicators.add(m_smoochedIndicator);

        m_velocityIndicator = new VelocityIndicator(this, barSizeMillis);
        m_indicators.add(m_velocityIndicator);
    }

    @Override public void paintAlgo(Graphics g, ChartAxe xTimeAxe, ChartAxe yPriceAxe, Point cursorPoint) {
        super.paintAlgo(g, xTimeAxe, yPriceAxe, cursorPoint);
        g.setColor(Color.BLUE);
        m_painter.paint(g, xTimeAxe, yPriceAxe, cursorPoint);
    }

    @Override protected void onCoppockBar() {
        ChartPoint lastPoint = m_coppockIndicator.getLastPoint();
        if (lastPoint != null) {
            m_smoochedIndicator.addBar(lastPoint);
        }
    }

    public static class CursorPainter {
        private final TresIndicator m_indicator;
        private final long m_barSizeMillis;

        public CursorPainter(TresIndicator indicator) {
            m_indicator = indicator;
            m_barSizeMillis = m_indicator.m_algo.m_tresExchData.m_tres.m_barSizeMillis;
        }

        public void paint(Graphics g, ChartAxe xTimeAxe, ChartAxe yPriceAxe, Point cursorPoint) {
            if (m_indicator.m_doPaint && (m_indicator.m_yAxe != null)) {
                if (cursorPoint != null) {
                    int x = (int) cursorPoint.getX();
                    long timeRight = (long) xTimeAxe.getValueFromPoint(x);

                    long timeMid = timeRight - m_barSizeMillis;
                    long timeLeft = timeMid - m_barSizeMillis;

                    int xRight = xTimeAxe.getPoint(timeRight);
                    int xMid = xTimeAxe.getPoint(timeMid);
                    int xLeft = xTimeAxe.getPoint(timeLeft);

                    double yMin = yPriceAxe.m_offset;
                    double yMax = yMin + yPriceAxe.m_size;
                    double yMid = (yMax - yMin) / 2;

                    int yTop = (int) (yMid - 100);
                    int yBottom = (int) (yMid + 100);

                    g.drawLine(xRight, yTop, xRight, yBottom);
                    g.drawLine(xMid, yTop, xMid, yBottom);
                    g.drawLine(xLeft, yTop, xLeft, yBottom);

                    ChartPoint right = findClosest(timeRight);
                    ChartPoint mid = findClosest(timeMid);
                    ChartPoint left = findClosest(timeLeft);

                    drawX(g, xTimeAxe, right);
                    drawX(g, xTimeAxe, mid);
                    drawX(g, xTimeAxe, left);
                }
            }

        }

        protected void drawX(Graphics g, ChartAxe xTimeAxe, ChartPoint cp) {
            long time = cp.m_millis;
            int xx = xTimeAxe.getPoint(time);
            double value = cp.m_value;
            int yy = m_indicator.m_yAxe.getPointReverse(value);
            BaseChartPaint.drawX(g, xx, yy, 5);
        }

        protected ChartPoint findClosest(long time) {
            long minDiff = Long.MAX_VALUE;
            ChartPoint closest = null;
            for (ChartPoint cp : m_indicator.m_avgPaintPoints) {
                long millis = cp.m_millis;
                long diffAbs = Math.abs(millis - time);
                if(diffAbs < minDiff) {
                    minDiff = diffAbs;
                    closest = cp;
                }
            }
            return closest;
        }

    }

    public static class VelocityIndicator extends TresIndicator {
        private final long m_frameSizeMillis;
        private final VelocityTracker m_velocityTracker;

        public VelocityIndicator(TresAlgo algo, long frameSizeMillis) {
            super("v", 0.1, algo);
            m_velocityTracker = new VelocityTracker(frameSizeMillis);
            m_frameSizeMillis = frameSizeMillis;
        }

        @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) { return null; }
        @Override public Color getColor() { return Color.orange; }
        @Override public Color getPeakColor() { return Color.orange; }

        @Override public void addBar(ChartPoint chartPoint) {
            long millis = chartPoint.m_millis;
            double value = chartPoint.m_value;
            m_velocityTracker.add(millis, value);
            double velocity = m_velocityTracker.getValue();
            ChartPoint smoochPoint = new ChartPoint(millis, velocity);
            super.addBar(smoochPoint);
        }

        @Override protected void preDraw(Graphics g, ChartAxe xTimeAxe, ChartAxe yAxe) {
            g.setColor(Color.orange);
            int y = yAxe.getPointReverse(0);
            g.drawLine(xTimeAxe.getPoint(xTimeAxe.m_min), y, xTimeAxe.getPoint(xTimeAxe.m_max), y);
        }
    }

    public static class SmoochedIndicator extends TresIndicator {
        public static double PEAK_TOLERANCE = CoppockIndicator.PEAK_TOLERANCE;

        private final Utils.FadingAverageCounter m_avgCounter;

        public SmoochedIndicator(TresAlgo algo, long frameSizeMillis) {
            super("s", PEAK_TOLERANCE, algo);
            m_avgCounter = new Utils.FadingAverageCounter(frameSizeMillis);
        }

        @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) { return null; }
        @Override public Color getColor() { return Color.green; }
        @Override public Color getPeakColor() { return Color.green; }

        @Override public void addBar(ChartPoint chartPoint) {
            long millis = chartPoint.m_millis;
            double avg = m_avgCounter.add(millis, chartPoint.m_value);
            ChartPoint smoochPoint = new ChartPoint(millis, avg);
            super.addBar(smoochPoint);
        }

        @Override protected void adjustMinMaxCalculator(Utils.DoubleDoubleMinMaxCalculator minMaxCalculator) {
            double max = Math.max(0.1, Math.max(Math.abs(minMaxCalculator.m_minValue), Math.abs(minMaxCalculator.m_maxValue)));
            minMaxCalculator.m_minValue = -max;
            minMaxCalculator.m_maxValue = max;
        }
    }

    private static class VelocityTracker {
        private final Utils.SlidingValuesFrame m_small;
        private final Utils.SlidingValuesFrame m_big;
        private final double m_x[] = new double[3];
        private final double m_y[] = new double[3];
        private final SplineInterpolator m_spline = new SplineInterpolator();

        public VelocityTracker(long frameSizeMillis) {
            m_small = new Utils.SlidingValuesFrame(frameSizeMillis);
            m_big = new Utils.SlidingValuesFrame(frameSizeMillis * 2);
        }

        public double getValue() {
            if (m_big.m_full) {
                Map.Entry<Long, Double> oldest = m_big.m_map.firstEntry();
                Long x1 = oldest.getKey();
                Double y1 = oldest.getValue();
                m_x[0] = x1;
                m_y[0] = y1;
//                System.out.println(" x1=" + x1 + " y1=" + y1);

                Map.Entry<Long, Double> mid = m_small.m_map.firstEntry();
                Long x2 = mid.getKey();
                Double y2 = mid.getValue();
                m_x[1] = x2;
                m_y[1] = y2;
//                System.out.println(" x2=" + x2 + " y2=" + y2);

                Map.Entry<Long, Double> newest = m_small.m_map.lastEntry();
                Long x3 = newest.getKey();
                Double y3 = newest.getValue();
                m_x[2] = x3;
                m_y[2] = y3;
//                System.out.println(" x3=" + x3 + " y3=" + y3);

                PolynomialSplineFunction f = m_spline.interpolate(m_x, m_y);

                PolynomialFunction[] polynomials = f.getPolynomials();
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
}
