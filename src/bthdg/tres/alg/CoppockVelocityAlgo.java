package bthdg.tres.alg;

import bthdg.BaseChartPaint;
import bthdg.ChartAxe;
import bthdg.Log;
import bthdg.exch.Direction;
import bthdg.osc.TrendWatcher;
import bthdg.tres.ChartPoint;
import bthdg.tres.Tres;
import bthdg.tres.TresExchData;
import bthdg.tres.ind.TresIndicator;
import bthdg.util.Utils;
import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;

import java.awt.*;
import java.util.Map;

public class CoppockVelocityAlgo extends CoppockAlgo {
    public static double PEAK_TOLERANCE  = 0.000000005; // cov_vel:
    public static double FRAME_RATIO = 0.4;             // CovRat: smoother frame ratio
    public static double DIRECTION_CUT_LEVEL = 0.99;     // cov_k: 0.998;
    public static double RANGE_SIZE = 0.002;

    private final VelocityIndicator m_velocityIndicator;
    private final VelocitySmoochedIndicator m_velocitySmoochedIndicator;
//    private final VelocityIndicator m_velocityIndicatorShort;
//    private final SmoochedIndicator m_smoochedIndicator;
//    private final VelocityIndicator m_smoochedVelocityIndicator;
    private final CursorPainter m_cursorPainter;
    private final AndIndicator m_andIndicator;

    private static void log(String s) { Log.log(s); }

    public CoppockVelocityAlgo(TresExchData tresExchData) {
        super("COPVEL", tresExchData);
        m_cursorPainter = new CursorPainter(m_coppockIndicator);

        final long barSizeMillis = tresExchData.m_tres.m_barSizeMillis;

        m_velocityIndicator = new VelocityIndicator(this, "vel", barSizeMillis, PEAK_TOLERANCE) {
            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                m_velocitySmoochedIndicator.addBar(getLastPoint());
            }
        };
        m_indicators.add(m_velocityIndicator);

        m_velocitySmoochedIndicator = new VelocitySmoochedIndicator(this, barSizeMillis) {
            public Direction m_lastDirection;

            @Override public void addBar(ChartPoint chartPoint) {
                super.addBar(chartPoint);
                ChartPoint lastPoint = getLastPoint();
                if (lastPoint != null) {
                    double value = calcDirectionAdjusted();
                    long millis = lastPoint.m_millis;
                    ChartPoint andPoint = new ChartPoint(millis, value);
                    m_andIndicator.addBar(andPoint);

                    Direction direction = getDirection();
                    if (direction != m_lastDirection) {
                        notifyListener();
                    }
                    m_lastDirection = direction;
                }
            }
        };
        m_indicators.add(m_velocitySmoochedIndicator);

//        m_velocityIndicatorShort = new VelocityIndicator(this, "velSh", barSizeMillis/2);
//        m_indicators.add(m_velocityIndicatorShort);
//
//        m_smoochedIndicator = new SmoochedIndicator(this, "sm", FRAME_RATIO * barSizeMillis, CoppockIndicator.PEAK_TOLERANCE) {
//            private CursorPainter m_cursorPainter = new CursorPainter(this);
//
//            @Override public void addBar(ChartPoint chartPoint) {
//                super.addBar(chartPoint);
//                m_smoochedVelocityIndicator.addBar(getLastPoint());
//            }
//
//            @Override public void paint(Graphics g, ChartAxe xTimeAxe, ChartAxe yPriceAxe, Point cursorPoint) {
//                super.paint(g, xTimeAxe, yPriceAxe, cursorPoint);
//                g.setColor(Colors.LIGHT_MAGNETA);
//                m_cursorPainter.paint(g, xTimeAxe, yPriceAxe, cursorPoint);
//            }
//        };
//        m_indicators.add(m_smoochedIndicator);
//
//        m_smoochedVelocityIndicator = new VelocityIndicator(this, "smVel", barSizeMillis);
//        m_indicators.add(m_smoochedVelocityIndicator);

        m_andIndicator = new AndIndicator(this);
        m_indicators.add(m_andIndicator);

        if (Tres.LOG_PARAMS) {
            log("CoppockVelocityAlgo");
            log(" PEAK_TOLERANCE=" + PEAK_TOLERANCE);
            log(" FRAME_RATIO=" + FRAME_RATIO);
            log(" RANGE_SIZE=" + RANGE_SIZE);
            log(" DIRECTION_CUT_LEVEL=" + DIRECTION_CUT_LEVEL);
        }
    }

    @Override public void paintAlgo(Graphics g, ChartAxe xTimeAxe, ChartAxe yPriceAxe, Point cursorPoint) {
        super.paintAlgo(g, xTimeAxe, yPriceAxe, cursorPoint);
        g.setColor(Color.WHITE);
        m_cursorPainter.paint(g, xTimeAxe, yPriceAxe, cursorPoint);
    }

    @Override protected void onCoppockBar() {
        ChartPoint lastPoint = m_coppockIndicator.getLastPoint();
        if (lastPoint != null) {
            m_velocityIndicator.addBar(lastPoint);
//            m_velocityIndicatorShort.addBar(lastPoint);
//            m_smoochedIndicator.addBar(lastPoint);
        }
    }

    @Override public double getDirectionAdjusted() {
        return m_velocitySmoochedIndicator.calcDirectionAdjusted();
    }

    @Override public Direction getDirection() {
        return m_velocitySmoochedIndicator.getDirection();
    }

    @Override public String getRunAlgoParams() {
        return "CopVel: tolerance=" + m_coppockIndicator.m_peakWatcher.m_avgPeakCalculator.m_tolerance
                + " RANGE_SIZE=" + RANGE_SIZE
                + " DIRECTION_CUT_LEVEL=" + DIRECTION_CUT_LEVEL
                + " FRAME_RATIO=" + FRAME_RATIO
                + " PEAK_TOLERANCE=" + PEAK_TOLERANCE;
    }


    public static class CursorPainter {
        private final TresIndicator m_indicator;
        private final long m_barSizeMillis;
        private final Interpolator m_interpolator = new Interpolator();

        public CursorPainter(TresIndicator indicator) {
            m_indicator = indicator;
            m_barSizeMillis = m_indicator.m_algo.m_tresExchData.m_tres.m_barSizeMillis;
        }

        public void paint(Graphics g, ChartAxe xTimeAxe, ChartAxe yPriceAxe, Point cursorPoint) {
            if (m_indicator.m_doPaint && (m_indicator.m_yAxe != null)) {
                if (cursorPoint != null) {
                    int cursorPointX = (int) cursorPoint.getX();

                    int xMin = xTimeAxe.m_offset;
                    int xMax = xMin + xTimeAxe.m_size;
                    if((cursorPointX > xMin) && (cursorPointX < xMax)) {
                        long timeRight = (long) xTimeAxe.getValueFromPoint(cursorPointX);
                        long timeMid = timeRight - m_barSizeMillis;
                        long timeLeft = timeMid - m_barSizeMillis;

                        int xRight = xTimeAxe.getPoint(timeRight);
                        int xMid = xTimeAxe.getPoint(timeMid);
                        int xLeft = xTimeAxe.getPoint(timeLeft);

                        int xRightRight = xRight + (xRight - xMid);
                        int xLeftLeft = xLeft - (xMid - xLeft);

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

                        long minMillis = left.m_millis;
                        long midMillis = mid.m_millis;
                        long maxMillis = right.m_millis;
                        if( (minMillis < midMillis) && (midMillis<maxMillis)) { // should linearly increase
                            PolynomialSplineFunction polynomialFunc = null;
                            try {
                                polynomialFunc = m_interpolator.interpolate(minMillis, left.m_value, midMillis, mid.m_value, maxMillis, right.m_value);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                            PolynomialFunction[] polynomials = polynomialFunc.getPolynomials();

                            int lastX = Integer.MAX_VALUE;
                            int lastY = Integer.MAX_VALUE;
                            for (int x = xLeftLeft; x <= xRightRight; x++) {
                                long time = (long) xTimeAxe.getValueFromPoint(x);
                                if (true/*(time >= minMillis) && (time <= maxMillis)*/) {
//                                    double value = polynomialFunc.value(time);
////                                    int xx = xTimeAxe.getPoint(time);
//                                    int yy = m_indicator.m_yAxe.getPointReverse(value);
//                                    if (lastX != Integer.MAX_VALUE) {
//                                        g.drawLine(lastX, lastY, x, yy);
//                                    }
//                                    lastX = x;
//                                    lastY = yy;

                                    if(x < xMid) {
                                        PolynomialFunction polynomial = polynomials[0];
//                    UnivariateFunction derivative = polynomial.derivative();
                                        long offset = time - minMillis;
                                        double value = polynomial.value(offset);
                                        int yy = m_indicator.m_yAxe.getPointReverse(value);
                                        if (lastX != Integer.MAX_VALUE) {
                                            g.drawLine(lastX, lastY, x, yy);
                                        }
                                        lastX = x;
                                        lastY = yy;
                                    } else {
                                        PolynomialFunction polynomial = polynomials[1];
                                        long offset = time - midMillis;
                                        double value = polynomial.value(offset);
                                        int yy = m_indicator.m_yAxe.getPointReverse(value);
                                        if (lastX != Integer.MAX_VALUE) {
                                            g.drawLine(lastX, lastY, x, yy);
                                        }
                                        lastX = x;
                                        lastY = yy;
                                    }
                                }
                            }
                        }
                    }
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

        public VelocityIndicator(TresAlgo algo, String name, long frameSizeMillis, double peakTolerance) {
            super(name, peakTolerance, algo);
            m_velocityTracker = new VelocityTracker(frameSizeMillis);
            m_frameSizeMillis = frameSizeMillis;
        }

        @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) { return null; }
        @Override public Color getColor() { return Color.orange; }
        @Override public Color getPeakColor() { return Color.orange; }

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

        @Override protected void preDraw(Graphics g, ChartAxe xTimeAxe, ChartAxe yAxe) {
            g.setColor(getColor());
            int y = yAxe.getPointReverse(0);
            g.drawLine(xTimeAxe.getPoint(xTimeAxe.m_min), y, xTimeAxe.getPoint(xTimeAxe.m_max), y);
        }
    }

    public static class SmoochedIndicator extends TresIndicator {
        private final Utils.FadingAverageCounter m_avgCounter;

        public SmoochedIndicator(TresAlgo algo, String name, long frameSizeMillis, double peakTolerance) {
            super(name, peakTolerance, algo);
            m_avgCounter = new Utils.FadingAverageCounter(frameSizeMillis);
        }

        @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) { return null; }
        @Override public Color getColor() { return Color.green; }
        @Override public Color getPeakColor() { return Color.green; }

        @Override public void addBar(ChartPoint chartPoint) {
            if (chartPoint != null) {
                long millis = chartPoint.m_millis;
                double avg = m_avgCounter.add(millis, chartPoint.m_value);
                ChartPoint smoochPoint = new ChartPoint(millis, avg);
                super.addBar(smoochPoint);
            }
        }

        @Override protected void adjustMinMaxCalculator(Utils.DoubleDoubleMinMaxCalculator minMaxCalculator) {
            double max = Math.max(Math.abs(minMaxCalculator.m_minValue), Math.abs(minMaxCalculator.m_maxValue));
            minMaxCalculator.m_minValue = -max;
            minMaxCalculator.m_maxValue = max;
        }
    }

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


    private static class Interpolator {
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

    private static class VelocitySmoochedIndicator extends SmoochedIndicator {
        public State m_state = State.NONE;
        private double m_lastPeak;

        public VelocitySmoochedIndicator(TresAlgo algo, long barSizeMillis) {
            super(algo, "velSm", (long) (CoppockVelocityAlgo.FRAME_RATIO * barSizeMillis), PEAK_TOLERANCE);
        }

        @Override protected boolean countHalfPeaks() { return false; }

        @Override protected void preDraw(Graphics g, ChartAxe xTimeAxe, ChartAxe yAxe) {
            g.setColor(getColor());
            int y = yAxe.getPointReverse(0);
            g.drawLine(xTimeAxe.getPoint(xTimeAxe.m_min), y, xTimeAxe.getPoint(xTimeAxe.m_max), y);
        }

        public double calcDirectionAdjusted() {
            if (m_state != null) {
                return m_state.calcDirectionAdjusted(this);
            }
            return 0;
        }

        public Direction getDirection() {
            if (m_state != null) {
                return m_state.getDirection(this);
            }
            return null;
        }

        @Override protected void onAvgPeak(TrendWatcher<ChartPoint> trendWatcher) {
            super.onAvgPeak(trendWatcher);
            Direction direction = trendWatcher.m_direction;
            ChartPoint peak = trendWatcher.m_peak;
            ChartPoint last = getLastPoint();
//            log("velocitySmoochedIndicator.onAvgPeak() " +
//                    "peak[t=" + peak.m_millis + ", v=" + Utils.format8(peak.m_value) + "]; " +
//                    "last[t=" + last.m_millis + ", v=" + Utils.format8(last.m_value) + "]; " +
//                    "direction=" + direction);
            double peakValue = peak.m_value;
            State state = m_state.onAvgPeak(peakValue, direction);
            if (state != null) {
                if (m_state != state) {
                    m_lastPeak = peakValue;
//                log(" state " + m_state + " -> " + state + "; lastPeak=" + m_lastPeak);
                    m_state = state;
                } else { // same direction peak again - update if bigger
                    double newPeakAbs = Math.abs(peakValue);
                    double lastPeakAbs = Math.abs(m_lastPeak);
                    if (newPeakAbs > lastPeakAbs) {
                        m_lastPeak = peakValue; // update with farthest
                    } else {
                        m_lastPeak = (m_lastPeak + peakValue) / 2; // update with nearest
                    }
                }
            } else {
                m_lastPeak = (m_lastPeak + peakValue*2) / 3; // update with nearest
            }
        }
    }

    public enum State {
        NONE {
        }, DOWN {
            @Override public double calcDirectionAdjusted(VelocitySmoochedIndicator indicator) {
                return calcDirection(indicator, 1.0);
            }
            @Override public Direction getDirection(VelocitySmoochedIndicator indicator) {
                return getDirectionInt(indicator);
            }

        }, UP {
            @Override public double calcDirectionAdjusted(VelocitySmoochedIndicator indicator) {
                return calcDirection(indicator, -1.0);
            }
            @Override public Direction getDirection(VelocitySmoochedIndicator indicator) {
                return getDirectionInt(indicator);
            }
        };

        protected Direction getDirectionInt(VelocitySmoochedIndicator indicator) {
            double lastPeak = indicator.m_lastPeak;
            double range = lastPeak * DIRECTION_CUT_LEVEL;
            double lastValue = indicator.getLastPoint().m_value;
            return (lastValue >= range) ? Direction.FORWARD : Direction.BACKWARD;
        }

        protected static double calcDirection(VelocitySmoochedIndicator indicator, double mul) {
            double lastPeak = indicator.m_lastPeak;
            double rangeStart = lastPeak * (DIRECTION_CUT_LEVEL + RANGE_SIZE / 2);
            double lastValue = indicator.getLastPoint().m_value;
            double rangeSize = RANGE_SIZE * lastPeak;
            double val = mul * (1.0 - 2 * (rangeStart - lastValue) / rangeSize);
            return Math.min(Math.max(val, -1), 1);
        }

        public State onAvgPeak(double peakValue, Direction direction) {
            if ((peakValue > 0) && direction.isBackward()) {
                return DOWN;
            }
            if ((peakValue < 0) && direction.isForward()) {
                return UP;
            }
            return null;
        }

        public double calcDirectionAdjusted(VelocitySmoochedIndicator indicator) {
            return 0;
        }

        public Direction getDirection(VelocitySmoochedIndicator indicator) {
            return null;
        }
    }


    public static class AndIndicator extends TresIndicator {
        public static double PEAK_TOLERANCE = 0.06470;

        public AndIndicator(TresAlgo algo) {
            super("+", PEAK_TOLERANCE, algo);
        }

        @Override protected boolean countPeaks() { return false; }
        @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) { return null; }
        @Override public Color getColor() { return Color.red; }
        @Override public Color getPeakColor() { return Color.red; }
    }
}
